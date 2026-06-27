#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────
#  jdk8-api-gate.sh — Gate 2: the closed-world JDK-8 API check (§3.4 / R1)
# ─────────────────────────────────────────────────────────────────────────
#  Scans a DOWNGRADED legacy (v52) jar and fails if it references any java.*/
#  javax.* API that does not exist in real Java 8 — the static net for the
#  un-shimmable JDK-9+ stdlib hazard JvmDowngrader silently passes through
#  (e.g. ThreadLocalRandom.nextDouble(double), which NoSuchMethodError'd on a
#  real 1.8 server). See docs/legacy-1.8.9-codeshare-design.md (Gate 2).
#
#  Embedded into scripts/build-legacy-jar.sh right after the JDG step, so it
#  gates EVERY legacy-producing path from one chokepoint: legacy-smoke.sh
#  (PR + push lanes) and build-mega-jar.sh (the release lane).
#
#  The checker (scripts/tools/Jdk8ApiGate.java) is a tiny standalone ASM tool,
#  compiled+run ad-hoc against an ASM jar fetched once into $WORK — mirroring
#  how the JDG CLI itself is fetched. It does NOT touch the Gradle build.
#
#  Baseline = a REAL JDK 8 (rt.jar + the rest of jre/lib + ext). That is already
#  a hard precondition everywhere this runs: legacy-smoke.sh requires $WORK/jdk8
#  locally, and both .github/workflows/legacy.yml and release.yml provision it.
#
#  Usage:  scripts/jdk8-api-gate.sh <downgraded.jar> [<jdk8Home>]
#  Env:
#    SE_LEGACY_WORK            work dir holding jdk8/ + the ASM jar (default $HOME/se-legacy-buildtools)
#    SE_ASM_VERSION            ASM version to fetch (default 9.7)
#    SE_JDK8_GATE_STRICT       1 => promote JDK-internal (sun/jdk/com.sun) warnings to hard failures
#    SE_SKIP_JDK8_GATE         1 => skip entirely (UNSOUND; honored by the caller, not here)
# ─────────────────────────────────────────────────────────────────────────
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

WORK="${SE_LEGACY_WORK:-$HOME/se-legacy-buildtools}"
ASM_VERSION="${SE_ASM_VERSION:-9.7}"

JAR="${1:-}"
JDK8="${2:-$WORK/jdk8}"
[ -n "$JAR" ] || { echo "[jdk8-gate] ERROR: no jar given (usage: jdk8-api-gate.sh <downgraded.jar> [<jdk8Home>])" >&2; exit 2; }
[ -f "$JAR" ] || { echo "[jdk8-gate] ERROR: jar not found: $JAR" >&2; exit 2; }

# The baseline is non-negotiable: you cannot soundly validate a 1.8 jar without the 1.8 API. The escape
# hatch (skip the gate) lives in the CALLER, and is loud there; reaching here without JDK 8 is a hard error.
RTJAR="$JDK8/jre/lib/rt.jar"
[ -f "$RTJAR" ] || RTJAR="$JDK8/lib/rt.jar"
if [ ! -f "$RTJAR" ]; then
  echo "[jdk8-gate] ERROR: no JDK 8 rt.jar under $JDK8 — the closed-world gate needs a real JDK 8 baseline." >&2
  echo "[jdk8-gate]        Provision one at \$SE_LEGACY_WORK/jdk8 (a JDK 8 home), or set SE_SKIP_JDK8_GATE=1" >&2
  echo "[jdk8-gate]        in the caller to bypass (UNSOUND — for local iteration only)." >&2
  exit 2
fi

# Fetch ASM once (one small jar; same ad-hoc pattern as the JDG CLI in build-legacy-jar.sh).
ASM_JAR="$WORK/asm-${ASM_VERSION}.jar"
if [ ! -f "$ASM_JAR" ]; then
  echo "[jdk8-gate] downloading ASM ${ASM_VERSION} ..." >&2
  mkdir -p "$WORK"
  if ! curl -sL --fail -o "$ASM_JAR" \
      "https://repo1.maven.org/maven2/org/ow2/asm/asm/${ASM_VERSION}/asm-${ASM_VERSION}.jar"; then
    rm -f "$ASM_JAR"
    echo "[jdk8-gate] ERROR: failed to download ASM ${ASM_VERSION} from Maven Central" >&2
    exit 2
  fi
fi
# Guard against a truncated / HTML-error download: it must be a real jar carrying ClassReader.
if ! unzip -l "$ASM_JAR" 'org/objectweb/asm/ClassReader.class' >/dev/null 2>&1; then
  rm -f "$ASM_JAR"
  echo "[jdk8-gate] ERROR: $ASM_JAR is not a valid ASM jar (corrupt download) — re-run to refetch" >&2
  exit 2
fi

# Compile the checker (tiny; recompile each run — keeps it honest against source edits).
SRC="$ROOT/scripts/tools/Jdk8ApiGate.java"
[ -f "$SRC" ] || { echo "[jdk8-gate] ERROR: checker source missing: $SRC" >&2; exit 2; }
OUT="$WORK/jdk8gate-classes"
rm -rf "$OUT"; mkdir -p "$OUT"
if ! javac -cp "$ASM_JAR" -d "$OUT" "$SRC"; then
  echo "[jdk8-gate] ERROR: failed to compile $SRC" >&2
  exit 2
fi

ARGS=("$JAR" "$JDK8")
[ "${SE_JDK8_GATE_STRICT:-0}" = "1" ] && ARGS+=(--strict-internal)
[ -f "$ROOT/scripts/jdk8-api-gate.allow" ] && ARGS+=(--allow "$ROOT/scripts/jdk8-api-gate.allow")

# Use the SAME java that compiled it (PATH java == javac), so major versions match.
java -cp "$OUT:$ASM_JAR" Jdk8ApiGate "${ARGS[@]}"
