# Integrations

StarEnchants integrates with other plugins **out of the box, from the one jar**. Every integration is
**soft**: it activates only when the other plugin is installed, and *no* other plugin is ever required.
There are no separate add-on jars to install (ADR [0027](decisions/0027-bundled-soft-integrations.md),
superseding [0017](decisions/0017-protection-addon-packaging.md)).

How it works: each bridged plugin's API is `compileOnly` (compiled against, never bundled — the jar contains
**zero** third-party plugin bytecode), and each bridge class loads only when its plugin is detected at boot.
So a server with none of these plugins runs exactly as if the integration code weren't there.

## Toggles

`config.yml` → `integrations`:

```yaml
integrations:
  protection: true    # consult region/claim plugins for the build gate
  economy: true       # use an economy backend for money effects
  named: {}           # per-integration off switch, e.g. named: {worldguard: false}
```

`named.<id>: false` disables one integration; an unlisted id is enabled. Ids: `worldguard`, `towny`,
`lands`, `superiorskyblock`, `factions`, `vault`, `placeholderapi`, `mental`, `nocheatplus`, `grim`,
`vulcan`, `matrix`, `spartan`, `mcmmo`, `mythicmobs`, `itemsadder`, `oraxen`. Integration discovery is read
once at boot, so a change here takes effect on the next server start.

## Protection / region plugins

When present, an enchant effect is allowed to act at a location only if the player could build there. First
deny wins; with no protection plugin, everything is allowed.

| Plugin | Gate |
| --- | --- |
| WorldGuard | the `BUILD` flag |
| Towny | the `BUILD` permission (non-Towny worlds are ungated) |
| Lands | the `BLOCK_PLACE` role-flag (unclaimed land is ungated) |
| SuperiorSkyblock2 | the island `BUILD` privilege (off-island is ungated) |
| Factions (FactionsUUID) | territory access — wilderness/safezone/warzone ungated; a player claim needs at least truce |

A custom region plugin can still register its own `platform.protect.ProtectionProvider` through Bukkit's
`ServicesManager`; bundled and external providers compose together.

## Economy

| Plugin | Use |
| --- | --- |
| Vault | money effects (`MODIFY_MONEY`) deposit/withdraw/transfer through the server's Vault economy backend |

The backend is resolved lazily, so the economy plugin may load in any order. With no economy, money effects
are no-ops. A custom `platform.economy.EconomyProvider` registered through the `ServicesManager` is used when
Vault is absent.

## PlaceholderAPI

- **Expansion** — StarEnchants registers `%starenchants_…%` placeholders:
  - `%starenchants_soulmode%` → `on` / `off`
  - `%starenchants_souls%` → the soul balance of the player's active gem
- **Passthrough** — other plugins' `%…%` placeholders are resolved inside StarEnchants chat messages for the
  target player. (Lore and menu text render from cached state and are intentionally not passthrough targets.)

## Mental (knockback)

The [Mental](decisions/0026-mental-knockback-coordination.md) knockback plugin owns player knockback. When it
is present, StarEnchants' `KNOCKBACK_CONTROL` effect composes onto Mental's computed knockback (via Mental's
`KnockbackApplyEvent`) instead of being overwritten — so a no-knockback / scaled-knockback effect keeps
working under Mental. Folia-correct.

## Anti-cheat

When StarEnchants moves a player itself (a `VELOCITY` or `TELEPORT` effect), it briefly tells the installed
anti-cheat to ignore the motion so engine-applied movement never false-flags.

| Plugin | Mechanism | Verification |
| --- | --- | --- |
| NoCheatPlus | exempts the player from `MOVING` checks for the window (reflective) | known stable API |
| GrimAC | cancels Grim's `FlagEvent` only for a player SE just moved, in a tight window (compiled against GrimAPI) | open-source, compiled |
| Vulcan / Matrix / Spartan | detected + logged — they handle server-applied velocity/teleport natively; use their own bypass if needed | closed/premium, no public exemption API |

The `Mental + StarEnchants + GrimAC` combo is safe by construction: SE composes knockback onto Mental's
authoritative pipeline (ADR [0026](decisions/0026-mental-knockback-coordination.md)) which Grim predicts
natively, and the Grim flag-cancel covers the residual edge.

## mcMMO

StarEnchants applies no combat effects between two players in the same mcMMO party (friendly-fire off). Bound
reflectively to `PartyAPI.inSameParty`.

## MythicMobs

A `%victim.mobtype%` condition variable exposes a victim's MythicMob internal name, so an enchant can react to
a specific custom mob (e.g. `victim.mobtype contains SkeletalKnight`). SE already targets MythicMobs (they are
ordinary living entities); this adds detection. Compiled against the MythicMobs API.

## ItemsAdder / Oraxen

Any config `material:` (item likenesses + menu icons) accepts a custom item id — `itemsadder:<namespace:id>`
or `oraxen:<id>` — resolved to that plugin's custom item as the base; StarEnchants' name/lore/PDC are applied
on top. A plain/vanilla token resolves as a material as before. Compiled against both APIs.
