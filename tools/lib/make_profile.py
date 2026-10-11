#!/usr/bin/env python3
"""Turn a raw probe directory (written by tools/probe-device.sh) into:

  docs/device/PROFILE.md       human-readable device profile
  docs/device/pen-device.json  machine-readable pen/input facts for PadStudio
  docs/device/baseline.env     rollback baseline (only written if it does not exist yet)

Usage: python3 -I tools/lib/make_profile.py <raw_dir> <device_dir> [--rebaseline]

Pure standard library; every parser tolerates missing files so a partial probe
still produces a profile that says exactly what is missing.
"""

from __future__ import annotations

import json
import re
import shlex
import sys
from dataclasses import asdict, dataclass, field
from pathlib import Path

# Keys copied verbatim into PROFILE.md (order matters for readability).
IDENTITY_PROPS = [
    "ro.product.model",
    "ro.product.marketname",
    "ro.product.device",
    "ro.product.vendor.device",
    "ro.product.mod_device",
    "ro.build.version.release",
    "ro.build.version.sdk",
    "ro.build.version.incremental",
    "ro.build.version.security_patch",
    "ro.mi.os.version.name",
    "ro.mi.os.version.code",
    "ro.mi.os.version.incremental",
    "ro.mi.os.flavor",
    "ro.miui.build.region",
    "ro.miui.region",
    "ro.product.locale",
    "persist.sys.miui_optimization",
]

KNOWN_DEVICES = {
    "yupei": "Xiaomi Pad 8 (India 25097RP43I) — pen: Xiaomi Focus Pen Pro (squeeze + slides, no buttons); HyperOS 3 / Android 16",
    "pipa": "Xiaomi Pad 6 — expected pen: Xiaomi Smart Pen (2nd gen); India builds: HyperOS 2 / Android 14",
    "uke": "Xiaomi Pad 7 — expected pen: Xiaomi Focus Pen; India builds: HyperOS 3 / Android 16",
}

# Settings recorded for rollback: (namespace, key, baseline variable name).
BASELINE_SETTINGS = [
    ("secure", "enabled_accessibility_services", "A11Y_SERVICES"),
    ("secure", "accessibility_enabled", "A11Y_ENABLED"),
    ("secure", "navigation_mode", "NAV_MODE"),
    ("secure", "launcher3.layout.provider", "LAYOUT_PROVIDER"),
    ("global", "force_fsg_nav_bar", "FORCE_FSG_NAV_BAR"),
    ("global", "adb_wifi_enabled", "ADB_WIFI_ENABLED"),
    ("global", "window_animation_scale", "WINDOW_ANIM"),
    ("global", "transition_animation_scale", "TRANSITION_ANIM"),
    ("global", "animator_duration_scale", "ANIMATOR_DURATION"),
    ("global", "stay_on_while_plugged_in", "STAY_ON"),
    ("secure", "enabled_notification_listeners", "NOTIF_LISTENERS"),
]

PEN_NAME = re.compile(r"pen|stylus|xiaomi.*(smart|focus)|mipen", re.IGNORECASE)
# Pen/shortcut settings only; app lists, usage data and cloud blobs stay out of the committed profile.
SETTINGS_OF_INTEREST = re.compile(
    r"^(stylus_|setting_stylus|support_native_handwriting|support_gesture_shortcut|touch_film|key_combination_|"
    r"key_chord_|volumekey_|long_press_|key_long_press|screen_key_press|notes_pref|miui_bluetooth_store_pen)",
    re.IGNORECASE,
)
MAC = re.compile(r"\b([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\b")
UUID = re.compile(r"\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b", re.IGNORECASE)


def redact(value: str) -> str:
    """Bluetooth addresses and UUIDs identify this tablet and its pen; the profile doesn't need them."""
    return UUID.sub("<uuid>", MAC.sub("<bt-address>", value))
COMPONENT = re.compile(r"^([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]+)$")


def read(raw: Path, name: str) -> str | None:
    path = raw / name
    if not path.exists():
        return None
    return path.read_text(errors="replace").replace("\r", "")


def parse_getprop(text: str | None) -> dict[str, str]:
    props: dict[str, str] = {}
    if not text:
        return props
    for line in text.splitlines():
        match = re.match(r"^\[(.+?)\]: \[(.*)\]$", line.strip())
        if match:
            props[match.group(1)] = match.group(2)
    return props


