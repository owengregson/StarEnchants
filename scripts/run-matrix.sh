#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────
#  run-matrix.sh — the LIVE Paper + Folia integration gate (matrix-gate skill)
# ─────────────────────────────────────────────────────────────────────────
#  Boots a real, cached server per (platform, version), installs the StarEnchants
#  tester jar, lets the in-server harness run its suites and write a FRESH
#  test-results.txt (PASS/FAIL), then reads that result HONESTLY: a server that
#  failed to boot leaves a stale/missing result, never a green banner.
#
#  Usage:
#    scripts/run-matrix.sh paper:1.20.6 folia:1.20.6     # explicit targets
#    scripts/run-matrix.sh --paper 1.20.6 --folia 1.20.6 # same, flag form
#    scripts/run-matrix.sh --all                         # the full matrix from gradle.properties
#
#  Each target is `platform:version` where platform ∈ {paper, folia}. The server
#  jars come from the gitignored reference cache (reference-cache skill); run
#  `scripts/setup-dev.sh --with-reference` first if a version is missing.
#
#  Targeted by design: pass only the versions whose code path you changed. The
#  scheduler/Capabilities path, for instance, only needs ONE Paper + ONE Folia.
#
#  Env:
#    SE_NO_BUILD=1      skip the ./gradlew build (use ONLY right after a fresh build;
#                       otherwise the matrix may test a STALE tester fat jar). Same as --no-build.
#    SE_WATCHDOG_SECS   per-server hard timeout (default 180)
#    SE_KEEP_RUNDIR=1   keep the per-server run dir for inspection (default: clean)
#    SE_BASE_PORT       first server port (default 25700; +1 per target)
# ─────────────────────────────────────────────────────────────────────────
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

WATCHDOG_SECS="${SE_WATCHDOG_SECS:-180}"
BASE_PORT="${SE_BASE_PORT:-25700}"
KEEP_RUNDIR="${SE_KEEP_RUNDIR:-0}"
NO_BUILD="${SE_NO_BUILD:-0}"
FLIP="$(grep -E '^se.toolchain.flip=' gradle.properties 2>/dev/null | cut -d= -f2)"
FLIP="${FLIP:-1.20.5}"

# ── Logging ──────────────────────────────────────────────────────────────────
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  C_RESET=$'\033[0m'; C_DIM=$'\033[2m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'
  C_RED=$'\033[31m'; C_BOLD=$'\033[1m'; C_CYAN=$'\033[36m'
else
  C_RESET=''; C_DIM=''; C_GREEN=''; C_YELLOW=''; C_RED=''; C_BOLD=''; C_CYAN=''
fi
# All human-facing logs go to STDERR so a function's STDOUT is free to carry only its
# return value (the verdict) — but here we set a global instead, so logging to stderr
# also just keeps the terminal output clean and ordered.
ts()   { date '+%H:%M:%S'; }
log()  { printf '%s[%s]%s %s\n' "$C_DIM" "$(ts)" "$C_RESET" "$*" >&2; }
pass() { printf '%s[%s] PASS%s %s\n' "$C_GREEN" "$(ts)" "$C_RESET" "$*" >&2; }
warn() { printf '%s[%s] WARN%s %s\n' "$C_YELLOW" "$(ts)" "$C_RESET" "$*" >&2; }
err()  { printf '%s[%s] FAIL%s %s\n' "$C_RED" "$(ts)" "$C_RESET" "$*" >&2; }
hdr()  { printf '\n%s── %s ──%s\n' "$C_BOLD$C_CYAN" "$*" "$C_RESET"; }

# ── Parse targets ────────────────────────────────────────────────────────────
declare -a TARGETS=()
read_csv_prop() { grep -E "^$1=" gradle.properties 2>/dev/null | cut -d= -f2; }
add_all() {
  local v
  for v in ${1//,/ }; do TARGETS+=("$2:$v"); done
}
while [ $# -gt 0 ]; do
  case "$1" in
    --all)    add_all "$(read_csv_prop se.matrix.paper)" paper
              add_all "$(read_csv_prop se.matrix.folia)" folia ;;
    --paper)  shift; TARGETS+=("paper:$1") ;;
    --folia)  shift; TARGETS+=("folia:$1") ;;
    --no-build) NO_BUILD=1 ;;
    paper:*|folia:*) TARGETS+=("$1") ;;
    -h|--help) sed -n '2,/^set /p' "$0" | sed '$d'; exit 0 ;;
    *) err "unknown argument: $1 (try --help)"; exit 2 ;;
  esac
  shift
