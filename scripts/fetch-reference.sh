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
    ( cd "$dir" && java -Dpaperclip.patchonly=true -jar "$project-$version.jar" >patchonly.log 2>&1 ) \
      || echo "  ! patchonly failed (see $dir/patchonly.log) — jar kept; extract later with a matching JDK"
    extracted="$(find "$dir" \( -path '*/versions/*.jar' -o -name 'patched_*.jar' \) 2>/dev/null | head -1)"
  fi
  [ -n "$extracted" ] && echo "  javap-ready: $extracted" || echo "  (not extracted)"
}

for v in "${PAPER_VERSIONS[@]}"; do fetch paper "$v"; done
for v in "${FOLIA_VERSIONS[@]}"; do fetch folia "$v"; done
echo "DONE"
