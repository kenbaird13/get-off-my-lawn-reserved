package draylar.goml.registry;

import draylar.goml.api.Augment;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class GOMLAugments {
    private static final Map<ResourceLocation, Augment> AUGMENTS = new HashMap<>();
    private static final Map<Augment, ResourceLocation> AUGMENT_IDS = new Reference2ObjectOpenHashMap<>();


    @Nullable
    public static Augment get(ResourceLocation identifier) {
        return AUGMENTS.get(identifier);
    }

    public static Augment register(ResourceLocation identifier, Augment augment) {
        AUGMENT_IDS.put(augment, identifier);
        return AUGMENTS.put(identifier, augment);
    }

    public static ResourceLocation getId(Augment augment) {
        return AUGMENT_IDS.get(augment);
    }
}
