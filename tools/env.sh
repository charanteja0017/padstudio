#!/usr/bin/env bash
# Shared environment for every Pad Suite script.
#   source tools/env.sh          → exports paths, defines helpers
#   tools/env.sh --check         → prints toolchain + device + disk summary
#
# Never changes the global `java`: builds pin JDK 17 through org.gradle.java.home.

PAD_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export PAD_ROOT

export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
case ":$PATH:" in
  *":$ANDROID_HOME/platform-tools:"*) ;;
  *) export PATH="$ANDROID_HOME/platform-tools:$PATH" ;;
esac

export PAD_JDK17="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PAD_BUILD_TOOLS="$ANDROID_HOME/build-tools/37.0.0"
export PAD_MIN_FREE_GB="${PAD_MIN_FREE_GB:-8}"

# Package ids used across scripts.
export PKG_KEYMAPPER="io.github.sds100.keymapper"
export PKG_PADSTUDIO="com.charan.padstudio"
export PKG_LAWNCHAIR="app.lawnchair"
export PKG_LAWNICONS="app.lawnchair.lawnicons"

pad_free_gb() {
  df -g "$HOME" | awk 'NR == 2 { print $4 }'
}

# Refuse heavy work (builds, emulator images) when the disk is nearly full.
pad_require_disk() {
  local free
  free="$(pad_free_gb)"
  if (( free < PAD_MIN_FREE_GB )); then
    echo "Refusing: only ${free} GB free on $HOME (minimum ${PAD_MIN_FREE_GB} GB)." >&2
    return 1
  fi
}

# Exactly one usable device, or a clear explanation of what to do.
pad_require_device() {
  local count
  if ! command -v adb >/dev/null 2>&1; then
    echo "adb not found (expected in $ANDROID_HOME/platform-tools)." >&2
    return 1
  fi
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    [[ "$(adb get-state 2>/dev/null)" == "device" ]] && return 0
    echo "Device $ANDROID_SERIAL is not ready (state: $(adb get-state 2>&1))." >&2
    return 1
  fi
  # Fields are tab-separated; wireless-debugging serials contain spaces ("adb-… (2)._adb-tls-connect._tcp").
  count="$(adb devices | awk -F'\t' 'NR > 1 && $2 == "device"' | wc -l | tr -d ' ')"
  if [[ "$count" == "1" ]]; then
    # Pin it: adb's mDNS auto-connect can add duplicate transports to the same tablet mid-script.
    export ANDROID_SERIAL="$(adb devices | awk -F'\t' 'NR > 1 && $2 == "device" { print $1; exit }')"
    return 0
  fi
  if [[ "$count" == "0" ]]; then
    cat >&2 <<'EOF'
No tablet connected. On the Pad:
  1. Settings → About tablet → tap "OS version" 7 times (enables Developer options).
  2. Settings → Additional settings → Developer options:
       USB debugging ............................ ON
       Install via USB .......................... ON
       USB debugging (Security settings) ........ ON   (may ask for Mi-account sign-in)
  3. Connect the USB-C cable and accept the "Allow USB debugging" prompt,
     or turn on Wireless debugging (same Wi-Fi as the Mac); `adb mdns services` should list it.
Then re-run this script. `adb devices` should list the tablet as "device".
EOF
    adb devices | sed -n '2,$p' | grep -v '^$' | sed 's/^/  seen: /' >&2 || true
    return 1
  fi
  # Toggling wireless debugging leaves several mDNS transports to the SAME tablet. If every transport
  # reports one ro.serialno, pick the first and carry on; only genuinely different devices need a choice.
  local serials first
  serials="$(adb devices | awk -F'\t' 'NR > 1 && $2 == "device" { print $1 }' | while IFS= read -r s; do
    adb -s "$s" shell getprop ro.serialno </dev/null 2>/dev/null | tr -d '\r'
  done | sort -u)"
  if [[ -n "$serials" && "$(printf '%s\n' "$serials" | wc -l | tr -d ' ')" == "1" ]]; then
    first="$(adb devices | awk -F'\t' 'NR > 1 && $2 == "device" { print $1; exit }')"
    export ANDROID_SERIAL="$first"
    echo "Several adb transports reach the same tablet ($serials); using: $first" >&2
    return 0
  fi
  echo "More than one device connected; set ANDROID_SERIAL to choose one:" >&2
  adb devices -l | sed -n '2,$p' >&2
  return 1
}

# Device-side timestamp in the format `logcat -T` accepts.
pad_device_time() {
  # Quoted as one string: adb joins arguments and the device shell would split the format at the space.
  adb shell "date +'%m-%d %H:%M:%S.000'" | tr -d '\r'
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  case "${1:-}" in
    --check)
      echo "PAD_ROOT      $PAD_ROOT"
      echo "ANDROID_HOME  $ANDROID_HOME"
      printf 'adb           '; adb version 2>/dev/null | sed -n '1p;2p' | tr '\n' ' '; echo
      printf 'JDK 17        '; "$PAD_JDK17/bin/java" -version 2>&1 | head -1
      printf 'free disk     %s GB (minimum for builds: %s GB)\n' "$(pad_free_gb)" "$PAD_MIN_FREE_GB"
      printf 'devices       '; adb devices | awk -F'\t' 'NR > 1 && NF { print $1 " [" $2 "]" }' | tr '\n' ';'; echo
      ;;
    *)
      echo "Source this file (source tools/env.sh) or run: tools/env.sh --check" >&2
      exit 64
      ;;
  esac
fi
