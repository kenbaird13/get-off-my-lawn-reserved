package draylar.goml.other;

import me.lucko.fabric.api.permissions.v0.Options;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.OptionalInt;
import java.util.function.Predicate;

/** Optional Lucko permissions support with vanilla command-level fallbacks. */
public final class FabricPermissionBridge {
    private static final boolean IS_LOADED = FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0");
    private static String node(ResourceLocation permission) { return permission.getNamespace() + "." + permission.getPath().replace('/', '.'); }

    public static boolean checkPermission(Player player, ResourceLocation permission, int level) {
        return IS_LOADED ? Permissions.check(player, node(permission), level) : player.hasPermissions(level);
    }
    public static boolean checkPermission(CommandSourceStack source, ResourceLocation permission, int level) {
        return IS_LOADED ? Permissions.check(source, node(permission), level) : source.hasPermission(level);
    }
    public static boolean checkPermission(Player player, ResourceLocation permission, boolean fallback) {
        return IS_LOADED ? Permissions.check(player, node(permission), fallback) : fallback;
    }
    public static boolean checkPermission(CommandSourceStack source, ResourceLocation permission, boolean fallback) {
        return IS_LOADED ? Permissions.check(source, node(permission), fallback) : fallback;
    }
    public static Predicate<CommandSourceStack> require(ResourceLocation permission, int level) {
        return source -> checkPermission(source, permission, level);
    }
    public static Predicate<CommandSourceStack> require(ResourceLocation permission, boolean fallback) {
        return source -> checkPermission(source, permission, fallback);
    }
    public static OptionalInt checkPermissionInteger(Player player, ResourceLocation permission) {
        if (IS_LOADED) {
            var value = Options.get(player, node(permission));
            if (value.isPresent()) {
                try { return OptionalInt.of(Integer.parseInt(value.get())); }
                catch (NumberFormatException ignored) { }
            }
        }
        return OptionalInt.empty();
    }
}
