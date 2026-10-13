# PadStudio 2 screens: brief for the 8 parallel screen agents (plan Step 4, W3)

**Read first:**
- `docs/redesign/APP-BRIEF.md`: the hard rules, the facts about the code, and the W2 contracts. All of it still applies.
- The plan, `/Users/charantejapampana/.claude/plans/i-want-you-to-distributed-wilkinson.md`: §4 (behaviour contract), §6, §7 (truthful status), §10 (your screen), §13 ("Editing and saving" and the acceptance rows for your screen).
- Your mock-up in `docs/redesign/` (the visual spec) and `docs/redesign/reference-today.png` (the house look). Open both with Read.
- `padstudio/app/src/main/kotlin/com/charan/padstudio/ui/components/Components.kt`. Its KDoc header is the component API; use only what it lists.

## Your base
- You work in your own worktree, on branch `redesign/screen-<name>`.
- It starts from the W2 merge commit named in your prompt. That commit has:
  - **the theme:** `Tokens`, `PadStudioTheme`, and `matchWallpaperColours`;
  - **the config:** the new `StudioConfig` fields and the `MainViewModel` functions;
  - **from :core:** `GridSpec`, `GroupPlanner.layoutFor`, `GridSpec.resolve`, `FactsFile.homeScreen` and `PenStatus`;
  - **the UI pieces:** `Sym` and the components;
  - **the shell:** with `Tab.TODAY`;
  - **the 3D pen:** `PenModelView`.

## Rules for every screen agent
1. **Touch only the files in your row of the table below.** You may add new files only inside your own package folder, as your row allows. Never edit:
   - `MainActivity.kt`, `MainViewModel.kt`, `PadStudioShell.kt`, `Theme.kt` or `Tokens.kt`;
   - `Symbols.kt` or `ui/components/*`;
   - `:core`, `AndroidManifest.xml`, `build.gradle.kts`, or another agent's files.
2. **When you need something from those files:**
   - Derived state goes in extension functions or small state holders inside your own package, for example `fun MainViewModel.todayHero(...)` in `today/TodayState.kt`. Read the ViewModel's public StateFlows.
   - If something genuinely can't be done without a shared change, write it under **Open issues** in your report and use the closest workable stand-in. Don't edit the shared file.
3. **Keep every public symbol other files use, with the same signature,** even when your new screen stops using it. The merger deletes dead code afterwards. Known ones:
   - `Section(title, content)` in `pen/PenStudioScreen.kt`;
   - `AppGrid`, `AppIcon`, `MoveDialog` and `moveApp` in `home/AppGrid.kt`;
   - `FolderPanelActivity.EXTRA_GROUP`;
   - `GroupWidget.GROUP`, `DENSITY` and `GRID`;
   - `WidgetSize`;
   - `PenReadout` and `pressureLevels` in `PenTestActivity.kt`.
4. **Keep your entry composable's signature exactly as the shell calls it.** Check the call in `MainActivity.kt` / `PadStudioShell.kt` at your base commit. Exception: the Setup agent adds `scrollToAbout: Boolean = false`, and the merger wires it.
5. **Protected behaviour (§4):** don't change what any existing button does. Re-skin it, re-word it and move it, but it must call the same ViewModel function with the same arguments. These must keep their behaviour exactly:
   - recording,
   - Send to Key Mapper,
   - pin widget,
   - icon style,
   - wallpaper,
   - move,
   - reset,
   - density,
   - the pen probe,
   - Reset.
6. **Look and feel:**
   - Use `ScreenScaffold` for the title, one-line subtitle and top-right action, with content at most 1040 dp wide and 24 dp gutters.
   - Use `StudioCard` for every surface, the `Sym` icons, colours from `MaterialTheme.colorScheme` only, and text from `MaterialTheme.typography` only.
   - At 840 dp and wider, use the list-detail panes your mock shows; below that, a single column. Measure with `BoxWithConstraints`.
   - Nothing from Material 3 Expressive.
7. **Copy:**
   - Plain language and sentence case, matching the mock wording.
   - Status words come from `PenStatus` (§7). Never write "verified", "running" or "Expert mode on".
   - Every action has these states (§13): pending ("Opening Key Mapper…"), success (a snackbar with the time), failure (a plain reason plus Fix or Retry), and unknown ("Requested").
8. **Accessibility:**
   - Every touch target is at least 48 dp.
   - Icons that do something get a `contentDescription`; decorative ones get null.
   - State is never shown by colour alone: use an icon and words.
   - Layouts must survive font scale 2.0 with Bold text, so use no fixed heights on text containers. Text scales; padding doesn't.
   - State that should survive recreation uses `rememberSaveable`: open sheets, pickers and the selected item.
