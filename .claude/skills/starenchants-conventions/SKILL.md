---
name: starenchants-conventions
description: Use when writing or reviewing ANY code in the StarEnchants repo — the design principles, engine boundaries, threading/Folia rules, config snapshot pattern, extensibility pattern, and the invariants every change must preserve.
---

# StarEnchants design principles

StarEnchants merges EliteEnchantments + EliteArmor into ONE plugin with a
single shared engine, cross-compatible Paper 1.17.1 → 26.1.x + Folia. It is a
deliberately large codebase — optimize for clarity and locality so any one
feature is small, self-describing, and independently testable.

> **This architecture is StarEnchants' own.** It is NOT derived from the
> construction of EliteEnchantments, AdvancedEnchantments, or any other plugin.
> Those decompilations tell us WHAT features exist and HOW they must interact —
> never how to build them. The one external practice we deliberately adopt is
> **real-server (Paper + Folia) integration testing** (`live-server-testing`,
> `matrix-gate`). When the approved design lands, the concrete module/package
> layout is recorded here; until then, follow the principles below.

## Separation principles (the structure serves these)

- **Pure logic away from the platform.** DSL parsing, the effect/condition
  model, interaction-resolution math, and config models contain NO Bukkit
  imports and are unit-tested with hand-computed expectations.
- **Quarantine version-specific code** behind runtime capability detection, so
  the common path compiles against the floor API and is binary-safe across the
  whole range (`paper-cross-version`).
- **A live test harness is part of the build**, not an afterthought
  (`live-server-testing`).

## The unified engine (the core idea)

Custom enchants, armor-set bonuses, and crystals/modifiers are NOT three
systems — they are three **sources of effects** feeding ONE engine:

- An effect engine owns running effects; a condition layer owns gating; a
  trigger router owns mapping Bukkit events → activations.
- A single item-data service is the ONLY place item state is read/written
  (enchants, souls, slots, crystals, set identity). Nothing parses lore for
  state; lore/name are RENDERED from state.
- Everything an armor set or crystal does flows through the same effect engine
  the enchants use (an armor `DISABLE_ENCHANT` is just an engine call).

(Detailed engine/item-data/interaction skills are authored once the design is
approved, so they encode our design rather than anything borrowed.)

## Extensibility pattern (how features are added)

Adding an effect / condition / trigger type / armor-set-effect / item / crystal
is a **small local change**: implement one interface, register it in one place.
No giant switch statements; no editing five files. A new contributor adds a
feature by copying one sibling. This is what keeps a huge codebase legible.

## Invariants (every change must preserve)

- **Folia-correct or it doesn't ship.** All entity/world mutation goes through
  the scheduling abstraction (`folia-scheduling`). No raw
  `Bukkit.getScheduler()` for entity work; no cross-region entity access.
- **Atomic config.** One immutable snapshot swapped by reference; no code path
  reads a torn mix mid-event. Definitions (enchants/sets/effects) are
  precompiled at load — never parse YAML or DSL strings on the hot path
  (`performance-hot-paths`).
- **One item-data layer.** State lives in PersistentDataContainer under
  versioned keys, never in lore.
- **Version-agnostic core, version-specific edges.** Volatile Bukkit surfaces
  (Material/Sound/Particle/Enchantment/Attribute/PotionEffectType/EntityType)
  resolve through boot-time resolvers (`cross-version-item-api`); never
  hard-reference a constant that was renamed in the range.
- **Modernize freely, but make divergences opt-in & documented.** Best
  behavior by default; gameplay-changing differences from EE/EA are toggles.

## Style

- Records over classes for data; immutable. Pure logic gets exhaustive unit
  tests; Bukkit shells stay thin.
- Imports always; never inline fully-qualified names.
- Comments explain WHY and provenance (which original behavior / version
  quirk), not what the code says. Config YAML is exhaustively commented.
- Conventional commits with prose bodies. Commit as you go.
