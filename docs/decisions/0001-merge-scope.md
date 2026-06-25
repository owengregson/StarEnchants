# ADR 0001: Combine custom enchants and armor sets into one plugin

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

## Context

StarEnchants must include ALL features of a custom enchant engine and an
armor-set system (armor sets, set bonuses, crystals/modifiers, heroic,
omni, crafting, crates) — fully intertwined. Cosmic Enchants-style setups
typically split these across two plugins that cross-call each other.

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
