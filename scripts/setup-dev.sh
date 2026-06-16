#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────
#  setup-dev.sh — one-command developer bootstrap for StarEnchants
# ─────────────────────────────────────────────────────────────────────────
#  Takes a fresh clone (or a fresh machine, or a coding agent) from zero to a
#  workable state. Idempotent, non-interactive, and safe to re-run: every step
#  detects whether it has already been done and skips it cleanly. Optional,
#  heavy, or network-bound steps are OFF by default and gated behind flags.
#
#  Order of operations (each step is skippable / degrades gracefully):
#    1. Verify prerequisites      git + a JDK >= 17; report the matrix toolchains
#    2. Git hooks                 scripts/setup-hooks.sh
#    3. Reference cache  (opt-in) scripts/fetch-reference.sh   (large download)
#    4. Decompile        (opt-in) scripts/decompile-reference.sh (large/slow)
#    5. Build                     ./gradlew build  (skips cleanly if no scaffold)
#
#  Exit status: non-zero ONLY on hard failures (missing required tool, failed
#  build). Skipped optional steps never fail the run.
#
#  Usage:
#    scripts/setup-dev.sh                       # prereqs + hooks + build
#    scripts/setup-dev.sh --with-reference      # also fetch the reference cache
#    scripts/setup-dev.sh --with-decompile      # also decompile (implies fetch)
#    scripts/setup-dev.sh --full                # everything (fetch + decompile)
#    scripts/setup-dev.sh --no-build            # skip the gradle build
#    scripts/setup-dev.sh --help                # this help
#
#  Env overrides:
#    SE_WITH_REFERENCE=1   same as --with-reference
#    SE_WITH_DECOMPILE=1   same as --with-decompile
#    SE_NO_BUILD=1         same as --no-build
#    NO_COLOR=1            disable ANSI colour in the log
# ─────────────────────────────────────────────────────────────────────────
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS_DIR="$REPO_ROOT/scripts"

# ── Options (env defaults, overridable by flags) ─────────────────────────────
WITH_REFERENCE="${SE_WITH_REFERENCE:-0}"
WITH_DECOMPILE="${SE_WITH_DECOMPILE:-0}"
NO_BUILD="${SE_NO_BUILD:-0}"

# ── Logging ──────────────────────────────────────────────────────────────────
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  C_RESET=$'\033[0m'; C_DIM=$'\033[2m'; C_GREEN=$'\033[32m'
  C_YELLOW=$'\033[33m'; C_RED=$'\033[31m'; C_BOLD=$'\033[1m'
else
  C_RESET=''; C_DIM=''; C_GREEN=''; C_YELLOW=''; C_RED=''; C_BOLD=''
fi

ts()   { date '+%H:%M:%S'; }
log()  { printf '%s[%s]%s %s\n'        "$C_DIM" "$(ts)" "$C_RESET" "$*"; }
ok()   { printf '%s[%s]%s %sOK%s    %s\n'   "$C_DIM" "$(ts)" "$C_RESET" "$C_GREEN" "$C_RESET" "$*"; }
skip() { printf '%s[%s]%s %sSKIP%s  %s\n'   "$C_DIM" "$(ts)" "$C_RESET" "$C_DIM"   "$C_RESET" "$*"; }
warn() { printf '%s[%s]%s %sWARN%s  %s\n'   "$C_DIM" "$(ts)" "$C_RESET" "$C_YELLOW" "$C_RESET" "$*" >&2; }
fail() { printf '%s[%s]%s %sERROR%s %s\n'   "$C_DIM" "$(ts)" "$C_RESET" "$C_RED"   "$C_RESET" "$*" >&2; }
step() { printf '\n%s[%s] ── %s ──%s\n'     "$C_BOLD" "$(ts)" "$*" "$C_RESET"; }

# ── Summary tracking ─────────────────────────────────────────────────────────
declare -a SUMMARY=()
record() { SUMMARY+=("$1"); }   # "STATUS|step|detail"
HARD_FAIL=0

