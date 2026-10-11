#!/usr/bin/env python3
"""Fetch Material Symbols Rounded at a pinned commit and turn them into PadStudio's sym_* icons (plan §3.5).

  python3 -I -B tools/lib/fetch_symbols.py [--work DIR] [--offline] [--dry-run]

Each slot in SLOTS lists symbol names in priority order. The first whose 24 px Rounded SVG exists at COMMIT
(HTTP 200) wins; a 404 moves on to the next name. Any other HTTP or network failure stops the run, so a
flaky connection can never quietly pick a lower-priority icon. A slot with no existing name stops the run
too. The repo is written only after every slot has resolved and converted.

Outputs, per slot:
  padstudio/app/src/main/res/drawable/sym_<slot>.xml        outline, as an Android vector drawable
  padstudio/app/src/main/res/drawable/sym_<slot>_fill.xml   filled (selected or active), when the fill1 SVG exists
  docs/redesign/assets/symbols/<slot>.svg, <slot>_fill.svg  the source SVGs for the HTML preview (byte for byte,
                                                            except old exports, rewritten as below)
  docs/redesign/assets/symbols/map.json                     {slot: chosen symbol name}

Downloads and conversion happen in --work (default: a temporary directory). The repo is written last, in
one short step: each file through a rename, unchanged files left alone (other agents may be compiling),
then sym_*.xml / *.svg left over from an earlier slot list are removed.

Each SVG must be exactly one <path d=…> on viewBox "0 -960 960 960". Symbols unchanged since 2022 (e.g.
assistant) still have the old export, 24 × 24 with no viewBox; their path is moved exactly onto the current
grid, (x, y) → (40x, 40y − 960), and their preview SVG is rewritten in the current form. Any other shape of
file stops the run. The drawable keeps the 960 viewport and moves the path down 960 in a <group>. Its
pathData is the path verbatim when Android's tokenizer (android_nodes below, a port of
androidx.core.graphics.PathParser, which the framework's native parser and Compose follow) reads the same
numbers as the SVG grammar; otherwise it is rewritten with explicit separators (compact arc flags like
"a5 5 0 0110 10" are valid SVG that Android misreads).

Icons: google/material-design-icons at COMMIT, Apache-2.0.
"""

from __future__ import annotations

import argparse
import http.client
import json
import os
import re
import shutil
import sys
import tempfile
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal
from pathlib import Path
from xml.etree import ElementTree as ET
from xml.sax.saxutils import quoteattr

COMMIT = "49d4db35df873165d6bd6ba09b063c7dafbac2f4"
BASE = f"https://raw.githubusercontent.com/google/material-design-icons/{COMMIT}/symbols/web"
ROOT = Path(__file__).resolve().parents[2]
RES_DIR = ROOT / "padstudio/app/src/main/res/drawable"
SVG_DIR = ROOT / "docs/redesign/assets/symbols"
RETRY_DELAYS = (2.0, 5.0, 10.0)  # seconds before each retry of a download that failed with anything but 404
WORKERS = 8

