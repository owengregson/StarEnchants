# ADR 0046: Pack ABI fingerprints — a stamped registry fingerprint + a dry-run compile gate on apply

- **Status:** Accepted
- **Date:** 2026-07-02
- **Deciders:** project owner + engine/config work
- **Relates to:** ADR-0023 (config packs), ADR-0029 (`validateCandidate` dry-run precedent),
  ADR-0038 (add-on effect kinds fold into the live registry), ADR-0042 (closed `DiagCode`,
  non-throwing loaders), docs/architecture.md §7 (the `ParamSpec` four-ways single source), §10 (the
  transactional reload)

## Context

A config pack (ADR-0023) is a ZIP of the whole authored surface — `content/`, `items/`, `config.yml`,
`lang.yml`, `menus/` — swapped over the live config by `/se pack apply`. Nothing checked that a pack
authored on one server would still load on another: a pack that used an effect head, a parameter, a
condition operator, or a `%var%` that a different StarEnchants build does not have swapped the disk surface
and then failed the reload — leaving the operator with an on-disk surface the running server could not
compile. Packs were versioned artifacts with no ABI check, like shipping a jar with no version handshake.

We want a pack to be checked against the running server's **content-authoring surface** the way a class file
is checked against a JVM: a fast compatibility signal plus, decisively, a real compile — before a single
live file is touched.

## Decision

**1. A registry fingerprint (`engine.boot.RegistryFingerprint`).** A stable SHA-256 over a canonical
serialization of the live authoring surface: effect heads + per-kind `ParamSpec`, selector heads +
`ParamSpec`, trigger names, the condition operator vocabulary, and the `%var%` vocabulary. The fingerprint is
`"1:" + lowercase-hex(SHA-256(UTF-8(canonical)))`; the leading `1:` versions the **serialization itself** — a
format change bumps to `2:` and every older fingerprint compares unequal. Chat shows a short form (`1:` +
first 12 hex). A manual hex loop (not `java.util.HexFormat`, JDK 17+) keeps the class in the v52 legacy tree.

The canonical text is normative (this ADR is where the format lives):

```text
starenchants authoring surface v1
[effects]
effect <HEAD>
  param <name> <kind> <required|optional> min=<num|-> max=<num|-> enum=<v1,v2|-> handle=<CATEGORY|-> default=<raw|->
  …one line per param, in DECLARED order…
…one block per effect, heads sorted (String.compareTo on the canonical upper-case head)…
[selectors]
selector <HEAD>
  param …same line shape, declared order…
…heads sorted…
[triggers]
trigger <NAME>
…sorted…
[operators]
cmp <symbol>
…sorted by symbol…
strop <symbol>
…sorted by symbol…
flow <name-lowercase>
…sorted…
flowmod chance
[vars]
var <scope.name> <NUM|BOOL|STR>
…sorted by key…
```

Field rules: `<HEAD>` is the canonical upper-case head; `<kind>` is `ParamType.Kind.name()`; param
**declared order is significant** (positional parsing makes order part of the ABI) so params are NOT sorted;
`min`/`max` render an integral value without a decimal (absent → `-`); `enum=` is the allowed values sorted
and comma-joined (absent → `-`); `handle=` is the `HandleCategory` (non-HANDLE → `-`); `default=` is the raw
default verbatim to end of line (it may contain spaces, so it is last; absent → `-`). Heads, enum values,
triggers, operators, and vars-by-key are **sorted** so registration/declaration order — including add-on
registration order — can never perturb the hash. `\n` separators; one trailing `\n`; no locale-dependent
formatting (`String.compareTo`, `Locale.ROOT` where any case mapping is needed).

**Documented exclusions** (surface-irrelevant): `doc`/`example` and per-param docs (prose, not surface);
`Affinity`, target slots / default selector, `needsActorOrigin` (runtime routing — they change behaviour,
never authorability); `CrossRule`s (unserializable lambdas — a cross-rule change is caught by the dry-run,
which is the truth); dense kind ids, trigger mask bit positions, and `%var%` slot indices (per-run
accelerators).

**2. Export stamps; the shipped pack is drift-guarded.** `/se pack export` stamps the live fingerprint and a
human-readable surface summary into `pack.yml` (new `fingerprint:`/`surface:` keys, written only when
non-empty so a pre-0046 unstamped export round-trips byte-identically). The shipped cosmic pack is stamped by
a committed, drift-guarded manifest — the repo's established `*DriftTest` pattern (regen-on-change, the Gradle
Zip task stays dumb). The canonical surface is committed at `docs/reference/authoring-surface.txt`, so adding
a kind or parameter is a reviewed one-block diff.