done
if [ "${#TARGETS[@]}" -eq 0 ]; then
  err "no targets — e.g. scripts/run-matrix.sh paper:1.20.6 folia:1.20.6  (or --all)"
  exit 2
fi

# ── Build first — rebundle the tester fat jar (matrix-stale-jar-trap) ─────────
# The tester jar is a FAT jar bundling engine/platform/item/feature/compile/schema. `find`-ing an
# existing jar without rebuilding silently tests OLD code → a false PASS/FAIL. So rebuild by default:
# `./gradlew build` recompiles, runs the unit gate, and re-runs :tester:jar (rebundling the fat jar).
# Set SE_NO_BUILD=1 / pass --no-build ONLY when you have just built in this session (to save time).
if [ "$NO_BUILD" = "1" ]; then
  warn "SE_NO_BUILD set — skipping ./gradlew build; the tester jar may be STALE (stale-jar trap)"
else
  log "building (./gradlew build) — rebundles the tester fat jar so the matrix tests fresh code"
  if ! ./gradlew build; then
    err "gradle build failed — refusing to boot any server against a stale/broken jar"
    exit 1
  fi
fi

# ── Locate the tester jar (built by gradle, above) ───────────────────────────
TESTER_JAR="$(find "$REPO_ROOT/se/tester/build/libs" -name 'tester-*.jar' ! -name '*-sources.jar' 2>/dev/null | head -1)"
if [ -z "$TESTER_JAR" ]; then
  err "tester jar not found — run ./gradlew :tester:jar (or drop --no-build / unset SE_NO_BUILD so this script builds it)"
  exit 2
fi
log "tester jar: ${TESTER_JAR#$REPO_ROOT/}"

# ── JDK selection (server runtime only; the plugin is always Java 17 classes) ──
ver_le_flip() { # returns 0 if version $1 is strictly BELOW the flip
  printf '%s\n%s\n' "$1" "$FLIP" | sort -t. -k1,1n -k2,2n -k3,3n | head -1 | grep -qx "$1" \
    && [ "$1" != "$FLIP" ]
}
jdk_home_for() { # echoes a JDK home appropriate for server version $1
  local v="$1" want=21
  if ver_le_flip "$v"; then want=17; fi
  local home=""
  if [ -x /usr/libexec/java_home ]; then home="$(/usr/libexec/java_home -v "$want" 2>/dev/null)"; fi
  if [ -z "$home" ]; then
    local envvar="JAVA${want}_HOME"; home="${!envvar:-}"
  fi
  [ -z "$home" ] && home="$(command -v java >/dev/null 2>&1 && dirname "$(dirname "$(command -v java)")")"
  echo "$home"
}

