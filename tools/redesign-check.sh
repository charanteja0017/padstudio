#!/usr/bin/env bash
# PadStudio 2 on the tablet: install, upgrade checks, profile hash, screenshots (plan §5, §12, §13).
#
#   tools/redesign-check.sh state                      wakefulness, lock screen, app in front
#   tools/redesign-check.sh install <apk>              install over the current app (data kept), then `check`
#   tools/redesign-check.sh check                      config fields kept, widgets 42/49 bound, profile hash
#   tools/redesign-check.sh dump                       debug `dump profile` + `dump colors` → hash + docs/test-logs/
#   tools/redesign-check.sh shots <name> [opts] tab…   screenshot PadStudio tabs (today pen home looks setup about)
#        opts: --theme dark|light  --font <scale>  --bold on|off   (PadStudio's own debug overrides, reset after)
#   tools/redesign-check.sh demo <name>                screenshot the 3D pen demo activity
#   tools/redesign-check.sh open <name> probe|pentest  screenshot the pen probe or the pen test
#   tools/redesign-check.sh home <name>                screenshot the home screen (only if it is in front)
#
# Tablet rule (§12): acts only when the tablet is asleep, or the home screen or PadStudio is in front.
# Never presses HOME over another app, never changes a system setting, never uses uiautomator, never unlocks.
# Exit 75 means "not now" (another app in front, or locked when the screen is needed): retry later.
set -uo pipefail
source "$(dirname "$0")/env.sh"

PKG="$PKG_PADSTUDIO"
BASELINE_SHA="cdbc98bc0674ffb529c592637baa463fdc33fe0b707f539ed762f979f7a2cd39"
STATE_DIR="$PAD_ROOT/docs/device/raw/redesign-check"   # git-ignored (docs/device/raw/)
LOGS="$PAD_ROOT/docs/test-logs"
mkdir -p "$STATE_DIR" "$LOGS"
WAS_ASLEEP=0
WAS_TOP=""

wakefulness() { adb shell dumpsys power | tr -d '\r' | sed -n 's/^ *mWakefulness=//p' | head -1; }
keyguard()    { adb shell dumpsys window | tr -d '\r' | sed -n 's/^ *isKeyguardShowing=//p' | head -1; }
top_pkg() {
  adb shell dumpsys activity activities | tr -d '\r' | grep -m1 -E 'topResumedActivity|mResumedActivity' \
    | sed -E 's/.* ([a-zA-Z0-9_.]+)\/.*/\1/'
}

cmd_state() {
  echo "wakefulness=$(wakefulness) keyguard=$(keyguard) top=$(top_pkg)"
}

# Allowed now? $1 = "screen" when the run needs to see the screen (screenshots, activity starts).
tablet_rule() {
  local need="${1:-}" wake kg top
  wake="$(wakefulness)"; kg="$(keyguard)"; top="$(top_pkg)"
  WAS_TOP="$top"
  if [[ "$wake" != "Awake" ]]; then
    [[ "$need" != "screen" ]] && return 0
    if [[ "$kg" == "true" ]]; then
      echo "NOT NOW: the tablet is asleep and locked; screenshots need you to unlock it (I never unlock it)." >&2
      return 75
    fi
    WAS_ASLEEP=1
    adb shell input keyevent KEYCODE_WAKEUP
    sleep 1
    return 0
  fi
  if [[ "$kg" == "true" ]]; then
    [[ "$need" == "screen" ]] && { echo "NOT NOW: the lock screen is showing." >&2; return 75; }
    return 0
  fi
  case "$top" in
    com.miui.home|"$PKG") return 0 ;;
    *) echo "NOT NOW: $top is in front; I don't act over your apps. Retry later." >&2; return 75 ;;
  esac
}

# Put things back: HOME only if we brought PadStudio up over the home screen (or a sleeping tablet) and it is
# still in front; then sleep again if it was asleep. If you had PadStudio open yourself, it stays open.
leave() {
  local top
  top="$(top_pkg)"
  if [[ "$top" == "$PKG" && ( "$WAS_TOP" != "$PKG" || $WAS_ASLEEP == 1 ) ]]; then
    adb shell input keyevent KEYCODE_HOME; sleep 0.5
  fi
  if (( WAS_ASLEEP )); then adb shell input keyevent KEYCODE_SLEEP; fi
}