usage() {
  cat <<'EOF'
setup-dev.sh — one-command developer bootstrap for StarEnchants

Takes a fresh clone to a workable state. Idempotent, non-interactive, safe to
re-run. Optional/heavy/network steps are OFF by default and gated behind flags.

Steps (each skippable, degrades gracefully):
  1. Verify prerequisites   git + a JDK >= 17; report matrix toolchains (17/21)
  2. Git hooks              scripts/setup-hooks.sh
  3. Reference cache (opt)  scripts/fetch-reference.sh   (large download)
  4. Decompile      (opt)   scripts/decompile-reference.sh (large/slow)
  5. Build                  ./gradlew build  (skips cleanly if no scaffold)

Usage:
  scripts/setup-dev.sh                 prereqs + hooks + build
  scripts/setup-dev.sh --with-reference    also fetch the reference cache
  scripts/setup-dev.sh --with-decompile    also decompile (implies fetch)
  scripts/setup-dev.sh --full              everything (fetch + decompile + build)
  scripts/setup-dev.sh --no-build          skip the gradle build
  scripts/setup-dev.sh --help              this help

Env overrides:
  SE_WITH_REFERENCE=1   same as --with-reference
  SE_WITH_DECOMPILE=1   same as --with-decompile
  SE_NO_BUILD=1         same as --no-build
  JAVA17_HOME, JAVA21_HOME   hint matrix-toolchain locations (non-macOS)
  NO_COLOR=1            disable ANSI colour in the log

Exit status: non-zero ONLY on hard failures (missing required tool, failed
build). Skipped optional steps never fail the run.
EOF
}

# ── Parse args ───────────────────────────────────────────────────────────────
for arg in "$@"; do
  case "$arg" in
    --with-reference)  WITH_REFERENCE=1 ;;
    --with-decompile)  WITH_DECOMPILE=1 ;;
    --full)            WITH_REFERENCE=1; WITH_DECOMPILE=1 ;;
    --no-build)        NO_BUILD=1 ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "unknown argument: $arg  (try --help)"
      exit 2
      ;;
  esac
done

# Decompiling needs the cache, so --with-decompile implies fetching it.
if [ "$WITH_DECOMPILE" = "1" ] && [ "$WITH_REFERENCE" != "1" ]; then
  WITH_REFERENCE=1
  log "--with-decompile implies fetching the reference cache"
fi

cd "$REPO_ROOT"
log "StarEnchants dev bootstrap — repo: $REPO_ROOT"

# ─────────────────────────────────────────────────────────────────────────
# Step 1 — Prerequisites
# ─────────────────────────────────────────────────────────────────────────
step "1/5  Prerequisites"

# git (required)
if command -v git >/dev/null 2>&1; then
  ok "git present ($(git --version 2>&1 | awk '{print $3}'))"
  record "OK|git|$(git --version 2>&1 | awk '{print $3}')"
else
  fail "git is required but not found on PATH"
  record "FAIL|git|missing"
  HARD_FAIL=1
fi

# Parse a Java major version from a `java -version` first line. Handles the old
# "1.8.0_x" scheme and the modern "17.0.19" / "25.0.2" scheme. Portable awk
# (no GNU sed). Prints the major (e.g. 8, 17, 25) or nothing.
java_major_from() {  # java_major_from "<java -version first line>"
  printf '%s\n' "$1" | awk '
    match($0, /version "[0-9]+(\.[0-9]+)?/) {
      v = substr($0, RSTART + 9, RLENGTH - 9)   # strip leading version "
      n = split(v, p, ".")
      if (p[1] == 1 && n >= 2) print p[2]; else print p[1]
      exit
    }'
}

# Major version of the JDK installed at a given home (verifies java_home output).
java_major_at() {  # java_major_at <home> -> prints major or nothing
  local home="$1"
  [ -x "$home/bin/java" ] || return 1
  java_major_from "$("$home/bin/java" -version 2>&1 | head -n1)"
}

# A JDK >= 17 on PATH (required). `java -version` writes to stderr.
JAVA_OK=0
if command -v java >/dev/null 2>&1; then
  java_ver_raw="$(java -version 2>&1 | head -n1)"
  java_major="$(java_major_from "$java_ver_raw")"
  if [ -n "$java_major" ] && [ "$java_major" -ge 17 ] 2>/dev/null; then
    ok "default JDK $java_major on PATH ($java_ver_raw)"
    record "OK|jdk(default)|$java_major"
    JAVA_OK=1
  else
    fail "a JDK >= 17 is required; default java is '$java_ver_raw'"
    record "FAIL|jdk(default)|${java_major:-unknown}"
    HARD_FAIL=1
  fi
else
  fail "no 'java' on PATH; a JDK >= 17 is required"
  record "FAIL|jdk(default)|missing"
  HARD_FAIL=1
fi

