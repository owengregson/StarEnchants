# Contributing to StarEnchants

This is a large, cross-version codebase. The rules below keep history clean and
make it easy to see every change work on a real server.

## First-time setup

```bash
git clone https://github.com/owengregson/StarEnchants.git
cd StarEnchants
scripts/setup-hooks.sh          # enable shared git hooks (conventional commits, hygiene)
```

The reverse-engineering workspace (`deobf/`) is **local-only** and gitignored —
it holds decompiled third-party plugins used only to understand *what* to build,
never *how*. Don't commit it.

## Branching & PR model

We use **short-lived feature branches → PR → rebase-merge**. Rebase-merge keeps
every commit on `main`, so history stays both linear *and* granular.

1. Branch off `main`: `git switch -c <type>/<short-description>`
   (e.g. `feat/effect-engine`, `fix/folia-aoe-region`).
2. Commit **frequently** in small, logical, conventional commits.
3. Open a PR; CI must be green.
4. **Rebase-merge** into `main` (never squash — that would collapse the granular
   history we deliberately keep). Keep the branch rebased on top of `main` so
   the merge is a clean fast-forward.

Don't commit directly to `main`.

## Commit messages — Conventional Commits

```
<type>(<optional-scope>): <imperative subject>

<body: the WHY — reasoning, trade-offs, provenance of any ported behavior>
```

Types: `feat fix docs style refactor perf test build ci chore revert`.
The `commit-msg` hook enforces this. Examples:

- `feat(effects): add POTION effect with PLAYER/TARGET resolution`
- `fix(folia): hop to each victim's region for DAMAGE_ARC`
- `test(live): pin armor-set bonus apply/remove on 1.17.1 + 1.21.x`

Co-author trailer for AI-assisted commits:
`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

## Code conventions

- Read [`.claude/skills/starenchants-conventions`](.claude/skills/starenchants-conventions/SKILL.md)
  before writing code; it defines the engine boundaries and invariants.
- Style is pinned by `.editorconfig` (LF, UTF-8, 4-space Java/Kotlin).
- Pure logic lives away from Bukkit and is unit-tested; Bukkit shells stay thin.
- Never hard-reference version-volatile API constants
  (Material/Sound/Particle/Enchantment/Attribute/PotionEffectType) — resolve by
  name (`cross-version-item-api`).
- Never touch entities/world/inventory/timers via the raw scheduler — use the
  scheduling abstraction so it's correct on Paper **and** Folia
  (`folia-scheduling`).

## Adding a feature

Adding an effect / condition / trigger type / armor-set effect / item / crystal
should be a small, local change: implement one interface, register it in one
place. Copy the nearest sibling. (Detailed engine skills are added once the
architecture is approved.)

## Testing gate (required before merge)

```bash
./gradlew build          # compile + unit tests (pure logic) — always first
<integration matrix>     # real Paper AND Folia servers run the live suites
```

Verify results are a **fresh PASS** — never trust a green banner alone. See
[`.claude/skills/matrix-gate`](.claude/skills/matrix-gate/SKILL.md) and
[`.claude/skills/live-server-testing`](.claude/skills/live-server-testing/SKILL.md).
A green Paper run does not imply Folia — both must pass.

See [docs/development.md](docs/development.md) for the day-to-day dev loop.
