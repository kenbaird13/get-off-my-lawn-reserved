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
import java.util.ArrayList;
import java.util.List;

public final class ClaimMaintenanceGui extends ClaimGui {
    private int displayTicks;

    public ClaimMaintenanceGui(ServerPlayer player, Claim claim) {
        super(claim, MenuType.GENERIC_9x3, player, true);
        setTitle(Component.translatable("text.goml.maintenance.title").withStyle(style -> style.withItalic(false)));
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
        setSlot(26, new GuiElementBuilder(Items.STRUCTURE_VOID)
                .setName(Component.translatable("text.goml.gui.back").withStyle(ChatFormatting.RED).withStyle(style -> style.withItalic(false)))
                .hideDefaultTooltip()
                .setCallback(() -> { close(false); if (!claim.isDestroyed()) claim.openUi(player); }));
        open();
    }

    private boolean canUseStorage() {
        return hasAccess();
    }

    @Override
    protected boolean hasAccess() {
        return super.hasAccess() && claim.isProtectionActive();
    }

    @Override
    public boolean open() {
        if (!claim.isDestroyed() && !claim.isProtectionActive()) {
            player.sendSystemMessage(GetOffMyLawn.CONFIG.prefix(claim.expiredMessage()));
            return false;
        }
        return super.open();
    }

    @Override
    public ItemStack quickMove(int index) {
        // SGui's quickMove implementation does not itself check the source slot's mayPickup.
        return canUseStorage() ? super.quickMove(index) : ItemStack.EMPTY;
    }

    private void refreshStatus() {
        setSlot(13, new GuiElementBuilder(claim.maintenanceRule().item())
                .hideDefaultTooltip()
                .setName(status(claim))
                .setLore(maintenanceDetails(claim)));
    }

    @Override
    public void onTick() {
        super.onTick();
        if (isOpen() && ++displayTicks % 20 == 0) refreshStatus();
    }

    public static Component status(Claim claim) {
        return Component.translatable(claim.maintenanceRule().isExempt() ? "text.goml.maintenance.exempt"
                : claim.isProtectionActive() ? "text.goml.maintenance.active" : "text.goml.maintenance.overdue")
                .withStyle(claim.maintenanceRule().isExempt() ? ChatFormatting.GRAY
                        : claim.isProtectionActive() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .withStyle(style -> style.withItalic(false));
    }

    public static Component timeRemaining(Claim claim) {
        if (claim.maintenanceRule().isExempt())
            return Component.translatable("text.goml.maintenance.remaining_exempt")
                    .withStyle(ChatFormatting.GRAY).withStyle(style -> style.withItalic(false));
        long ticks = claim.maintenance().isInitialized() ? claim.maintenance().remainingTicks()
                : claim.maintenanceRule().intervalTicks();
        return Component.translatable("text.goml.maintenance.remaining", duration(ticks))
                .withStyle(claim.isProtectionActive() ? ChatFormatting.GOLD : ChatFormatting.RED)
                .withStyle(style -> style.withItalic(false));
    }

    private static Component duration(long ticks) {
        long seconds = (ticks + 19) / 20;
        return Component.translatable("text.goml.maintenance.duration", seconds / 60, seconds % 60)
                .withStyle(ChatFormatting.WHITE).withStyle(style -> style.withItalic(false))
                .append(" ")
                .append(Component.translatable("text.goml.maintenance.ticks", ticks)
                        .withStyle(ChatFormatting.GRAY).withStyle(style -> style.withItalic(false)));
    }

    private static List<Component> maintenanceDetails(Claim claim) {
        var rule = claim.maintenanceRule();
        var lines = new ArrayList<Component>(List.of(
                Component.translatable("text.goml.maintenance.tier",
                        claim.getType().getName().copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.BLUE).withStyle(style -> style.withItalic(false)),
                Component.translatable("text.goml.maintenance.cost",
                        rule.item().getDefaultInstance().getHoverName().copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.YELLOW).withStyle(style -> style.withItalic(false)),
                Component.translatable("text.goml.maintenance.period", duration(rule.intervalTicks()))
                        .withStyle(ChatFormatting.GOLD).withStyle(style -> style.withItalic(false))));
        if (!rule.isExempt()) lines.add(timeRemaining(claim));
        return lines;
    }

    public static List<Component> statusLines(Claim claim) {
        var lines = new ArrayList<>(maintenanceDetails(claim));
        // Full claim information used outside the maintenance menu needs a status line.
        lines.add(status(claim));
        return lines;
    }
}
