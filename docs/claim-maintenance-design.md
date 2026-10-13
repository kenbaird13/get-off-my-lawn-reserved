# Claim maintenance design plan

This plan adds periodic item payments to Get Off My Lawn ReServed claims. Each anchor tier has its own payment item and interval. A persistent inventory funds payments; missing a payment disables protection and allows an eligible player to pay and take ownership, subject to placement and overlap restrictions.

This document records the agreed feature behavior and implementation requirements. The intended target is Minecraft 1.21.1. The feature is implemented in this working tree; validation notes are recorded at the end.

## Agreed behavior

- Each payment consumes one item of the type configured for that anchor tier.
- Configuration expresses intervals in minutes. Runtime timing uses server ticks: `period * 60L * 20L`.
- New claims and existing claims without maintenance data receive one full grace interval.
- Breaking and replacing an anchor to obtain another grace interval is intended. This alternative requires active player interaction and risks leaving the land exposed while the claim is removed. It remains subject to normal placement restrictions; it does not preserve the destroyed claim's ownership, trust, or stored inventory in the replacement claim.
- Maintenance continues while claim chunks are unloaded, but pauses when the server stops ticking. Low TPS extends the interval in real time.
- Missing a payment disables claim protection and augment effects without deleting the claim, anchor, boundaries, or inventory.
- An eligible payment on an overdue claim immediately consumes one item, makes the payer its sole owner, clears prior trust, and restores protection for a full interval.
- Treat takeover eligibility as new-claim placement. If the target overlaps another claim, only an existing owner of the overdue claim may pay, and the normal placement overlap rules must also permit it. "Original owner" means any member of the owner set immediately before payment, not a separately recorded historical founder.
- Upgrading a non-overdue anchor to a non-exempt tier resets its countdown to the **full interval of the new tier**, with no immediate payment. It does not preserve the old interval or its remaining fraction. An exempt destination tier pauses maintenance state instead.
- Upgrading an overdue claim does not clear its overdue status. Payment is still required to restore protection when maintenance applies; that payment starts a full interval at the new tier. An exempt destination restores effective protection without clearing the overdue flag.
- Admin anchors are exempt. A tier with a period of `0` is exempt.

## Configuration

Add a `maintenance` section to the existing `config/getoffmylawn.json` file through `GOMLConfig`. This replaces the originally proposed single global item and period with settings for each tier.

```json
{
  "maintenance": {
    "makeshift":  { "cost": "minecraft:diamond", "period": 30 },
    "reinforced": { "cost": "minecraft:diamond", "period": 30 },
    "glistening": { "cost": "minecraft:diamond", "period": 30 },
    "crystal":    { "cost": "minecraft:diamond", "period": 30 },
    "emeradic":   { "cost": "minecraft:diamond", "period": 30 },
    "withered":   { "cost": "minecraft:diamond", "period": 30 }
  }
}
```

These identical defaults preserve the original diamond and 30-minute proposal. Server administrators can set a different item and interval for every tier; specific balancing values have not been chosen.

Use a typed maintenance rule containing an item identifier and a nonnegative integer period. Resolve the rule from `Claim.getType()` through an explicit anchor-to-tier mapping. Resolve item identifiers through the item registry, accepting registered vanilla or modded items and rejecting air or unknown identifiers. Match payments by item type; preserve complete item stack data in storage.

Separate parsing, validation, live activation, and saving. Validate the complete configuration before replacing the live configuration. Invalid reloads must report failure and the offending tier or field where identifiable, retain the last valid live settings, and preserve the submitted file for correction. Do not rewrite the file during parsing or announce a successful reload after failure. Handle malformed JSON, explicit nulls, invalid field types, invalid item identifiers, and I/O errors. Missing entries receive defaults; explicit invalid entries do not silently become defaults. Use long arithmetic when converting minutes to ticks.

On initial startup, invalid individual maintenance rules produce a clear error and leave the affected tiers exempt. If an existing file cannot be loaded as a whole, use default non-maintenance settings with all maintenance exempt, log the failure, and preserve the file. A missing file is normal first-run setup and receives the documented defaults. Save generated or normalized configuration only after successful validation; never overwrite invalid input with startup fallback settings.

