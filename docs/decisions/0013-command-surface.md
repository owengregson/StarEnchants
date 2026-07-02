# ADR 0013: Single `/se` command root

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

> **[Amendment 2026-07-01]** Two refinements landed after this ADR: (1) **ADR-0030 added `/enchants`** as a
> second **player-facing** command root (the user hub), so the surface is now `/se` (operator console +
> full tree) plus `/enchants` (player hub) — the "one root only" wording below predates the GUI overhaul.
> (2) The **`/star` alias ships now** — it is added to `plugin.yml` in the `feat/diagnostic-commands` PR of
> this wave. The dropping of the *legacy per-feature* command roots still holds.

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
