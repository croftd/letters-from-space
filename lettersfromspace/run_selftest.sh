#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
source ./java_env.sh
bash ./build.sh                             # always rebuild (1-2 s) so edited sources are never ignored
exec java -cp build/classes lettersfromspace.SelfTest --data data
