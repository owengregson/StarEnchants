#!/usr/bin/env bash
#
# Build the single MEGA-JAR — ONE artifact that auto-selects bytecode by server era:
#   • on a modern Paper/Folia server (1.17.1 → 26.1.x, JVM 16+) it loads the MODERN
#     Java-17 (class v61) classes;
#   • on a Minecraft 1.8.x server (JVM 8) it loads the LEGACY Java-8 (class v52) classes.
# Selection is automatic via the Multi-Release JAR mechanism (JEP 238) — no reflection,
# no version probe. See docs/legacy-1.8.9-codeshare-design.md §10 (this supersedes its
# "one universal jar is impossible" line, which assumed a single bytecode tree + reflection;
# an MRJAR ships BOTH pre-built trees and the JVM picks one).
#
# Why "which JVM" == "which era": a 1.8.x server runs ONLY on Java 8 (which ignores
# META-INF/versions/ → reads the base v52 tree), and a 1.17.1+ server runs ONLY on
# Java 16+ (Paper opens the plugin JarFile with JarFile.runtimeVersion() → reads
# META-INF/versions/17 → v61). The Java version is a 1:1 proxy for the server era.
#
# CLEAN, ORDER-INDEPENDENT BUILD (the rational design): the two eras compile into SEPARATE
# build dirs — modern → se/<m>/build/, legacy (-Pse.target=legacy) → se/<m>/build-legacy/
# (root build.gradle.kts redirects the buildDir). So the two jars NEVER share a filename or a
# classes dir: no clobber, no "build modern before legacy" ordering, and no incremental-compile
# cross-contamination across the overlay srcDir swap. The merge just reads the modern jar from
# build/ and the downgraded legacy jar from build-legacy/, in any order, and a self-check at the
# end asserts the bytecode actually forked (base = v52, versions/17 = v61, Multi-Release: true).
#
# Layout produced:
#   <root>/**.class                 legacy v52 classes              (Java 8 / 1.8.x)
#   <root>/se_jdg/**                 JvmDowngrader api+runtime shims (v52; inert on modern)
#   <root>/{plugin.yml,content/,…}   resources — identical in both trees, shared once
#   META-INF/versions/17/**.class    modern v61 classes             (Java 16+ / 1.17.1→26.1.x)
#   META-INF/MANIFEST.MF + Multi-Release: true
#
# Usage:   scripts/build-mega-jar.sh [bootstrap|tester] [--no-build]
#   module       bootstrap (the shipped plugin, default) | tester (the in-server harness, also
#                multi-release so ONE tester jar boots on every era — see scripts/run-matrix.sh).
#   --no-build   reuse the existing modern + legacy jars (use only right after a fresh build).
# Output:  se/<module>/build/libs/StarEnchants[-Tester]-<version>-mega.jar
#
# Prereqs (same as the legacy lane): JDK 17+ on PATH, and the BuildTools-local craftbukkit
# 1.8.8 in ~/.m2 for the legacy dual-compile gate (see scripts/build-legacy-jar.sh).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MODULE="bootstrap"
NO_BUILD=0
for a in "$@"; do
  case "$a" in
    bootstrap|tester) MODULE="$a" ;;
    --no-build) NO_BUILD=1 ;;
    *) echo "ERROR: unknown argument '$a' (expected: bootstrap | tester | --no-build)" >&2; exit 2 ;;
  esac
done
case "$MODULE" in
  bootstrap) OUT_NAME="StarEnchants" ;;
  tester)    OUT_NAME="StarEnchants-Tester" ;;
esac

VERSION="$(grep -E '^[[:space:]]*version = "' build.gradle.kts | head -1 | sed -E 's/.*version = "(.*)".*/\1/')"
[ -n "$VERSION" ] || { echo "ERROR: could not read project version from build.gradle.kts" >&2; exit 2; }

MOD="se/${MODULE}/build/libs/${MODULE}-${VERSION}.jar"                       # modern fat jar (v61), build/
LEG="se/${MODULE}/build-legacy/libs/${OUT_NAME}-${VERSION}-legacy.jar"       # downgraded legacy (v52), build-legacy/
MEGA="se/${MODULE}/build/libs/${OUT_NAME}-${VERSION}-mega.jar"               # the merged MRJAR

if [ "$NO_BUILD" = "1" ]; then
  echo "[mega] --no-build: reusing existing ${MODULE} jars"