**3. An apply gate (`bootstrap.PackGate`).** `/se pack apply` first compares fingerprints, then — regardless
of the match — DRY-RUN compiles the whole pack surface against the live registry via the real loaders (the
`LibraryLoader` + the live compiler for `content/`, and `ItemsLoader`/`MasterConfigLoader`/`LangLoader`/
`MenusLoader` for the rest) in a throwaway staging dir, and aborts before touching disk when any authored
line would fail — printing each failure as `file:line:col: error[CODE]: message` via `Diagnostic.render()`.
`--force` overrides. The fingerprint is the fast explanation; the dry-run is the truth.

The policy is:

| Fingerprint | Dry-run result | Default | With `--force` |
|---|---|---|---|
| MATCH | clean | proceed (`gate-clean`) | same |
| MATCH | warnings only | proceed (`gate-warnings` count) | same |
| MATCH | errors | **abort**: every blocking diagnostic + `gate-abort` | proceed (`gate-forced`) |
| MISMATCH | clean | proceed (informational `gate-mismatch`) | same |
| MISMATCH | warnings only | proceed (`gate-mismatch` + `gate-warnings`) | same |
| MISMATCH | errors | **abort**: `gate-mismatch` + blocking lines + `gate-abort` | proceed (`gate-forced`) |
| UNSTAMPED | any | as MISMATCH, but the explanation line is `gate-unstamped` | same |

The fingerprint check NEVER aborts by itself (a MATCH can still carry errors; a MISMATCH can still compile
clean). Errors render every blocking diagnostic (no cap); warnings are a count (the full list is on
`/se problems` after the post-apply reload). An abort leaves the disk untouched, no backup, no reload. A
forced apply of an erroring pack swaps disk but the transactional reload keeps the OLD content in memory
until the errors are fixed — `gate-forced` states exactly that. The gate compiles the whole surface, not just
`content/`, because a broken `lang.yml` or `config.yml` would otherwise swap disk and then fail the reload —
the precise failure mode this feature exists to prevent.

**4. Add-on semantics (ADR-0038).** The fingerprint is computed from the **live** `EffectRegistry`
(builtins + registered add-on kinds); selectors/triggers/vars remain builtin-only, exactly mirroring
`ContentCompiler.production`'s sourcing. So a pack exported on a server with add-on X bakes X's heads into its
fingerprint; applied where X is absent it reads MISMATCH, and if the pack authors X's kinds the dry-run
reports `E_UNKNOWN_KIND` per line and aborts. A vanilla pack applied on a server WITH add-ons reads an
informational MISMATCH and, on a clean compile, proceeds. The shipped cosmic pack is stamped with the
**builtin-only** fingerprint — add-ons cannot exist at build time. Fingerprints are recomputed fresh at each
export and each apply (an add-on may register between boot and apply).

## Consequences

- A pack that would fail to load is caught offline, disk untouched, with the exact `file:line` failures — the
  brick-the-surface-then-fail-the-reload mode is gone.
- The gate re-runs the whole compile every apply (a throwaway temp-dir compile, off-thread via the existing
  `Scheduling.async` path). It is safe to run concurrently — a fresh scratch dir per call — and the
  subsequent reload stays single-flight.
- Every future kind/param/trigger/var addition now touches `docs/reference/authoring-surface.txt` and
  `packs-src/cosmic-pack/pack.yml` alongside `dsl-reference.md`/`catalog.json`. Deliberate — a surface change
  is a reviewed diff — and `regenDocs` regenerates all of them.
- A serialization-version bump (`2:`) flips every stamped pack to MISMATCH at once; the wording
  ("checking whether it still compiles…") keeps that informational, and the dry-run still decides.
- No fingerprint on `StarEnchantsApi` (`:api` untouched); no gate on `/se import` (it already dry-runs via
  `validateCandidate`, ADR-0029); no `PackManifest.CURRENT_FORMAT` bump (added manifest keys are ignorable
  both directions); no menu surface (pack apply is command-only).

## Alternatives considered

- **Gate on the fingerprint alone.** Rejected — it has both false negatives (a cross-rule tightened, a handle
  token that no longer resolves — a MATCH that still fails) and false positives (a semantic-only registry
  change). Only a real compile is authoritative, so the fingerprint explains and the dry-run decides.
- **Build-time codegen to stamp the shipped pack.** Rejected in favour of the drift-test pattern: a committed
  manifest gives reviewable diffs and keeps the Gradle Zip task dumb, matching `surface.json`/
  `dsl-reference.md`.
- **Bump `format:`.** Rejected — `fromYaml` already ignores unknown keys both directions and `toYaml` omits
  empty ones, so the added keys are layout-compatible; `format` bumps only for an archive-layout break.
- **Validate only `content/`.** Rejected — a broken `lang.yml`/`config.yml`/`items/`/`menus/` would still
  swap disk and brick the surface; the reload checks all five, so the gate must too.
