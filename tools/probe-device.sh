#!/usr/bin/env bash
# Read-only inventory of the connected Xiaomi Pad.
#
#   tools/probe-device.sh                 collect raw dumps + regenerate PROFILE.md, pen-device.json
#   tools/probe-device.sh --rebaseline    also overwrite docs/device/baseline.env (normally written once)
#   tools/probe-device.sh --camera        additionally open the stock camera and dump its UI tree
#                                         (finds the shutter button's resource-id; launches an app, nothing else)
#
# Nothing here changes device state, except --camera which opens the camera app.
set -euo pipefail
source "$(dirname "$0")/env.sh"

REBASELINE=""
WITH_CAMERA=0
for arg in "$@"; do
  case "$arg" in
    --rebaseline) REBASELINE="--rebaseline" ;;
    --camera) WITH_CAMERA=1 ;;
    *) echo "unknown option: $arg" >&2; exit 64 ;;
  esac
done

pad_require_device

STAMP="$(date +%Y%m%d-%H%M%S)"
RAW="$PAD_ROOT/docs/device/raw/$STAMP"
mkdir -p "$RAW"
echo "Collecting into docs/device/raw/$STAMP …"

sh_capture() { # sh_capture <file> <device shell command>
  local file="$1"; shift
  if ! adb shell "$@" >"$RAW/$file" 2>"$RAW/$file.err"; then
    echo "  (failed: $* — see $file.err)"
  fi
  [[ -s "$RAW/$file.err" ]] || rm -f "$RAW/$file.err"
}

sh_capture getprop.txt getprop
sh_capture wm.txt 'wm size; wm density'
sh_capture home.txt 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
sh_capture launchables.txt 'cmd package query-activities --brief --components -a android.intent.action.MAIN -c android.intent.category.LAUNCHER'
sh_capture packages_all.txt 'pm list packages'
sh_capture packages_disabled.txt 'pm list packages -d'
sh_capture packages_third.txt 'pm list packages -3'
sh_capture settings_system.txt 'settings list system'
sh_capture settings_secure.txt 'settings list secure'
sh_capture settings_global.txt 'settings list global'
sh_capture deviceidle.txt 'dumpsys deviceidle whitelist'
sh_capture dumpsys_input.txt 'dumpsys input'
sh_capture getevent_lp.txt 'getevent -lp'
sh_capture getevent_i.txt 'getevent -i'
sh_capture role_notes.txt 'cmd role get-role-holders android.app.role.NOTES'
sh_capture accessibility.txt 'dumpsys accessibility'
sh_capture stylus_packages.txt "pm list packages | grep -iE 'pen|stylus|canvas|notes|handwrit|thememanager'"

# One line per package we care about: "<pkg>\t<versionName or 'not installed'>".
: >"$RAW/versions.txt"
for pkg in com.miui.home com.android.camera com.miui.notes com.miui.creation com.android.thememanager \
           "$PKG_KEYMAPPER" "$PKG_PADSTUDIO" "$PKG_LAWNICONS" com.donnnno.arcticons net.sourceforge.opencamera \
           com.notein.android com.notewise.app com.flexcil.flexcilnote; do
  version="$(adb shell "dumpsys package $pkg | grep -m1 versionName" 2>/dev/null | tr -d '\r' | sed 's/.*versionName=//')"
  printf '%s\t%s\n' "$pkg" "${version:-not installed}" >>"$RAW/versions.txt"
done

if (( WITH_CAMERA )); then
  echo "Opening the stock camera to locate the shutter button…"
  adb shell am start -W -a android.media.action.STILL_IMAGE_CAMERA >"$RAW/camera_start.txt" 2>&1 || true
  sleep 3
  # One orientation per run: rotate the tablet and run `--camera` again for the other one.
  rotation="$(adb shell dumpsys input | grep -m1 -oE 'SurfaceOrientation: [0-9]' | tr -dc '0-9')"
  adb shell uiautomator dump /sdcard/padsuite_cam.xml >"$RAW/camera_uiautomator_rot${rotation:-x}.log" 2>&1 || true
  adb shell cat /sdcard/padsuite_cam.xml >"$RAW/camera_ui_rot${rotation:-x}.xml" 2>/dev/null || true
  adb shell rm -f /sdcard/padsuite_cam.xml >/dev/null 2>&1 || true
  adb shell dumpsys activity top >"$RAW/camera_activity_top_rot${rotation:-x}.txt" 2>/dev/null || true
  adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
fi

python3 -I "$PAD_ROOT/tools/lib/make_profile.py" "$RAW" "$PAD_ROOT/docs/device" $REBASELINE
echo "Done. Read docs/device/PROFILE.md"
