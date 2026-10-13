package draylar.goml.item;

import draylar.goml.GetOffMyLawn;
import draylar.goml.api.ClaimUtils;
import draylar.goml.block.ClaimAnchorBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Consumer;


public class ClaimAnchorBlockItem extends TooltippedBlockItem {

    private final ClaimAnchorBlock claimBlock;

    public ClaimAnchorBlockItem(ClaimAnchorBlock block, Properties settings, int lines) {
        super(block, settings, lines);
        this.claimBlock = block;
    }

    @Override
    public void addLines(Consumer<Component> textConsumer) {
        super.addLines(textConsumer);
        textConsumer.accept(Component.translatable("text.goml.radius",
                Component.literal("" + this.claimBlock.getRadius()).withStyle(ChatFormatting.WHITE)
        ).withStyle(ChatFormatting.YELLOW));
    }

    @Override
    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        if (context.getLevel().isClientSide()) {
            return true;
        }

        var box = ClaimUtils.createClaimBox(context.getClickedPos(), Math.max(claimBlock.getRadius(), 1));
        var rejection = draylar.goml.api.ClaimPlacement.rejection(context.getLevel(), context.getPlayer(), claimBlock, box, null);
        if (rejection != null) {
            if (context.getPlayer() != null) context.getPlayer().sendSystemMessage(GetOffMyLawn.CONFIG.prefix(rejection.copy().withStyle(ChatFormatting.RED)));
            return false;
        }

        return super.canPlace(context, state);
    }
}
