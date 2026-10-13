package draylar.goml.other.nbt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** Writes the claim's existing NBT layout using the world's registry context. */
public final class NbtOutput {
    private final CompoundTag tag;
    private final HolderLookup.Provider registries;
    private final DynamicOps<Tag> ops;

    public NbtOutput(CompoundTag tag, HolderLookup.Provider registries) {
        this.tag = tag;
        this.registries = registries;
        this.ops = registries.createSerializationContext(NbtOps.INSTANCE);
    }

    public CompoundTag buildResult() { return tag; }
    public void putInt(String key, int value) { tag.putInt(key, value); }
    public void putLong(String key, long value) { tag.putLong(key, value); }
    public void putBoolean(String key, boolean value) { tag.putBoolean(key, value); }
    public void putString(String key, String value) { tag.putString(key, value); }
    public <T> void store(String key, Codec<T> codec, T value) { tag.put(key, codec.encodeStart(ops, value).getOrThrow()); }

    public NbtOutput child(String key) {
        var child = new CompoundTag();
        tag.put(key, child);
        return new NbtOutput(child, registries);
    }

    public ChildList childrenList(String key) {
        var list = new ListTag();
        tag.put(key, list);
        return new ChildList(list);
    }

    public <T> ItemList<T> list(String key, Codec<T> codec) {
        var list = new ListTag();
        tag.put(key, list);
        return new ItemList<>(list, codec);
    }

    public final class ChildList {
        private final ListTag list;
        private ChildList(ListTag list) { this.list = list; }
        public NbtOutput addChild() {
            var child = new CompoundTag();
            list.add(child);
            return new NbtOutput(child, registries);
        }
    }

    public final class ItemList<T> {
        private final ListTag list;
        private final Codec<T> codec;
        private ItemList(ListTag list, Codec<T> codec) { this.list = list; this.codec = codec; }
        public void add(T value) { list.add(codec.encodeStart(ops, value).getOrThrow()); }
    }
}
