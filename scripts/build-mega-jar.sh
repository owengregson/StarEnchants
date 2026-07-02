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
#   module       bootstrap (the shipped plugin, default). Passing `tester` is a DEMONSTRATION only:
#                its two era-trees diverge in era-specific suites, so the soundness gate below REJECTS
#                the merge — the tester is boot-smoked PER ERA instead (scripts/run-matrix.sh boots the
#                modern v61 tester; scripts/legacy-smoke.sh the downgraded v52 one).
#   --no-build   reuse the existing modern + legacy jars (use only right after a fresh build).
# Output:  se/<module>/build/libs/StarEnchants[-Tester]-<version>-mega.jar
#
# Prereqs (same as the legacy lane): JDK 17+ on PATH, the BuildTools-local craftbukkit
# 1.8.8 in ~/.m2 for the legacy dual-compile gate, AND a real JDK 8 at $SE_LEGACY_WORK/jdk8 —
# build-legacy-jar.sh now runs the closed-world JDK-8 API gate (Gate 2) against that baseline.
# (Set SE_SKIP_JDK8_GATE=1 to bypass it for local iteration only — UNSOUND.) See build-legacy-jar.sh.
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
# DERIVED SOUNDNESS GATE (ADR-0044 §7 G2) — scripts/tools/MegaJarGate.java, invoked via scripts/mega-jar-gate.sh.
# Replaces the hand-maintained ALLOW_ERA_EXCLUSIVE(_PREFIX) allowlist that used to sit here: era-exclusivity is
# now READ from the overlay tree (a one-era class must have a source under overlay/<era>/), the one modern-only
# module (integrate) is DERIVED from the module set, and cross-era unreachability is PROVEN by a constant-pool
# walk — no list to drift. Same load-bearing invariant as before: on a modern JVM the classloader serves
# versions/17 for a class present there and the base (v52) copy otherwise, so a one-era class reachable from the
# other era loads with the wrong-era bytecode → NoSuchMethodError. The gate reads the two INPUT jars ($MOD modern,
# $LEG legacy) — their merge IS $MEGA. An era-divergent module (e.g. the tester's era-specific suites) is rejected
# by P1 exactly as before. See scripts/tools/MegaJarGate.java (P1–P6) and docs/decisions/0044-*.md.
#
# The declared same-FQN bindings twins (P4) + the command-carrying binding (P5, the v1_8_R3 era-direction check
# that generalises the old bootstrap.compat.Commands grep) live here — the composition-root-adjacent declaration.
# Empty entries are simply not asserted, so this grows as the ADR-0044 era-erasure migration lands each binding.
if [ "$MODULE" = "bootstrap" ]; then
  # The two same-FQN bindings twins (ADR-0044): bootstrap.compat.EraBindings (the composition-root manifest,
  # which also carries the v1_8_R3 command-map cast → the P5 era-direction check) and platform.resolve.HandleLookups.
  export SE_MEGA_BINDINGS="platform/resolve/HandleLookups,bootstrap/compat/EraBindings"
  export SE_MEGA_COMMAND_BINDING="bootstrap/compat/EraBindings"
fi
scripts/mega-jar-gate.sh "$MOD" "$LEG" "$ROOT"

# The merged base (non-versions) vs versions/17 class-name sets — used only by the sentinel self-check below
# (the derived gate above proves soundness; this picks any class present in both to verify the bytecode forked).
base_set="$(zipinfo -1 "$ROOT/$MEGA" | grep '\.class$' | grep -v '^META-INF/versions/' | grep -v '^se_jdg/' | LC_ALL=C sort)"
v17_set="$(zipinfo -1 "$ROOT/$MEGA" | sed -n 's#^META-INF/versions/17/\(.*\.class\)$#\1#p' | LC_ALL=C sort)"

# SELF-CHECK — assert the bytecode actually forked by era. Pick a sentinel class present in BOTH trees
# (so it exists under base AND versions/17), then require the base copy be Java 8 (class v52) and the
# versions/17 copy be Java 17 (class v61), plus Multi-Release: true. This is module-agnostic and is the
# real selection criterion, so it catches a botched merge regardless of which class names changed.
# NB: take the first shared class via bash parameter expansion, NOT `comm … | head -1` — `head` closing the
# pipe early gives `comm` a SIGPIPE/broken-pipe that `set -o pipefail` turns into a script failure (CI-flaky).
classmajor() { unzip -p "$ROOT/$MEGA" "$1" | od -An -tu1 -j6 -N2 | awk '{print $2}'; }
shared="$(comm -12 <(printf '%s\n' "$v17_set") <(printf '%s\n' "$base_set"))"
sentinel="${shared%%$'\n'*}"
[ -n "$sentinel" ] || { echo "ERROR: no shared sentinel class between base and versions/17 — merge is wrong" >&2; exit 1; }
base_major="$(classmajor "$sentinel")"
v17_major="$(classmajor "META-INF/versions/17/$sentinel")"
mr="$(unzip -p "$ROOT/$MEGA" META-INF/MANIFEST.MF | grep -ci '^Multi-Release: true' || true)"
if [ "$base_major" != "52" ] || [ "$v17_major" != "61" ] || [ "${mr:-0}" -lt 1 ]; then
  echo "ERROR: MRJAR self-check FAILED (sentinel=$sentinel: base class v=$base_major want 52; versions/17 v=$v17_major want 61; Multi-Release=$mr want ≥1)" >&2
  exit 1
fi
# The command-seam v1_8_R3 direction check is now MegaJarGate P5 (generalised onto the command-carrying binding
# via SE_MEGA_COMMAND_BINDING above), not a fixed bootstrap.compat.Commands grep — see the derived gate above.

base_n="$(zipinfo -1 "$ROOT/$MEGA" | grep '\.class$' | grep -vc '^META-INF/versions/')"
v17_n="$(zipinfo -1 "$ROOT/$MEGA" | grep -c '^META-INF/versions/17/.*\.class$')"
echo "[mega] done → $MEGA"
echo "[mega]   base classes (v52, Java 8) : $base_n"
echo "[mega]   versions/17 (v61, Java 16+): $v17_n"
echo "[mega]   self-check                 : sentinel $sentinel  base=v$base_major versions/17=v$v17_major  Multi-Release ✓"
echo "[mega]   size                       : $(ls -lh "$ROOT/$MEGA" | awk '{print $5}')"
