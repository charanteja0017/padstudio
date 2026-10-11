# Pad Suite

Makes a Xiaomi Pad's pen useful in every app (camera shutter, page turns, undo, shortcuts), tidies the home screen into dense auto-grouped widgets, and gives it a coherent look — **without root**, and fully reversible.

| Part | What it is |
|---|---|
| **PadStudio** (`padstudio/`, `com.charan.padstudio`) | Our app: pen-profile generator for Key Mapper, group widgets + folder panels for the stock HyperOS launcher, icon styles, wallpapers, setup checks. |
| **Key Mapper 4.6.1** (upstream, unmodified) | Runs the pen buttons. PadStudio hands it a generated profile in one tap. |
| HyperOS launcher (stock) | Kept, so full-screen gestures, the dock and split-screen keep working. |

Plan and reasoning: [`docs/PLAN.md`](docs/PLAN.md). Pinned versions and checksums: [`docs/decisions/ADR-000-baselines.md`](docs/decisions/ADR-000-baselines.md).

## Use

```sh
tools/env.sh --check                 # toolchain, disk, connected device
tools/probe-device.sh                # read-only device profile + rollback baseline (run first!)
tools/capture-pen.sh upper-short     # record what one pen press produces (repeat per button/press)
tools/capture-pen.sh --matrix        # → docs/device/pen-matrix.md
tools/install.sh keymapper           # pen engine; then lawnicons, padstudio
tools/shot.sh home-compact           # screenshot evidence → docs/test-logs/
tools/rollback.sh --dry-run          # see what undo would do; drop --dry-run to undo everything
```

Before the first run, on the tablet: Settings → About tablet → tap *OS version* 7×, then in Developer options turn on **USB debugging**, **Install via USB** and **USB debugging (Security settings)**.

## Layout

```
docs/      plan, device profile, decisions (ADRs), test evidence
tools/     adb scripts (bash) + parsers with tests (tools/lib, python3 -I)
padstudio/ the app (:core pure Kotlin, :app Compose + Glance)
forks/ reference/ apks/   upstream sources and APKs (not committed; see ADR-000)
```

Tests for the scripts' parsers: `python3 -I tools/lib/test_make_profile.py && python3 -I tools/lib/test_pen_matrix.py`.

## Licences

PadStudio is GPL-3.0 (it reuses GPL-3.0-or-later data and code from Lawnchair's *flowerpot* categoriser). Key Mapper (GPL-3.0) is used unmodified as a separate app.
