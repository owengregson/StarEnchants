# Cosmic Port — PR 0 (signature-pack rename) + PR 1 (decomposition matrix) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the clean `signature-pack` rename (PR 0) and produce the full-corpus
decomposition matrix + clustered primitive proposals (PR 1) per the approved spec
`docs/superpowers/specs/2026-08-01-cosmic-suite-port-redo-design.md`.

**Architecture:** PR 0 is a mechanical rename of the shipped EE-derived pack
(`cosmic-pack` → `signature-pack`) across pack tree, build wiring, tests, and living
docs — freeing the `cosmic-pack` name. PR 1 is document production: delegated Opus
agents decompose all ~250 codex items onto the existing engine surface, then the main
loop clusters residual gaps into proposed primitives — the input that defines engine
wave 1. No engine code changes in either PR.

**Tech Stack:** Gradle (Java), git worktrees, Workflow tool (Opus subagents), markdown.

## Global Constraints

- **Parallel-agent git protocol (binding for every step):** all operations run inside
  the dedicated worktree `/Users/owengregson/Documents/StarEnchants/.claude/worktrees/cosmic-port`.
  NEVER cd into, build in, or switch branches of the primary checkout
  `/Users/owengregson/Documents/StarEnchants` — other agents own it. Branches are cut
  from freshly-fetched `origin/main` inside this worktree. No force-pushes. PRs land
  via `gh pr merge --rebase --auto` (honors branch protection without blocking us —
  per owner ruling R4, we do not sit waiting for green on docs/content PRs).
