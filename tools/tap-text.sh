#!/usr/bin/env bash
# Tap the on-screen element whose text is exactly <text> (first match), found through uiautomator.
#
#   tools/tap-text.sh "Replace"            tap it
#   tools/tap-text.sh --find "Replace"     only print its centre, tap nothing
#
# Exits 1 without tapping when the text is not on screen, so a script never taps a guessed spot.
set -euo pipefail
source "$(dirname "$0")/env.sh"

FIND=0
if [[ "${1:-}" == "--find" ]]; then FIND=1; shift; fi
TEXT="${1:?usage: tools/tap-text.sh [--find] <text>}"

pad_require_device
adb shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1
xml="$(adb shell cat /sdcard/window_dump.xml | tr -d '\r')"

centre="$(TEXT="$TEXT" python3 -I -c '
import os, re, sys
import xml.etree.ElementTree as ET
root = ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    if node.get("text") == os.environ["TEXT"]:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
' <<<"$xml")"

if [[ -z "$centre" ]]; then
  echo "\"$TEXT\" is not on screen" >&2
  exit 1
fi
read -r x y <<<"$centre"
if (( FIND )); then
  echo "$x $y"
else
  adb shell input tap "$x" "$y"
  echo "tapped \"$TEXT\" at $x,$y"
fi
