#!/usr/bin/env bash
# Builds the lab as a static web site in build/site, with the network running in each visitor's
# browser: copy that folder to GitHub Pages or any web host. Options go to SiteBuilder, e.g.
#   ./build_site.sh --url https://example.org/lettersfromspace/ --counter abacus
# To try it locally:  python3 -m http.server 8000 --directory build/site  and open http://localhost:8000/
set -euo pipefail
cd "$(dirname "$0")"
source ./java_env.sh
bash ./build.sh
exec java -cp build/classes lettersfromspace.web.SiteBuilder "$@"
