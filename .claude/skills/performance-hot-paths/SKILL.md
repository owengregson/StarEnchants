---
name: performance-hot-paths
description: Use when writing or reviewing code on the combat or item hot path, declaring an effect Affinity, touching the Sink/Dispatcher, the ItemView cache, interning, or anything the ArchUnit/CI lint or the JMH bench guards.
---

# Performance hot paths

The combat hit is an **allocation-light array walk over primitives** (§8), **enforced by a gate,
not by hope**. The loop itself (`WornState.combatAttack/Defense` `int[]` → `Ability[]` → gates →
effects → flush) lives in **effect-engine**; this skill is the *budget* it runs under. Cold paths
(load/reload/menus/commands/item-apply) are unconstrained — parse and allocate freely there.

On the hit: no string ops, no boxing, no item re-read, no map lookups, no YAML/DSL parse. Damage
folds **once** (§6.1) — never `event.setDamage` from an effect; contribute a delta.

## Banned from the inner loop (the lint enforces these — §8, §11)

| Banned in `se-engine` hot-path packages | Use instead |
| --- | --- |
| `Bukkit.getScheduler()` / direct entity mutation | declare an `Affinity`; emit a Sink intent (§3.5–3.6) |
| `new NBTItem`, `ItemStack#clone`, Gson, NBT clone | `ItemView.of(stack)` cache: one hash + lookup (§5.2) |
| `String#split`, regex compile, YAML/DSL parse, map lookups | compiled at load; dense-int indices at runtime |
| string ops / boxing in conditions or effect args | thread-local primitive `FactBuffer` by slot (§3.4) |

**Interning:** every name (enchant/group/world/material/potion/sound) is a **dense int at
runtime**; stable strings exist **only at the PDC boundary** (§5.3, §8). See **item-data-model**.

## Read once, resolve once

- **Item read** = one content-hash + cache lookup; miss = one compact decode. Key is
  **content-hash + generation counter** (collision-safe, bumped on reload) — NOT ItemMeta
  identity, which misses (copy-on-write) and can alias (§5.2). A helmet hit 20×/sec decodes once.
- **Worn-set resolve** runs **once per equip event, never per hit**; the result is immutable,
  multi-set, pre-flattened per direction — set/omni/crystal cost **nothing** per hit (§5.5). The
  victim path reads only this snapshot, never the live `ItemStack`.

## Scheduler-hop budget (Affinity — §3.6)

Declared `Affinity` folds to ability level at compile time (the SPI rule is in **effect-engine**);
the per-hop *cost* is the perf concern here:

| Affinity | Hops (Paper + Folia) |
| --- | --- |
| `CONTEXT_LOCAL` (DAMAGE, REDUCTION, POTION:self, MESSAGE, SOUND) | **0** — inline on the firing region thread |
| `TARGET_ENTITY` / `REGION` / `AOE` (cross-region, AoE) | **~1 per distinct target thread**, batched |
| defense-side victim mutation (heal-on-hit, dodge, warp) | **1 hop on Folia** — stated honestly, not "zero" |

Dynamic victim facts (`%victim health%`, pose) are captured at event entry on the firing region or
from the immutable `WornState`; **never** a live cross-region victim read (§3.4). See **folia-scheduling**.

Deferred/cross-region intents must **snapshot the primitives they need into an immutable carrier
before the plan flushes**, so a pooled intent is never aliased after `run` returns (§3.6).

## Enforcement — the number is the spec (§8, §11)

- **ArchUnit / CI lint** rejects every banned symbol above inside the hot-path packages.
- A **JMH bench** asserts ~0 steady-state allocation on the per-hit pipeline **and** a throughput
  floor; a regression fails the build. Verify on the real matrix too — see **matrix-gate**.

Effects emit intents and never schedule, so an author cannot write a Folia or allocation bug — see
**effect-engine**. Interaction folds (damage, suppression) run once on single-thread-owned scratch
— see **feature-interaction-rules**. General invariants: **starenchants-conventions**.