SLOTS: dict[str, tuple[str, ...]] = {
    # Navigation rail (plan §10 Shell)
    "today": ("space_dashboard",),
    "pen": ("stylus",),
    "home": ("apps",),
    "looks": ("palette",),
    "setup": ("checklist",),
    # Pen actions, one per PenIntent (core/.../pen/PenModel.kt). The slot is the enum name in lower case,
    # except HOME → home_action, OPEN_LIBRARY → library, DO_NOT_DISTURB → dnd.
    "shutter": ("photo_camera",),
    "open_camera": ("add_a_photo", "photo_camera_front", "camera"),
    "flashlight": ("flashlight_on", "flashlight"),
    "next_page": ("arrow_forward_ios", "chevron_right", "navigate_next"),
    "previous_page": ("arrow_back_ios", "chevron_left", "navigate_before"),
    "undo": ("undo",),
    "redo": ("redo",),
    "open_notes": ("edit_note", "sticky_note_2", "note_alt"),
    "play_pause": ("play_pause", "play_arrow"),
    "next_track": ("skip_next",),
    "previous_track": ("skip_previous",),
    "volume_up": ("volume_up",),
    "volume_down": ("volume_down",),
    "back": ("arrow_back",),
    "home_action": ("home",),
    "recents": ("select_window", "filter_none", "view_carousel", "layers"),
    "last_app": ("swap_horiz", "sync_alt"),
    "split_screen": ("splitscreen_left", "splitscreen", "vertical_split"),
    "library": ("grid_view", "apps"),
    "screenshot": ("screenshot_region", "screenshot", "crop_free"),
    "notifications": ("notifications",),
    "quick_settings": ("toggle_on", "tune"),
    "assistant": ("assistant", "mic", "auto_awesome"),
    "dnd": ("do_not_disturb_on", "do_not_disturb"),
    "rotation_lock": ("screen_lock_rotation", "screen_rotation", "screen_rotation_alt"),
    # Pen controls (Focus Pen Pro)
    "squeeze": ("pinch", "compress", "unfold_less"),
    "slide_up": ("swipe_up", "keyboard_double_arrow_up"),
    "slide_down": ("swipe_down", "keyboard_double_arrow_down"),
    "double_tap": ("touch_app", "ads_click", "gesture"),
    # Interface
    "search": ("search",),
    "ok": ("check_circle",),
    "error": ("error",),
    "warning": ("warning",),
    "info": ("info",),
    "help": ("help",),
    "close": ("close",),
    "expand_more": ("expand_more",),
    "expand_less": ("expand_less",),
    "more": ("more_vert",),
    "add": ("add",),
    "edit": ("edit",),
    "refresh": ("refresh",),
    "settings": ("settings",),
    "widgets": ("widgets",),
    "wallpaper": ("wallpaper",),
    "grid": ("grid_view",),
    "open": ("open_in_new",),
    "chevron": ("chevron_right",),
    "pause": ("pause_circle",),
    "play": ("play_circle",),
    "keyboard": ("keyboard",),
    "dark": ("dark_mode",),
    "light": ("light_mode",),
    "lock": ("lock",),
    "code": ("code", "developer_mode"),
    "send": ("send",),
    "time": ("schedule",),
    "names_hidden": ("visibility_off",),
    "test_pen": ("draw", "gesture"),
    "hyperos": ("phone_android", "tablet_android"),
    "fit_warn": ("aspect_ratio", "crop"),
}


class Failure(Exception):
    """Stops the run; main prints it and exits non-zero."""


# ---------------------------------------------------------------- download


def svg_file(name: str, fill: bool) -> str:
    return f"{name}_fill1_24px.svg" if fill else f"{name}_24px.svg"


def download(url: str) -> bytes | None:
    """The body at url, or None for HTTP 404. Any other failure is retried, then raised as a Failure."""
    problem = ""
    for delay in (0.0, *RETRY_DELAYS):
        time.sleep(delay)
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "padstudio-fetch-symbols"})
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            problem = f"HTTP {e.code}"
        except (urllib.error.URLError, http.client.HTTPException, OSError) as e:  # DNS, refused, reset, timeout
            problem = str(getattr(e, "reason", e)) or type(e).__name__
    raise Failure(f"{url}: {problem} (tried {len(RETRY_DELAYS) + 1} times)")


def write_atomic(path: Path, data: bytes) -> None:
    """Write through a dot-file and a rename: readers see the old file or the new one, never half of one.
    The resource merger skips dot-files."""
    tmp = path.with_name(f".{path.name}.tmp")
    tmp.write_bytes(data)
    os.replace(tmp, path)


