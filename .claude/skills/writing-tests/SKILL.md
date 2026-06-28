---
name: writing-tests
description: Use when writing, reviewing, removing, or restructuring ANY test in StarEnchants — deciding whether a test should exist, which layer it belongs in, and how to write it so it catches real bugs without breaking on non-bugs. Supersedes the unit-vs-live notes in live-server-testing / matrix-gate.
---

# Writing tests

The governing policy for tests in this repo: *whether* a test should exist, *which
layer* it belongs in, and *how* to write it so it catches real bugs without breaking
on non-bugs. The target design is [docs/testing-architecture.md](../../../docs/testing-architecture.md).

> **This supersedes** the "when to write a live test" guidance and the unit-vs-live
> notes in the `live-server-testing` / `matrix-gate` skills. When this conflicts with
> older prose, this wins.

Two failure modes are equally bad: tests too **dense** to catch a real bug
(boilerplate, "verify the mock was called", exact-string assertions) and too
**sparse** on the edges that actually break (lifecycle teardown, Folia regions, error
paths). Push every test toward the middle: **assert the contract a real bug would
violate, once, against the one place that owns it.**

## Rule 0 — A test must justify its existence

Name the **specific bug it would catch that nothing else catches.** If you can't,
don't write it. A test that can only fail when a human edits the test itself (a
getter returning the value the test set two lines up; a `!= null` on a field the
canonical constructor already requires) is noise.

**Coverage is a tool, not a target.** Write a test per *contract* — a routing
decision, a numeric formula, a serialization layout, a precedence rule, an error
path — not per public method. One contract may be a single `@ParameterizedTest` row;
one method may carry several contracts.

## Rule 1 — Never re-type a string production owns (single source of truth)

The #1 rule. A test must **never re-type a user-facing or config string** production
already defines — a display name, lore line, color code, message, doc/example,
tab-complete label, command output, default material/identifier, DSL token, spec
head, alias, or delimiter. Re-typing creates a **two-place edit**: change the
production string and the test breaks for a non-bug, pointing at the test, not a bug.

Assert against the **same source production reads**, or feed your **own** input:

- **A value with an accessor/registry/spec/constant** → reference it.
  `assertEquals(TraksConfig.defaults().block().material(), …)`, not `SLIME_BALL`.
  `LoreStyle.DEFAULT.unknownLabel()`, not `"§8Unknown Enchant"`. For a section of
  defaults, compare the whole record: `assertEquals(SoulGemConfig.defaults(), gem)`.
- **A transform/algorithm** → feed a **test-owned** fixture and assert the output;
  the literal is then *your input*, not the catalogue. `LoreRendererTest` passing its
  own `LoreStyle` + name map is the correct model.
- **A diagnostic** → assert the **code** through `schema.diag.DiagCode`, never the
  message wording: `assertTrue(d.is(DiagCode.E_COND_TYPE))`. Producers emit
  `DiagCode`; tests read it back — one symbol, no wire-string literal.
- **A DSL token / spec head / param key** → drive it from the kind's own
  `EffectSpec` (`SpecDrivenCtx` fills a `FakeEffectCtx` straight from the spec), not a
  bare `"give"`/`"AOE"`/`"who"`.
- **A synthetic test-owned code** (a diagnostic the test itself constructs to check
  propagation, e.g. `Diagnostic.error("X_BAD", …)`) is fine — the test owns both
  ends, so there is no drift.

**Exact text may be pinned in exactly two sanctioned ways:** (1) a **golden file**
regenerated from the single production source via a regen flag (never a hand-copied
literal); (2) a **format/algorithm spec** (`render()`, `usage()`, numerals,
word-wrap) evaluated over a **test-owned** fixture.

> **Litmus test.** If changing one production string would force editing a test that
> is *not* specifically testing that string's transform, the test is coupled. Fix
> it — reference the constant, or use a synthetic fixture.

## Rule 2 — A family of one behavior is one data-driven test, not N files

When several tests differ only by input and expected output — the effect-kind wiring
tables, per-error diagnostics, per-store TTL contracts — they are **one parameterized
test with rows**, not one file each. A new instance is one row; a missing case is a
structurally absent row, loudly visible, not a silently-missing file.

But — **collapsing must preserve every assertion the per-instance tests carried.**
The audit overturned 27 of 34 proposed effect deletions because each was the *sole*
guard of its kind's contract. A collapse is a **coverage-preserving rewrite** (Rule
5): each old `@Test` becomes a row that keeps its **distinct-value arguments** (to
catch a transposition), its **multi-target** case (to catch a broken loop), and its
`verifyNoMoreInteractions` (to catch a spurious intent). Dropping any of these is a
regression, not a cleanup.

The effect-kind tables are the worked example: `FanOutEffectTest` (per-target / per-
player / player-only fan-out), `ModeDispatchEffectTest` (mode/channel/side/type
branchers), `LocationEffectTest` (world/block/spawn + no-op guards),
`FlagAndSoulEffectTest` (no-target flags + soul). Each row is a `DynamicTest` so a
typed `verify(sink)…` survives intact; the ctx is `testfx.FakeEffectCtx`.

