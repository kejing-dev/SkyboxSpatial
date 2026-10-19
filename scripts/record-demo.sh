#!/usr/bin/env bash
# Records the scripted SkyboxSpatial demo on the PICO emulator: types the prompt, generates for real,
# saves, enters the Full Space and turns the panorama. Output: deliverables/SkyboxSpatial-demo.mp4
#
# Usage: scripts/record-demo.sh <lang zh|en> <prompt-file> [style-id] [key-file]
#   key-file is optional; when given its content is installed as the app's API key and not kept on disk
#   by this script (the caller decides whether to delete the source).
set -euo pipefail
export MSYS_NO_PATHCONV=1
export PATH="$PATH:/c/Users/Admin/AppData/Local/Android/Sdk/platform-tools"

LANG_TAG="${1:-zh}"
PROMPT_FILE="${2:?prompt file}"
STYLE_ID="${3:-}"
KEY_FILE="${4:-}"
DEVICE="${DEVICE:-emulator-5554}"
SECONDS_TO_RECORD="${SECONDS_TO_RECORD:-170}"
PKG=com.kejing.skyboxspatial
ROOT="$(cd "$(dirname "$0")/.." && pwd -W 2>/dev/null || pwd)"
OUT_DIR="$ROOT/deliverables"
mkdir -p "$OUT_DIR"
A="adb -s $DEVICE"

push_private() { # <local> <name in files/>
  $A push "$1" "/data/local/tmp/$2" >/dev/null
  $A shell "chmod 644 /data/local/tmp/$2; run-as $PKG cp /data/local/tmp/$2 files/$2; rm /data/local/tmp/$2"
}

$A shell am force-stop $PKG
push_private "$PROMPT_FILE" demo_prompt.txt
if [ -n "$KEY_FILE" ]; then push_private "$KEY_FILE" demo_key.txt; fi
# Start from an empty library so the flow looks like a first run.
$A shell "run-as $PKG rm -rf files/library cache/results" || true
$A shell settings put global skyboxspatial_demo "record:${LANG_TAG}${STYLE_ID:+:$STYLE_ID}"

$A shell "rm -f /sdcard/skybox-demo.mp4"
$A shell "screenrecord --time-limit $SECONDS_TO_RECORD --size 1080x1080 --bit-rate 12000000 /sdcard/skybox-demo.mp4" &
REC=$!
sleep 2
pico-cli app launch -d "$DEVICE" $PKG >/dev/null
wait $REC || true
$A pull /sdcard/skybox-demo.mp4 "$OUT_DIR/skybox-demo-raw.mp4" >/dev/null
$A shell settings delete global skyboxspatial_demo >/dev/null
$A shell "run-as $PKG rm -f files/demo_prompt.txt"
echo "raw recording: $OUT_DIR/skybox-demo-raw.mp4"