- **Codex access is read-only and by absolute path:**
  `/Users/owengregson/Documents/StarEnchants/deobf/cosmic/codex/` (gitignored, exists
  only in the primary checkout — worktrees don't materialize it). Agents may READ it;
  nothing under `deobf/` or `CosmicJars/` is ever committed.
- **Branding rule in committed artifacts:** cite codex documents by filename
  (e.g. `01-enchants-armor-a-l.md § Aegis`); never quote decompiled Java, class
  names, or package names in committed files.
- **ADRs are historical records** — `docs/decisions/*.md` are never edited by the
  rename; only living docs are updated.
- **Commit style:** Conventional Commits, ending with
  `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` and the Claude-Session
  trailer.
- **Owner rulings R1–R8** in the spec govern all judgment calls.

---

### Task 1: Baseline build in the worktree

**Files:** none (verification only)

**Interfaces:**

- Produces: a known-green baseline; all later failures are attributable to our changes.

- [ ] **Step 1: Fetch and confirm branch state**

Run: `git fetch origin && git status --short && git branch --show-current`
Expected: clean tree, branch `docs/cosmic-port-spec`.

- [ ] **Step 2: Run the unit gate**

Run: `./gradlew build -q 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`. Gradle runs against this worktree's own `build/`
directories, so it cannot collide with other agents' builds.
If it fails: STOP — report the failure; do not proceed on a dirty baseline.

---

### Task 2: Open the spec/plan PR (auto-merge)

**Files:**

- Commit: `docs/superpowers/plans/2026-08-01-cosmic-port-pr0-rename-pr1-matrix.md` (this file)

- [ ] **Step 1: Commit the plan onto `docs/cosmic-port-spec`**

```bash
git add docs/superpowers/plans/2026-08-01-cosmic-port-pr0-rename-pr1-matrix.md
git commit -m "docs(plans): implementation plan for cosmic-port PR 0 + PR 1"
git push
```

- [ ] **Step 2: Open the PR with auto-merge**

```bash
gh pr create --base main --head docs/cosmic-port-spec \
  --title "docs: cosmic-suite port redo — approved spec + PR0/PR1 plan" \
  --body "Records the approved design (owner rulings R1–R8) for the cosmic-suite port redo and the implementation plan for PR 0 (signature-pack rename) and PR 1 (decomposition matrix). Attempt 1 is parked on wip/cosmic-import-attempt-1. Analysis/docs only — no code."
gh pr merge --rebase --auto
```

Expected: PR opens; auto-merge armed.

---

### Task 3: PR 0 — pack tree rename + build wiring

**Files:**

- Rename: `se/bootstrap/packs-src/cosmic-pack/` → `se/bootstrap/packs-src/signature-pack/`
- Modify: `se/bootstrap/build.gradle.kts:46-48,116-119,130`
- Modify: `se/bootstrap/resources/packs/index.txt`
- Modify: `se/bootstrap/packs-src/signature-pack/pack.yml` (then regenerate)

**Interfaces:**

- Produces: Gradle task `packSignaturePack` producing `signature-pack.zip`; manifest
  property name `signaturePackManifest`. Tasks 4–5 rely on these exact names.

- [ ] **Step 1: Cut the PR 0 branch from fresh origin/main**

```bash
git fetch origin && git switch -c chore/signature-pack-rename origin/main
```

- [ ] **Step 2: Rename the pack tree**

```bash
git mv se/bootstrap/packs-src/cosmic-pack se/bootstrap/packs-src/signature-pack
```

- [ ] **Step 3: Update `se/bootstrap/build.gradle.kts`**

Line 46-48 — task registration becomes:

```kotlin
val packSignaturePack = tasks.register<Zip>("packSignaturePack") {
    from(layout.projectDirectory.dir("packs-src/signature-pack"))
    archiveFileName.set("signature-pack.zip")
```

Line 116-119 — manifest input becomes (comment updated to say "signature-pack
manifest golden"):

```kotlin
    inputs.files(layout.projectDirectory.file("packs-src/signature-pack/pack.yml"))
        .withPropertyName("signaturePackManifest").optional()
```

Line 130 — `from(packCosmicPack)` → `from(packSignaturePack)`.

- [ ] **Step 4: Update `se/bootstrap/resources/packs/index.txt`**

Replace the archive line `cosmic-pack.zip` → `signature-pack.zip`, and in the header
comment replace "The shipped cosmic-pack.zip is produced at build time from
se/bootstrap/packs-src/cosmic-pack/" with the signature-pack paths.

- [ ] **Step 5: Update `pack.yml` identity fields manually**

In `se/bootstrap/packs-src/signature-pack/pack.yml`: header comment
`/se pack apply cosmic-pack` → `/se pack apply signature-pack`;
`name: "cosmic-pack"` → `name: "signature-pack"`;
`description:` → `"The Signature Pack — StarEnchants' complete flagship set of enchants, armour sets, and economy items."`.
Leave `author`, `format`, `created` untouched (same pack, new name).

- [ ] **Step 6: Regenerate the fingerprint**

Run: `./gradlew :bootstrap:regenDocs -q 2>&1 | tail -5`
Expected: SUCCESS; `git diff --stat` shows `pack.yml` fingerprint/surface lines
regenerated. (The pack name participates in the ABI fingerprint — ADR-0046.)

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "chore(packs): rename cosmic-pack to signature-pack (tree + build wiring)"
```

---

### Task 4: PR 0 — test renames

**Files:**

- Rename: `se/bootstrap/test/bootstrap/CosmicPackValidationTest.java` → `SignaturePackValidationTest.java`
- Rename: `se/bootstrap/test/bootstrap/CosmicPackFingerprintDriftTest.java` → `SignaturePackFingerprintDriftTest.java`
- Modify: `se/bootstrap/test/bootstrap/ModernHandleEraTest.java:41-42`
- Modify: `se/bootstrap/test/bootstrap/SeCommandCompletionTest.java:16,143,146-147`

**Interfaces:**

- Consumes: task name `packSignaturePack` and path `packs-src/signature-pack` from Task 3.

- [ ] **Step 1: Rename the two test files and their contents**

```bash
git mv se/bootstrap/test/bootstrap/CosmicPackValidationTest.java se/bootstrap/test/bootstrap/SignaturePackValidationTest.java
git mv se/bootstrap/test/bootstrap/CosmicPackFingerprintDriftTest.java se/bootstrap/test/bootstrap/SignaturePackFingerprintDriftTest.java
```

In `SignaturePackValidationTest.java`: class name → `SignaturePackValidationTest`;
`Path PACK = Path.of("packs-src/signature-pack")`; method names
`cosmicPackContentCompilesClean` → `signaturePackContentCompilesClean`,
`cosmicPackItemsLoadClean` → `signaturePackItemsLoadClean`,
`cosmicPackMenusLoadClean` → `signaturePackMenusLoadClean`,
`cosmicPackMasterConfigLoadsClean` → `signaturePackMasterConfigLoadsClean`,
`cosmicPackHasOnlySurfaceRootsAtTopLevel` → `signaturePackHasOnlySurfaceRootsAtTopLevel`;
javadoc `{@code cosmic-pack}` → `{@code signature-pack}`; the line-137 comment
`packCosmicPack` → `packSignaturePack` and `cosmic-pack.zip` → `signature-pack.zip`;
assertion message `"cosmic-pack has top-level entries..."` → `"signature-pack has..."`.

In `SignaturePackFingerprintDriftTest.java`: class name →
`SignaturePackFingerprintDriftTest`; `PACK_YML = Path.of("packs-src/signature-pack/pack.yml")`;
the fingerprint-compute call's pack-name argument `"cosmic-pack"` → `"signature-pack"`;
all `cosmic-pack/pack.yml ...` failure messages → `signature-pack/pack.yml ...`;
javadoc `packCosmicPack` → `packSignaturePack`.

- [ ] **Step 2: Update `ModernHandleEraTest.java`**

Line 41-42: method `cosmicPackResolvesOnModernEra` → `signaturePackResolvesOnModernEra`;
`compileClean(Path.of("packs-src/signature-pack/content"), era, 400);`.

- [ ] **Step 3: Update `SeCommandCompletionTest.java`**

Line 16: `PACKS = List.of("signature-pack", "vanilla-plus")`.
Line 143: `assertEquals(List.of("signature-pack"), completePack("pack", "apply", "sig"));`
Lines 146-147: replace argument `"cosmic-pack"` → `"signature-pack"` in both
`completePack(...)` calls (comments unchanged).

- [ ] **Step 4: Run the bootstrap tests**

Run: `./gradlew :bootstrap:test -q 2>&1 | tail -10`
Expected: PASS (fingerprint test passes because Task 3 Step 6 regenerated pack.yml).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "test(bootstrap): rename cosmic-pack tests to signature-pack"
```

---

### Task 5: PR 0 — tester, imagegen, migrate wiring

**Files:**

- Modify: `se/tester/build.gradle.kts:61-74`
- Modify: `se/tester/src/tester/suite/CatalogSuite.java:24,39,42-43`
- Modify: `se/imagegen/imports.yml:14`
- Modify: `se/imagegen/src/imagegen/imports/ImportManifest.java:21`
- Modify: `se/migrate/test/migrate/EePortGenerator.java:26`

**Interfaces:**

- Produces: tester bundle root `pack-signature/` and expectation id
  `catalog.signaturePackCompilesCleanWithRealHandles` (the id is declared and guarded
  only inside CatalogSuite — verified by grep, no external manifest references it).

- [ ] **Step 1: Update `se/tester/build.gradle.kts`**

Lines 61-74: comment "the shipped cosmic pack's content" → "the shipped signature
pack's content"; `val cosmicContent` → `val signatureContent` with
`rootProject.file("se/bootstrap/packs-src/signature-pack/content")`; index dir
`generated/pack-cosmic-index` → `generated/pack-signature-index`; both `into(...)`
targets `pack-cosmic/content` / `pack-cosmic` → `pack-signature/content` /
`pack-signature`; `val root = signatureContent.toPath()`.

- [ ] **Step 2: Update `CatalogSuite.java`**

Line 24 comment: "the bundled cosmic pack" → "the bundled signature pack".
Lines 39, 42-43:

```java
h.expect("catalog.signaturePackCompilesCleanWithRealHandles");
// ...
h.guard("catalog.signaturePackCompilesCleanWithRealHandles",
        () -> compileClean("pack-signature", 400, "signature pack"));
```

- [ ] **Step 3: Update imagegen + migrate references**

`se/imagegen/imports.yml:14`: `root: se/bootstrap/packs-src/signature-pack/content`.
`ImportManifest.java:21` javadoc example path → `packs-src/signature-pack/content`.
`EePortGenerator.java:26` javadoc: `{@code cosmic-pack}` → `{@code signature-pack}`.

- [ ] **Step 4: Compile the touched modules**

Run: `./gradlew :tester:compileJava :imagegen:compileJava :migrate:compileTestJava -q 2>&1 | tail -5`
Expected: SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore(tester,imagegen,migrate): follow the signature-pack rename"
```

---

### Task 6: PR 0 — living docs + straggler sweep

**Files:**

- Modify: `docs/dev/internals/config-packs.md` (1 mention)
- Modify: `docs/dev/internals/the-migrator.md` (1 mention)

- [ ] **Step 1: Update the two living-doc mentions**

In each file, replace the `cosmic-pack` reference with `signature-pack` (adjusting
surrounding prose if it says "first shipped pack" — it may note "renamed from
cosmic-pack in v1.13" for reader continuity).

- [ ] **Step 2: Straggler grep — the safety net**

Run:

```bash
grep -rn 'cosmic-pack\|packCosmicPack\|pack-cosmic\|cosmicPack' \
  --include='*.java' --include='*.kts' --include='*.yml' --include='*.txt' \
  --include='*.sh' --include='*.md' \
  se scripts tools .github docs website/docs website/src 2>/dev/null \
  | grep -v 'docs/decisions/' | grep -v 'docs/superpowers/' | grep -v 'build/'
```

Expected: zero hits. Any hit is a missed reference — fix it the same way and re-run
until clean. (ADRs and the spec/plan are intentionally excluded; `build/` dirs are
stale outputs.)

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "docs(internals): follow the signature-pack rename"
```

---

### Task 7: PR 0 — verify, PR, auto-merge

- [ ] **Step 1: Full unit gate**

Run: `./gradlew build -q 2>&1 | tail -10`
Expected: `BUILD SUCCESSFUL` (owner ruling R4: local build is PR 0's gate).

- [ ] **Step 2: Push and open the PR**

```bash
git push -u origin chore/signature-pack-rename
gh pr create --base main --head chore/signature-pack-rename \
  --title "chore(packs): rename cosmic-pack to signature-pack" \
  --body "Frees the cosmic-pack name for the true Cosmic suite port (spec: docs/superpowers/specs/2026-08-01-cosmic-suite-port-redo-design.md, ruling R6). Mechanical rename: pack tree, build wiring (packSignaturePack / signature-pack.zip), regenerated ABI fingerprint, tests, tester bundle, imagegen/migrate refs, living docs. ADRs untouched. signature-pack remains the boot default."
gh pr merge --rebase --auto
```

Expected: PR opens, auto-merge armed; we proceed immediately (R4).

---

### Task 8: PR 1 — scaffold the matrix workspace

**Files:**

- Create: `docs/dev/cosmic-port/README.md`
- Create: `docs/dev/cosmic-port/deviations.md`

**Interfaces:**

- Produces: the matrix item format and directory layout consumed by Tasks 9–10.

- [ ] **Step 1: Cut the PR 1 branch**

```bash
git fetch origin && git switch -c docs/cosmic-decomposition-matrix origin/main
mkdir -p docs/dev/cosmic-port/matrix
```

- [ ] **Step 2: Write `docs/dev/cosmic-port/README.md`**

Content — the matrix contract (verbatim):

```markdown
# Cosmic port — decomposition matrix

Working documents for the cosmic-suite port
(spec: `../../superpowers/specs/2026-08-01-cosmic-suite-port-redo-design.md`).
One matrix doc per codex category; one entry per ported item. The behavioral
authority is the local-only codex (gitignored); entries cite codex documents by
filename and never quote decompiled code.

## Entry format

### <Display Name> (`<family>/<slug>`)
- **codex:** `<codex doc> § <entry>`
- **activation:** trigger + condition/selector mapping (existing vocabulary names)
- **decomposition:** ordered `KIND(param=value, …)` sequence using ONLY primitives
  present in `docs/reference/authoring-surface.txt` at HEAD
- **gaps:** `NAME — semantics; params; consumers` for capabilities the surface
  lacks. Names describe the capability, never an enchant (`ATTACKER_INDEX_WINDOW`,
  not `AEGIS_EFFECT`). Omit the line when fully expressible.
- **interactions:** stacking / suppression / cross-feature rules touched
- **strings:** verbatim display strings (placeholders → brace tokens only)
- **numbers:** per-level values; known-bug status with the as-intended value and a
  `deviations.md` row id when deviating from measured jar behavior
- **era:** 1.8.9 hazards (materials/sounds/particles/API), noted for the legacy sweep

## Files

| Doc | Source codex doc | Items |
| --- | --- | ---: |
| `matrix/01-enchants-armor-a-l.md` | 01 | 36 |
| `matrix/02-enchants-armor-m-z.md` | 02 | 31 |
| `matrix/03-enchants-swords.md` | 03 | 36 |
| `matrix/04-enchants-axes.md` | 04 | 21 |
| `matrix/05-enchants-bows.md` | 05 | 22 |
| `matrix/06-enchants-tools-heroic.md` | 06 | 36 |
| `matrix/07-enchants-mastery-soul.md` | 07 | 16 |
| `matrix/10-armor-sets.md` | 10 | 12 |
| `matrix/11-masks.md` | 11 | 27 |
| `matrix/12-pets.md` | 12 | 17 |

`proposed-primitives.md` clusters every gap into the minimal general primitive set
(spec §4 bar) and assigns each to engine wave 1 or 2.
```

- [ ] **Step 3: Write `docs/dev/cosmic-port/deviations.md`**

```markdown
# Deviation ledger

Every divergence between measured cosmic-jar behavior and what StarEnchants ships
(spec §6, ruling R2). Display strings never deviate.

| ID | Item | Measured jar behavior | Evidence (codex ref) | Shipped behavior | Rationale |
| --- | --- | --- | --- | --- | --- |
| D-001 | (defensive family) | Defensive passes fire twice per melee hit; 32 enchants apply twice | `00-MECHANICS.md` §3 | Single defensive pass; single-pass intended values | Double-fire is a listener-registration bug, never replicated (spec §6) |
```

- [ ] **Step 4: Commit**

```bash
git add docs/dev/cosmic-port
git commit -m "docs(cosmic-port): scaffold the decomposition matrix + deviation ledger"
```

---

### Task 9: PR 1 — enchant matrix docs (Workflow, 7 authors + verify)

**Files:**

- Create: `docs/dev/cosmic-port/matrix/01-enchants-armor-a-l.md` … `07-enchants-mastery-soul.md`

**Interfaces:**

- Consumes: entry format from Task 8; codex docs (read-only, absolute path);
  `docs/reference/authoring-surface.txt` (this worktree's copy at HEAD).
- Produces: 7 matrix docs whose `gaps:` lines feed Task 10.

- [ ] **Step 1: Launch the authoring workflow**

One `agent()` per codex doc 01–07, `model: 'opus'`, writing its matrix doc directly
into this worktree. Author brief (per category, with `<N>` substituted):

> Read `/Users/owengregson/Documents/StarEnchants/deobf/cosmic/codex/<N>.md`
> (READ-ONLY reference; never copy decompiled code, class or package names) and this
> worktree's `docs/reference/authoring-surface.txt` (the complete engine surface:
> effects, selectors, triggers, operators, vars — the ONLY primitives that exist).
> For EVERY item in the codex doc, write an entry in the exact format defined by
> `docs/dev/cosmic-port/README.md` into `docs/dev/cosmic-port/matrix/<N>.md`.
> Decompose behavior into existing primitives FIRST; a `gaps:` line is a last resort
> and must name a generic capability (semantics + params + why existing primitives
> cannot express it). Numbers must be copied exactly from the codex, including its
> known-bug annotations; where the codex marks a bug, also state the as-intended
> value. Strings verbatim. Return JSON: `{doc, items, gapNames: [...]}`.

Pipeline each author into a verifier agent (`model: 'opus'`) with this brief:

> Verify `docs/dev/cosmic-port/matrix/<N>.md` against
> `/Users/owengregson/Documents/StarEnchants/deobf/cosmic/codex/<N>.md`:
> (1) entry count matches the codex item count; (2) every primitive named in a
> `decomposition:` line exists in `docs/reference/authoring-surface.txt` — list any
> that don't; (3) pick 3 entries at random and check every number against the codex;
> (4) no decompiled identifiers appear. Fix small errors in place; return JSON
> `{doc, entries, fixed: [...], unfixable: [...]}`.

Expected: 7 docs written; all verifiers return empty `unfixable`.

- [ ] **Step 2: Spot-check and commit**

Manually read 2 entries per doc against the codex (main loop). Then:

```bash
git add docs/dev/cosmic-port/matrix
git commit -m "docs(cosmic-port): decomposition matrix for the seven enchant families"
```

---

### Task 10: PR 1 — sets/masks/pets matrix docs (Workflow, 3 authors + verify)

**Files:**

- Create: `docs/dev/cosmic-port/matrix/10-armor-sets.md`, `11-masks.md`, `12-pets.md`

Same briefs as Task 9 with `<N>` ∈ {`10-armor-sets`, `11-masks`, `12-pets`}. Sets
entries additionally record set-bonus tiers, crystal interactions, and summon
abilities in `interactions:`; pets record charge/cooldown semantics in `numbers:`
(the codex documents the inverted-cooldown bug — as-intended values required, ledger
row per pet family).

- [ ] **Step 1: Launch the workflow (3 authors → 3 verifiers, `model: 'opus'`)**

Expected: 3 docs; empty `unfixable`.

- [ ] **Step 2: Spot-check and commit**

```bash
git add docs/dev/cosmic-port/matrix
git commit -m "docs(cosmic-port): decomposition matrix for sets, masks, and pets"
```

---

### Task 11: PR 1 — gap clustering (main loop, no delegation)

**Files:**

- Create: `docs/dev/cosmic-port/proposed-primitives.md`
- Modify: `docs/dev/cosmic-port/deviations.md` (rows discovered during the matrix)

**Interfaces:**

- Consumes: every `gaps:` line across the 10 matrix docs.
- Produces: the wave-1/wave-2 primitive list — the direct input to the next plan.

- [ ] **Step 1: Extract and cluster all gaps**

Run: `grep -h '^- \*\*gaps:' docs/dev/cosmic-port/matrix/*.md` and cluster
semantically identical requests. For each cluster write an entry:

```markdown
### <PRIMITIVE_NAME> (wave 1|2)
- **kind:** effect | selector | condition | var | trigger | store
- **semantics:** one paragraph, generic, no item names in the mechanism
- **params:** name: type/range/default, …
- **consumers:** `family/slug`, … (≥2, or a documented irreducibility argument)
- **bar check:** how it satisfies each spec §4 rule
```

Wave assignment: needed by enchant families → wave 1; needed only by
sets/masks/pets → wave 2.

- [ ] **Step 2: Self-review against the bar**

Reject any cluster that (a) encodes one item's routine, (b) duplicates an existing
primitive's semantics with different parameters (extend the existing kind instead —
note it as `EXTEND: <KIND>`), or (c) belongs in pack YAML conditions (interaction
logic). Rejected clusters get re-decomposed in the matrix doc instead.

- [ ] **Step 3: Commit, push, PR, auto-merge**

```bash
git add docs/dev/cosmic-port
git commit -m "docs(cosmic-port): cluster matrix gaps into proposed primitives"
git push -u origin docs/cosmic-decomposition-matrix
gh pr create --base main --head docs/cosmic-decomposition-matrix \
  --title "docs(cosmic-port): full-corpus decomposition matrix + proposed primitives" \
  --body "PR 1 of the cosmic-suite port (spec R8): all ~250 items decomposed onto the existing engine surface; residual gaps clustered into general primitives with consumers and wave assignments. Analysis only — no code. Feeds the engine wave-1 plan."
gh pr merge --rebase --auto
```

---

### Task 12: Generate the engine wave-1 plan

- [ ] **Step 1: Invoke writing-plans with the matrix in hand**

With `proposed-primitives.md` as the requirements input, write
`docs/superpowers/plans/<date>-cosmic-port-engine-wave-1.md`: one TDD task per
wave-1 primitive (spec-conformance test first, minimal kind, Affinity declaration,
fuzz corpus entry, hot-path lint), per spec §5 row 2. That plan — not this one —
governs the first engine PR, and it waits for full verification (unit gate +
targeted remote matrix) per ruling R4.

## Self-review record

- **Spec coverage:** PR 0 ↔ spec §5 row 0 (rename, local build gate); PR 1 ↔ rows 1
  (matrix + ledger) and the §3 matrix contract; Task 11 ↔ §3 clustering + §4 bar;
  Task 12 hands off §5 row 2. Later rows (3–15) are deliberately out of scope until
  the matrix exists (R8).
- **Placeholders:** none — every edit lists exact files/lines/replacement text;
  agent briefs are verbatim.
- **Name consistency:** `packSignaturePack` / `signature-pack.zip` /
  `signaturePackManifest` / `pack-signature` /
  `catalog.signaturePackCompilesCleanWithRealHandles` used identically in Tasks 3–7.
