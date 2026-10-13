package draylar.goml.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** A validated per-tier rule. Saved claim data deliberately does not contain this rule. */
public final class MaintenanceRule {
    public String cost = "minecraft:diamond";
    public int period = 30;
    private transient Item paymentItem;

    public MaintenanceRule() {}

    public MaintenanceRule(String cost, int period) {
        this.cost = cost;
        this.period = period;
    }

    public static MaintenanceRule exempt() {
        return new MaintenanceRule("minecraft:diamond", 0);
    }

    public long intervalTicks() {
        return period * 60L * 20L;
    }

    public boolean isExempt() {
        return period == 0;
    }

    public Item item() {
        if (paymentItem == null) {
            validate();
        }
        return paymentItem;
    }

    public void validate() {
        if (period < 0) {
            throw new IllegalArgumentException("period must be a nonnegative integer");
        }
        var id = cost == null ? null : ResourceLocation.tryParse(cost);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            throw new IllegalArgumentException("cost must name a registered item: " + cost);
        }
        var item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalArgumentException("cost cannot be air");
        }
        paymentItem = item;
    }
}
