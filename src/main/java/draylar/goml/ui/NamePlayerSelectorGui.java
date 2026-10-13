package draylar.goml.ui;

import draylar.goml.registry.GOMLTextures;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.item.Items;

import static draylar.goml.ui.PagedGui.playClickSound;

public class NamePlayerSelectorGui extends CloseAwareAnvilGui {
    private final Runnable regularClose;
    private final Consumer<GameProfile> playerConsumer;
    private final Predicate<GameProfile> shouldDisplay;
    private boolean ignore = false;
    private GameProfile selectedPlayer;
    private int tick;
    private String currentName;
    private final PagedGui accessSource;

    private boolean hasAccess() { return accessSource == null || accessSource.hasClaimAccess(); }
    private int timer;

    public NamePlayerSelectorGui(ServerPlayer player, Predicate<GameProfile> shouldDisplay, Runnable regularClose, Consumer<GameProfile> playerConsumer) {
        this(player, shouldDisplay, regularClose, playerConsumer, null);
    }

    public NamePlayerSelectorGui(ServerPlayer player, Predicate<GameProfile> shouldDisplay, Runnable regularClose,
                                 Consumer<GameProfile> playerConsumer, PagedGui accessSource) {
        super(player, false);
        this.accessSource = accessSource;
        this.regularClose = regularClose;
        this.playerConsumer = playerConsumer;
        this.shouldDisplay = shouldDisplay;
        this.updateIconInvalid();
        this.setTitle(Component.translatable("text.goml.gui.player_selector.input.title"));
        this.setSlot(2, new GuiElementBuilder(Items.STRUCTURE_VOID)
                .setName(Component.translatable("text.goml.gui.back").withStyle(ChatFormatting.RED))
                .hideDefaultTooltip()
                .setCallback(() -> {
                    playClickSound(this.player);
                    this.close(true);
                }));
        if (hasAccess()) {
            if (accessSource != null && accessSource.boundClaim != null) accessSource.boundClaim.registerMenu(this);
            this.open();
        }
    }

    @Override
    public void onManualClose() {
        if (!ignore && hasAccess()) {
            this.regularClose.run();
        }
    }

    @Override
    public void onTick() {
        if (!hasAccess()) { close(false); return; }
        this.tick++;

        if (this.timer-- == 0 && this.currentName != null) {
            this.updateIcon();
            CompletableFuture.supplyAsync(() -> this.player.level().getServer().getProfileCache().get(this.currentName)).thenAccept((profile) -> {
                this.player.level().getServer().execute(() -> {
                    if (!isOpen() || !hasAccess()) return;
                    if (profile.isPresent()) {
                        this.selectedPlayer = profile.get();
                        if (this.shouldDisplay.test(profile.get())) {
                            this.updateIcon();
                        } else {
                            this.updateIconInvalid();
                        }
                    } else {
                        this.updateIconInvalid();
                    }
                    this.updateBack();
                });
            });
        }
        this.updateBack();
    }

    @Override
    public void onInput(String input) {
        super.onInput(input);
        if (input.equals(this.currentName)) {
            return;
        }
        this.selectedPlayer = null;
        if (input.length() < 3 || input.length() > 16) {
            this.updateIconInvalid();
        } else {
            this.timer = 20;
            this.currentName = input;
            this.updateIcon();
        }
        this.updateBack();
    }

    @Override public boolean onAnyClick(int index, eu.pb4.sgui.api.ClickType type, net.minecraft.world.inventory.ClickType action) {
        if (!hasAccess()) { close(false); return false; }
        return super.onAnyClick(index, type, action);
    }

    @Override public void onClose() {
        if (accessSource != null && accessSource.boundClaim != null) accessSource.boundClaim.unregisterMenu(this);
        super.onClose();
    }

    private void updateIconInvalid() {
        this.setSlot(1, new GuiElementBuilder(Items.PLAYER_HEAD)
                .setName(Component.translatable("text.goml.gui.player_selector.input.invalid").withStyle(ChatFormatting.RED))
                        .hideDefaultTooltip()
                .setSkullOwner(GOMLTextures.GUI_QUESTION_MARK)
                .setCallback(() -> this.updateBack())
                .hideDefaultTooltip());
    }

    private void updateIcon() {
        var b = new GuiElementBuilder(Items.PLAYER_HEAD)
                .setName(Component.translatable("text.goml.gui.player_selector.input.select_player", this.selectedPlayer != null ? this.selectedPlayer.getName() : this.currentName).withStyle(ChatFormatting.WHITE));

        if (this.selectedPlayer != null) {
            b.setSkullOwner(this.selectedPlayer, this.player.getServer());
        } else {
            b.setSkullOwner(GOMLTextures.GUI_QUESTION_MARK);
        }

        this.setSlot(1, b
                .hideDefaultTooltip()
                .setCallback(() -> {
                    if (!hasAccess()) { close(false); return; }
                    if (this.selectedPlayer != null) {
                        playClickSound(this.player);
                        this.ignore = true;
                        this.close(true);
                        this.playerConsumer.accept(this.selectedPlayer);
                    } else {
                        this.updateBack();
                    }
                }));
    }

    private void updateBack() {
        if (this.screenHandler != null) {
            this.player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(this.screenHandler.containerId, this.screenHandler.incrementStateId(), 2, this.getSlot(2).getItemStack()));
        }
    }
}
