#!/usr/bin/env python3
"""Tests for pen_matrix.py. Fixtures are SYNTHETIC `getevent -lt` lines in the AOSP format.

Run: python3 -I tools/lib/test_pen_matrix.py
"""

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pen_matrix as pm  # noqa: E402

NAMES = """add device 1: /dev/input/event7
  name:     "Xiaomi Smart Pen"
add device 2: /dev/input/event3
  name:     "xiaomi-touch"
"""

# short PAGEUP, then a long PAGEDOWN (0.8 s), then a double PAGEUP, plus digitizer noise.
GETEVENT = """[   100.000000] /dev/input/event7: EV_MSC       MSC_SCAN             00070075
[   100.000100] /dev/input/event7: EV_KEY       KEY_PAGEUP           DOWN
[   100.000200] /dev/input/event7: EV_SYN       SYN_REPORT           00000000
[   100.120000] /dev/input/event7: EV_KEY       KEY_PAGEUP           UP
[   102.000000] /dev/input/event7: EV_KEY       KEY_PAGEDOWN         DOWN
[   102.800000] /dev/input/event7: EV_KEY       KEY_PAGEDOWN         UP
[   104.000000] /dev/input/event7: EV_KEY       KEY_PAGEUP           DOWN
[   104.090000] /dev/input/event7: EV_KEY       KEY_PAGEUP           UP
[   104.250000] /dev/input/event7: EV_KEY       KEY_PAGEUP           DOWN
[   104.330000] /dev/input/event7: EV_KEY       KEY_PAGEUP           UP
[   105.000000] /dev/input/event3: EV_ABS       ABS_MT_POSITION_X    00000123
[   105.000100] /dev/input/event3: EV_KEY       BTN_TOOL_PEN         DOWN
[   105.500000] /dev/input/event3: EV_KEY       BTN_TOOL_PEN         UP
"""


class PenMatrixTests(unittest.TestCase):
    def test_classifies_short_long_double(self):
        found = pm.presses(pm.parse_getevent(GETEVENT), pm.parse_names(NAMES))
        pen = [(p.code, p.kind) for p in found if p.device == "/dev/input/event7"]
        self.assertEqual(pen, [("KEY_PAGEUP", "short"), ("KEY_PAGEDOWN", "long"), ("KEY_PAGEUP", "double")])
        self.assertEqual(found[0].device_name, "Xiaomi Smart Pen")

    def test_summary_and_matrix(self):
        with tempfile.TemporaryDirectory() as tmp:
            d = Path(tmp)
            (d / "upper-short.getevent").write_text(GETEVENT)
            (d / "upper-short.names").write_text(NAMES)
            (d / "upper-short.before").write_text("com.miui.home/.launcher.Launcher\nScreenshot_1.png\n")
            (d / "upper-short.after").write_text("com.miui.creation/.MainActivity\nScreenshot_2.png\n")
            (d / "upper-short.logcat").write_text("10-11 14:00:00.000 I MiuiStylusShortcutManager: quick note\n")
            s = pm.summarize(d, "upper-short")
            self.assertTrue(s["foreground_changed"])
            self.assertTrue(s["screenshot_taken"])
            self.assertEqual(len(s["xiaomi_log_lines"]), 1)
            self.assertEqual(json.loads((d / "upper-short.json").read_text())["label"], "upper-short")
            text = pm.matrix(d, d / "pen-matrix.md")
            self.assertIn("KEY_PAGEDOWN`: long", text)
            self.assertIn("com.miui.creation", text)

    def test_denied_getevent_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            d = Path(tmp)
            (d / "x.getevent").write_text("could not open /dev/input/event7, Permission denied\n")
            s = pm.summarize(d, "x")
            self.assertTrue(s["getevent_denied"])
            self.assertIn("getevent denied", pm.matrix(d, d / "m.md"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