## State ownership and persistence

Each `Claim` owns a new `ClaimMaintenance` object. The anchor block entity and menus reference this object rather than maintaining separate copies. This keeps maintenance available when the anchor chunk is unloaded and preserves inventory across upgrades.

| Field | Type | Purpose | Saved |
| --- | --- | --- | --- |
| `inventory` | Nine inventory slots | Holds actual payment item stacks | Yes |
| `remainingTicks` | `long` | Countdown to the next payment | Yes |
| `overdue` | `boolean` | Records a missed payment | Yes |
| `initialized` | `boolean` | Records whether the first non-exempt interval has been initialized | Yes |
| Menu access revision | Counter or equivalent invalidation mechanism | Invalidates stale menus after ownership or lifecycle changes | No |

The claim's existing `Type` field identifies the tier. Its existing `Owners`, `Trusted`, and `TrustedGroups` fields remain authoritative for access. Do not duplicate ownership, the configured item, or configured period inside maintenance state.

Extend `Claim.writeData(ValueOutput)` and `Claim.readData(ValueInput)` with an optional `Maintenance` child. The existing `WorldClaimComponent` already writes every claim to its `Claims` list and restores those claims on load. Keep that save path.

Schematic additional NBT data:

```text
Claims: [
  {
    Type: <existing anchor block identifier>,
    Owners: [...],
    Trusted: [...],
    TrustedGroups: [...],
    Box: {...},
    Maintenance: {
      Version: 1,
      Initialized: true,
      RemainingTicks: 36000L,
      Overdue: false,
      Items: [
        { Slot: 0, Stack: <serialized ItemStack> }
      ]
    }
  }
]
```

Serialize occupied slots using Minecraft's item stack codec, retaining counts and components. Validate slot indices and countdown bounds during loading, but do not clamp a valid saved countdown to the currently configured period: reloads deliberately preserve remaining time even when the period becomes shorter. Absence of the maintenance child denotes an older claim: initialize an empty inventory, non-overdue status, and `initialized = false`. Once validated configuration is available and the tier is non-exempt, assign its full interval and set `initialized = true`. Do not restart the grace interval on subsequent loads.

Claims created or migrated while exempt retain `initialized = false` across saves and restarts until maintenance is first enabled. Existing initialized claims retain their countdown, overdue flag, and initialization marker while exempt. Disabling maintenance must not grant them a new grace interval when it is re-enabled.

Cardinal Components 6.1.3 stores the `goml:claims` component with world/dimension saved data. For Minecraft 1.21.1 the paths are:

```text
<world>/data/cardinal_world_components.dat                     # overworld
<world>/DIM-1/data/cardinal_world_components.dat               # nether
<world>/DIM1/data/cardinal_world_components.dat                # end
<world>/dimensions/<namespace>/<dimension>/data/cardinal_world_components.dat
```

The overworld path was verified against a generated Minecraft 1.21.1 save. Maintenance belongs inside each existing claim record; no separate database or maintenance save file is required. Serialization uses registry-aware NBT codecs through the `NbtInput`/`NbtOutput` helpers and the CCA 6 world callbacks.

Persist through the normal world-save lifecycle. In Cardinal Components 6.1.3, `ComponentPersistentState.isDirty()` always returns true; the real world-save test confirms that inventory and timer mutations are saved without an additional dirty-state notification. Do not write to disk on every tick. Normal shutdown and save/reload preserve the countdown; recovery after a crash is limited to the last successful world save, as with other claim data.

## Tick processing and payment transitions

The existing level-tick callback in `GetOffMyLawn` calls `Claim.tick(world)` for claims in each level. Add maintenance processing at the start of this method, before its `chunksLoadedCount > 0` gate. Retain the loaded-chunk gate for augment processing.

For each non-destroyed claim:

