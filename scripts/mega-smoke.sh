#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────
#  mega-smoke.sh — prove the SINGLE mega-jar enables on BOTH eras
# ─────────────────────────────────────────────────────────────────────────
#  Builds the Multi-Release mega-jar (scripts/build-mega-jar.sh) and boots the
#  SAME artifact on:
#    • a real craftbukkit-1.8.x server under JDK 8   → must load the base v52 tree
#    • real Paper reference servers under JDK 17/21+  → must load versions/17 v61
#  and asserts the plugin ENABLES on each (no enable error, no NoClassDefFoundError,
#  no UnsupportedClassVersionError, no v1_8_R3 leak on modern). It feeds `se` + `stop`
#  on the console so the /se registration — which flows through the era-forked
#  bootstrap.compat.Commands seam — is exercised, not just class-loaded.
#
#  This is the mega-jar analogue of run-matrix.sh (modern) + legacy-smoke.sh (1.8).
#  It is a LOAD/ENABLE smoke, not the full in-server suite: those two gates own the
#  behavioural coverage; this one owns "the merged artifact selects the right tree".
#
#  Usage:
#    scripts/mega-smoke.sh                       # defaults: legacy 1.8.8 + paper 1.17.1,26.1.2
#    scripts/mega-smoke.sh paper:1.20.6 paper:26.1.2 legacy:1.8.8
#    SE_NO_BUILD=1 scripts/mega-smoke.sh         # reuse an already-built mega-jar
#
#  Env:  SE_LEGACY_WORK (default ~/se-legacy-buildtools), SE_WATCHDOG_SECS (default 150),
#        SE_BASE_PORT (default 25730), SE_KEEP_RUNDIR (default 0 → clean on PASS).
# ─────────────────────────────────────────────────────────────────────────
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$ROOT"

WORK="${SE_LEGACY_WORK:-$HOME/se-legacy-buildtools}"
WATCHDOG_SECS="${SE_WATCHDOG_SECS:-150}"
BASE_PORT="${SE_BASE_PORT:-25730}"
KEEP_RUNDIR="${SE_KEEP_RUNDIR:-0}"
NO_BUILD="${SE_NO_BUILD:-0}"
FLIP="$(grep -E '^se.toolchain.flip=' gradle.properties 2>/dev/null | cut -d= -f2)"; FLIP="${FLIP:-1.20.5}"

