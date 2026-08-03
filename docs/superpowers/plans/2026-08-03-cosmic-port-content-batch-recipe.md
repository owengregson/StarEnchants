# Cosmic Port — Content Batch Recipe + Batch 1 (armor A–L) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The reusable recipe for content batches 1–7 (spec §5 rows 3–9), plus
the batch-1 specifics: the `cosmic-pack` scaffold and the 36 armor A–L enchants
from `docs/dev/cosmic-port/matrix/01-enchants-armor-a-l.md`.

**Mode shift:** content batches are TRANSLATION, not design — every entry's
decomposition, numbers, verbatim strings, and interaction rules are already in
the matrix (adversarially verified). The engine surface is frozen (wave 1,
PRs #267–#277). Authoring is Opus-delegated; the compile gate is the
mechanical floor; number-diff verification against the matrix is the fidelity
check. Content PRs do not wait for full CI (ruling R4) — they self-certify
with the local pack gate.

## Global Constraints

- Spec + rulings R1–R9. Binding skills: `starenchants-conventions`,
  `config-and-migration` (authoring surface), `feature-interaction-rules`,
  `writing-tests`, `code-comments`. Parallel-agent git protocol.
- **Verbatim strings** (R2): display/lore/messages copied exactly from the
  matrix; placeholders as brace tokens. Deviation = matrix ledger ID cited in
  a YAML comment.
- **The matrix is the authority.** An author who thinks a matrix entry is
  wrong STOPs and reports — never "fixes" content against the codex directly.
- **No ship wiring until the polish PR**: the pack tree is source + tests
  only. NO `build.gradle.kts` zip task, NO `packs/index.txt` line — an
  operator cannot apply a half-built pack.
- One enchant per file (`content/enchants/<slug>.yml`), matching
  signature-pack file conventions (envelope keys per level: `chance`,
  `cooldown`, `condition`, `effects`; `abilities:` list per level for
  multi-hook entries, #268 shape; `soul-cost` / `no-souls-message` where the
  matrix says so).

## Batch 1 only: the scaffold

- [ ] Branch `feat/cosmic-content-armor-a-l` from freshly-fetched
  `origin/main` (must contain PR #266 — the matrix docs; verify
  `docs/dev/cosmic-port/matrix/` exists before starting, STOP if not).
- [ ] Create `se/bootstrap/packs-src/cosmic-pack/`:
  - `pack.yml` — name `cosmic-pack`, description
    `"The Cosmic Pack — the classic Cosmic experience, ported number-exact."`,
    format 1; fingerprint/surface lines regenerate via
    `./gradlew :bootstrap:regenDocs` ONLY if the regen task covers this pack
    (it is keyed to signature-pack — if so, hand-write the manifest WITHOUT
    fingerprint lines and note that the drift test wiring joins at the polish
    PR; STOP only if neither shape validates).
  - `config.yml` + `lang.yml` — minimal viable: copy signature-pack's files
    and strip to defaults (no re-tuning; the cosmic-specific knobs arrive
    with their features in later batches).
  - `content/tiers.yml` — the COSMIC tier ladder, derived from the matrix
    corpus (the `numbers:` lines carry `tier N` per enchant, 1–6 plus the
    heroic/soul groupings; colors per the codex tier colors recorded in the
    matrix strings). Weights ascending like signature-pack's shape.
  - `content/enchants/` (batch 1 fills it), empty `items/`, `menus/`.
- [ ] Validation gate: `se/bootstrap/test/bootstrap/CosmicPackValidationTest.java`
  — mirror `SignaturePackValidationTest`'s shape (content compiles clean
  through the real loaders + permissive fake resolvers; items/menus loaders on
  the empty dirs; master config loads). This test is the batch gate for every
  later content PR.
- [ ] Commit scaffold; `./gradlew :bootstrap:test` green.

## The per-batch recipe (batches 1–7)

1. **Author fan-out (Opus, ~6 entries per agent):** each author translates
   their matrix entries to YAML. Inputs per agent: the matrix doc section
   (verbatim), `docs/reference/authoring-surface.txt` + `dsl-reference.md`
   at HEAD, two shipped signature-pack enchants as shape reference, the R9
   note rule. Envelope mapping: matrix `activation:` → trigger + condition +
   chance/cooldown envelope; `decomposition:` → effects list (per level where
   numbers scale); multi-hook entries → `abilities:`; era hazards → a YAML
   comment citing the matrix (the legacy sweep consumes them).
2. **Verify fan-out (Opus):** per authored file — every number diffed against
   the matrix `numbers:` line; every string byte-diffed; every KIND/param
   exists in the authoring surface; ledger IDs cited where the matrix cites
   them; interactions authored as conditions match the `interactions:` line.
   Fix small errors in place; structural mismatches → report.
3. **Compile loop:** run the pack validation test; feed each diagnostic back
   to a fixer until clean. (Diagnostics carry file/line — mechanical.)
4. **Spot-check (main loop):** 2–3 entries read against the matrix.
5. **PR:** `feat(content): cosmic <category> (<n> enchants)`, body = entry
   list + any STOPs; `--rebase --auto`, no waiting (R4); the pack gate result
   pasted in the body.

## Batch 1 roster

The 36 entries of matrix/01, Aegis → Nimble, one file each
(`enchants/aegis.yml` … — slugs from the matrix headers). Known batch-1
specifics: the metadata-keyed family note (D-01-15) is engine-native
(WornState) — no YAML expression needed; Guardians/Spirits ride the extended
GUARD params (#275); Avenging Angel + Blood Lust ride PROXIMITY_EVENT (#274,
player-death only — Blood Lust's tagged-effect leech variant is wave-2
deferred: author its death-adjacent ability now, leave a matrix-cited comment
for the leech); death-saves gate on `%posthit.health%` (#273).

## After batch 1

Batches 2–7 reuse the recipe against matrix docs 02–07 in order. Doc 07
(mastery+soul) WAITS for engine wave 2 (spec §5). Sets/masks/pets batches get
their own recipe addendum (items/ definitions join there).

## Self-review record

- Recipe encodes the verified-translation discipline end to end; the one
  scaffold unknown (regenDocs pack coverage) carries a graceful fallback and
  a STOP.
- R9 has no batch-1 consumers (Auto Sell is doc 07) — noted so authors don't
  hunt for it.
- No ship wiring: stated twice deliberately (constraint + scaffold).
