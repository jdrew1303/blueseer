#!/bin/bash
# Builds BlueSeer with the "browser" Maven profile (Java 17 bytecode, the
# ceiling CheerpJ's JVM understands) and serves target/ over HTTP with
# index.html copied in, so /app/dist/* resolves for the CheerpJ loader.
# See browser/README.md for the full picture, including what's known NOT
# to work yet (SQLite JDBC, hardware fingerprinting).
set -euo pipefail
cd "$(dirname "$0")/.."

PORT="${1:-8934}"

mvn package -Pbrowser -DskipTests
cp browser/index.html target/index.html

echo "Serving target/ at http://localhost:$PORT/index.html"
npx http-server target -p "$PORT" -c-1
