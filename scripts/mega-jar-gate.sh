#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────
#  mega-jar-gate.sh — the DERIVED mega-jar soundness gate (ADR-0044 §7 G2)
# ─────────────────────────────────────────────────────────────────────────
#  Replaces the hand-maintained ALLOW_ERA_EXCLUSIVE / ALLOW_ERA_EXCLUSIVE_PREFIX
#  allowlist that used to live inline in build-mega-jar.sh. Given the two INPUT
#  jars (modern v61 + downgraded legacy v52) and the repo root, it proves the
#  merge is sound WITHOUT an allowlist:
#    P1  class-set divergence is exactly the overlay-derived era-exclusive set
#    P2  no class references a class that exists only in the OTHER era's tree
#    P3  a whole modern-only module (integrate) is derived from the module set
#    P4  each declared bindings twin is present in BOTH eras (shadowing)
#    P5  the command-carrying binding forks v1_8_R3 by era (base yes, modern no)
#    P6  every single-sourced resource is byte-identical across the two jars
#  See scripts/tools/MegaJarGate.java for the proofs and their failure shapes.
#
#  The checker is a tiny standalone ASM tool, compiled+run ad-hoc against a
#  cached ASM jar — the SAME pattern as scripts/jdk8-api-gate.sh, so it touches
#  neither the Gradle build nor the modern spine.
#
#  Usage:  scripts/mega-jar-gate.sh <modernJar> <legacyJar> [<repoRoot>]
#  Env:
#    SE_LEGACY_WORK   work dir holding the cached ASM jar (default $HOME/se-legacy-buildtools)
#    SE_ASM_VERSION   ASM version to fetch (default 9.7)
#    SE_MEGA_BINDINGS         comma-separated same-FQN bindings twins (P4)
#    SE_MEGA_COMMAND_BINDING  the one binding carrying the v1_8_R3 command seam (P5)
#    SE_MEGA_MODULES          override the derived module-root set (P3; normally omitted)
# ─────────────────────────────────────────────────────────────────────────
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

WORK="${SE_LEGACY_WORK:-$HOME/se-legacy-buildtools}"
ASM_VERSION="${SE_ASM_VERSION:-9.7}"

MOD="${1:-}"
LEG="${2:-}"
REPO="${3:-$ROOT}"
[ -n "$MOD" ] && [ -n "$LEG" ] || { echo "[mega-gate] ERROR: usage: mega-jar-gate.sh <modernJar> <legacyJar> [<repoRoot>]" >&2; exit 2; }
[ -f "$MOD" ] || { echo "[mega-gate] ERROR: modern jar not found: $MOD" >&2; exit 2; }
[ -f "$LEG" ] || { echo "[mega-gate] ERROR: legacy jar not found: $LEG" >&2; exit 2; }

# Fetch ASM once (same ad-hoc pattern as jdk8-api-gate.sh).
ASM_JAR="$WORK/asm-${ASM_VERSION}.jar"
if [ ! -f "$ASM_JAR" ]; then
  echo "[mega-gate] downloading ASM ${ASM_VERSION} ..." >&2
  mkdir -p "$WORK"
  if ! curl -sL --fail -o "$ASM_JAR" \
      "https://repo1.maven.org/maven2/org/ow2/asm/asm/${ASM_VERSION}/asm-${ASM_VERSION}.jar"; then
    rm -f "$ASM_JAR"
    echo "[mega-gate] ERROR: failed to download ASM ${ASM_VERSION} from Maven Central" >&2
    exit 2
  fi
fi
if ! unzip -l "$ASM_JAR" 'org/objectweb/asm/ClassReader.class' >/dev/null 2>&1; then
  rm -f "$ASM_JAR"
  echo "[mega-gate] ERROR: $ASM_JAR is not a valid ASM jar (corrupt download) — re-run to refetch" >&2
  exit 2
fi

# Compile the checker (tiny; recompile each run — keeps it honest against source edits).
SRC="$ROOT/scripts/tools/MegaJarGate.java"
[ -f "$SRC" ] || { echo "[mega-gate] ERROR: checker source missing: $SRC" >&2; exit 2; }
OUT="$WORK/megagate-classes"
rm -rf "$OUT"; mkdir -p "$OUT"
if ! javac -cp "$ASM_JAR" -d "$OUT" "$SRC"; then
  echo "[mega-gate] ERROR: failed to compile $SRC" >&2
  exit 2
fi

ARGS=("$MOD" "$LEG" "$REPO")
[ -n "${SE_MEGA_BINDINGS:-}" ]        && ARGS+=(--bindings "$SE_MEGA_BINDINGS")
[ -n "${SE_MEGA_MODULES:-}" ]         && ARGS+=(--modules "$SE_MEGA_MODULES")
[ -n "${SE_MEGA_COMMAND_BINDING:-}" ] && ARGS+=(--command-binding "$SE_MEGA_COMMAND_BINDING")

# Use the SAME java that compiled it (PATH java == javac), so major versions match.
java -cp "$OUT:$ASM_JAR" MegaJarGate "${ARGS[@]}"
