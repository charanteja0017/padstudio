# Pad Suite: making the Xiaomi Pad's pen, home screen and looks actually good

## Context

**What you asked for:**
- The pen does almost nothing outside Xiaomi's own apps. It can't act as a camera shutter or a shortcut.
- The theme is ugly.
- Folders don't group apps well.
- App icons are spread far apart.
- Clone the open-source projects closest to this and start from them.
- Plan in detail. Decide everything; ask no questions.

**Where this comes from.** It builds on your research report (PDF). Everything was re-checked on 11 Oct 2026 against primary sources: GitHub source and API, developer.android.com, AOSP and androidx source. Three web research passes and a code-level feasibility review went into it, and I re-checked the facts the plan depends on myself. Anything not confirmed is marked *UNVERIFIED*. (Your PDF isn't on disk here; drop it into `docs/research/` if you want it versioned.)

### Facts that shape the plan

| Fact (source) | What it decides |
|---|---|
| Xiaomi pen buttons usually reach Android as **Page Up / Page Down** key events from the pen's Bluetooth link. Seen on Pad 6, Smart Pen 2 and 6S Pro (Key Mapper #1232, #1214; Krita MR 2597; Nomad Sculpt forum). | They can be remapped without root. |
| Smart Pen 2 by default: long-press top key = screenshot, long-press second key = Xiaomi's note app (mi.com FAQ). The Focus Pen's side buttons reportedly act as a **remote shutter in Xiaomi Camera** (TechAdvisor Pad 7 review). **No shutter was found for Smart Pen 2.** No HyperOS setting to remap pen buttons was found (*UNVERIFIED*). | The shutter has to work in *every* camera app and on both pens. |
| **India builds.** Pad 6 (`pipa`) ships Smart Pen 2, HyperOS 2 on **Android 14**, end of life. Pad 7 (`uke`) ships the Focus Pen, **HyperOS 3 / Android 16** (xmfirmwareupdater). | The device probe picks the branch; nothing is assumed. |
| **Key Mapper 4.6.1** (10 Oct 2026, GPL-3.0, release asset `keymapper-4.6.1-foss.apk`) supports: per-app key maps; groups with AND/OR constraints, including inverted "app NOT in foreground"; short, long and double press; screenshot, open camera, key events, screen taps and swipes, **clicking a recorded UI element**, launching apps and intents, media and navigation actions. | The pen engine already exists. |
| Key Mapper has an **exported restore screen** that accepts a JSON or zip backup from another app (`RestoreKeyMapsActivity`, VIEW `application/json`). It also has **exported broadcasts**: `ACTION_PAUSE_MAPPINGS` / `ACTION_RESUME_MAPPINGS`, and `ACTION_TRIGGER_KEYMAP_BY_UID`. Backups are versioned JSON (`db_version`, with migrations). | **Our app can generate the whole pen profile and hand it to Key Mapper in one tap**, and actions can be tested over adb. Forking is optional. |
| Key Mapper **Expert Mode** runs a helper with ADB-level access and exclusively grabs raw input devices, which can override Xiaomi's long-presses. Once paired it auto-restarts on Wi-Fi if `WRITE_SECURE_SETTINGS` is granted (`SystemBridgeAutoStarter.kt`). **Risk:** recording a trigger in Expert Mode grabs *all* input devices and filters only finger touches (`RecordTriggerController.kt`). A hovering pen could be recorded off the digitizer and then grabbed permanently. | Expert triggers are **generated from the probe and filtered to the pen's Bluetooth device**. They are never recorded in Key Mapper's UI. |
| **Any third-party default launcher forces HyperOS into 3-button navigation** ("Cannot use full-screen gestures with third-party launchers"). Lawnchair closed this as unfixable (#6065). The stock launcher is the only Recents provider. HyperOS 2+ may even refuse a third-party default launcher (Niagara help). | **Keep the HyperOS launcher.** We fix the look *on top of* it. A launcher fork is only a gated option. |
| Lawnchair on a Xiaomi tablet *also* spaces icons too far apart (#6924). Its only tablet grid is 6×5 with 64 dp gaps. It has no stable release. | Switching launcher isn't a free win anyway. |
| Lawnchair's **flowerpot** dataset (GPL-3.0-or-later) has per-category app lists (EDUCATION alone has 425) and intent-category rules (APP_BROWSER, APP_FILES, …). Its icon-pack code lives in `lawnchair/src/app/lawnchair/icons/iconpack/`. The standard icon-pack format is `org.adw.launcher.THEMES` + `res/xml/appfilter.xml`. | **Auto-grouping and icon styles start from this open-source code and data.** |
| Glance (Compose for widgets): at most **10 children per Row/Column**, about 500 views per layout, and fixed-column grids only allow **1–5 columns** (androidx `LazyVerticalGridTranslator.kt`). Widget clicks through Glance don't carry `sourceBounds` (from reading the source, not tested on a device; there's a fallback). You can't listen for app installs in the background (`ACTION_PACKAGE_ADDED`); you have to poll `getChangedPackages`. | Widget design in §7. |
| Android 14+ accessibility listening on the stylus input source **consumes** the events, which breaks writing (developer.android.com). | Pen buttons are caught as *key events* only. |
| **Open Camera** (GPL-3.0) takes a photo on `KEYCODE_CAMERA`, `SPACE`/`NUMPAD_5`, and volume or media keys when set to "take photo" (verified in its `MainUI.onKeyDown` source). | A guaranteed key-driven shutter camera. |
| **This Mac:** Apple M4, 16 GB RAM, macOS 27, **26 GB free**. SDK at `/opt/homebrew/share/android-commandlinetools` (android-37, build-tools 36/37, adb 37 not on PATH). JDK 17 and 27 installed. Gradle 9.8.1. `gh` logged in. **No tablet connected yet.** Your WiFiBar project (AGP 9.4.1, Kotlin 2.4.21, Compose, Glance, SDK 37, minSdk 31, JDK 17 pin, `com.charan.*`, `:core` + `:app`) already builds here. | The default path needs **no new toolchain**: PadStudio copies WiFiBar's setup. JDK 21, NDK and Rust are only installed if a fork is triggered. |

### Intended outcome (no root; one script undoes everything)
1. **Pen buttons do the right thing per app.**
   - Camera: **shutter**, in any camera app.
   - PDF readers: page turns.
   - Notes apps without pen support: undo/redo.
   - Video: play/pause.
   - Everywhere else: screenshot, back, open camera, or open your app library.
   - Notes apps that already support the pen (Notein, Notewise, Flexcil, Mi Canvas) are left to handle it themselves.
2. **A tidy, dense home screen on the stock launcher**, with gestures, the dock and split-screen kept.
   - Apps are **auto-grouped** into themed group widgets with tight icon spacing.
   - Tapping a group opens a folder-style panel.
   - The pen can open a searchable app library.
   - New apps file themselves into the right group.
3. **A coherent look:** themed icons (monochrome or an icon pack), generated matching wallpapers, consistent corners and colours.

---

## 1. Architecture

```
            ┌──────────────────────── PadStudio (NEW, our code, your house style) ────────────────────────┐
            │  Pen Studio: wizard + per-app profile matrix → generates Key Mapper profile JSON            │
            │  Home Kit:   categoriser (flowerpot + our India list) → group widgets, folder panels, library│
            │  Looks:      icon styles (monochrome / icon packs), wallpaper generator                     │
            │  Setup hub:  health checks, deep links, re-arm banner, probe import                         │
            └───────────────┬──────────────────────────────────────────────┬───────────────────────────────┘
          one-tap restore   │  (VIEW application/json)                     │ widgets placed on
                            ▼                                              ▼
            Key Mapper 4.6.1 (UPSTREAM, unmodified)          HyperOS launcher (STOCK, unmodified)
            runs the pen buttons: accessibility (Basic)      keeps gestures, dock, split-screen, Recents
            or Expert Mode (wireless-debugging helper)
                            ▲
            pen buttons ────┘  (Bluetooth key events: Page Up/Down …)

Gated forks (fully specified, built only on a measured trigger):
  PadPen  = fork of Key Mapper   → if gate G4 fails (generated profiles can't deliver a needed behaviour)
  PadHome = thin fork of Lawnchair → if gate G2 passes (third-party launcher keeps gestures/dock on this Pad)
```

## 2. Decisions made for you

| # | Decision | Why |
|---|---|---|
| D1 | **No root, unlock, flashing or debloating.** `tools/rollback.sh` restores the recorded baseline. | Not authorised. Root projects (FocusPen Pro X, HyperCeiler, HyperOS-Stylus-Remapper) are reading material only. |
| D2 | **We build one app, PadStudio** (GPL-3.0, so it can include Lawnchair's GPL flowerpot code). The engines stay upstream and unmodified: Key Mapper for the pen, the HyperOS launcher for home. | Least code, no fork maintenance, auto-updating engines, and gestures kept. |
| D3 | **Forks are gated, not skipped.** PadPen (Key Mapper fork) gets built if **G4** fails. PadHome (Lawnchair thin fork) gets built if **G2** passes. Both are specified in §8, and Key Mapper is cloned on day 0 regardless. | The cloned sources are the schema reference and the fork starting point. |
| D4 | **PadStudio is the single source of truth for pen mappings.** Profiles are imported into Key Mapper in **REPLACE** mode. PadStudio keeps every profile version, so nothing is lost. APPEND mode re-UIDs maps and would duplicate them. | Re-imports are deterministic and repeatable. |
| D5 | **Pen modes:** **Basic** (accessibility only) is the default and maps **short presses only**, plus double presses where Xiaomi doesn't react. **Expert** (Expert Mode) is enabled if G1 shows Basic can't see a button, or to take over Xiaomi's long-presses (quick note → *your* notes app). | In Basic, mapping only a long or double press would swallow the short press. Expert needs re-arming when Wi-Fi is off. |
| D6 | **Groups:** Study · Work · Reading · Create · Social · Watch & Listen · Shop & Pay · Games · Life · Tools · System · More, plus a **Hidden** group for unwanted preinstalled apps (instead of uninstalling them). | Fits Indian app ecosystems (UPI, OTT, edtech). |
| D7 | **Density presets for widgets and panels:** **Calm** (76 dp cell, labels), **Compact** (64 dp, labels; default), **Dense** (52 dp, no labels). Final numbers tuned from device screenshots. | This is the fix for "icons are spread out". |
| D8 | **Not built:** system-wide theming (Settings, Control Centre, lock screen, system font). | No reliable no-root route; the Themes app is region-gated and theme or font tools revert within 30–60 s. Documented as an optional experiment. |
| D9 | **Local only:** no GitHub forks, pushes or store uploads unless you ask later. | Outward-facing actions need your OK. |
| D10 | IDs: `com.charan.padstudio`, and if built `com.charan.padpen` / `com.charan.padhome`. Never run two key-filtering accessibility services at once. | Two of them would fight over the same press. |

## 3. What gets cloned, and why

| Repo | How | Licence | Used for |
|---|---|---|---|
| `github.com/keymapperorg/KeyMapper` @ `develop` | `forks/padpen`, `--depth 1` | GPL-3.0 | Schema reference for backup JSON (entities in `data/`, `backup/BackupManager.kt`, action, constraint and trigger models). PadPen fork base if G4 fails. |
| `github.com/LawnchairLauncher/lawnchair` @ `16-dev` | `reference/lawnchair-parts`, sparse checkout: `flowerpot/`, `lawnchair/src/app/lawnchair/{flowerpot,icons}/` | GPL-3.0-or-later (own files) / Apache-2.0 (Launcher3) | flowerpot data and parser, icon-pack parsing logic. Full shallow clone with submodule only if G2 passes. |
| `github.com/HMQYHM/FocusPenProX` | `reference/focuspen-pro-x`, `--depth 1` | GPL-3.0-only | Shows how HyperOS routes pen keys (`MiuiStylusShortcutManager`, `interceptKeyBeforeQueueing`). Never built: root only. |
| Upstream APKs → `apks/` (SHA-256 noted) | `gh release download` / F-Droid | — | Key Mapper 4.6.1 (`keymapper-4.6.1-foss.apk`), Lawnicons 2.18.0, Arcticons, Open Camera; Lawnchair pre-release only for the G2 test. |

Not cloned:
- **Arcticons:** a 4.2 GB repo; install the APK instead.
- **HyperCeiler:** root only, AGPL.
- **Shizuku-API:** Key Mapper already has its own wireless-debugging helper.

## 4. Workspace (`/Users/charantejapampana/Projects/MI-pad themes`, empty today)

```
MI-pad themes/                 ← git repo "pad-suite" (git init, main)
├── README.md                  ← parts, build, install, rollback
├── .gitignore                 ← forks/ reference/ apks/ **/build/ .gradle/ local.properties
├── docs/
│   ├── PLAN.md                ← this plan
│   ├── device/PROFILE.md      ← probe output (identity, baseline, inputs, camera nodes)
│   ├── device/pen-device.json ← machine-readable pen facts → pushed into PadStudio
│   ├── device/pen-matrix.md   ← G1 evidence
│   ├── decisions/ADR-000-baselines.md … ADR-004-G4.md
│   └── test-logs/<date>-<topic>.md (+ png)
├── tools/  env.sh · probe-device.sh · capture-pen.sh · install.sh · rollback.sh · shot.sh
├── padstudio/                 ← :core (pure Kotlin) + :app (Compose, Glance)
├── forks/padpen/              ← Key Mapper clone (remote upstream, branch pad/main)
├── reference/                 ← lawnchair-parts/, focuspen-pro-x/
└── apks/
```

---

## 5. Milestones (each ends with evidence in `docs/test-logs/` and a **local** git commit; nothing is ever pushed)

### M0: Set up and measure (adb only; no NDK or Rust yet)
1. **Mac:**
   - `tools/env.sh` exports `ANDROID_HOME=/opt/homebrew/share/android-commandlinetools` and adds `platform-tools` to PATH. It prints versions and `df -h ~`, and refuses to build below 8 GB free.
   - Optional: `brew install scrcpy` for screen mirroring and recordings.
   - `git init`, the `.gitignore`, then the clones from §3. Record SHAs in ADR-000.
2. **Tablet** (once, guided):
   - About tablet → tap *OS version* 7×.
   - Developer options: **USB debugging**, **Install via USB**, **USB debugging (Security settings)**. Xiaomi needs all three for installs, `pm grant` and Expert Mode; Mi-account sign-in may be required. `INSTALL_FAILED_USER_RESTRICTED` means one is off.
   - Connect USB-C and accept the RSA prompt.
3. **`tools/probe-device.sh`** (read-only) → `PROFILE.md` + `pen-device.json`:
   - **Identity:** `ro.product.model`, `ro.product.device`, `ro.product.mod_device`, `ro.build.version.release`/`.sdk`/`.incremental`, `ro.mi.os.version.name`/`.incremental`, `ro.mi.os.flavor`, `ro.miui.build.region`, plus `getprop | grep -iE 'region|mi.os|mod_device'`; `wm size`, `wm density`.
   - **Branch:** `pipa` → Pad 6 / Smart Pen 2. `uke` → Pad 7 / Focus Pen. Anything else is handled generically.
   - **Rollback baseline** (recorded, never changed by us unless listed in §9):
     - Current HOME component.
     - Settings: `enabled_accessibility_services`, `navigation_mode`, `force_fsg_nav_bar` (*UNVERIFIED* meaning), `launcher3.layout.provider`, `miui_optimization` (record only, never toggle), animation scales.
     - Doze allowlist (`dumpsys deviceidle whitelist`).
   - **Apps:** full launchable list (`cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER`); versions of `com.miui.home` and `com.android.camera`; Themes app state (`pm list packages -a|-d | grep thememanager`).
   - **Pen settings and input:**
     - `settings list system|secure|global | grep -iE 'pen|stylus|handwrit|shortcut'` to find Xiaomi pen-shortcut keys.
     - `dumpsys input` (devices, sources, descriptors, key layouts) and `getevent -lp` / `getevent -i` (name, **bus**, vendor, product). Expect a Bluetooth device (bus 0005) with KEY_PAGEUP/PAGEDOWN; the digitizer shows BTN_TOOL_PEN/BTN_STYLUS. `getevent` without root on HyperOS is *UNVERIFIED*; if it's denied, use `dumpsys input` plus PadStudio's Pen Probe (then built first thing in M2).
   - **Camera shutter discovery:**
     - Open Xiaomi Camera (`am start -a android.media.action.STILL_IMAGE_CAMERA`).
     - `uiautomator dump` → shutter node `resource-id`, `content-desc`, bounds, in portrait and landscape. Fallback: `dumpsys activity top`.
     - Record the camera's "Volume buttons function" setting.
4. **`tools/capture-pen.sh`** → `pen-matrix.md`:
   - Each button (upper, lower, Focus key) × press (short, long, double) × position (hovering, **≥30 cm away**: the remote-shutter case).
   - For each it records `getevent -lt`, a `logcat` slice and Xiaomi's visible reaction.
   - Then the same presses in **upstream Key Mapper's trigger recorder**, in Basic mode (shows what accessibility sees).
5. **Gates** (each written to an ADR; no questions):
   - **G1, pen path:**
     - **A:** accessibility sees short presses → Basic.
     - **B:** only `getevent` sees them → Expert.
     - **C:** nothing → in-app mappings only. Stop pen work and document root routes as not done.
   - **G2, launcher:**
     - Install the Lawnchair pre-release and `cmd package set-home-activity app.lawnchair/app.lawnchair.LawnchairLauncher`.
     - Record gestures, in-app dock, drag-to-split, Recents, rotation and reboot.
     - **Roll back to stock and confirm.** Uninstall Lawnchair.
     - **Pass** (gestures *and* dock/split survive; evidence says unlikely) → also build PadHome (M5b). **Fail** → stock launcher.
   - **G3, stock widget capability:** add an existing Android widget and record allowed sizes, resize handles, behaviour after rotation and reboot, whether "App drawer" mode exists, and whether a widget's "reconfigure" is offered.

### M1: Same-day wins with upstream apps
1. `adb install apks/keymapper-4.6.1-foss.apk`. Enable its accessibility service (Settings, or `settings put secure enabled_accessibility_services …` appended to the baseline value). If Android shows "Restricted setting": App info → ⋮ → **Allow restricted settings**, then enable it.
2. **Xiaomi survival kit** for Key Mapper:
   - Autostart on, battery "No restrictions", lock in Recents (dontkillmyapp.com/xiaomi).
   - "Display pop-up windows while running in the background" if present (*UNVERIFIED* need).
   - `pm grant io.github.sds100.keymapper android.permission.WRITE_SECURE_SETTINGS`.
   - Run Key Mapper's own **Xiaomi optimisation screen**.
3. Hand-build **one** sample map: group "Camera" with an AppInForeground constraint, short press of the upper button, action = UI-element click on the shutter. Use it as the first working shutter.
4. **Export a backup** from Key Mapper → `docs/device/golden-backup.json`. This is the **golden schema sample** the generator must match.
5. **Notes apps:**
   - Notein/Notewise: button 1 = Eraser, button 2 = Undo/Lasso.
   - Notewise: Single finger = Ignored, Double finger = Scroll/Zoom, Hide system bars (report p. 4).
6. **Stock home tidy:** App-drawer mode if G3 found it, remove loose icons from home pages, lock the layout.

### M2: PadStudio Pen Studio → gate G4
1. **Scaffold** `padstudio/` by copying WiFiBar's Gradle setup: version catalog, AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, Glance 1.2.0, WorkManager, kotlinx-serialization, AboutLibraries, SDK 37, minSdk 31, JDK 17 pin, `:core` + `:app`.
2. **`:core` pen model and generator** (pure Kotlin):
   - **Inputs:** `PenButton` (Basic: keyCode, scanCode, descriptor, name; Expert: `EvdevDeviceInfo(name, bus, vendor, product)` from `pen-device.json`) and `Context`s (app lists, pass-through flag). Plus the **action catalogue** and per-camera shutter facts.
   - **Output:** Key Mapper backup JSON shaped exactly like `golden-backup.json`:
     - Field names come from the cloned `data/` entities.
     - A root group "PadStudio Pen" with one child group per context. Each group has **one flat constraint list with one AND/OR mode**.
     - Key maps with **deterministic UIDs** (`UUID.nameUUIDFromBytes("padstudio:<context>:<button>:<press>")`).
   - **Precedence rule:** "Everywhere" = AND of `AppInForeground(isNot=true)` over **every app in every other context *including pass-through apps***. Exactly one map is eligible per (app, button, press).
   - **Shutter mechanism, chosen per camera app at generation time:**
     - Basic: `InteractUiElement` with `viewResourceId` from the probe, `nodeActions={CLICK}`, and `contentDescription`/`text` **null** (they are localised) → else `TapScreen` at calibrated portrait and landscape points (orientation constraints).
     - Expert: `KEY_CODE` `VOLUME_DOWN` for Xiaomi Camera (if its volume key = shutter), `CAMERA` for Open Camera and others.
   - **Page turn:** Basic → swipe or tap first. Expert → `PAGE_DOWN`/`PAGE_UP`.
   - **Undo:** Expert → Ctrl+Z. Basic → UI-element "Undo" if found.
   - **Evdev safety:** Expert evdev triggers are only built from devices with `bus == 0x05` (Bluetooth), the pen found in G1, and codes that are never `BTN_TOOL_*` / `BTN_STYLUS*`. Unit-tested.
3. **`:core` tests:**
   - The JSON matches the golden structure; every field name is checked against the entity sources.
   - **Descriptor match:** the device descriptor PadStudio captures for each pen button equals the one Key Mapper stored for the same button in `golden-backup.json`. If they differ, triggers would never fire, so this is checked on the device before G4.
   - A mini evaluator mirrors Key Mapper's semantics (parent group AND child constraints, `isNot`). It proves exactly one eligible map per (app, button, press).
   - Pass-through apps match nothing.
   - The evdev filter rejects digitizer codes.
   - A deliberately broken rule makes a test fail, which proves the tests can fail.
4. **`:app` Pen Studio UI:**
   - **Wizard:** first sends `ACTION_PAUSE_MAPPINGS` to Key Mapper, so it doesn't eat the presses. Then "press upper button / lower button". It captures `KeyEvent` keyCode, scanCode, `device.descriptor` and name in its own window. Afterwards `ACTION_RESUME_MAPPINGS`.
   - **Profile matrix** (contexts × button × press) using the presets in §6. Each cell shows "Xiaomi also reacts" warnings from the matrix.
   - **"Apply to Key Mapper"** writes the JSON to the cache, shares it through `FileProvider`, and starts `Intent.ACTION_VIEW` (type `application/json`, package `io.github.sds100.keymapper`, read-grant). You confirm **Replace** once.
   - **Expert card:** pairing steps (Key Mapper's built-in wireless debugging), the `WRITE_SECURE_SETTINGS` grant, and the reminder to switch on Key Mapper's key-event-via-system-bridge setting. That's `Keys.keyEventActionsUseSystemBridge`, read in `PerformKeyEventActionDelegate.kt`; without it key actions go through Key Mapper's keyboard. Plus a status check.
   - **Pen on/off toggle:** sends `ACTION_ENABLE_GROUP` / `ACTION_DISABLE_GROUP` with `EXTRA_GROUP_UID` for the "PadStudio Pen" root group. Also available as a Quick Settings tile.
5. **G4, does the generated-profile route work?**
   - It passes when the generated profile imports cleanly in Key Mapper 4.6.1, **every** row of §10's pen checks passes on the device, and no required behaviour needs engine changes.
   - **Pass** → no fork; Key Mapper stays upstream and auto-updates.
   - **Fail** → build PadPen (M5a) for exactly the failing behaviour.

### M3: PadStudio Home Kit (stock launcher)
1. **`:core` categoriser.** Precedence:
   1. User override.
   2. `categories.json`: our India and global exact-package list, ~200 entries. Each is checked against the Pad's package list; unknown entries are dropped, never guessed.
   3. **flowerpot intent rules**, resolved by `:app` through `PackageManager` and passed in.
   4. **flowerpot Play Store lists**, mapped as follows:

      | flowerpot categories | Our group |
      |---|---|
      | EDUCATION | Study |
      | BOOKS_AND_REFERENCE, NEWS_AND_MAGAZINES, COMICS | Reading |
      | BUSINESS, PRODUCTIVITY | Work |
      | ART_AND_DESIGN, PHOTOGRAPHY | Create |
      | COMMUNICATION, SOCIAL, DATING | Social |
      | ENTERTAINMENT, MUSIC_AND_AUDIO, VIDEO_PLAYERS | Watch & Listen |
      | SHOPPING, FINANCE, FOOD_AND_DRINK | Shop & Pay |
      | GAME | Games |
      | TRAVEL, MAPS, HEALTH, WEATHER, SPORTS, LIFESTYLE, … | Life |
      | TOOLS, PERSONALIZATION, LIBRARIES | Tools |

   5. `ApplicationInfo.category`.
   6. Keyword heuristics.
   7. `com.miui.*` / `com.xiaomi.*` system apps → System.
   8. Otherwise More. Groups with 1 app fold into More.
   - flowerpot files are vendored under `assets/flowerpot/` and credited in About.
2. **Group widget** (Glance):
   - One widget per group; you place as many as you like.
   - **Layout:** a non-lazy Column of Rows with **≤10 icons per Row**, staying under 500 views. Big groups show the first N plus a "+12" tile.
   - **Sizing:** `SizeMode.Responsive` with 2–3 sizes from G3 (`Exact` multiplies bitmap payload).
   - **Icons:** `Icon.createWithResource(pkg, resId)` for original and icon-pack icons; small cached bitmaps (≤96 px) only for composited themed icons.
   - **Header:** name and count, plus "›" for the panel. "›" uses `AndroidRemoteViews` with a **mutable** PendingIntent so the panel can animate from the widget's `sourceBounds`, falling back to a centred animation. There's an in-widget **Edit** button, because HyperOS may not offer reconfigure.
3. **Folder panel** (translucent Compose activity):
   - Rounded panel with search, a dense grid, and long-press for App info, Move to group, Hide or Uninstall.
   - Background blur when `WindowManager.isCrossWindowBlurEnabled`, otherwise a dim scrim.
4. **Library panel:** all groups as tabs, plus search. It's the target of the pen's "Open Library" action (Key Mapper `INTENT` action with PadStudio's component).
5. **Keeping groups current:** `PackageManager.getChangedPackages(seq)` polled on open plus a WorkManager job (15 min minimum). Store the boot count with the sequence number. Declares `QUERY_ALL_PACKAGES` (sideloaded, personal use).
6. **Organize screen** (two-pane on a tablet): group columns of app chips; tap a chip to move it; choose a preset; Apply refreshes every widget.
7. **Setup hub:** health checks with deep links:
   - Key Mapper accessibility bound.
   - Expert Mode up, with a re-arm banner when it isn't.
   - Autostart and battery settings.
   - Icon pack present.
   - Widgets placed.
   - Wallpaper set.
   - `pen-device.json` imported (adb push to the app's files dir).
8. **Pen Probe** (debug screen): logs `KeyEvent` and `MotionEvent` (toolType, buttonState) in its own window and exports them.

### M4: Looks and tidy files
1. **Icons:** install Lawnicons 2.18.0 (`app.lawnchair.lawnicons`, Apache-2.0) and Arcticons (`com.donnnno.arcticons`, code GPL-3.0, icons CC BY-SA 4.0, F-Droid).
   - PadStudio styles: **Themed** (default) = `AdaptiveIconDrawable.monochrome` (API 33+) tinted with Material You colours, with the Lawnicons drawable used when an app has no monochrome layer. Other styles: **Icon pack** (any ADW/Nova pack via `org.adw.launcher.THEMES` / `com.novalauncher.THEME` + `appfilter.xml`, ported from Lawnchair's `IconPack.kt` logic) and **Original**.
2. **Wallpaper generator:**
   - Canvas drawing: soft multi-stop gradient, faint shapes, fine grain. Light and dark pair, accent picker.
   - Rendered as a square of max(w, h) so it crops well in both orientations. Applied via `WallpaperManager` to home and lock.
3. **Optional Open Camera install** (F-Droid). Set its volume keys to "take photo" for a pure key-driven shutter in Expert mode.
4. **Home page:**
   - The clock widget at the top.
   - PadStudio groups Study, Work, Reading, Social and Watch & Listen on page 1. Everything else via the Library or the drawer.
   - Dock: notes app, browser, Files, Camera, Settings.
5. **Documents tree** (report p. 6): `adb shell mkdir -p /sdcard/Documents/TabletWork/{00_Inbox,01_Subjects,02_Projects,90_Exports,99_Backups}`. Naming convention goes in the README.
6. **Optional experiments, documented only, not part of done:**
   - **System icons:** IconPack-to-MTZ (Apache-2.0) to build an `.mtz` from the chosen icon pack, applied with Zyper or HyperOS-MTZ-Studio.
   - **System font:** zFont 3.9 with Shizuku.
   - Each comes with its revert risk.

### M5: Gated forks (only on a measured trigger)
- **M5a PadPen** (only if G4 fails): see §8.1.
- **M5b PadHome** (only if G2 passes): see §8.2.

### M6: Harden and hand over
1. Run the full §10 verification once more. Then reboot, wait 30 minutes idle and re-test.
2. **Round-trip test of both scripts:**
   - Run `tools/rollback.sh` for real and confirm the device matches the baseline.
   - Run `tools/install.sh`, which restores PadStudio's saved config, re-imports the pen profile and re-places widgets.
   - Re-verify.
3. README.
4. `./gradlew clean`.
5. Final ADR summary.

---

## 6. Default pen profile (generated; editable in Pen Studio)

| Context (apps, filtered to what's installed) | Upper short | Lower short | Double presses (if conflict-free) | Expert-only long presses |
|---|---|---|---|---|
| Camera (Xiaomi Camera `com.android.camera`, Open Camera, Google Camera) | **Shutter** | **Shutter** | — | — |
| PDF and reading (Adobe Acrobat, Xodo, WPS, Moon+, ReadEra, Kindle, Play Books, Drive viewer) | Next page | Previous page | — | — |
| Notes with native pen support (Notein, Notewise, Flexcil, Mi Canvas) | *pass-through* | *pass-through* | — | — |
| Other notes and whiteboards (OneNote, Keep, Squid, …) | Undo | Redo | — | — |
| Video and music (YouTube, JioHotstar, Netflix, Prime Video, Spotify, JioSaavn) | Play/Pause | Back | — | — |
| Home screen (`com.miui.home`) | Open Library | Open notes app | upper: Open camera | — |
| Everywhere else | Screenshot | Back | upper: Open camera; lower: Recents | upper long: Screenshot; lower long: Quick note in *your* notes app |

---

## 7. PadStudio module map (key classes)

- **`:core`**
  - `pen/`: `PenButton`, `PenContext`, `PenAction`, `ProfileGenerator`, `KeyMapperJson` (writer that mirrors the golden sample), `ConstraintEvaluator` (test mirror of Key Mapper's semantics), `EvdevSafetyFilter`.
  - `home/`: `AppFacts`, `Group`, `Categorizer`, `FlowerpotParser` (port of Lawnchair's `Flowerpot`), `GroupPlanner`, `Presets`.
  - `icons/`: `AppFilterParser` (pure XML parsing).
- **`:app`**
  - `pen/`: `PenStudioScreen`, `PenWizard`, `KeyMapperBridge` (pause/resume broadcasts, VIEW import, trigger-by-UID).
  - `home/`: `GroupWidget` + receiver + `EditGroupActivity`, `FolderPanelActivity`, `LibraryActivity`, `OrganizeScreen`, `PackageWatcherWorker`.
  - `icons/`: `IconRepository` (themed, icon-pack and original; disk cache).
  - `looks/`: `WallpaperGenerator`.
  - `setup/`: `HealthScreen`, `ProbeImport`, `PenProbeActivity`.
  - `about/`: AboutLibraries plus Key Mapper and Lawnchair attributions.

## 8. Fork specs (built only when their gate says so)

### 8.1 PadPen (Key Mapper fork), if G4 fails
1. **Toolchain** (installed only now):
   - `brew install openjdk@21` (Key Mapper CI uses 21), `sdkmanager "ndk;27.3.13750724"`, `rustup` with the minimal profile plus `aarch64-linux-android` (debug builds only arm64).
   - In `local.properties`: `rust.cargoCommand` / `rust.rustcCommand`.
   - First run `./gradlew assembleDebug testDebugUnitTest ktlintCheck` unmodified. Follow the repo's own `CLAUDE.md` (ktlint, no expression-bodied functions, `#<issue> <type>:` commit messages).
2. **Rename:**
   - `applicationId "com.charan.padpen"`; debug becomes `com.charan.padpen.debug` (fix pm, dumpsys and rollback strings, or build `debug_release`). Namespace unchanged; authorities already use `${applicationId}`.
   - Replace the hard-coded `io.github.sds100.keymapper/` strings found by search (`CodeBlock.kt`, `ExpertModeScreen.kt`, `AccessibilityServiceAdapterImpl.kt`, `KeyMapListHeader.kt`, `GroupConstraintRow.kt`, `CreateKeyMapShortcutScreen.kt`, `KeyMapperImeService.kt`, `PerformActionsUseCase.kt`). Re-grep after cloning.
   - Keep stored IDs such as `DEVICE_ID_THIS_DEVICE`.
3. **Hooks** (`HomeTab` is dead code):
   - `utils/navigation/NavDestination.kt`: a `PadPen` destination.
   - `BaseMainNavHost.kt`: its `composable`.
   - `home/KeyMapListAppBar.kt`: a menu entry.
4. **New package `base/.../pad/`:**
   - Port PadStudio's generator to write directly through `GroupRepository` (parents first; never delete and recreate groups, because the foreign key cascades) and `KeyMapRepository.insert(KeyMapEntityMapper.toEntity(...))` with deterministic UIDs.
   - Triggers via `ConfigTriggerDelegate.addKeyEventTriggerKey(..., KeyEventTriggerDevice.External(descriptor, name))` or `addEvdevTriggerKey(..., EvdevDeviceInfo)` behind the evdev safety filter.
   - Fix only the behaviour G4 found missing.
5. **Tests:** `processKeyMapsAndGroups` (`detection/DetectKeyMapsUseCase.kt`) with a fake `ConstraintSnapshot` asserting exactly one eligible map per (app, button, press). Then uninstall upstream Key Mapper after migrating via backup.

### 8.2 PadHome (thin Lawnchair fork), if G2 passes
1. **Toolchain and clone:** JDK 21 (`jdkRelease = "21"`). Full shallow clone with `--recurse-submodules --shallow-submodules`.
2. **Flavor:** add a `pad` channel next to `github`/`nightly`/`play` (`applicationId 'com.charan.padhome'`, `pad/AndroidManifest.xml`). Build `assembleLawnWithQuickstepPadDebug`.
3. **Dense tablet profiles** `pad_8_by_5` and `pad_10_by_6` in `lawnchair/res/xml/device_profiles.xml`, with smaller `borderSpace…`/`horizontalMargin…` than 64 dp / 120 dp. Use the Nova-style padding prefs (PR #6872, merged).
4. **`PadPresets`** writes the real keys:
   - Grid: `pref_workspaceColumns`, `pref_workspaceRows`, `pref_hotseatColumns`.
   - Folders: `pref_folderRows`, `folderColumns`.
   - Icons and labels: `homeIconSizeFactor`, `homeIconLabelSizeFactor`.
   - Themed icons: `themed_icons`, `pref_themedIconPackPackage`.
   - Folder look: `folderBackgroundOpacity`.
5. **Organize:** a `// PAD-HOOK` in `LayoutParserFactory.createExternalLayoutParser` reads PadStudio's layout provider (authority `com.charan.padstudio.layout`, caller allowlist). The `REORGANIZE` action runs `ModelDbController.createEmptyDB()` then `LauncherModel.forceReload()`, so settings are kept.
   - Layout XML (verified): `<folder container="desktop" screen x y titleText>` with `<appicon packageName className/>`, and `<appicon container="hotseat" rank>`.
6. **Verify:** screenshots in both orientations, folder count matches the plan, gestures and Recents re-tested.

---

## 9. Rollback (`tools/rollback.sh`, idempotent; restores PROFILE.md baseline)
- HOME → the baseline component (`cmd package set-home-activity`).
- `enabled_accessibility_services` → baseline value. `launcher3.layout.provider` → deleted if we set it.
- **First** save PadStudio's config (pen profiles, group overrides, presets) with `adb exec-out run-as com.charan.padstudio cat files/config.json > docs/device/backup/padstudio-config.json`. `run-as` works because the debug build is debuggable. `install.sh` writes it back.
- Uninstall `com.charan.padstudio` (its widgets disappear with it) and `io.github.sds100.keymapper` (pass `--keep-keymapper` to keep it), plus `com.charan.padpen`/`com.charan.padhome` if built. This also revokes their grants. Turn off wireless debugging.
- **Wallpaper:** before uninstalling, `rollback.sh` opens PadStudio's exported `ResetActivity` (`am start`). You confirm once, and it calls `WallpaperManager.clear()` to return to the HyperOS default. Apps can't read back a previous custom wallpaper, so PadStudio tells you to note it before applying ours.
- Empty `Documents/TabletWork` folders are removed only if still empty.
- `miui_optimization`, nav mode and animation scales are never changed, so there's nothing to restore.

## 10. Verification: what "done" means (on the real Pad, installed APKs, portrait *and* landscape, each with a failure case)

| Area | Check | Must also behave |
|---|---|---|
| Unit tests | `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug`. A deliberately broken rule is shown to fail, then reverted. | — |
| Import | The generated JSON restores in Key Mapper 4.6.1 with no error. The group tree in Key Mapper matches the profile. | Re-import (REPLACE) creates no duplicates. |
| Action half | For each generated map, `adb shell am broadcast -a io.github.sds100.keymapper.ACTION_TRIGGER_KEYMAP_BY_UID -n io.github.sds100.keymapper/io.github.sds100.keymapper.api.TriggerKeyMapsBroadcastReceiver --es io.github.sds100.keymapper.EXTRA_KEYMAP_UID <uid>` (names verified in `api/Api.kt` and the manifest) → expected effect. | An unknown UID does nothing. Disabling the group with `ACTION_DISABLE_GROUP` + `EXTRA_GROUP_UID` stops every pen action. |
| **Shutter** | **Physical** press in Xiaomi Camera, Open Camera and one more camera app. `adb shell ls -t /sdcard/DCIM/Camera` gains a file per press, in both orientations, and from ≥30 cm if Bluetooth allows. | A press outside camera apps runs the Everywhere action, never a tap at the shutter position. |
| Page turn | PDF screenshot before and after shows the page changed. | In Notein the raw button reaches the app (its eraser works). |
| Global | Screenshot → a new file in `Pictures/Screenshots`; Back, Recents, Open Library observed. | Pen asleep or disconnected → no crash; the health screen shows the state. |
| Survival | `dumpsys accessibility` shows Key Mapper bound after install, **after reboot**, and after 30 min idle with the screen off. Expert re-arms on Wi-Fi. | Only one key-filtering service is enabled. |
| Writing safety | The same 20-second handwriting sample in Notein with mappings off, Basic and Expert; compare strokes and `getevent` cadence. logcat "Grab evdev devices…" lists **only** the pen's Bluetooth device. | If Key Mapper issue #2122 (stylus freeze) appears → Basic only, recorded. |
| Grouping | Every launchable app appears exactly once across groups (PadStudio debug export compared with the `query-activities` list). | A test APK installed shows up in the right group within one worker period, and disappears when uninstalled. |
| Widgets and panels | Screenshots at Calm, Compact and Dense in both orientations. I review spacing and alignment and tune D7. | Rotate and reboot → no blank tiles. A missing icon pack falls back to themed or original. |
| Looks | `dumpsys wallpaper` shows our wallpaper. Switching icon style re-renders every widget. | — |
| Rollback | After `rollback.sh` the device state equals the PROFILE.md baseline, and the home screen screenshot matches. | Running it twice is harmless. |

Physical pen presses can't be automated. I'll call out each press for you and collect results over adb. Simulated `input keyevent` is a smoke test only, never proof.

## 11. Risks

| Risk | Fallback |
|---|---|
| Buttons hidden from accessibility | G1-B Expert; G1-C in-app only, recorded. |
| Xiaomi's action and ours fire together | Basic maps conflict-free presses only. Expert grabs the pen device exclusively (*UNVERIFIED* whether Xiaomi reads that same device). |
| HyperOS kills Key Mapper | Survival kit; `WRITE_SECURE_SETTINGS` self-restart; health banner. |
| Backup schema drifts in a Key Mapper update | Golden sample re-exported and the G4 check re-run after each update; Key Mapper migrates older `db_version` backups itself. |
| Shutter node or position changes with a camera app update | Re-run the probe step. Resource-id match first, calibrated tap second. |
| Glance or RemoteViews limits | ≤10 per row, ≤500 views, resource icons, Responsive sizes. |
| HyperOS OTA changes behaviour | Re-run `probe-device.sh` and `capture-pen.sh` and compare matrices. |
| Disk space | `env.sh` guard, shallow and sparse clones, no NDK/Rust unless a fork is triggered, `clean` after milestones. |
| Licences | PadStudio GPL-3.0 (flowerpot attribution); Key Mapper untouched upstream; forks keep their licences. |

## 12. Out of scope
Root, LSPosed, HyperCeiler or FocusPen Pro X execution. Theme-store unlocking and system-wide theming (optional experiments only). Debloating. Pen hardware faults (ghost strokes, battery, nibs). Store publishing and GitHub pushes.

## 13. Key sources (all opened during planning)

**Key Mapper:**
- Repo, release v4.6.1, `CLAUDE.md`, `ConstraintId.kt`, `Constraint.kt` (`isNot`), `SystemBridgeAutoStarter.kt`, `RecordTriggerController.kt`, `BackupManager.kt`, `base/src/main/AndroidManifest.xml` (`RestoreKeyMapsActivity`), `api/src/main/AndroidManifest.xml` (pause, trigger and enable receivers).
- Issues #1232, #1214, #2122.

**Lawnchair:**
- `LayoutParserFactory.kt`, `AutoInstallsLayout.java`, `LauncherLayoutBuilder.kt`, `ModelDbController.java`, `build.gradle`, `libs.versions.toml`, `LICENSE.txt`, `flowerpot/README.md`, preference managers.
- Issues #6065, #6924; PR #6872.

**Android and platform:**
- androidx `LazyVerticalGridTranslator.kt`.
- developer.android.com: AccessibilityServiceInfo, KeyEvent.
- Open Camera `MainUI.java`.

**Device and app facts:**
- TechAdvisor Pad 7 review; dontkillmyapp.com/xiaomi; xmfirmwareupdater (Pad 6 / Pad 7 India builds); Pad 6 firmware dump `build.prop`.
- Lawnicons and Arcticons releases; Niagara HyperOS help; Zyper and zFont pages.