9. **Checks before you report:**
   - `tools/lib/gradle-slot.sh -q :core:test :app:assembleDebug :app:lintDebug` passes from your worktree.
   - Lint has 0 errors and no new warnings. Compare against the base commit's count, which your prompt states.
   - Report the real numbers. No adb: the orchestrator installs and screenshots on the tablet after the merge.
10. **Commits:**
    - Commit on your branch only, staged by path.
    - Author with `git -c user.name=charanteja0017 -c user.email=61497314+charanteja0017@users.noreply.github.com commit`.
    - End the message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
    - Never push.
11. **Report:**
    - branch and commit SHA(s);
    - files changed;
    - every `MainViewModel` function and StateFlow you call;
    - what from the mock you could **not** build, and why;
    - checks with real numbers;
    - open issues.

## Who owns what

| Agent | Owns (edit or create) | Spec | Notes |
|---|---|---|---|
| **Today** | `today/*.kt`, replacing W2's placeholder `TodayScreen.kt` | `mock-today.html`, `reference-today.png`, §10 Today, §7 | **Hero:** a dark brand card with `PenModelView` and callout pills placed at the marker offsets (squeeze, slide up, slide down, double tap: action names from the current bindings). The headline comes from `PenStatus`. **Quick actions:** Test pen (`PenTestActivity`), Pause or Resume pen (`setPenEnabled`), Send to Key Mapper (`applyToKeyMapper`, then `onStart`), Open library (`LibraryActivity`). **Cards:** pen per app (app icon stacks per context), home groups (a mini widget drawn with `layoutFor`), looks (current icon style and wallpaper swatch), health (`ProgressRing` "N of 8 ready" plus the top issue and its Fix). Cards open their tab through `onOpenTab`. |
| **Pen** | `pen/PenStudioScreen.kt`, new `pen/screen/*.kt` | `mock-pen.html`, `pen-motion-kit.html`, §10 Pen, §13 recording | **Left pane:** pen kind (`SegmentedChoice`), the setup-mode chip, contexts with app icon stacks. **Right pane:** `PenModelView` with callouts and `BindingChip`s (a 2 × 3 grid for two-button pens), and the "Needs a look" chip. **Action picker:** a side sheet at 840 dp and wider, a bottom sheet below that. It shows 25 actions in groups (Camera, Reading & notes, Media, Navigation, System), and "Unavailable: needs …". **Advanced** (`Disclosure`): Basic vs Expert, key injection, the notes app with "None", and Developer details (codes, devices, Record, the profile hash, raw warnings). **Recording:** a 15 s countdown card. Changing tab or pressing Back cancels it through `cancelCapture`. On timeout: "Nothing pressed · Try again". `MainActivity.dispatchKeyEvent` stays the input path. |
| **Home Kit** | `home/HomeKitScreen.kt`, new `home/kit/*.kt` | `mock-homekit.html`, §6a, §6b, §10 Home Kit | **"Home screen grid" card**, outcome G5-B, wording from `docs/decisions/ADR-005-home-grid.md`. It shows the recorded baseline from `studio.pen.factsFile()?.homeScreen` ("Recorded 11 Oct: 6 × 4, names hidden"), "HyperOS on this tablet has no grid setting", and links to `com.miui.home.Setting` and `com.miui.home.action.ICON_CUSTOMIZE` through `onStart`-style intents (resolve them first; if they're missing, show a disabled reason). **Default widget grid picker:** Auto, 6 × 3, 8 × 4, 10 × 5, Custom (columns 4–10, rows 2–8). It shows a live preview using your real icons and `layoutFor`, and the line "Applies to all widgets set to Default (N)". It calls `setDefaultWidgetGrid`. Also: density for Auto, group cards with icon previews, Add widget S/M/L (`pinWidget`) and Open, Organize (two panes, `move`/`MoveDialog`), and Hidden last. |
| **Looks** | `looks/LooksScreen.kt`, new `looks/screen/*.kt` | `mock-looks.html`, §10 Looks | Icon-style previews on 12 of your apps (`setIconStyle`). The icon-pack picker (`iconPacks()`, refreshed on resume). **"Match wallpaper colours"** (`setMatchWallpaperColours`). The wallpaper preview with Shuffle and Apply to Home, Lock or Both (`applyWallpaper`; failure shows a plain reason plus Retry). |
| **Setup + About** | `setup/SetupScreen.kt`, `ui/AboutScreen.kt`, the **UI only** of `PenProbeActivity` in `setup/SetupActivities.kt` (class names unchanged; `ResetActivity` untouched) | `mock-setup.html`, §10 Setup | `ProgressRing` "N of 8 ready" over the existing 8 `health` checks, grouped Pen, Home, Looks. Each check in plain words with its existing Fix intent. **Developer:** the pen probe; facts via `tools/facts.sh` (correct the old `probe-device.sh` copy). **About** is a section at the end. Add `scrollToAbout: Boolean = false`, which scrolls there on first composition. |
| **Pen test** | `pen/PenTestActivity.kt` | `mock-pentest.html`, §10 Pen test, §13 Pen test row | **Canvas plus instruments:** pressure arc, peak, levels, tilt dial, hover, rotation, rate and sparkline. Our own floating toolbar. **Performance:** graphs redraw at 30 Hz at most on their own layer (`graphicsLayer` or a separate View). The canvas uses `MotionEvent` historical points. No allocation per event in the draw path. |
| **Panels** | `home/Panels.kt`, `home/AppGrid.kt` | `mock-panel.html`, §10 Panels, §6b (Edit widget) | **Frosted sheet:** keep `blurOrDimBehind()`, 28 dp corners. Search, a dense grid, and the Library group chips. **Edit widget:** the current group; the grid picker (Default (shows what it resolves to), Auto, 6 × 3, 8 × 4, 10 × 5, Custom) with **disabled options showing the fit reason from `layoutFor`** for the widget's current size (read with `AppWidgetManager.getAppWidgetOptions`; say where the size came from); density. Write the per-widget value under `GroupWidget.GRID` as `"default"`, `"auto"` or `"CxR"`, then update that widget. `LibraryActivity`, `FolderPanelActivity` and `EditGroupWidgetActivity` keep their class names. |
| **Widget** | `home/GroupWidget.kt`, `home/WidgetPinning.kt`, `res/xml/group_widget*.xml`, new `res/layout/widget_preview_*.xml`, new `res/drawable/widget_*`, and `res/values/strings.xml` (widget descriptions only) | `mock-widget.html`, §6b, §10 Widget | **Grid choice:** `GridSpec.resolve(state[GRID], config.widgetGrid)` feeds `GroupPlanner.layoutFor(LocalSize, …)`. `spec == null` must render **exactly** as today (`gridFor`). **When trimmed:** the header note "8 × 4 doesn't fit · showing 6 × 3". **"+N"** goes in the last cell, as a 48 dp target. Colours come from the brand surface, or from wallpaper colours when `matchWallpaperColours` is on. Also: the system corner radius, the header pill, designed empty states ("Choose a group"; "No apps in Games right now · Edit"), a placeholder tile with the app's initial for a missing icon, and `previewLayout` plus `previewImage` for the system widget picker. Keep `SizeMode.Exact`, the receivers, `GROUP`/`DENSITY`, and `EXTRA_GROUP`. Labels use `TypedValue.applyDimension(COMPLEX_UNIT_SP, 11f, …)`, never `fontScale` multiplication. |

## After you: review and merge (the orchestrator runs these)
- **Review:** each branch gets one reviewer agent covering protected behaviour, accessibility, copy, and the 125 % / 200 % plus bold layout reasoning, then at most one fixer.
- **Merge:** a merger brings the green branches together in this order: Widget, Panels, Home Kit, Looks, Setup, Pen test, Pen, Today. It builds after each one.
- **On the tablet:** I install the result under the tablet rule and screenshot every screen. Then I compare each against its mock and check the profile SHA-256 against `cdbc98bc…`.

## W2 facts you code against (filled in as the foundation agents report)

### From :core (task B, commit `05634e7`)
- **`GridSpec(columns, rows)`.**
  - `toString()` gives `"8x4"` (the stored form); `label` gives `"8 × 4"` (for display).
  - The constructor **throws** outside 4–10 × 2–8, so saved strings go only through `GridSpec.parse(String?)` / `GridSpec.resolve(perWidget, defaultSpec)`. Both return null for Auto, or for anything invalid.
  - Also: `GridSpec.PRESETS` (6x3, 8x4, 10x5), `COLUMNS`, `ROWS`, `AUTO`, `DEFAULT`.
- **`GroupPlanner.layoutFor(widthDp, heightDp, density, spec, labelLineDp): WidgetLayout`.**
  - Pass the **whole** widget size (Glance `LocalSize`), not size − 20. `layoutFor` subtracts the padding and header itself.
  - With `spec == null` it equals today's `gridFor(w − 20, h − 20, density)` exactly.
  - `WidgetLayout` has `columns`, `rows`, `cellWidthDp`, `cellHeightDp`, `iconDp`, `showLabels`, `fits`, `reason`, `trimmedFrom`, `note` ("8 × 4 doesn't fit · showing 6 × 3") and `overflow(appCount)`.
  - **Never build a `GridSpec` from a trimmed layout's columns and rows.** They can fall below 4 × 2, and the constructor throws. Use `note`.
- **Other helpers:**
  - `GroupPlanner.overflow(appCount, columns, rows)` returns `Overflow(shown, more)`. The "+N" tile is the last cell.
  - `GroupPlanner.largestFit(w, h)` gives "Up to 8 × 5" in Custom.
  - `widthNeededDp(c)` / `heightNeededDp(r)` give refusal text such as "10 columns need a 500 dp wide widget".
- **`labelLineDp`** (for the app to compute): `TypedValue.applyDimension(COMPLEX_UNIT_SP, 11f, dm) / dm.density`. That uses Android's nonlinear font scale, never a `fontScale` multiplication. Log invalid saved grids with `Log.w` (core has no logger).
- **The mock's numbers aren't the source of truth:** `mock-panel.html` says "Auto: 6 × 4" for a 412 × 300 dp widget at Standard, but the real rule gives 6 × 3. Always show `layoutFor`'s numbers.
- **`FactsFile.homeScreen: HomeScreenFacts?`:**
  - fields `columns`, `rows`, `namesHidden`, `orientation?`, `recordedAt`, `source`, `hyperosGridSetting: Boolean?`, `hyperosOptions`;
  - a malformed block reads as null;
  - in the app: `context.studio.pen.factsFile()?.homeScreen`.
- **`PenStatus(listening: Boolean?, sentHash, planHash, appliedAt: Long?, penEnabled)`** (`core.pen`).
  - **Inputs:**
    - `listening` from `KeyMapperBridge`;
    - `sentHash` = `config.appliedProfileHash`;
    - `planHash` = `plan?.hash`;
    - `appliedAt` and `penEnabled` from `StudioConfig`.
  - **Rows**, each an enum with a `Tone` (GOOD/WARN/BAD/UNKNOWN; map these to `StatusPill`): `configured`, `sent`, `keyMapper`, `penActions`, `expertMode`, `working`.
  - **Words:**
    - `configuredText(penName, mode, controlsSet)`
    - `sentText(formatTime)`: the formatter gives "14:05", plus the day if it wasn't today, and follows the device's 12/24-hour setting (`DateFormat.getTimeFormat(context)`)
    - `keyMapperText`, `penActionsText`, `penActionsLine`, `expertModeText`
    - `headline`, for the Today hero
    - `needsSend`: show Send when true
  - Use these strings as they are. Don't write your own status words.

### From the preview mock-ups (W1 agents' notes that apply to the app)
- **Callouts at large text (Pen, Today).** At 200 % text, four callout chips can't fit beside the pen. When the chips would overlap each other or the card header, show the bindings as a list (`BindingChip` rows) next to a smaller pen. Measure with `BoxWithConstraints` plus the actual chip widths, not a hard-coded font-scale threshold.
- **Callouts during the `write` clip.** The pen tilts 22° on its nib, and `marker_double_tap` drifts up to 56 mm. Hide the callout pills while `write` plays, or tie them to the ink overlay. In the other clips, drift is ≤ 2 mm.
- **The marker layout is a design choice,** not a measurement of the real pen's gesture zones. The model is a placeholder until the owner sends theirs (same marker names). Never hard-code marker screen positions: always use `PenModelView`'s `onMarkers` offsets.
- **Pen context titles.** The core title "Notes with their own pen settings" wraps to four lines at 125 % plus bold. The screen may show "Own pen settings" with the supporting line "Leaves the pen to the app". That's a display string only: `:core` labels must not change.
- **The action picker keeps the "Nothing (leave to the app / HyperOS)" choice** the current app has.
- **The Notes context really has no pen actions** in the Focus Pen Pro defaults. Today's "Pen by app" card says "No pen actions set yet" for it. That's truthful; don't hide it.
- **The mock's numbers are examples.** "Widget layout 4 × 3", the "Needs a look" chip on Reading & PDF, and "Sent 14:05" are examples. Show the real state, and leave out the chip when core has no warning.
- **Wallpaper.** The Today mock's violet-dusk landscape is the reference's look; the app's `WallpaperGenerator` draws something else. Show a thumbnail of the user's real current wallpaper choice, and don't change the generator in W3.
