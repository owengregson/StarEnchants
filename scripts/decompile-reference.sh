#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────
#  decompile-reference.sh — readable source for the cached server jars
# ─────────────────────────────────────────────────────────────────────────
#  Turns each cached Paper/Folia server jar in reference/servers/<platform>/
#  <version>/ into a browsable, mojang-mapped Java source tree under
#  reference/decompiled/<platform>/<version>/ — for grep/IDE archaeology of
#  version-specific behavior (see the nms-archaeology + reference-cache skills).
#
#  Which jar: we decompile the FULL fat server jar that actually carries the
#  packages we read — net.minecraft + org.bukkit(.craftbukkit) + io.papermc +
#  Folia internals. On modern Paper/Folia that is the paperclip-extracted bundle
#  at versions/<ver>/<platform>-<ver>.jar (mojang-mapped net.minecraft, real
#  craftbukkit/paper names). On the old 1.17.1 paperclip it is cache/
#  patched_<ver>.jar. We deliberately do NOT use cache/mojang_<ver>.jar: that is
#  a Mojang BUNDLER jar — its root holds only ~4 bootstrap classes and the real
#  vanilla server is nested at META-INF/versions/<ver>/server-<ver>.jar, with no
#  bukkit/paper packages at all. (We unpack that nested jar only as a last-resort
#  fallback — net.minecraft only, no bukkit.)
#
#  Output (reference/decompiled/) is gitignored: large + third-party. Only this
#  script is committed; populate locally, like scripts/fetch-reference.sh.
#
#  Usage:
#    scripts/decompile-reference.sh                 # every cached version
#    scripts/decompile-reference.sh 1.21.4          # one version, both platforms
#    scripts/decompile-reference.sh 1.21.4 paper    # one version, one platform
#    scripts/decompile-reference.sh all folia       # all versions of one platform
#    FORCE=1 scripts/decompile-reference.sh 1.17.1  # re-decompile even if present
#
#  Env:
#    FORCE=1     re-decompile versions whose output already exists
#    XMX=6g      JVM heap for Vineflower (default 6g)
#    KEEP_TEMP=1 keep the filtered .class staging dir (debug)
# ─────────────────────────────────────────────────────────────────────────
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVERS_DIR="$REPO_ROOT/reference/servers"
OUT_ROOT="$REPO_ROOT/reference/decompiled"
VINEFLOWER="$REPO_ROOT/deobf/vineflower.jar"

WANT_VERSION="${1:-all}"
WANT_PLATFORM="${2:-both}"
XMX="${XMX:-6g}"

# Packages worth reading for plugin/version work. Vanilla internals + the
# CraftBukkit/Paper implementation + Paper API additions. Shaded libraries
# (com.google, io.netty, org.apache, it.unimi, …) are dropped to keep the
# output focused and the decompile fast.
KEEP_PATTERNS=(
  'net/minecraft/*'
  'org/bukkit/*'
  'io/papermc/*'
  'com/destroystokyo/paper/*'
  'org/spigotmc/*'
  'ca/spottedleaf/*'
  'com/mojang/math/*'
  'com/mojang/brigadier/*'
)
# com/mojang/datafixers and com/mojang/serialization are intentionally excluded:
# huge, generated, and rarely useful for plugin/version archaeology.

