package draylar.goml.ui;

import draylar.goml.GetOffMyLawn;
import draylar.goml.api.Claim;
import draylar.goml.api.ClaimMaintenance;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

public final class ClaimMaintenanceGui extends ClaimGui {
    private int displayTicks;

    public ClaimMaintenanceGui(ServerPlayer player, Claim claim) {
        super(claim, MenuType.GENERIC_9x3, player, false);
        setTitle(Component.translatable("text.goml.maintenance.title"));
        for (int i = 0; i < getSize(); i++) setSlot(i, PagedGui.DisplayElement.filler().element());
        for (int i = 0; i < ClaimMaintenance.SIZE; i++) {
            setSlotRedirect(i, new Slot(claim.maintenance(), i, 0, 0) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return canUseStorage() && stack.is(claim.maintenanceRule().item());
                }
                @Override public boolean mayPickup(Player player) { return canUseStorage(); }
                @Override public boolean allowModification(Player player) { return canUseStorage(); }
            });
        }
        refreshStatus();
        setSlot(22, new GuiElementBuilder(Items.EMERALD)
                .setName(Component.translatable("text.goml.maintenance.pay"))
                .addLoreLine(Component.translatable("text.goml.maintenance.pay_help"))
                .setCallback(() -> {
                    if (!hasAccess()) { close(false); return; }
                    var error = claim.takeOver(player);
                    if (error != null) player.sendSystemMessage(GetOffMyLawn.CONFIG.prefix(error));
                    else {
                        player.sendSystemMessage(GetOffMyLawn.CONFIG.prefix(Component.translatable("text.goml.maintenance.paid")));
                        new ClaimMaintenanceGui(player, claim);
                    }
                }));
        setSlot(26, new GuiElementBuilder(Items.STRUCTURE_VOID)
                .setName(Component.translatable("text.goml.gui.back"))
                .setCallback(() -> { close(false); if (!claim.isDestroyed()) claim.openUi(player); }));
        open();
    }

    private boolean canUseStorage() {
        return hasAccess() && claim.isProtectionActive() && claim.canManage(player);
    }

    @Override
    public ItemStack quickMove(int index) {
        // SGui's quickMove implementation does not itself check the source slot's mayPickup.
        return canUseStorage() ? super.quickMove(index) : ItemStack.EMPTY;
    }

    private void refreshStatus() {
        setSlot(13, new GuiElementBuilder(claim.maintenanceRule().item())
                .setName(status(claim)).setLore(statusLines(claim)));
    }

    @Override
    public void onTick() {
        super.onTick();
        if (isOpen() && ++displayTicks % 20 == 0) refreshStatus();
    }

    public static Component status(Claim claim) {
        return Component.translatable(claim.maintenanceRule().isExempt() ? "text.goml.maintenance.exempt"
                : claim.isProtectionActive() ? "text.goml.maintenance.active" : "text.goml.maintenance.overdue")
                .withStyle(claim.isProtectionActive() ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    public static List<Component> statusLines(Claim claim) {
        var rule = claim.maintenanceRule();
        long seconds = (claim.maintenance().remainingTicks() + 19) / 20;
        return List.of(
                Component.translatable("text.goml.maintenance.tier", claim.getType().getName()),
                Component.translatable("text.goml.maintenance.cost", rule.item().getDefaultInstance().getHoverName()),
                Component.translatable("text.goml.maintenance.period", rule.period),
                Component.translatable("text.goml.maintenance.remaining", seconds / 60, seconds % 60),
                status(claim));
    }
}
