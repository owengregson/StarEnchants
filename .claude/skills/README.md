# StarEnchants project skills

Hard-won, project-specific knowledge for building StarEnchants (a unified
EliteEnchantments + EliteArmor plugin, Paper 1.17.1 → 26.1.x + Folia). **Check
the relevant skill BEFORE working in its area.**

> The architecture is **self-derived and unique to StarEnchants** — not modeled
> on EliteEnchantments, AdvancedEnchantments, or Mental/StrikeSync. The
> decompiled analysis informs WHAT to build and HOW features interact, never how
> to construct it. The one practice deliberately adopted from elsewhere is
> real-server (Paper + Folia) testing.

## Available now — platform reality & testing

| Skill | Use when… |
| --- | --- |
| `starenchants-conventions` | writing/reviewing ANY code here (principles, engine boundaries, invariants) |
| `paper-cross-version` | code must behave across 1.17.1 → 26.1.x (API selection, mapping flip, toolchains) |
| `cross-version-item-api` | referencing Material/Sound/Particle/Enchantment/Attribute/PotionEffectType/EntityType/ItemMeta across versions |
| `folia-scheduling` | touching entities/blocks/worlds/inventories/timers (must work on Paper AND Folia) |
| `nms-archaeology` | a version behaves unexpectedly — read the server with javap, don't guess |
| `live-server-testing` | writing/debugging the real-server integration suites |
| `matrix-gate` | running or verifying the Paper+Folia test gate |

## Deferred — authored after the unique design is approved

These encode StarEnchants' own architecture, so they are written once the design
is locked (to avoid baking in anything borrowed):

- `effect-engine` — the unified effect/condition/trigger registry; adding an
  effect/condition/type/set-effect; target (PLAYER/TARGET) resolution; DSL.
- `item-data-model` — the single item-state layer (PDC keys for
  enchants/souls/slots/crystals/set-identity), lore/format rendering, transmog.
- `feature-interaction-rules` — precedence/stacking/suppression among features
  (damage & reduction stacking, disable-enchant, soul gating, slots, omni).
- `config-and-migration` — unified schema + atomic snapshots; the AE / EE+EA
  importers.
- `performance-hot-paths` — perf budget & caching for combat/passive/item-data
  hot paths.
