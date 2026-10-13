package draylar.goml.ui;

import draylar.goml.api.Claim;
import eu.pb4.sgui.api.ClickType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;

/** Revision-checked base for claim menus, including packets arriving before the next menu tick. */
public class ClaimGui extends CloseAwareGui {
    protected final Claim claim;
    private final long revision;
    private final boolean management;

    public ClaimGui(Claim claim, MenuType<?> type, ServerPlayer player, boolean management) {
        super(type, player, false);
        this.claim = claim;
        this.revision = claim.accessRevision();
        this.management = management;
    }

    protected boolean hasAccess() {
        return !claim.isDestroyed() && revision == claim.accessRevision()
                && player.level() == claim.getWorldInstance(player.level().getServer())
                && (!management || claim.canManage(player));
    }

    @Override
    public boolean open() {
        if (!hasAccess()) return false;
        claim.registerMenu(this);
        return super.open();
    }

    @Override
    public boolean onAnyClick(int index, ClickType type, net.minecraft.world.inventory.ClickType action) {
        if (!hasAccess()) { close(false); return false; }
        return super.onAnyClick(index, type, action);
    }

    @Override
    public void onTick() {
        if (!hasAccess()) close(false);
    }

    @Override
    public void onClose() {
        claim.unregisterMenu(this);
        super.onClose();
    }
}
