#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────
#  legacy-smoke.sh — Gate 4: the LIVE 1.8.9 reduced smoke gate
# ─────────────────────────────────────────────────────────────────────────
#  Builds the downgraded TESTER fat jar (-Pse.target=legacy + JvmDowngrader 61→52),
#  boots a real craftbukkit-1.8.8 server under JDK 8, lets the in-server harness run
#  ONLY the reduced legacy smoke suite (the legacy build compiles just the 1.8-safe subset
#  and swaps the plugin main to tester.legacy.LegacySmokePlugin, which starts the curated
#  subset via a delayed task — no ServerLoadEvent, absent on 1.8; see se/tester/build.gradle.kts),
#  then reads the FRESH test-results.txt HONESTLY: a server that failed to boot leaves
#  a stale/missing result, never a green banner (matrix-gate skill).
#
#  This is the 1.8 analogue of scripts/run-matrix.sh (which gates 1.17.1 → 26.1.x on
#  Paper + Folia). The floor stays 1.17.1; 1.8.9 is the OPTIONAL second jar, and per the
#  §11 ownership precondition it must not ship without this gate green.
#
#  Prerequisites (one-time, machine-local — NOT on any public repo, so not in CI yet):
#    - $WORK/craftbukkit-1.8.8.jar   (Spigot BuildTools: java -jar BuildTools.jar --rev 1.8.8 --compile craftbukkit)
#    - $WORK/jdk8/bin/java           (a JDK 8 — on Apple Silicon an x86_64 build, run via Rosetta)
#  where $WORK = ${SE_LEGACY_WORK:-$HOME/se-legacy-buildtools}.
#
#  Env:
#    SE_LEGACY_WORK   buildtools/work dir (default $HOME/se-legacy-buildtools)
#    SE_NO_BUILD=1    skip the jar build (use ONLY right after a fresh build of the legacy tester jar)
#    SE_WATCHDOG_SECS hard timeout before the server is killed (default 150)
#    SE_KEEP_RUNDIR=1 keep the smoke server dir for inspection (default: keep; set 0 to clean on PASS)
# ─────────────────────────────────────────────────────────────────────────
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

WORK="${SE_LEGACY_WORK:-$HOME/se-legacy-buildtools}"
WATCHDOG_SECS="${SE_WATCHDOG_SECS:-150}"
NO_BUILD="${SE_NO_BUILD:-0}"
KEEP_RUNDIR="${SE_KEEP_RUNDIR:-1}"

