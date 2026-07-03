# ADR 0045: `/se why` — the activation flight recorder

- **Status:** Accepted
- **Date:** 2026-07-02
- **Deciders:** StarEnchants maintainers

## Context

`GateOutcome` already names the gate an ability stopped at — its own javadoc says so a runtime trace
"can explain why an ability fired or not" — but the value was discarded at `AbilityExecutor.run`, which
kept only `.activated()`. So the plugin knew exactly why every activation did or didn't fire and threw
that away every hit.

"Why didn't my enchant proc?" is the #1 operator support question, and nothing answered it at gate time.
`/se problems` and the runtime quarantine cover compile-time faults and misbehaving abilities; neither
sees the gate walk. Operators were left guessing between a dozen reasons (wrong world, region-protected,
suppressed, on cooldown, a failed condition, an unlucky roll, no souls) with no way to tell which.

The question always arrives *after* the miss, so a toggle would defeat the point — the record has to
already exist. That makes affordability the whole design: the record path runs on the combat hot path,
so it must be allocation-free and lock-free, and its cost must be a build gate, not a claim.

## Decision

Record **every** `ActivationPipeline.evaluate` call into a per-player, fixed-size (64) int-packed ring
in the `EngineStores` lifecycle (quit-swept), always on. Recording lives **in the pipeline** — the only
place per-gate payloads exist — behind a primitive-only `WhyRecorder` seam (no `Ability`/`Activation` on
the signature, so `engine.stores` gains no cycle into `engine.pipeline`). Each row packs the tick, the
`Ability.defId`, a `verdict | trigger | generation` meta word, and two per-verdict payload ints (the
blocked world, the suppression scope/flavor/suppressor, the first blocked cooldown scope + remaining
ticks, the roll/chance basis points, the soul fail mode).

`/se why <player> [key]` renders the recent attempts human-readably, resolving every id **at render
time** against the live `Snapshot` (defId → `SourceMap` stable key + kind; trigger/world/scope/suppressor
names → the frozen interners). Each row carries an 8-bit **generation** stamp; a row whose generation
mismatches the live snapshot renders as pre-reload rather than resolving against the wrong tables.

Concurrency is a **per-slot seqlock**: writers claim a unique slot with `head.getAndIncrement()`, bracket
plain lane writes with an in-progress stamp and a release stamp (`AtomicLongArray.set`), and the reader
validates the stamp before AND after copying — dropping a torn row instead of trusting it. Writers never
synchronize; reads take no lock and add zero cost to the record path. Only `java.util.concurrent.atomic`
and plain arrays — **no VarHandle** — so the JDK-8 legacy jar compiles it unchanged under JvmDowngrader.

Suppressor attribution is threaded through the sink's `suppress` intent: `SuppressionStore` windows carry
the arming ability's `defId` (`Sink.suppress` gains a default 5-arg overload, so no implementor breaks),
letting a row render "suppressed by `DISABLE_GROUP(defense)` from `sets/yeti`".

A `gateWalkRecorded` JMH row wired with a live `WhyStore` (as production is) floors the always-on cost, so
"always-on is affordable" is a `./gradlew build` gate.

Quarantine skips and not-worn abilities are **render-time notes**, not ring rows: a quarantined ability
would otherwise flood the ring, and an ability that never became a candidate (not worn / trigger never
fired) leaves no attempt to record — both are diagnosed by cross-referencing the live quarantine list and
the player's `WornState` when the operator filters by key.

## Consequences

- ~2 KB per player-session (a 64-slot ring), freed on quit; nothing for a player who never attempted.
- The per-attempt cost is one CHM get + one atomic claim + two volatile stores. Measured (short JMH):
  `gateWalkRecorded` ~139M ops/s, ~0 B/op — versus ~129–275M for the bare walk. The floor (1.5M ops/s) is
  the tunable if CI variance bites, not the allocation budget.
- `Sink.suppress` gained a default overload; `SuppressionStore` values carry the suppressor's defId (one
  extra field, allocated only on the rare suppress-apply path — the per-hit read is unchanged).
- The renderer is defensive on every decoded field (verdict range, interner bounds, `SourceMap` null,
  generation mismatch), so a torn/stale/foreign row degrades to a note, never garbage.
- An 8-bit generation aliases after 256 reloads without a matching attempt between — accepted (worst case
  a name resolves against the wrong tables once, and the ring turns over in seconds of combat).

## Alternatives considered

- **Record in `AbilityExecutor`** instead of the pipeline — the executor only sees `.activated()`, not
  the per-gate payloads, so the render would be blind to *why*.
- **A config toggle** — defeats post-hoc diagnosis (the question arrives after the miss); the cost is
  floored by the JMH gate instead.
- **An event-object log with strings** — allocation on the hot path; the packed primitive ring is
  allocation-free.
- **A VarHandle seqlock** — a JvmDowngrader / JDK-8 risk for the legacy jar; `java.util.concurrent.atomic`
  is equivalent here and downgrades cleanly.
- **Per-gate ring rows for quarantine skips** — a quarantined ability floods the ring with identical rows
  for the life of the snapshot; a single render-time note is strictly better.
