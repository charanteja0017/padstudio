# ADR-005: The home-screen grid on this tablet (gate G5)

**Status:** decided 11 Oct 2026. **Outcome: G5-B.** HyperOS on this tablet has no grid-size setting.

## Context
You asked to change the grid size on the home screen. HyperOS's launcher (`com.miui.home` 4.50.0.1182) keeps its grid in private storage, and there is no grid key in system settings (checked with `settings list system|secure|global`). So no app can set the grid without root.

The redesign plan (§6a) therefore measured what HyperOS itself offers, before designing PadStudio's "Home screen grid" card.

## What was checked
On 11 Oct 2026, after you had unlocked the tablet and it was on the home screen, I opened the screens the launcher exposes to other apps, took screenshots, and changed nothing.

| Screen (intent) | Options shown, verbatim | Screenshot |
|---|---|---|
| `com.miui.home.Setting` → `MiuiHomeSettingActivity`, titled "Home screen" | **Home screen settings:** "Home screen: With App drawer", "Double tap an empty spot on the Home screen to lock" (off). **Home screen layout:** "Fill cells of uninstalled apps" (off), "Lock Home screen layout" (off), "Don't show text" (on). **Other:** "System navigation: Gestures", "Blur preview for Recents". The whole page fits on one screen. | `docs/test-logs/2026-10-11-g5-home-settings-1.png` |
| `com.miui.home.action.ICON_CUSTOMIZE` → `IconCustomizeActivity`, titled "Background colour" | "Background colour: Change background colour for compatible icons". Nothing else. | `docs/test-logs/2026-10-11-g5-icon-customize-2.png` |
| The "Home screen" row → `AllAppsSettingsActivity`, titled "Home screen" | Mode "Classic" or "With App drawer" (selected); "App suggestions" (on); "Manage app categories"; "Backgrounds: Dark"; "Scroll bar: Original layout"; "Place new apps on the Home screen" (on). | `docs/test-logs/2026-10-11-g5-app-drawer-settings.png` |

None of these pages has a columns × rows or icon-size setting.

## Today's grid (recorded baseline)
- **6 columns × 4 rows, landscape, names hidden.**
- **How it was measured:** icon centres on `docs/test-logs/2026-10-11-g5-home-baseline.png` (3200 × 2136):
  - columns start at x = 570 px with a 411.5 px pitch; rows start at y = 320 px with a 413 px pitch;
  - six columns give symmetric margins (570 px left, 572 px right);
  - a fifth row would overlap the app-drawer handle;
  - the occupied lattice points match what's on screen: row 1 holds 5 icons; row 2 holds Key Mapper and PadStudio.
- **Not measured:** portrait.
- **Stored for the app:** `docs/device/home-grid.json`, pushed through `tools/facts.sh` as the `homeScreen` block of `device-facts.json`.
- **How the app may present it:** only as a **recorded baseline** with its date and source. PadStudio can't read HyperOS's grid (plan §7).

## Decision
1. The "Home screen grid" card (plan §6a, outcome G5-B) shows four things:
   - the line "HyperOS on this tablet has no grid setting";
   - the recorded baseline, "Recorded 11 Oct: 6 × 4, names hidden";
   - the nearest HyperOS options as links: Don't show text, Lock Home screen layout, Classic or App drawer mode, and icon background colour, opened through `com.miui.home.Setting` and `com.miui.home.action.ICON_CUSTOMIZE`;
   - PadStudio's own widget grid (plan §6b) as the way to a denser home page.
2. PadStudio never claims to know the live grid. If you change HyperOS's mode, ask me to re-measure.

## Side observation, for plan D12
The stale grid-and-pencil PadStudio icon that sat in the top row earlier is no longer on the home screen. PadStudio now appears at row 2, column 5, with the icon installed at 17:47 (the violet stroke).

So D12's test changes. After installing versionCode 2, check that **this** home-screen icon and the dock icon both change to the interim icon.
