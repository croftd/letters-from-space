#!/usr/bin/env bash
# Compiles everything under src/ into build/classes. Needs a JDK (17 or newer) on the PATH,
# or set JAVA_HOME. On this WSL machine a Temurin JDK lives in ~/.local/jdk (auto-detected).
set -euo pipefail
cd "$(dirname "$0")"
if [ -n "${JAVA_HOME:-}" ]; then
  JAVAC="$JAVA_HOME/bin/javac"
elif command -v javac >/dev/null 2>&1; then
  JAVAC=javac
else
  JAVAC=$(ls -d "$HOME"/.local/jdk/jdk-*/bin/javac 2>/dev/null | head -1 || true)
fi
if [ -z "${JAVAC:-}" ] || [ ! -x "$JAVAC" ] && ! command -v "$JAVAC" >/dev/null 2>&1; then
  echo "No javac found. Install a JDK (https://adoptium.net) or set JAVA_HOME." >&2
  exit 1
fi
rm -rf build/classes                      # start clean: no classes left over from renamed or deleted files
mkdir -p build/classes
find src -name '*.java' > build/sources.txt
"$JAVAC" -encoding UTF-8 --release 17 -Xlint:all -d build/classes @build/sources.txt
echo "Compiled $(wc -l < build/sources.txt) files into build/classes with $("$JAVAC" -version 2>&1)"
