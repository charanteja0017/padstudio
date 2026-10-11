#!/usr/bin/env bash
# Is Key Mapper's Expert mode holding the pen? Optionally fix it.
#
#   tools/pen-health.sh          report
#   tools/pen-health.sh --fix    restart Expert mode (Key Mapper's own start.sh) when nothing is held
#
# Key Mapper 4.6.1's system bridge can deadlock when a grab request (its accessibility service
# reconnecting, key maps changing, pause/resume) meets the pen reconnecting (evdev_grab_controller.rs
# takes its two locks in opposite orders). The bridge then stays alive but holds nothing, Key Mapper
# still says "running", and squeezes reach HyperOS. Restarting the bridge clears it.
# Note: `uiautomator dump` unbinds every accessibility service for a moment, which makes that
# collision likely; tools/tap-text.sh uses it, so run this check after using tap-text.sh.
set -euo pipefail
source "$(dirname "$0")/env.sh"

FIX=0
for arg in "$@"; do
  case "$arg" in
    --fix) FIX=1 ;;
    *) echo "unknown option: $arg" >&2; exit 64 ;;
  esac
done
pad_require_device

held() { # prints the /dev/input nodes the bridge holds (not its uinput copies), empty if none
  (adb shell 'p=$(pidof keymapper_sysbridge); [ -n "$p" ] && ls -l /proc/$p/fd 2>/dev/null | grep -oE "/dev/input/event[0-9]+"' || true) |
    tr -d '\r' | sort -u | tr '\n' ' '
}

bridge="$( (adb shell pidof keymapper_sysbridge || true) | tr -d '\r')"
nodes="$(held)"
if [[ -n "$bridge" && -n "${nodes// /}" ]]; then
  echo "OK: Expert mode (pid $bridge) holds $nodes"
  exit 0
fi
if [[ -z "$bridge" ]]; then
  echo "PROBLEM: Expert mode is not running."
else
  echo "PROBLEM: Expert mode (pid $bridge) holds no pen device — squeezes go to HyperOS."
  echo "         (Also the case while Key Mapper is paused, or when no key map uses the pen.)"
fi
(( FIX )) || { echo "Run tools/pen-health.sh --fix to restart Expert mode."; exit 1; }

echo "Restarting Expert mode…"
adb shell sh /sdcard/Android/data/io.github.sds100.keymapper/start.sh | tail -1
sleep 5
nodes="$(held)"
if [[ -n "${nodes// /}" ]]; then
  echo "FIXED: Expert mode (pid $( (adb shell pidof keymapper_sysbridge || true) | tr -d '\r')) holds $nodes"
else
  echo "Still nothing held: check that Key Mapper isn't paused and that PadStudio's profile is applied." >&2
  exit 1
fi