snapshot() { # snapshot <tag>: config.json and the widget table
  adb exec-out run-as "$PKG" cat files/config.json >"$STATE_DIR/config-$1.json" 2>/dev/null
  adb shell dumpsys appwidget | tr -d '\r' >"$STATE_DIR/appwidget-$1.txt"
}

widget_bound() { # widget_bound <file> <id> <receiver>
  grep -A3 -E "^ +\[[0-9]+\] id=$2\$" "$1" | grep -q "cmp:ComponentInfo{$PKG/$PKG.home.$3}"
}

cmd_check() {
  local ok=0 tag="${1:-after}"
  echo "• config fields kept:"
  if [[ "$tag" == "after" && -s "$STATE_DIR/config-before.json" ]]; then
    python3 -I -B - "$STATE_DIR/config-before.json" "$STATE_DIR/config-after.json" <<'PY' || ok=1
import json, sys
old, new = (json.load(open(p)) for p in sys.argv[1:3])
bad = [k for k in old if new.get(k) != old[k]]
added = sorted(set(new) - set(old))
print(f"  {len(old) - len(bad)}/{len(old)} existing fields identical; new fields: {added or 'none'}")
for k in bad:
    print(f"  CHANGED {k}: {json.dumps(old[k])[:120]} -> {json.dumps(new.get(k))[:120]}")
sys.exit(1 if bad else 0)
PY
  else
    echo "  (not right after an install; showing current keys)"
    python3 -I -B -c 'import json,sys; print("  ", sorted(json.load(open(sys.argv[1]))))' "$STATE_DIR/config-after.json"
  fi
  echo "• widgets (every PadStudio widget placed before the install is still bound to the same receiver):"
  python3 -I -B - "$STATE_DIR/appwidget-before.txt" "$STATE_DIR/appwidget-after.txt" "$tag" <<'PY2' || ok=1
import re, sys, os
def widgets(path):
    if not os.path.exists(path):
        return None
    found, current = {}, None
    for line in open(path):
        m = re.match(r"\s+\[\d+\] id=(\d+)$", line)
        if m:
            current = m.group(1)
            continue
        m = re.search(r"provider=.*cmp:ComponentInfo\{com\.charan\.padstudio/com\.charan\.padstudio\.home\.(\w+)\}", line)
        if m and current:
            found[current] = m.group(1)
            current = None
    return found
before, after = widgets(sys.argv[1]), widgets(sys.argv[2]) or {}
if sys.argv[3] != "after" or before is None:
    print("  now:", ", ".join(f"{i} {r}" for i, r in sorted(after.items())) or "none")
    sys.exit(0)
bad = [i for i, r in before.items() if after.get(i) != r]
for i, r in sorted(before.items()):
    print(f"  id {i} {r}: " + ("bound" if after.get(i) == r else "FAIL missing after install"))
for i in sorted(set(after) - set(before)):
    print(f"  id {i} {after[i]}: new since the snapshot")
sys.exit(1 if bad else 0)
PY2
  return $ok
}

cmd_install() {
  local apk="${1:-}"
  [[ -f "$apk" ]] || { echo "usage: $0 install <apk>" >&2; return 64; }
  tablet_rule || return $?
  snapshot before
  echo "Installing $(basename "$apk") ($(stat -f %z "$apk") bytes) …"
  adb install -r "$apk" || { echo "install failed" >&2; return 1; }
  sleep 2
  snapshot after
  cmd_check after
}

start_app() { # start_app <extras…>
  adb shell am start -W -n "$PKG/.MainActivity" "$@" >/dev/null
}

