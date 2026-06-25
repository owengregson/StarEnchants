# ADR 0013: Single `/se` command root

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

## Context

Cosmic Enchants-style plugins expose separate command roots for the enchant
engine and the armor-set system. StarEnchants combines both into one plugin, so
the command surface is a choice: keep separate legacy roots as aliases, or
consolidate.

## Decision

One command root: **`/se`** (alias `/star`), exposing the full merged surface
(`effects | conditions | triggers | selectors | info | problems | reload |
migrate | item dump | give | …`). Separate legacy command roots are **dropped**.

## Consequences

- A single, discoverable command tree; permissions declared once.
- Operators/scripts using the legacy command roots must update — acceptable since
  StarEnchants is a fresh plugin with a migrator, not a drop-in upgrade (ADR 0006).

## Alternatives considered

- Keep the separate legacy command roots as aliases for muscle memory — rejected
  by the user in favor of one clean root.
