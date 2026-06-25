# Regenerating generated docs

Several documentation and data artifacts in the repo are **generated from the
code and bundled resources**, not hand-edited. Each is paired with a **drift test**
that fails `./gradlew build` if the committed file no longer matches its source, so
the docs site and the web enchant creator can never silently fall out of step with
the plugin. This page is the contract: what is generated, how to regenerate it, and
why the build won't let a stale artifact through.

## The generated artifacts

| File | Source of truth | Generator | Drift test |
| --- | --- | --- | --- |
| `docs/reference/dsl-reference.md` | the live effect/selector/trigger/condition/variable registries | `engine.doc.ReferenceDoc` | `ReferenceDocDriftTest` |
| `website/src/data/catalog.json` | the same five runtime registries (with full per-param schema) | `engine.doc.ReferenceCatalogJson` | `ReferenceCatalogDriftTest` |
| `website/src/data/surface.json` | the `/se` command table + bundled `content/tiers.yml`, `items/*.yml`, `config.yml` | `bootstrap.SurfaceCatalogDriftTest#render` | `SurfaceCatalogDriftTest` |
| `se/bootstrap/resources/content/index.txt` | the on-disk `se/bootstrap/resources/content/**/*.yml` files | `bootstrap.ContentIndexDriftTest` | `ContentIndexDriftTest` |

The split is intentional: the **engine** half (`ReferenceDoc` /
`ReferenceCatalogJson`) is driven purely by the runtime registries, so the moment
you register a new effect/selector/trigger — or add a DSL operator or runtime
variable — its docs and creator schema appear automatically. The **bootstrap**
half (`SurfaceCatalogDriftTest` / `ContentIndexDriftTest`) is driven by the
shipped commands and the bundled content tree.

## How generation works

Each generator is a small, dependency-free, **deterministic** renderer (sorted
keys, stable ordering, a tiny hand-rolled JSON writer for the catalogs) so a given
source state always yields a byte-identical file that diffs cleanly. Two examples:

- `ReferenceCatalogJson.render()` walks `BuiltinEffects.registry()`,
  `BuiltinSelectors.registry()`, `BuiltinTriggers.registry()`, the DSL operators,
  and `BuiltinVars.vocabulary()`, sorting heads and variables, and emits the full
  per-param schema (kind, range, enum values, handle category, default) the
  creator's form needs.
- `SurfaceCatalogDriftTest#render` emits the `SeCommand.COMMANDS` table, the rarity
  tiers from `content/tiers.yml`, the item kinds from each `items/*.yml`, and the
  annotated `config.yml` **verbatim** (so its inline comments survive into the
  docs).

The drift tests run in two modes off a system property. In normal mode they assert
the committed file equals a fresh render and fail with a "regenerate with …"
message if not; in **regen mode** (`-Dse.doc.regen=true`, and
`-Dse.index.regen=true` for the content index) they *write* the fresh render
instead of asserting. The Gradle `regenDocs` tasks just run the drift tests in
regen mode.

## Regenerate everything

```bash
./gradlew regenDocs
```

The root `regenDocs` task fans out to `:engine:regenDocs` (rewrites
`dsl-reference.md` + `catalog.json` from the registries) and `:bootstrap:regenDocs`
(rewrites `surface.json` + `content/index.txt` from the sources). Run it after any
change that feeds the table above, then commit the regenerated files alongside your
change.

To regenerate a single artifact, target its drift test in regen mode, e.g.:

```bash
./gradlew :engine:test --tests "*ReferenceCatalogDriftTest" -Dse.doc.regen=true
./gradlew :bootstrap:test --tests "*ContentIndexDriftTest" -Dse.index.regen=true
```

(The root build forwards any `-Dse.*` property into the forked test JVM, so the
regen flags reach the tests.)

## The build won't let stale slip through

A plain `./gradlew build` runs the drift tests in **assert** mode. If you change a
source that feeds a generated artifact but forget to regenerate, the matching test
fails the build with the exact command to fix it — an un-regenerated change cannot
merge. This is why the [verification gate](verification-gate.md)'s first layer
already covers doc staleness.

## The pre-commit hook

The shared git hook (`.githooks/pre-commit`, enabled by `scripts/setup-hooks.sh` or
`scripts/setup-dev.sh`) keeps the artifacts in step **before** the commit lands. If
a staged file feeds the generated docs — the engine/schema/compile registries, the
command catalog (`SeCommand`/`CommandInfo`), or the bundled `config.yml` /
`content/` — it runs `./gradlew regenDocs` and re-stages the four generated files,
so you never trip the drift gate in CI. (It also blocks committing the
reverse-engineering workspace and large binaries.) Skip it with `git commit -n`
only when you know the regen isn't needed.

## How the site and creator consume the catalogs

The docs site and the web enchant creator are downstream consumers of the two JSON
files under `website/src/data/`:

- `catalog.json` is the structured vocabulary — every effect/selector/trigger with
  its per-param schema, plus the DSL operators and runtime variables. The web
  enchant creator builds its forms from it, and the docs site renders the DSL
  reference from it. Because it carries the full param schema, the creator's UI
  validates exactly what the compiler will.
- `surface.json` is the operator surface — the `/se` commands, the rarity tiers,
  the item kinds, and the annotated `config.yml`. The docs site renders the
  command/config/tier/item reference from it.

Keeping both generated-and-drift-guarded means a contributor only edits code and
content; the public docs and the creator stay correct for free. Operator/user docs
are published at <https://owengregson.github.io/StarEnchants/>.
