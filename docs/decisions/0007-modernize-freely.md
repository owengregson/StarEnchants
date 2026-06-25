# ADR 0007: Modernize freely; adopt Cosmic Enchants-style engine-level improvements only

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

## Context

Cosmic Enchants-style plugins carry many bugs (chance-math off-by-one,
`isPlayerHolding` reads the wrong entity, dead `DRAIN_SOULS_CONSTANT`, `KNOCKBACK`
NaN, non-atomic economy steals, crystal `armor-types` that throw, etc.). The most
mature ones offer richer engine capabilities and standalone subsystems.

## Decision

Modernize freely: fix the bugs of Cosmic Enchants-style plugins and adopt best
behavior by default (no parity toggles required). Adopt Cosmic Enchants-style
**engine-level** improvements (rich conditions/variables/selectors/triggers,
op-visible error reporting) but **not** the marquee standalone subsystems (no
GKits, StatTrak, loot-population, or mob-drop tables).

## Consequences

- Behavior may intentionally diverge from Cosmic Enchants-style plugins where they were wrong.
- Scope stays bounded to the custom-enchant + armor-set combination plus engine-level polish.

## Alternatives considered

- Strict behavior parity with toggles — rejected: the user chose to modernize.
- Adopt the marquee standalone subsystems too — rejected as out of scope for now.
