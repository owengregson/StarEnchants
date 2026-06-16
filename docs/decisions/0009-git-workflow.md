# ADR 0009: Git workflow — feature branch → PR → rebase-merge

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

## Context

The user wants a clean, granular version history with frequent commits, plus a
proper contributor workflow.

## Decision

Short-lived feature branches off `main` → PR (CI green) → **rebase-merge**
(never squash, which would collapse the frequent commits). Conventional Commits,
enforced by a `commit-msg` hook; a `pre-commit` hook blocks the `deobf/`
workspace and >2MB blobs. Don't commit directly to `main`.

## Consequences

- `main` stays linear AND granular.
- Substantive feature PRs pause for review before merge by default; low-risk
  chore/docs PRs may be self-merged.

## Alternatives considered

- Trunk-based on `main` — viable but the user chose PR-gated branches.
- Squash-merge — rejected: collapses the granular history we want.
