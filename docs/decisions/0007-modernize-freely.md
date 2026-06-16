# ADR 0007: Modernize freely; adopt AE engine-level improvements only

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

## Context

The originals carry many bugs (chance-math off-by-one, `isPlayerHolding` reads
the wrong entity, dead `DRAIN_SOULS_CONSTANT`, `KNOCKBACK` NaN, non-atomic
economy steals, crystal `armor-types` that throw, etc.). AdvancedEnchantments is
the mature competitor with richer engine capabilities and standalone subsystems.

## Decision

Modernize freely: fix the originals' bugs and adopt best behavior by default
(no parity toggles required). Adopt AdvancedEnchantments' **engine-level**
improvements (rich conditions/variables/selectors/triggers, op-visible error
reporting) but **not** its marquee standalone subsystems (no GKits, StatTrak,
loot-population, or mob-drop tables).

## Consequences

- Behavior may intentionally diverge from EE/EA where the originals were wrong.
- Scope stays bounded to the EE+EA merge plus engine-level polish.

## Alternatives considered

- Strict behavior parity with toggles — rejected: the user chose to modernize.
- Adopt AE marquee subsystems too — rejected as out of scope for now.
