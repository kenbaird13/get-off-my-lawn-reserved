package draylar.goml.mixin;

import com.jamieswhiteshirt.rtree3i.Entry;
import draylar.goml.api.ClaimBox;
import draylar.goml.api.Claim;
import draylar.goml.api.ClaimUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin which prevents TNT primed by player A from going off in player B's claim.
 */
@Mixin(PrimedTnt.class)
public abstract class PrimedTntMixin extends Entity {

    @Shadow @Nullable private LivingEntity owner;

    public PrimedTntMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Inject(at = @At("HEAD"), method = "explode", cancellable = true)
    private void goml_attemptExplosion(CallbackInfo ci) {
        if (level().isClientSide()) {
            return;
        }
        if (this.owner != null) {
            var claimsFound = ClaimUtils.getActiveClaimsAt(level(), blockPosition());
            var entity = this.owner;
            if (entity instanceof Player player) {
                if (!claimsFound.isEmpty()) {
                    boolean noPermission = claimsFound.anyMatch((Entry<ClaimBox, Claim> boxInfo) -> !boxInfo.getValue().hasPermission(player));

                    if (noPermission) {
                        ci.cancel();
                    }
                }
            } else if (entity != null) {
                if (!claimsFound.isEmpty()) {
                    boolean noPermission = claimsFound.anyMatch((Entry<ClaimBox, Claim> boxInfo) -> !boxInfo.getValue().hasPermission(this.owner.getUUID()));

                    if (noPermission) {
                        ci.cancel();
                    }
                }
            }
        }
    }
}