else
  # Order-independent: the two builds write to disjoint dirs (build/ vs build-legacy/), so neither
  # can clobber or incrementally contaminate the other.
  echo "[mega] 1/3  build the MODERN ${MODULE} fat jar (Java 17 / v61 → build/) ..."
  ./gradlew ":${MODULE}:jar"
  echo "[mega] 2/3  build the LEGACY ${MODULE} fat jar (Java 8 / v52 + se_jdg → build-legacy/) ..."
  scripts/build-legacy-jar.sh "${MODULE}"
fi
[ -f "$MOD" ] || { echo "ERROR: modern jar not found: $MOD (drop --no-build to build it)" >&2; exit 2; }
[ -f "$LEG" ] || { echo "ERROR: legacy jar not found: $LEG (drop --no-build to build it)" >&2; exit 2; }

echo "[mega] 3/3  merge → MRJAR  (base=${LEG##*/}, versions/17=${MOD##*/} .class) ..."
WORK="$(mktemp -d "${TMPDIR:-/tmp}/se-mega.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

# Base tree = the ENTIRE downgraded legacy jar (build-legacy/). For bootstrap this is the exact artifact
# whose boot+enable on craftbukkit-1.8.8 under JDK 8 is proven by scripts/mega-smoke.sh. We add to it; we
# never alter a legacy class.
mkdir -p "$WORK/base"
( cd "$WORK/base" && unzip -oq "$ROOT/$LEG" )

# Overlay = ONLY the modern *.class files (resources are identical and already in base), unpacked straight
# under META-INF/versions/17 so a runtime-versioned JarFile prefers them on Java 16+.
mkdir -p "$WORK/base/META-INF/versions/17"
( cd "$WORK/base/META-INF/versions/17" && unzip -oq "$ROOT/$MOD" '*.class' )

# Flip the manifest to a Multi-Release JAR (idempotent; CRLF-correct so the Manifest parser is happy).
MF="$WORK/base/META-INF/MANIFEST.MF"
perl -0777 -pe 's/(Manifest-Version:[^\r\n]*\r?\n)/$1Multi-Release: true\r\n/ unless /^Multi-Release:/mi' \
  "$MF" > "$MF.tmp" && mv "$MF.tmp" "$MF"

# Repack deterministically: MANIFEST first (tidy, though JarFile reads the central directory either way),
# then every other entry in sorted order. -X drops platform extra attributes for reproducibility.
rm -f "$ROOT/$MEGA"
( cd "$WORK/base" \
    && zip -qX "$ROOT/$MEGA" META-INF/MANIFEST.MF \
    && find . -type f ! -path './META-INF/MANIFEST.MF' | LC_ALL=C sort | zip -qX@ "$ROOT/$MEGA" )

echo "[mega] 4/4  verify the merged MRJAR ..."
# SOUNDNESS GATE — the two trees' class sets must be IDENTICAL except for known era-exclusive seam classes.
# Why this is load-bearing: on a modern JVM the classloader serves versions/17 for any class present there
# and the base (v52) copy otherwise. So a class that exists ONLY in the base, if reachable from modern code,
# loads as v52 and calls shared classes using their LEGACY signatures — but those shared classes resolve to
# v61 (modern signatures, e.g. DispatchSinkFactory(RenameResolvers) vs (RuntimeHandles)) → NoSuchMethodError.
# A clean merge therefore requires the modern and legacy class sets to MATCH; the only allowed difference is
# the era-exclusive seam pair (each era's own impl of a swapped seam, which that era's code never cross-
# references — verified). A larger divergence (e.g. the tester's era-specific suites) is UNSOUND to merge and
# is rejected here. See docs/legacy-1.8.9-codeshare-design.md.
ALLOW_ERA_EXCLUSIVE="item/codec/LegacyNbt.class platform/resolve/RuntimeHandles.class"
base_set="$(zipinfo -1 "$ROOT/$MEGA" | grep '\.class$' | grep -v '^META-INF/versions/' | grep -v '^se_jdg/' | LC_ALL=C sort)"
v17_set="$(zipinfo -1 "$ROOT/$MEGA" | sed -n 's#^META-INF/versions/17/\(.*\.class\)$#\1#p' | LC_ALL=C sort)"
diverge="$(comm -3 <(printf '%s\n' "$base_set") <(printf '%s\n' "$v17_set") | tr -d '\t' | grep -v '^$' || true)"
unsound=0
for c in $diverge; do
  case " $ALLOW_ERA_EXCLUSIVE " in
    *" $c "*) ;;
    *) echo "ERROR: UNSOUND merge — '$c' exists in only ONE era's tree (not an allowlisted era seam)." >&2; unsound=1 ;;
  esac
