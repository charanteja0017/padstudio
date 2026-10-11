#!/usr/bin/env bash
# Plan Step 9 spike, live run: PadPenProbe takes the Focus Pen Pro instead of Key Mapper, for a bounded time.
#
#   tools/spike/padpen-probe/run_spike.sh [LOG_DIR]        (default: docs/device/raw/spike-<stamp>/, git-ignored)
#
# Safety: Key Mapper is paused only for the run and is ALWAYS resumed on exit (normal end, timeout, Ctrl-C,
# kill). The probe exits by itself after SPIKE_SECONDS (default 600). While it runs, the pen's gestures are
# only logged; a double tap injects VOLUME_DOWN + VOLUME_UP (net zero) to prove injection.
set -uo pipefail
source "$(dirname "$0")/../../env.sh"
pad_require_device || exit 1

SECONDS_MAX="${SPIKE_SECONDS:-600}"
LOG_DIR="${1:-$PAD_ROOT/docs/device/raw/spike-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$LOG_DIR"
KM="io.github.sds100.keymapper"
RX="$KM/$KM.api.PauseMappingsBroadcastReceiver"

resume() {
  [[ -n "${MON_PID:-}" ]] && kill "$MON_PID" 2>/dev/null
  adb shell am broadcast -a "$KM.ACTION_RESUME_MAPPINGS" -n "$RX" >/dev/null 2>&1 \
    && echo "$(date +%H:%M:%S) Key Mapper resumed" | tee -a "$LOG_DIR/probe.log"
}
trap resume EXIT
trap 'exit 130' INT TERM

adb push "$PAD_ROOT/tools/spike/padpen-probe/build/classes.dex" /data/local/tmp/padpen-probe.dex >/dev/null || exit 1
adb shell am broadcast -a "$KM.ACTION_PAUSE_MAPPINGS" -n "$RX" >/dev/null && echo "$(date +%H:%M:%S) Key Mapper paused" | tee "$LOG_DIR/probe.log"
sleep 2

# Any window HyperOS shows during the run (its squeeze panel, the volume panel) appears as a focus change.
(
  last=""
  while true; do
    f="$(adb shell dumpsys window displays 2>/dev/null | grep -m1 'mCurrentFocus=' | tr -d '\r' | sed 's/^ *//')"
    if [[ "$f" != "$last" ]]; then echo "$(date +%H:%M:%S) $f"; last="$f"; fi
    sleep 0.4
  done
) >"$LOG_DIR/focus.log" 2>&1 &
MON_PID=$!

echo "$(date +%H:%M:%S) probe start (max ${SECONDS_MAX}s)" | tee -a "$LOG_DIR/probe.log"
adb shell "timeout $SECONDS_MAX app_process -Djava.class.path=/data/local/tmp/padpen-probe.dex /system/bin PadPenProbe --inject" 2>&1 \
  | perl -pe 'BEGIN { $| = 1 } s/\r//' | tee -a "$LOG_DIR/probe.log"
echo "$(date +%H:%M:%S) probe ended; logs in $LOG_DIR" | tee -a "$LOG_DIR/probe.log"
