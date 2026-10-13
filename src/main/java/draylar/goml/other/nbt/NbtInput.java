package draylar.goml.other.nbt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Registry-aware reads from the claim's existing NBT layout. */
public final class NbtInput {
    private final CompoundTag tag;
    private final HolderLookup.Provider registries;
    private final DynamicOps<Tag> ops;

    public NbtInput(CompoundTag tag, HolderLookup.Provider registries) {
        this.tag = tag;
        this.registries = registries;
        this.ops = registries.createSerializationContext(NbtOps.INSTANCE);
    }

    public int getIntOr(String key, int fallback) { return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback; }
    public long getLongOr(String key, long fallback) { return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : fallback; }
    public Optional<Long> getLong(String key) { return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? Optional.of(tag.getLong(key)) : Optional.empty(); }
    public boolean getBooleanOr(String key, boolean fallback) { return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getBoolean(key) : fallback; }
    public String getStringOr(String key, String fallback) { return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback; }
    public Optional<NbtInput> child(String key) { return tag.contains(key, Tag.TAG_COMPOUND) ? Optional.of(new NbtInput(tag.getCompound(key), registries)) : Optional.empty(); }
    public NbtInput childOrEmpty(String key) { return child(key).orElseGet(() -> new NbtInput(new CompoundTag(), registries)); }

    public List<NbtInput> childrenListOrEmpty(String key) {
        var result = new ArrayList<NbtInput>();
        for (var child : tag.getList(key, Tag.TAG_COMPOUND)) result.add(new NbtInput((CompoundTag) child, registries));
        return result;
    }

    public <T> Optional<T> read(String key, Codec<T> codec) {
        var value = tag.get(key);
        return value == null ? Optional.empty() : codec.parse(ops, value).result();
    }

    public <T> List<T> listOrEmpty(String key, Codec<T> codec) {
        return read(key, codec.listOf()).orElseGet(List::of);
    }
}
