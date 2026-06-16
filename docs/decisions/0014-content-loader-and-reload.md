# ADR 0014: Content loader + atomic reload

- **Status:** Accepted
- **Date:** 2026-06-16
- **Deciders:** owengregson

## Context

ADR-0004 (compile-at-load DSL) and ADR-0006 (fresh unified schema, atomic full
reload) set the principles; the compiler pipeline (`se-schema` → `se-compile`) is
built and consumes `AbilityDef`s. What was undesigned: the concrete authored file
format, the stable-key scheme (the gate for `WornResolver` + end-to-end combat),
and the reload machinery. This ADR fixes those.

## Decision

**Authored format — one def per file, dirs by source.** `content/enchants/lifesteal.yml`
is one enchant. Identity/shared fields at the top (`display`, `description`,
`trigger`, `applies-to` as named target *groups*, `group`, `disabled-worlds`,
`max-level`); per-level fields under `levels:` (`chance`, `cooldown`, `soul-cost`,
`condition`, `effects`). `applies-to` is metadata for later apply/render cycles, not
a runtime ability property.

**Level model — per-level entries → one ability each.** Each `levels:` entry
produces one `AbilityDef`. Arbitrary (even non-linear) per-level data; matches the
compiled model (level baked into each `Ability`) and how EE/EA author.

**Stable-key scheme — path-derived.** Base key = the path under `content/` minus
extension (`enchants/lifesteal`); per-level enchant abilities append `/<level>`
(`enchants/lifesteal/3`). Reorder-proof, collision-safe across source types
(directory namespaces), human-readable. The on-item PDC stores the base key + level;
`WornResolver` composes the per-level key (`enchantKeyFor = (k,l) -> k + "/" + l`).
`group` → the cooldown-group scope; suppression is keyed off the enchant base key.

**Module placement.** Pure additions to `se-compile` (`compile.load`): `EnchantDefReader`
(one YAML file → `EnchantDef` metadata + N `AbilityDef`s, via SnakeYAML + `Source`
marks + the existing effect/condition grammar), `LibraryLoader` (a `content/` tree →
`Library` = `Snapshot` + the `EnchantDef` catalog + diagnostics, via the injected
`Compiler`), and `ContentHolder` (`AtomicReference<Library>` — the single published
content the engine/item read, §0; pure so they depend *down* onto it). Reload
orchestration (`ContentReloader`) lives in `se-platform` (it needs `Scheduling`); the
new **`bootstrap`** module is the `StarEnchants` JavaPlugin composition root + `/se reload`.

**Reload — transactional.** Build off-thread (the loader is pure), swap the
`AtomicReference` on the global thread only when there are no blocking diagnostics;
a fatal edit keeps the old content live (never takes the server down). `/se reload`
and `/se reload --dry-run`. `validateContent` (gradle) runs `LibraryLoader` on bundled
content against a fake `PlatformResolvers`, failing the build on blocking diagnostics.

**SnakeYAML** is server-provided (`compileOnly`, not shaded — no version coupling).

## Consequences

- The parse→compile→load path is pure and unit-testable with zero server; the live
  matrix verifies SnakeYAML-on-the-real-server (its version varies) + the Folia swap.
- Unblocks `WornResolver` (the key scheme is fixed) and end-to-end combat.
- The `bootstrap` module is the first real plugin main (reload requires it).
- Non-enchant readers (`sets`/`weapons`/`crystals`/`heroic`) register the same way
  when authored; only `EnchantDefReader` ships in this cycle.

## Alternatives considered

- Level as a runtime parameter (one ability per enchant) — rejected: the compiled
  model bakes level into each `Ability`, and per-level-distinct data is awkward as
  formulas.
- Grouped files per source (EE/EA style) — rejected: huge, conflict-prone files.
- Reload/holder in `api` or a new `content` module — rejected: `se-compile` already
  holds the pure pieces; only the `Scheduling`-backed reloader needs to live higher.
