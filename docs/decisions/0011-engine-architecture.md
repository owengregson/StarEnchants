# ADR 0011: Content-compiler + data-oriented runtime architecture

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson
- **Supersedes the open question in:** ADR 0010

## Context

ADR 0010 committed to deriving the architecture from first principles via a
multi-lens design workshop (5 independent proposals → 3 adversarial critiques →
synthesis). The workshop completed; the synthesis is in `docs/architecture.md`.

## Decision

Adopt the synthesized architecture: **StarEnchants is a content compiler + a
data-oriented runtime.**

- All five effect **sources** (enchant level, armor-set bonus, weapon bonus,
  crystal/modifier, heroic) **compile at load to one source-erased `Ability`
  record**; the compiled world is an immutable `Snapshot` swapped atomically with
  transactional reload.
- The runtime is stateless **systems** walking a per-player, pre-flattened,
  immutable, **multi-set** `WornState`, executing abilities through a **`Sink`**
  (the single mutation boundary; effects emit intents, never touch
  entities/schedulers) routed by declared **`Affinity`**.
- Feature interactions resolve via **contribute-then-resolve arbiters** (one
  damage fold, interned-id suppression set, soul/slot ledgers) on
  single-thread-owned scratch.
- Item state: one PDC codec, **stable string keys**, cache keyed by
  **content-hash + generation**; lore rendered from state.
- Modules (lifecycle-spined): `se-schema, se-compile, se-engine, se-item,
  se-feature, se-platform, se-migrate, se-api, compat-folia, compat-modern,
  se-tester`.

Full detail and the explicit divergences from the StrikeSync-mirror prior are in
`docs/architecture.md`.

## Consequences

- Per-source special-casing is structurally impossible; the originals' worst
  interaction bugs are eliminated by construction.
- Folia-correctness and cross-version safety are structural (the `Sink` removes
  the scheduler door; resolvers run at compile time), not author discipline.
- Explicitly rejected (do not reintroduce): a full opcode VM, WAIT-as-continuation,
  an in-process mediator bus, an 18-module federation, a pooled mutable context
  effects can stash, single-`activeSetId` worn state, per-load dense ids in PDC,
  and codegen as the primary wiring.

## Alternatives considered

See the five workshop proposals and three critiques (`deobf/analysis/design/`,
local). The StrikeSync-mirror prior (`api/common/core/compat/tester` split by
purity) was rejected as the organizing spine — see `docs/architecture.md` §1.
