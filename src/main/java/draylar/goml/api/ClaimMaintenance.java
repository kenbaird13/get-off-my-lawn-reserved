package draylar.goml.api;

import draylar.goml.GetOffMyLawn;
import draylar.goml.config.MaintenanceRule;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import draylar.goml.other.nbt.NbtInput;
import draylar.goml.other.nbt.NbtOutput;

/** Claim-owned stock and tick state; independent of the anchor's chunk or block entity. */
public final class ClaimMaintenance extends SimpleContainer {
    public static final int SIZE = 9;
    public static final long MAX_TICKS = Integer.MAX_VALUE * 1200L;
    private long remainingTicks;
    private boolean overdue;
    private boolean initialized;

    public ClaimMaintenance() {
        super(SIZE);
    }

    public long remainingTicks() { return remainingTicks; }
    public boolean isOverdue() { return overdue; }
    public boolean isInitialized() { return initialized; }

    /** Returns true only when protection has expired. No disk writes occur here. */
    public boolean tick(MaintenanceRule rule) {
        if (rule.isExempt() || overdue) return false;
        if (!initialized) {
            renew(rule);
            return false;
        }
        if (remainingTicks > 0) remainingTicks--;
        if (remainingTicks == 0) {
            if (consumeOne(this, rule.item())) renew(rule);
            else {
                overdue = true;
                return true;
            }
        }
        return false;
    }

    public void renew(MaintenanceRule rule) {
        if (rule.isExempt()) return;
        initialized = true;
        overdue = false;
        remainingTicks = rule.intervalTicks();
    }

    public void upgraded(MaintenanceRule rule) {
        if (!overdue) renew(rule);
    }

    public static boolean consumeOne(Container inventory, Item item) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item) && !inventory.getItem(i).isEmpty()) {
                inventory.removeItem(i, 1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    public void writeData(NbtOutput out) {
        out.putInt("Version", 1);
        out.putBoolean("Initialized", initialized);
        out.putLong("RemainingTicks", remainingTicks);
        out.putBoolean("Overdue", overdue);
        var items = out.childrenList("Items");
        for (int i = 0; i < SIZE; i++) {
            if (!getItem(i).isEmpty()) {
                var entry = items.addChild();
                entry.putInt("Slot", i);
                entry.store("Stack", ItemStack.CODEC, getItem(i));
            }
        }
    }

    public void readData(NbtInput in) {
        clearContent();
        initialized = in.getBooleanOr("Initialized", false);
        overdue = in.getBooleanOr("Overdue", false);
        remainingTicks = Math.clamp(in.getLongOr("RemainingTicks", 0), 0, MAX_TICKS);
        if (overdue) {
            initialized = true;
            remainingTicks = 0;
        }
        for (var entry : in.childrenListOrEmpty("Items")) {
            int slot = entry.getIntOr("Slot", -1);
            if (slot < 0 || slot >= SIZE || !getItem(slot).isEmpty()) {
                GetOffMyLawn.LOGGER.warn("Ignoring invalid or duplicate claim maintenance slot {}", slot);
                continue;
            }
            entry.read("Stack", ItemStack.CODEC).ifPresent(stack -> setItem(slot, stack));
        }
    }
}
