package draylar.goml.block.augment;

import draylar.goml.GetOffMyLawn;
import draylar.goml.api.Claim;
import draylar.goml.api.DataKey;
import draylar.goml.block.ClaimAugmentBlock;
import draylar.goml.ui.PagedGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

public class GreeterAugmentBlock extends ClaimAugmentBlock {

    public static final DataKey<String> MESSAGE_KEY = DataKey.ofString(GetOffMyLawn.id("greeter/message"), "Welcome %player on my claim!");

    public GreeterAugmentBlock(Properties settings, String texture) {
        super(settings, texture);
    }

    @Override
    public void onPlayerEnter(Claim claim, Player player) {
        var text = claim.getData(MESSAGE_KEY);

        if (text != null && !text.isBlank()) {
            player.sendSystemMessage(GetOffMyLawn.CONFIG.messagePrefix.mutableText().append(Component.literal(" " + (text
                    .replace("%player", player.getName().getString())
                    .replace("%p", player.getName().getString()))
            ).withStyle(ChatFormatting.GRAY)));
        }
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public void openSettings(Claim claim, ServerPlayer player, @Nullable Runnable closeCallback) {
        if (!claim.canManage(player)) return;
        long revision = claim.accessRevision();
        var currentInput = claim.getData(MESSAGE_KEY);

        var ui = new draylar.goml.ui.CloseAwareAnvilGui(player, false) {
            private boolean hasAccess() { return claim.canManage(player) && claim.accessRevision() == revision; }
            @Override public boolean onAnyClick(int index, eu.pb4.sgui.api.ClickType type, net.minecraft.world.inventory.ClickType action) {
                if (!hasAccess()) { close(false); return false; }
                return super.onAnyClick(index, type, action);
            }
            @Override public void onTick() { if (!hasAccess()) close(false); }
            @Override public void onClose() { claim.unregisterMenu(this); super.onClose(); }
            @Override
            public void onManualClose() {
                if (closeCallback != null && hasAccess()) {
                    closeCallback.run();
                }
            }
        };
        ui.setTitle(Component.translatable("text.goml.gui.input_greeting.title"));
        ui.setDefaultInputValue(currentInput);

        ui.setSlot(1,
                new GuiElementBuilder(Items.SLIME_BALL)
                        .setName(Component.translatable("text.goml.gui.input_greeting.set").withStyle(ChatFormatting.GREEN))
                        .setCallback(() -> {
                            PagedGui.playClickSound(player);
                            claim.setData(MESSAGE_KEY, ui.getInput());
                            player.sendSystemMessage(Component.translatable("text.goml.changed_greeting", Component.literal(ui.getInput()).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GREEN), false);
                        })
        );

        ui.setSlot(2,
                new GuiElementBuilder(Items.STRUCTURE_VOID)
                        .setName(Component.translatable("text.goml.gui.input_greeting.close").withStyle(ChatFormatting.RED))
                        .setCallback(() -> {
                            PagedGui.playClickSound(player);
                            ui.close(closeCallback != null);
                        })
        );

        claim.registerMenu(ui);
        ui.open();
    }
}