def parse_settings(text: str | None) -> dict[str, str]:
    values: dict[str, str] = {}
    if not text:
        return values
    for line in text.splitlines():
        if "=" in line:
            key, _, value = line.partition("=")
            values[key.strip()] = value.strip()
    return values


def parse_components(text: str | None) -> list[str]:
    """Extract pkg/Activity tokens from `cmd package query-activities` / resolve-activity output."""
    components: list[str] = []
    if not text:
        return components
    for line in text.splitlines():
        token = line.strip()
        if COMPONENT.match(token):
            components.append(token)
    return components


def parse_package_list(text: str | None) -> list[str]:
    if not text:
        return []
    return sorted({line.strip()[len("package:"):] for line in text.splitlines() if line.strip().startswith("package:")})


@dataclass
class InputDevice:
    id: str
    name: str
    classes: str = ""
    path: str = ""
    descriptor: str = ""
    bus: str = ""
    vendor: str = ""
    product: str = ""
    version: str = ""
    key_layout: str = ""
    enabled: str = ""
    is_pen_candidate: bool = False
    reasons: list[str] = field(default_factory=list)


def parse_event_hub(text: str | None) -> list[InputDevice]:
    """Parse the `Event Hub State: … Devices:` block of `dumpsys input`."""
    devices: list[InputDevice] = []
    if not text:
        return devices
    lines = text.splitlines()
    try:
        start = next(i for i, line in enumerate(lines) if line.strip() == "Event Hub State:")
    except StopIteration:
        return devices
    header = re.compile(r"^\s{2,6}(-?\d+): (.*)$")
    current: InputDevice | None = None
    for line in lines[start + 1:]:
        if line and not line.startswith(" "):
            break  # next top-level section
        match = header.match(line)
        if match and not line.strip().startswith(("Classes", "Path")):
            indent = len(line) - len(line.lstrip())
            if indent <= 6:
                current = InputDevice(id=match.group(1), name=match.group(2).strip())
                devices.append(current)
                continue
        if current is None:
            continue
        stripped = line.strip()
        if stripped.startswith("Classes:"):
            current.classes = stripped.split(":", 1)[1].strip()
        elif stripped.startswith("Path:"):
            current.path = stripped.split(":", 1)[1].strip()
        elif stripped.startswith("Enabled:"):
            current.enabled = stripped.split(":", 1)[1].strip()
        elif stripped.startswith("Descriptor:"):
            current.descriptor = stripped.split(":", 1)[1].strip()
        elif stripped.startswith("KeyLayoutFile:"):
            current.key_layout = stripped.split(":", 1)[1].strip()
        elif stripped.startswith("Identifier:"):
            for key, value in re.findall(r"(bus|vendor|product|version)=(0x[0-9a-fA-F]+)", stripped):
                setattr(current, key, value.lower())
    for device in devices:
        if "TOUCH" in device.classes.upper():
            # Touchscreen/digitizer (e.g. "NVTCapacitivePen…"): carries the ink, never a pen-button device.
            continue
        if PEN_NAME.search(device.name):
            device.reasons.append("name looks like a pen")
        if device.bus == "0x0005" and "KEYBOARD" in device.classes.upper():
            device.reasons.append("Bluetooth (bus 0x0005) device with keys")
        if "STYLUS" in device.classes.upper() or "EXTERNAL_STYLUS" in device.classes.upper():
            device.reasons.append("input classes include STYLUS")
        device.is_pen_candidate = bool(device.reasons)
    return devices


def parse_getevent_lp(text: str | None) -> dict[str, list[str]]:
    """Map /dev/input/eventN → list of KEY/BTN codes it can emit (from `getevent -lp`)."""
    result: dict[str, list[str]] = {}
    if not text:
        return result
    current = None
    in_key_block = False
    for line in text.splitlines():
        device = re.match(r"^add device \d+: (\S+)", line)
        if device:
            current = device.group(1)
            result[current] = []
            in_key_block = False
            continue
        if current is None:
            continue
        if re.match(r"^\s+KEY \(0001\):", line):
            in_key_block = True
            line = line.split(":", 1)[1]
        elif re.match(r"^\s+[A-Z]{2,4} \(", line):
            in_key_block = False
        if in_key_block:
            result[current].extend(re.findall(r"\b((?:KEY|BTN)_[A-Z0-9_]+)\b", line))
    return result


