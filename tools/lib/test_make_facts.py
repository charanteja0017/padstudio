#!/usr/bin/env python3
"""Tests for make_facts.py's camera facts.

The fixtures are SYNTHETIC, in the formats probe-device.sh writes: a `uiautomator dump`
of the camera (camera_ui_rot*.xml) and the --camera-keys result (camera_keys_<pkg>.tsv).

Run: python3 -I tools/lib/test_make_facts.py
"""

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_facts as mf  # noqa: E402

CAMERA_UI = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="1">
  <node index="0" text="" resource-id="com.android.camera:id/shutter_button" class="android.view.View"
        package="com.android.camera" content-desc="Shutter button" clickable="true" bounds="[2718,955][2959,1196]" />
</hierarchy>
"""


class CameraFactsTest(unittest.TestCase):
    def probe(self, files: dict[str, str]) -> Path:
        raw = Path(tempfile.mkdtemp())
        for name, text in files.items():
            (raw / name).write_text(text)
        return raw

    def test_shutter_node_and_keys_merge_into_one_camera(self):
        raw = self.probe({
            "camera_ui_rot1.xml": CAMERA_UI,
            "camera_keys_com.android.camera.tsv": "KEYCODE_CAMERA\t1\tIMG_1.jpg \nKEYCODE_VOLUME_DOWN\t0\t\n",
        })
        [camera] = mf.cameras([raw])
        self.assertEqual("com.android.camera", camera["packageName"])
        self.assertEqual("com.android.camera:id/shutter_button", camera["shutterNode"]["viewResourceId"])
        self.assertIs(True, camera["honoursCameraKey"])
        self.assertIs(False, camera["volumeKeyIsShutter"], "a key that took no photo must not count")

    def test_keys_alone_describe_a_camera(self):
        raw = self.probe({"camera_keys_net.sourceforge.opencamera.tsv": "KEYCODE_CAMERA\t1\tIMG_2.jpg \n"})
        self.assertEqual(
            [{"packageName": "net.sourceforge.opencamera", "honoursCameraKey": True}],
            mf.cameras([raw]),
        )

    def test_nothing_measured_means_no_cameras(self):
        self.assertEqual([], mf.cameras([self.probe({})]))


if __name__ == "__main__":
    unittest.main()
