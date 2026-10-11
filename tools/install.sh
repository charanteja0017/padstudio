#!/usr/bin/env bash
# Install Pad Suite components on the tablet.
#
#   tools/install.sh keymapper    upstream Key Mapper 4.6.1 (pen engine) + WRITE_SECURE_SETTINGS
#   tools/install.sh lawnicons    Lawnicons icon pack
#   tools/install.sh lawnchair    upstream Lawnchair beta — ONLY for gate G2, removed afterwards
#   tools/install.sh padstudio    build + install PadStudio, restore its saved config if present
#
# Refuses to run until tools/probe-device.sh has recorded the rollback baseline.
set -euo pipefail
source "$(dirname "$0")/env.sh"

TARGET="${1:-}"
BASELINE="$PAD_ROOT/docs/device/baseline.env"
APKS="$PAD_ROOT/apks"

pad_require_device
if [[ ! -f "$BASELINE" ]]; then
  echo "No rollback baseline yet. Run tools/probe-device.sh first (it records the device state before we change anything)." >&2
  exit 1
fi

install_apk() { # install_apk <file>
  local apk="$1"
  if [[ ! -f "$apk" ]]; then
    echo "missing $apk (see docs/decisions/ADR-000-baselines.md for the download command)" >&2
    exit 1
  fi
  echo "Installing $(basename "$apk") …"
  if ! adb install -r "$apk"; then
    echo "Install failed. On Xiaomi, INSTALL_FAILED_USER_RESTRICTED means 'Install via USB' or" >&2
    echo "'USB debugging (Security settings)' is off in Developer options." >&2
    exit 1
  fi
}

case "$TARGET" in
  keymapper)
    install_apk "$APKS/keymapper-4.6.1-foss.apk"
    adb shell pm grant "$PKG_KEYMAPPER" android.permission.WRITE_SECURE_SETTINGS \
      && echo "Granted WRITE_SECURE_SETTINGS (lets Key Mapper restart its service and use Expert Mode auto-start)." \
      || echo "Could not grant WRITE_SECURE_SETTINGS — turn on 'USB debugging (Security settings)' and re-run."
    cat <<'EOF'
Next on the tablet (Xiaomi survival kit):
  • Key Mapper → turn on its accessibility service (if "Restricted setting" appears:
    Settings → Apps → Key Mapper → ⋮ → Allow restricted settings, then try again)
  • Settings → Apps → Key Mapper → Autostart ON; Battery saver → No restrictions
  • Recents → pull Key Mapper's card down to lock it
  • Key Mapper's own Xiaomi optimisation screen (it walks through the rest)
EOF
    ;;
  lawnicons)
    install_apk "$APKS/Lawnicons.2.18.0.apk"
    ;;
  lawnchair)
    install_apk "$APKS/Lawnchair.15.0.0.Beta.3.0.apk"
    echo "Gate G2 only. Set as HOME with: adb shell cmd package set-home-activity app.lawnchair/app.lawnchair.LawnchairLauncher"
    echo "Undo with tools/rollback.sh (restores the baseline HOME and uninstalls Lawnchair)."
    ;;
  padstudio)
    pad_require_disk
    (cd "$PAD_ROOT/padstudio" && ./gradlew --quiet :app:assembleDebug)
    install_apk "$PAD_ROOT/padstudio/app/build/outputs/apk/debug/app-debug.apk"
    SAVED="$PAD_ROOT/docs/device/backup/padstudio-config.json"
    if [[ -f "$SAVED" ]]; then
      adb shell "run-as $PKG_PADSTUDIO sh -c 'mkdir -p files && cat > files/config.json'" <"$SAVED" \
        && echo "Restored PadStudio config from docs/device/backup/."
    fi
    ;;
  *)
    sed -n '2,9p' "$0" >&2
    exit 64
    ;;
esac