def getevent_names(text: str | None) -> dict[str, str]:
    """Map /dev/input/eventN → device name (from `getevent -lp` or `getevent -i`)."""
    names: dict[str, str] = {}
    if not text:
        return names
    current = None
    for line in text.splitlines():
        device = re.match(r"^add device \d+: (\S+)", line)
        if device:
            current = device.group(1)
            continue
        name = re.match(r'^\s+name:\s+"(.*)"', line)
        if current and name:
            names[current] = name.group(1)
    return names


def first_version(text: str | None) -> dict[str, str]:
    versions: dict[str, str] = {}
    if not text:
        return versions
    for line in text.splitlines():
        if "\t" in line:
            pkg, _, version = line.partition("\t")
            versions[pkg.strip()] = version.strip() or "not installed"
    return versions


def build(raw: Path) -> dict:
    props = parse_getprop(read(raw, "getprop.txt"))
    settings = {
        "system": parse_settings(read(raw, "settings_system.txt")),
        "secure": parse_settings(read(raw, "settings_secure.txt")),
        "global": parse_settings(read(raw, "settings_global.txt")),
    }
    home = parse_components(read(raw, "home.txt"))
    launchables = parse_components(read(raw, "launchables.txt"))
    packages = parse_package_list(read(raw, "packages_all.txt"))
    disabled = parse_package_list(read(raw, "packages_disabled.txt"))
    third_party = parse_package_list(read(raw, "packages_third.txt"))
    devices = parse_event_hub(read(raw, "dumpsys_input.txt"))
    getevent_keys = parse_getevent_lp(read(raw, "getevent_lp.txt"))
    getevent_dev_names = getevent_names(read(raw, "getevent_lp.txt"))
    versions = first_version(read(raw, "versions.txt"))
    wm = (read(raw, "wm.txt") or "").strip()
    notes_role = (read(raw, "role_notes.txt") or "").strip()

    codename = props.get("ro.product.device") or props.get("ro.product.vendor.device") or ""
    interesting_settings = {
        namespace: {k: v for k, v in values.items() if SETTINGS_OF_INTEREST.search(k)}
        for namespace, values in settings.items()
    }
    pen_devices = [d for d in devices if d.is_pen_candidate]
    for device in pen_devices:
        # Attach the evdev key list when getevent could see the same node.
        if device.path in getevent_keys:
            device.reasons.append("getevent keys: " + ", ".join(sorted(set(getevent_keys[device.path]))[:24]))

    return {
        "props": props,
        "codename": codename,
        "known": KNOWN_DEVICES.get(codename, "Unknown model — handled generically"),
        "wm": wm,
        "home": home[-1] if home else "",
        "settings": settings,
        "interesting_settings": interesting_settings,
        "launchables": launchables,
        "packages": packages,
        "disabled": disabled,
        "third_party": third_party,
        "devices": devices,
        "pen_devices": pen_devices,
        "getevent_available": bool(getevent_keys),
        "getevent_dev_names": getevent_dev_names,
        "versions": versions,
        "notes_role": notes_role,
        "thememanager": {
            "present": "com.android.thememanager" in packages,
            "disabled": "com.android.thememanager" in disabled,
        },
    }


def baseline_lines(data: dict) -> list[str]:
    lines = [
        "# Rollback baseline recorded by tools/probe-device.sh BEFORE any Pad Suite change.",
        "# Sourced by tools/rollback.sh. Empty value + *_UNSET=1 means the setting did not exist.",
        f"HOME_COMPONENT={shlex.quote(data['home'])}",
    ]
    for namespace, key, var in BASELINE_SETTINGS:
        value = data["settings"][namespace].get(key)
        lines.append(f"{var}={shlex.quote(value or '')}")
        lines.append(f"{var}_UNSET={0 if value is not None else 1}")
    lines.append(f"MIUI_OPTIMIZATION={shlex.quote(data['props'].get('persist.sys.miui_optimization', ''))}")
    third = set(data["third_party"])
    for var, pkg in [
        ("PREEXISTING_KEYMAPPER", "io.github.sds100.keymapper"),
        ("PREEXISTING_PADSTUDIO", "com.charan.padstudio"),
        ("PREEXISTING_LAWNCHAIR", "app.lawnchair"),
        ("PREEXISTING_LAWNICONS", "app.lawnchair.lawnicons"),
        ("PREEXISTING_ARCTICONS", "com.donnnno.arcticons"),
        ("PREEXISTING_OPENCAMERA", "net.sourceforge.opencamera"),
    ]:
        lines.append(f"{var}={1 if pkg in third else 0}")
    return lines