# ── Per-server runner ────────────────────────────────────────────────────────
# Sets globals RUN_VERDICT (PASS/FAIL) and RUN_LOG; never uses stdout for the result
# (so the caller reads RUN_VERDICT, not command substitution that would swallow logs).
run_one() { # run_one <platform> <version> <port>
  local platform="$1" version="$2" port="$3"
  RUN_VERDICT="FAIL"; RUN_LOG=""
  local cache="$REPO_ROOT/reference/servers/$platform/$version"
  local paperclip
  paperclip="$(find "$cache" -maxdepth 1 -name "$platform-$version.jar" 2>/dev/null | head -1)"
  if [ -z "$paperclip" ]; then
    err "$platform:$version — no cached server jar under ${cache#$REPO_ROOT/} (run setup-dev.sh --with-reference)"
    return
  fi
  local jdk; jdk="$(jdk_home_for "$version")"
  if [ -z "$jdk" ] || [ ! -x "$jdk/bin/java" ]; then
    err "$platform:$version — no suitable JDK found"
    return
  fi

  local run; run="$(mktemp -d "/tmp/se-matrix.$platform-$version.XXXXXX")"
  RUN_LOG="$run/boot.log"
  # Symlink the immutable cached artifacts; only mutable state lands in the run dir. The modern
  # paperclip extracts versions/ + libraries/; the old 1.17-era paperclip uses only cache/ — so
  # link whichever dirs this version actually has (a dangling link would confuse paperclip).
  local d
  for d in versions libraries cache; do
    [ -d "$cache/$d" ] && ln -s "$cache/$d" "$run/$d"
  done
  cp "$paperclip" "$run/paperclip.jar"
  mkdir -p "$run/plugins"
  cp "$TESTER_JAR" "$run/plugins/"
  echo "eula=true" > "$run/eula.txt"
  cat > "$run/server.properties" <<EOF
online-mode=false
level-type=flat
spawn-protection=0
difficulty=peaceful
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

  local started; started="$(date +%s)"
  log "$platform:$version — booting on $(basename "$jdk") port $port  ($run)"
  ( cd "$run" && caffeinate -i "$jdk/bin/java" -Xms512M -Xmx1280M \
      -Ddisable.watchdog=true -Dcom.mojang.eula.agree=true \
      -jar paperclip.jar --nogui > boot.log 2>&1 ) &
  local pid=$!

  # Poll for exit or watchdog deadline (no `timeout` binary on macOS).
  local waited=0
  while kill -0 "$pid" 2>/dev/null; do
    if [ "$waited" -ge "$WATCHDOG_SECS" ]; then
      warn "$platform:$version — watchdog ($WATCHDOG_SECS s) tripped; killing"
      pkill -P "$pid" 2>/dev/null; kill -9 "$pid" 2>/dev/null
      pkill -f "$run/paperclip.jar" 2>/dev/null
      break
    fi
    sleep 2; waited=$((waited + 2))
  done
  wait "$pid" 2>/dev/null

  # Read the result HONESTLY: it must exist AND be fresh (written during this run).
  local result="$run/test-results.txt"
  local verdict="FAIL"
  if [ -f "$result" ]; then
    local mtime; mtime="$(stat -f %m "$result" 2>/dev/null || stat -c %Y "$result" 2>/dev/null)"
    if [ -n "$mtime" ] && [ "$mtime" -ge "$started" ]; then
      if head -1 "$result" | grep -qx "PASS"; then verdict="PASS"; fi
    else
      warn "$platform:$version — result file is STALE (server likely failed to boot)"
    fi
  else
    warn "$platform:$version — no test-results.txt (server failed to boot or never finished)"
  fi

  RUN_VERDICT="$verdict"
  if [ "$verdict" = "PASS" ]; then
    pass "$platform:$version  ($(grep -c ': PASS' "$result" 2>/dev/null) checks)"
  else
    err "$platform:$version — see $RUN_LOG"
    [ -f "$run/test-failures.txt" ] && { warn "failures:"; sed 's/^/      /' "$run/test-failures.txt" >&2; }
    [ ! -f "$result" ] && tail -25 "$RUN_LOG" | sed 's/^/      /' >&2
  fi

  if [ "$verdict" = "PASS" ] && [ "$KEEP_RUNDIR" != "1" ]; then rm -rf "$run"; fi
}

# ── Drive the matrix ─────────────────────────────────────────────────────────
hdr "StarEnchants live matrix — ${#TARGETS[@]} target(s)"
declare -a SUMMARY=()
overall=0
idx=0
for target in "${TARGETS[@]}"; do
  platform="${target%%:*}"; version="${target##*:}"
  port=$((BASE_PORT + idx)); idx=$((idx + 1))
  run_one "$platform" "$version" "$port"
  SUMMARY+=("$RUN_VERDICT|$target")
  [ "$RUN_VERDICT" = "PASS" ] || overall=1
done

hdr "Matrix summary"
for line in "${SUMMARY[@]}"; do
  v="${line%%|*}"; t="${line##*|}"
  if [ "$v" = "PASS" ]; then printf '  %sPASS%s  %s\n' "$C_GREEN" "$C_RESET" "$t"
  else printf '  %sFAIL%s  %s\n' "$C_RED" "$C_RESET" "$t"; fi
done
if [ "$overall" -eq 0 ]; then
  printf '\n%sMATRIX PASS%s — all %d target(s) fresh-PASS\n' "$C_GREEN$C_BOLD" "$C_RESET" "${#TARGETS[@]}"
else
  printf '\n%sMATRIX FAIL%s — at least one target did not fresh-PASS\n' "$C_RED$C_BOLD" "$C_RESET"
fi
# Honesty: a build-skip means this verdict could be testing a STALE jar — say so next to the banner
# so an inherited SE_NO_BUILD (or a forgotten --no-build) can never quietly defeat the trap this guards.
if [ "$NO_BUILD" = "1" ]; then
  printf '%s  ⚠ build was SKIPPED (SE_NO_BUILD/--no-build) — verdict assumes the tester jar is already fresh%s\n' "$C_YELLOW" "$C_RESET"
fi
exit "$overall"