1. Resolve its current tier rule and create missing maintenance state if necessary.
2. If maintenance is exempt, skip initialization of the first interval, countdown, and payment processing, but continue to the effective-protection check for augments.
3. If non-exempt and uninitialized, assign the tier's full interval and mark initialized. Do not decrement it on this initialization call.
4. Otherwise, if non-exempt and not overdue, decrement `remainingTicks` once per call. Overdue claims do not count down or automatically consume stored items.
5. When the countdown in step 4 reaches zero, attempt to consume one matching item from the inventory.
6. On success, set the countdown to the current tier's full interval. On failure, set `overdue = true`, leave the countdown at zero, and run expiration cleanup.

At 20 TPS, 30 minutes is 36,000 ticks. No wall clock, scheduled executor, asynchronous timer, or offline catch-up is used. Depositing stock into an active claim does not reset its countdown.

Define effective protection separately from saved overdue status: a non-destroyed claim protects its area when maintenance is exempt or it is not overdue. After maintenance processing, run augments only when effective protection is active and the existing loaded-chunk gate allows it. An exempt claim with a saved overdue flag therefore still runs augments. This allows maintenance to be temporarily disabled without erasing saved state.

On expiration, remove ongoing augment grants through the existing exit callbacks and clear tracked players. Stop ticking augments and ensure augment-related mixins and login handlers also ignore inactive claims. Ordinary timed effects already applied may finish their vanilla duration; ongoing grants such as flight must be revoked. Emit a claim update on state transitions, not on each timer decrement.

## Claim lookup and protection integration

Keep existing general lookup methods able to find inactive claims. `ClaimAnchorBlockEntity.tick()` currently uses claim lookup to recover its claim; hiding inactive claims there could cause it to destroy an apparently orphaned anchor.

Add explicit active-claim lookup helpers for position and area queries. Audit consumers in `ClaimUtils`, `EventHandlers`, `GomlProtectionProvider`, protection mixins, augment mixins, and flight login/exit handling. Protection checks must ignore effectively inactive claims while retaining overlapping active claims in their inputs. Existing overlap-precedence defects are deferred below; active filtering alone does not repair them.

Do not make `hasPermission()` return true for everyone on an overdue claim: that would grant management permissions, not just disable land protection. Management continues to use explicit ownership and administrator checks.

Retain inactive claims in management lists, anchor recovery, and spatial storage. Show their overdue status in claim information, menus, and relevant placeholders. Notify map integrations on activation, expiration, and ownership changes; mark inactive claims distinctly or omit their protected-area marker while retaining their underlying claim record.

Retain inactive boundaries for new-anchor overlap checks. Players can use the unprotected land, but must take over or remove its anchor before establishing a conflicting claim. Update anchor and augment destruction rules so effectively inactive claims do not retain owner-only break protection. Saved overdue status alone must not weaken an exempt claim's protection.

## Inventory menu and takeover transaction

Add a `ClaimMaintenanceGui` accessed from the anchor menu. It displays nine storage slots plus the tier, payment item, configured interval, countdown, and protection status. The countdown is shown as nominal game time derived from ticks.

While protection is active, owners and administrators may deposit and withdraw. Other players do not receive inventory mutation access. Reject new deposits of items that do not match the current tier's payment item, but allow owners to retrieve items left from earlier rules or upgrades.

For an overdue claim, expose an explicit payment action with server-side eligibility checks and a rejection reason when payment is not allowed. The operation must know which player supplied the item. Consume one matching item directly from that player as the takeover payment, then open normal storage for the new owner to add future payments. Existing stored items remain attached to the claim and transfer with it. Hopper automation is outside this initial feature, since automated deposits cannot identify a new owner.

Share placement eligibility logic with takeover, using the target's existing boundaries and excluding the target itself from collision checks. Check all other intersecting claims, including inactive ones. If any overlap exists, the payer must already own the target; this additional restriction has no admin bypass. Then apply normal placement overlap rules, including `allowClaimOverlappingIfSameOwner`, along with dimension/region blacklists and claim limits. Retain existing placement admin exceptions for those normal rules. Owning a neighboring claim does not make an outsider eligible to take over the target. Payment does not bypass protection from other active claims at the anchor.

Extract the placement claim-limit calculation for both entry points. Count non-admin claims per dimension, including inactive claims; honor dimension-specific permission overrides before global permission overrides and `maxClaimsPerPlayer`, and retain the admin-mode limit bypass. Compute the prospective count without double-counting a target the payer already owns.

