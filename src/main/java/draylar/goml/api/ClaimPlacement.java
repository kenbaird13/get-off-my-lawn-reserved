package draylar.goml.api;

import draylar.goml.GetOffMyLawn;
import draylar.goml.block.ClaimAnchorBlock;
import draylar.goml.other.FabricPermissionBridge;
import draylar.goml.registry.GOMLBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import static draylar.goml.GetOffMyLawn.id;

/** Shared placement and takeover policy. A null result means eligible. */
public final class ClaimPlacement {
    private ClaimPlacement() {}

    @Nullable
    public static Component rejection(Level world, Player player, ClaimAnchorBlock type, ClaimBox box, @Nullable Claim target) {
        if (player == null || player.isSpectator()) return Component.translatable("text.goml.maintenance.unavailable");
        boolean admin = ClaimUtils.isInAdminMode(player);
        if (type.getRadius() <= 0 && !admin) return Component.translatable("text.goml.cant_place_claim.admin_only");
        if (!admin) {
            var count = ClaimUtils.getClaimsOwnedBy(world, player.getUUID())
                    .filter(x -> x.getValue() != target && x.getValue().getType() != GOMLBlocks.ADMIN_CLAIM_ANCHOR.getFirst()).count();
            var general = FabricPermissionBridge.checkPermissionInteger(player, id("claim_limit"));
            var dimension = world.dimension().location();
            var specific = FabricPermissionBridge.checkPermissionInteger(player,
                    id("claim_limit/" + dimension.getNamespace() + "/" + dimension.getPath()));
            int max = specific.orElse(general.orElse(GetOffMyLawn.CONFIG.maxClaimsPerPlayer));
            if (max != -1 && count >= max) return Component.translatable("text.goml.cant_place_claim.max_count_reached", count, max);
            if (GetOffMyLawn.CONFIG.isBlacklisted(world, box.toBox()))
                return Component.translatable("text.goml.cant_place_claim.blacklisted_area", dimension.toString(), box.getOrigin().toShortString());
        }
        var overlaps = ClaimUtils.getClaimsInBox(world, box.rtree3iBox()).filter(x -> x.getValue() != target);
        if (target != null && overlaps.isNotEmpty() && !target.isOwner(player))
            return Component.translatable("text.goml.maintenance.overlap_owner_only");
        if (GetOffMyLawn.CONFIG.allowClaimOverlappingIfSameOwner)
            overlaps = overlaps.filter(x -> !x.getValue().isOwner(player) || x.getKey().toBox().equals(box.toBox()));
        if (overlaps.isNotEmpty()) return Component.translatable("text.goml.maintenance.overlap");
        return null;
    }
}
