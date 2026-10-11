# ADR-006: Can PadStudio own the pen without Key Mapper? (plan Step 9 spike)

**Status:** spike run 11 Oct 2026, 22:54–23:10 IST.
- **Result:** 3 of 4 gate criteria pass. The reconnect case has not been tested yet.
- **Decision:** a native PadStudio pen engine is feasible without root, Key Mapper or the NDK. It gets built **after the UI** (you asked for UI first), starting with the reconnect test.

## Question
HyperOS consumes the Focus Pen Pro's keys before any app or accessibility service sees them (ADR-002, G1). Can a process **we** start take the pen's input device first, the way Key Mapper's Expert mode does, so PadStudio doesn't need a second app?

## Method
- **The probe:** `tools/spike/padpen-probe/PadPenProbe.java`, about 170 lines of Java.
  - Compiled with `javac --release 11` and turned into a 6 KB dex with `d8` from build-tools 37.
  - Run on the tablet as the shell user over wireless debugging: `app_process -Djava.class.path=/data/local/tmp/padpen-probe.dex /system/bin PadPenProbe --inject`.
- **Finding the device:**
  - HyperOS's security policy denies shell `/proc/bus/input/devices` and sysfs device names (both EACCES). So the probe asks `getevent -i`.
  - Two devices are named "Xiaomi Focus Pen Pro Keyboard":
    - the real pen, `/dev/input/event15`, has location `2c:0d:cf:05:47:41` and id `28:ce:15:2f:76:6f`;
    - Key Mapper's uinput copy, `event17`, has the same name, bus, vendor and product, but an **empty** id.
  - The probe picks the one with an id.
- **The exclusive hold:**
  - `EVIOCGRAB` is issued through the platform's own `android.system.Os.ioctlInt(FileDescriptor, int)`, reached by reflection; it's a hidden API, usable from an `app_process` process.
  - That call passes a non-null pointer, which evdev treats as "grab".
- **The runner:** `tools/spike/padpen-probe/run_spike.sh`.
  - It pauses Key Mapper with its own `ACTION_PAUSE_MAPPINGS` broadcast, which makes it release the pen.
  - It logs window focus every 0.4 s.
  - It runs the probe for at most 600 s.
  - It **always** resumes Key Mapper on exit.
- **Logs:** `docs/device/raw/spike-live1/` and `spike-live2/`, git-ignored.

## Results

| Check | Result | Evidence |
|---|---|---|
| The hold is exclusive, and the instrument can fail | **Yes.** With Key Mapper holding the pen, the probe's `EVIOCGRAB` was refused: `EBUSY (Device or resource busy)`. | dry run, 22:5x |
| We can hold the pen ourselves | **Yes.** With Key Mapper paused: `opened /dev/input/event15 grabbed=true`. `/proc/<probe pid>/fd` showed the probe holding `event15`, and Key Mapper's helper held no input device. | spike-live1, spike-live2 |
| Every gesture reaches us | **Yes.** In about 30 s of your pressing (23:00:21–23:00:49): **21 squeezes, 5 slide ups, 2 slide downs, 8 double taps**, each with down and up. | `spike-live2/probe.log` |
| HyperOS doesn't react | **Very likely, not separately proven.** No window-focus change at all during the gestures (`focus.log`: last change 23:00:12, before the first squeeze). Kernel `EVIOCGRAB` semantics deliver a grabbed device's events only to the grabbing file, so HyperOS's input reader gets nothing. **Caveat:** the focus monitor wasn't validated against a control (one squeeze with nothing holding the pen). | `spike-live2/focus.log` |
| We can inject actions | **Yes.** Each of the 8 double taps injected VOLUME_DOWN and then VOLUME_UP (net zero). | `probe.log` |
| Survives a pen reconnect | **Not tested.** No Bluetooth toggle happened during the window. The code path exists (read fails, then wait for the pen, then grab again) but is unproven. | — |
| Your setup restored | **Yes.** After each run Key Mapper was resumed, held `event15` again, and `tools/pen-health.sh` printed "OK: Expert mode (pid 9660) holds /dev/input/event15". | 23:09:46 / 23:10 |

## Problems met, and fixes now in the repo
- **Duplicate adb transports.** Toggling wireless debugging leaves several mDNS transports to the same tablet, so every `adb` call fails with "more than one device".
  - `tools/env.sh` `pad_require_device` now picks one when they all report the same `ro.serialno`.
  - It also pins `ANDROID_SERIAL` even when there is only one, because duplicates can appear mid-script.
- **Log buffering.** `tr` block-buffered the live log in a pipe, so lines only appeared on exit. The runner now uses an unbuffered `perl` filter.

## What a PadStudio pen engine needs (after the UI)
1. **The reconnect test** (Bluetooth off and on) and the HyperOS-reaction control: a squeeze with nothing holding the pen should show HyperOS's panel in the focus log.
2. **Starting the helper from the tablet, not the Mac:**
   - PadStudio pairs with the tablet's own wireless debugging once (the pairing-code flow Key Mapper and Shizuku use);
   - starts `app_process` with its dex;
   - re-arms after a reboot when Wi-Fi connects (`WRITE_SECURE_SETTINGS`, granted once over adb).
3. **The action runner:** key injection (shutter = volume down, page turns, media), screenshot, back, home, recents, and app launches, with per-app rules from the foreground app.
4. **Pen status the app can actually observe**, replacing plan §7's "can't be checked from the tablet".
5. Retire Key Mapper only after this works for a week of normal use, and uninstall it only with your OK.
