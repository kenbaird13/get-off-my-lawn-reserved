# Get Off My Lawn ReServed

*Get Off My Lawn ReServed* is a take on the popular concept of player claims for Survival/Freebuild Fabric servers. 
This mod works fully server side (no client mod required!) while being compatible with major Fabric modpacks

This project is a fork [Get Off My Lawn by Draylar](https://github.com/Draylar/get-off-my-lawn), with focus on improving and building on top of the original.

This working tree targets **Minecraft 1.21.1** with Java 21, Fabric API for 1.21.1, and Polymer 0.9.x for 1.21.1. The built JAR declares that exact Minecraft version. Check `minecraft_version` in `gradle.properties` when building another branch.


# Video Showcase

* English: https://youtu.be/R9-PuMRbNEc

* Polish: https://youtu.be/1V8kh0h3NoU

# Getting started

To get started, you'll have to craft a *Claim Anchor*. Each anchor has a different (configurable by admin) claim radius; after placing one, a box around it will be formed. This box is yours!

* **Makeshift**, default radius of 10
* **Reinforced**, default radius of 25
* **Glistening**, default radius of 50
* **Crystal**, default radius of 75
* **Emeradic**, default radius of 125
* **Withered**, default radius of 200

To see claim areas, you'll have to craft a *Goggles of (Claim) Revealing*

When this item equipped in the helmet, mainhand or offhand slot, claim outlines become visible.

## [Recipes](recipes.md)

## Claim configuration:
To configure your claim, you can interact with the anchor block. A UI will appear that offers several configuration options:
- The player list can be used to add and remove access of players to your claim
- The Augment list, that can be used for checking and configuring active augments
- Claim maintenance, which shows payment status and provides nine storage slots for future payments

## Claim maintenance

By default, each non-admin claim consumes **one diamond every 30 minutes of server ticking**. New claims and claims from older saves receive one full grace interval. Timing continues with unloaded claim chunks, pauses when the server stops ticking, and runs more slowly in real time at low TPS.

Open the anchor's maintenance menu to stock payments. While the claim is protected, owners and players in admin mode can deposit and withdraw; trusted players do not receive storage access. New deposits must match the tier's configured payment item. Old items left after an upgrade or rule change can still be withdrawn.

If stock runs out at a deadline, the claim becomes overdue and loses protection and augment effects. Its boundaries, owners, settings, and inventory remain saved. An eligible player can use **Pay overdue claim and take ownership** to consume one matching item from their own inventory. That player becomes the sole owner, previous player/group trust is cleared, and protection resumes for a full interval. Remaining stock transfers with the claim. Adding stock alone does not recover an overdue claim, and hoppers are not supported.

Payment eligibility follows new-claim placement rules, including claim limits and blacklists. If claims overlap, only an existing owner of the overdue claim may pay, and normal same-owner overlap rules must also allow it. The extra ownership requirement has no admin bypass. Other active claims still protect their areas.

Upgrading a non-overdue anchor to a non-exempt tier starts its full interval without an immediate charge and keeps the inventory. An upgrade does not clear overdue status. Breaking and replacing an anchor for another grace interval is an intended alternative that requires player interaction and leaves the land exposed during replacement. Genuine destruction drops stock once; removal with an unloaded anchor defers the stock drop until its chunk loads.

Add or customize this section in `config/getoffmylawn.json`:

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

`cost` accepts a registered vanilla or modded item identifier, except air. `period` is a nonnegative integer in minutes; `0` exempts that tier. Admin anchors are always exempt. Missing entries use the defaults above. The config file must contain valid JSON.

`/goml admin reload` validates the entire candidate before activating it. A failed reload reports the error and preserves both the current settings and the submitted file. At startup, an invalid maintenance tier is exempt; an unreadable or malformed whole file falls back to default non-maintenance settings with every tier exempt. Existing invalid files are not overwritten.

Changing a period preserves the current countdown; changing the item affects the next payment. Exemption pauses saved state and restores effective protection without clearing overdue status. Re-enabling maintenance resumes that state. Claims first created while exempt receive their initial grace interval when enabled, even after a restart.

Claim menus, information, and the `goml:claim_status` placeholder report maintenance status. `goml:claim_info` includes status, and its templates can reference `${status}`. Inactive claims are omitted from BlueMap/Dynmap protected-area markers and return when protection resumes.

Stock and timers use the existing Cardinal Components world save, including while anchors are unloaded. Normal saves and restarts retain them; crash recovery uses the last successful save. Existing unrelated augment and overlap-protection defects remain tracked in [Deferred upstream bugfixes](docs/deferred-upstream-bugfixes.md).

## Claim upgrades:
To upgrade your claim, place an Anchor Augment next to the core Claim Anchor. Anchor Augments available include:
- Ender Binding: Prevents Endermen from teleporting
- Villager Core: Prevents Zombies from damaging Villagers
- Greeter: MOTD to visitors
- Angelic Aura: Regen to all players inside region
- Withering Seal: Prevents wither status effect
- Force Field: non-whitelisted players get launched out of the claim
- Heaven's Wings: flight
- Lake Spirit's Grace: water breathing, water sight, and better breathing
- Chaos Zone: Strength to all players inside region
- PvP Arena: Allows changing pvp state in claim
- Explosion Controller: Allows toggling explosion protection

## Config:
You can find config file in `./config/getoffmylawn.json`. To reload it, just type `/goml admin reload` in chat/console.

```json5
{
  "makeshiftRadius": 10,                // Radius of makeshift claim
  "reinforcedRadius": 25,               // Radius of reinforced claim
  "glisteningRadius": 50,               // Radius of glistening claim
  "crystalRadius": 75,                  // Radius of crystal claim
  "emeradicRadius": 125,                // Radius of emeradic claim
  "witheredRadius": 200,                // Radius of withered claim
  "claimProtectsFullWorldHeight": false,// Makes claim protect area from bottom of the world to top
  "dimensionBlacklist": [               // Allows to blacklist specific dimensions
    "example:dim"
  ],             
  "regionBlacklist": {                  // Allows to blacklist specific regions
    "example:dim": [
      {
        x1: -200,
        y1: -64,
        z1: -200,
        x2: 200,
        y2: 512,
        z2: 200,
      }
    ]
  },
  "enabledAugments": {                  // Allows to enable/disable augments per their id
    "goml:lake_spirit_grace": true,
    "goml:angelic_aura": true,
    "goml:greeter": true,
    "goml:force_field": true,
    "goml:village_core": true,
    "goml:withering_seal": true,
    "goml:ender_binding": true,
    "goml:heaven_wings": true,
    "goml:chaos_zone": true
  },
  "allowedBlockInteraction": [          // Allows to interact with specific blocks in claim
    "somemod:store"
  ],
  "allowedEntityInteraction": [         // Allows to interact with specific entities in claim
    "minecraft:villager"
  ],
  "messagePrefix": "<dark_gray>[<#a1ff59>GOML</color>]", // Default prefix used in messages
  "placeholderNoClaimInfo": "<gray><italic>Wilderness",
  "placeholderNoClaimOwners": "<gray><italic>Nobody",
  "placeholderNoClaimTrusted": "<gray><italic>Nobody",
  "placeholderClaimCanBuildInfo": "${owners} <gray>(<green>${anchor}</green>)",
  "placeholderClaimCantBuildInfo": "${owners} <gray>(<red>${anchor}</red>)",
  "claimColorSource": "location"        // either "location" or "player" - "location" will chose the color based on the location of the claim (hash of coordinates), "player" will chose the color based on the owner of the claim (hash of UUID).
}
```


## Building and testing

Use JDK 21 and run `./gradlew build`. The build includes server-side Fabric GameTests for maintenance payments, save/load, takeover, inventory interactions, stale menus, upgrades, flight expiration, and deferred drops, and all 23 survival crafting recipes. Run `./gradlew runGameTest` to run the game tests separately. Test code lives in `src/gametest` and is not packaged in the production mod. Install the remapped `build/libs/goml-1.22.0+1.21.1.jar`; the file in `build/devlibs` is for development.

Client presentation and installed BlueMap/Dynmap integrations still need manual gameplay checks. The maintenance design and validation notes are in [the design document](docs/claim-maintenance-design.md).

## License
*Get Off My Lawn ReServed* is available under the MIT license. The project, code, and assets found in this repository are available for free public use (as long as credited).
