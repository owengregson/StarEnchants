# ADR 0004: Modern compile-at-load DSL

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

## Context

EE/EA parse effect/condition strings by `:`/space-splitting on EVERY activation,
with no validation (malformed lines throw at runtime), 16 discrete condition
classes, and per-effect ad-hoc targeting.

## Decision

A real DSL parsed and **compiled once at load**: structured effects, an
expression condition engine (`&& || ()`, comparators, a variable vocabulary +
PlaceholderAPI passthrough, flow results STOP/FORCE/CONTINUE + chance deltas),
and named, shared target selectors. Validation and op-visible error reporting
happen at load, not at runtime.

## Consequences

- No string parsing on the hot path; invalid content is caught at load with
  file/line context.
- A migrator translates old AE/EE/EA colon-strings into the new DSL (ADR 0006).
- Richer than the originals — an intentional modernization (ADR 0007).

## Alternatives considered

- Keep the colon-string syntax — rejected: carries the parsing warts and limits.
- Support both syntaxes — rejected: two grammars to maintain forever.
