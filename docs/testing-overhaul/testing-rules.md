# Testing rules

> **Status: the governing policy for tests in this repo.** It decides *whether* a
> test should exist, *which layer* it belongs in, and *how* it must be written so
> it catches real bugs without breaking on non-bugs. The target architecture is
> [testing-architecture.md](testing-architecture.md); the overhaul that brings the
> existing suite into line is [testing-overhaul-plan.md](testing-overhaul-plan.md).
>
> **This document supersedes and absorbs** the "when to write a live test"
> guidance in `writing-a-live-test.md` and the unit-vs-live notes in the
> `live-server-testing` / `matrix-gate` skills. When this conflicts with older
> prose, this wins.

These rules exist because the audit found two failure modes in equal measure:
tests too **dense** to catch a real bug (boilerplate, "verify the mock was
called", exact-string assertions) and too **sparse** on the edges that actually
break (lifecycle teardown, Folia regions, error paths). The rules push every test
toward the middle: **assert the contract a real bug would violate, once, against
the one place that owns it.**

---

## Rule 0 — A test must justify its existence

Before writing a test, name the **specific bug it would catch that nothing else
catches.** If you can't, don't write it. A test that cannot fail except when a
human edits the test itself (a getter returning the value the test set two lines
up; a `!= null` on a field the canonical constructor already requires) is noise —
it dilutes signal and adds a maintenance edge.

Corollary: **coverage is a tool, not a target.** We do not write a test per public
method. We write a test per *contract* — a routing decision, a numeric formula, a
serialization layout, a precedence rule, an error path. One contract may be a
single `@ParameterizedTest` row; one method may carry several contracts.

## Rule 1 — Never re-type a string production owns (single source of truth)

This is the #1 rule and the audit's #1 anti-pattern. A test must **never re-type a
user-facing or config string** that production already defines — a display name,
lore line, color code, message, doc/example, tab-complete label, command output,
default material/identifier, DSL token, spec head, alias, namespace, or delimiter.
Re-typing it creates a **two-place edit**: change the production string and the
test breaks for a non-bug, and the failure points at the test instead of a bug.

Assert against the **same source production reads**, or feed your **own** input:

- **A value with an accessor/registry/spec/constant** → reference it.
  `assertEquals(TraksConfig.defaults().block().material(), …)`, not `SLIME_BALL`.
  `MasterConfig.defaults().commandTrigger().name()`, not `"cast"`.
  `LoreStyle.DEFAULT.unknownLabel()`, not `"§8Unknown Enchant"`.
- **A transform/algorithm** → feed a **test-owned** fixture and assert the output.
  The literal is then *your input*, not the catalogue. (`LoreRendererTest` passing
  its own `LoreStyle` + name map is the correct model.)
- **A diagnostic** → assert the **code**, through the shared `schema.diag.DiagCode`
  symbol, never the message wording. `first.code() == DiagCode.E_COND_TYPE.name()`.
- **A DSL token / spec head / param key** → reference the kind's own
  `EffectSpec`/`SelectorSpec` (its `D.enumOf(...)`/`.param(...)`/`SPEC.head()`),
  not a bare `"give"`/`"AOE"`/`"who"`.
- **Cross-version alias content** → drive the resolve *strategy* with a **synthetic**
  `Map.of(...)`; pin the real `Aliases` table in **one** well-formedness test
  (every value has its inverse, no self-cycle).

**Exact text may be pinned in exactly two sanctioned ways:**

1. **A golden file** regenerated from the single production source via a regen flag
   (`docs/reference/*.md`, `website/src/data/*.json`, `content/index.txt`, the new
   render golden). Never a hand-maintained literal copy.
2. **A format/algorithm spec** (`render()`, `usage()`, `label()`, numerals,
   word-wrap) evaluated over a **test-owned** fixture, not the shipped catalogue.

> **Litmus test.** If changing one production string would force editing a test
> that is *not specifically testing that string's transform*, the test is coupled.
> Fix the test — reference the constant, or use a synthetic fixture. Do not edit
> the literal in two places.

The canonical sources every test must reference instead of re-typing are
tabulated in
[testing-audit-findings.md](testing-audit-findings.md#single-source-of-truth-map).

## Rule 2 — A family of one behavior is one data-driven test, not N files

When several tests differ only by input and expected output — the ~46 effect-kind
wiring tests, the ~8 TTL-store contracts, per-provider protection gates, per-error
diagnostics, migrator token tables — they are **one parameterized test with rows**,
not one file each. A new instance becomes one row, not a new file; a missing case
is a structurally absent row, loudly visible, not a silently-missing file.

But — **collapsing must preserve every assertion the per-instance tests carried.**
The audit proved this: of 34 proposed effect-kind deletions, 27 were overturned
because each test is the *sole* guard of its kind's contract. So a collapse is a
**coverage-preserving rewrite**, governed by Rule 5: each old `@Test` becomes a row
that keeps its distinct-value arguments (to catch a transposition), its multi-target
case (to catch a broken loop), and its `verifyNoMoreInteractions` (to catch a
spurious intent). A collapse that drops any of these is a regression, not a cleanup.

## Rule 3 — Pick the layer by what only it can prove

Default to the **lowest** layer that can prove the contract. The layers, cheapest
first ([architecture](testing-architecture.md#the-layers-the-pyramid)):

- **Pure-unit** — deterministic logic over real production types, no Bukkit, no
  mocks. DSL/compiler/engine-math/codec/render-algorithm/resolution. The default.
- **Data-driven corpus** — a family of the above (Rule 2).
- **Contract & property** — single-source enforcement and architectural invariants
  (spec-conformance, code contract, purity guards, structural shape).
- **Golden / snapshot** — the *only* home for shipped user-facing strings and
  generated artifacts (Rule 1, sanctioned way #1).
- **Live integration** — the last resort; see Rule 4.

If a synthetic event or a mocked `Sink` can prove it, it is **not** a live test.
Effect kinds emit through a mocked `Sink` at the unit layer; the `Sink` *is* the
mutation boundary, so verifying the emission is verifying the contract — that is
not "testing a mock", that is testing the seam.

## Rule 4 — Live tests are reserved for what a booted server alone can prove

Write a live suite **only** when the truth lives in the real game and nothing
below can reach it:

- A real Bukkit event producing observable **server-side state** (a potion on a
  real mob, a health delta, fire ticks, a block change, item NBT changed) — assert
  the state change, **never client motion** (clientless fake players have none).
- Real **PDC serialize/deserialize** across the spigot→mojang mapping flip.
- **Folia** region/thread correctness — and stage a **second region** when the
  contract is cross-region; a single-region test passes trivially and proves
  nothing about the invariant the matrix exists for.
- Real inventory/GUI routing; cross-version registry existence/absence on the
  running version; reload atomicity under a concurrent reader.

Live suite mechanics are non-negotiable (each fixes a real matrix flake): **wait in
game ticks**, never wall time; **fresh actors per scenario** with listener teardown
(`HandlerList.unregisterAll`) — residual state contaminates; **reset captors
immediately before** the staged action; **pin event counts** (exactly-once), not
just values — a blocked activation passes vacuously otherwise; **sanitize the
arena** (peaceful, no spawning, purge mobs). Run the **same** suites on real Folia,
not just Paper — a green Paper run says nothing about Folia.

## Rule 5 — Never delete coverage; re-home it

When you remove or merge a test, you must **point at the specific row, golden line,
or case that now carries every contract the old test pinned** — before the old file
is deleted, and with both green. The audit's per-file contract list and a JaCoCo
per-module coverage diff are the ledger: **a change may not shrink covered
branches.** "This looks redundant" is a hypothesis to verify against the actual
production code, not a license to delete — 27 of 34 "redundant/vacuous" calls were
wrong on a second read.

The only deletions that need no re-homing are tests of **test doubles**
(`PlatformResolversTest` exercised only the fake resolver), **non-tests** living in
the test set (`EePortGenerator` is a code-gen tool with zero assertions — relocate
it), and **dormant gates** the maintainer signs off as dead.

---

## When you add a feature — the decision rubric

For each new behavior, walk this once:

1. **Is there a contract a real bug would violate?** No → no test (Rule 0).
   Yes → continue.
2. **Does production already own a string/value I'd assert?** Yes → reference it;
   if it's shipped display copy, it belongs in the golden, not a literal (Rule 1).
3. **Is this one instance of a family already tested as a table?** Yes → add a
   **row**, not a file (Rule 2).
4. **What is the lowest layer that can prove it?** Start there (Rule 3). Reach for
   live only if a booted server is the only oracle (Rule 4).
5. **Did I touch an edge that ships dark?** Error path, absence, overflow, a Folia
   region boundary, a teardown/lifecycle path — pin it; sparseness there is a
   defect (principle 5).
6. **If I removed or merged anything, is its contract re-homed and green?** (Rule 5.)

### What this looks like in practice

- **A new effect kind** → one **row** in `EffectSinkWiringTest` (or a focused
  `ModeDispatchEffectTest` if it branches), built from a `FakeEffectCtx` keyed off
  the kind's `EffectSpec`. The `SpecConformanceTest` covers it automatically. A new
  file only if it has genuinely novel branch logic. **No** new live suite — the
  combat spine already proves an effect reaches the world; one representative kind
  suffices for that.
- **A new config field** → assert it round-trips through the real compiler from
  **your own** YAML (`YamlFixture`); assert defaults via `.defaults()`; assert any
  clamp/reorder with a reversed/out-of-range input. No exact-string default copy.
- **A new command/give-type** → add it to the production constant
  (`SeCommand.GIVE_TYPES`); the structural contract test (every `GIVE_TYPES` entry
  has a `give()` case) guards it. Don't re-type the completion list.
- **A new user-facing message/lore/menu label** → it lands in the render golden via
  one `regenDocs`; review the diff. Don't assert the literal in a test.
- **A new store with a TTL/lifecycle** → implement `TtlStoreAdapter`; you get the
  shared contract for free. The file holds only the store's distinctive semantics.
- **A new Folia-relevant world mutation** → a live suite **with a cross-region
  staging** and an exactly-once event count.

### Anti-patterns — never do these

- Assert an exact lore/menu/command string a production constant defines (Rule 1).
- Assert a diagnostic **message**; assert the **code** (Rule 1).
- One file per effect kind / store / provider when they share a contract (Rule 2).
- A live suite for pure logic a mocked `Sink` or synthetic event proves (Rule 3/4).
- `verify(mock).x()` where `x` is the *whole* behavior **and** the value is the
  mock's own default (e.g. asserting `ticks=0`, `multiplier=0.0`) — use a distinct
  non-default value so the wiring is actually pinned.
- A single-target effect test (can't catch a broken fan-out loop) — use two.
- A single-region live test for a cross-region contract (proves nothing).
- Delete a test because it "looks redundant" without re-homing its contract
  (Rule 5).
