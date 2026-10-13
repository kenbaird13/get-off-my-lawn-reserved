package draylar.goml.ui;

import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;

/** Keeps user close callbacks separate from revision invalidation in SGui 1.6. */
public class CloseAwareGui extends SimpleGui {
    private boolean programmaticClose;
    private boolean closing;

    public CloseAwareGui(MenuType<?> type, ServerPlayer player, boolean includePlayer) {
        super(type, player, includePlayer);
    }

    /** The argument indicates a user close; SGui 1.6 itself uses it for handler removal. */
    @Override public void close(boolean manual) {
        if (closing) return;
        closing = true;
        programmaticClose = !manual;
        try {
            if (isOpen()) super.close(false);
            else if (manual) onClose();
        } finally {
            closing = false;
            programmaticClose = false;
        }
    }

    @Override public void onClose() {
        if (!programmaticClose) onManualClose();
    }

    public void onManualClose() {}
}
