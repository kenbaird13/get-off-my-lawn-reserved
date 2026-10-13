package draylar.goml.ui;

import eu.pb4.sgui.api.gui.AnvilInputGui;
import net.minecraft.server.level.ServerPlayer;

/** Anvil counterpart of CloseAwareGui. */
public class CloseAwareAnvilGui extends AnvilInputGui {
    private boolean programmaticClose;
    private boolean closing;

    public CloseAwareAnvilGui(ServerPlayer player, boolean includePlayer) {
        super(player, includePlayer);
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