if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  C_RESET=$'\033[0m'; C_DIM=$'\033[2m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_RED=$'\033[31m'; C_BOLD=$'\033[1m'
else
  C_RESET=''; C_DIM=''; C_GREEN=''; C_YELLOW=''; C_RED=''; C_BOLD=''
fi
log()  { printf '%s[legacy-smoke]%s %s\n' "$C_DIM" "$C_RESET" "$*" >&2; }
pass() { printf '%s[legacy-smoke] PASS%s %s\n' "$C_GREEN" "$C_RESET" "$*" >&2; }
warn() { printf '%s[legacy-smoke] WARN%s %s\n' "$C_YELLOW" "$C_RESET" "$*" >&2; }
err()  { printf '%s[legacy-smoke] FAIL%s %s\n' "$C_RED" "$C_RESET" "$*" >&2; }

# ── Preconditions ─────────────────────────────────────────────────────────
CB_JAR="$WORK/craftbukkit-1.8.8.jar"
J8="$WORK/jdk8/bin/java"
[ -f "$CB_JAR" ] || { err "craftbukkit-1.8.8 jar not found at $CB_JAR (run Spigot BuildTools once)"; exit 2; }
[ -x "$J8" ]     || { err "JDK 8 not found at $J8 (extract a JDK 8 under \$WORK/jdk8)"; exit 2; }

# Apple Silicon needs Rosetta to run an x86_64 JDK 8; on x86 hosts ARCH is a no-op passthrough.
ARCH_PREFIX=()
if [ "$(uname -s)" = "Darwin" ] && [ "$(uname -m)" = "arm64" ]; then ARCH_PREFIX=(arch -x86_64); fi

# ── 1. Build + downgrade the legacy tester jar ────────────────────────────
# The tester is NOT a Multi-Release jar: its modern and legacy trees diverge in era-specific suites/signatures,
# which build-mega-jar.sh's soundness gate rejects — only the SHIPPED plugin (identical class sets) merges into
# one jar. So the 1.8 gate boots the DOWNGRADED legacy tester (v52); the modern matrix (scripts/run-matrix.sh)
# boots the modern tester (v61). The shipped MEGA-plugin itself is boot-smoked on both eras by scripts/mega-smoke.sh.
if [ "$NO_BUILD" = "1" ]; then
  warn "SE_NO_BUILD set — skipping the jar build; the legacy tester jar may be STALE"
else
  log "building + downgrading the legacy tester fat jar (this runs the dual-compile gate too) ..."
  if ! scripts/build-legacy-jar.sh tester; then
    err "legacy tester jar build failed — refusing to boot against a stale/broken jar"
    exit 1
  fi
fi

# Pin the legacy tester to the CANONICAL version: build-legacy/libs can retain a stale
# StarEnchants-Tester-<oldversion>-legacy.jar that a bare `find | head -1` would grab by directory order,
# silently smoke-testing OLD code (a false PASS). Select the exact current-version filename instead.
# (-Pse.target=legacy redirects the buildDir to build-legacy/, so the downgraded tester lives there.)
VERSION="$(grep -E '^[[:space:]]*version = "' "$ROOT/build.gradle.kts" | head -1 | sed -E 's/.*version = "(.*)".*/\1/')"
[ -n "$VERSION" ] || { err "could not read project version from build.gradle.kts"; exit 2; }
TESTER_JAR="$ROOT/se/tester/build-legacy/libs/StarEnchants-Tester-${VERSION}-legacy.jar"
[ -f "$TESTER_JAR" ] || { err "downgraded tester jar not found: ${TESTER_JAR#$ROOT/} (drop SE_NO_BUILD so this builds it)"; exit 2; }
log "tester jar: ${TESTER_JAR#$ROOT/}  (the downgraded v52 legacy tester)"

# ── 2. Stage a fresh 1.8.8 server ─────────────────────────────────────────
SRV="$WORK/smoke-server"
rm -rf "$SRV"; mkdir -p "$SRV/plugins"
cp "$CB_JAR" "$SRV/server.jar"
cp "$TESTER_JAR" "$SRV/plugins/StarEnchantsTester.jar"
echo "eula=true" > "$SRV/eula.txt"
# 1.8 server.properties: peaceful (difficulty 0) sanitized arena so a stray mob can't read as a phantom
# proc; FLAT so spawn is open; spawn-protection 0 so the fake player can act. spawn-monsters off, but
# explicit world.spawnEntity(COW) for the combat victim is unaffected (it is not a natural spawn).
cat > "$SRV/server.properties" <<EOF
online-mode=false
level-type=FLAT
difficulty=0
spawn-protection=0
spawn-monsters=false
generate-structures=false
allow-nether=false
allow-end=false
max-players=4
view-distance=4
server-port=25710
EOF

# ── 3. Boot under JDK 8; the harness self-shuts-down once results are written ──
log "booting craftbukkit 1.8.8 under $(${ARCH_PREFIX[@]+"${ARCH_PREFIX[@]}"} "$J8" -version 2>&1 | head -1) ..."
started="$(date +%s)"
# Feed nothing on stdin; the in-server Harness calls server.shutdown() itself after writing results. The
# watchdog below is the fallback if it never gets that far.
( cd "$SRV" && ${ARCH_PREFIX[@]+"${ARCH_PREFIX[@]}"} "$J8" -Xmx1024M -Ddisable.watchdog=true \
    -jar server.jar nogui > "$SRV/boot.log" 2>&1 ) &
pid=$!

waited=0
while kill -0 "$pid" 2>/dev/null; do
  if [ "$waited" -ge "$WATCHDOG_SECS" ]; then
    warn "watchdog (${WATCHDOG_SECS}s) tripped — killing the server"
    pkill -P "$pid" 2>/dev/null; kill -9 "$pid" 2>/dev/null
    break
  fi
  sleep 2; waited=$((waited + 2))
done
wait "$pid" 2>/dev/null

# ── 4. Read the result HONESTLY (must exist AND be fresh) ──────────────────
RESULT="$SRV/test-results.txt"
verdict="FAIL"
if [ -f "$RESULT" ]; then
  # GNU stat (-c, Linux/CI) FIRST, BSD stat (-f, macOS) second: on Linux `stat -f` is --file-system (prints
  # filesystem info and EXITS 0), so a BSD-first order never falls through to the GNU form on a CI runner.
  mtime="$(stat -c %Y "$RESULT" 2>/dev/null || stat -f %m "$RESULT" 2>/dev/null)"
  if [ -n "$mtime" ] && [ "$mtime" -ge "$started" ]; then
    head -1 "$RESULT" | grep -qx "PASS" && verdict="PASS"
  else
    warn "test-results.txt is STALE (server likely failed to boot/enable) — see $SRV/boot.log"
  fi
else
  warn "no test-results.txt — server failed to boot, never enabled, or never finished — see $SRV/boot.log"
fi

echo "" >&2
if [ "$verdict" = "PASS" ]; then
  pass "1.8.9 reduced smoke — $(grep -c ': PASS' "$RESULT" 2>/dev/null) check(s) green"
  [ -f "$RESULT" ] && sed 's/^/      /' "$RESULT" >&2
  [ "$KEEP_RUNDIR" != "1" ] && rm -rf "$SRV"
  printf '\n%sLEGACY SMOKE PASS%s\n' "$C_GREEN$C_BOLD" "$C_RESET"
  exit 0
else
  err "1.8.9 reduced smoke did not fresh-PASS"
  [ -f "$SRV/test-failures.txt" ] && { warn "failures:"; sed 's/^/      /' "$SRV/test-failures.txt" >&2; }
  [ ! -f "$RESULT" ] && { warn "boot.log tail:"; tail -30 "$SRV/boot.log" 2>/dev/null | sed 's/^/      /' >&2; }
  printf '\n%sLEGACY SMOKE FAIL%s — inspect %s\n' "$C_RED$C_BOLD" "$C_RESET" "$SRV/boot.log"
  exit 1
fi
