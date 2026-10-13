package draylar.goml.block;

import draylar.goml.GetOffMyLawn;
import draylar.goml.api.Claim;
import draylar.goml.api.ClaimUtils;
import draylar.goml.api.event.ClaimEvents;
import draylar.goml.block.entity.ClaimAnchorBlockEntity;
import draylar.goml.item.UpgradeKitItem;
import draylar.goml.registry.GOMLEntities;
import draylar.goml.registry.GOMLTextures;
import eu.pb4.polymer.core.api.block.PolymerHeadBlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import draylar.goml.ui.ClaimMaintenanceGui;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

@SuppressWarnings({"deprecation"})
public class ClaimAnchorBlock extends Block implements EntityBlock, PolymerHeadBlock {

    private final IntSupplier radius;
    private final String texture;

    @Deprecated
    public ClaimAnchorBlock(BlockBehaviour.Properties settings, int radius) {
        this(settings, () -> radius, GOMLTextures.MISSING_TEXTURE);
    }

    public ClaimAnchorBlock(BlockBehaviour.Properties settings, IntSupplier radius, String texture) {
        super(settings.forceSolidOn());
        this.radius = radius;
        this.texture = texture;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        if (world == null) {
            return;
        }

        if (!world.isClientSide()) {
            var radius = Math.max(this.radius.getAsInt(), 1);

            Claim claimInfo = new Claim(world.getServer(), Collections.singleton(placer.getUUID()), Collections.emptySet(), pos);
            claimInfo.internal_setIcon(new ItemStack(itemStack.getItem()));
            claimInfo.internal_setType(this);
            claimInfo.internal_setWorld(world.dimension().location());
            var box = ClaimUtils.createClaimBox(pos, radius);
            claimInfo.internal_setClaimBox(box);
            GetOffMyLawn.CLAIM.get(world).add(claimInfo);

            // Assign claim to BE
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof ClaimAnchorBlockEntity anchor) {
                anchor.setClaim(claimInfo, box);
            }
            if (world instanceof ServerLevel world1) {
                claimInfo.internal_updateChunkCount(world1);
            }

            ClaimEvents.CLAIM_CREATED.invoker().onEvent(claimInfo);
            claimInfo.internal_enableUpdates();

            if (placer instanceof ServerPlayer player) {
                ClaimUtils.drawClaimInWorld(player, claimInfo);
            }
        }

        super.setPlacedBy(world, pos, state, placer, itemStack);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClientSide() && state.getBlock() != newState.getBlock()
                && world.getBlockEntity(pos) instanceof ClaimAnchorBlockEntity be && be.getClaim() != null
                && !be.getClaim().isReplacingAnchor()) be.getClaim().destroy();
        super.onRemove(state, world, pos, newState, moved);
    }

    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (world == null || world.isClientSide()) {
            return state;
        }

        ClaimUtils.getClaimsAt(world, pos).forEach(claimedArea -> {
            if (ClaimUtils.canDestroyClaimBlock(claimedArea, player, pos)) {
                claimedArea.getValue().destroy();
            }
        });

        return super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
                                             Player playerEntity, InteractionHand hand, BlockHitResult hit) {
        if (playerEntity instanceof ServerPlayer player
                && world.getBlockEntity(pos) instanceof ClaimAnchorBlockEntity anchor) {
            var claim = anchor.getClaim();
            if (claim != null && !claim.isProtectionActive() && stack.is(claim.maintenanceRule().item())) {
                var error = claim.takeOver(player, hand);
                if (error != null) {
                    player.sendSystemMessage(GetOffMyLawn.CONFIG.prefix(error.copy().withStyle(ChatFormatting.RED)));
                    // End this interaction so a rejected payment cannot place or otherwise use the held item.
                    return ItemInteractionResult.CONSUME;
                }
                player.sendSystemMessage(GetOffMyLawn.CONFIG.prefix(Component.translatable("text.goml.maintenance.paid").withStyle(ChatFormatting.GREEN)));
                new ClaimMaintenanceGui(player, claim);
                return ItemInteractionResult.SUCCESS;
            }
        }
        return super.useItemOn(stack, state, world, pos, playerEntity, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player playerEntity, BlockHitResult hit) {
        if (playerEntity instanceof ServerPlayer player && !player.isShiftKeyDown() && !(player.getMainHandItem().getItem() instanceof UpgradeKitItem)) {
            var blockEntity = world.getBlockEntity(pos, GOMLEntities.CLAIM_ANCHOR);
            // Allow Minecraft's second-hand interaction instead of opening a menu over an offhand payment.
            if (blockEntity.isPresent()) {
                var claim = blockEntity.get().getClaim();
                if (claim != null && !claim.isProtectionActive() && player.getOffhandItem().is(claim.maintenanceRule().item()))
                    return InteractionResult.PASS;
            }
            blockEntity.ifPresent(anchor -> { if (anchor.getClaim() != null) anchor.getClaim().openUi(player); });

            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, world, pos, playerEntity, hit);
    }

    public int getRadius() {
        return this.radius.getAsInt();
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
        if (ClaimUtils.isInAdminMode(player) || (world.getBlockEntity(pos) instanceof ClaimAnchorBlockEntity be
                && be.getClaim() != null && (!be.getClaim().isProtectionActive() || be.getClaim().isOwner(player)))) {
            return super.getDestroyProgress(state, player, world, pos);
        } else {
            return 0;
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ClaimAnchorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return ClaimAnchorBlockEntity::tick;
    }

    @Override
    public String getPolymerSkinValue(BlockState state, BlockPos pos, ServerPlayer player) {
        return this.texture;
    }
}
