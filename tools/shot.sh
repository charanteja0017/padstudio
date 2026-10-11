#!/usr/bin/env bash
# Save what the tablet shows right now: tools/shot.sh <name> → docs/test-logs/<date>-<name>.png
set -euo pipefail
source "$(dirname "$0")/env.sh"

NAME="${1:-}"
if [[ ! "$NAME" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "usage: tools/shot.sh <name>   (letters, digits, dot, dash, underscore)" >&2
  exit 64
fi
pad_require_device

OUT="$PAD_ROOT/docs/test-logs/$(date +%Y-%m-%d)-$NAME.png"
mkdir -p "$(dirname "$OUT")"
adb exec-out screencap -p >"$OUT"
# A valid PNG starts with the 8-byte signature; an empty or text file means the capture failed.
if [[ "$(head -c 8 "$OUT" | xxd -p)" != "89504e470d0a1a0a" ]]; then
  echo "screencap failed (not a PNG): $OUT" >&2
  exit 1
fi
echo "$OUT"
