# ADR-001 — The device, as measured

Date: 2026-10-11 · Status: accepted · Evidence: `tools/probe-device.sh` over wireless debugging → `docs/device/PROFILE.md` (raw dumps in `docs/device/raw/20261011-151148`, local only)

## Facts (measured, not assumed)

| | |
|---|---|
| Tablet | **Xiaomi Pad 8**, model 25097RP43I, codename `yupei`, `yupei_in_global` (India) |
| Software | HyperOS **3.0.303.0.WPZINXM**, Android **16** (SDK 36), security patch 2026-07-01 |
| Display | 2136 × 3200 physical (portrait-natural), density 440 |
| Launcher | stock `com.miui.home` 4.50.0.1182; navigation mode 2 (gestures), `force_fsg_nav_bar=1` |
| Pen | **Xiaomi Focus Pen Pro** — three Bluetooth `uhid` input devices, vendor:product `0022:5081`: "Xiaomi Focus Pen Pro" (KEY_KPPLUS), "… Keyboard" (F13–F19, Page Up/Down, arrows, numpad), "… Mouse" (mouse buttons + REL axes for stylus-mouse) |
| Digitizer | "NVTCapacitivePenP81c" / "NVTCapacitivePenM80p" / "NVTCapacitiveTouchScreen" (bus 0x001c, TOUCH) — must never be grabbed by Expert Mode |
| Root needed for `getevent`? | No — readable over adb |
| HyperOS pen settings | `stylus_pinch_status=5`, `stylus_double_click_status=1`, `stylus_quick_note_screen_off=1`, `stylus_pinch_pressure_adjust=0`, `setting_stylus_version=8` |
| Themes app | **present, enabled and launchable** (`com.android.thememanager/.ThemeResourceTabActivity`, 3.0.5.85-pad-global) — contrary to the research report's expectation for Pads; what it offers on this build is checked next |
| Pen-relevant apps | Xiaomi Camera 6.3.006770.1, Mi Canvas (`com.miui.creation` 3.8.5), Mi Notes, Google Keep, freenote (`ai.note.notepad.memo.journal.freenote`), ReadEra; no Notein / Notewise / Flexcil |
| Accessibility (baseline) | `com.milink.service/…MLAccessibilityService`, `com.xiaomi.aicr/…SelectToSpeakService` |

## Consequences

1. Pen kind is **FOCUS_PEN_PRO** (squeeze + slides, no buttons) — PadStudio's `PenKind.FOCUS_PEN_PRO` profile applies.
2. Whether its gestures reach apps/accessibility (Basic) or only the kernel (Expert) is gate **G1**, measured next with `tools/capture-pen.sh` and PadStudio's Pen Probe.
3. Expert-mode triggers may only use the three `0022:5081` Bluetooth devices; the digitizers are excluded by `EvdevSafetyFilter` (they report `BTN_TOUCH`).
4. The Themes app's availability reopens D8 (system-wide theming): if its catalogue works on this build, official themes beat any workaround.
5. Pen contexts on this tablet: Camera = Xiaomi Camera; Notes = Mi Canvas, Mi Notes, Keep, freenote; Reading = ReadEra.