class Store:
    """The SVGs, cached in one directory. A 404 is remembered as <file>.404; the commit is pinned, so
    neither can go stale."""

    def __init__(self, directory: Path, offline: bool):
        self.dir = directory
        self.offline = offline
        directory.mkdir(parents=True, exist_ok=True)

    def get(self, name: str, fill: bool) -> bytes | None:
        path = self.dir / svg_file(name, fill)
        missing = path.with_name(path.name + ".404")
        if path.is_file():
            return path.read_bytes()
        if missing.is_file():
            return None
        if self.offline:
            raise Failure(f"{path.name} hasn't been downloaded into {self.dir}, and --offline was given")
        body = download(f"{BASE}/{name}/materialsymbolsrounded/{path.name}")
        if body is None:
            missing.touch()
        else:
            write_atomic(path, body)
        return body


def resolve(slots: dict[str, tuple[str, ...]], store: Store) -> tuple[dict[str, str], dict[str, list[str]]]:
    """Each slot's first candidate that exists, and the candidates skipped because they don't.
    Round k asks for the k-th candidate of every slot still open, so nothing past a winner is fetched."""
    chosen: dict[str, str] = {}
    skipped: dict[str, list[str]] = {slot: [] for slot in slots}
    open_slots = {slot: list(names) for slot, names in slots.items()}
    with ThreadPoolExecutor(WORKERS) as pool:
        while open_slots:
            names = sorted({queue[0] for queue in open_slots.values()})
            exists = dict(zip(names, pool.map(lambda n: store.get(n, fill=False) is not None, names)))
            for slot, queue in list(open_slots.items()):
                name = queue.pop(0)
                if exists[name]:
                    chosen[slot] = name
                else:
                    skipped[slot].append(name)
                if exists[name] or not queue:
                    del open_slots[slot]
    unresolved = [slot for slot in slots if slot not in chosen]
    if unresolved:
        detail = "; ".join(f"{slot} (tried {', '.join(skipped[slot])})" for slot in unresolved)
        raise Failure(f"no candidate exists at {COMMIT[:12]} for {len(unresolved)} slot(s): {detail}")
    return {slot: chosen[slot] for slot in slots}, skipped


# ---------------------------------------------------------------- SVG → vector drawable

SVG = "{http://www.w3.org/2000/svg}"
ARITY = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}
NUMBER = re.compile(r"[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?")
WSP = re.compile(r"[ \t\n\r\f]*")
COMMA_WSP = re.compile(r"[ \t\n\r\f]*(,?)[ \t\n\r\f]*")
JAVA_FLOAT = re.compile(r"[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?[fFdD]?")


def svg_path(data: bytes, label: str) -> tuple[str, bool]:
    """The d of the one <path> in a Material Symbols SVG, on the current grid, and whether it was an old
    24 × 24 export that had to be moved there. Any other shape of file is a Failure."""
    try:
        root = ET.fromstring(data)
    except ET.ParseError as e:
        raise Failure(f"{label}: not XML ({e})") from None
    attributes = dict(root.attrib)
    legacy = attributes == {"width": "24", "height": "24"}
    current = attributes.get("viewBox") == "0 -960 960 960" and set(attributes) <= {"width", "height", "viewBox"}
    if root.tag != SVG + "svg" or not (legacy or current):
        raise Failure(f'{label}: expected <svg viewBox="0 -960 960 960"> (or the old 24 × 24 export), got {root.tag} {attributes}')
    children = list(root)
    if len(children) != 1 or children[0].tag != SVG + "path" or set(children[0].attrib) != {"d"} or len(children[0]):
        raise Failure(f"{label}: expected exactly one <path d=…> and no other element or attribute")
    d = children[0].get("d", "").strip()
    if not legacy:
        return d, False
    try:
        return regrid(d), True
    except ValueError as e:
        raise Failure(f"{label}: path data isn't valid SVG: {e}") from None


