# PadStudio 2 app build: brief for parallel agents (plan Step 3)

**Read first:**
- the plan, `/Users/charantejapampana/.claude/plans/i-want-you-to-distributed-wilkinson.md`, especially §3, §4 (behaviour contract), §6, §7, §10, §11 and §13;
- the owner's reference design, `docs/redesign/reference-today.png` (open it with Read);
- the preview mock-ups `docs/redesign/mock-*.html`, which are the visual spec;
- `docs/redesign/pen-motion-kit.html`, the gesture language.

## Hard rules
- **Your files:** each agent works in its own git worktree on its own branch (`redesign/<task>`) and touches **only the files its task owns**. If you need something from another task, use the contract below, never its files.
- **Builds:** only through `tools/lib/gradle-slot.sh <tasks>`, at most 2 builds at once across all agents. From a worktree, run the worktree's own copy.
- **Commits:** your own branch only, staged by path (never `git add -A`). End every message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never push.
- **Off limits:**
  - No adb; the orchestrator owns the tablet.
  - Never rename the protected names (§4): `Tab` enum names, `.setup.ResetActivity`, `.home.LibraryActivity`, the widget receivers, Glance keys `group`/`density`, `EXTRA_GROUP`, existing `StudioConfig` fields, `:core` labels (they feed the Key Mapper JSON).
  - Keep `MainActivity.dispatchKeyEvent`/`onStop` pen recording intact.
- **Must stay green:** `:core:test` (incl. `ProfileSnapshotTest`), `:app:assembleDebug`, `:app:lintDebug` (0 errors, no new warnings).
- **Return a structured report:** branch, commit SHA, files, the API you exposed, checks run with real results, and open issues. Never claim a check you didn't run.

## Facts about the current code (from the code inventory, 11 Oct)
- **Navigation:** `MainActivity.kt` has `private enum class Tab` (PEN, HOME, LOOKS, SETUP, ABOUT) and `rememberSaveable { Tab.PEN }`.
  - At ≥ 600 dp it shows `NavigationRail` + `VerticalDivider`; below that, `NavigationBar`.
  - Debug extras live at `MainActivity.kt:93-106` (debuggable only): `tab`, `open probe|pentest`, `apply keymapper`, `widgets update`, `dumpicons`.
  - The `tab` extra is matched case-insensitively against the enum *name* (`:96`, `:113`).
- **MainViewModel:**
  - StateFlows: `config`, `snapshot`, `plan`, `capturing`, `message`, `health`.
  - Functions: `refresh(force)`, `clearMessage`, `setKeyInjection`, `applyWhenReady`, `setPenMode`, `setPenKind`, `setNotesApp`, `startCapture`, `cancelCapture`, `onKeyEvent`, `forgetControl`, `setBinding(ctx, control, press, intent?)`, `resetBindings`, `applyToKeyMapper(): Intent?`, `setPenEnabled`, `setDensity`, `move(pkg, group)`, `resetOverrides`, `pinWidget(group, size)`, `setIconStyle(style, pack)`, `iconPacks()`, `applyWallpaper(seed, dark, accent)`, `dumpIcons`, `updateWidgets`.
  - `healthChecks()` produces the 8 checks at `:267-307`.
- **Other facts:**
  - `appliedProfileHash` is written when the profile is *handed* to Key Mapper (`:169`).
  - `setPenEnabled` only broadcasts.
  - `ConfigStore` JSON uses `ignoreUnknownKeys = true` and `encodeDefaults = true`.
- **Theme:** `ui/Theme.kt`'s `PadStudioTheme(content)` uses dynamic colour only. `themedIconColors(context)` is used by `IconRepository` and `GroupWidget`, so keep it.
- **Generated, do not hand-edit:**
  - `ui/theme/Tokens.kt` (via `padstudio/design/make_tokens.mjs`): `Tokens.lightScheme(p)` / `darkScheme(p)`, `BrandPalette`, type sizes, weights, corners, spacing, `Good`/`Warn`.
  - `res/drawable/sym_<slot>[_fill].xml` (`tools/lib/fetch_symbols.py`; the slot→name map is in `docs/redesign/assets/symbols/map.json`).
