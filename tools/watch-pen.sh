#!/usr/bin/env bash
# Watch what pen presses do, for physical tests (nothing on the tablet is changed).
#
#   tools/watch-pen.sh            print changes until Ctrl-C
#
# Prints a line when the foreground app, the photo count (DCIM/Camera) or the screenshot count
# changes, and every pen key that reaches anything other than Key Mapper's Expert-mode grab:
# with the grab in place a mapped press prints nothing there, an unmapped one shows up as
# "passed on" (Key Mapper forwards it to HyperOS through its own copy of the pen device).
# No -e/pipefail: a monitor must outlive one failed adb call (and head/grep closing pipes early).
set -u
source "$(dirname "$0")/env.sh"
pad_require_device

stamp() { date '+%H:%M:%S'; }

adb shell "getevent -lt 2>/dev/null | grep --line-buffered -E 'KEY_F1[3-9]|KEY_KP[0-9A-Z]*|KEY_PAGE(UP|DOWN)'" |
  while IFS= read -r line; do echo "$(stamp) passed on  ${line//$'\r'/}"; done &
events=$!
trap 'kill "$events" 2>/dev/null' EXIT

last=""
while true; do
  # HyperOS prints "mCurrentFocusedWindow = pkg", AOSP "mCurrentFocus=Window{id u0 pkg/activity}".
  focus="$(adb shell dumpsys window 2>/dev/null | tr -d '\r' | grep -E 'mCurrentFocus' | head -1 | sed -E 's/.*= *//; s/^Window\{[^ ]+ [^ ]+ //; s/\}.*//')"
  photos="$(adb shell 'ls /sdcard/DCIM/Camera 2>/dev/null | wc -l' | tr -d '\r ')"
  shots="$(adb shell 'ls /sdcard/DCIM/Screenshots /sdcard/Pictures/Screenshots 2>/dev/null | grep -c Screenshot_' | tr -d '\r ')"
  now="app=$focus photos=$photos screenshots=$shots"
  [[ "$now" != "$last" ]] && echo "$(stamp) $now"
  last="$now"
  sleep 1
done