# What each argument of a command is, for regrid: x or y (a coordinate; one of a relative command is only
# scaled), r (an arc radius: scaled) or . (an arc's angle or flag: kept).
ROLES = {"M": "xy", "L": "xy", "T": "xy", "H": "x", "V": "y", "C": "xyxyxy", "S": "xyxy", "Q": "xyxy", "A": "rr...xy", "Z": ""}


def regrid(d: str) -> str:
    """A path on the old 24 × 24 grid (y down from 0) moved exactly onto the current one, viewBox
    0 -960 960 960: (x, y) → (40x, 40y − 960), in decimal arithmetic so no digit is rounded."""
    commands = []
    for n, (letter, args) in enumerate(svg_commands(d)):
        roles = ROLES[letter.upper()]
        values = []
        for k, text in enumerate(args):
            role = roles[k % len(roles)]
            # A path's first moveto is absolute even when written as m (SVG paths, "moveto" commands).
            absolute = letter.isupper() or (n == 0 and k < 2)
            value = Decimal(text)
            if role in "xyr":
                value *= 40
            if role == "y" and absolute:
                value -= 960
            plain = format(value, "f")
            plain = plain.rstrip("0").rstrip(".") if "." in plain else plain
            values.append("0" if plain in ("-0", "") else plain)
        commands.append(letter + " ".join(values))
    return "".join(commands)


def current_svg(d: str) -> bytes:
    """An SVG in the form Material Symbols exports today, for a path already on the current grid."""
    return f'<svg xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 -960 960 960" width="24"><path d={quoteattr(d)}/></svg>'.encode()


def svg_commands(d: str) -> list[tuple[str, list[str]]]:
    """Path data split by the SVG grammar: [(command letter, [argument text, …]), …].
    Arc flags are single characters. Raises ValueError for anything the grammar rejects."""
    commands: list[tuple[str, list[str]]] = []
    i = WSP.match(d).end()
    while i < len(d):
        letter = d[i]
        arity = ARITY.get(letter.upper())
        if arity is None:
            raise ValueError(f"expected a command at {i}: {d[i:i + 16]!r}")
        if not commands and letter not in "Mm":
            raise ValueError("path data must start with a moveto")
        i = WSP.match(d, i + 1).end()
        args: list[str] = []
        while arity:  # one or more argument groups
            for k in range(arity):
                if k:
                    i = COMMA_WSP.match(d, i).end()
                if letter in "Aa" and k in (3, 4):
                    if d[i:i + 1] not in ("0", "1"):
                        raise ValueError(f"expected an arc flag at {i}: {d[i:i + 16]!r}")
                    args.append(d[i])
                    i += 1
                else:
                    number = NUMBER.match(d, i)
                    if not number:
                        raise ValueError(f"expected a number at {i}: {d[i:i + 16]!r}")
                    args.append(number.group())
                    i = number.end()
            separator = COMMA_WSP.match(d, i)
            i = separator.end()
            if i >= len(d) or d[i].upper() in ARITY:
                if separator.group(1):
                    raise ValueError(f"stray comma before {i}")
                break
        commands.append((letter, args))
    return commands


def java_float(text: str) -> float:
    """Float.parseFloat: surrounding characters up to ' ' are trimmed; anything else unparsable throws."""
    text = text.strip("".join(map(chr, range(33))))
    if not JAVA_FLOAT.fullmatch(text):
        raise ValueError(f"Float.parseFloat({text!r}) throws")
    return float(text.rstrip("fFdD"))


