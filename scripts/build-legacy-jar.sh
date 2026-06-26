#!/usr/bin/env bash
#
# Build the OPTIONAL Minecraft 1.8.9 jar (docs/legacy-1.8.9-codeshare-design.md).
#
# Pipeline (the §7 "lowers" gate, end to end):
#   1. dual-compile + assemble the legacy fat jar  — ./gradlew -Pse.target=legacy :bootstrap:jar
#      (compiles every module + the overlay/legacy seams against the REAL Spigot 1.8.8 jar; a 1.8-absent
#       symbol is a javac error here, not a runtime crash — Gate 1 + Gate 1b).
#   2. lower it to Java-8 bytecode + shade the JvmDowngrader stdlib API stubs AND its runtime helpers
#      (util.Utils) so the jar is self-contained and loads on a JDK-8 server.
#
# Prerequisites (one-time):
#   - JDK 17+ on PATH (runs Gradle + the JvmDowngrader CLI).
#   - The v1_8_R3 server jar in the local maven: run Spigot BuildTools once —
#       java -jar BuildTools.jar --rev 1.8.8 --compile craftbukkit
#     (installs org.bukkit:craftbukkit:1.8.8-R0.1-SNAPSHOT into ~/.m2). NOT on any public repo.
#
# Output: se/bootstrap/build/libs/StarEnchants-<version>-legacy.jar
#
# Live verification (Gate 4) is a SEPARATE, mandatory step: boot a real Spigot 1.8.8 under JDK 8 with this
# jar and run the legacy smoke suite. Per the §11 ownership precondition, the legacy lane must not ship
# without that gate green on every release.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

JDG_VERSION="1.3.6"
TARGET="52"                 # Java 8 class version
PREFIX="se_jdg"            # shade prefix for the JDG api/runtime (a standalone jar; avoids clashes)
WORK="${SE_LEGACY_WORK:-$HOME/se-legacy-buildtools}"
mkdir -p "$WORK"

# Confirm the BuildTools-local 1.8.8 server jar is present (the dual-compile gate's classpath).
if ! find "$HOME/.m2" -path '*craftbukkit/1.8.8*' -name '*.jar' | grep -q .; then
  echo "ERROR: org.bukkit:craftbukkit:1.8.8-R0.1-SNAPSHOT not found in ~/.m2." >&2
  echo "       Run Spigot BuildTools once: java -jar BuildTools.jar --rev 1.8.8 --compile craftbukkit" >&2
  exit 2
fi

echo "[legacy] 1/2  dual-compile + assemble the legacy fat jar (Gate 1 + Gate 1b) ..."
./gradlew -Pse.target=legacy :bootstrap:jar
VERSION="$(./gradlew -q -Pse.target=legacy :bootstrap:properties 2>/dev/null | awk -F': ' '/^version:/{print $2}')"
IN_JAR="$ROOT/se/bootstrap/build/libs/bootstrap-${VERSION}.jar"
OUT_JAR="$ROOT/se/bootstrap/build/libs/StarEnchants-${VERSION}-legacy.jar"

# Fetch the JvmDowngrader CLI once.
JDG="$WORK/jvmdowngrader-${JDG_VERSION}-all.jar"
if [ ! -f "$JDG" ]; then
  echo "[legacy] downloading JvmDowngrader ${JDG_VERSION} ..."
  curl -sL --fail -o "$JDG" \
    "https://repo1.maven.org/maven2/xyz/wagyourtail/jvmdowngrader/jvmdowngrader/${JDG_VERSION}/jvmdowngrader-${JDG_VERSION}-all.jar"
fi

echo "[legacy] 2/2  lower ${IN_JAR##*/} 61→${TARGET} + shade the JDG api/runtime ..."
# JDG's `shade` bundles the stub api it finds in --api, but NOT its util/runtime helpers — so build a
# self-contained api jar = the downgraded stub api PLUS JDG's util/* (the downgraded code calls util.Utils).
API_STUBS="$WORK/jdg-api-${TARGET}.jar"
UTIL_RAW="$WORK/jdg-util-raw.jar"
UTIL_LOW="$WORK/jdg-util-${TARGET}.jar"
API_FULL="$WORK/jdg-api-full-${TARGET}.jar"
IGNORE=(-i org.bukkit -i net.minecraft -i com.destroystokyo -i io.papermc)  # server-provided externals

java -jar "$JDG" -c "$TARGET" debug downgradeApi "$API_STUBS"
( cd "$WORK" && rm -rf jdgutil && mkdir jdgutil && cd jdgutil \
    && unzip -oq "$JDG" 'xyz/wagyourtail/jvmdg/util/*' && jar cf "$UTIL_RAW" xyz )
java -jar "$JDG" -c "$TARGET" downgrade --target "$UTIL_RAW" "$UTIL_LOW"
cp "$API_STUBS" "$API_FULL"
( cd "$WORK" && rm -rf jdgmerge && mkdir jdgmerge && cd jdgmerge \
    && unzip -oq "$UTIL_LOW" && jar uf "$API_FULL" xyz )

java -jar "$JDG" -c "$TARGET" --api "$API_FULL" "${IGNORE[@]}" \
  downgrade --target "$IN_JAR" - \
  shade --prefix "$PREFIX" --target - "$OUT_JAR"

echo "[legacy] done → $OUT_JAR"