done
if [ "$unsound" -ne 0 ]; then
  echo "       A one-era-only class loads with its own bytecode version on the matching JVM and can call a" >&2
  echo "       shared class with the wrong-era signature → NoSuchMethodError. Only a module whose two trees" >&2
  echo "       have identical class sets (the shipped plugin) can be MRJAR-merged; era-divergent modules" >&2
  echo "       (e.g. the tester, with its era-specific suites) cannot. Build + boot those per-era instead." >&2
  exit 1
fi

# SELF-CHECK — assert the bytecode actually forked by era. Pick a sentinel class present in BOTH trees
# (so it exists under base AND versions/17), then require the base copy be Java 8 (class v52) and the
# versions/17 copy be Java 17 (class v61), plus Multi-Release: true. This is module-agnostic and is the
# real selection criterion, so it catches a botched merge regardless of which class names changed.
classmajor() { unzip -p "$ROOT/$MEGA" "$1" | od -An -tu1 -j6 -N2 | awk '{print $2}'; }
sentinel="$(comm -12 \
    <(zipinfo -1 "$ROOT/$MEGA" | sed -n 's#^META-INF/versions/17/\(.*\.class\)$#\1#p' | LC_ALL=C sort) \
    <(zipinfo -1 "$ROOT/$MEGA" | grep '\.class$' | grep -v '^META-INF/versions/' | grep -v '^se_jdg/' | LC_ALL=C sort) \
  | head -1)"
[ -n "$sentinel" ] || { echo "ERROR: no shared sentinel class between base and versions/17 — merge is wrong" >&2; exit 1; }
base_major="$(classmajor "$sentinel")"
v17_major="$(classmajor "META-INF/versions/17/$sentinel")"
mr="$(unzip -p "$ROOT/$MEGA" META-INF/MANIFEST.MF | grep -ci '^Multi-Release: true' || true)"
if [ "$base_major" != "52" ] || [ "$v17_major" != "61" ] || [ "${mr:-0}" -lt 1 ]; then
  echo "ERROR: MRJAR self-check FAILED (sentinel=$sentinel: base class v=$base_major want 52; versions/17 v=$v17_major want 61; Multi-Release=$mr want ≥1)" >&2
  exit 1
fi
# Extra defense for the plugin's command seam: bootstrap.compat.Commands forks to v1_8_R3 on legacy and to
# Server.getCommandMap() on modern, so base MUST reference v1_8_R3 and the versions/17 copy MUST NOT.
if [ "$MODULE" = "bootstrap" ] && zipinfo -1 "$ROOT/$MEGA" | grep -Fxq 'bootstrap/compat/Commands.class'; then
  if unzip -p "$ROOT/$MEGA" bootstrap/compat/Commands.class | grep -qa 'v1_8_R3'; then base_v18=1; else base_v18=0; fi
  if unzip -p "$ROOT/$MEGA" META-INF/versions/17/bootstrap/compat/Commands.class | grep -qa 'v1_8_R3'; then v17_v18=1; else v17_v18=0; fi
  if [ "$base_v18" -ne 1 ] || [ "$v17_v18" -ne 0 ]; then
    echo "ERROR: command-seam check FAILED (base→1.8 v1_8_R3=$base_v18 want 1; versions/17→modern v1_8_R3=$v17_v18 want 0)" >&2
    exit 1
  fi
fi

base_n="$(zipinfo -1 "$ROOT/$MEGA" | grep '\.class$' | grep -vc '^META-INF/versions/')"
v17_n="$(zipinfo -1 "$ROOT/$MEGA" | grep -c '^META-INF/versions/17/.*\.class$')"
echo "[mega] done → $MEGA"
echo "[mega]   base classes (v52, Java 8) : $base_n"
echo "[mega]   versions/17 (v61, Java 16+): $v17_n"
echo "[mega]   self-check                 : sentinel $sentinel  base=v$base_major versions/17=v$v17_major  Multi-Release ✓"
echo "[mega]   size                       : $(ls -lh "$ROOT/$MEGA" | awk '{print $5}')"
