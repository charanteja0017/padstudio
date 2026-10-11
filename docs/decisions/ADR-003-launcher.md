# ADR-003 — Launcher: stay on the HyperOS launcher (gate G2)

Date: 2026-10-11 · Status: accepted · Evidence: `docs/test-logs/2026-10-11-lawnchair-*.png`, `…-home-restored.png`

## What was measured (Lawnchair 15 Beta 3, `app.lawnchair`, on HyperOS 3.0.303 / Android 16)

| Check | Result |
|---|---|
| Lawnchair made the default home (`cmd package set-home-activity`) | HyperOS switched to 3-button navigation at once: `navigation_mode` 2 → 0, `force_fsg_nav_bar` 1 → 0 |
| Recents (nav-bar square, and `KEYCODE_APP_SWITCH`) | Nothing opened; focus stayed on Lawnchair |
| `settings put global force_fsg_nav_bar 1` with Lawnchair still home | Gesture handle back, `navigation_mode` 2 |
| Swipe-up-and-hold Recents under Lawnchair with gestures forced on | Not tested |

## Decision

The owner tried Lawnchair and rejected it ("i hate it"). It was uninstalled the same day; the HyperOS
launcher is home again with gesture navigation (`navigation_mode` 2, `force_fsg_nav_bar` 1). This
confirms D2: PadStudio improves the stock launcher (group widgets, panels, icons, wallpaper) instead of
replacing it, and **PadHome (M5b) is not built**. Lawnicons stays installed: PadStudio's themed icons use it.

`tools/rollback.sh` now restores `force_fsg_nav_bar` and `navigation_mode` after putting the stock
launcher back, because HyperOS changes them whenever another launcher becomes home.