Perform takeover on the server thread as one validated operation:

1. Recheck that the claim exists, is overdue, and is subject to maintenance.
2. Recheck placement eligibility, including overlap ownership, collision rules, blacklists, and prospective claim limits as described above.
3. Verify the player has a matching payment item and is authorized to use this interface.
4. Consume exactly one item and remove prior group associations, including unresolved saved group keys.
5. Replace the owner set with the payer and clear trusted players and groups.
6. Clear overdue status and set a full interval for the current tier.
7. Invalidate prior management menus, emit a claim update, and refresh the payer's interface.

All mutation paths must revalidate access, including shift-click, drag, number-key swaps, and callbacks from already-open menus. Audit existing player-list and augment menus as well as the new inventory, including nested player/group selectors and augment-setting callbacks. Serialize competing takeover attempts through the server thread so only the first succeeds and later attempts do not consume items.

Centralize ownership and trust mutations in claim methods that invalidate stale access and emit claim updates. Route commands and menus through these methods instead of directly mutating the exposed owner/trust collections. Revalidate current ownership, admin mode, claim lifecycle, and menu revision at mutation time rather than relying on permissions captured when a menu opened. A takeover batches owner replacement, trust removal, menu invalidation, and notification into one completed transition; callbacks must not observe a partially transferred claim.

## Upgrades and other lifecycle events

**Upgrade:** `UpgradeKitItem` already carries the existing claim through anchor replacement. After a successful type change, retain the same inventory and ownership. If the new tier is non-exempt and the claim is not overdue, assign its full interval without charging and set `initialized = true`. Keep an overdue claim overdue with a zero countdown; upgrading alone cannot recover unpaid protection under a non-exempt rule. If the new tier is exempt, preserve initialized maintenance state and pause it, or retain the uninitialized marker if no first interval has begun. A failed upgrade changes nothing. Explicitly distinguish replacement from destruction even while the claim is temporarily absent from the spatial index: replacement callbacks must neither drop/queue inventory nor destroy the reused claim. Reset the loaded-chunk count before recalculating it for the new boundaries.

**Destruction:** Close menus, remove the claim, and drop remaining inventory exactly once for actual anchor destruction. Make destruction and inventory disposal idempotent at the claim level across repeated block, block-entity, and administrative callbacks. For removal with an unloaded anchor, transfer the inventory into a persisted pending-drop record in the world component before discarding the claim, clearing its inventory so no second path can dispose of the same stacks. Deliver that record once at the origin when the chunk loads, then remove it; do not force-load chunks or silently discard items. Persist pending records through the normal save lifecycle. Upgrades never create pending drops. Exactly-once handling covers normal operation and save/restart; crash recovery retains the last-successful-save limitation described above.

**Anchor lookup:** Make `ClaimUtils.getAnchor()` non-destructive and handle missing or unexpected block entities safely. Menus must handle an unavailable anchor without deleting the claim, losing inventory, or dereferencing null. Keep genuine removal in explicit lifecycle operations; an unloaded or missing block entity returned by a lookup is not itself authorization to destroy a claim.

**Configuration reload:** Preserve the current countdown when the period changes; the next successful payment uses the new interval. The next payment uses the current configured item. Do not discard old inventory items. When a tier becomes exempt, restore effective protection and pause its saved countdown and overdue state. Re-enabling maintenance resumes that state; claims that were overdue become inactive again until paid. Claims created while exempt receive a full interval when maintenance is first enabled. Reconcile augment effects and emit updates when effective protection changes.

**Restart and unload:** Restore maintenance with the claim data, independently of block-entity loading. Resuming a non-exempt overdue claim does not grant a fresh interval or restore protection; exemption still restores effective protection without clearing its saved overdue state. Anchor chunk unloads do not close out or reset maintenance state.

## Deferred issues

The following existing defects found during review are recorded for later work, not added to this feature's implementation scope:

