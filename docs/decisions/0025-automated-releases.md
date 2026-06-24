# ADR 0025: Automated releases — version-bump-driven GitHub Release of the universal jar

- **Status:** Accepted
- **Date:** 2026-06-24
- **Deciders:** project owner + agent
- **Relates to:** ADR 0002 (one universal shaded jar), ADR 0009 (git workflow), the CI gate
  (`.github/workflows/ci.yml`) and the live matrix gate (`matrix-gate` skill)

## Context

The plugin ships as a single universal jar (the bootstrap fat jar, 1.17.1 → 26.1.x + Folia). Until now
there was no published artifact: every consumer had to build from source. We want GitHub to build the jar
and publish a tagged GitHub Release automatically, on a clear, intentional signal — not on every commit
(release spam) and not only by hand (forgotten releases, mismatched version metadata).

The version is already a single source of truth: `version` in the root `build.gradle.kts`, which is stamped
into `plugin.yml` at build time. That makes a **version bump** the natural release trigger.

## Decision

**A release is cut when the project version on `main` becomes a non-`SNAPSHOT` value that has not yet been
released.** `.github/workflows/release.yml` runs on pushes to `main` that touch `build.gradle.kts` (plus a
manual `workflow_dispatch`), and a gate job decides:

- parse `version` from `build.gradle.kts`;
- if it ends in `-SNAPSHOT` → **skip** (development versions never release);
- if a `v<version>` release already exists → **skip** (idempotent — re-runs and unrelated edits to the file
  are no-ops);
- otherwise → build and publish.

The release job mirrors CI's toolchain (JDKs 17 + 21, Gradle cache), runs the unit gate plus the fat-jar
task (`./gradlew build :bootstrap:jar`), then creates the GitHub Release: it **creates the `v<version>`
tag** at the released commit, attaches `StarEnchants-<version>.jar` (the bootstrap fat jar) and its
`.sha256`, and uses GitHub's auto-generated notes (categorised by `.github/release.yml`).

So the entire release procedure is: **bump `version` in `build.gradle.kts` (drop `-SNAPSHOT` / set the next
number) in a PR, merge it, and the jar ships.** The version in the tag, the release, the jar filename, and
`plugin.yml` are guaranteed to agree because they all derive from that one line.

## Consequences

- Releasing is a one-line, reviewable change — no manual tagging, no separate publish step, no version
  drift between the tag and the jar.
- `-SNAPSHOT` is the "unreleased" marker: `main` sits on `X.Y.Z-SNAPSHOT` between releases, so normal
  merges never publish. The first release happens the first time the version is set to a non-SNAPSHOT value.
- The release build runs the **unit gate only**, like CI. The real-server Paper+Folia matrix is the LOCAL
  pre-release gate (it needs the gitignored server reference cache, so it can't run in stock CI) — the
  release-cutting workflow is therefore: run `scripts/run-matrix.sh --all` locally, see all targets PASS,
  then bump the version.
- Branch protection still applies: the version bump lands through a normal PR with the required checks, so
  a release can only come from a green `main`.

## Alternatives considered

- **Release on every push to `main`** — continuous artifacts, but spammy and forces an auto-versioning
  scheme; rejected for a plugin whose releases are deliberate.
- **Release on pushing a `v*` tag** (tag is the trigger) — conventional, but the jar/`plugin.yml` version
  comes from `build.gradle.kts`, so a hand-pushed tag can disagree with the embedded version. Folding the
  trigger into the version line removes that whole class of mismatch. `workflow_dispatch` covers the manual
  re-run case.
- **Running the full live matrix in the release job** — it needs the large, gitignored, per-version
  Paper/Folia server cache; impractical in stock GitHub-hosted CI. Kept as the documented local gate.
