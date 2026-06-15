---
name: matrix-gate
description: Use when running or verifying StarEnchants' test gate — which commands to run, the Paper+Folia version matrix, concurrency rules for booting many servers, and how to read results honestly instead of trusting a success banner.
---

# The verification gate

Two layers, always in order:

```bash
./gradlew build          # compile + unit tests (pure logic) — ALWAYS first
<integration matrix>     # boot real Paper + Folia servers, run live suites
```

The integration matrix boots a real server per (platform, version), installs
the StarEnchants + tester jars, runs the in-server suites
(`live-server-testing`), writes PASS/FAIL, and shuts down. The paired check
fails the build on anything but a fresh PASS.

## The matrix (Paper + Folia across the range)

Cover floor, the mapping-flip boundary, and the ceiling — plus Folia where it
exists (Folia builds begin ~1.19.4):

- **Paper**: 1.17.1, 1.18.2, 1.19.4, 1.20.6, 1.21.x, 26.1.x (floor, mid, the
  1.20.5 spigot→mojang flip on both sides, ceiling).
- **Folia**: 1.19.4+, e.g. 1.20.6, 1.21.x, 26.1.x.

Keep the list in `gradle.properties`; adding a version updates the Java-toolchain
boundary check (17 for ≤1.20.4, 21+ for 1.20.5+) and caches a paperclip/folia
jar (`paper-cross-version`).

## Reading results HONESTLY

- **Never trust "BUILD SUCCESSFUL" / a passing banner alone.** Verify each
  server's `test-results.txt` is FRESH (mtime within this run) and reads PASS;
  failures detail in `test-failures.txt`. A server that failed to boot leaves a
  stale or missing result, not a red banner.
- A hung/silently-stalled server's LOG is the only evidence it leaves — keep
  per-server logs and read them on any non-PASS.
- A green Paper run says nothing about Folia — both must be green.

## Concurrency rules (booting many JVMs)

- Launch the heaviest (newest) servers first with a small stagger so the fast
  old servers boot into a calm machine.
- Small heaps, far from memory pressure — page-fault storms read as tick
  stalls.
- On macOS, keep each JVM awake (`caffeinate -i`); an App-Napped background JVM
  stalls for tens of seconds without ever logging "Can't keep up".
- A killed run leaves orphan servers holding `world/session.lock` and ports —
  reap leftover server processes before re-running.
- A test correct sequentially but flaky under concurrent load is almost always
  wall-clock-anchored — fix the test to be tick-anchored, not the load
  (`live-server-testing`).
