package draylar.goml;

import draylar.goml.api.*;
import draylar.goml.cca.WorldClaimComponent;
import draylar.goml.config.GOMLConfig;
import draylar.goml.config.MaintenanceRule;
import draylar.goml.registry.GOMLBlocks;
import draylar.goml.ui.ClaimMaintenanceGui;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import draylar.goml.other.nbt.NbtInput;
import draylar.goml.other.nbt.NbtOutput;

import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;

public class MaintenanceTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
    @Override
    public void invokeTestMethod(GameTestHelper h, java.lang.reflect.Method method) {
        TestEnvironment.initializeServices(h.getLevel().getServer());
        net.fabricmc.fabric.api.gametest.v1.FabricGameTest.super.invokeTestMethod(h, method);
    }

    private static MaintenanceRule rule(int minutes) { return new MaintenanceRule("minecraft:diamond", minutes); }

    private static NbtOutput output(GameTestHelper h) {
        return new NbtOutput(new CompoundTag(), h.getLevel().registryAccess());
    }

    private static draylar.goml.other.nbt.NbtInput input(GameTestHelper h, CompoundTag data) {
        return new NbtInput(data, h.getLevel().registryAccess());
    }

    private static Claim claim(GameTestHelper h, UUID owner, BlockPos pos) {
        var claim = new Claim(h.getLevel().getServer(), Set.of(owner), Set.of(), pos);
        claim.internal_setWorld(h.getLevel().dimension().location());
        claim.internal_setType(GOMLBlocks.MAKESHIFT_CLAIM_ANCHOR.getFirst());
        claim.internal_setClaimBox(new ClaimBox(pos, 1, 1));
        claim.internal_setIcon(new ItemStack(Items.STONE));
        return claim;
    }

    private static void expire(Claim claim) {
        var rule = claim.maintenanceRule();
        claim.maintenance().tick(rule);
        for (long i = 0; i < rule.intervalTicks(); i++) claim.maintenance().tick(rule);
        claim.maintenanceChanged();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void exactDeadlinesAndStock(GameTestHelper h) {
        var stock = new ClaimMaintenance();
        var rule = rule(1);
        stock.setItem(0, new ItemStack(Items.DIAMOND, 2));
        stock.tick(rule);
        h.assertValueEqual(stock.remainingTicks(), 1200L, "full grace interval");
        for (int i = 0; i < 1199; i++) stock.tick(rule);
        h.assertValueEqual(stock.getItem(0).getCount(), 2, "no premature charge");
        stock.tick(rule);
        h.assertValueEqual(stock.getItem(0).getCount(), 1, "one payment at deadline");
        h.assertValueEqual(stock.remainingTicks(), 1200L, "renewed interval");
        stock.setItem(1, new ItemStack(Items.DIAMOND));
        h.assertValueEqual(stock.remainingTicks(), 1200L, "deposit preserves timer");
        stock.clearContent();
        for (int i = 0; i < 1200; i++) stock.tick(rule);
        h.assertTrue(stock.isOverdue(), "empty deadline expires");
        stock.setItem(0, new ItemStack(Items.DIAMOND));
        stock.tick(rule);
        h.assertTrue(stock.isOverdue() && stock.getItem(0).getCount() == 1, "stock cannot auto-takeover overdue claim");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void persistenceAndExemption(GameTestHelper h) {
        var stock = new ClaimMaintenance();
        stock.tick(rule(0));
        var saved = output(h);
        stock.writeData(saved);
        var loaded = new ClaimMaintenance();
        loaded.readData(input(h, saved.buildResult()));
        h.assertFalse(loaded.isInitialized(), "exempt first interval survives restart uninitialized");
        loaded.tick(rule(30));
        loaded.tick(rule(30));
        var diamonds = new ItemStack(Items.DIAMOND, 17);
        diamonds.set(DataComponents.CUSTOM_NAME, Component.literal("Saved components"));
        loaded.setItem(8, diamonds);
        saved = output(h);
        loaded.writeData(saved);
        stock.readData(input(h, saved.buildResult()));
        h.assertTrue(ItemStack.matches(stock.getItem(8), diamonds), "counts, slot and components survive");
        h.assertValueEqual(stock.remainingTicks(), 35999L, "countdown survives");
        stock.tick(rule(1));
        h.assertValueEqual(stock.remainingTicks(), 35998L, "shorter rule does not clamp remaining time");
        stock.tick(rule(0));
        h.assertValueEqual(stock.remainingTicks(), 35998L, "exemption pauses");
        stock.upgraded(rule(2));
        h.assertValueEqual(stock.remainingTicks(), 2400L, "upgrade resets full interval");
        h.assertValueEqual(stock.getItem(8).getCount(), 17, "upgrade is free");
        stock.clearContent();
        for (int i = 0; i < 2400; i++) stock.tick(rule(2));
        stock.upgraded(rule(4));
        h.assertTrue(stock.isOverdue() && stock.remainingTicks() == 0, "overdue upgrade stays overdue");
        saved = output(h);
        stock.writeData(saved);
        loaded.readData(input(h, saved.buildResult()));
        h.assertTrue(loaded.isOverdue(), "overdue survives restart");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void configurationValidation(GameTestHelper h) throws Exception {
        var defaults = GOMLConfig.parse("{}", false);
        h.assertTrue(defaults.valid(), "missing config entries default");
        var types = java.util.List.of(GOMLBlocks.MAKESHIFT_CLAIM_ANCHOR.getFirst(), GOMLBlocks.REINFORCED_CLAIM_ANCHOR.getFirst(),
                GOMLBlocks.GLISTENING_CLAIM_ANCHOR.getFirst(), GOMLBlocks.CRYSTAL_CLAIM_ANCHOR.getFirst(),
                GOMLBlocks.EMERADIC_CLAIM_ANCHOR.getFirst(), GOMLBlocks.WITHERED_CLAIM_ANCHOR.getFirst());
        for (int i = 0; i < types.size(); i++) {
            defaults.config().maintenance.put(GOMLConfig.MAINTENANCE_TIERS.get(i), new MaintenanceRule("goml:goggles", i + 1));
            var selected = defaults.config().maintenanceRule(types.get(i));
            h.assertValueEqual(selected.intervalTicks(), (i + 1) * 1200L, "tier mapping");
            h.assertTrue(selected.item() != Items.AIR, "registered modded payment item");
        }
        h.assertTrue(defaults.config().maintenanceRule(GOMLBlocks.ADMIN_CLAIM_ANCHOR.getFirst()).isExempt(), "admin exempt");
        h.assertValueEqual(rule(Integer.MAX_VALUE).intervalTicks(), 2576980376400L, "long conversion");
        for (var invalid : java.util.List.of("null", "-1", "1.5", "2147483648", "\"30\"")) {
            var result = GOMLConfig.parse("{\"maintenance\":{\"makeshift\":{\"period\":" + invalid + "}}}", false);
            h.assertFalse(result.valid(), "invalid period rejected: " + invalid);
            h.assertTrue(result.config() == null, "invalid reload cannot be activated");
        }
        for (var item : java.util.List.of("minecraft:air", "missing:payment", "Bad ResourceLocation"))
            h.assertFalse(GOMLConfig.parse("{\"maintenance\":{\"makeshift\":{\"cost\":\"" + item + "\"}}}", false).valid(), "invalid item rejected");
        var fallback = GOMLConfig.parse("{\"maintenance\":{\"makeshift\":null}}", true);
        h.assertTrue(fallback.config().maintenance.get("makeshift").isExempt(), "bad startup tier exempt");
        h.assertFalse(fallback.config().maintenance.get("reinforced").isExempt(), "good startup tier retained");
        h.assertTrue(GOMLConfig.parse("{broken", true).config().maintenance.values().stream().allMatch(MaintenanceRule::isExempt), "broken startup file exempts all");
        var path = Files.createTempFile("goml-invalid-config-", ".json");
        try {
            Files.writeString(path, "{broken");
            h.assertFalse(GOMLConfig.load(path, false).valid(), "bad reload fails");
            h.assertValueEqual(Files.readString(path), "{broken", "bad reload preserves input");
        } finally { Files.deleteIfExists(path); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unloadedClaimAndLegacySave(GameTestHelper h) {
        var original = GetOffMyLawn.CONFIG;
        try {
            GetOffMyLawn.CONFIG = new GOMLConfig();
            GetOffMyLawn.CONFIG.maintenance.put("makeshift", rule(1));
            var claim = claim(h, UUID.randomUUID(), new BlockPos(28000000, 70, 28000000));
            h.assertFalse(h.getLevel().hasChunkAt(claim.getOrigin()), "anchor chunk is unloaded");
            var component = new WorldClaimComponent(h.getLevel());
            component.add(claim);
            claim.tick(h.getLevel());
            for (int i = 0; i < 1200; i++) claim.tick(h.getLevel());
            h.assertFalse(claim.isProtectionActive(), "unloaded claim expires");
            var saved = output(h);
            component.writeToNbt(saved.buildResult(), h.getLevel().registryAccess());
            var restored = new WorldClaimComponent(h.getLevel());
            restored.readData(input(h, saved.buildResult()));
            restored.getClaims().values().forEach(c -> h.assertTrue(c.maintenance().isOverdue(), "component restores overdue state"));
            var claimOut = output(h);
            claim.writeData(claimOut);
            var legacy = claimOut.buildResult();
            legacy.remove("Maintenance");
            var migrated = Claim.readData(h.getLevel().getServer(), input(h, legacy), 1);
            migrated.tick(h.getLevel());
            h.assertValueEqual(migrated.maintenance().remainingTicks(), 1200L, "legacy grace");
            migrated.tick(h.getLevel());
            claimOut = output(h);
            migrated.writeData(claimOut);
            var reloaded = Claim.readData(h.getLevel().getServer(), input(h, claimOut.buildResult()), 1);
            h.assertValueEqual(reloaded.maintenance().remainingTicks(), 1199L, "legacy grace not restarted");
            GetOffMyLawn.CONFIG.maintenance.put("makeshift", rule(0));
            h.assertTrue(claim.isProtectionActive() && claim.maintenance().isOverdue(), "exempt overdue claim protects");
            GetOffMyLawn.CONFIG.maintenance.put("makeshift", rule(1));
            h.assertFalse(claim.isProtectionActive(), "reenabling resumes overdue");
        } finally { GetOffMyLawn.CONFIG = original; }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void takeoverClearsTrustAndRejectsSecondPayment(GameTestHelper h) {
        var original = GetOffMyLawn.CONFIG;
        var player = h.makeMockServerPlayerInLevel();
        Claim target = null;
        try {
            GetOffMyLawn.CONFIG = new GOMLConfig();
            GetOffMyLawn.CONFIG.maintenance.put("makeshift", rule(1));
            target = claim(h, UUID.randomUUID(), h.absolutePos(BlockPos.ZERO));
            target.trust(UUID.randomUUID());
            var saved = output(h);
            target.writeData(saved);
            var data = saved.buildResult();
            var groups = new net.minecraft.nbt.ListTag();
            groups.add(net.minecraft.nbt.StringTag.valueOf("missing:old_group"));
            data.put("TrustedGroups", groups);
            target = Claim.readData(h.getLevel().getServer(), input(h, data), 1);
            target.internal_setWorld(h.getLevel().dimension().location());
            GetOffMyLawn.CLAIM.get(h.getLevel()).add(target);
            expire(target);
            target.maintenance().setItem(0, new ItemStack(Items.EMERALD, 3));
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 2));
            h.assertTrue(target.takeOver(player, net.minecraft.world.InteractionHand.MAIN_HAND) == null, "eligible takeover succeeds");
            h.assertTrue(target.getOwners().equals(Set.of(player.getUUID())) && target.getTrusted().isEmpty(), "ownership replaced and trust cleared");
            saved = output(h);
            target.writeData(saved);
            h.assertTrue(input(h, saved.buildResult()).listOrEmpty("TrustedGroups", com.mojang.serialization.Codec.STRING).isEmpty(), "unresolved group keys cleared");
            h.assertValueEqual(target.maintenance().getItem(0).getCount(), 3, "old stock transferred");
            h.assertTrue(target.takeOver(player, net.minecraft.world.InteractionHand.MAIN_HAND) != null, "second takeover fails");
            h.assertValueEqual(player.getInventory().getItem(0).getCount(), 1, "one attributed payment only");
        } finally {
            if (target != null) GetOffMyLawn.CLAIM.get(h.getLevel()).remove(target);
            player.discard();
            GetOffMyLawn.CONFIG = original;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void overlapEligibilityAndActiveLookups(GameTestHelper h) {
        var original = GetOffMyLawn.CONFIG;
        var player = h.makeMockServerPlayerInLevel();
        Claim target = null, neighbor = null;
        try {
            GetOffMyLawn.CONFIG = new GOMLConfig();
            GetOffMyLawn.CONFIG.maintenance.put("makeshift", rule(1));
            GetOffMyLawn.CONFIG.allowClaimOverlappingIfSameOwner = true;
            var pos = h.absolutePos(BlockPos.ZERO);
            target = claim(h, UUID.randomUUID(), pos);
            neighbor = claim(h, player.getUUID(), pos.offset(1, 0, 0));
            var component = GetOffMyLawn.CLAIM.get(h.getLevel());
            component.add(target);
            component.add(neighbor);
            expire(target);
            h.assertTrue(ClaimUtils.getClaimsAt(h.getLevel(), pos).count() == 2, "inactive record retained");
            h.assertTrue(ClaimUtils.getActiveClaimsAt(h.getLevel(), pos).count() == 1, "active overlapping claim retained");
            h.assertTrue(ClaimPlacement.rejection(h.getLevel(), player, target.getType(), target.getClaimBox(), target) != null, "neighbor owner cannot take over target");
            target.addOwner(player.getUUID());
            h.assertTrue(ClaimPlacement.rejection(h.getLevel(), player, target.getType(), target.getClaimBox(), target) == null, "existing owner allowed by same-owner policy");
            GetOffMyLawn.CONFIG.allowClaimOverlappingIfSameOwner = false;
            h.assertTrue(ClaimPlacement.rejection(h.getLevel(), player, target.getType(), target.getClaimBox(), target) != null, "placement overlap config enforced");
            component.remove(neighbor);
            GetOffMyLawn.CONFIG.maxClaimsPerPlayer = 1;
            h.assertTrue(ClaimPlacement.rejection(h.getLevel(), player, target.getType(), target.getClaimBox(), target) == null, "owned target not double-counted");
        } finally {
            if (target != null) GetOffMyLawn.CLAIM.get(h.getLevel()).remove(target);
            if (neighbor != null) GetOffMyLawn.CLAIM.get(h.getLevel()).remove(neighbor);
            player.discard();
            GetOffMyLawn.CONFIG = original;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void deferredDropsSurviveSave(GameTestHelper h) {
        var far = new BlockPos(28000000, 70, 28000000);
        var component = new WorldClaimComponent(h.getLevel());
        component.queueDrops(far, java.util.List.of(new ItemStack(Items.DIAMOND, 9)));
        component.tickPendingDrops();
        var saved = output(h);
        component.writeToNbt(saved.buildResult(), h.getLevel().registryAccess());
        var loaded = new WorldClaimComponent(h.getLevel());
        loaded.readData(input(h, saved.buildResult()));
        var again = output(h);
        loaded.writeData(again);
        h.assertValueEqual(again.buildResult(), saved.buildResult(), "unloaded pending drops round trip without duplication");
        h.assertFalse(h.getLevel().hasChunkAt(far), "queue does not load chunk");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void inventoryAccessAndStaleMenu(GameTestHelper h) {
        var original = GetOffMyLawn.CONFIG;
        var owner = h.makeMockServerPlayerInLevel();
        var target = claim(h, owner.getUUID(), h.absolutePos(BlockPos.ZERO));
        try {
            GetOffMyLawn.CONFIG = new GOMLConfig();
            var menu = new ClaimMaintenanceGui(owner, target);
            var slot = menu.getSlotRedirect(0);
            h.assertTrue(slot.mayPlace(new ItemStack(Items.DIAMOND)), "owner can deposit payment");
            h.assertFalse(slot.mayPlace(new ItemStack(Items.EMERALD)), "wrong item rejected");
            target.maintenance().setItem(0, new ItemStack(Items.EMERALD));
            h.assertTrue(slot.mayPickup(owner), "old configured item can be withdrawn");
            target.removeOwner(owner.getUUID());
            h.assertFalse(menu.isOpen(), "ownership mutation closes menu immediately");
            h.assertFalse(slot.mayPickup(owner), "stale slot cannot withdraw");
            h.assertTrue(menu.quickMove(0).isEmpty(), "stale shift-click cannot withdraw");
            h.assertValueEqual(target.maintenance().getItem(0).getCount(), 1, "stale access preserves stock");
        } finally { target.invalidateMenus(); owner.discard(); GetOffMyLawn.CONFIG = original; }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void inventoryClickPaths(GameTestHelper h) {
        var original = GetOffMyLawn.CONFIG;
        var owner = h.makeMockServerPlayerInLevel();
        var target = claim(h, owner.getUUID(), h.absolutePos(BlockPos.ZERO));
        try {
            GetOffMyLawn.CONFIG = new GOMLConfig();
            var gui = new ClaimMaintenanceGui(owner, target);
            var menu = owner.containerMenu;
            menu.setCarried(new ItemStack(Items.DIAMOND, 4));
            menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, owner);
            h.assertValueEqual(target.maintenance().getItem(0).getCount(), 4, "ordinary deposit");
            h.assertTrue(menu.getCarried().isEmpty(), "deposit removes cursor stack");
            menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, owner);
            h.assertTrue(target.maintenance().isEmpty(), "shift withdrawal");
            h.assertTrue(owner.getInventory().contains(new ItemStack(Items.DIAMOND)), "shift returns stock to player");
            menu.setCarried(new ItemStack(Items.EMERALD, 3));
            menu.clicked(1, 0, net.minecraft.world.inventory.ClickType.PICKUP, owner);
            h.assertTrue(target.maintenance().getItem(1).isEmpty(), "wrong-item click rejected");
            h.assertValueEqual(menu.getCarried().getCount(), 3, "rejection preserves cursor");
            menu.setCarried(ItemStack.EMPTY);
            owner.getInventory().setItem(0, new ItemStack(Items.EMERALD));
            menu.clicked(1, 0, net.minecraft.world.inventory.ClickType.SWAP, owner);
            h.assertTrue(target.maintenance().getItem(1).isEmpty(), "wrong-item number-key swap rejected");
            owner.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 2));
            menu.clicked(1, 0, net.minecraft.world.inventory.ClickType.SWAP, owner);
            h.assertValueEqual(target.maintenance().getItem(1).getCount(), 2, "payment number-key swap accepted");
            menu.setCarried(new ItemStack(Items.DIAMOND, 4));
            menu.clicked(-999, 0, net.minecraft.world.inventory.ClickType.QUICK_CRAFT, owner);
            menu.clicked(2, 1, net.minecraft.world.inventory.ClickType.QUICK_CRAFT, owner);
            menu.clicked(3, 1, net.minecraft.world.inventory.ClickType.QUICK_CRAFT, owner);
            menu.clicked(-999, 2, net.minecraft.world.inventory.ClickType.QUICK_CRAFT, owner);
            h.assertValueEqual(target.maintenance().getItem(2).getCount(), 2, "drag first slot");
            h.assertValueEqual(target.maintenance().getItem(3).getCount(), 2, "drag second slot");
            target.removeOwner(owner.getUUID());
            var visitorGui = new ClaimMaintenanceGui(owner, target);
            h.assertFalse(visitorGui.isOpen(), "non-owner cannot open maintenance inventory");
            owner.containerMenu.clicked(1, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, owner);
            owner.containerMenu.clicked(1, 0, net.minecraft.world.inventory.ClickType.PICKUP, owner);
            owner.containerMenu.clicked(1, 0, net.minecraft.world.inventory.ClickType.SWAP, owner);
            h.assertValueEqual(target.maintenance().getItem(1).getCount(), 2, "visitor cannot extract via click, swap or shift");
            h.assertTrue(visitorGui.quickMove(2).isEmpty(), "visitor direct quickMove denied");
        } finally { target.invalidateMenus(); owner.discard(); GetOffMyLawn.CONFIG = original; }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void upgradeAndDestruction(GameTestHelper h) {
        var original = GetOffMyLawn.CONFIG;
        var owner = h.makeMockServerPlayerInLevel();
        Claim target = null;
        var level = h.getLevel();
        var pos = h.absolutePos(BlockPos.ZERO).offset(0, 3, 0);
        try {
            GetOffMyLawn.CONFIG = new GOMLConfig();
            GetOffMyLawn.CONFIG.reinforcedRadius = 1;
            GetOffMyLawn.CONFIG.glisteningRadius = 2;
            GetOffMyLawn.CONFIG.maintenance.put("makeshift", rule(1));
            GetOffMyLawn.CONFIG.maintenance.put("reinforced", new MaintenanceRule("minecraft:emerald", 2));
            target = claim(h, owner.getUUID(), pos);
            level.setBlockAndUpdate(pos, GOMLBlocks.MAKESHIFT_CLAIM_ANCHOR.getFirst().defaultBlockState());
            ((draylar.goml.block.entity.ClaimAnchorBlockEntity)level.getBlockEntity(pos)).setClaim(target, target.getClaimBox());
            GetOffMyLawn.CLAIM.get(level).add(target);
            target.maintenance().tick(rule(1));
            target.maintenance().tick(rule(1));
            target.maintenance().setItem(0, new ItemStack(Items.DIAMOND, 7));
            var kit = new ItemStack(draylar.goml.registry.GOMLItems.REINFORCED_UPGRADE_KIT);
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, kit);
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
            var result = kit.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(owner, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
            h.assertTrue(result == net.minecraft.world.InteractionResult.SUCCESS, "actual upgrade succeeds");
            h.assertFalse(target.isDestroyed(), "replacement does not destroy reused claim");
            h.assertValueEqual(target.maintenance().remainingTicks(), 2400L, "new tier full interval");
            h.assertValueEqual(target.maintenance().getItem(0).getCount(), 7, "upgrade preserves stock");
            h.assertTrue(((draylar.goml.block.entity.ClaimAnchorBlockEntity)level.getBlockEntity(pos)).getClaim() == target, "new anchor reuses same claim");
            var blocker = claim(h, owner.getUUID(), pos.offset(3, 0, 0));
            GetOffMyLawn.CLAIM.get(level).add(blocker);
            var nextKit = new ItemStack(draylar.goml.registry.GOMLItems.GLISTENING_UPGRADE_KIT);
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, nextKit);
            var failed = nextKit.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(owner, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
            h.assertTrue(failed != net.minecraft.world.InteractionResult.SUCCESS, "overlapping upgrade fails");
            h.assertValueEqual(target.maintenance().remainingTicks(), 2400L, "failed upgrade preserves timer");
            h.assertValueEqual(nextKit.getCount(), 1, "failed upgrade preserves kit");
            GetOffMyLawn.CLAIM.get(level).remove(blocker);
            var bounds = new net.minecraft.world.phys.AABB(pos).inflate(3);
            h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds).isEmpty(), "upgrade drops no stock");
            level.destroyBlock(pos, true);
            target.destroy();
            var count = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds).stream()
                    .filter(x -> x.getItem().is(Items.DIAMOND)).mapToInt(x -> x.getItem().getCount()).sum();
            h.assertValueEqual(count, 7, "genuine destruction drops exactly once");
            h.assertTrue(target.maintenance().isEmpty() && target.isDestroyed(), "destroyed stock emptied");
            h.assertTrue(ClaimUtils.getClaimsWithOrigin(level, pos).isEmpty(), "destroyed claim removed");
        } finally {
            if (target != null) GetOffMyLawn.CLAIM.get(level).remove(target);
            level.removeBlock(pos, false);
            owner.discard(); GetOffMyLawn.CONFIG = original;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void expirationRevokesFlightAndRespectsOverlap(GameTestHelper h) {
        var original = GetOffMyLawn.CONFIG;
        var player = h.makeMockServerPlayerInLevel();
        Claim first = null, second = null;
        try {
            GetOffMyLawn.CONFIG = new GOMLConfig();
            GetOffMyLawn.CONFIG.maintenance.put("makeshift", rule(1));
            var pos = h.absolutePos(BlockPos.ZERO);
            player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            first = claim(h, player.getUUID(), pos);
            second = claim(h, player.getUUID(), pos.offset(1, 0, 0));
            var component = GetOffMyLawn.CLAIM.get(h.getLevel());
            component.add(first); component.add(second);
            var wings = GOMLBlocks.HEAVEN_WINGS.getFirst();
            first.addAugment(pos, wings); second.addAugment(pos.offset(1, 0, 0), wings);
            first.internal_updateChunkCount(h.getLevel()); second.internal_updateChunkCount(h.getLevel());
            first.tick(h.getLevel()); second.tick(h.getLevel());
            var source = draylar.goml.block.augment.HeavenWingsAugmentBlock.HEAVEN_WINGS;
            h.assertTrue(source.grants(player, io.github.ladysnake.pal.VanillaAbilities.ALLOW_FLYING), "flight granted");
            expire(first);
            h.assertTrue(source.grants(player, io.github.ladysnake.pal.VanillaAbilities.ALLOW_FLYING), "overlapping active claim retains flight");
            expire(second);
            h.assertFalse(source.grants(player, io.github.ladysnake.pal.VanillaAbilities.ALLOW_FLYING), "last expiration revokes flight");
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
            GetOffMyLawn.CONFIG.allowClaimOverlappingIfSameOwner = true;
            h.assertTrue(first.takeOver(player, net.minecraft.world.InteractionHand.MAIN_HAND) == null, "owner can pay overlapping claim");
            first.tick(h.getLevel());
            h.assertTrue(source.grants(player, io.github.ladysnake.pal.VanillaAbilities.ALLOW_FLYING), "recovery re-enters tracked player");
        } finally {
            if (first != null) GetOffMyLawn.CLAIM.get(h.getLevel()).remove(first);
            if (second != null) GetOffMyLawn.CLAIM.get(h.getLevel()).remove(second);
            draylar.goml.block.augment.HeavenWingsAugmentBlock.HEAVEN_WINGS.revokeFrom(player, io.github.ladysnake.pal.VanillaAbilities.ALLOW_FLYING);
            player.discard(); GetOffMyLawn.CONFIG = original;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void normalWorldSaveContainsMaintenance(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var pos = new BlockPos(28000032, 70, 28000032);
        var target = claim(h, UUID.randomUUID(), pos);
        var component = GetOffMyLawn.CLAIM.get(level);
        target.maintenance().tick(rule(1));
        target.maintenance().tick(rule(1));
        target.maintenance().setItem(7, new ItemStack(Items.DIAMOND, 11));
        component.add(target);
        try {
            level.getServer().saveEverything(false, true, true);
            var dimension = level.dimension().location();
            var path = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data/cardinal_world_components.dat");
            var nbt = net.minecraft.nbt.NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var stored = input(h, nbt).childOrEmpty("data").childOrEmpty("cardinal_components").childOrEmpty("goml:claims");
            boolean found = false;
            for (var entry : stored.childrenListOrEmpty("Claims")) {
                if (entry.getLongOr("Pos", 0) != pos.asLong()) continue;
                var restored = Claim.readData(level.getServer(), entry, 1);
                h.assertValueEqual(restored.maintenance().remainingTicks(), 1199L, "normal save preserves timer without dirty notification");
                h.assertValueEqual(restored.maintenance().getItem(7).getCount(), 11, "normal save preserves stock");
                found = true;
            }
            h.assertTrue(found, "claim appears in actual CCA world file");
        } finally { component.remove(target); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 2000)
    public void unloadedRemovalAndDeferredDelivery(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(10000, 70, 10000));
        var target = claim(h, UUID.randomUUID(), pos);
        var component = GetOffMyLawn.CLAIM.get(h.getLevel());
        target.maintenance().setItem(0, new ItemStack(Items.DIAMOND, 9));
        component.add(target);
        h.assertFalse(h.getLevel().hasChunkAt(pos), "removal starts unloaded");
        target.destroy();
        target.destroy();
        h.assertTrue(target.maintenance().isEmpty(), "stock transferred out of removed claim");
        h.assertFalse(h.getLevel().hasChunkAt(pos), "destruction does not force load");
        var saved = output(h);
        component.writeToNbt(saved.buildResult(), h.getLevel().registryAccess());
        var restored = new WorldClaimComponent(h.getLevel());
        restored.readData(input(h, saved.buildResult()));
        // Loading terrain alone need not make its entity section visible yet.
        var chunkPos = new net.minecraft.world.level.ChunkPos(pos);
        h.getLevel().getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunkPos, 2, chunkPos);
        h.getLevel().getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        var bounds = new net.minecraft.world.phys.AABB(pos).inflate(2);
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(h.getLevel().hasChunkAt(pos), "chunk terrain ready");
            h.assertTrue(h.getLevel().areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(pos).toLong()), "entity data loaded");
        }).thenExecute(() -> {
            try {
                // The live queue may already have delivered during world ticking. Drain it once,
                // then remove those entities before checking the independent save/reload copy.
                component.tickPendingDrops();
                h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds)
                        .forEach(net.minecraft.world.entity.Entity::discard);
                restored.tickPendingDrops();
                restored.tickPendingDrops();
                int count = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds).stream()
                        .filter(x -> x.getItem().is(Items.DIAMOND)).mapToInt(x -> x.getItem().getCount()).sum();
                h.assertValueEqual(count, 9, "restored queue delivers only once");
                var after = output(h);
                restored.writeData(after);
                for (var pending : input(h, after.buildResult()).childrenListOrEmpty("PendingMaintenanceDrops")) {
                    h.assertFalse(pending.getLongOr("Pos", 0) == pos.asLong(), "delivered record removed before next save");
                }
            } finally {
                h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds)
                        .forEach(net.minecraft.world.entity.Entity::discard);
                h.getLevel().getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunkPos, 2, chunkPos);
            }
        }).thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void nestedMenusLoseMutationAccess(GameTestHelper h) {
        var owner = h.makeMockServerPlayerInLevel();
        var target = claim(h, owner.getUUID(), h.absolutePos(BlockPos.ZERO));
        try {
            var selector = new draylar.goml.ui.GenericPlayerAndGroupSelectionGui(owner, Component.literal("Test"),
                    profile -> true, group -> true, profile -> target.trust(profile.getId()), target::trust, () -> {});
            selector.bindClaim(target, true);
            selector.updateAndOpen();
            var nameMenu = new draylar.goml.ui.NamePlayerSelectorGui(owner, profile -> true, () -> {},
                    profile -> target.trust(profile.getId()), selector);
            target.removeOwner(owner.getUUID());
            h.assertFalse(selector.hasClaimAccess(), "nested selector revision invalidated");
            h.assertFalse(nameMenu.isOpen(), "nested name menu closed");
            target.addOwner(owner.getUUID());
            GOMLBlocks.EXPLOSION_CONTROLLER.getFirst().openSettings(target, owner, null);
            var settingsMenu = owner.containerMenu;
            var key = draylar.goml.block.augment.ExplosionControllerAugmentBlock.KEY;
            var before = target.getData(key);
            target.removeOwner(owner.getUUID());
            settingsMenu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, owner);
            h.assertValueEqual(target.getData(key), before, "stale settings click cannot mutate");
        } finally { target.invalidateMenus(); owner.discard(); }
        h.succeed();
    }
    @GameTest(template = EMPTY_STRUCTURE)
    public void menuCloseCallbacks(GameTestHelper h) {
        var owner = h.makeMockServerPlayerInLevel();
        var target = claim(h, owner.getUUID(), h.absolutePos(BlockPos.ZERO));
        var closes = new java.util.concurrent.atomic.AtomicInteger();
        try {
            var menu = new draylar.goml.ui.PagedGui(owner, closes::incrementAndGet) {
                @Override protected int getPageAmount() { return 1; }
                @Override protected DisplayElement getElement(int id) { return DisplayElement.empty(); }
            };
            menu.bindClaim(target, true);
            menu.refreshOpen();
            menu.close(true);
            h.assertFalse(menu.isOpen(), "user close removes the actual container");
            h.assertValueEqual(closes.get(), 1, "user close calls back once");
            menu.refreshOpen();
            target.removeOwner(owner.getUUID());
            h.assertFalse(menu.isOpen(), "access revocation removes the actual container");
            h.assertValueEqual(closes.get(), 1, "access revocation does not invoke navigation");
        } finally { target.invalidateMenus(); owner.discard(); }
        h.succeed();
    }

    private static net.minecraft.world.InteractionResult clickAnchor(GameTestHelper h, net.minecraft.server.level.ServerPlayer player,
                                                                    BlockPos pos, net.minecraft.world.InteractionHand hand) {
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                net.minecraft.core.Direction.UP, pos, false);
        return player.gameMode.useItemOn(player, h.getLevel(), player.getItemInHand(hand), hand, hit);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void rightClickPaymentUsesHeldHand(GameTestHelper h) {
        var original = GetOffMyLawn.CONFIG;
        var player = h.makeMockServerPlayerInLevel();
        var pos = h.absolutePos(BlockPos.ZERO).offset(0, 3, 0);
        var target = claim(h, UUID.randomUUID(), pos);
        var component = GetOffMyLawn.CLAIM.get(h.getLevel());
        try {
            GetOffMyLawn.CONFIG = new GOMLConfig();
            GetOffMyLawn.CONFIG.maintenance.put("makeshift", rule(1));
            h.getLevel().setBlockAndUpdate(pos, target.getType().defaultBlockState());
            ((draylar.goml.block.entity.ClaimAnchorBlockEntity) h.getLevel().getBlockEntity(pos))
                    .setClaim(target, target.getClaimBox());
            component.add(target);
            target.trust(UUID.randomUUID());
            expire(target);
            player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 4));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 2));
            clickAnchor(h, player, pos, net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(target.maintenance().isOverdue(), "holding the wrong item cannot pay from inventory");
            h.assertTrue(player.containerMenu == player.inventoryMenu, "expired anchor does not open a menu before payment");
            h.assertValueEqual(player.getInventory().getItem(1).getCount(), 4, "unheld payment stock untouched");
            h.assertValueEqual(player.getMainHandItem().getCount(), 2, "wrong held item untouched");
            player.closeContainer();

            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 3));
            var result = clickAnchor(h, player, pos, net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(result.consumesAction() && target.isProtectionActive(), "anchor click restores protection");
            h.assertValueEqual(player.getMainHandItem().getCount(), 2, "main-hand payment consumes exactly one");
            h.assertTrue(target.getOwners().equals(Set.of(player.getUUID())) && target.getTrusted().isEmpty(), "click transfers ownership and clears trust");
            h.assertValueEqual(target.maintenance().remainingTicks(), 1200L, "click starts the full interval");
            var gui = new ClaimMaintenanceGui(player, target);
            h.assertTrue(gui.getSlot(22).getItemStack().is(Items.WHITE_STAINED_GLASS_PANE), "former payment button is a filler");
            gui.close(false);
            clickAnchor(h, player, pos, net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertValueEqual(player.getMainHandItem().getCount(), 2, "active claim click does not pay again");
            h.assertValueEqual(target.maintenance().remainingTicks(), 1200L, "active click preserves countdown");
            player.closeContainer();

            expire(target);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 5));
            var main = clickAnchor(h, player, pos, net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(main == net.minecraft.world.InteractionResult.PASS && target.maintenance().isOverdue(), "empty main hand allows the offhand interaction");
            clickAnchor(h, player, pos, net.minecraft.world.InteractionHand.OFF_HAND);
            h.assertTrue(target.isProtectionActive(), "offhand click restores protection");
            h.assertValueEqual(player.getOffhandItem().getCount(), 4, "offhand payment consumes exactly one");
            h.assertValueEqual(player.getInventory().getItem(1).getCount(), 4, "both clicks leave unheld stock untouched");
        } finally {
            target.invalidateMenus(); component.remove(target); h.getLevel().removeBlock(pos, false);
            player.discard(); GetOffMyLawn.CONFIG = original;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void rejectedBlockPaymentDoesNotPlaceItem(GameTestHelper h) {
        var original = GetOffMyLawn.CONFIG;
        var player = h.makeMockServerPlayerInLevel();
        var pos = h.absolutePos(BlockPos.ZERO).offset(0, 3, 0);
        var target = claim(h, UUID.randomUUID(), pos);
        var neighbor = claim(h, player.getUUID(), pos.offset(2, 0, 0));
        var component = GetOffMyLawn.CLAIM.get(h.getLevel());
        try {
            GetOffMyLawn.CONFIG = new GOMLConfig();
            GetOffMyLawn.CONFIG.allowClaimOverlappingIfSameOwner = true;
            GetOffMyLawn.CONFIG.maintenance.put("makeshift", new MaintenanceRule("minecraft:diamond_block", 1));
            h.getLevel().setBlockAndUpdate(pos, target.getType().defaultBlockState());
            ((draylar.goml.block.entity.ClaimAnchorBlockEntity) h.getLevel().getBlockEntity(pos))
                    .setClaim(target, target.getClaimBox());
            component.add(target); component.add(neighbor);
            expire(target);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_BLOCK, 2));
            clickAnchor(h, player, pos, net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(target.maintenance().isOverdue() && !target.isOwner(player), "neighbor ownership cannot take over overlapping target");
            h.assertValueEqual(player.getMainHandItem().getCount(), 2, "rejected overlap does not consume payment");
            h.assertTrue(h.getLevel().getBlockState(pos.above()).isAir(), "rejected block payment does not fall through into placement");
            component.remove(neighbor);
            GetOffMyLawn.CONFIG.maxClaimsPerPlayer = 0;
            clickAnchor(h, player, pos, net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(target.maintenance().isOverdue(), "claim limit also rejects anchor payment");
            h.assertValueEqual(player.getMainHandItem().getCount(), 2, "limit rejection preserves held stock");
            h.assertTrue(h.getLevel().getBlockState(pos.above()).isAir(), "limit rejection does not place the block item");
        } finally {
            target.invalidateMenus(); component.remove(target); component.remove(neighbor);
            h.getLevel().removeBlock(pos, false); h.getLevel().removeBlock(pos.above(), false);
            player.discard(); GetOffMyLawn.CONFIG = original;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void expiredInventoryOpensOnlyAfterPayment(GameTestHelper h) {
        var original = GetOffMyLawn.CONFIG;
        var oldOwner = h.makeMockServerPlayerInLevel();
        var visitor = h.makeMockServerPlayerInLevel();
        var pos = h.absolutePos(BlockPos.ZERO).offset(0, 3, 0);
        var target = claim(h, oldOwner.getUUID(), pos);
        var component = GetOffMyLawn.CLAIM.get(h.getLevel());
        try {
            GetOffMyLawn.CONFIG = new GOMLConfig();
            GetOffMyLawn.CONFIG.maintenance.put("makeshift", rule(1));
            h.getLevel().setBlockAndUpdate(pos, target.getType().defaultBlockState());
            ((draylar.goml.block.entity.ClaimAnchorBlockEntity) h.getLevel().getBlockEntity(pos))
                    .setClaim(target, target.getClaimBox());
            component.add(target);
            target.maintenance().setItem(0, new ItemStack(Items.STONE, 6));
            expire(target);
            GetOffMyLawn.CONFIG.maintenance.put("makeshift", new MaintenanceRule("minecraft:emerald", 1));
            var message = (net.minecraft.network.chat.contents.TranslatableContents) target.expiredMessage().getContents();
            h.assertValueEqual(message.getKey(), "text.goml.maintenance.pay_help", "expired interaction uses the ownership instruction");
            h.assertValueEqual(((Component) message.getArgs()[0]).getString(), Items.EMERALD.getDefaultInstance().getHoverName().getString(), "instruction names the configured payment item");

            target.openUi(oldOwner);
            target.openUi(visitor);
            h.assertTrue(oldOwner.containerMenu == oldOwner.inventoryMenu && visitor.containerMenu == visitor.inventoryMenu,
                    "expired main menu remains closed for old owner and visitor");
            var ownerMenu = new ClaimMaintenanceGui(oldOwner, target);
            var visitorMenu = new ClaimMaintenanceGui(visitor, target);
            h.assertFalse(ownerMenu.isOpen() || visitorMenu.isOpen(), "expired inventory cannot open directly");
            h.assertFalse(ownerMenu.getSlotRedirect(0).mayPickup(oldOwner), "old owner cannot withdraw before reclaiming");
            h.assertTrue(visitorMenu.quickMove(0).isEmpty(), "unclaimed inventory cannot shift stock");
            h.assertValueEqual(target.maintenance().getItem(0).getCount(), 6, "blocked opens preserve stored stock");

            visitor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 2));
            clickAnchor(h, visitor, pos, net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(target.isOwner(visitor) && target.isProtectionActive(), "payment establishes new owner and protection");
            h.assertTrue(visitor.containerMenu != visitor.inventoryMenu, "successful payment opens maintenance inventory");
            var claimedMenu = new ClaimMaintenanceGui(visitor, target);
            h.assertTrue(claimedMenu.isOpen() && claimedMenu.getSlotRedirect(0).mayPickup(visitor), "new owner can open and use inventory");
            var oldOwnerMenu = new ClaimMaintenanceGui(oldOwner, target);
            h.assertFalse(oldOwnerMenu.isOpen(), "previous owner cannot reopen reclaimed inventory");
        } finally {
            target.invalidateMenus(); component.remove(target); h.getLevel().removeBlock(pos, false);
            oldOwner.discard(); visitor.discard(); GetOffMyLawn.CONFIG = original;
        }
        h.succeed();
    }

    private static Component infoLine(ItemStack icon, String key) {
        return icon.getOrDefault(DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY).lines().stream()
                .filter(line -> line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                        && text.getKey().equals(key))
                .findFirst().orElseThrow(() -> new AssertionError("Missing information: " + key));
    }

    private static long displayedTicks(ItemStack icon) {
        return displayedDurationTicks(infoLine(icon, "text.goml.maintenance.remaining"));
    }

    private static long displayedDurationTicks(Component line) {
        var match = java.util.regex.Pattern.compile("\\((\\d+) ticks\\)").matcher(line.getString());
        if (!match.find()) throw new AssertionError("Missing parenthesized tick count: " + line.getString());
        return Long.parseLong(match.group(1));
    }

    private static void assertGrayTicks(GameTestHelper h, Component line, long ticks) {
        var grayText = new StringBuilder();
        line.visit((style, text) -> {
            if (net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.GRAY).equals(style.getColor()))
                grayText.append(text);
            return java.util.Optional.<Boolean>empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        h.assertValueEqual(grayText.toString(), "(" + ticks + " ticks)", "parenthesized tick count is gray");
    }

    private static void assertMainStatus(GameTestHelper h, ItemStack icon) {
        var keys = icon.getOrDefault(DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY).lines().stream()
                .map(line -> ((net.minecraft.network.chat.contents.TranslatableContents) line.getContents()).getKey()).toList();
        h.assertValueEqual(keys, java.util.List.of("text.goml.position", "text.goml.radius",
                "text.goml.maintenance.remaining", "text.goml.maintenance.active"), "main status has exactly the requested four rows in order");
    }

    private static void assertPlainTooltip(GameTestHelper h, ItemStack icon, net.minecraft.server.level.ServerPlayer player) {
        for (var line : icon.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(h.getLevel()), player,
                net.minecraft.world.item.TooltipFlag.NORMAL)) {
            h.assertFalse(line.visit((style, text) -> style.isItalic() ? java.util.Optional.of(true) : java.util.Optional.empty(),
                    net.minecraft.network.chat.Style.EMPTY).isPresent(), "rendered menu tooltip has no italic text");
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void infoCardsRefreshAndKeepDetails(GameTestHelper h) {
        var mainPlayer = h.makeMockServerPlayerInLevel();
        var storagePlayer = h.makeMockServerPlayerInLevel();
        var pos = h.absolutePos(BlockPos.ZERO).offset(0, 3, 0);
        var target = claim(h, mainPlayer.getUUID(), pos);
        target.addOwner(storagePlayer.getUUID());
        var trusted = UUID.randomUUID();
        target.trust(trusted);
        var profiles = h.getLevel().getServer().getProfileCache();
        profiles.add(new com.mojang.authlib.GameProfile(mainPlayer.getUUID(), "CountdownOwner"));
        profiles.add(new com.mojang.authlib.GameProfile(trusted, "CountdownTrusted"));
        var savedIcon = new ItemStack(Items.STONE);
        savedIcon.set(DataComponents.CUSTOM_NAME, Component.literal("Saved anchor name"));
        savedIcon.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(Component.literal("Saved radius detail"))));
        target.internal_setIcon(savedIcon);
        target.maintenance().renew(target.maintenanceRule());
        target.maintenance().tick(target.maintenanceRule());
        var component = GetOffMyLawn.CLAIM.get(h.getLevel());
        component.add(target);
        target.openUi(mainPlayer);
        var mainMenu = mainPlayer.containerMenu;
        var storage = new ClaimMaintenanceGui(storagePlayer, target);
        var start = target.maintenance().remainingTicks();
        var mainIcon = mainMenu.getSlot(0).getItem();
        var storageIcon = storage.getSlot(13).getItemStack();
        h.assertValueEqual(displayedTicks(mainIcon), start, "main status includes exact remaining ticks");
        h.assertValueEqual(displayedTicks(storageIcon), start, "maintenance status uses the same remaining ticks");
        h.assertTrue(mainIcon.has(DataComponents.HIDE_ADDITIONAL_TOOLTIP), "anchor tooltip cannot add a duplicate radius");
        assertMainStatus(h, mainIcon);
        infoLine(storageIcon, "text.goml.maintenance.tier");
        infoLine(storageIcon, "text.goml.maintenance.cost");
        var interval = infoLine(storageIcon, "text.goml.maintenance.period");
        h.assertValueEqual(displayedDurationTicks(interval), target.maintenanceRule().intervalTicks(), "payment interval includes exact ticks");
        h.assertTrue(interval.getString().contains("30 min 0 sec"), "payment interval also shows normal time");
        assertGrayTicks(h, interval, target.maintenanceRule().intervalTicks());
        for (var icon : java.util.List.of(mainIcon, storageIcon)) {
            h.assertFalse(icon.getOrDefault(DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY).lines().stream()
                    .anyMatch(line -> line.getString().equals("Saved radius detail")), "saved icon lore does not replace the current information");
        }
        for (int slot = 0; slot < 5; slot++) assertPlainTooltip(h, mainMenu.getSlot(slot).getItem(), mainPlayer);
        for (int slot = 9; slot < storage.getSize(); slot++) assertPlainTooltip(h, storage.getSlot(slot).getItemStack(), storagePlayer);
        h.assertValueEqual(((net.minecraft.network.chat.contents.TranslatableContents) storageIcon.getHoverName().getContents()).getKey(), "text.goml.maintenance.active", "maintenance heading retains protection status");
        var entry = mainMenu.getSlot(3).getItem();
        h.assertTrue(entry.getOrDefault(DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY).lines().isEmpty(), "maintenance entry has no detail lore");
        h.assertValueEqual(entry.get(DataComponents.ITEM_NAME).getStyle().getColor(), net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.WHITE), "maintenance entry uses the existing white button color");
        h.assertValueEqual(mainIcon.get(DataComponents.ITEM_NAME).getStyle().getColor(), net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.WHITE), "main information heading uses white");
        var remaining = infoLine(mainIcon, "text.goml.maintenance.remaining");
        h.assertValueEqual(remaining.getStyle().getColor(), net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.GOLD), "countdown label uses gold");
        h.assertTrue(remaining.getString().contains("30 min 0 sec"), "partial seconds round up in the normal-time countdown");
        assertGrayTicks(h, remaining, start);
        assertGrayTicks(h, infoLine(storageIcon, "text.goml.maintenance.remaining"), start);
        var text = (net.minecraft.network.chat.contents.TranslatableContents) remaining.getContents();
        h.assertValueEqual(((Component) text.getArgs()[0]).getStyle().getColor(), net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.WHITE), "normal time uses white");

        h.startSequence().thenIdle(25).thenExecute(() -> {
            try {
                h.assertTrue(mainPlayer.containerMenu == mainMenu && storage.isOpen(), "both status screens remain open");
                var mainTicks = displayedTicks(mainMenu.getSlot(0).getItem());
                var storageTicks = displayedTicks(storage.getSlot(13).getItemStack());
                h.assertTrue(mainTicks < start && storageTicks < start, "both open cards refresh while the claim ticks");
                h.assertValueEqual(mainTicks, storageTicks, "both open cards show the same refreshed value");
                assertGrayTicks(h, infoLine(mainMenu.getSlot(0).getItem(), "text.goml.maintenance.remaining"), mainTicks);
                assertGrayTicks(h, infoLine(storage.getSlot(13).getItemStack(), "text.goml.maintenance.remaining"), storageTicks);
                assertMainStatus(h, mainMenu.getSlot(0).getItem());
                infoLine(storage.getSlot(13).getItemStack(), "text.goml.maintenance.cost");
                infoLine(storage.getSlot(13).getItemStack(), "text.goml.maintenance.period");
                assertPlainTooltip(h, mainMenu.getSlot(0).getItem(), mainPlayer);
                assertPlainTooltip(h, storage.getSlot(13).getItemStack(), storagePlayer);
                expire(target);
                h.assertTrue(mainPlayer.containerMenu == mainPlayer.inventoryMenu && !storage.isOpen(), "expiration still closes both menus");
                var expired = ClaimMaintenanceGui.timeRemaining(target);
                h.assertValueEqual(displayedDurationTicks(expired), 0L, "expired countdown is zero ticks");
                h.assertTrue(expired.getString().contains("0 min 0 sec"), "expired countdown shows zero normal time");
                assertGrayTicks(h, expired, 0L);
                target.internal_setType(GOMLBlocks.ADMIN_CLAIM_ANCHOR.getFirst());
                var exempt = ClaimMaintenanceGui.timeRemaining(target);
                h.assertValueEqual(((net.minecraft.network.chat.contents.TranslatableContents) exempt.getContents()).getKey(), "text.goml.maintenance.remaining_exempt", "exempt claims retain the time-remaining label without a payment countdown");
                h.assertValueEqual(exempt.getStyle().getColor(), net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.GRAY), "exemption uses neutral gray");
            } finally {
                target.invalidateMenus(); component.remove(target); mainPlayer.discard(); storagePlayer.discard();
            }
        }).thenSucceed();
    }

}
