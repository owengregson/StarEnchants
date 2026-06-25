# The verification gate

StarEnchants is verified in **two layers, always in this order**:

```bash
./gradlew build          # compile + unit tests (pure logic) — ALWAYS first
scripts/run-matrix.sh    # boot real Paper AND Folia servers, run the live suites
```

The first layer is fast and runs constantly while you develop. The second boots
real servers across the supported range and is the gate before merging anything
that touches version-specific, NMS, Folia, or integration behavior. A change does
not "pass" until you have read a **fresh PASS**, not a green banner.

## Layer 1 — `./gradlew build`

Compiles every module and runs the **pure unit tests** plus the generated-doc
**drift tests**. The unit tests pin logic that needs no server:

- DSL parse round-trips, `ParamSpec` typecheck/range math.
- chance/cooldown math, interaction-precedence resolution.
- config-snapshot parsing and the resolver alias maps.
- effect kinds via a **mocked `Sink`** — verify a kind emits exactly the right
  intents, with no server in sight.

It also runs the drift tests, so an un-regenerated change to the DSL reference,
the docs-site catalog, or the content index fails the build here (see
[regenerating-generated-docs.md](regenerating-generated-docs.md)).

For a pure-logic change (a new effect/condition/selector kind, math, interaction
rules) **this layer is the whole gate** — there is nothing a real server would add.
Reserve the matrix for changes that actually exercise a server.

## Layer 2 — the live Paper + Folia matrix

```bash
scripts/run-matrix.sh paper:1.20.6 folia:1.20.6   # explicit targets
scripts/run-matrix.sh --paper 1.20.6 --folia 1.20.6   # flag form, same thing
scripts/run-matrix.sh --all                       # the full matrix from gradle.properties
```

`run-matrix.sh` boots a real cached server per `(platform, version)`, installs the
`tester` fat jar, lets the in-server harness run its suites and write a **fresh**
`test-results.txt` (PASS/FAIL), then reads that result — a server that failed to
boot leaves a stale or missing result, never a red banner. The harness is the one
practice deliberately adopted from an external project (real-server testing),
adapted to enchants/sets/items/GUIs; everything else about how the plugin is built
is its own. See [writing-a-live-test.md](writing-a-live-test.md) for the harness
internals.

It **auto-rebuilds** before booting, so you can't accidentally test a stale jar;
pass `--no-build` (or `SE_NO_BUILD=1`) only immediately after a fresh build to
skip that. The server jars come from the gitignored reference cache — run
`scripts/setup-dev.sh --with-reference` first if a version is missing.

The matrix is **targeted by design**: pass only the versions whose code path you
changed. A scheduler/`Capabilities` change needs one Paper + one Folia; a
mapping-flip change needs both sides of 1.20.5; only a broad change needs `--all`.

### The matrix to cover

Cover the floor, the 1.20.5 spigot→mojang mapping-flip boundary (both sides), and
the ceiling — plus Folia where it exists (Folia builds begin ~1.19.4):

- **Paper:** 1.17.1, 1.18.2, 1.19.4, 1.20.6, 1.21.x, 26.1.x.
- **Folia:** 1.19.4+, e.g. 1.20.6, 1.21.x, 26.1.x.

The list lives in `gradle.properties`; adding a version updates the
Java-toolchain boundary (17 for ≤ 1.20.4, 21+ for 1.20.5+) and caches a
paperclip/folia jar.

### Concurrency on one machine

Booting many JVMs at once has its own failure modes:

- Launch the heaviest (newest) servers first with a small stagger, so the fast old
  servers boot into a calm machine.
- Keep heaps small and far from memory pressure — a page-fault storm reads like a
  tick stall.
- On macOS keep each JVM awake (`caffeinate -i`); an App-Napped background JVM can
  stall for tens of seconds without ever logging "Can't keep up".
- A killed run leaves orphan servers holding `world/session.lock` and ports — reap
  leftover server processes before re-running.

## Reading a result HONESTLY

This is the part that matters most:

- **Never trust "BUILD SUCCESSFUL" or a passing banner alone.** Confirm each
  server's `test-results.txt` is **FRESH** (mtime within this run) and reads
  `PASS`; failures detail in `test-failures.txt`.
- A server that hung or failed to boot leaves a **stale or missing** result, not a
  red banner — its log is the only evidence it leaves, so keep per-server logs and
  read them on any non-PASS.
- **A green Paper run says nothing about Folia.** Cross-region effects (AoE,
  steal-between-players, teleport-to-target) only reveal wrong-thread bugs on
  Folia. Both must be green, independently, for the change to ship.

A test that is correct sequentially but flaky under concurrent load is almost
always wall-clock-anchored — fix the **test** to be tick-anchored, not the machine
(see [writing-a-live-test.md](writing-a-live-test.md)).

## Before a release

The release build runs the **unit gate only** (it can't carry the gitignored
server cache). So the live matrix is the **local pre-release gate**: run
`scripts/run-matrix.sh --all`, see every target PASS, *then* bump the version. See
[release-process.md](release-process.md).