- **Augment cleanup (review item 4):** `Claim.clearTickedPlayers()` invokes exit callbacks without clearing its tracked list. `SelectiveClaimAugmentBlock.openSettings()` loops over occupants but applies/removes effects on the menu operator instead of each occupant. Shared cleanup/effect-reconciliation repairs are deferred. Maintenance still must clear tracked players on expiration, suppress inactive effects, and validate settings-menu access; calling the existing cleanup helper alone does not satisfy those requirements.
- **Protection consistency (review item 5):** `ClaimUtils.canFluidFlow()` compares source claims against themselves; explosion checks can permit destruction based on one overlapping claim; profile-only Common Protection checks use different overlap permission semantics from player checks; and the Common Protection PvP accumulator starts false and skips evaluation. Repairs and a broader overlap-denial precedence policy are deferred. This feature filters inactive claims without treating these existing defects as resolved.

Unload/re-entry and settings-driven augment reconciliation tests, plus fluid, explosion, profile-only, and PvP overlap tests, may expose these deferred defects. Record those failures explicitly as known limitations. The maintenance-specific expiration, eligibility, inventory, and access checks remain required; do not claim unrestricted augment or overlapping-protection correctness while the deferred issues remain.

## Implementation map

Paths below are relative to `src/main/java/draylar/goml/` unless stated otherwise. The implementation includes the new classes listed below.

| File or area | Responsibility |
| --- | --- |
| `config/GOMLConfig.java` | Per-tier rules, defaults, transactional loading/validation, safe startup fallback |
| New `api/ClaimMaintenance.java` | Inventory, countdown, payment helpers, serialization |
| `api/Claim.java` | Own maintenance state, tick it, expose protection state, centralize ownership/trust mutations, handle takeover and idempotent lifecycle transitions |
| `cca/WorldClaimComponent.java` | Preserve claim save integration and persist pending drops for unloaded removal |
| New `ui/ClaimMaintenanceGui.java` | Inventory access, status display, player-attributed takeover |
| `api/ClaimUtils.java`, `item/ClaimAnchorBlockItem.java` | Active-only queries, status text, non-destructive anchor lookup, shared placement/takeover eligibility and limits |
| `EventHandlers.java`, `api/GomlProtectionProvider.java`, `mixin/` | Apply active protection consistently |
| `block/ClaimAnchorBlock.java`, `block/entity/ClaimAnchorBlockEntity.java` | Menu entry, recovery, destruction and item drops |
| `block/ClaimAugmentBlock.java`, `block/augment/` | Inactive behavior and effect cleanup |
| `item/UpgradeKitItem.java` | Preserve storage, distinguish replacement from destruction, reset eligible countdowns, and recalculate chunk counts |
| `ui/`, `other/ClaimCommand.java`, `other/PlaceholdersReg.java`, `compat/webmap/` | Revalidate access, reconcile reloads, report ownership and protection changes |
| `src/main/resources/data/goml/lang/en_us.json`, `README.md` | Interface strings and configuration documentation |

Implement state and serialization first, then ticking and protection, inventory and takeover, lifecycle integration, and finally documentation and validation. The implementation uses redirected vanilla slots with SGui 1.6.1+1.21.1. Its `quickMove` helper does not check source pickup permission itself, so the maintenance menu also guards that entry point explicitly. No additional runtime library is expected.

## Validation and acceptance criteria

