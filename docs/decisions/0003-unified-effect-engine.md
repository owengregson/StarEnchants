# ADR 0003: One unified effect engine; everything is an effect source

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

## Context

Cosmic Enchants-style plugins run THREE parallel effect frameworks (custom enchant
effects, armor-set effects, crystal NBT), causing duplicated logic, order-dependent damage
compounding, dead crystal-suppression effects, and fragile cross-plugin events.

## Decision

A single engine. Custom enchants, armor-set bonuses, weapon bonuses,
crystals/modifiers, and Heroic stats are all **effect sources** feeding one
trigger → condition → target → effect pipeline. `DISABLE_ENCHANT`/`DISABLE_GROUP`
become internal suppress-activation calls, not cross-plugin events.

## Consequences

- Crystal suppression works; one combined damage-modifier pass replaces N
  compounding `setDamage` calls; one full-set resolver.
- The pipeline must faithfully reproduce the originals' gameplay semantics while
  fixing their bugs (see ADR 0007).
- The concrete shape of the engine/execution model is decided by the design
  workshop (see ADR 0010).

## Alternatives considered

- Keeping separate subsystems behind a shared facade — rejected: preserves the
  duplication and the interaction bugs.
