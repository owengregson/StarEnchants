# Getting started

This gets a fresh clone to the point where you can build the universal jar, run it
on a real Paper server, and make your first change. Budget ten minutes for setup
plus the first Gradle build.

## Prerequisites

- **Git** and a **JDK ≥ 17** on your `PATH`.
- The version matrix builds with **JDK 17** (Minecraft ≤ 1.20.4) and **JDK 21**
  (1.20.5+); CI builds on 21/25. You only need both installed to run the live
  Paper + Folia matrix locally — plain `./gradlew build` is happy with one JDK 17+.
  On macOS the bootstrap finds them automatically; elsewhere point at them with
  `JAVA17_HOME` / `JAVA21_HOME`.

## Clone and set up

```bash
git clone https://github.com/owengregson/StarEnchants.git
cd StarEnchants
scripts/setup-dev.sh
```

`setup-dev.sh` is idempotent, non-interactive, and safe to re-run — every step
detects whether it is already done and skips it. It verifies prerequisites,
installs the shared git hooks (Conventional Commits + hygiene; see
[`CONTRIBUTING.md`](../../CONTRIBUTING.md)), and runs `./gradlew build`. Useful
flags:

```bash
scripts/setup-dev.sh --with-reference   # also fetch the per-version Paper/Folia jar + doc cache
scripts/setup-dev.sh --full             # everything (fetch + decompile + build)
scripts/setup-dev.sh --help             # all flags & env overrides
```

The `--with-reference` cache is what the live matrix and any version-specific
debugging read from; you don't need it for everyday unit work. See
[`docs/development.md`](../development.md) for the full setup story.

If you only want the hooks (e.g. you set the JDKs up yourself), run
`scripts/setup-hooks.sh` once per clone instead.

## Build

```bash
./gradlew build
```

This compiles every module and runs the **pure unit tests** plus the generated-doc
**drift tests** — the fast inner gate. It does **not** boot a server; the real
Paper + Folia integration matrix is a separate, deliberate step (see
[verification-gate.md](verification-gate.md)). Treat `./gradlew build` as the thing
you run constantly while developing.

## Build the jar and run it on a server

The shipped artifact is a single universal **fat jar** built by the `bootstrap`
module's `jar` task (it bundles every runtime module — there is no separate jar
per Minecraft version):

```bash
./gradlew :bootstrap:jar
```

The jar lands in `se/bootstrap/build/libs/`. Drop it into a Paper server's
`plugins/` folder and start the server:

```bash
cp se/bootstrap/build/libs/StarEnchants-*.jar /path/to/paper-server/plugins/
# start the server; on first enable the default content/ tree is copied into the
# plugin's data folder, and `/se reload` reloads that config (never code).
```

That same jar loads unchanged on any supported version, Paper or Folia — that is
the whole point of the floor-API + version-edge design.

For a tight edit → see-it loop, the project also exposes dev-server run tasks
(`./gradlew runPaper` / `runFolia`, version-pickable with `-Pmc=...`) that
download, install the freshly-built jar, and boot a server in one command. See
[`docs/development.md`](../development.md#the-fast-inner-loop) for the loop and why
Bukkit hot-reload is deliberately avoided.

## The module layout

Source is a **flat tree** — modules live at `se/<module>/`, production code under
`src/`, tests under `test/`, resources alongside (no Maven `src/main/java`). Each
module's package is a **single segment equal to its name**, so a file path reads
straight: `se/schema/src/schema/diag/Severity.java` is module `schema`, package
`schema.diag`, type `Severity`. The current modules:

| Module | Role |
| --- | --- |
| `schema` | the DSL grammar, `ParamSpec`/`ParamType`, and diagnostics |
| `compile` | the content compiler: resolve → typecheck → lower → erase → snapshot |
| `platform` | capability probing, the `Scheduling` abstraction, cross-version resolvers |
| `engine` | the runtime: stateless systems, the activation pipeline, kinds, the `Sink` |
| `item` | item state, the PDC codec, the `ItemView` cache, the `WornState` resolver |
| `feature` | the Bukkit-facing shells (listeners, triggers, commands, GUIs) |
| `compat-folia` | the Folia scheduler edge, behind capabilities |
| `migrate` | the legacy Cosmic Enchants-style config importer |
| `pack` | shipped, swappable config packs |
| `integrate` | bundled soft-depend third-party integrations |
| `api` | the public events fired at activation/reload points |
| `bootstrap` | the composition root — the `JavaPlugin` and the fat jar |
| `tester` | the in-server live-test harness (test-only; not shipped) |

The dependency direction is acyclic and one-way: `platform → compile`, `engine →
{compile, platform}`; the lower layers never depend on the runtime. The root
`build.gradle.kts` applies the shared toolchain, repositories, and JUnit stack
once; each module declares only its own `plugins { java-library }` and deps.

## Your first change

A good first change is a new effect kind — it is the project's "one interface +
one registration" pattern at its smallest, it is pure logic (no server needed to
test it), and it walks you through the spec/registry/test/regen loop:

1. Read [guides/developing-an-effect.md](guides/developing-an-effect.md).
2. Implement the kind, add the single `.register(...)` line, write the mock-host
   unit test.
3. `./gradlew regenDocs` to refresh the generated DSL reference (see
   [regenerating-generated-docs.md](regenerating-generated-docs.md)).
4. `./gradlew build` — green means the unit tests **and** the drift tests pass.

Then branch, commit in small Conventional Commits, and open a PR (see
[`CONTRIBUTING.md`](../../CONTRIBUTING.md)). Before reaching for the slow live
matrix, check [verification-gate.md](verification-gate.md) — most pure-logic
changes need only `./gradlew build`.
