package draylar.goml.mixin;

import draylar.goml.api.ClaimUtils;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;

@Mixin(value = Explosion.class, priority = 800)
public abstract class ServerExplosionMixin {

    @Shadow @Nullable public abstract LivingEntity getIndirectSourceEntity();

    @Shadow @Final private net.minecraft.world.level.Level level;
    @Shadow @Final private it.unimi.dsi.fastutil.objects.ObjectArrayList<BlockPos> toBlow;

    @Inject(method = "explode", at = @At("TAIL"))
    private void goml_clearBlocks(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        this.toBlow.removeIf((b) -> !ClaimUtils.canExplosionDestroy(this.level, b, this.getIndirectSourceEntity()));
    }

    @ModifyVariable(method = "explode", at = @At("STORE"), ordinal = 0)
    private List<Entity> goml_clearEntities(List<Entity> x) {
        x.removeIf((e) -> !ClaimUtils.canExplosionDestroy(this.level, e.blockPosition(), this.getIndirectSourceEntity()));
        return x;
    }
}
