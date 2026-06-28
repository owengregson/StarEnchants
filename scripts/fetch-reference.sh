#!/usr/bin/env bash
# Download per-version Paper + Folia server jars into the local reference cache
# (reference/servers/, gitignored) and extract them for javap (nms-archaeology).
#
#   Paper/Folia jars are mojang-mapped from 1.20.5+ (javap directly); spigot-mapped
#   before (route names through a remapper). See the nms-archaeology skill.
#
# Uses the PaperMC Fill v3 API (has all versions incl. 26.x), falling back to the
# legacy v2 API. Re-runnable: skips already-downloaded + already-extracted jars.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/reference/servers"
UA="StarEnchants-dev (owen@owen.lol)"
V3="https://fill.papermc.io/v3/projects"
V2="https://api.papermc.io/v2/projects"

# Server-runtime JDK boundaries (mirrors run-matrix.sh / gradle.properties): a paperclip's patchonly extract
# runs the server's own bootstrap, so it must use a JDK the server accepts — 26.1+ refuses anything below 25,
# pre-flip versions want 17. Picking per version makes the extraction (and thus the cached server) reliable
# across the whole range instead of relying on whatever `java` happens to be on PATH.
FLIP="$(grep -E '^se.toolchain.flip=' "$ROOT/gradle.properties" 2>/dev/null | cut -d= -f2)"; FLIP="${FLIP:-1.20.5}"
FLIP25="$(grep -E '^se.toolchain.flip25=' "$ROOT/gradle.properties" 2>/dev/null | cut -d= -f2)"; FLIP25="${FLIP25:-26.1}"
ver_lt() { [ "$1" = "$2" ] && return 1; printf '%s\n%s\n' "$1" "$2" | sort -t. -k1,1n -k2,2n -k3,3n | head -1 | grep -qx "$1"; }
java_for() { # echo a java binary appropriate for server version $1 (17/21/25), else plain 'java'
  local v="$1" want=21
  ver_lt "$v" "$FLIP" && want=17; ver_lt "$v" "$FLIP25" || want=25
  local home=""
  [ -x /usr/libexec/java_home ] && home="$(/usr/libexec/java_home -v "$want" 2>/dev/null)"
  [ -z "$home" ] && { local envvar="JAVA${want}_HOME"; home="${!envvar:-}"; }
  if [ -n "$home" ] && [ -x "$home/bin/java" ]; then echo "$home/bin/java"; else echo java; fi
}

# Newest available is the 26.1 line (26.2 is still RC). The whole supported range:
PAPER_VERSIONS=(1.17.1 1.18.2 1.19.4 1.20.6 1.21.4 1.21.11 26.1.1 26.1.2)
FOLIA_VERSIONS=(1.19.4 1.20.6 1.21.4 1.21.11 26.1.2)

# Echoes "<url>|<name>" for the latest build, trying v3 then v2.
build_info() {
  local project="$1" version="$2" out
  out="$(curl -fsS --max-time 60 -H "User-Agent: $UA" "$V3/$project/versions/$version/builds" 2>/dev/null \
    | python3 -c "
import sys,json
d=json.load(sys.stdin)
b=max(d, key=lambda x: x['id'])
app=b['downloads']['server:default']
print(app['url']+'|'+app['name'])
" 2>/dev/null)" && [ -n "$out" ] && { echo "$out"; return 0; }

  curl -fsS --max-time 60 "$V2/$project/versions/$version/builds" 2>/dev/null \
    | python3 -c "
import sys,json
d=json.load(sys.stdin)['builds'][-1]
name=d['downloads']['application']['name']
print('$V2/$project/versions/$version/builds/%d/downloads/%s|%s'%(d['build'],name,name))
" 2>/dev/null
}

fetch() {
  local project="$1" version="$2"
  local dir="$DEST/$project/$version"
  mkdir -p "$dir"
  local jar="$dir/$project-$version.jar"

  if [ ! -f "$jar" ]; then
    echo "[$project $version] resolving latest build..."
    local info url
    info="$(build_info "$project" "$version")" || { echo "  ! no build info"; return 1; }
    url="${info%%|*}"
    [ -n "$url" ] || { echo "  ! empty url"; return 1; }
    echo "  downloading $(basename "${info##*|}") ..."
    curl -fsS --max-time 900 -H "User-Agent: $UA" -o "$jar" "$url" \
      || { echo "  ! download failed"; rm -f "$jar"; return 1; }
  fi

  # Newer paperclip extracts to versions/<v>/*.jar; older (1.17-era) to cache/patched_*.jar.
  local extracted
  extracted="$(find "$dir" \( -path '*/versions/*.jar' -o -name 'patched_*.jar' \) 2>/dev/null | head -1)"
  if [ -z "$extracted" ]; then
    echo "[$project $version] extracting (paperclip patchonly)..."
    ( cd "$dir" && "$(java_for "$version")" -Dpaperclip.patchonly=true -jar "$project-$version.jar" >patchonly.log 2>&1 ) \
      || echo "  ! patchonly failed (see $dir/patchonly.log) — jar kept; extract later with a matching JDK"
    extracted="$(find "$dir" \( -path '*/versions/*.jar' -o -name 'patched_*.jar' \) 2>/dev/null | head -1)"
  fi
  [ -n "$extracted" ] && echo "  javap-ready: $extracted" || echo "  (not extracted)"
}

# With no args, fetch the whole supported range (the local-dev default). With explicit `platform:version`
# targets (the same grammar as run-matrix.sh), fetch ONLY those — so a CI matrix job pulls just the one
# server jar it boots instead of all 13. Unknown platforms/args fail loud.
if [ "$#" -eq 0 ]; then
  for v in "${PAPER_VERSIONS[@]}"; do fetch paper "$v"; done
  for v in "${FOLIA_VERSIONS[@]}"; do fetch folia "$v"; done
else
  for target in "$@"; do
    case "$target" in
      paper:*) fetch paper "${target#paper:}" ;;
      folia:*) fetch folia "${target#folia:}" ;;
      *) echo "  ! unknown target '$target' (expected paper:<version> or folia:<version>)"; exit 2 ;;
    esac
  done
fi
echo "DONE"
