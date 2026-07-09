#!/bin/bash
# Compiles (if needed) and runs the UI regression walker.
#
# Usage:
#   run.sh record <bsCfgWorkingDir> <outDir> [--filter text] [--max-leaves N] [--seed N]
#   run.sh replay <bsCfgWorkingDir> <actionLog> <outDir>
#
# <bsCfgWorkingDir> is a directory containing bs.cfg, data/, etc. (e.g. a copy
# of target/, or a jpackage app-image's app directory) whose dist/ folder has
# the bsmf.jar build under test. See README.md in this directory.
set -euo pipefail

TOOL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$TOOL_DIR/../.." && pwd)"
CP_FILE="$TOOL_DIR/.test-classpath.txt"

if [ ! -f "$TOOL_DIR/out/com/blueseer/uitest/UiRegressionRunner.class" ] || [ "$1" == "--rebuild" ]; then
  if [ "$1" == "--rebuild" ]; then shift; fi
  echo "Building UI regression walker..." >&2
  ( cd "$REPO_ROOT" && mvn -q -Dmaven.compiler.release=21 dependency:build-classpath \
      -Dmdep.outputFile="$CP_FILE" -DincludeScope=test )
  rm -rf "$TOOL_DIR/out"
  mkdir -p "$TOOL_DIR/out"
  javac -encoding UTF-8 --release 21 -d "$TOOL_DIR/out" \
      -cp "$REPO_ROOT/target/classes:$(cat "$CP_FILE")" \
      "$TOOL_DIR"/src/com/blueseer/uitest/*.java
fi

if [ ! -f "$CP_FILE" ]; then
  ( cd "$REPO_ROOT" && mvn -q -Dmaven.compiler.release=21 dependency:build-classpath \
      -Dmdep.outputFile="$CP_FILE" -DincludeScope=test )
fi

WORK_DIR="$(cd "$2" && pwd)"

cd "$WORK_DIR"
java -cp "$TOOL_DIR/out:$(cat "$CP_FILE"):$WORK_DIR/dist/*" \
    -Djava.awt.headless=false \
    com.blueseer.uitest.UiRegressionRunner "$@"
