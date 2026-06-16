# StarEnchants project skills

Hard-won, project-specific knowledge for building StarEnchants (a unified
EliteEnchantments + EliteArmor plugin, Paper 1.17.1 → 26.1.x + Folia). **Check
the relevant skill BEFORE working in its area.**

> The architecture is **self-derived and unique to StarEnchants** — not modeled
> on EliteEnchantments, AdvancedEnchantments, or Mental/StrikeSync. The
> decompiled analysis informs WHAT to build and HOW features interact, never how
> to construct it. The one practice deliberately adopted from elsewhere is
> real-server (Paper + Folia) testing.

## Platform reality & testing

| Skill | Use when… |
| --- | --- |
| `starenchants-conventions` | writing/reviewing ANY code here (principles, engine boundaries, invariants) |
| `paper-cross-version` | code must behave across 1.17.1 → 26.1.x (API selection, mapping flip, toolchains) |
| `cross-version-item-api` | referencing Material/Sound/Particle/Enchantment/Attribute/PotionEffectType/EntityType/ItemMeta across versions |
| `folia-scheduling` | touching entities/blocks/worlds/inventories/timers (must work on Paper AND Folia) |
| `nms-archaeology` | a version behaves unexpectedly — read the server with javap, don't guess |
| `live-server-testing` | writing/debugging the real-server integration suites |
| `matrix-gate` | running or verifying the Paper+Folia test gate |
| `reference-cache` | needing cached per-version Paper/Folia jars or docs (fetch/decompile) |

## Architecture — the self-derived engine

These encode StarEnchants' own approved architecture (`docs/architecture.md`);
check the relevant one before working in that area of the engine.

| Skill | Use when… |
| --- | --- |
| `effect-engine` | the runtime: systems, the activation pipeline/gate order, effect/condition/trigger/selector kinds, the Ability record, the Sink, Affinity |
| `item-data-model` | item state, the PDC codec, the ItemView content-hash cache, component stores, the WornState resolver, lore rendering, migration |
| `feature-interaction-rules` | two+ features interact — damage/reduction stacking, DISABLE_* suppression, souls, slots, crystals, omni, enchant stamping |
| `config-and-migration` | config/YAML, the DSL & ParamSpec, the compiler, diagnostics, transactional reload, validateContent, the EE/EA/AE migrator |
| `performance-hot-paths` | code on the combat/item hot path, declaring an Affinity, the Sink/cache/interning, or the ArchUnit/JMH lint gate |