- Verify every tier independently selects its configured item and interval; validate missing, invalid, zero, and very large period values.
- Verify malformed JSON, explicit nulls, invalid types, and I/O failures preserve live configuration and submitted files on reload, report failure, and use the documented exemption fallback at startup.
- Verify exact tick boundaries: one decrement per tick, one item per deadline, no premature payment, and no countdown while overdue or exempt.
- Verify breaking and replacing an anchor intentionally grants new-claim grace, remains subject to placement checks, and disposes of old inventory only once.
- Verify non-overdue anchor upgrades to non-exempt tiers reset to the full new interval, preserve inventory, and charge nothing immediately. Overdue upgrades retain overdue status; exempt destination tiers pause maintenance while providing effective protection.
- Round-trip inventory counts and item components, countdown, initialization marker, overdue status, owners, and trusted groups through save/load. Verify old claims receive grace exactly once.
- Test exempt creation/migration followed by restart and enablement: grant the first full interval once. Test an initialized overdue claim becoming exempt and active, then inactive again on re-enablement without a fresh interval.
- Test restart and unloaded chunks without resetting or pausing the countdown incorrectly. Check the physical saved component path and dirty-state behavior.
- Confirm expired claims stop contributing player, entity, explosion, fire, fluid, piston, and augment protection while overlapping active claims remain in protection queries. Record the known overlap-precedence failures listed under deferred issues separately.
- Expire a claim while a player is flying; confirm its flight grant is removed unless another active claim grants it.
- Exercise normal clicks, shift-click, drag, number-key swaps, stale menus including nested selectors/settings, and simultaneous takeover attempts. Failed takeovers must not consume items.
- Test full and partial overlaps with active and inactive claims: reject outsiders even if they own a neighbor or use admin mode; allow existing owners only when normal placement policy also permits payment. Test same-owner overlap configuration, blacklists, per-dimension limits, permission overrides, and already-owned targets against placement semantics.
- Verify takeover clears owners and all trust references, respects limits, and updates claim lists, placeholders, and map integration.
- Verify genuine destruction drops stock once, upgrades drop none, repeated removal callbacks are harmless, and unloaded administrative removal preserves pending drops through save/restart without duplication.
- Verify menu anchor lookups cannot delete claims or inventory when a block entity is unavailable, and repeated upgrades recalculate loaded-chunk counts without accumulating them.
- Verify rule changes preserve stored items and follow the documented pause/resume behavior, including a shorter configured period followed by save/reload without clamping the saved countdown.
- Run focused automated tests for maintenance transitions and serialization, then `./gradlew build` with JDK 21 and in-game checks on the repository's target Minecraft version.

The feature is complete when payment state survives saves, maintenance participation follows effective protection throughout the integration points, and neither inventory operations nor ownership transitions create item duplication, stale access, or orphaned claims. Report the deferred pre-existing augment and protection defects as known limitations rather than implying they have been fixed by maintenance support.


## Implementation and validation notes

Implemented per-tier configuration and transactional reload, persistent nine-slot stock and tick state, active-only protection/augment queries, explicit takeover, revision-checked menus, upgrade preservation, and idempotent removal with persisted pending drops. Pending delivery waits for both terrain and entity data to load; a rejected item spawn remains queued. The feature never loads a chunk solely to deliver stock.

The Fabric GameTest suite covers exact payment boundaries, exemption and migration, configuration validation, stock/state serialization, normal CCA world saves, unloaded ticking, takeover/trust clearing and repeated payment, overlap eligibility, inventory click paths, stale nested menus, actual anchor upgrades/destruction, overlapping flight grants on expiration, and deferred drop save/load/delivery. The save test reads the actual dimension component file after a normal server save; CCA 6 marks its component save data dirty automatically.

Initial 26.3 validation has been superseded by the requested Minecraft 1.21.1 port. Validation on 2026-10-13: `./gradlew build --offline --no-daemon` with JDK 21 completed successfully, including all 16 required GameTests (15 maintenance/menu tests plus a test that loads and crafts every survival recipe). `git diff --check` passed, maintenance translation keys were checked, and the production JAR was verified to include the feature classes without the test classes.

The remapped release JAR also passed a production Fabric bootstrap check on Minecraft 1.21.1 with Java 21, using `--initSettings`. This checked the packaged metadata, bundled dependencies, and remapped mixins outside the development environment. Client rendering and live BlueMap/Dynmap integration have not been manually exercised. Automated coverage does not establish correctness of every third-party protection integration or every overlap scenario. The separate [deferred upstream bugfixes](deferred-upstream-bugfixes.md) remain outstanding, including their shared augment reconciliation and protection precedence defects.


The 1.21.1 gameplay log exposed recipes left in the newer string-ingredient format during the port. All 23 recipe files now use 1.21.1 item/tag ingredient objects. The recipe test verifies the loaded recipe manager contains every survival anchor, augment, upgrade kit, and goggles recipe and that each recipe matches valid ingredients and produces its intended item. The rebuilt JAR was checked for the corrected resources; the new build log contains no GOML recipe parsing errors.
