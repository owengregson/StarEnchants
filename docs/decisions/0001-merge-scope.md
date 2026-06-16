# ADR 0001: Merge EliteEnchantments + EliteArmor into one plugin

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

## Context

StarEnchants must include ALL features of EliteEnchantments (custom enchant
engine) and EliteArmor (armor sets, set bonuses, crystals/modifiers, heroic,
omni, crafting, crates) — fully intertwined. The originals are two plugins by
the same author that already cross-call each other.

## Decision

Build ONE plugin that absorbs the complete feature set of both, **excluding the
web server**. Ship ALL default content (every default enchant, all 13 armor
sets, all crystals + pairwise synergies, crates, crafting, menus) in the new
unified schema.

## Consequences

- A single distributable; no inter-plugin hook fragility.
- The two feature sets unify through shared cores (see ADR 0003).
- Large content surface — the architecture must keep content authoring + audit
  cheap and safe.

## Alternatives considered

- Two plugins with a hook (the status quo) — rejected: the user wants them
  deeply intertwined, and a unified engine is cleaner and more correct.
