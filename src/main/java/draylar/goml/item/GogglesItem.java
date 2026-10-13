package draylar.goml.item;

import draylar.goml.api.ClaimUtils;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ArmorMaterials;


public class GogglesItem extends net.minecraft.world.item.ArmorItem implements PolymerItem {
    public GogglesItem(Properties settings) {
        super(ArmorMaterials.IRON, net.minecraft.world.item.ArmorItem.Type.HELMET, settings.stacksTo(1));
    }

    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.world.level.Level world, Entity entity, int slot, boolean selected) {
        if (entity instanceof ServerPlayer player && (selected || player.getOffhandItem() == stack || player.getItemBySlot(EquipmentSlot.HEAD) == stack)) {
            if (player.tickCount % 70 == 0) {
                var distance = player.level().getServer().getPlayerList().getViewDistance() * 16;

                ClaimUtils.getClaimsInBox(
                        world,
                        entity.blockPosition().offset(-distance, -distance, -distance),
                        entity.blockPosition().offset(distance, distance, distance)).forEach(
                        claim -> {
                            ClaimUtils.drawClaimInWorld(player, claim.getValue());
                        });
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, ServerPlayer player) {
        return Items.IRON_HELMET;
    }

}
