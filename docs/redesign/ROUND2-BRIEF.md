# PadStudio 2 — UI round 2 brief (for the 8 parallel agents)

**Read first:**
- The plan, `/Users/charantejapampana/.claude/plans/i-want-you-to-distributed-wilkinson.md`. Its §1 table quotes the owner's 13 requests verbatim.
- `docs/redesign/SCREENS-BRIEF.md` and `APP-BRIEF.md`. Their rules still apply, except where this brief changes them.
- The owner's screenshots of the current build: `docs/test-logs/2026-10-12-tour/`. `00-overview.png` is one page.

## Base: the Step 0 commit named in your prompt

Step 0 already contains everything shared, so you don't build your own versions:

- **Type and spacing scale** (`padstudio/design/tokens.json` → `Tokens.kt`):
  - display 30/38, headline 24/32, title 18/26, titleMedium 15/22, body 15/22, bodyMedium 13/20, label 12/16, labelSmall 11/16;
  - Gutter 32, CardPadding 24, GapS 12, GapM 20, GapL 32 (dp).
  - `StudioTypography` maps them (see the table in `ui/Theme.kt`).
- **Font:** Manrope, bundled in `res/font`. `StudioFont` and `studioFontFamily()` are in `ui/Theme.kt`. A debug build can switch typeface with `--es font manrope|jakarta|outfit|reset`.
- **Glass:** `ui/components/Glass.kt` has `GlassSurface(modifier, shape, alpha, content)`, `AmbientBackdrop(modifier, content)` and `GlassDefaults` (alpha 0.62 dark / 0.70 light, `OVER_CONTENT_ALPHA` 0.92, a 1 dp highlight edge, `Shape` at 28 dp).
- **Control sizes:**
  - `SegmentedChoiceDefaults` (`MinSegmentWidth`, `SegmentChrome`, `requiredWidth(widest, count)`). Screens decide whether a segmented control fits with this, never with copies of Material's sizes.
  - `BindingChipDefaults.Shape` / `Corner`.
- **`PenModelView`:**
  - New last parameter `onBusyChange: (Boolean) -> Unit`. The Step 0 stub reports busy for the clip's nominal 1.7 s; B1 replaces it with the zoom timeline.
  - `PenModel.markers` includes `marker_top`, the end cap. The shipped GLB doesn't have it yet; B1 adds it. Screens fall back to `marker_double_tap` when it's missing.
- **About:** `Tab.ABOUT` is in the rail and opens `AboutScreen`. Setup no longer has an About card, `scrollToAbout` or a licences dialog.
- **Pen wording:** "N controls recorded" means the pen's controls PadStudio knows (`PenStatus.configuredText`). "N of M have actions" means the actions chosen, in the Pen hero card (`controlsSetLine`). The two are never both called "controls set".

## Gesture markers (round 2; B1 builds them, everyone computes tests from this table)

Positions are in metres in pen space: +Y runs toward the end cap and the nib apex is at Y = −0.0825. The pen is 0.165 m long with a 0.005 m barrel radius. Gesture markers sit on the barrel surface toward the camera (+Z = 0.005).

| Marker | From the nib apex | Position (x, y, z) |
|---|---|---|
| `marker_nib` | 0 | (0, −0.0825, 0) |
| `marker_slide_bottom` | 26 mm | (0, −0.0565, 0.005) |
| `marker_squeeze` | 32 mm | (0, −0.0505, 0.005) |
| `marker_double_tap` | 44 mm | (0, −0.0385, 0.005) |
| `marker_slide_top` | 48 mm | (0, −0.0345, 0.005) |
| `marker_top` | 165 mm | (0, +0.0825, 0) |

- The grip band, where every gesture happens, runs from 24 to 50 mm above the nib. The slide strip covers it on +Z.
- During a gesture, B1's view zooms to frame the nib-to-60 mm band. `write` doesn't zoom.

## Rules for round 2 (in addition to SCREENS-BRIEF)

1. **Only your files.** Your files are listed in the plan (§4, your letter).
   - Don't edit `:core`. Step 0 made the only `:core` change.
   - Only B3 edits `AndroidManifest.xml`.
2. **Spacing:**
   - In your files, a hard-coded padding or gap that equals an old token value (24, 20, 16, 12 dp) becomes the token.
   - Behaviour thresholds and widget metrics are never swapped for tokens: `STEADY_THRESHOLD`, `MOVE_THRESHOLD`, `GroupPlanner`'s fit rule, the 48 dp touch target.
3. **Callouts:** `onBusyChange` is the only signal for hiding callouts. While it's true, don't place, re-fit or step layouts down.
4. **Look:** our own controls, not stock Material: glass, the pill toggle, glass chips. Still Material 3 1.4.0 public APIs only, with nothing Expressive and no new libraries.
5. **Copy:** plain words, and still §7-truthful: never "verified", "running", "working" or "Expert mode on".
6. **Accessibility:**
   - Touch targets of at least 48 dp.
   - A `contentDescription` on every icon-only control.
   - State is never shown by colour alone.
   - Nothing clips or breaks a word mid-word at 200 % text with Bold. Decide layouts by measured text, not by font scale.
7. **Checks before you report:**
   - `tools/lib/gradle-slot.sh -q :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` passes;
   - lint has 0 errors and only the existing `UnusedAttribute` warning;
   - there are no Kotlin warnings;
   - any new logic has a JUnit test that you've shown can fail.
8. **Commits:**
   - Commit on your branch only, staged by path, with `git -c user.name=charanteja0017 -c user.email=61497314+charanteja0017@users.noreply.github.com commit`.
   - End the message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
   - Never push. No adb: the orchestrator installs and screenshots on the tablet.
9. **Report:**
   - branch and SHAs;
   - files;
   - for each of the owner's requests you own: what changed, with file:line;
   - checks, with real numbers;
   - what you couldn't do and why;
   - open issues.
