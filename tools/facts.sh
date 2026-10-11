#!/usr/bin/env bash
# Rebuild docs/device/device-facts.json from the newest probe runs and hand it to PadStudio.
#
#   tools/facts.sh            rebuild + push into PadStudio (files/device-facts.json, private to the app)
#   tools/facts.sh --no-push  rebuild only
#
# Inputs: the newest docs/device/raw/<stamp>/ with getevent_lp.txt (pen devices, screen) and every
# raw dir with camera dumps (probe-device.sh --camera / --camera-keys). The pen controls are the
# Focus Pen Pro codes measured for gate G1 (docs/device/pen-matrix.md): squeeze = KEY_F19,
# slide up = KEY_KP9, slide down = KEY_KP3, all on "Xiaomi Focus Pen Pro Keyboard", Expert mode.
set -euo pipefail
source "$(dirname "$0")/env.sh"

PUSH=1
for arg in "$@"; do
  case "$arg" in
    --no-push) PUSH=0 ;;
    *) echo "unknown option: $arg" >&2; exit 64 ;;
  esac
done

RAW_ROOT="$PAD_ROOT/docs/device/raw"
probe="$(ls -d "$RAW_ROOT"/2*/ 2>/dev/null | while read -r d; do [[ -f "$d/getevent_lp.txt" ]] && echo "$d"; done | tail -1)"
[[ -n "$probe" ]] || { echo "No probe run with getevent_lp.txt under docs/device/raw — run tools/probe-device.sh first." >&2; exit 1; }
camera_args=()
for d in "$RAW_ROOT"/2*/; do
  compgen -G "$d/camera_ui_*.xml" >/dev/null || compgen -G "$d/camera_keys_*.tsv" >/dev/null || continue
  camera_args+=(--camera "$d")
done

OUT="$PAD_ROOT/docs/device/device-facts.json"
python3 -I "$PAD_ROOT/tools/lib/make_facts.py" --probe "$probe" "${camera_args[@]}" \
  --device "Xiaomi Focus Pen Pro Keyboard" \
  --control pinch=KEY_F19 --control slide_up=KEY_KP9 --control slide_down=KEY_KP3 \
  --kind FOCUS_PEN_PRO --mode EXPERT --out "$OUT"

if (( PUSH )); then
  pad_require_device
  adb shell "run-as $PKG_PADSTUDIO sh -c 'cat > files/device-facts.json && chmod 600 files/device-facts.json'" <"$OUT"
  echo "Pushed to $PKG_PADSTUDIO files/device-facts.json (PadStudio picks it up when it next refreshes)."
fi
