# ADR 0042: One error-handling + logging policy

- **Status:** Accepted
- **Date:** 2026-07-02
- **Deciders:** StarEnchants maintainers

## Context

Error handling and logging had drifted into eight divergent local habits, so the same class of
fault behaved differently depending on which file it happened in:

1. **Silent auto-reload** — the timed `reloader.reload(result -> { })` discarded its `ReloadResult`
   entirely, so a failing or rejected auto-reload left the operator with silence while `/se reload`
   reported the identical faults.
2. **Stack-dropping boot failure** — a total content-load failure at boot bypassed the diagnostics
   system and string-concatenated the throwable (`"…: " + failure`), dropping the stack trace.
3. **Three directory-listing policies** — `LibraryLoader` threw `UncheckedIOException`, while
   `ItemsLoader` and `MenusLoader` silently returned an empty list, on the same "directory became
   unreadable" fault.
4. **Five-plus private number/bool parse copies** — `MasterConfigLoader`, `ItemsLoader`,
   `MenusLoader`, `ParticleSpec`, `SoundCue`, and `SetDefReader` each re-implemented
   `NumberFormatException` catching with divergent DiagCodes and severities.
5. **Open string diagnostic codes** — `migrate` and `platform` still minted bare-string codes
   (`migrate.*`, `reload.busy`, `reload.failed`) via retained `String` overloads on `Diagnostics` /
   `Diagnostic`, defeating the closed `DiagCode` set.
6. **A double-meaning code** — `W_LOAD_EFFECTS` was reused for both "an ability declares no effects"
   and "an on:weapon bonus can never fire".
7. **Hand-rolled cross-region guards** — the expected Folia cross-region `RuntimeException` was
   caught-and-swallowed silently at ~10 sites, active on Paper too, where it could only hide a real
   bug.
8. **Two logging stacks concatenating exceptions** — JUL sites string-concatenated `getMessage()`
   (losing the stack exactly where reflection failures need it), and the tester `Harness` recorded
   only `Throwable.toString()`.

## Decision

- **Auto-reload logs its result.** Failure throwables at `WARNING` with the exception object;
  rejected reloads log the blocking-diagnostic count + first few rendered diagnostics; single-flight
  rejections and clean swaps at `FINE` (busy at FINE so a build outlasting the period cannot self-spam).
- **Boot total-failure is diagnosed and traced.** It records an `E_LOAD_IO` diagnostic into the empty
  Library and logs `SEVERE` with the stack via the log-record parameter.
- **One directory-listing policy.** All three loaders emit an `E_*_IO` diagnostic and none throws —
  the `compile` module is uniformly non-throwing. Both `IOException` (open fault) and
  `UncheckedIOException` (iteration fault from `Files.walk`/`list`) are caught.
- **One `ContentParse` number/bool family.** `intOr` / `doubleOr` / `boolOr` parameterized by
  `DiagCode` + `Severity`, with strict/lenient named entry points. Content numbers block
  (`Severity.ERROR`); config/item/menu numbers warn-and-fall-back (`Severity.WARNING`). One canonical
  truthy/falsy vocabulary (`true/yes/on/1` | `false/no/off/0`) warns on anything else. The policy is
  stated once, in a comment on the family; the private copies are deleted.
- **The `DiagCode` set is closed.** Added `W_LOAD_BOOL`, `W_CONFIG_BOOL`,
  `W_LOAD_SET_WEAPON_UNREACHABLE` (splitting `W_LOAD_EFFECTS`), `E_RELOAD_BUSY`, `E_RELOAD_FAILED`,
  and `W_MIGRATE_ID/TRIGGER/APPLIES/EFFECT`; converted every producer; then removed the `String`
  overloads so an off-catalogue code cannot compile. `ReloadResult` also gained a `Throwable failure`
  component so the build crash rides the result.
- **One cross-region guard: `platform.caps.Regions`.** `read(site, body, fallback)` for cold callers,
  `swallowed(site, fault)` for hot callers that keep a local try/catch (no per-hit lambda capture). On
  Folia the expected cross-region `RuntimeException` falls back silently; on non-Folia it logs the full
  stack at `FINE` (DEBUG) instead of vanishing. It lives in `platform.caps`, NOT `platform.sched`,
  because `EngineBoundaryArchTest.onlySinkAndBootTouchScheduling` bans `engine.run` (`FactPopulator`)
  from depending on `platform.sched..`, and the guard is a capability question, not a scheduling
  operation. Non-Folia logs rather than rethrows: rethrowing would turn a latent bug into a
  gameplay-visible crash mid-combat, a behaviour change the constraints forbid.
- **Logging convention.** Bukkit shells log via `plugin.getLogger()` (JUL); plugin-less modules via
  `System.Logger` named `StarEnchants.<Area>`. ALWAYS pass the `Throwable` as the log parameter —
  never concatenate it.
- **Tester.** `Harness.fail(String, Throwable)` writes the same toString to the results file but logs
  the full stack; guard/launch and the suite catch sites route through it.

## Consequences

The ONLY observable changes:

1. **Diagnostic code strings changed** in rendered lines: `reload.busy` → `E_RELOAD_BUSY`,
   `reload.failed` → `E_RELOAD_FAILED`, `migrate.*` → `W_MIGRATE_*`, and the weapon-unreachable case
   `W_LOAD_EFFECTS` → `W_LOAD_SET_WEAPON_UNREACHABLE`. These are operator-visible in `/se problems`
   / migration-review output. No `lang.yml` chat strings changed.
2. **Previously-silent faults now diagnosed** — an unlistable `items/` or `menus/` directory now
   emits an `E_ITEM_IO` / `E_MENU_IO` diagnostic (was a silent empty result), and a config boolean
   outside the canonical vocabulary now warns `W_CONFIG_BOOL` (was a silent fallback).

Everything else is logging only — no accept/reject decision changes. The single deliberate control-flow
change is `LibraryLoader` on an unlistable content directory: it now records a diagnostic and returns
empty instead of throwing, so `ContentReloader` reports a rejected result rather than a build crash.

## Alternatives considered

- **Rethrow on Paper in the cross-region guard** — rejected: a latent bug would become a mid-combat
  crash; `FINE`-with-stack surfaces it without changing behaviour.
- **`Regions` in `platform.sched`** (the charter's suggestion) — rejected: it would break the
  `engine.run` scheduling-boundary ArchUnit rule; the guard belongs with capabilities.
- **Keep the `String` overloads for migration headroom** — rejected: the whole point is a
  compiler-enforced closed set; test fixtures that need a synthetic code use the canonical `Diagnostic`
  constructor instead.
