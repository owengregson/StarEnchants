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
`lands`, `superiorskyblock`, `factions`, `vault`, `placeholderapi`, `mental`. Integration discovery is
read once at boot, so a change here takes effect on the next server start.

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

## Planned (behaviour to be decided)

mcMMO, anticheat exemptions (for StarEnchants-applied velocity/teleport), ItemsAdder/Oraxen custom item-id
resolution in configs, and a MythicMobs/EliteBosses mob-type condition variable are scoped but await a
decision on their exact behaviour (see `docs/v3-directives.md` §N).