def android_nodes(d: str) -> list[tuple[str, list[float]]]:
    """How androidx.core.graphics.PathParser.createNodesFromPathData splits path data: one node per letter
    (e and E excepted), its floats cut at ' ', ',', a '-' that doesn't follow e/E, or a second '.'."""

    def next_start(end: int) -> int:
        while end < len(d) and not (d[end].isascii() and d[end].isalpha() and d[end] not in "eE"):
            end += 1
        return end

    def extract(s: str, start: int) -> tuple[int, bool]:
        second_dot = exponential = False
        index = start
        while index < len(s):
            previous_exponential, exponential = exponential, False
            c = s[index]
            if c in " ,":
                return index, False
            if c == "-" and index != start and not previous_exponential:
                return index, True
            if c == ".":
                if second_dot:
                    return index, True
                second_dot = True
            if c in "eE":
                exponential = True
            index += 1
        return index, False

    def get_floats(s: str) -> list[float]:
        if s[0] in "zZ":
            return []
        floats: list[float] = []
        start = 1
        while start < len(s):
            end, keep_sign = extract(s, start)
            if start < end:
                floats.append(java_float(s[start:end]))
            start = end if keep_sign else end + 1
        return floats

    nodes: list[tuple[str, list[float]]] = []
    start, end = 0, 1
    while end < len(d):
        end = next_start(end)
        s = d[start:end].strip("".join(map(chr, range(33))))
        if s:
            nodes.append((s[0], get_floats(s)))
        start = end
        end += 1
    if end - start == 1 and start < len(d):
        nodes.append((d[start], []))
    return nodes


def android_reads_same(d: str) -> bool:
    """True when Android's tokenizer gets the same commands and numbers as the SVG grammar."""
    try:
        grammar = [(letter, [java_float(a) for a in args]) for letter, args in svg_commands(d)]
        return android_nodes(d) == grammar
    except ValueError:
        return False


def android_path(d: str, label: str) -> str:
    """Path data Android reads as the SVG means it: d itself, or d with explicit separators."""
    try:
        commands = svg_commands(d)
    except ValueError as e:
        raise Failure(f"{label}: path data isn't valid SVG: {e}") from None
    if android_reads_same(d):
        return d
    spaced = "".join(letter + " ".join(args) for letter, args in commands)
    if not android_reads_same(spaced):
        raise Failure(f"{label}: Android would misread this path data, even with explicit separators")
    return spaced


def drawable(path_data: str, name: str, fill: bool) -> str:
    kind = "filled" if fill else "outline"
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- Generated by tools/lib/fetch_symbols.py; edit the script, not this file.\n"
        f'     Material Symbols Rounded "{name}", {kind}: google/material-design-icons@{COMMIT}, Apache-2.0. -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        '    android:viewportWidth="960"\n'
        '    android:viewportHeight="960">\n'
        '    <group android:translateY="960">\n'
        "        <path\n"
        '            android:fillColor="@android:color/white"\n'
        f"            android:pathData={quoteattr(path_data)} />\n"
        "    </group>\n"
        "</vector>\n"
    )


# ---------------------------------------------------------------- run


def check_slots(slots: dict[str, tuple[str, ...]]) -> None:
    for slot, names in slots.items():
        # A resource name; and no slot may end in _fill, or its files would collide with another slot's fill.
        if not re.fullmatch(r"[a-z][a-z0-9_]*", slot) or slot.endswith("_fill"):
            raise Failure(f"slot {slot!r} can't name a resource (lower case, digits, _; not ending in _fill)")
        if not names or any(not re.fullmatch(r"[a-z0-9_]+", n) for n in names):
            raise Failure(f"slot {slot!r}: candidates must be Material Symbols names, got {names!r}")


def install(src: Path, dst: Path, patterns: tuple[str, ...]) -> tuple[int, int, int]:
    """Make dst's files that match patterns equal to src's files: (written, unchanged, removed)."""
    dst.mkdir(parents=True, exist_ok=True)
    files = {p.name: p.read_bytes() for p in sorted(src.iterdir())}
    written = unchanged = removed = 0
    for name, data in files.items():
        target = dst / name
        if target.is_file() and target.read_bytes() == data:
            unchanged += 1
        else:
            write_atomic(target, data)
            written += 1
    for pattern in patterns:
        for old in sorted(dst.glob(pattern)):
            if old.name not in files:
                old.unlink()
                removed += 1
    return written, unchanged, removed


