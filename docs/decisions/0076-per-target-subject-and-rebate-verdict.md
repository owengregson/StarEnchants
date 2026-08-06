# ADR 0076: The per-target subject cursor and the rebate verdict

- **Status:** Accepted
- **Date:** 2026-08-05
- **Deciders:** project owner + engine work
- **Relates to:** ADR-0039 (dense dispatch + `FactMask` — this design adds no mask bits and no populate pass),
  ADR-0043 (the actor-origin snapshot), ADR-0045 (`/se why`), ADR-0050 R4 (cooldown scope rules),
  ADR-0075 (consume-time feedback, whose dispatch-layer emit shape part E reuses), owner ruling R-v (the
  per-target `SUPPRESS_INCOMING` consult this generalises), docs/architecture.md §3.3–3.6, §8; skills
  `effect-engine`, `feature-interaction-rules`, `performance-hot-paths`
- **Rulings:** R-QC49 (one designed feature, one wave), R-QC66 (the design is approved whole),
  R-QC67 (`%selected%` publishes −1 for "never activated"), R-QC68 (`%target.potion.*` is CUT),
  R-QC69 (the two balance deltas are confirmed)

## Context

An activation binds exactly one subject — the combat victim — before gate 5, and every fact, roll, cooldown
key and message token reads it. A selector then resolves N bodies at gate 12 and nothing downstream can
address them individually. Seventeen ledger rows across enchants, sets and pets are blocked on some view of
that: a per-target immunity roll (Pummel, Nature's Wrath), a per-target numeric draw (Chain Lifesteal), an
emptiness answer (five pets), a resolved count (Tombstone, Horrify), a per-selector-target cooldown (Cleave),
and a "rebate ate this proc" event (the Metaphysical family, Guided Rocket). R-QC49 rules them one feature.

Ruling R-v had already built the mechanism for one case — a per-target `SUPPRESS_INCOMING` consult inside
`AbilityExecutor.runEffects` — and had already settled the semantics: a target verdict may only remove a body.

## Decision

**A. The activation is adjudicated once; a target is adjudicated many times, and a target verdict may only
remove a body.** The gate sequence (§3.3) is unchanged; gates 8 and 9 keep firing once per activation.
Per-target evaluation is a filter inside gate 12, not a gate. It has no `GateOutcome`, releases no cooldown,
un-spends no souls, and records nothing to the `WhyRecorder`.

**B. A subject cursor on the `FactBuffer`.** A third scope, `%target.*%`, re-points the existing UUID-keyed
lazy readers (enchant levels, crystal counts, vars, souls, heroic pieces) plus `getType()`/`Allies` — reads the
selector already paid for on this thread. Its vocabulary **excludes every live entity read** (health, pose,
geometry, and — per R-QC68 — potions): that exclusion is the Folia rule made structural rather than
documented, and it means the per-target pass **never touches a target, only decides about one**, so it needs no
intent, no hop, and no `Regions` guard. `%target.*%` in `condition:`/`chance:` is a blocking `E_VAR_SCOPE`
diagnostic; those gates run before any selector resolves. So is naming a fact the scope does not have — the
rejection is by construction, not by documentation.

**C. `each-if` / `each-chance` / `each-cooldown`,** declared once on `EffectSpec.Builder` for every kind that
declares an ENTITY target slot, so all ~140 registered kinds gain them from one declaration, and hoisted at
lower time into `CompiledEffect` fields. `each-chance: X` is defined as `each-if: "%target.roll% < X"` over
**one shared draw per body per ability** — memoised on the cursor, so a filter and its complement partition
across rows instead of double-rolling. `each-cooldown` uses `CooldownStore`'s existing per-victim dimension,
keyed on the selector target under a target bucket gate 6 never produces; declaring it on an ability with no
cooldown scope is a blocking diagnostic.

**D. `%selected%`** — the count the most recent targeting effect in this ability bound after filtering,
published by the executor (the `%soulcost%` precedent). Zeroed when an ability begins its effect walk, and
**−1 when the ability never activated** (R-QC67) — the distinction a bare count cannot make, and the one the
empty-selection refusal idiom rests on. One `FactBuffer` serves the whole trigger pass, so a sibling ability's
gate-7 condition reads it, which makes the "post-selection branch" a **fact**, not a new construct.

**E. A REBATED chance verdict.** A declared `chance-rebate:` (points) or `chance-rebate-scale:` (a fraction of
base) lets gate 8 split its single roll into ACTIVATED / REBATED / CHANCE_FAILED at identical distribution,
giving the blocked-proc family a real event to hang a line on. Feedback is emitted by the dispatch layer,
exactly as ADR-0075's consume-time feedback is. `rebate-spends-cooldown:` reuses the pipeline's existing
`spendCooldownOnChanceFail` mechanism on the REBATED arm.

Parts A–D ship as stages S1–S3 (engine) and S4 (content); part E as S5–S6.

## Consequences

- Fifteen ledger rows close; two are re-pointed at named successor waves (a deferred-payload fact surface for
  Mother of Yijki and its `STACKING_DOT`/`PERIODIC_DAMAGE`/`TURRET_RING` siblings; a `DURABILITY` transfer wave
  for Soul Siphon).
- No new `FactMask` bits are needed for `%target.*%` — every subject node is a lazy reader, so `FactMasks`
  correctly contributes nothing and a filtered AoE cannot drag the demand-driven populator back to computing
  facts nobody reads. `%selected%` is one appended number slot.
- An effect that opts into nothing pays **one null check** and allocates exactly what it allocates today; an
  unfiltered pass returns the resolved list ITSELF (the defender consult's copy-on-first-drop idiom). The
  cursor is thread-local and re-pointed by field writes, so a 20-body sweep allocates nothing for it. The JMH
  `ExecutorBenchmark` floor and its ~0 B/op assertion hold unchanged.
- **The filtered-AoE bench row is NOT built, and the reason is structural.** `:bench` has the Bukkit API on its
  classpath and no server, and `SubjectCursor.bind` reads `getUniqueId()` off a real `LivingEntity`, so a
  16-body row needs sixteen entity stand-ins. Every way to make one distorts exactly the number the row exists
  to prove: a Mockito proxy or a `java.lang.reflect.Proxy` allocates per invocation, which swamps a 0 B/op
  assertion; a hand-written `LivingEntity` implementation allocates nothing but pins ~200 no-op methods against
  an interface that grows across the 1.17.1 → 26.1.x range, so a toolchain bump would break the build on a
  benchmark. The per-body cost the row would measure is already gated from two sides — `PerTargetFilterTest`
  asserts the copy-on-first-drop identity (an unfiltered pass returns the list itself) and the existing
  `effectExecution` budget covers the executor path the filter sits in. Revisit if the engine ever grows a
  UUID-keyed filter seam a bench could drive without an entity at all.
- The authoring surface grows by three effect params, one variable scope, one variable (and, at S5, four
  ability knobs) — `RegistryFingerprint` and `docs/reference/authoring-surface.txt` churn in two stages, so
  two golden-regen reviews rather than one. Declaring an ENTITY target slot now moves the fingerprint, because
  it declares three authorable params; a LOCATION slot still does not.
- Five kinds (`BATTERY`, `HIT_TEMPO`, `DISARM_SHUFFLE`, `CONVERT_SUMMON`, `PROC_REBOUND`) already read a
  numeric argument inside their target loop and now say so with `perTarget()`. Their behaviour is unchanged —
  `ctx.dbl` always re-evaluated — but the cursor is now bound while they do it, so `%target.*%` is readable
  from their arguments too.
- `%victim%` vs `%target%` is a real authoring hazard, closed by the scope-legality diagnostic rather than by
  documentation.
- `%selected%` is ONE dense slot, rewritten by every targeting effect and overwritten with `-1` by every
  ability that fails to activate. So exactly one effect row and one sibling ability may read it directly;
  anything beyond that captures the count into a var first (the `lava-elemental.yml` marker idiom). Found by
  the S4 content, and now stated in `docs/dev/internals/effect-engine.md` and on the `BuiltinVars` entry.
- `GateOutcome` gains one constant and `/se why` gains one verdict rendering both the roll and the UNREBATED
  chance — "you would have procced at 40 %, and a rebate took it" is an answer a bare `CHANCE_FAILED` cannot
  give. `REBATED` is inserted next to `CHANCE_FAILED` to keep the enum in gate order; the ordinal is a
  per-run `WhyRing` encoding and is not persisted, so the insert costs nothing.

### As built, where part E's shape differs from the design

Two adjustments, both made during S5 and neither changing what the decision above says:

- The six rebate knobs land as ONE nullable record component (`ChanceRebate` on `Ability`, `RebateKnobs` on
  `AbilityDef`) rather than six flat fields in the `noSouls*` shape. They are absent together on all but a
  handful of abilities, so one null check at gate 8 replaces two, three records with 30+ components each grow
  by one instead of six, and the seven def readers attach the envelope through one `withRebate(...)` seam
  instead of each growing a positional argument list.
- The mutual exclusion is a reader diagnostic (`E_LOAD_REBATE`), not a `ParamSpec` `CrossRule`. `CrossRule`
  governs EFFECT params; these are ability-level knobs the `ContentParse` envelope reads, so `ContentFuzz`
  (which generates effect lines) is untouched and needs no new rule. The same code covers the second shape
  worth rejecting — feedback declared with no term at all, which would ship as a line that can never fire.

## Alternatives considered

Per-target **sub-activations** — one gate walk per target — rejected: it multiplies every gate's side effect
by N (N cooldowns, N soul debits, N `PreActivate` events, N chance rolls, N `/se why` verdicts), silently
re-pricing every AoE enchant already shipped, and it is precisely the "gate that runs per target after gate 9"
the design forbids.

A **new pipeline gate** for the filter — rejected: §3.3's sequence is a contract about the ACTIVATION, and its
value is that a gate stops the walk. A per-target stage has no verdict, stops nothing and releases nothing;
putting it in the sequence would force `GateOutcome` to grow a meaning it cannot have ("partially activated")
and would put a Bukkit-typed entity list into a deliberately Bukkit-free pipeline.

**Naive per-target re-population of `%victim.*%`** — rejected: it would re-run `populateVictim` per body (N
cross-region entity reads on Folia, each swallowed into a default — wrong answers, silently), re-purpose one
name to mean two different bodies depending on where it is read, and reuse the ABILITY's condition, so an
author could not separate "this gates the activation" from "this gates each body". The chosen shape keeps the
idea and fixes all three.

**Building the scratch on the existing `VarStore`** — rejected for the hot path: string-keyed (a map get per
read, which the `performance-hot-paths` lint bans in the inner loop), player-scoped (it cannot carry a
per-body value at all), stringly-typed. It remains the right tool for the content-level marker idiom.

Re-gating the Metaphysical family on FULL immunity, and re-expressing the vetoes as a defender-armed
`SUPPRESS_INCOMING` — both already rejected in the ledger, recorded here so they are not re-proposed. A
one-off per-body rebate knob on `DELAYED_STRIKE_FIELD` — rejected as bespoke; recorded as the cheap fallback
if Mother of Yijki must close before the deferred-payload wave.

`%target.potion.<effect>%` — **CUT** (R-QC68). It is the one listed fact that is not UUID-keyed: it reads the
live entity, so a cross-region target would throw and default. No consumer in the cluster needs it, and
keeping the no-live-entity-read rule absolute is worth more than the fact.