def profile_markdown(data: dict, raw: Path) -> str:
    p = data["props"]
    out = [
        "# Device profile",
        "",
        f"Generated by `tools/probe-device.sh` from raw dumps in `{raw.name}` (kept locally in `docs/device/raw/`, not committed).",
        "",
        "## Identity",
        "",
        "| Property | Value |",
        "|---|---|",
    ]
    for key in IDENTITY_PROPS:
        out.append(f"| `{key}` | {p.get(key, '*(not set)*') or '*(empty)*'} |")
    out += [
        "",
        f"**Branch:** `{data['codename'] or '?'}` → {data['known']}",
        "",
        "```",
        data["wm"] or "(wm size/density not captured)",
        "```",
        "",
        "## Launcher, apps, roles",
        "",
        f"- Default HOME: `{data['home'] or '(not captured)'}`",
        f"- Launchable activities: {len(data['launchables'])}; installed packages: {len(data['packages'])}; user-installed: {len(data['third_party'])}",
        f"- Notes role holder: `{data['notes_role'] or '(none / not captured)'}`",
        f"- Themes app (`com.android.thememanager`): present={data['thememanager']['present']}, disabled={data['thememanager']['disabled']}",
        "",
        "| Package | Version |",
        "|---|---|",
    ]
    for pkg, version in sorted(data["versions"].items()):
        out.append(f"| `{pkg}` | {version} |")
    out += ["", "## Input devices (from `dumpsys input` → Event Hub)", ""]
    if not data["devices"]:
        out.append("*No Event Hub devices parsed — check `dumpsys_input.txt` in the raw folder.*")
    else:
        out += ["| id | name | bus | vendor:product | classes | pen? |", "|---|---|---|---|---|---|"]
        for d in data["devices"]:
            flag = "**yes** — " + "; ".join(d.reasons) if d.is_pen_candidate else ""
            out.append(f"| {d.id} | {d.name} | {d.bus} | {d.vendor}:{d.product} | {d.classes} | {flag} |")
    out += [
        "",
        f"`getevent -lp` readable without root: **{'yes' if data['getevent_available'] else 'no / empty'}**",
        "",
        "## Settings that mention pens, stylus, shortcuts or keys",
        "",
    ]
    any_setting = False
    for namespace, values in data["interesting_settings"].items():
        for key, value in sorted(values.items()):
            any_setting = True
            out.append(f"- `{namespace}` `{key}` = `{redact(value)}`")
    if not any_setting:
        out.append("- *(none found)*")
    out += [
        "",
        "## Rollback baseline",
        "",
        "Recorded in `docs/device/baseline.env` the first time the probe ran (never overwritten unless `--rebaseline`).",
        "",
    ]
    return "\n".join(out) + "\n"


def pen_json(data: dict) -> dict:
    return {
        "codename": data["codename"],
        "model": data["props"].get("ro.product.model", ""),
        "android_sdk": data["props"].get("ro.build.version.sdk", ""),
        "hyperos": data["props"].get("ro.mi.os.version.incremental", ""),
        "getevent_available": data["getevent_available"],
        "pen_devices": [asdict(d) for d in data["pen_devices"]],
        "all_devices": [asdict(d) for d in data["devices"]],
    }


def main(argv: list[str]) -> int:
    if len(argv) < 3:
        print(__doc__, file=sys.stderr)
        return 64
    raw = Path(argv[1])
    device_dir = Path(argv[2])
    rebaseline = "--rebaseline" in argv
    data = build(raw)
    device_dir.mkdir(parents=True, exist_ok=True)
    (device_dir / "PROFILE.md").write_text(profile_markdown(data, raw))
    (device_dir / "pen-device.json").write_text(json.dumps(pen_json(data), indent=2) + "\n")
    baseline = device_dir / "baseline.env"
    if rebaseline or not baseline.exists():
        baseline.write_text("\n".join(baseline_lines(data)) + "\n")
        print(f"baseline written: {baseline}")
    else:
        print(f"baseline kept (already exists): {baseline}")
    print(f"profile: {device_dir / 'PROFILE.md'}  ({len(data['devices'])} input devices, {len(data['pen_devices'])} pen candidates)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
