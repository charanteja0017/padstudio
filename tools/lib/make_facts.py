#!/usr/bin/env python3
"""Build docs/device/device-facts.json (PadStudio's FactsFile) from probe and pen-capture data.

  python3 -I tools/lib/make_facts.py --probe <raw_dir> [--camera <raw_dir>]... \
      --device "Xiaomi Focus Pen Pro Keyboard" \
      --control pinch=KEY_F19 --control slide_up=KEY_KP9 --control slide_down=KEY_KP3 \
      --kind FOCUS_PEN_PRO --mode EXPERT --out docs/device/device-facts.json

Control codes come from tools/capture-pen.sh (docs/device/pen-matrix.md). The JSON layout mirrors
padstudio/core/.../pen/FactsFile.kt (kotlinx.serialization; sealed PenTrigger uses "type").
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_profile as mp  # noqa: E402

# Linux input-event-codes.h scan codes, and the Android key code Generic.kl maps each to
# (checked on the Pad 8: `key 189 F19`, `key 73 NUMPAD_9`, `key 81 NUMPAD_3`; values from android.jar).
LINUX = {"KEY_F19": 189, "KEY_KP9": 73, "KEY_KP3": 81, "KEY_PAGEUP": 104, "KEY_PAGEDOWN": 109, "KEY_KPPLUS": 78, "KEY_KPENTER": 96}
ANDROID = {"KEY_F19": 332, "KEY_KP9": 153, "KEY_KP3": 147, "KEY_PAGEUP": 92, "KEY_PAGEDOWN": 93, "KEY_KPPLUS": 157, "KEY_KPENTER": 160}


def screen(raw: Path) -> dict | None:
    match = re.search(r"Physical size: (\d+)x(\d+)", (raw / "wm.txt").read_text() if (raw / "wm.txt").exists() else "")
    return {"width": int(match.group(1)), "height": int(match.group(2))} if match else None


def pen_devices(raw: Path, wanted: str | None) -> list[dict]:
    devices = mp.parse_event_hub(mp.read(raw, "dumpsys_input.txt"))
    keys = mp.parse_getevent_lp(mp.read(raw, "getevent_lp.txt"))
    result = []
    seen = set()
    for d in devices:
        if not d.is_pen_candidate:
            continue
        if wanted and d.name != wanted:
            continue
        # Key Mapper's Expert mode adds a uinput copy of each device it grabs, with the same identity.
        identity = (d.name, d.bus, d.vendor, d.product)
        if identity in seen:
            continue
        seen.add(identity)
        result.append({
            "name": d.name,
            "bus": int(d.bus, 16),
            "vendor": int(d.vendor, 16),
            "product": int(d.product, 16),
            "capabilities": sorted(set(keys.get(d.path, []))),
        })
    return result


# probe-device.sh --camera-keys: which injected key took a photo.
SHUTTER_KEYS = {"KEYCODE_CAMERA": "honoursCameraKey", "KEYCODE_VOLUME_DOWN": "volumeKeyIsShutter"}


def cameras(camera_dirs: list[Path]) -> list[dict]:
    found: dict[str, dict] = {}
    for raw in camera_dirs:
        for xml in sorted(raw.glob("camera_ui_*.xml")):
            try:
                root = ET.fromstring(xml.read_text(errors="replace"))
            except ET.ParseError:
                continue
            for node in root.iter("node"):
                rid = node.get("resource-id", "")
                if rid.endswith(":id/shutter_button") and node.get("clickable") == "true":
                    pkg = node.get("package", "")
                    found.setdefault(pkg, {"packageName": pkg})["shutterNode"] = {
                        "viewResourceId": rid,
                        "className": node.get("class") or None,
                        "description": node.get("content-desc") or "Shutter button",
                    }
        for keys in sorted(raw.glob("camera_keys_*.tsv")):
            pkg = keys.stem.removeprefix("camera_keys_")
            for line in keys.read_text().splitlines():
                key, fired = (line.split("\t") + ["", ""])[:2]
                if key in SHUTTER_KEYS:
                    found.setdefault(pkg, {"packageName": pkg})[SHUTTER_KEYS[key]] = fired == "1"
    return list(found.values())


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--probe", required=True, type=Path)
    ap.add_argument("--camera", action="append", default=[], type=Path)
    ap.add_argument("--device", help="name of the pen key device the controls come from")
    ap.add_argument("--control", action="append", default=[], help="control=KEY_NAME")
    ap.add_argument("--kind", choices=["TWO_BUTTON", "FOCUS_PEN_PRO"])
    ap.add_argument("--mode", choices=["BASIC", "EXPERT"])
    ap.add_argument("--out", required=True, type=Path)
    args = ap.parse_args()

    devices = pen_devices(args.probe, None)
    controls = {}
    if args.control:
        source = next((d for d in devices if d["name"] == args.device), None)
        if source is None:
            print(f"device {args.device!r} not among pen candidates: {[d['name'] for d in devices]}", file=sys.stderr)
            return 1
        for spec in args.control:
            control, _, code = spec.partition("=")
            if code not in LINUX:
                print(f"unknown code {code} (add it to LINUX/ANDROID)", file=sys.stderr)
                return 1
            controls[control] = {"type": "evdev", "keyCode": ANDROID[code], "scanCode": LINUX[code], "device": source}

    facts = {
        "screen": screen(args.probe),
        "cameras": cameras(args.camera),
        "penDevices": devices,
        "controls": controls,
    }
    if args.kind:
        facts["penKind"] = args.kind
    if args.mode:
        facts["penMode"] = args.mode
    args.out.write_text(json.dumps(facts, indent=2) + "\n")
    print(f"wrote {args.out}: {len(devices)} pen devices, {len(controls)} controls, {len(facts['cameras'])} cameras")
    return 0


if __name__ == "__main__":
    sys.exit(main())