- **The pen model:** `app/src/main/assets/pen/focus_pen_pro.glb`.
  - Clips: `idle`, `squeeze`, `slide_up`, `slide_down`, `double_tap`, `write`.
  - Marker nodes, in metres: `marker_squeeze` (0, −0.01885, 0.005), `marker_slide_top` (0, 0.0203, 0.005), `marker_slide_bottom` (0, −0.0043, 0.005), `marker_double_tap` (0, 0.040, 0.005), `marker_nib` (0, −0.0825, 0).
- **Widget:** `home/GroupWidget.kt`, `SizeMode.Exact`, `GroupPlanner.gridFor(w, h, density, headerDp = 36)` caps columns at 10.
- **Facts:** `core/.../pen/FactsFile.kt` (`ignoreUnknownKeys`). `files/device-facts.json` now carries `homeScreen {columns: 6, rows: 4, namesHidden: true, orientation, recordedAt, source, hyperosGridSetting: false, hyperosOptions[]}`.

## Contracts between the parallel tasks
Code against these signatures; the merge brings them together.

1. **Theme and config (task A):**
   - `PadStudioTheme(content)` keeps its signature and package (`com.charan.padstudio.ui`). It reads `StudioConfig.matchWallpaperColours` and uses `Tokens` brand schemes unless that switch is on.
   - New `StudioConfig` fields: `penEnabled: Boolean = true`, `appliedAt: Long? = null`, `matchWallpaperColours: Boolean = false`, `widgetGrid: String? = null` ("8x4" or null = Auto).
   - New `MainViewModel` functions: `setMatchWallpaperColours(Boolean)` and `setDefaultWidgetGrid(spec: String?)`; both call `updateWidgets()`.
2. **Core (task B):**
   - `com.charan.padstudio.core.home.GridSpec(columns, rows)` with `GridSpec.parse(String): GridSpec?` and `toString() = "8x4"`.
   - `GroupPlanner.layoutFor(widthDp, heightDp, density, spec: GridSpec?, labelLineDp: Float): WidgetLayout`, where `WidgetLayout` holds columns, rows, cellWidthDp, cellHeightDp, iconDp, showLabels, fits, reason, trimmedFrom.
   - `FactsFile.homeScreen: HomeScreenFacts?`.
   - `com.charan.padstudio.core.pen.PenStatus`: a pure model of plan §7 (inputs: listening: Boolean?, sentHash, planHash, appliedAt, penEnabled; outputs: per-state enums plus plain copy).
3. **Components (task C):** `ui/Symbols.kt` (`object Sym { val today = R.drawable.sym_today; … ; fun filled(id) }`) and `ui/components/*`, listed in a KDoc header in `ui/components/Components.kt`. Other agents may only use what that header lists.
4. **Shell (task D):**
   - `ui/PadStudioShell.kt`: the expanded rail like the reference, with mark, wordmark, labelled items, and a bottom status slot `statusLine: @Composable () -> Unit`.
   - `Tab` gains `TODAY`, and `--es tab today` works.
   - New debug extras (debuggable only): `theme dark|light`, `fontscale <float>` and `boldtext on|off` (both via `attachBaseContext` Configuration overrides), `dump profile` (writes `files/profile-dump.json`) and `dump colors` (writes `files/colors.json`).
5. **3D pen (task E):** `pen/PenModelView.kt`:

   ```kotlin
   @Composable fun PenModelView(
       modifier: Modifier,
       clip: String = "idle",
       playing: Boolean = true,
       onMarkers: (Map<String, Offset>) -> Unit = {},
   )
   ```

   It loads the `.glb`, plays clips, pauses when not visible or under reduce-motion, projects the markers to screen offsets, and falls back to a 2D pen drawn from the motion kit if 3D fails. SceneView 4.53.0 (same Compose BOM). Measure and report the APK size delta.
