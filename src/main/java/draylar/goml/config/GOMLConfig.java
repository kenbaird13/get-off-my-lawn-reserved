package draylar.goml.config;


import com.google.gson.*;
import com.jamieswhiteshirt.rtree3i.Box;
import draylar.goml.GetOffMyLawn;
import draylar.goml.other.WrappedText;
import draylar.goml.registry.GOMLBlocks;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.io.*;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import draylar.goml.block.ClaimAnchorBlock;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class GOMLConfig {
    public static final List<String> MAINTENANCE_TIERS = List.of(
            "makeshift", "reinforced", "glistening", "crystal", "emeradic", "withered");
    public Map<String, MaintenanceRule> maintenance = defaultMaintenance();

    private static Map<String, MaintenanceRule> defaultMaintenance() {
        var rules = new LinkedHashMap<String, MaintenanceRule>();
        MAINTENANCE_TIERS.forEach(tier -> rules.put(tier, new MaintenanceRule()));
        return rules;
    }

    public MaintenanceRule maintenanceRule(ClaimAnchorBlock type) {
        String tier;
        if (type == GOMLBlocks.MAKESHIFT_CLAIM_ANCHOR.getFirst()) tier = "makeshift";
        else if (type == GOMLBlocks.REINFORCED_CLAIM_ANCHOR.getFirst()) tier = "reinforced";
        else if (type == GOMLBlocks.GLISTENING_CLAIM_ANCHOR.getFirst()) tier = "glistening";
        else if (type == GOMLBlocks.CRYSTAL_CLAIM_ANCHOR.getFirst()) tier = "crystal";
        else if (type == GOMLBlocks.EMERADIC_CLAIM_ANCHOR.getFirst()) tier = "emeradic";
        else if (type == GOMLBlocks.WITHERED_CLAIM_ANCHOR.getFirst()) tier = "withered";
        else return EXEMPT_RULE; // Admin and third-party anchors do not opt in implicitly.
        return maintenance.getOrDefault(tier, EXEMPT_RULE);
    }

    private static final MaintenanceRule EXEMPT_RULE = MaintenanceRule.exempt();

    public int makeshiftRadius = 10;
    public int reinforcedRadius = 25;
    public int glisteningRadius = 50;
    public int crystalRadius = 75;
    public int emeradicRadius = 125;
    public int witheredRadius = 200;

    public int maxClaimsPerPlayer = -1;
    public boolean enablePvPinClaims = false;
    public boolean allowDamagingUnnamedHostileMobs = true;
    public boolean allowDamagingNamedHostileMobs = false;

    public boolean claimProtectsFullWorldHeight = false;
    public double claimAreaHeightMultiplier = 1;
    public boolean makeClaimAreaChunkBound = false;
    public boolean allowClaimOverlappingIfSameOwner = false;
    public boolean allowFakePlayersToModify = false;
    public boolean protectAgainstHostileExplosionsActivatedByTrustedPlayers = false;
    public boolean relaxedEntitySourceProtectionCheck = false;
    public Set<ResourceLocation> dimensionBlacklist = new HashSet<>();
    public Map<ResourceLocation, List<Box>> regionBlacklist = new HashMap<>();

    public Map<Block, Boolean> enabledAugments = new HashMap<>();

    public Set<Block> allowedBlockInteraction = new HashSet<>();

    public Set<EntityType<?>> allowedEntityInteraction = Set.of();

    public WrappedText messagePrefix = WrappedText.of("<dark_gray>[<#a1ff59>GOML</>]");

    public WrappedText placeholderNoClaimInfo = WrappedText.of("<gray><italic>Wilderness");
    public WrappedText placeholderNoClaimOwners = WrappedText.of("<gray><italic>Nobody");
    public WrappedText placeholderNoClaimTrusted = WrappedText.of("<gray><italic>Nobody");
    public WrappedText placeholderClaimCanBuildInfo = WrappedText.of("${owners} <gray>(<green>${anchor}</green>)");
    public WrappedText placeholderClaimCantBuildInfo = WrappedText.of("${owners} <gray>(<red>${anchor}</red>)");

    public String claimColorSource = "location";

    public boolean canInteract(Block block) {
        return this.allowedBlockInteraction.contains(block);
    }

    public boolean canInteract(Entity entity) {
        return this.allowedEntityInteraction.contains(entity.getType());
    }

    public boolean isBlacklisted(Level world, Box claimBox) {
        if (this.dimensionBlacklist.contains(world.dimension().location())) {
            return true;
        }

        var list = this.regionBlacklist.get(world.dimension().location());
        if (list != null) {
            for (var box : list) {
                if (box.intersectsClosed(claimBox)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean useLocationForColor() {
        return !usePlayerForColor();
    }

    public boolean usePlayerForColor() {
        return "player".equalsIgnoreCase(claimColorSource);
    }

    public MutableComponent prefix(Component text) {
        return Component.empty().append(messagePrefix.text()).append(Component.literal(" ")).append(text);
    }

    public record LoadResult(GOMLConfig config, List<String> errors) {
        public boolean valid() { return errors.isEmpty(); }
    }

    public static GOMLConfig loadOrCreateConfig() {
        var result = load(configPath(), true);
        result.errors().forEach(error -> GetOffMyLawn.LOGGER.error("Configuration: {}", error));
        return result.config();
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("getoffmylawn.json");
    }

    /** Reads without altering the live config or overwriting an existing file. */
    public static LoadResult load(Path path, boolean startup) {
        try {
            boolean missing = Files.notExists(path);
            var result = parse(missing ? "{}" : Files.readString(path, StandardCharsets.UTF_8), startup);
            if (missing && result.valid()) {
                Files.createDirectories(path.toAbsolutePath().getParent());
                Files.writeString(path, BaseGson.GSON.toJson(result.config()), StandardCharsets.UTF_8);
            }
            return result;
        } catch (Exception e) {
            return failedLoad(e.getMessage(), startup);
        }
    }

    public static LoadResult parse(String json, boolean startup) {
        try {
            var reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(json));
            reader.setLenient(false);
            var root = com.google.gson.internal.Streams.parse(reader).getAsJsonObject();
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) throw new JsonSyntaxException("Trailing JSON data");
            if (root == null) throw new IllegalArgumentException("configuration must be an object");
            var rulesJson = root.remove("maintenance");
            validateFields(root);
            var config = BaseGson.GSON.fromJson(root, GOMLConfig.class);
            for (var field : GOMLConfig.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())) validateValue(field.get(config), field.getName());
            }
            var errors = new ArrayList<String>();
            if (rulesJson != null && !rulesJson.isJsonObject()) {
                errors.add("maintenance must be an object");
                config.maintenance.replaceAll((tier, rule) -> MaintenanceRule.exempt());
            } else {
                var rules = rulesJson == null ? new JsonObject() : rulesJson.getAsJsonObject();
                for (var tier : MAINTENANCE_TIERS) {
                    try {
                        var rule = new MaintenanceRule();
                        if (rules.has(tier)) {
                            var entry = rules.get(tier);
                            if (!entry.isJsonObject()) throw new IllegalArgumentException("rule must be an object");
                            var obj = entry.getAsJsonObject();
                            if (obj.has("cost")) {
                                var cost = obj.get("cost");
                                if (!cost.isJsonPrimitive() || !cost.getAsJsonPrimitive().isString())
                                    throw new IllegalArgumentException("cost must be an item identifier string");
                                rule.cost = cost.getAsString();
                            }
                            if (obj.has("period")) {
                                var period = obj.get("period");
                                if (!period.isJsonPrimitive() || !period.getAsJsonPrimitive().isNumber())
                                    throw new IllegalArgumentException("period must be a nonnegative integer");
                                rule.period = period.getAsBigDecimal().intValueExact();
                            }
                        }
                        rule.validate();
                        config.maintenance.put(tier, rule);
                    } catch (Exception e) {
                        errors.add("maintenance." + tier + ": " + e.getMessage());
                        config.maintenance.put(tier, MaintenanceRule.exempt());
                    }
                }
            }
            for (var augment : GOMLBlocks.AUGMENTS) config.enabledAugments.putIfAbsent(augment, true);
            return new LoadResult(!startup && !errors.isEmpty() ? null : config, List.copyOf(errors));
        } catch (Exception e) {
            return failedLoad(e.getMessage(), startup);
        }
    }

    private static LoadResult failedLoad(String error, boolean startup) {
        var fallback = new GOMLConfig();
        fallback.maintenance.replaceAll((tier, rule) -> MaintenanceRule.exempt());
        return new LoadResult(startup ? fallback : null, List.of(error == null ? "invalid configuration" : error));
    }

    private static void validateFields(JsonObject root) throws ReflectiveOperationException {
        for (var field : GOMLConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || !root.has(field.getName())) continue;
            var value = root.get(field.getName());
            if (value.isJsonNull()) throw new IllegalArgumentException(field.getName() + " cannot be null");
            var type = field.getType();
            boolean valid = true;
            if (type == boolean.class) valid = value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
            else if (type == int.class || type == double.class) {
                valid = value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
                if (valid && type == int.class) value.getAsBigDecimal().intValueExact();
                if (valid && type == double.class) valid = Double.isFinite(value.getAsDouble());
            } else if (type == String.class || type == WrappedText.class) valid = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
            else if (Set.class.isAssignableFrom(type)) valid = value.isJsonArray();
            else if (Map.class.isAssignableFrom(type)) valid = value.isJsonObject() || value.isJsonArray();
            if (!valid) throw new IllegalArgumentException(field.getName() + " has an invalid type");
        }
    }

    private static void validateValue(Object value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " contains a null or invalid value");
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, entry) -> { validateValue(key, field); validateValue(entry, field); });
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(entry -> validateValue(entry, field));
        }
    }

    public static void saveConfig(GOMLConfig config) {
        try {
            Files.writeString(configPath(), BaseGson.GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            GetOffMyLawn.LOGGER.error("Could not save configuration", e);
        }
    }
}
