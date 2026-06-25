# StarEnchants — agent & contributor guide

A single Paper/Folia plugin combining custom enchants and armor sets (no web
server) into one engine, cross-compatible **Paper 1.17.1 → 26.1.x + Folia**.

> **Status: design & bootstrap.** The unique architecture is being derived (see
> `docs/decisions/0010`); plugin code starts after it's approved. Do NOT scaffold
> the module/source tree until then.

## Read these before working

Project skills in `.claude/skills/` encode hard-won knowledge — check the
relevant one BEFORE working in its area:

| Skill | When |
| --- | --- |
| `starenchants-conventions` | writing/reviewing ANY code (principles, invariants) |
| `paper-cross-version` | code across 1.17.1 → 26.1.x (mapping flip, toolchains) |
| `cross-version-item-api` | Material/Sound/Particle/Enchantment/Attribute/etc. |
| `folia-scheduling` | entities/blocks/world/inventories/timers (Paper + Folia) |
| `nms-archaeology` | a version misbehaves — read the server, don't guess |
| `live-server-testing` | the real Paper/Folia integration suites |
| `matrix-gate` | running/verifying the test gate |
| `reference-cache` | needing cached per-version Paper/Folia jars or docs |
| `effect-engine` | the runtime: systems, pipeline/gate order, kinds, Ability, Sink, Affinity |
| `item-data-model` | item state, PDC codec, ItemView cache, component stores, WornState, render |
| `feature-interaction-rules` | features interact — damage stacking, suppression, souls, slots, crystals, omni |
| `config-and-migration` | config/DSL/ParamSpec, the compiler, diagnostics, reload, the migrator |
| `performance-hot-paths` | combat/item hot path, Affinity, Sink/cache/interning, the lint/JMH gate |

Decision rationale lives in `docs/decisions/` (ADRs). Domain vocabulary is in
`docs/glossary.md`. The dev loop is in `docs/development.md`.

## Non-negotiable invariants

- **Architecture is self-derived** — not modeled on the construction of any
  existing enchantment plugin. Analysis of a Cosmic Enchants-style reference
  (local-only, gitignored) informs WHAT features exist and HOW they interact,
  never how to build them. The only borrowed practice is real-server (Paper+Folia)
  testing.
- **Folia-correct or it doesn't ship** — all entity/world work via the scheduling
  abstraction; no raw `Bukkit.getScheduler()` for entity work.
- **One item-data layer** (PDC, versioned keys); lore is rendered from state.
- **Atomic config** — compiled, immutable snapshot swapped by reference.
- **Version-agnostic core, version-specific edges** behind resolvers/capabilities.
- **Adding a feature is local** — one interface + one registration.
- **Flat source layout** — modules live at `se/<module>/`; sources in `src/`,
  tests in `test/` (no `src/main/java`); each module's package is a **single
  segment = its name** (`schema`, `engine`, …), never `com.starenchants.*`.
  Shaded third-party deps are relocated under their own root so the short roots
  never collide.

## Workflow

Feature branch → frequent Conventional Commits → PR (CI green) → **rebase-merge**
(never squash). Enable hooks once: `scripts/setup-hooks.sh`. End AI-assisted
commits with `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
See `CONTRIBUTING.md`.

## Verification gate

`./gradlew build` (pure unit tests) → real Paper **and** Folia integration matrix.
Never trust a green banner — verify fresh PASS (`matrix-gate`). A green Paper run
says nothing about Folia.
