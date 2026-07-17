#!/bin/bash
# Runs a JSON-scripted demo flow against the live BlueSeer Swing app and
# records the whole session to video.
#
# Stack: Xvfb (virtual display) + fluxbox (a bare Xvfb has no window manager,
# so the app's own maximize-on-launch call never takes effect and the window
# stays tiny - see MainFrame's setExtendedState(MAXIMIZED_BOTH) call, which
# only works if something re-asserts it once the window appears) + ffmpeg
# (x11grab, records the whole display) wrapped around DemoRunner, which plays
# back the JSON script via AssertJ-Swing (see SwingAppDriver.java).
#
# Usage:
#   demo.sh <workDir> <script.json> <outVideo.mp4> [width] [height]
#
# <workDir> is a directory containing bs.cfg, data/, etc. (e.g. a copy of
# target/) whose dist/ has the bsmf.jar build to demo.
#
# IMPORTANT: use a database that's already past BlueSeer's first-run "Choose
# Country of Origin" setup dialog (a fresh, never-configured bsdb.db throws
# that up on first login and requires an app restart to clear - not
# something this script scripts around, since it's one-time setup, not
# normal app behavior). A copy of a database that's already been through
# that once is what you want here.
set -euo pipefail

if [ $# -lt 3 ]; then
  echo "usage: demo.sh <workDir> <script.json> <outVideo.mp4> [width] [height]" >&2
  exit 2
fi

TOOL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK_DIR="$1"
SCRIPT_JSON="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
OUT_VIDEO="$3"
WIDTH="${4:-1280}"
HEIGHT="${5:-900}"

DISPLAY_NUM="${DEMO_DISPLAY:-95}"
export DISPLAY=":${DISPLAY_NUM}"

MANIFEST_DIR="$(mktemp -d)"

XVFB_PID=""
FLUXBOX_PID=""
FFMPEG_PID=""

cleanup() {
  local status=$?
  set +e
  if [ -n "$FFMPEG_PID" ] && kill -0 "$FFMPEG_PID" 2>/dev/null; then
    # SIGINT (not -9) so ffmpeg finalizes the mp4's container/moov atom
    # instead of leaving a corrupt/unplayable file.
    kill -INT "$FFMPEG_PID"
    wait "$FFMPEG_PID" 2>/dev/null
  fi
  [ -n "$FLUXBOX_PID" ] && kill "$FLUXBOX_PID" 2>/dev/null
  [ -n "$XVFB_PID" ] && kill "$XVFB_PID" 2>/dev/null
  exit $status
}
trap cleanup EXIT

Xvfb "$DISPLAY" -screen 0 "${WIDTH}x${HEIGHT}x24" >"$MANIFEST_DIR/xvfb.log" 2>&1 &
XVFB_PID=$!
for _ in $(seq 1 50); do
  [ -S "/tmp/.X11-unix/X${DISPLAY_NUM}" ] && break
  sleep 0.1
done

fluxbox >"$MANIFEST_DIR/fluxbox.log" 2>&1 &
FLUXBOX_PID=$!
sleep 1

ffmpeg -y -f x11grab -video_size "${WIDTH}x${HEIGHT}" -framerate 12 -i "$DISPLAY" \
    -c:v libx264 -preset ultrafast -pix_fmt yuv420p "$OUT_VIDEO" \
    >"$MANIFEST_DIR/ffmpeg.log" 2>&1 &
FFMPEG_PID=$!
sleep 1

"$TOOL_DIR/run.sh" demo "$WORK_DIR" "$SCRIPT_JSON" "$MANIFEST_DIR"

echo ""
echo "Video:    $OUT_VIDEO"
echo "Manifest: $MANIFEST_DIR/manifest.json"
