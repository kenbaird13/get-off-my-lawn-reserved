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
        var server = h.getLevel().getServer();
        if (server.getProfileCache() == null) {
            var authentication = new com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService(java.net.Proxy.NO_PROXY);
            var services = net.minecraft.server.Services.create(authentication,
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile());
            ((draylar.goml.mixin.TestServerServicesAccess) server).gomlTestSetServices(services);
            net.minecraft.server.players.GameProfileCache.setUsesAuthentication(false);
        }
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
            h.assertTrue(target.takeOver(player) == null, "eligible takeover succeeds");
            h.assertTrue(target.getOwners().equals(Set.of(player.getUUID())) && target.getTrusted().isEmpty(), "ownership replaced and trust cleared");
            saved = output(h);
            target.writeData(saved);
            h.assertTrue(input(h, saved.buildResult()).listOrEmpty("TrustedGroups", com.mojang.serialization.Codec.STRING).isEmpty(), "unresolved group keys cleared");
            h.assertValueEqual(target.maintenance().getItem(0).getCount(), 3, "old stock transferred");
            h.assertTrue(target.takeOver(player) != null, "second takeover fails");
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
            h.assertTrue(first.takeOver(player) == null, "owner can pay overlapping claim");
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

}