## Rule 3 — Pick the layer by what only it can prove

Default to the **lowest** layer that can prove the contract (cheapest first):

- **Pure-unit** — deterministic logic over real production types, no Bukkit, no
  mocks. DSL/compiler/engine-math/codec/render-algorithm/resolution. The default.
- **Data-driven corpus** — a family of the above (Rule 2).
- **Contract & property** — single-source enforcement and architectural invariants
  (`SpecConformanceTest`, purity guards, structural shape).
- **Golden / snapshot** — the *only* home for shipped user-facing strings and
  generated artifacts (Rule 1, sanctioned way #1).
- **Live integration** — the last resort (Rule 4).

A mocked `Sink` is **not** "testing a mock": the `Sink` *is* the mutation boundary,
so verifying the emitted intent verifies the contract. Use the strict
`FakeEffectCtx` for the ctx (it throws on an unset param, so a mistyped param key
fails loudly instead of passing on a mock's silent `0`/`null`).

## Rule 4 — Live tests are reserved for what a booted server alone can prove

Write a live suite **only** when the truth lives in the real game and nothing below
can reach it: a real Bukkit event producing observable **server-side state** (assert
the state change, **never client motion** — clientless fake players have none); real
**PDC** serialize/deserialize across the mapping flip; **Folia** region/thread
correctness (stage a **second region** for a cross-region contract — a single-region
test proves nothing); real inventory/GUI routing; cross-version registry presence on
the running version; reload atomicity under a concurrent reader.

Live mechanics are non-negotiable (each fixes a real flake): **wait in game ticks**,
never wall time; **fresh actors per scenario** with listener teardown
(`HandlerList.unregisterAll`); **reset captors immediately before** the staged
action; **pin event counts** (exactly-once), not just values; **sanitize the arena**.
Run the **same** suites on real Folia, not just Paper. See `live-server-testing`.

## Rule 5 — Never delete coverage; re-home it

When you remove or merge a test, **point at the specific row, golden line, or case
that now carries every contract the old test pinned** — before the old file is
deleted, with both green. A change may not shrink covered branches. "This looks
redundant" is a hypothesis to verify against the production code, not a license to
delete.

The only deletions that need no re-homing: tests of **test doubles**, **non-tests**
in the test set (a code-gen tool with zero assertions), and **dormant gates** the
maintainer signs off as dead (e.g. a one-off migration-equivalence gate after the
migration ships).

---

## When you add a feature — the rubric

1. **Is there a contract a real bug would violate?** No → no test (Rule 0).
2. **Does production already own a string/value I'd assert?** Yes → reference it; if
   it's shipped display copy, it belongs in a golden, not a literal (Rule 1).
3. **Is this one instance of a family already tested as a table?** Yes → add a
   **row**, not a file (Rule 2).
4. **What is the lowest layer that can prove it?** Start there (Rule 3); reach for
   live only if a booted server is the only oracle (Rule 4).
5. **Did I touch an edge that ships dark?** Error path, absence, overflow, a Folia
   region boundary, a teardown/lifecycle path — pin it; sparseness there is a defect.
6. **If I removed or merged anything, is its contract re-homed and green?** (Rule 5.)

### In practice

- **A new effect kind** → one **row** in the matching effect table (a `FanOut` row,
  or a `ModeDispatch` row if it branches), built from a `FakeEffectCtx`.
  `SpecConformanceTest` covers it automatically (it drives every kind from its own
  spec, so an undeclared/mistyped param trips the strict ctx). A new file only for
  genuinely novel branch logic. **No** new live suite — the combat spine already
  proves an effect reaches the world.
- **A new compiler shape** → build the def through `testfx.Defs` (the one place the
  `AbilityDef`/`LoweredAbility` record arity lives), so a new field is a one-place
  change, not a parallel edit across every stage test.
- **A new config field** → assert it round-trips through the real loader from **your
  own** YAML; assert defaults via `.defaults()`; assert any clamp/reorder with a
  reversed/out-of-range input. No exact-string default copy.
- **A new user-facing message/lore/menu label** → it lands in a golden via one regen;
  review the diff. Don't assert the literal.
- **A new Folia-relevant world mutation** → a live suite **with cross-region staging**
  and an exactly-once event count.

### Anti-patterns — never do these

- Assert an exact lore/menu/command string a production constant defines (Rule 1).
- Assert a diagnostic **message**; assert the **code** via `DiagCode` (Rule 1).
- One file per effect kind / store / provider when they share a contract (Rule 2).
- A live suite for pure logic a mocked `Sink` or synthetic event proves (Rule 3/4).
- `verify(mock).x()` where the asserted value is the mock's own default (`ticks=0`,
  `multiplier=0.0`) — use a distinct non-default value so the wiring is pinned.
- A single-target effect test (can't catch a broken fan-out loop) — use two.
- A single-region live test for a cross-region contract (proves nothing).
- Delete a test because it "looks redundant" without re-homing its contract (Rule 5).
