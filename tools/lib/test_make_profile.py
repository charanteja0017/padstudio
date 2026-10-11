#!/usr/bin/env python3
"""Tests for make_profile.py.

The fixtures below are SYNTHETIC: hand-written in the formats AOSP prints for
`dumpsys input` (EventHub::dump), `getevent -lp`, `getprop`, `settings list` and
`cmd package resolve-activity`. They are not captures from a real Xiaomi Pad —
the first real probe replaces guesswork with data.

Run: python3 -I tools/lib/test_make_profile.py
"""

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_profile as mp  # noqa: E402

DUMPSYS_INPUT = """INPUT MANAGER (dumpsys input)

Input Manager Service (Java) State:
  Uniq Ids: 3

Event Hub State:
  BuiltInKeyboardId: -2
  Devices:
    -1: Virtual
      Classes: KEYBOARD | ALPHAKEY
      Path: <virtual>
      Enabled: true
      Descriptor: a718a782d34bc767f4689c232d64d527998ea7fd
      Location:
      ControllerNumber: 0
      UniqueId: <virtual>
      Identifier: bus=0x0000, vendor=0x0000, product=0x0000, version=0x0000, bluetoothAddress=<not set>
      KeyLayoutFile: /system/usr/keylayout/Generic.kl
    3: xiaomi-touch
      Classes: TOUCH | TOUCH_MT
      Path: /dev/input/event3
      Enabled: true
      Descriptor: 5e4d7f0c0ffee0000000000000000000000000aa
      Identifier: bus=0x0000, vendor=0x0000, product=0x0000, version=0x0000, bluetoothAddress=<not set>
    7: Xiaomi Smart Pen
      Classes: KEYBOARD | EXTERNAL
      Path: /dev/input/event7
      Enabled: true
      Descriptor: 0123456789abcdef0123456789abcdef01234567
      Identifier: bus=0x0005, vendor=0x2717, product=0x5014, version=0x0001, bluetoothAddress=<not set>
      KeyLayoutFile: /system/usr/keylayout/Generic.kl
Input Reader State (Nums of device: 3):
  Device -1: Virtual
"""

GETEVENT_LP = """add device 1: /dev/input/event7
  bus:      0005
  vendor    2717
  product   5014
  version   0001
  name:     "Xiaomi Smart Pen"
  location: ""
  events:
    KEY (0001): KEY_PAGEUP            KEY_PAGEDOWN
    MSC (0004): MSC_SCAN
  input props:
    <none>
add device 2: /dev/input/event3
  name:     "xiaomi-touch"
  events:
    KEY (0001): BTN_TOOL_PEN          BTN_TOUCH             BTN_STYLUS            BTN_STYLUS2
    ABS (0003): ABS_X                 : value 0, min 0, max 2879, fuzz 0, flat 0, resolution 0
  input props:
    INPUT_PROP_DIRECT
"""

GETPROP = """[ro.product.model]: [23043RP34G]
[ro.product.device]: [pipa]
[ro.build.version.release]: [14]
[ro.build.version.sdk]: [34]
[ro.mi.os.version.incremental]: [OS2.0.14.0.UMZINXM]
[persist.sys.miui_optimization]: [true]
"""

HOME = """priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
com.miui.home/.launcher.Launcher
"""

SETTINGS_SECURE = """accessibility_enabled=0
navigation_mode=2
stylus_buttons_enabled=1
"""

SETTINGS_GLOBAL = """window_animation_scale=1.0
force_fsg_nav_bar=1
"""


def write_fixture(raw: Path) -> None:
    (raw / "dumpsys_input.txt").write_text(DUMPSYS_INPUT)
    (raw / "getevent_lp.txt").write_text(GETEVENT_LP)
    (raw / "getprop.txt").write_text(GETPROP)
    (raw / "home.txt").write_text(HOME)
    (raw / "settings_secure.txt").write_text(SETTINGS_SECURE)
    (raw / "settings_global.txt").write_text(SETTINGS_GLOBAL)
    (raw / "settings_system.txt").write_text("")
    (raw / "packages_all.txt").write_text("package:com.miui.home\npackage:com.android.thememanager\n")
    (raw / "packages_disabled.txt").write_text("package:com.android.thememanager\n")
    (raw / "packages_third.txt").write_text("package:io.github.sds100.keymapper\n")
    (raw / "launchables.txt").write_text("com.android.settings/.Settings\ncom.miui.home/.launcher.Launcher\nnot a component\n")
    (raw / "versions.txt").write_text("com.miui.home\t4.40.0\ncom.android.camera\tnot installed\n")


