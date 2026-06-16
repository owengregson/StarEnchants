# ADR 0010: The concrete architecture is self-derived, not borrowed

- **Status:** Accepted — workshop complete; outcome adopted in ADR 0011
- **Date:** 2026-06-15
- **Deciders:** owengregson

## Context

An early analysis proposed an `api/common/core/compat-*/tester` layout "mirroring
StrikeSync" (a packet/combat/anticheat plugin). StarEnchants is a different kind
of plugin (content-/engine-/GUI-driven). The user is explicit: the architecture
must be uniquely best for THIS plugin, not borrowed from StrikeSync, EE, or AE.

## Decision

Derive the concrete module/package layout and the engine/execution/data model
from first principles via a multi-lens **design workshop**: independent
architecture proposals (distinct philosophies) → adversarial critique panel →
synthesis, with the synthesis required to justify how it diverges from the
mirror prior. The decompiled analysis informs WHAT to build and HOW features
interact — never how to construct it.

## Consequences

- The concrete structure is recorded by superseding this ADR once the workshop
  output is reviewed and approved by the user.
- The locked decisions (ADRs 0001–0008) constrain the workshop but do not dictate
  its structure.

## Alternatives considered

- Adopt the StrikeSync-mirror layout directly — rejected: risks building on a
  borrowed, possibly inferior architecture.
