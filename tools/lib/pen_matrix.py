#!/usr/bin/env python3
"""Parse pen-button captures written by tools/capture-pen.sh and build docs/device/pen-matrix.md.

  python3 -I tools/lib/pen_matrix.py summarize <capture_dir> <label>   → <label>.json (+ prints a summary)
  python3 -I tools/lib/pen_matrix.py matrix <capture_dir> <out.md>     → pen-matrix.md from every *.json

A capture directory contains, per label:
  <label>.getevent     output of `getevent -lt` during the capture window
  <label>.names        output of `getevent -lp` (device path → name), optional
  <label>.logcat       logcat slice for the window, optional
  <label>.before/after resumed activity + newest screenshot before/after, optional
"""

from __future__ import annotations

import json
import re
import sys
from dataclasses import asdict, dataclass
from pathlib import Path

LINE = re.compile(
    r"^\[\s*(?P<ts>\d+\.\d+)\]\s+(?P<dev>/dev/input/event\d+):\s+(?P<type>\S+)\s+(?P<code>\S+)\s+(?P<value>\S+)"
)
LONG_PRESS_S = 0.5      # Key Mapper's default long-press delay is in this range; refined on device
DOUBLE_PRESS_S = 0.3    # gap between two releases/presses that counts as a double press


@dataclass
class KeyEvent:
    ts: float
    device: str
    code: str
    value: str  # DOWN / UP / REPEAT or numeric


@dataclass
class Press:
    device: str
    device_name: str
    code: str
    kind: str          # short | long | double
    duration_s: float
    started_at: float


def parse_getevent(text: str) -> list[KeyEvent]:
    events = []
    for line in text.splitlines():
        match = LINE.match(line.strip())
        if not match or match.group("type") != "EV_KEY":
            continue
        value = match.group("value")
        value = {"00000000": "UP", "00000001": "DOWN", "00000002": "REPEAT"}.get(value, value)
        events.append(KeyEvent(float(match.group("ts")), match.group("dev"), match.group("code"), value))
    return events


def parse_names(text: str) -> dict[str, str]:
    names, current = {}, None
    for line in text.splitlines():
        device = re.match(r"^add device \d+: (\S+)", line)
        if device:
            current = device.group(1)
            continue
        name = re.match(r'^\s+name:\s+"(.*)"', line)
        if current and name:
            names[current] = name.group(1)
    return names


def presses(events: list[KeyEvent], names: dict[str, str]) -> list[Press]:
    """Pair DOWN/UP per (device, code); classify long presses; merge quick repeats into doubles."""
    open_downs: dict[tuple[str, str], float] = {}
    singles: list[Press] = []
    for event in events:
        key = (event.device, event.code)
        if event.value == "DOWN":
            open_downs[key] = event.ts
        elif event.value == "UP" and key in open_downs:
            start = open_downs.pop(key)
            duration = event.ts - start
            kind = "long" if duration >= LONG_PRESS_S else "short"
            singles.append(Press(event.device, names.get(event.device, ""), event.code, kind, round(duration, 3), start))
    merged: list[Press] = []
    for press in singles:
        previous = merged[-1] if merged else None
        if (
            previous
            and previous.kind == "short"
            and press.kind == "short"
            and previous.device == press.device
            and previous.code == press.code
            and press.started_at - (previous.started_at + previous.duration_s) <= DOUBLE_PRESS_S
        ):
            previous.kind = "double"
            continue
        merged.append(press)
    return merged


def read(path: Path) -> str:
    return path.read_text(errors="replace").replace("\r", "") if path.exists() else ""


def summarize(capture_dir: Path, label: str) -> dict:
    getevent = read(capture_dir / f"{label}.getevent")
    names = parse_names(read(capture_dir / f"{label}.names"))
    events = parse_getevent(getevent)
    found = presses(events, names)
    before = read(capture_dir / f"{label}.before").strip().splitlines()
    after = read(capture_dir / f"{label}.after").strip().splitlines()
    logcat = read(capture_dir / f"{label}.logcat")
    xiaomi_lines = [l for l in logcat.splitlines() if re.search(r"stylus|pen|screenshot|quick ?note|creation", l, re.I)]
    summary = {
        "label": label,
        "getevent_lines": len(getevent.splitlines()),
        "getevent_denied": "Permission denied" in getevent or "could not open" in getevent.lower(),
        "presses": [asdict(p) for p in found],
        "foreground_before": before[0] if before else "",
        "foreground_after": after[0] if after else "",
        "foreground_changed": bool(before and after and before[0] != after[0]),
        "screenshot_before": before[1] if len(before) > 1 else "",
        "screenshot_after": after[1] if len(after) > 1 else "",
        "screenshot_taken": bool(len(before) > 1 and len(after) > 1 and before[1] != after[1]),
        "xiaomi_log_lines": xiaomi_lines[:20],
    }
    (capture_dir / f"{label}.json").write_text(json.dumps(summary, indent=2) + "\n")
    return summary


def matrix(capture_dir: Path, out: Path) -> str:
    rows = [
        "# Pen capture matrix",
        "",
        "Generated by `tools/capture-pen.sh --matrix` from captures in `docs/device/raw/pen/` (raw files stay local).",
        "evdev = what the kernel reports (`getevent -lt`); Xiaomi reaction = foreground app change / new screenshot during the window.",
        "",
        "| Capture | evdev presses (device → code: kind, seconds) | Foreground change | Screenshot taken | Xiaomi log hints |",
        "|---|---|---|---|---|",
    ]
    for path in sorted(capture_dir.glob("*.json")):
        s = json.loads(path.read_text())
        if s.get("getevent_denied"):
            evdev = "*getevent denied — use Key Mapper recorder / PadStudio probe*"
        elif not s["presses"]:
            evdev = "*nothing*"
        else:
            evdev = "<br>".join(
                f"{p['device_name'] or p['device']} → `{p['code']}`: {p['kind']} ({p['duration_s']} s)" for p in s["presses"]
            )
        change = f"{s['foreground_before']} → {s['foreground_after']}" if s["foreground_changed"] else "—"
        hints = "<br>".join(l[-90:].replace("|", "/") for l in s["xiaomi_log_lines"][:3]) or "—"
        rows.append(f"| `{s['label']}` | {evdev} | {change} | {'yes' if s['screenshot_taken'] else '—'} | {hints} |")
    rows += [
        "",
        "Add the accessibility layer by repeating each press in Key Mapper's trigger recorder (Basic mode) and noting what it shows.",
        "",
    ]
    text = "\n".join(rows)
    out.write_text(text)
    return text


def main(argv: list[str]) -> int:
    if len(argv) == 4 and argv[1] == "summarize":
        s = summarize(Path(argv[2]), argv[3])
        print(f"{s['label']}: {len(s['presses'])} press(es)")
        for p in s["presses"]:
            print(f"  {p['device_name'] or p['device']}  {p['code']}  {p['kind']}  ({p['duration_s']} s)")
        if s["getevent_denied"]:
            print("  getevent was denied — kernel layer not visible without root on this build")
        print(f"  foreground: {s['foreground_before']} → {s['foreground_after']}")
        print(f"  screenshot taken during window: {s['screenshot_taken']}")
        return 0
    if len(argv) == 4 and argv[1] == "matrix":
        matrix(Path(argv[2]), Path(argv[3]))
        print(f"wrote {argv[3]}")
        return 0
    print(__doc__, file=sys.stderr)
    return 64


if __name__ == "__main__":
    sys.exit(main(sys.argv))
