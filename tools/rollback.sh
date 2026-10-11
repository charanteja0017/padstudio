#!/usr/bin/env bash
# Undo Pad Suite: put the tablet back to the baseline recorded by tools/probe-device.sh.
#
#   tools/rollback.sh                 do it
#   tools/rollback.sh --dry-run       print what would happen, change nothing
#   tools/rollback.sh --keep-keymapper  keep upstream Key Mapper installed
#   tools/rollback.sh --keep-extras     keep Lawnicons / Arcticons / Open Camera installed
#
# Idempotent: running it twice is harmless. Never touches MIUI optimisation, navigation mode
# or animation scales (Pad Suite never changes them).
set -euo pipefail
source "$(dirname "$0")/env.sh"

DRY=0; KEEP_KM=0; KEEP_EXTRAS=0
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY=1 ;;
    --keep-keymapper) KEEP_KM=1 ;;
    --keep-extras) KEEP_EXTRAS=1 ;;
    *) echo "unknown option: $arg" >&2; exit 64 ;;
  esac
done

BASELINE="$PAD_ROOT/docs/device/baseline.env"
[[ -f "$BASELINE" ]] || { echo "No baseline at docs/device/baseline.env — nothing to roll back to." >&2; exit 1; }
# shellcheck source=/dev/null
source "$BASELINE"
pad_require_device

run() { # run <description> <command…>  (honours --dry-run)
  local what="$1"; shift
  if (( DRY )); then echo "would: $what"; else echo "• $what"; "$@" || echo "  (ignored failure: $what)"; fi
}

# Quote a value for the device-side shell (single quotes; embedded ' becomes '\'').
rq() { printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"; }

installed() { adb shell pm path "$1" 2>/dev/null | grep -q '^package:'; }

restore_setting() { # restore_setting <namespace> <key> <value> <unset-flag>
  local ns="$1" key="$2" value="$3" unset="$4"
  if [[ "$unset" == "1" ]]; then
    run "settings delete $ns $key" adb shell settings delete "$ns" "$key"
  else
    run "settings put $ns $key = $value" adb shell "settings put $ns $key $(rq "$value")"
  fi
}

# 1. Keep PadStudio's own data so a reinstall can restore it.
if installed "$PKG_PADSTUDIO"; then
  mkdir -p "$PAD_ROOT/docs/device/backup"
  run "save PadStudio config → docs/device/backup/padstudio-config.json" \
    sh -c "adb exec-out run-as $PKG_PADSTUDIO cat files/config.json > '$PAD_ROOT/docs/device/backup/padstudio-config.json'"
  run "open PadStudio reset screen (confirm on the tablet to reset the wallpaper)" \
    adb shell am start -W -n "$PKG_PADSTUDIO/.setup.ResetActivity"
  (( DRY )) || read -r -t 120 -p "Press Enter after confirming the reset on the tablet (or wait 2 min)… " _ || true
fi

# 2. Home app.
CURRENT_HOME="$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1)"
if [[ -n "${HOME_COMPONENT:-}" && "$CURRENT_HOME" != "$HOME_COMPONENT" ]]; then
  run "HOME → $HOME_COMPONENT" adb shell cmd package set-home-activity "$HOME_COMPONENT"
fi

# 3. Settings Pad Suite may have changed.
restore_setting secure enabled_accessibility_services "${A11Y_SERVICES:-}" "${A11Y_SERVICES_UNSET:-1}"
restore_setting secure accessibility_enabled "${A11Y_ENABLED:-}" "${A11Y_ENABLED_UNSET:-1}"
restore_setting secure launcher3.layout.provider "${LAYOUT_PROVIDER:-}" "${LAYOUT_PROVIDER_UNSET:-1}"
restore_setting global adb_wifi_enabled "${ADB_WIFI_ENABLED:-}" "${ADB_WIFI_ENABLED_UNSET:-1}"

# 4. Apps Pad Suite added (never ones that were there before the baseline).
remove_if_added() { # remove_if_added <pkg> <preexisting-flag>
  if [[ "$2" == "0" ]] && installed "$1"; then
    run "uninstall $1" adb uninstall "$1"
  fi
}
remove_if_added "$PKG_PADSTUDIO" "${PREEXISTING_PADSTUDIO:-0}"
remove_if_added com.charan.padpen 0
remove_if_added com.charan.padhome 0
remove_if_added "$PKG_LAWNCHAIR" "${PREEXISTING_LAWNCHAIR:-0}"
(( KEEP_KM )) || remove_if_added "$PKG_KEYMAPPER" "${PREEXISTING_KEYMAPPER:-0}"
if (( ! KEEP_EXTRAS )); then
  remove_if_added "$PKG_LAWNICONS" "${PREEXISTING_LAWNICONS:-0}"
  remove_if_added com.donnnno.arcticons "${PREEXISTING_ARCTICONS:-0}"
  remove_if_added net.sourceforge.opencamera "${PREEXISTING_OPENCAMERA:-0}"
fi

# 5. Empty folders we created (rmdir refuses non-empty ones, so user files are safe).
run "remove empty Documents/TabletWork folders" adb shell \
  'for d in 00_Inbox 01_Subjects 02_Projects 90_Exports 99_Backups; do rmdir /sdcard/Documents/TabletWork/$d 2>/dev/null; done; rmdir /sdcard/Documents/TabletWork 2>/dev/null; true'

# 6. Report what still differs from the baseline.
echo
echo "Check against baseline:"
printf '  HOME                %s (baseline %s)\n' \
  "$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1)" \
  "${HOME_COMPONENT:-?}"
printf '  accessibility       %s (baseline %s)\n' \
  "$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')" "${A11Y_SERVICES:-null}"
printf '  layout provider     %s (baseline %s)\n' \
  "$(adb shell settings get secure launcher3.layout.provider | tr -d '\r')" "${LAYOUT_PROVIDER:-null}"
for pkg in "$PKG_PADSTUDIO" "$PKG_KEYMAPPER" "$PKG_LAWNCHAIR"; do
  printf '  %-19s %s\n' "$pkg" "$(installed "$pkg" && echo installed || echo absent)"
done
