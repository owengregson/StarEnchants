# Release process

Releasing StarEnchants is automated and driven by **one signal: a version bump**.
The version lives in a single place — `version` in the root `build.gradle.kts`,
which is stamped into `plugin.yml` at build time — and CI publishes a GitHub
Release the moment that value on `main` becomes a non-`SNAPSHOT` version that
hasn't been released yet. There is no manual tagging and no separate publish step.
The full rationale is [ADR-0025](../decisions/0025-automated-releases.md).

## The single source of truth

Because the tag, the GitHub Release, the jar filename, and the `plugin.yml`
version **all derive from that one line**, they can never disagree. `-SNAPSHOT` is
the "unreleased" marker: `main` sits on `X.Y.Z-SNAPSHOT` between releases, so
ordinary merges never publish.

## The procedure

```bash
# 1. LOCAL pre-release gate: every target PASS on real Paper AND Folia.
scripts/run-matrix.sh --all

# 2. Drop -SNAPSHOT (or set the next number) in build.gradle.kts:
#    version = "1.0.0-SNAPSHOT"   ->   version = "1.0.0"

# 3. Open a PR with just that bump, get CI green, and rebase-merge to main.
```

On merge, `.github/workflows/release.yml` runs the gate and ships the jar:

1. The workflow triggers on pushes to `main` that touch `build.gradle.kts` (plus a
   manual `workflow_dispatch`).
2. A gate job parses `version`: if it ends in `-SNAPSHOT` it **skips**; if a
   `v<version>` release already exists it **skips** (idempotent — re-runs and
   unrelated edits to the file are no-ops); otherwise it proceeds.
3. The release job mirrors CI's toolchain (JDKs 17 + 21, Gradle cache), runs
   `./gradlew build :bootstrap:jar` (the unit gate plus the universal fat jar),
   creates the `v<version>` tag at the released commit, and publishes the GitHub
   Release with `StarEnchants-<version>.jar` + its `.sha256` and GitHub's
   auto-generated, categorised notes.

So the entire release is: **bump the version in a PR, merge it, the jar ships.**

## After releasing

Bump `main` back to the next development version, e.g. `1.0.1-SNAPSHOT`, so
ordinary merges resume being no-ops until the next deliberate release.

## Why the live matrix is a LOCAL gate

The release build runs the **unit gate only**, exactly like CI. The real-server
Paper + Folia matrix needs the large, gitignored, per-version server reference
cache, which can't live in stock GitHub-hosted CI — so it stays the **local
pre-release gate** you run in step 1. Branch protection still applies: the version
bump lands through a normal PR with the required checks, so a release can only ever
come from a green `main`. Read a fresh PASS from the matrix honestly
([verification-gate.md](verification-gate.md)) before you bump — once the bump
merges, the jar publishes without re-running it.

## Re-running by hand

`workflow_dispatch` re-runs the gate check manually (the same skip logic applies),
which is the escape hatch if a release job needs to be retried without touching the
version line.