def shown(path: Path) -> str:
    return str(path.relative_to(ROOT)) if path.is_relative_to(ROOT) else str(path)


def run(work: Path, offline: bool, res_dir: Path | None, svg_dir: Path | None) -> None:
    check_slots(SLOTS)
    store = Store(work / "download", offline)
    chosen, skipped = resolve(SLOTS, store)
    names = sorted(set(chosen.values()))
    with ThreadPoolExecutor(WORKERS) as pool:
        fills = dict(zip(names, pool.map(lambda n: store.get(n, fill=True), names)))

    stage = work / "out"
    shutil.rmtree(stage, ignore_errors=True)
    drawables, symbols = stage / "drawable", stage / "symbols"
    drawables.mkdir(parents=True)
    symbols.mkdir()
    regridded, spaced = [], []
    for slot, name in chosen.items():
        variants = [("", store.get(name, fill=False), False)]
        if fills[name] is not None:
            variants.append(("_fill", fills[name], True))
        for suffix, svg, fill in variants:
            label = f"{slot}: {svg_file(name, fill)}"
            d, legacy = svg_path(svg, label)
            path_data = android_path(d, label)
            if legacy:
                regridded.append(label)
            if path_data != d:
                spaced.append(label)
            (drawables / f"sym_{slot}{suffix}.xml").write_text(drawable(path_data, name, fill), encoding="utf-8")
            (symbols / f"{slot}{suffix}.svg").write_bytes(current_svg(d) if legacy else svg)
        notes = [] if fills[name] is not None else ["no fill1 SVG"]
        if skipped[slot]:
            notes.append(f"not at this commit: {', '.join(skipped[slot])}")
        print(f"  {slot:<15} {name:<22} {'; '.join(notes)}".rstrip())
    (symbols / "map.json").write_text(json.dumps(chosen, indent=2) + "\n", encoding="utf-8")

    n_xml = len(list(drawables.iterdir()))
    n_svg = len(list(symbols.glob("*.svg")))
    print(f"{len(chosen)} slots, {len(names)} distinct symbols: {n_xml} drawables, {n_svg} SVGs and map.json in {stage}")
    for label in regridded:
        print(f"  old 24 x 24 export, moved onto the 960 grid: {label}")
    for label in spaced:
        print(f"  pathData rewritten with explicit separators (Android would misread the original): {label}")
    if res_dir is None or svg_dir is None:
        print("--dry-run: the repo was not touched")
        return
    for src, dst, patterns in ((drawables, res_dir, ("sym_*.xml",)), (symbols, svg_dir, ("*.svg", "map.json"))):
        written, unchanged, removed = install(src, dst, patterns)
        print(f"{shown(dst)}: {written} written, {unchanged} unchanged, {removed} stale removed")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--work", type=Path, help="download cache and staging directory (default: a temporary one)")
    parser.add_argument("--offline", action="store_true", help="use only SVGs already downloaded into --work")
    parser.add_argument("--dry-run", action="store_true", help="resolve and convert in --work; don't write the repo")
    parser.add_argument("--res", type=Path, default=RES_DIR, help="where sym_*.xml go (default: the app's res/drawable)")
    parser.add_argument("--svg", type=Path, default=SVG_DIR, help="where the SVGs and map.json go (default: %(default)s)")
    args = parser.parse_args(argv)
    if args.offline and args.work is None:
        parser.error("--offline needs --work")
    with tempfile.TemporaryDirectory(prefix="fetch_symbols-") as temporary:
        work = (args.work or Path(temporary)).resolve()
        try:
            run(work, args.offline, None if args.dry_run else args.res, None if args.dry_run else args.svg)
        except Failure as e:
            print(f"fetch_symbols: FAILED: {e}", file=sys.stderr)
            print("fetch_symbols: nothing in the repo was written", file=sys.stderr)
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