if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  C_RESET=$'\033[0m'; C_DIM=$'\033[2m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_RED=$'\033[31m'; C_BOLD=$'\033[1m'
else C_RESET=''; C_DIM=''; C_GREEN=''; C_YELLOW=''; C_RED=''; C_BOLD=''; fi
log()  { printf '%s[mega-smoke]%s %s\n' "$C_DIM" "$C_RESET" "$*" >&2; }
pass() { printf '%s[mega-smoke] PASS%s %s\n' "$C_GREEN" "$C_RESET" "$*" >&2; }
warn() { printf '%s[mega-smoke] WARN%s %s\n' "$C_YELLOW" "$C_RESET" "$*" >&2; }
err()  { printf '%s[mega-smoke] FAIL%s %s\n' "$C_RED" "$C_RESET" "$*" >&2; }

TARGETS=("$@"); [ "${#TARGETS[@]}" -eq 0 ] && TARGETS=(legacy:1.8.8 paper:1.17.1 paper:26.1.2)

# ── 1. Build (or reuse) the mega-jar ──────────────────────────────────────
if [ "$NO_BUILD" = "1" ]; then
  scripts/build-mega-jar.sh --no-build >&2 || { err "mega-jar (--no-build) failed"; exit 1; }
else
  scripts/build-mega-jar.sh >&2 || { err "mega-jar build failed"; exit 1; }
fi
VERSION="$(grep -E '^\s*version = "' build.gradle.kts | head -1 | sed -E 's/.*version = "(.*)".*/\1/')"
MEGA="$ROOT/se/bootstrap/build/libs/StarEnchants-${VERSION}-mega.jar"
[ -f "$MEGA" ] || { err "mega-jar not found: $MEGA"; exit 2; }
log "mega-jar: ${MEGA#$ROOT/}  ($(ls -lh "$MEGA" | awk '{print $5}'))"

# ── JDK pickers ───────────────────────────────────────────────────────────
ver_lt_flip() { printf '%s\n%s\n' "$1" "$FLIP" | sort -t. -k1,1n -k2,2n -k3,3n | head -1 | grep -qx "$1" && [ "$1" != "$FLIP" ]; }
jdk_modern()  { local v="$1" want=21; ver_lt_flip "$v" && want=17
  local h=""
  [ -x /usr/libexec/java_home ] && h="$(/usr/libexec/java_home -v "$want" 2>/dev/null)"        # macOS, exact
  if [ -z "$h" ]; then local envvar="JAVA${want}_HOME"; h="${!envvar:-}"; fi                    # CI: JAVA17_HOME / JAVA21_HOME
  [ -z "$h" ] && [ -x /usr/libexec/java_home ] && h="$(/usr/libexec/java_home 2>/dev/null)"     # macOS, highest available
  [ -z "$h" ] && command -v java >/dev/null 2>&1 && h="$(dirname "$(dirname "$(command -v java)")")"  # PATH java
  echo "$h"; }

# ── Per-target runner ─────────────────────────────────────────────────────
# Sets RUN_VERDICT; never writes the verdict to stdout (logs go to stderr).
run_one() { # run_one <platform> <version> <port>
  local platform="$1" version="$2" port="$3"; RUN_VERDICT="FAIL"
  local run jdk srv_jar arch=()
  run="$(mktemp -d "/tmp/se-mega.$platform-$version.XXXXXX")"; mkdir -p "$run/plugins"
  cp "$MEGA" "$run/plugins/StarEnchants.jar"
  echo "eula=true" > "$run/eula.txt"

  if [ "$platform" = "legacy" ]; then
    srv_jar="$WORK/craftbukkit-$version.jar"; jdk="$WORK/jdk8"
    [ -f "$srv_jar" ] || { err "$platform:$version — craftbukkit jar missing ($srv_jar)"; return; }
    [ -x "$jdk/bin/java" ] || { err "$platform:$version — JDK 8 missing ($jdk)"; return; }
    [ "$(uname -s)" = "Darwin" ] && [ "$(uname -m)" = "arm64" ] && arch=(arch -x86_64)
    cp "$srv_jar" "$run/server.jar"
    cat > "$run/server.properties" <<EOF
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
server-port=$port
EOF
  else
    local cache="$ROOT/reference/servers/$platform/$version"
    srv_jar="$(find "$cache" -maxdepth 1 -name "$platform-$version.jar" 2>/dev/null | head -1)"
    [ -n "$srv_jar" ] || { err "$platform:$version — no cached server jar under ${cache#$ROOT/}"; return; }
    jdk="$(jdk_modern "$version")"
    [ -n "$jdk" ] && [ -x "$jdk/bin/java" ] || { err "$platform:$version — no suitable JDK"; return; }
    local d; for d in versions libraries cache; do [ -d "$cache/$d" ] && ln -s "$cache/$d" "$run/$d"; done
    cp "$srv_jar" "$run/paperclip.jar"
    cat > "$run/server.properties" <<EOF
online-mode=false
level-type=flat
difficulty=peaceful
spawn-protection=0
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
generate-structures=false
allow-nether=false
allow-end=false
max-players=5
view-distance=4
simulation-distance=4
server-port=$port
EOF
  fi

  local jar_args; [ "$platform" = "legacy" ] && jar_args=(-jar server.jar nogui) || jar_args=(-jar paperclip.jar --nogui)
  log "$platform:$version — booting on $(basename "$jdk") (port $port)  $run"
  # Feed `se` (exercise the era-forked /se registration) then `stop` after the server settles. A unique
  # -Dse.smoke.id marker lets the watchdog reap the actual java process: it is a GRANDCHILD (under caffeinate,
  # and under `arch` on arm64), so pkill -P on $pid misses it — pkill -f on the marker catches it by cmdline.
  ( cd "$run" && { sleep 55; printf 'se\nstop\n'; sleep 20; } | caffeinate -i ${arch[@]+"${arch[@]}"} "$jdk/bin/java" \
      -Xms512M -Xmx1280M -Ddisable.watchdog=true -Dcom.mojang.eula.agree=true -Dse.smoke.id="$port" "${jar_args[@]}" > boot.log 2>&1 ) &
  local pid=$! waited=0
  while kill -0 "$pid" 2>/dev/null; do
    [ "$waited" -ge "$WATCHDOG_SECS" ] && { warn "$platform:$version — watchdog (${WATCHDOG_SECS}s) — killing"; pkill -P "$pid" 2>/dev/null; pkill -f "se.smoke.id=$port" 2>/dev/null; kill -9 "$pid" 2>/dev/null; break; }
    sleep 3; waited=$((waited + 3))
  done; wait "$pid" 2>/dev/null

  # ── Verdict from boot.log: enabled cleanly, no class-selection failure ───
  local lg="$run/boot.log"
  local booted enabled bad_enable ncdfe ucve v18leak
  booted="$(grep -c 'Done (' "$lg" 2>/dev/null || true)"
  enabled="$(grep -c 'StarEnchants — ' "$lg" 2>/dev/null || true)"          # onEnable marker (line 152)
  bad_enable="$(grep -c 'Error occurred while enabling StarEnchants' "$lg" 2>/dev/null || true)"
  ncdfe="$(grep -c 'NoClassDefFoundError' "$lg" 2>/dev/null || true)"
  ucve="$(grep -c 'UnsupportedClassVersionError' "$lg" 2>/dev/null || true)"
  v18leak="$(grep -c 'v1_8_R3' "$lg" 2>/dev/null || true)"
  local verdict="FAIL" why=""
  if [ "${booted:-0}" -ge 1 ] && [ "${enabled:-0}" -ge 1 ] && [ "${bad_enable:-0}" -eq 0 ] && [ "${ncdfe:-0}" -eq 0 ] && [ "${ucve:-0}" -eq 0 ]; then
    if [ "$platform" != "legacy" ] && [ "${v18leak:-0}" -ne 0 ]; then verdict="FAIL"; why="v1_8_R3 referenced on modern (base v52 leaked!)"
    else verdict="PASS"; fi
  else
    why="booted=$booted enable=$enabled enableErr=$bad_enable NCDFE=$ncdfe UCVE=$ucve"
  fi
  RUN_VERDICT="$verdict"
  if [ "$verdict" = "PASS" ]; then
    local caps; caps="$(grep -m1 'StarEnchants — ' "$lg" | sed 's/.*StarEnchants — //')"
    pass "$platform:$version — enabled cleanly  [${caps:0:70}]"
    [ "$KEEP_RUNDIR" != "1" ] && rm -rf "$run"
  else
    err "$platform:$version — $why  (see $lg)"
    grep -nE 'Error occurred while enabling|NoClassDefFoundError|UnsupportedClassVersionError|v1_8_R3|Caused by' "$lg" 2>/dev/null | head -8 | sed 's/^/      /' >&2
  fi
}

# ── Drive ─────────────────────────────────────────────────────────────────
printf '\n%s── mega-jar live smoke — %d target(s) ──%s\n' "$C_BOLD" "${#TARGETS[@]}" "$C_RESET" >&2
declare -a SUMMARY=(); overall=0; idx=0
for t in "${TARGETS[@]}"; do
  platform="${t%%:*}"; version="${t##*:}"; port=$((BASE_PORT + idx)); idx=$((idx + 1))
  run_one "$platform" "$version" "$port"
  SUMMARY+=("$RUN_VERDICT|$t"); [ "$RUN_VERDICT" = "PASS" ] || overall=1
done

printf '\n%s── summary ──%s\n' "$C_BOLD" "$C_RESET"
for line in "${SUMMARY[@]}"; do v="${line%%|*}"; t="${line##*|}"
  if [ "$v" = "PASS" ]; then printf '  %sPASS%s  %s\n' "$C_GREEN" "$C_RESET" "$t"; else printf '  %sFAIL%s  %s\n' "$C_RED" "$C_RESET" "$t"; fi
done
if [ "$overall" -eq 0 ]; then printf '\n%sMEGA SMOKE PASS%s — one jar, both eras\n' "$C_GREEN$C_BOLD" "$C_RESET"
else printf '\n%sMEGA SMOKE FAIL%s\n' "$C_RED$C_BOLD" "$C_RESET"; fi
exit "$overall"
