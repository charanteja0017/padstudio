# ADR-002 — Pen path: Expert Mode, generated profiles, key-driven shutter

Date: 2026-10-11 · Status: accepted · Gates: **G1 = B (Expert)**, **G4 = partial** (no fork yet)
Evidence (local, git-ignored unless noted): `docs/device/raw/pen/` (capture-pen runs before Key Mapper),
`docs/device/raw/2026-10-11-pen-squeeze.txt`, `tools/watch-pen.sh` logs, Key Mapper's own log,
`docs/test-logs/` screenshots (committed).

## G1 — where the Focus Pen Pro's gestures arrive

| Gesture | Arrives as | Device |
|---|---|---|
| Squeeze | `MSC_SCAN 0x0007006e` + `KEY_F19` (Android 332), down/up only | "Xiaomi Focus Pen Pro Keyboard" (Bluetooth, 0022:5081) |
| Slide up / down | `KEY_KP9` (153) / `KEY_KP3` (81) | same |
| Unidentified gesture | `KEY_KPENTER` (seen once, passed to HyperOS) | same |

- HyperOS consumes them (a squeeze opens its QuickAppPanel); none reach app windows or accessibility.
  So Basic mode can't use them: **Expert Mode** (Key Mapper's system bridge grabs the evdev device).
- **The squeeze has no pressure value.** None of the pen's three Bluetooth devices reports an analog
  axis; HyperOS's `stylus_pinch_pressure_adjust` sets the force threshold inside the pen.
- **The tip** (digitizer `NVTCapacitivePenP81c`): pressure raw 0–16383 (16,384 levels, PadStudio's pen
  test agrees), tilt ±60° per axis, hover reported as in range / out of range only, about 227–240
  samples/s. An `ABS_BRAKE` 0–360 axis is exposed as a joystick axis; no events seen (meaning UNVERIFIED).

## G4 — does a generated profile deliver the behaviours?

| Behaviour | Result |
|---|---|
| Import | Works once the shared file answers DocumentFile's columns; **Replace** keeps PadStudio's key map ids (Append re-randomises them). |
| Camera shutter | Clicking the recorded shutter button did nothing (cause not found). An **injected volume-down** takes a photo — through Key Mapper too (`ACTION_TRIGGER_KEYMAP_BY_UID` → `IMG_20261011_163058.jpg`). The camera key also works, but not right after the camera opens. |
| Screenshot, Back, library | Work (double squeeze → screenshot seen in the screenshot count; home squeeze → library seen in the foreground app). |
| Open camera | Key Mapper's generic action shows a chooser here; PadStudio now launches Xiaomi Camera directly. |
| Page turns (ReadEra) | Page Down / Up turn whole pages (9 → 10 of 19). |
| Play/pause | Needs Key Mapper's notification listener (granted; baseline recorded); not yet confirmed in a video app. |
| Pen on/off | Key Mapper switches only key maps directly in a group, so PadStudio switches each group. |

### Engine defects found in Key Mapper 4.6.1

1. **Bridge deadlock (very likely; stack traces need root or a bug report, not taken).**
   `evdev_grab_controller.rs`: `set_grab_targets` locks `grab_targets` then `grabbed_devices`;
   `on_inotify_dev_input` locks `grabbed_devices` then `grab_targets`. A grab request that meets a
   `/dev/input` change (a pen reconnect creates three nodes) deadlocks: the bridge stays alive holding
   nothing, Key Mapper still says "running", squeezes go to HyperOS. Seen twice (16:03 and 16:34, both
   right after the pen reconnected); two bridge threads were parked in `futex_wait`; restarting the
   bridge fixed it both times.
2. Key Mapper re-requests grabs whenever its accessibility service reconnects, and `uiautomator dump`
   unbinds every accessibility service — so screen automation makes (1) likely. Both incidents
   happened during automation.
3. A grabbed device is kept while a device with the same identity exists at the same path, so a pen
   recreated at the same `/dev/input/eventN` can look "still grabbed" (read in the source; not
   separately observed).

## Decision

- **Profile route stays; no fork yet.** In normal use a deadlock needs a pen reconnect at the same
  moment as a grab request. `tools/pen-health.sh --fix` detects "nothing held" and restarts the bridge.
- PadStudio's Pen tab never records Expert-mode controls the probe measured (HyperOS keeps those
  presses from apps); shutter = measured key first, button click only without key injection.
- **Build PadPen (M5a) with the lock-order fix** if `pen-health.sh` finds nothing held during normal
  use while Key Mapper is not paused. Reporting the bug upstream is outward-facing: only with the
  owner's go-ahead.
