#!/bin/bash
# Pairwise-diffs two UI regression screenshot directories (record vs. replay
# output) using ImageMagick, and writes a CSV report sorted worst-first plus
# heatmap diff images for anything over the threshold.
#
# Usage: diff-report.sh <recordOutDir> <replayOutDir> <reportDir> [aeThreshold]
set -uo pipefail

RECORD_DIR="$1"
REPLAY_DIR="$2"
REPORT_DIR="$3"
THRESHOLD="${4:-2000}"   # ImageMagick "AE" (absolute error pixel count) at -fuzz 5%

mkdir -p "$REPORT_DIR/diffs"
CSV="$REPORT_DIR/report.csv"
echo "screenshot,ae_pixels,status" > "$CSV"

shopt -s nullglob
for img in "$RECORD_DIR"/*.png; do
  name="$(basename "$img")"
  other="$REPLAY_DIR/$name"
  if [ ! -f "$other" ]; then
    echo "$name,,MISSING_IN_REPLAY" >> "$CSV"
    continue
  fi
  ae=$(compare -metric AE -fuzz 5% "$img" "$other" "$REPORT_DIR/diffs/$name" 2>&1 || true)
  # `compare` prints the AE count to stderr even on success; strip any
  # trailing "(0.0123)" normalized-error suffix it sometimes appends.
  ae_num=$(echo "$ae" | grep -oE '^[0-9]+' | head -1)
  if [ -z "$ae_num" ]; then
    echo "$name,,ERROR:$ae" >> "$CSV"
    continue
  fi
  if [ "$ae_num" -le "$THRESHOLD" ]; then
    rm -f "$REPORT_DIR/diffs/$name"
    echo "$name,$ae_num,OK" >> "$CSV"
  else
    echo "$name,$ae_num,DIFF" >> "$CSV"
  fi
done

for img in "$REPLAY_DIR"/*.png; do
  name="$(basename "$img")"
  if [ ! -f "$RECORD_DIR/$name" ]; then
    echo "$name,,MISSING_IN_RECORD" >> "$CSV"
  fi
done

echo "Report: $CSV"
echo "Diff images (over threshold $THRESHOLD): $REPORT_DIR/diffs/"
echo
echo "Worst divergences:"
tail -n +2 "$CSV" | grep ',DIFF$' | sort -t, -k2 -rn | head -20 | column -s, -t
