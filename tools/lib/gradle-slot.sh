#!/usr/bin/env bash
# Run PadStudio's Gradle with at most PAD_GRADLE_SLOTS builds at once, across this checkout and every
# git worktree of it (redesign plan §11: parallel agents share one 16 GB Mac).
#
#   tools/lib/gradle-slot.sh :core:test :app:assembleDebug :app:lintDebug
#
# 1. Takes a free slot: an atomic `mkdir` lock under $PAD_GRADLE_LOCKS. A lock whose process is gone is
#    cleared, so a killed agent never blocks the others.
# 2. Waits while macOS reports less than 10 % free memory (`memory_pressure`).
# 3. Refuses below the disk minimum (tools/env.sh, 8 GB).
# 4. Runs ./gradlew --build-cache in this checkout's padstudio/ (worktrees reuse each other's outputs).
# PAD_GRADLE_CMD replaces the Gradle command (used only to test the locking).
set -uo pipefail
source "$(dirname "$0")/../env.sh"

SLOTS="${PAD_GRADLE_SLOTS:-2}"
LOCKS="${PAD_GRADLE_LOCKS:-/private/tmp/claude-501/padstudio-gradle-slots}"
mkdir -p "$LOCKS"
pad_require_disk || exit 1

free_pct() { memory_pressure 2>/dev/null | awk -F': ' '/free percentage/ { gsub("%", "", $2); print int($2) }'; }

slot=""
release() { [[ -n "$slot" ]] && rm -rf "$slot"; }
trap release EXIT
trap 'release; exit 130' INT TERM

waited=0
while :; do
  for i in $(seq 1 "$SLOTS"); do
    lock="$LOCKS/slot-$i"
    if mkdir "$lock" 2>/dev/null; then
      echo "$$" >"$lock/pid"
      slot="$lock"
      break 2
    fi
    owner="$(cat "$lock/pid" 2>/dev/null || true)"
    if [[ -n "$owner" ]] && ! kill -0 "$owner" 2>/dev/null; then
      rm -rf "$lock"   # stale: its process has exited without releasing
    fi
  done
  if (( waited % 30 == 0 )); then echo "gradle-slot: all $SLOTS build slots busy, waiting…" >&2; fi
  sleep 2
  waited=$((waited + 2))
done

while :; do
  pct="$(free_pct)"
  if [[ -z "$pct" || "$pct" -ge 10 ]]; then break; fi
  echo "gradle-slot: memory pressure critical (${pct}% free), waiting…" >&2
  sleep 5
done

cd "$PAD_ROOT/padstudio" || exit 1
if [[ -n "${PAD_GRADLE_CMD:-}" ]]; then
  bash -c "$PAD_GRADLE_CMD"
else
  ./gradlew --build-cache "$@"
fi
