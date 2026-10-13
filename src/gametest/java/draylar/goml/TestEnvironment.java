package draylar.goml;

import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import draylar.goml.mixin.TestServerServicesAccess;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.storage.LevelResource;

import java.net.Proxy;

/** Initialize services before saved claims are read or startup integrations inspect their owners. */
public final class TestEnvironment implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(TestEnvironment::initializeServices);
    }

    public static void initializeServices(MinecraftServer server) {
        if (server.getProfileCache() == null) {
            var authentication = new YggdrasilAuthenticationService(Proxy.NO_PROXY);
            var services = Services.create(authentication, server.getWorldPath(LevelResource.ROOT).toFile());
            ((TestServerServicesAccess) server).gomlTestSetServices(services);
            GameProfileCache.setUsesAuthentication(false);
        }
    }
}
