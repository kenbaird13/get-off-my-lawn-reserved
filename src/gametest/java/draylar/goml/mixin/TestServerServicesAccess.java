package draylar.goml.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minecraft 1.21.1's GameTestServer omits profile services used by claim menus. */
@Mixin(MinecraftServer.class)
public interface TestServerServicesAccess {
    @Accessor("services") @Mutable
    void gomlTestSetServices(Services services);
}
