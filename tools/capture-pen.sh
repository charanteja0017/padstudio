#!/usr/bin/env bash
# Capture what the tablet reports while you press a pen button.
#
#   tools/capture-pen.sh <label> [seconds]   one capture window (default 8 s), e.g. upper-short-hover
#   tools/capture-pen.sh --matrix            rebuild docs/device/pen-matrix.md from all captures
#
# Per window it records: kernel input events (getevent -lt), the foreground app and newest
# screenshot before/after (to see what Xiaomi did), and a logcat slice. Read-only.
set -euo pipefail
source "$(dirname "$0")/env.sh"

DIR="$PAD_ROOT/docs/device/raw/pen"
mkdir -p "$DIR"

if [[ "${1:-}" == "--matrix" ]]; then
  python3 -I "$PAD_ROOT/tools/lib/pen_matrix.py" matrix "$DIR" "$PAD_ROOT/docs/device/pen-matrix.md"
  exit 0
fi

LABEL="${1:-}"
WINDOW="${2:-8}"
if [[ ! "$LABEL" =~ ^[a-z0-9-]+$ ]]; then
  echo "usage: tools/capture-pen.sh <label> [seconds]   (label: lowercase letters, digits, dashes)" >&2
  exit 64
fi

pad_require_device

# Foreground component on line 1, newest screenshot file on line 2.
device_state() {
  adb shell "dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'" 2>/dev/null \
    | tr -d '\r' | grep -oE '[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+' | head -1 || true
  adb shell 'ls -t /sdcard/Pictures/Screenshots/* /sdcard/DCIM/Screenshots/* 2>/dev/null | head -1' 2>/dev/null \
    | tr -d '\r' || true
}

adb shell getevent -lp >"$DIR/$LABEL.names" 2>&1 || true
device_state >"$DIR/$LABEL.before"
START="$(pad_device_time)"

echo ">>> Recording for ${WINDOW}s — press the pen button now  [$LABEL]"
adb shell "timeout $WINDOW getevent -lt" >"$DIR/$LABEL.getevent" 2>&1 || true
sleep 1

device_state >"$DIR/$LABEL.after"
adb logcat -d -T "$START" >"$DIR/$LABEL.logcat" 2>/dev/null || true

python3 -I "$PAD_ROOT/tools/lib/pen_matrix.py" summarize "$DIR" "$LABEL"