class ParseTests(unittest.TestCase):
    def test_event_hub_devices_and_pen_candidate(self):
        devices = mp.parse_event_hub(DUMPSYS_INPUT)
        self.assertEqual([d.id for d in devices], ["-1", "3", "7"])
        pen = [d for d in devices if d.is_pen_candidate]
        self.assertEqual([d.name for d in pen], ["Xiaomi Smart Pen"])
        self.assertEqual((pen[0].bus, pen[0].vendor, pen[0].product), ("0x0005", "0x2717", "0x5014"))
        self.assertEqual(pen[0].descriptor, "0123456789abcdef0123456789abcdef01234567")
        touch = devices[1]
        self.assertFalse(touch.is_pen_candidate, "the digitizer must never be offered as a pen-button device")

    def test_getevent_keys(self):
        keys = mp.parse_getevent_lp(GETEVENT_LP)
        self.assertEqual(keys["/dev/input/event7"], ["KEY_PAGEUP", "KEY_PAGEDOWN"])
        self.assertIn("BTN_STYLUS", keys["/dev/input/event3"])
        self.assertNotIn("ABS_X", keys["/dev/input/event3"])
        self.assertEqual(mp.getevent_names(GETEVENT_LP)["/dev/input/event7"], "Xiaomi Smart Pen")

    def test_components_skip_noise(self):
        self.assertEqual(mp.parse_components(HOME), ["com.miui.home/.launcher.Launcher"])

    def test_missing_files_do_not_crash(self):
        with tempfile.TemporaryDirectory() as tmp:
            data = mp.build(Path(tmp))
            self.assertEqual(data["devices"], [])
            self.assertIn("not captured", mp.profile_markdown(data, Path(tmp)))


class OutputTests(unittest.TestCase):
    def test_profile_json_and_baseline(self):
        with tempfile.TemporaryDirectory() as tmp:
            raw = Path(tmp) / "raw"
            out = Path(tmp) / "device"
            raw.mkdir()
            write_fixture(raw)
            self.assertEqual(mp.main(["x", str(raw), str(out)]), 0)

            pen = json.loads((out / "pen-device.json").read_text())
            self.assertEqual(pen["codename"], "pipa")
            self.assertEqual(len(pen["pen_devices"]), 1)
            self.assertTrue(any("KEY_PAGEUP" in r for r in pen["pen_devices"][0]["reasons"]))

            baseline = (out / "baseline.env").read_text()
            self.assertIn("HOME_COMPONENT=com.miui.home/.launcher.Launcher", baseline)
            self.assertIn("A11Y_SERVICES=''\nA11Y_SERVICES_UNSET=1", baseline)
            self.assertIn("NAV_MODE=2\nNAV_MODE_UNSET=0", baseline)
            self.assertIn("FORCE_FSG_NAV_BAR=1", baseline)
            self.assertIn("PREEXISTING_KEYMAPPER=1", baseline)
            self.assertIn("PREEXISTING_PADSTUDIO=0", baseline)

            profile = (out / "PROFILE.md").read_text()
            self.assertIn("Xiaomi Pad 6", profile)
            self.assertIn("stylus_buttons_enabled", profile)
            self.assertIn("present=True, disabled=True", profile)

    def test_baseline_is_never_overwritten_without_flag(self):
        with tempfile.TemporaryDirectory() as tmp:
            raw = Path(tmp) / "raw"
            out = Path(tmp) / "device"
            raw.mkdir()
            out.mkdir()
            write_fixture(raw)
            (out / "baseline.env").write_text("HOME_COMPONENT=original\n")
            mp.main(["x", str(raw), str(out)])
            self.assertEqual((out / "baseline.env").read_text(), "HOME_COMPONENT=original\n")
            mp.main(["x", str(raw), str(out), "--rebaseline"])
            self.assertIn("com.miui.home", (out / "baseline.env").read_text())


if __name__ == "__main__":
    unittest.main(verbosity=2)
