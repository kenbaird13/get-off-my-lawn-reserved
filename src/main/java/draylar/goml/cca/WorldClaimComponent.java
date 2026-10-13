package draylar.goml.cca;

import com.jamieswhiteshirt.rtree3i.ConfigurationBuilder;
import com.jamieswhiteshirt.rtree3i.RTreeMap;
import draylar.goml.api.Claim;
import draylar.goml.api.ClaimBox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import draylar.goml.other.nbt.NbtInput;
import draylar.goml.other.nbt.NbtOutput;
import org.jetbrains.annotations.Nullable;

public class WorldClaimComponent implements ClaimComponent {

    private RTreeMap<ClaimBox, Claim> claims = RTreeMap.create(new ConfigurationBuilder().star().build(), ClaimBox::toBox);
    private final Level world;
    private final List<PendingDrop> pendingDrops = new ArrayList<>();

    private record PendingDrop(BlockPos origin, List<ItemStack> stacks) {}

    public WorldClaimComponent(Level world) {
        this.world = world;
    }

    @Override
    public RTreeMap<ClaimBox, Claim> getClaims() {
        return claims;
    }

    @Override
    public void add(Claim info) {
        this.claims = this.claims.put(info.getClaimBox(), info);
    }

    @Override
    public void remove(Claim info) {
        // A late callback from an old anchor must not remove a newly placed claim at the same box.
        if (this.claims.values().anyMatch(x -> x == info)) this.claims = this.claims.remove(info.getClaimBox());
    }

    @Override
    public void queueDrops(BlockPos origin, List<ItemStack> stacks) {
        var stock = stacks.stream().filter(x -> !x.isEmpty()).map(ItemStack::copy).toList();
        if (stock.isEmpty()) return;
        pendingDrops.add(new PendingDrop(origin.immutable(), stock));
        tickPendingDrops();
    }

    @Override
    public void tickPendingDrops() {
        if (!(world instanceof ServerLevel level)) return;
        var iterator = pendingDrops.iterator();
        while (iterator.hasNext()) {
            var drop = iterator.next();
            if (!level.hasChunkAt(drop.origin()) || !level.areEntitiesLoaded(new ChunkPos(drop.origin()).toLong())) continue;
            for (var stack : drop.stacks()) {
                if (stack.isEmpty()) continue;
                var entity = new ItemEntity(level, drop.origin().getX() + 0.5, drop.origin().getY() + 0.5,
                        drop.origin().getZ() + 0.5, stack.copy());
                entity.setDefaultPickUpDelay();
                // Retain rejected spawns in the persisted queue; accepted stacks cannot be delivered again.
                if (level.addFreshEntity(entity)) stack.setCount(0);
            }
            if (drop.stacks().stream().allMatch(ItemStack::isEmpty)) iterator.remove();
        }
    }

    @Override
    public void readFromNbt(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        readData(new NbtInput(tag, registries));
    }

    @Override
    public void writeToNbt(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        writeData(new NbtOutput(tag, registries));
    }

    public void readData(NbtInput view) {
        pendingDrops.clear();
        for (var drop : view.childrenListOrEmpty("PendingMaintenanceDrops")) {
            var stacks = new ArrayList<ItemStack>();
            drop.listOrEmpty("Items", ItemStack.CODEC).forEach(stacks::add);
            if (!stacks.isEmpty()) pendingDrops.add(new PendingDrop(BlockPos.of(drop.getLongOr("Pos", 0)), stacks));
        }
        this.claims = RTreeMap.create(new ConfigurationBuilder().star().build(), ClaimBox::rtree3iBox);
        var world = this.world.dimension().location();

        var version = view.getIntOr("Version", 0);
        var nbtList = view.childrenListOrEmpty("Claims");

        if (version == 0) {
            nbtList.forEach(child -> {
                ClaimBox box = boxFromTag(child.childOrEmpty("Box"));
                if (box != null) {
                    Claim claimInfo = Claim.readData(this.world.getServer(), child.childOrEmpty("Info"), version);
                    claimInfo.internal_setWorld(world);
                    claimInfo.internal_setClaimBox(box);
                    if (this.world instanceof ServerLevel world1) {
                        claimInfo.internal_updateChunkCount(world1);
                    }
                    claimInfo.internal_enableUpdates();
                    add(claimInfo);
                }
            });
        } else {
            nbtList.forEach(child -> {
                Claim claimInfo = Claim.readData(this.world.getServer(), child, version);
                claimInfo.internal_setWorld(world);
                if (this.world instanceof ServerLevel world1) {
                    claimInfo.internal_updateChunkCount(world1);
                }
                claimInfo.internal_enableUpdates();
                add(claimInfo);
            });
        }
    }

    public void writeData(NbtOutput view) {
        var nbtListClaims = view.childrenList("Claims");
        view.putInt("Version", 1);
        claims.values().forEach(claim -> claim.writeData(nbtListClaims.addChild()));
        // CCA 6's ComponentPersistentState.isDirty() always returns true: the normal world save includes mutations.
        var drops = view.childrenList("PendingMaintenanceDrops");
        for (var drop : pendingDrops) {
            var out = drops.addChild();
            out.putLong("Pos", drop.origin().asLong());
            var items = out.list("Items", ItemStack.CODEC);
            drop.stacks().stream().filter(x -> !x.isEmpty()).forEach(items::add);
        }
    }

    @Nullable
    @Deprecated
    public ClaimBox boxFromTag(NbtInput tag) {
        return ClaimBox.readData(tag, 0);
    }
}
