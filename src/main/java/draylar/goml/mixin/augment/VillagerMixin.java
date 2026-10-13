package draylar.goml.mixin.augment;

import draylar.goml.api.ClaimUtils;
import draylar.goml.registry.GOMLBlocks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Villager.class)
public abstract class VillagerMixin extends LivingEntity {

    private VillagerMixin(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if(source.getEntity() instanceof Monster) {
            boolean b = ClaimUtils.getActiveClaimsAt(level(), blockPosition()).anyMatch(claim -> claim.getValue().hasAugment(GOMLBlocks.VILLAGE_CORE.getFirst()));

            if(b) return true;
        }

        return super.isInvulnerableTo(source);
    }
}