# Matrix toolchains: JDK 17 (<=1.20.4) and JDK 21 (1.20.5+). Warn, don't fail.
# macOS: /usr/libexec/java_home -v <ver>; otherwise probe common env vars.
has_jdk() {  # has_jdk <major> -> prints home on EXACT-major match
  local want="$1" home=""
  # macOS: `java_home -v N` is a *minimum* match, so verify the real major.
  if [ -x /usr/libexec/java_home ]; then
    home="$(/usr/libexec/java_home -v "$want" 2>/dev/null)"
    if [ -n "$home" ] && [ "$(java_major_at "$home" 2>/dev/null)" = "$want" ]; then
      echo "$home"; return 0
    fi
  fi
  # Explicit env hints (JAVA17_HOME / JAVA21_HOME).
  local envvar="JAVA${want}_HOME"
  home="${!envvar:-}"
  if [ -n "$home" ] && [ "$(java_major_at "$home" 2>/dev/null)" = "$want" ]; then
    echo "$home"; return 0
  fi
  # Last resort: is the default java exactly this major?
  if command -v java >/dev/null 2>&1; then
    if [ "$(java_major_from "$(java -version 2>&1 | head -n1)")" = "$want" ]; then
      command -v java; return 0
    fi
  fi
  return 1
}

for tc in 17 21; do
  if home="$(has_jdk "$tc")"; then
    ok "matrix toolchain JDK $tc available"
    record "OK|toolchain-jdk$tc|$home"
  else
    warn "matrix toolchain JDK $tc not found — the version matrix needs 17 (<=1.20.4) and 21 (1.20.5+); CI uses 21/25. Install it before running the live matrix."
    record "WARN|toolchain-jdk$tc|missing"
  fi
done

# Gradle: prefer the wrapper, fall back to a global gradle.
GRADLE_CMD=""
if [ -x "$REPO_ROOT/gradlew" ]; then
  GRADLE_CMD="$REPO_ROOT/gradlew"
  ok "Gradle wrapper present (./gradlew)"
  record "OK|gradle|wrapper"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD="gradle"
  ok "global gradle present ($(gradle --version 2>/dev/null | awk '/^Gradle/{print $2; exit}'))"
  record "OK|gradle|global $(gradle --version 2>/dev/null | awk '/^Gradle/{print $2; exit}')"
else
  warn "no Gradle wrapper or global gradle found — fine until the build scaffold lands"
  record "WARN|gradle|missing"
fi

# Bail before doing real work if a required tool is missing.
if [ "$HARD_FAIL" = "1" ]; then
  fail "missing required tooling — aborting before any side effects"
  printf '\n%sSummary%s\n' "$C_BOLD" "$C_RESET"
  for line in "${SUMMARY[@]}"; do
    IFS='|' read -r s n d <<<"$line"
    printf '  %-5s %-22s %s\n' "$s" "$n" "$d"
  done
  exit 1
fi

# ─────────────────────────────────────────────────────────────────────────
# Step 2 — Git hooks
# ─────────────────────────────────────────────────────────────────────────
step "2/5  Git hooks"

current_hookspath="$(git config --get core.hooksPath 2>/dev/null || true)"
if [ "$current_hookspath" = ".githooks" ]; then
  skip "git hooks already enabled (core.hooksPath=.githooks)"
  record "SKIP|hooks|already enabled"
elif [ -x "$SCRIPTS_DIR/setup-hooks.sh" ]; then
  if bash "$SCRIPTS_DIR/setup-hooks.sh"; then
    ok "git hooks installed"
    record "OK|hooks|installed"
  else
    warn "setup-hooks.sh failed — commit-msg/pre-commit hooks not active"
    record "WARN|hooks|setup-hooks.sh failed"
  fi
else
  warn "scripts/setup-hooks.sh missing or not executable — skipping hooks"
  record "WARN|hooks|setup-hooks.sh missing"
fi

# ─────────────────────────────────────────────────────────────────────────
# Step 3 — Reference cache (opt-in; large download)
# ─────────────────────────────────────────────────────────────────────────
step "3/5  Reference cache (Paper/Folia jars + docs)"

if [ "$WITH_REFERENCE" != "1" ]; then
  skip "reference cache (pass --with-reference or --full to fetch; it is a large download)"
  record "SKIP|reference|not requested"
elif [ ! -x "$SCRIPTS_DIR/fetch-reference.sh" ]; then
  warn "scripts/fetch-reference.sh missing or not executable — cannot fetch reference"
  record "WARN|reference|fetch-reference.sh missing"
