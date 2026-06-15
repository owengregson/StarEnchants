---
name: cross-version-item-api
description: Use when referencing any Bukkit Material, Sound, Particle, Enchantment, PotionEffectType, Attribute, EntityType, or ItemMeta/PDC API that StarEnchants must resolve across 1.17.1 → 26.1.x — the enum→registry renames, legacy aliases in user configs, and the boot-time resolver pattern that absorbs them.
---

# Cross-version item/registry resolution

StarEnchants is content-heavy: every enchant/armor/item config names Materials,
Sounds, Particles, Enchantments, PotionEffectTypes, and EntityTypes as STRINGS.
Those enums were renamed (and several became registry-backed interfaces) across
the range, and EE/EA/AE configs use the OLD names. **Never hard-reference a
constant** (`Particle.VILLAGER_HAPPY`) — it won't compile on the floor or won't
exist at runtime on some version. Resolve every such token by NAME through a
resolver that knows the aliases.

## The break table (resolve by name; alias old↔new)

| Surface | Break | Examples (config name → modern) |
| --- | --- | --- |
| `Enchantment` | enum→abstract class (registry-backed); renamed at **1.20.5** | `DAMAGE_ALL`→sharpness, `PROTECTION_ENVIRONMENTAL`→protection, `DURABILITY`→unbreaking, `ARROW_DAMAGE`→power, `LOOT_BONUS_MOBS`→looting |
| `PotionEffectType` | registry-backed; renamed at **1.20.5** | `CONFUSION`→nausea, `SLOW`→slowness, `FAST_DIGGING`→haste, `SLOW_DIGGING`→mining_fatigue, `JUMP`→jump_boost, `HEAL`→instant_health |
| `Particle` | renamed at **1.20.5** | `VILLAGER_HAPPY`→happy_villager, `VILLAGER_ANGRY`→angry_villager, `ENCHANTMENT_TABLE`→enchant, `SPELL_MOB`→entity_effect, `SMOKE_NORMAL`→smoke |
| `Attribute` | enum→registry interface at **1.21.3**; `GENERIC_` prefix dropped | `GENERIC_MAX_HEALTH`→max_health, `GENERIC_ATTACK_DAMAGE`→attack_damage |
| `Sound` | enum→interface at **1.21.3** | names mostly stable, but never store the constant — resolve + cache by name |
| `Material` | pre-1.13 legacy aliases appear in configs | `SULPHUR`→gunpowder, `EMPTY_MAP`→map, `INK_SACK`/`INK_SAC`, color/data-value suffixes |
| `EntityType` | a few renames; spawn API changed | resolve by name; check `spawnEntity` overloads |
| `ItemMeta`/PDC | PDC since **1.14** (storage of record; present across our whole range); some enchant helper signatures shifted at the 1.20.5 flip | go through PDC + the Enchantment resolver, not removed helpers |

## The resolver pattern

For each surface, a `platform/<Surface>s` resolver resolves a config token to a
live value ONCE (at load or first use), trying modern spelling → legacy alias →
`Registry` lookup → graceful null with a warn. Resolution happens at config
load, never on the hot path (`performance-hot-paths`).

```java
// modern first, then legacy alias, then registry, then warn-and-skip
PotionEffectType type = PotionEffects.resolve(token); // "CONFUSION" works everywhere
if (type == null) { warnUnknown("potion effect", token); /* skip this effect */ }
```

Build the alias maps from BOTH directions so configs authored on any era load
on any server. The migrator (`config-and-migration`, authored later) reuses
these same alias maps to normalize imported AE/EE/EA configs.

## Rules

- Resolve by name + alias; never compile-time reference a volatile constant.
- Resolve once; cache the resolved object (or a small holder) keyed by token.
- Unknown token = warn with the offending token + source file, skip that one
  effect/item — never crash the load.
- Confirm a genuinely-missing member on a version with `nms-archaeology` before
  assuming an alias is wrong.
