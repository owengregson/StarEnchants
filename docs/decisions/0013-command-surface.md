# ADR 0013: Single `/se` command root

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

## Context

The originals expose separate `/ee` (EliteEnchantments) and `/ea` (EliteArmor)
command roots. StarEnchants merges both into one plugin, so the command surface
is a choice: keep the legacy roots as aliases, or consolidate.

## Decision

One command root: **`/se`** (alias `/star`), exposing the full merged surface
(`effects | conditions | triggers | selectors | info | problems | reload |
migrate | item dump | give | …`). The legacy `/ee` and `/ea` roots are **dropped**.

## Consequences

- A single, discoverable command tree; permissions declared once.
- Operators/scripts using `/ee`/`/ea` must update — acceptable since StarEnchants
  is a fresh plugin with a migrator, not a drop-in upgrade (ADR 0006).

## Alternatives considered

- Keep `/ee` and `/ea` as aliases for muscle memory — rejected by the user in
  favor of one clean root.