else
  # fetch-reference.sh takes no version args: it downloads the whole supported
  # range (skipping anything already cached) and is itself re-runnable.
  log "running scripts/fetch-reference.sh (downloads the full Paper+Folia range; cached versions are skipped)"
  if bash "$SCRIPTS_DIR/fetch-reference.sh"; then
    ok "reference cache populated under reference/servers + reference/docs"
    record "OK|reference|fetched"
  else
    warn "fetch-reference.sh exited non-zero — some versions may be missing (re-run later)"
    record "WARN|reference|partial fetch"
  fi
fi

# ─────────────────────────────────────────────────────────────────────────
# Step 4 — Decompile (opt-in; large/slow)
# ─────────────────────────────────────────────────────────────────────────
step "4/5  Decompile reference jars"

if [ "$WITH_DECOMPILE" != "1" ]; then
  skip "decompile (pass --with-decompile or --full; it is large and slow)"
  record "SKIP|decompile|not requested"
elif [ ! -x "$SCRIPTS_DIR/decompile-reference.sh" ]; then
  warn "scripts/decompile-reference.sh missing or not executable — cannot decompile"
  record "WARN|decompile|decompile-reference.sh missing"
elif [ ! -d "$REPO_ROOT/reference/servers" ] || [ -z "$(ls -A "$REPO_ROOT/reference/servers" 2>/dev/null)" ]; then
  warn "no cached server jars under reference/servers — run with --with-reference first; skipping decompile"
  record "WARN|decompile|no cached jars"
elif [ ! -f "$REPO_ROOT/deobf/vineflower.jar" ]; then
  warn "deobf/vineflower.jar not found — decompile-reference.sh needs it; skipping"
  record "WARN|decompile|vineflower.jar missing"
else
  log "running scripts/decompile-reference.sh (all cached versions; already-decompiled ones are skipped)"
  if bash "$SCRIPTS_DIR/decompile-reference.sh"; then
    ok "reference sources written under reference/decompiled"
    record "OK|decompile|done"
  else
    warn "decompile-reference.sh exited non-zero — output may be partial"
    record "WARN|decompile|partial"
  fi
fi

# ─────────────────────────────────────────────────────────────────────────
# Step 5 — Build
# ─────────────────────────────────────────────────────────────────────────
step "5/5  Build (./gradlew build)"

scaffold_present=0
if [ -f "$REPO_ROOT/settings.gradle.kts" ] || [ -f "$REPO_ROOT/settings.gradle" ] \
   || [ -f "$REPO_ROOT/build.gradle.kts" ] || [ -f "$REPO_ROOT/build.gradle" ]; then
  scaffold_present=1
fi

if [ "$NO_BUILD" = "1" ]; then
  skip "build (--no-build)"
  record "SKIP|build|--no-build"
elif [ "$scaffold_present" != "1" ]; then
  skip "build — Gradle scaffold not present yet (foundation phase); nothing to build"
  record "SKIP|build|no scaffold yet"
elif [ -z "$GRADLE_CMD" ]; then
  warn "Gradle scaffold present but no gradle/gradlew available — cannot build"
  record "WARN|build|no gradle"
else
  log "running: $GRADLE_CMD build"
  if "$GRADLE_CMD" build; then
    ok "build succeeded"
    record "OK|build|passed"
  else
    fail "build failed"
    record "FAIL|build|failed"
    HARD_FAIL=1
  fi
fi

# ─────────────────────────────────────────────────────────────────────────
# Summary
# ─────────────────────────────────────────────────────────────────────────
printf '\n%s═══ Bootstrap summary ═══%s\n' "$C_BOLD" "$C_RESET"
for line in "${SUMMARY[@]}"; do
  IFS='|' read -r s n d <<<"$line"
  case "$s" in
    OK)   colour="$C_GREEN" ;;
    WARN) colour="$C_YELLOW" ;;
    FAIL) colour="$C_RED" ;;
    *)    colour="$C_DIM" ;;
  esac
  printf '  %s%-5s%s %-22s %s%s%s\n' "$colour" "$s" "$C_RESET" "$n" "$C_DIM" "$d" "$C_RESET"
done

printf '\n'
if [ "$HARD_FAIL" = "1" ]; then
  fail "bootstrap finished with hard failures (see above)"
  exit 1
fi

# Friendly next-steps pointer.
log "next:"
[ "$WITH_REFERENCE" = "1" ] || log "   • fetch the reference cache for nms-archaeology:  scripts/setup-dev.sh --with-reference"
[ "$scaffold_present" = "1" ] || log "   • the Gradle build/run loop activates once the scaffold lands (see docs/development.md)"
log "   • day-to-day dev loop:  docs/development.md"
ok "bootstrap complete"
exit 0
