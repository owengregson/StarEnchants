# Developer documentation

This is the hub for working **on** StarEnchants — building it, testing it on real
servers, understanding the engine, and extending it. StarEnchants is a single
universal Paper/Folia plugin (custom enchants + armor sets) that loads unchanged
across Minecraft 1.17.1 → 26.1.x, so almost every doc here has a cross-version or
a Folia angle. Read the [getting-started guide](getting-started.md) first; reach
for the internals and guides as you touch each area.

The codebase is a flat set of modules at `se/<module>/` (sources in `src/`, tests
in `test/`, each module's package a single segment). The two non-negotiables that
shape everything: **Folia-correct or it doesn't ship** (all entity/world work goes
through the scheduling abstraction) and **one universal jar** (floor API, version
edges behind resolvers).

> Operator and end-user documentation (installation, commands, config, the web
> enchant creator) lives at <https://owengregson.github.io/StarEnchants/>. This
> hub is for contributors.

## Getting started

- [getting-started.md](getting-started.md) — clone, set up, build the jar, run it
  on a local server, make your first change.
- [verification-gate.md](verification-gate.md) — the two-layer gate: `./gradlew
  build` then the real Paper + Folia matrix, and reading a fresh PASS honestly.
- [writing-a-live-test.md](writing-a-live-test.md) — the in-server `tester`
  module, the clientless fake-player harness, and staging a scenario.
- [regenerating-generated-docs.md](regenerating-generated-docs.md) — the
  generated artifacts, `./gradlew regenDocs`, the drift tests, and the pre-commit
  hook.
- [release-process.md](release-process.md) — how a version bump ships a GitHub
  Release of the universal jar.

## Internals

- [internals/effect-engine.md](internals/effect-engine.md) — stateless systems,
  the activation pipeline and gate order, kinds, the `Ability`, the `Sink`, and
  declared `Affinity`.
- [internals/item-data-model.md](internals/item-data-model.md) — item state, the
  PDC codec, the `ItemView` cache, the stable-key map, the `WornState` resolver,
  and lore/name rendering.
- [internals/compiler-and-config.md](internals/compiler-and-config.md) — the
  config schema/DSL, `ParamSpec`, the compiler stages, diagnostics, and
  transactional reload.
- [internals/feature-interactions.md](internals/feature-interactions.md) — how
  features compose: damage stacking, suppression, souls, slots, crystals, and
  multi-set completion.
- [internals/cross-version-api.md](internals/cross-version-api.md) — the floor
  API, version-volatile referents, the boot-time resolver pattern, and the
  1.20.5 mapping flip.
- [internals/folia-scheduling.md](internals/folia-scheduling.md) — Folia's
  region/entity/global threading model and the `Scheduling` abstraction that
  makes one codebase correct on both servers.
- [internals/performance-hot-paths.md](internals/performance-hot-paths.md) — the
  combat/item hot path, `Affinity`, the `Sink`/cache/interning, and the
  lint/bench guards.
- [internals/the-migrator.md](internals/the-migrator.md) — importing legacy
  Cosmic Enchants-style configs into StarEnchants content.
- [internals/config-packs.md](internals/config-packs.md) — the shipped, swappable
  config packs.
- [internals/testing-architecture.md](internals/testing-architecture.md) — how the
  test suite is shaped (the layer pyramid, one source of truth per string, the
  data-driven effect-kind tables, the `se/testfx` fixtures); the design rationale
  behind the `writing-tests` skill.

## Extending the plugin

- [guides/developing-an-effect.md](guides/developing-an-effect.md) — add an
  effect kind (the most common change).
- [guides/developing-a-condition.md](guides/developing-a-condition.md) — add a
  condition / gate.
- [guides/developing-a-selector.md](guides/developing-a-selector.md) — add a
  target selector.
- [guides/developing-a-trigger.md](guides/developing-a-trigger.md) — add a
  trigger family.
- [guides/extending-the-dsl-grammar.md](guides/extending-the-dsl-grammar.md) —
  extend the effect/condition DSL grammar.
- [guides/adding-an-item-type.md](guides/adding-an-item-type.md) — add a new
  physical item type.
- [guides/adding-an-integration.md](guides/adding-an-integration.md) — add a
  third-party (soft-depend) integration.
- [guides/adding-a-config-option.md](guides/adding-a-config-option.md) — add a
  config option.
- [guides/adding-a-command.md](guides/adding-a-command.md) — add a `/se`
  subcommand.

## Reference

- [../architecture.md](../architecture.md) — the approved architecture (the
  design spec).
- [../decisions/](../decisions/) — Architecture Decision Records (every
  significant decision and *why*).
- [../glossary.md](../glossary.md) — shared domain vocabulary.
- [../integrations.md](../integrations.md) — supported third-party integrations.
- [../../CONTRIBUTING.md](../../CONTRIBUTING.md) — branching model, commit
  conventions, and the testing gate.
- [../../CLAUDE.md](../../CLAUDE.md) — the top-level entry point and the
  non-negotiable invariants.