cmd_dump() {
  tablet_rule screen || return $?
  local before now sha="" i
  before="$(adb exec-out run-as "$PKG" stat -c %Y files/profile-dump.json 2>/dev/null)"
  start_app --es dump profile
  for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
    sleep 1
    now="$(adb exec-out run-as "$PKG" stat -c %Y files/profile-dump.json 2>/dev/null)"
    [[ -n "$now" && "$now" != "$before" ]] && break
  done
  [[ -n "$now" && "$now" != "$before" ]] || echo "  WARNING: files/profile-dump.json was not (re)written" >&2
  sha="$(adb exec-out run-as "$PKG" sha256sum files/profile-dump.json 2>/dev/null | awk '{print $1}')"
  adb exec-out run-as "$PKG" cat files/profile-dump.json >"$STATE_DIR/profile-dump.json" 2>/dev/null
  start_app --es dump colors
  sleep 2
  adb exec-out run-as "$PKG" cat files/colors.json >"$STATE_DIR/colors.json" 2>/dev/null
  leave
  echo "profile-dump sha256: ${sha:-MISSING} ($(wc -c <"$STATE_DIR/profile-dump.json" | tr -d ' ') bytes)"
  if [[ "$sha" == "$BASELINE_SHA" ]]; then echo "  MATCHES the protected baseline cdbc98bc…"; else echo "  DIFFERS from cdbc98bc… (explain with the pm list diff)"; fi
  echo "colors: $STATE_DIR/colors.json ($(wc -c <"$STATE_DIR/colors.json" | tr -d ' ') bytes)"
}

shot() { # shot <file>
  adb exec-out screencap -p >"$1"
  [[ "$(head -c 8 "$1" | xxd -p)" == "89504e470d0a1a0a" ]] || { echo "screencap failed: $1" >&2; return 1; }
  echo "$1"
}

cmd_shots() {
  local name="${1:-}"; shift || true
  [[ "$name" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "usage: $0 shots <name> [opts] tab…" >&2; return 64; }
  local extras=() resets=() tabs=()
  while (( $# )); do
    case "$1" in
      --theme) extras+=(--es theme "$2"); resets+=(--es theme system); shift 2 ;;
      --font)  extras+=(--es fontscale "$2"); resets+=(--es fontscale reset); shift 2 ;;
      --bold)  extras+=(--es boldtext "$2"); resets+=(--es boldtext reset); shift 2 ;;
      *) tabs+=("$1"); shift ;;
    esac
  done
  (( ${#tabs[@]} )) || tabs=(today pen home looks setup about)
  tablet_rule screen || return $?
  if (( ${#extras[@]} )); then start_app "${extras[@]}"; sleep 2; fi
  local t
  for t in "${tabs[@]}"; do
    start_app --es tab "$t"
    sleep 2
    shot "$LOGS/$(date +%Y-%m-%d)-$name-$t.png" || true
  done
  if (( ${#resets[@]} )); then start_app "${resets[@]}"; sleep 1; fi
  leave
}

cmd_open() { # open <name> probe|pentest: PadStudio's debug `--es open` screens, then a screenshot
  local name="${1:-}" what="${2:-}"
  [[ "$name" =~ ^[A-Za-z0-9._-]+$ && "$what" =~ ^(probe|pentest)$ ]] || { echo "usage: $0 open <name> probe|pentest" >&2; return 64; }
  tablet_rule screen || return $?
  start_app --es open "$what"
  sleep 3
  shot "$LOGS/$(date +%Y-%m-%d)-$name.png" || true
  adb shell input keyevent KEYCODE_BACK
  sleep 1
  leave
}

cmd_home() { # home <name>: a screenshot of the home screen (only when it is already in front)
  local name="${1:-home}"
  [[ "$(top_pkg)" == "com.miui.home" && "$(keyguard)" != "true" ]] || { echo "NOT NOW: the home screen isn't in front." >&2; return 75; }
  shot "$LOGS/$(date +%Y-%m-%d)-$name.png"
}

cmd_demo() {
  local name="${1:-pen3d}"
  tablet_rule screen || return $?
  adb shell am start -W -n "$PKG/.pen.PenModelDemoActivity" >/dev/null
  sleep 4
  shot "$LOGS/$(date +%Y-%m-%d)-$name.png"
  leave
}

pad_require_device || exit 1
case "${1:-}" in
  state) cmd_state ;;
  install) shift; cmd_install "$@" ;;
  check) snapshot after; cmd_check now ;;
  dump) cmd_dump ;;
  shots) shift; cmd_shots "$@" ;;
  demo) shift; cmd_demo "$@" ;;
  open) shift; cmd_open "$@" ;;
  home) shift; cmd_home "$@" ;;
  *) sed -n '2,13p' "$0" >&2; exit 64 ;;
esac
