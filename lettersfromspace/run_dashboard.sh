#!/usr/bin/env bash
# Starts the web dashboard on http://localhost:8080 (pass another port as the first argument).
# Further options go to the server, e.g.  ./run_dashboard.sh 8080 --video data/space_video_2001.dat
set -euo pipefail
cd "$(dirname "$0")"
source ./java_env.sh
bash ./build.sh                             # always rebuild (1-2 s) so edited sources are never ignored
exec java -cp build/classes lettersfromspace.web.DashboardServer --port "${1:-8080}" --data data --web web "${@:2}"
