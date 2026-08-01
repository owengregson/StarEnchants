# Cosmic suite port, attempt 2 — design

**Date:** 2026-08-01
**Status:** Approved (owner rulings recorded below)
**Supersedes:** the first import attempt, parked verbatim on
`wip/cosmic-import-attempt-1` (never merged).

## Goal

Port the complete Cosmic suite — **194 enchants, 12 armor sets, 27 masks,
17 inventory pets** — into the `cosmic-pack` config pack, implemented the
StarEnchants way: every behavior decomposed into engine primitives and
authored as pack YAML, with the engine growing only general, reusable
capabilities.

The behavioral authority is the local-only codex at `deobf/cosmic/codex/`
(21 documents, number-exact, QA-consolidated, built from all 6 decompiled
jars). It stays gitignored; committed artifacts cite codex document names
only.

## Why attempt 1 was parked

Two failure modes, both confirmed by inspection:

1. **No decomposition.** Whole cosmic routines were transplanted into
   bespoke `Sink` mega-methods (`cosmicSilence`, `heroKiller`, `sabotage`,
   ~30 more) and ~45 per-feature listeners that bypass the activation
   pipeline entirely (own cooldown maps, own RNG, hand-rolled armor math).
   Effect kinds hardcoded cross-feature knowledge (a Dragon Slayer set
   check inside the Silence kind) and message strings.
2. **Wrong execution order.** Primitives were invented per-enchant at the
   moment of need instead of designed once from corpus-wide knowledge, so
   the engine surface accreted narrow, single-consumer kinds.

## Owner rulings (2026-08-01)

| # | Question | Ruling |
| --- | --- | --- |
| R1 | Prior WIP | Park verbatim on a `wip/` branch; restart clean. Consult per-item only, after codex-first decomposition. |
| R2 | Fidelity | As-intended port + deviation ledger. Provable bugs fixed; display strings verbatim (placeholders → brace tokens only). |
| R3 | Scope | The four families only — no economy/delivery items (books, orbs, Tinkerer, mystery boxes, etc. deferred). |
| R4 | Cadence | Staged PRs to `main`; content PRs do not wait for green CI — only engine-primitive waves (and the final milestone) get full verification. |
| R5 | Era | Modern-first; a dedicated legacy-1.8.9 sweep near the end. |
| R6 | Default pack | `signature-pack` (the renamed EE-derived pack) stays the boot default; `cosmic-pack` ships as an applyable preset. |
| R7 | Family order | Enchants → Sets → Masks → Pets. |
| R8 | Method | Full-corpus decomposition matrix first (document only), then staged primitive waves each followed by its content batches. |

## 1. Git & parallel-agent protocol

- Attempt 1 is parked on `wip/cosmic-import-attempt-1` (pushed). `main`'s
  working tree is clean again for parallel agents.
- Every PR below is a short-lived branch cut from **freshly fetched**
  `origin/main`. No force-pushes, no touching other branches, frequent
  Conventional Commits, rebase-merge per repo workflow.
- `deobf/` and `CosmicJars/` are gitignored and must never be committed;
  verify with `git add -A --dry-run` before any park-style bulk commit.

## 2. Sources of truth & salvage policy

- **The codex is authoritative** for mechanics, numbers, and strings —
  including its reliability protocol: closure-capture claims are
  inadmissible without `18-closure-capture-verification.md`; corpus
  unknowns stay `UNRESOLVED` and are never guessed. The decompiled
  sources settle disputes.
- The parked WIP is a cross-check, not a starting point: each item is
  decomposed from the codex **first**, then diffed against the WIP's YAML
  for missed details. WIP engine code is mined only as a checklist of
  capabilities it thought it needed, to test the matrix for missed gaps.

## 3. The decomposition matrix

The central artifact and the fix for failure #1. One committed doc per
codex category (~250 rows total). Each row:

| Column | Content |
| --- | --- |
| Codex ref | `doc § entry` |
| Activation | trigger / condition / selector mapping onto the existing vocabulary |
| Decomposition | ordered sequence of **existing named primitives** (the 88-kind surface at HEAD plus selectors/vars) |
| Gaps | residual *named capability requests* — what's missing, never which enchant wants it |
| Interactions | stacking, suppression, cross-feature rules touched |
| Strings | verbatim display-string inventory |
| Numbers | values + known-bug status + the as-intended value (ledger link if deviating) |
| Era | 1.8.9 hazards, noted now, fixed in the legacy sweep |

Gaps are clustered corpus-wide into a minimal primitive set; every
proposed primitive lists its consumers. The matrix is built by delegated
Opus agents in parallel with verification passes (per the standing
workflow-model allocation), and doubles as the port checklist and the
deviation-ledger skeleton.

## 4. The primitive bar

The fix for failure #2. Every engine change must pass, and wave PR
descriptions record the justification:

- A new kind/selector/var is **generic**: parameterized, named for what
  it does, zero enchant/set/mask names in engine code. Justified by ≥2
  matrix consumers, or irreducible after a documented decomposition
  attempt.
- The **Sink gains only narrow verbs** (one side-effect each) — never
  routines.
- Cross-feature interactions are authored in **pack YAML conditions**
  against the existing interaction/suppression layer, never inside a kind.
- State (cooldowns, counters, windows) lives in the existing stores/var
  system — no ad-hoc maps in listeners.
- New listeners only for genuinely new **event sources**, wired through
  TriggerDispatch into the activation pipeline — never per-feature.
- Each wave carries the standard gates: spec-conformance tests, declared
  Affinity, hot-path lint, fuzz corpus entries.

## 5. Phases & PR sequence

| # | PR | Verification |
| --- | --- | --- |
| 0 | `signature-pack` rename, redone cleanly (not lifted from the entangled WIP) | local build |
| 1 | Decomposition matrix docs + deviation-ledger skeleton (no code) | review only |
| 2 | **Engine wave 1**: combat-core primitives clustered from the enchant families | unit gate + targeted remote matrix |
| 3–9 | Enchant batches by codex category: armor A–L, armor M–Z, swords, axes, bows, tools+heroic, mastery+soul | pack compile + validation tests |
| 10 | **Engine wave 2**: set/mask/pet-support primitives | unit gate + targeted remote matrix |
| 11–13 | Sets (12) → Masks (27) → Pets (17) | pack validation |
| 14 | Legacy 1.8.9 era sweep over the whole pack | legacy + mega smoke gates |
| 15 | Pack polish (`config.yml`, lang), bundled-zip build wiring, website catalog | **live-matrix milestone** |

## 6. Fidelity & verification policy

- Display strings verbatim; placeholders swapped for brace tokens only.
- As-intended numbers: provable bugs (inverted pet cooldowns,
  integer-division no-ops, Corrupt's 100% proc, level-nullifying clamps)
  are fixed. **The defensive double-fire is never replicated** — shipped
  values are the single-pass intended numbers.
- Every deviation from measured jar behavior gets a `deviations.md` row:
  item, measured behavior, evidence pointer, shipped behavior, rationale.
- Content batches self-certify with the fast local pack gate; engine
  waves and the final milestone run on remote CI (never local matrix).

## 7. Risks

- **Matrix anchoring on WIP mistakes** — mitigated by codex-first
  ordering (§2).
- **Codex errors** — its own inadmissibility rules apply; disputes go to
  bytecode.
- **Wave-1 primitives misshapen for later families** — wave 2 may amend
  them; cheap while only pack YAML depends on them.
- **Working-tree contention with parallel agents** — solved by §1.
