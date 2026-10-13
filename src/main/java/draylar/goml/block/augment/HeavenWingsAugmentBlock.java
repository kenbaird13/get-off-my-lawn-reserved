package draylar.goml.block.augment;

import draylar.goml.GetOffMyLawn;
import draylar.goml.api.Claim;
import draylar.goml.api.ClaimUtils;
import draylar.goml.block.SelectiveClaimAugmentBlock;
import io.github.ladysnake.pal.AbilitySource;
import io.github.ladysnake.pal.Pal;
import io.github.ladysnake.pal.VanillaAbilities;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.entity.player.Player;

public class HeavenWingsAugmentBlock extends SelectiveClaimAugmentBlock {

    public static final AbilitySource HEAVEN_WINGS = Pal.getAbilitySource("goml", "heaven_wings");

    public HeavenWingsAugmentBlock(Properties settings, String texture) {
        super("heaven_wings", settings, texture);
        ServerPlayConnectionEvents.JOIN.register((handler, packetSender, minecraftServer) -> {
            GetOffMyLawn.NEXT_TICK_TASK.add(() -> {
                if (!handler.isAcceptingMessages()) {
                    return;
                }

                var canFly = ClaimUtils.getActiveClaimsAt(handler.player.level(), handler.player.blockPosition())
                        .filter(x -> x.getValue().hasAugment(this) && this.canApply(x.getValue(), handler.player)).isNotEmpty();

                if (canFly) {
                    return;
                }

                HEAVEN_WINGS.revokeFrom(handler.player, VanillaAbilities.ALLOW_FLYING);
            });
        });
    }

    @Override
    public void applyEffect(Player player) {
        HEAVEN_WINGS.grantTo(player, VanillaAbilities.ALLOW_FLYING);
    }

    @Override
    public void removeEffect(Player player) {
        HEAVEN_WINGS.revokeFrom(player, VanillaAbilities.ALLOW_FLYING);
    }

    @Override
    public void onPlayerExit(Claim claim, Player player) {
        var canFly = ClaimUtils.getActiveClaimsAt(player.level(), player.blockPosition())
                .filter(x -> x.getValue() != claim && x.getValue().hasAugment(this) && this.canApply(x.getValue(), player)).isNotEmpty();

        if (!canFly) {
            super.onPlayerExit(claim, player);
        }
    }
}
