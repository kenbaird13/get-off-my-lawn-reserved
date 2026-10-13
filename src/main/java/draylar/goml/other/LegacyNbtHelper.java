package draylar.goml.other;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.Util;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Helper methods for handling NBT.
 */
public final class LegacyNbtHelper {
    private LegacyNbtHelper() {
    }

    // Before 1.21.4 this class serialized using int array
    // In 1.21.5 this was changed to Uuids.CODEC (string without dashes)
    public static final Codec<UUID> UUID_CODEC = Codec.withAlternative(UUIDUtil.CODEC, UUIDUtil.AUTHLIB_CODEC);

    @Nullable
    public static GameProfile toGameProfile(CompoundTag nbt) {
        try {
            var id = nbt.hasUUID("Id") ? nbt.getUUID("Id") : Util.NIL_UUID;
            var profile = new GameProfile(id, nbt.getString("Name"));
            var properties = nbt.getCompound("Properties");
            for (var key : properties.getAllKeys()) {
                for (var value : properties.getList(key, Tag.TAG_COMPOUND)) {
                    var property = (CompoundTag) value;
                    profile.getProperties().put(key, new com.mojang.authlib.properties.Property(key,
                            property.getString("Value"), property.contains("Signature") ? property.getString("Signature") : null));
                }
            }
            return profile;
        } catch (IllegalArgumentException ex) { return null; }
    }

    public static CompoundTag writeGameProfile(CompoundTag nbt, GameProfile profile) {
        if (profile.getName() != null) nbt.putString("Name", profile.getName());
        if (profile.getId() != null) nbt.putUUID("Id", profile.getId());
        var properties = new CompoundTag();
        for (var key : profile.getProperties().keySet()) {
            var list = new ListTag();
            for (var property : profile.getProperties().get(key)) {
                var value = new CompoundTag();
                value.putString("Value", property.value());
                if (property.signature() != null) value.putString("Signature", property.signature());
                list.add(value);
            }
            properties.put(key, list);
        }
        if (!properties.isEmpty()) nbt.put("Properties", properties);
        return nbt;
    }

    public static IntArrayTag fromUuid(UUID uuid) {
        return new IntArrayTag(UUIDUtil.uuidToIntArray(uuid));
    }

    public static UUID toUuid(Tag element) {
        if (element.getType() != IntArrayTag.TYPE) {
            throw new IllegalArgumentException(
                    "Expected UUID-Tag to be of type " + IntArrayTag.TYPE.getName() + ", but found " + element.getType().getName() + "."
            );
        } else {
            int[] is = ((IntArrayTag) element).getAsIntArray();
            if (is.length != 4) {
                throw new IllegalArgumentException("Expected UUID-Array to be of length 4, but found " + is.length + ".");
            } else {
                return UUIDUtil.uuidFromIntArray(is);
            }
        }
    }

    public static BlockPos toBlockPos(CompoundTag nbt) {
        return new BlockPos(nbt.getInt("X"), nbt.getInt("Y"), nbt.getInt("Z"));
    }

    public static CompoundTag fromBlockPos(BlockPos pos) {
        CompoundTag nbtCompound = new CompoundTag();
        nbtCompound.putInt("X", pos.getX());
        nbtCompound.putInt("Y", pos.getY());
        nbtCompound.putInt("Z", pos.getZ());
        return nbtCompound;
    }
}