log()  { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
warn() { printf '[%s] WARN: %s\n' "$(date '+%H:%M:%S')" "$*" >&2; }
die()  { printf '[%s] ERROR: %s\n' "$(date '+%H:%M:%S')" "$*" >&2; exit 1; }

[ -f "$VINEFLOWER" ] || die "Vineflower not found at $VINEFLOWER"
[ -d "$SERVERS_DIR" ] || die "No cached servers at $SERVERS_DIR — run scripts/fetch-reference.sh first"
command -v unzip >/dev/null || die "unzip is required"

# True if the jar actually contains the org/bukkit/craftbukkit implementation
# (i.e. it is the real fat server jar, not a Mojang bundler with only bootstrap
# classes). Tolerates unzip exit 11 ("nothing matched").
jar_has_craftbukkit() {
  local jar="$1"
  [ -f "$jar" ] || return 1
  unzip -l "$jar" 'org/bukkit/craftbukkit/*' >/dev/null 2>&1
}

# Resolve the server jar that actually carries the packages we read, in priority
# order — verifying each candidate really contains org/bukkit/craftbukkit:
#   1. versions/<ver>/<platform>-<ver>.jar  (modern paperclip fat bundle)
#   2. cache/patched_<ver>.jar              (old 1.17.1 paperclip)
#   3. unpack META-INF/versions/<ver>/server-<ver>.jar from cache/mojang_<ver>.jar
#      (Mojang BUNDLER fallback — net.minecraft only, NO bukkit)
# $1=vdir  $2=ver  $3=platform  $4=scratch dir (for unpacking the bundler)
# Echoes the resolved jar path, or "" if none found.
pick_jar() {
  local vdir="$1" ver="$2" platform="$3" scratch="$4" j

  # 1. modern fat bundle (preferred): versions/<ver>/<platform>-<ver>.jar
  j="$vdir/versions/$ver/$platform-$ver.jar"
  if jar_has_craftbukkit "$j"; then echo "$j"; return; fi
  # tolerate odd filenames: any jar under versions/ that has craftbukkit
  while IFS= read -r j; do
    if jar_has_craftbukkit "$j"; then echo "$j"; return; fi
  done < <(find "$vdir/versions" -maxdepth 2 -name '*.jar' 2>/dev/null)

  # 2. old paperclip: cache/patched_<ver>.jar
  j="$vdir/cache/patched_${ver}.jar"
  if jar_has_craftbukkit "$j"; then echo "$j"; return; fi

  # 3. last resort: unpack the vanilla server jar nested in the Mojang bundler.
  #    This has net/minecraft only (no bukkit/paper) — better than nothing.
  local bundler="$vdir/cache/mojang_${ver}.jar"
  if [ -f "$bundler" ]; then
    local nested="META-INF/versions/$ver/server-$ver.jar"
    if unzip -l "$bundler" "$nested" >/dev/null 2>&1; then
      mkdir -p "$scratch"
      if unzip -qq -o "$bundler" "$nested" -d "$scratch" >/dev/null 2>&1; then
        local out="$scratch/server-$ver.jar"
        mv -f "$scratch/$nested" "$out"
        echo "$out"; return
      fi
    fi
  fi

  echo ""
}

decompile_one() {
  local platform="$1" ver="$2"
  local vdir="$SERVERS_DIR/$platform/$ver"
  local dest="$OUT_ROOT/$platform/$ver"

  # Skip-early check before doing any extraction work.
  if [ -d "$dest" ] && [ -n "$(ls -A "$dest" 2>/dev/null)" ] && [ "${FORCE:-0}" != "1" ]; then
    log "$platform/$ver: already decompiled (FORCE=1 to redo) — skipping"
    return
  fi

  # Staging dir doubles as scratch space for unpacking the bundler fallback jar.
  local stage; stage="$(mktemp -d "${TMPDIR:-/tmp}/se-decompile.XXXXXX")"
  trap '[ "${KEEP_TEMP:-0}" = "1" ] || rm -rf "$stage"' RETURN

  local jar; jar="$(pick_jar "$vdir" "$ver" "$platform" "$stage/_bundler")"
  [ -n "$jar" ] || { warn "$platform/$ver: no server jar with target packages found — skipping"; return; }

  case "$jar" in
    *patched_*) warn "$platform/$ver: spigot-mapped jar — net.minecraft names will be obfuscated (no mojmap available)" ;;
    *_bundler/*) warn "$platform/$ver: falling back to Mojang bundler — net.minecraft only, NO org.bukkit/io.papermc" ;;
  esac

  # Extract the wanted packages into a dedicated subdir so the bundler scratch
  # jar (under $stage/_bundler) is never fed to Vineflower.
  local classdir="$stage/classes"; mkdir -p "$classdir"

  log "$platform/$ver: extracting relevant packages from $(basename "$jar")"
  # Tolerate "nothing matched a given pattern" (unzip exit 11) per pattern.
  unzip -qq -o "$jar" "${KEEP_PATTERNS[@]}" -d "$classdir" || true
  if [ -z "$(ls -A "$classdir" 2>/dev/null)" ]; then
    warn "$platform/$ver: no target packages found in jar — skipping"
    return
  fi

  rm -rf "$dest"; mkdir -p "$dest"
  local classes; classes="$(find "$classdir" -name '*.class' | wc -l | tr -d ' ')"
  log "$platform/$ver: decompiling $classes classes → reference/decompiled/$platform/$ver"

  # -e=<full jar> gives Vineflower full type context (cleaner generics) while
  # only the staged subset is emitted. --folder writes a mirrored source tree.
  if java -Xmx"$XMX" -jar "$VINEFLOWER" \
        --silent --folder \
        -e="$jar" \
        "$classdir" "$dest"; then
    local files; files="$(find "$dest" -name '*.java' | wc -l | tr -d ' ')"
    log "$platform/$ver: done — $files .java files"
  else
    warn "$platform/$ver: Vineflower exited non-zero (partial output may remain)"
  fi
}

# ── Enumerate work ──────────────────────────────────────────────────────────
platforms=()
case "$WANT_PLATFORM" in
  both) platforms=(paper folia) ;;
  paper|folia) platforms=("$WANT_PLATFORM") ;;
  *) die "platform must be paper|folia|both (got '$WANT_PLATFORM')" ;;
esac

log "Decompiling cached servers → reference/decompiled/  (version=$WANT_VERSION platform=$WANT_PLATFORM heap=$XMX)"
total=0
for platform in "${platforms[@]}"; do
  pdir="$SERVERS_DIR/$platform"
  [ -d "$pdir" ] || { warn "no cached $platform servers"; continue; }
  for vdir in "$pdir"/*/; do
    [ -d "$vdir" ] || continue
    ver="$(basename "$vdir")"
    if [ "$WANT_VERSION" != "all" ] && [ "$ver" != "$WANT_VERSION" ]; then continue; fi
    decompile_one "$platform" "$ver"
    total=$((total + 1))
  done
done

[ "$total" -gt 0 ] || die "no matching versions under $SERVERS_DIR"
log "All done. Browse: reference/decompiled/<platform>/<version>/  (grep -r '<symbol>' reference/decompiled)"
