# Deferred bugfix candidates for upstream

These six findings concern existing mod code, independently of the proposed claim-maintenance feature. No fixes are implemented by this document. Keep this work separate from maintenance so it can be reviewed and potentially submitted upstream on its own.

Reviewed on 2026-10-13 against local commit `9915f1492c4eef3dfcee70c1e038af110f297ec0` (`Update to 26.3`), targeting Minecraft `26.3-rc-1`. The configured origin is `kenbaird13/get-off-my-lawn-reserved`; the current state of a separate upstream repository has not been checked. Recheck the intended upstream branch before preparing a PR.

The maintenance working tree was subsequently ported to the intended Minecraft 1.21.1 target. The six deferred issues remain in the port; the locations and excerpts below describe the original review baseline, so method names and line numbers can differ. The supplied upstream repository is [Patbox/get-off-my-lawn-reserved](https://github.com/Patbox/get-off-my-lawn-reserved); its `main` ref matched the reviewed commit when checked during the port.

All findings below are confirmed by source inspection. Reproduction scenarios and regression tests are proposed, not executed. In particular, the cleanup finding needs a controlled lifecycle test; it is not a confirmed ordinary-gameplay reproduction. Overlap-policy changes should be explained explicitly to upstream maintainers rather than presented as simple typo fixes.

## 1. Fluid protection compares source claims against themselves

**Location:** [ClaimUtils.java](../src/main/java/draylar/goml/api/ClaimUtils.java), `canFluidFlow`, lines 196–199 at the reviewed commit. Called by [FlowingFluidMixin.java](../src/main/java/draylar/goml/mixin/FlowingFluidMixin.java), `applyFluidFlowEvent`.

The current return expression is:

```java
claimsDest.isEmpty() || claimsCur.anyMatch(x -> claimsCur.anyMatch(y -> x.equals(y)))
```

For a nonempty source selection, an entry matches itself. The destination selection is only checked for emptiness; membership in the destination claims is never compared. Consequently, fluid coming from any claimed position passes this check even when it enters an additional or unrelated protected claim.

**Suggested correction:** compare source membership with destination membership. Recommended policy: allow flow into wilderness, and otherwise require the source to belong to every claim covering the destination. Merely replacing the inner `claimsCur` with `claimsDest` would fix the self-comparison but still allow one shared claim to override another destination claim's boundary. Confirm the intended overlap policy with upstream.

**Proposed tests:**

- Wilderness → wilderness: allowed.
- Wilderness → claim: blocked.
- Same claim → same claim: allowed.
- Claim → wilderness: allowed.
- Source in A only → destination in B only: blocked under the recommended policy.
- Source in A only → destination in A and B: blocked under the recommended policy; the current code allows it.
- Source in A and B → destination in A and B: allowed.

Use unit/integration fixtures for membership combinations. For an in-game overlap scenario, enable `allowClaimOverlappingIfSameOwner`, place partially overlapping claims with the same owner, and test fluid crossing from A-only territory into A/B territory.

## 2. Profile-only permission checks allow one claim to override another

**Location:** [GomlProtectionProvider.java](../src/main/java/draylar/goml/api/GomlProtectionProvider.java), `canBreakBlock`, lines 39–48. Compare [ClaimUtils.java](../src/main/java/draylar/goml/api/ClaimUtils.java), `canModify`, lines 305–315.

When a live player is supplied, the provider delegates to `canModify`, which rejects an operation if an applicable claim denies it. When only a profile is supplied, the provider allows the operation if any claim grants permission:

```java
claims.isEmpty() || claims.anyMatch(c -> c.getValue().hasPermission(profile.id()))
```

This inconsistency also propagates to placement and interaction operations that delegate to `canBreakBlock`. It requires an integration using the profile-only API; ordinary player block breaking follows the other branch.

**Suggested correction:** for profile-only requests, require permission in every covering claim, while retaining wilderness access. Do not infer live-player admin mode or event exceptions from a UUID alone.

**Proposed reproduction/test:** create same-owner overlapping claims A and B. Trust a visitor in A but not B. At their intersection, call `canBreakBlock` with the visitor's profile and `player = null`; the current result allows access. Compare with a real-player request, without admin mode or permission-event overrides, which denies it. Also test permission in both claims, neither claim, and wilderness, plus delegating placement/interaction methods.

## 3. One explosion controller can override another claim's protection

**Location:** [ClaimUtils.java](../src/main/java/draylar/goml/api/ClaimUtils.java), `canExplosionDestroy`, lines 202–227. Used by [ServerExplosionMixin.java](../src/main/java/draylar/goml/mixin/ServerExplosionMixin.java) and the Common Protection provider.

In the branch without an attributed player, `anyMatch` allows destruction if any covering claim has an explosion controller set to `DISABLED` (explosion protection disabled). A second covering claim can still have protection enabled or no controller, but does not prevent that allowance.

Player-attributed explosions use an earlier permission-check branch. A creeper targeting a player can also enter that earlier branch depending on configuration, so it is not a reliable reproduction for this finding.

**Suggested correction:** require every covering claim to permit the explosion in this branch. Under this proposed policy, a claim without an explosion controller retains default protection. Preserve the existing attributed-player behavior unless a separate change is deliberately proposed.

**Proposed tests:** invoke the helper with no causing entity, or use a verified unattributed explosion. Test wilderness; a single default claim; a single claim with protection disabled; overlapping claims with conflicting controller settings; one disabled controller overlapping a claim with no controller; and all overlapping controllers disabled. Destruction should be allowed only in wilderness or when all covering claims permit it. Separately verify that attributed-player behavior remains unchanged.

This is an overlap-precedence correction requiring an explicit policy statement in a PR. Same-owner overlaps with different augment settings are sufficient to expose it.

## 4. Common Protection PvP accumulator starts in the rejecting state

**Location:** [GomlProtectionProvider.java](../src/main/java/draylar/goml/api/GomlProtectionProvider.java), `canDamageEntity`, lines 98–114. Compare [ClaimUtils.java](../src/main/java/draylar/goml/api/ClaimUtils.java), the equivalent accumulator at line 282.

The Common Protection path constructs `new MutableBoolean()`, whose initial value is false. Each iteration immediately returns when that value is false, so the arena mode is never evaluated. Whenever the filtered selection contains a PvP arena augment, this path returns false, even for `EVERYONE`.

The corresponding `ClaimUtils` path starts with `new MutableBoolean(true)`. This finding concerns the Common Protection integration, not every PvP event in the mod.

**Suggested correction:** initialize the accumulator to true so the first arena is evaluated and any rejection can stop subsequent evaluation. Preserve the branch's existing mode and admin rules.

**Proposed tests:** query `GomlProtectionProvider.canDamageEntity` for a player target inside a claim with a PvP arena. Verify `EVERYONE`, `DISABLED`, `TRUSTED`, and `UNTRUSTED`, including matching and nonmatching trust combinations. Also check no-claim and no-arena fallbacks, multiple arenas with a rejecting mode, and the existing admin exception. Compare equivalent non-admin cases with `ClaimUtils.canDamageEntity`.

## 5. Augment settings apply effects to the menu operator instead of occupants

**Location:** [SelectiveClaimAugmentBlock.java](../src/main/java/draylar/goml/block/SelectiveClaimAugmentBlock.java), `openSettings`, lines 81–85.

The settings callback loops over occupants as `p` and tests `canApply(claim, p)`, but invokes `removeEffect(player)` and `applyEffect(player)` on the player operating the menu. Other occupants are not reconciled. The operator can be granted or denied an effect based on another occupant's eligibility and iteration order.

Heaven's Wings makes the consequence particularly visible because it grants persistent flight. Other selective augments may refresh timed effects through their regular ticks, which can mask the immediate callback error.

**Suggested correction:** update each occupant `p`, not the menu operator. For flight, account for other qualifying claims before revoking the shared `HEAVEN_WINGS` ability source. [HeavenWingsAugmentBlock.java](../src/main/java/draylar/goml/block/augment/HeavenWingsAugmentBlock.java) already performs an overlap check in `onPlayerExit`; a direct `removeEffect(p)` does not use that check. A variable substitution alone therefore does not settle shared-flight reconciliation.

**Proposed reproduction/tests:** place Heaven's Wings and keep an owner, trusted visitor, and untrusted visitor inside. Change the target mode between everyone, trusted, and untrusted. Check each occupant's flight immediately, without leaving and re-entering. Verify results do not depend on player iteration order. Repeat with another overlapping claim that independently grants flight: withdrawing eligibility in one claim must not revoke the other claim's grant.

## 6. Player cleanup invokes exits without clearing tracked players

**Location:** [Claim.java](../src/main/java/draylar/goml/api/Claim.java), `clearTickedPlayers`, lines 477–488. Callers include zero-loaded-chunk handling and `destroy`; normal ticking updates the list separately at lines 581–583.

The helper invokes augment exit callbacks but leaves `previousTickPlayers` populated. Until a subsequent loaded tick replaces it, repeated cleanup can repeat exit callbacks, and other methods such as `addAugment` can still act on stale occupants. If processing resumes with the same player object present, the entry check can skip that player because the stale list still contains it.

**Suggested correction:** empty the tracked list as part of cleanup, including when no augments remain. Ensure exit callbacks run once per tracked player/augment for that cleanup and repeated cleanup is harmless. Preserve the existing flight overlap behavior when invoking exits.

**Proposed tests:** seed a claim's tracked occupants through a tick, invoke the lifecycle cleanup path in a controlled fixture, and assert exit callbacks occur and tracking is empty. Repeat cleanup and assert no additional exits. Resume processing with the same player object and assert entry callbacks occur again. Test an empty augment collection and destruction separately.

The stale list is confirmed in source; the likelihood and timing of an ordinary unload/re-entry reproduction have not been established. Future maintenance expiration/recovery would make this helper more directly relevant, but the existing defect predates that feature.

## Preparing future upstream PRs

- Recheck all findings against the target upstream branch and record its exact commit and supported Minecraft version.
- Keep maintenance configuration, payment state, takeover behavior, and maintenance-specific active-claim filtering out of these bugfix PRs.
- Consider separate PRs for the PvP initializer, fluid membership check, overlap permission/explosion policy, and augment lifecycle/settings behavior. Keep any shared-flight reconciliation with the augment work it requires.
- Add focused regression tests or a small integration reproduction for each fix. Report actual executed results separately from the proposed scenarios above.
- For overlap behavior, explain why the proposed rule requires every covering claim to permit an action and document the behavior change. Avoid implying that changing `anyMatch` alone is a universally agreed policy.
- Run the build and relevant in-game/API checks on the target branch before submission. No build or gameplay validation has been performed as part of this report.
