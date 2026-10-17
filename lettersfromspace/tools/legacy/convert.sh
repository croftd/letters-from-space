#!/usr/bin/env bash
# Converts the 2001 video.dat (Java serialization, French classes) to data/space_video_2001.dat (text).
# Needs the original sources in ../lettres_d_espace/code. Run from the lettersfromspace folder.
set -euo pipefail
cd "$(dirname "$0")/../.."
JAVAC=${JAVAC:-javac}; JAVA=${JAVA:-java}
LEGACY_SRC=../lettres_d_espace/code
mkdir -p build/legacy
find "$LEGACY_SRC/edu/esi/outils" "$LEGACY_SRC/edu/esi/reseauxneuronaux" "$LEGACY_SRC/edu/esi/lettresdespace" -maxdepth 1 -name '*.java' > build/legacy-sources.txt
"$JAVAC" -nowarn -encoding ISO-8859-1 -d build/legacy @build/legacy-sources.txt tools/legacy/ConvertLegacyVideo.java 2>&1 | grep -v -E '^(Note:|warning:)' || true
"$JAVA" -cp build/legacy ConvertLegacyVideo "$LEGACY_SRC/edu/esi/lettresdespace/video.dat" data/space_video_2001.dat
