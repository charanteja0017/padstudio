"""Build the PadStudio 2 preview assets from the owner's own screenshots (plan section 8).

  python3 -I -B make_assets.py <detA.json> <detB.json> [--write]

Without --write it computes and prints every check and writes crops to the scratchpad only.
With --write it also writes, in the repo:
  docs/redesign/assets/icons/<slug>.png   168 x 168 RGBA; alpha = coverage of |x|^4.4 + |y|^4.4 <= 1
  docs/redesign/assets/manifest.json      [{file, label, package, confidence, source, slot, box, evidence}]
  docs/redesign/assets/before/*.jpg       1600 px wide, JPEG quality 85

Boxes come from detect.py. Names come from the launcher's own record of what sits where
(docs/device/raw/20261011-162623/camera_activity_top_rot1.txt, 16:26: the home page cells and the
dock list), matched to the boxes by grid position and cross-checked with the installed packages.

Edge colours: the home screenshots show the same icons on two backgrounds (pink A, dark B), drawn
pixel-identically, so each edge pixel's true alpha and colour follow from difference matting:
C_A - C_B = (1 - a)(bg_A - bg_B). That removes the old wallpaper from the edges. Icons seen on one
background only are cleaned with the launcher's alpha measured that way.
"""
import json
import pathlib
import tempfile
import os
import re
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

REPO = str(pathlib.Path(__file__).resolve().parents[3])  # repo root, from tools/lib/redesign_assets/
SCRATCH = os.environ.get("PAD_ASSET_SCRATCH") or os.path.join(tempfile.gettempdir(), "padstudio-assets")
os.makedirs(SCRATCH, exist_ok=True)
SRC = {
    "A": "docs/test-logs/2026-10-11-home-widget-v2.png",
    "B": "docs/test-logs/2026-10-11-home-brand-icon.png",
}
DUMP = "docs/device/raw/20261011-162623/camera_activity_top_rot1.txt"
PACKAGES = "docs/device/backup/2026-10-11/packages.txt"
LAUNCHABLES = "docs/device/raw/20261011-162623/launchables.txt"
ICON_DIR = "docs/redesign/assets/icons"
MANIFEST = "docs/redesign/assets/manifest.json"
BEFORE_DIR = "docs/redesign/assets/before"
BEFORE = [
    ("docs/test-logs/2026-10-11-padstudio-user-now.png", "before-pen.jpg"),
    ("docs/test-logs/2026-10-11-padstudio-pen-top.png", "before-mappings.jpg"),
    ("docs/test-logs/2026-10-11-padstudio-penkind.png", "before-penkind.jpg"),
    ("docs/test-logs/2026-10-11-pen-test-live.png", "before-pentest.jpg"),
    ("docs/test-logs/2026-10-11-home-brand-icon.png", "before-home.jpg"),
]

N = 4.4
SIZE = 168
HALF = SIZE / 2.0
SS = 16  # supersampling per axis for mask coverage

# Launcher label -> (file slug, package or None, extra evidence). Packages are asserted below to be
# in packages.txt and to have a launcher activity.
IDENTITY = {
    "Flipkart": ("flipkart", "com.flipkart.android", ""),
    "Meesho": ("meesho", "com.meesho.supply", ""),
    "War Thunder Mobile": ("war-thunder-mobile", "com.gaijingames.wtm", ""),
    "Сonquistadorio": ("conquistadorio", "com.MorionStudio.ConquistadorioFull",
                            "The label's first letter is Cyrillic С (U+0421), as the app ships it."),
    "WiFiBar": ("wifibar", "com.charan.wifibar", ""),
    "PadStudio": ("padstudio", "com.charan.padstudio", ""),
    "Key Mapper": ("key-mapper", "io.github.sds100.keymapper", ""),
    "Freenotes": ("freenotes", "ai.note.notepad.memo.journal.freenote",
                  "The only freenote package installed; ADR-001 records it as freenote."),
    "ByteByteGo | Technical Interview Prep": ("bytebytego", None,
                  "Launcher item type 14, a shortcut rather than an installed app (apps are type 0); "
                  "no ByteByteGo package is installed. Full launcher title: "
                  "'ByteByteGo | Technical Interview Prep'."),
    "ReadEra": ("readera", "org.readera",
                "Key Mapper's 'Reading & PDF' group chip also shows this icon; that group's first "
                "constraint is org.readera (test-logs/2026-10-11-state-check.png)."),
    "Notes": ("notes", "com.miui.notes", ""),
    "Gmail": ("gmail", "com.google.android.gm", ""),
    "WhatsApp": ("whatsapp", "com.whatsapp", ""),
    "Brave": ("brave", "com.brave.browser", ""),
    "Claude": ("claude", "com.anthropic.claude", ""),
    "Camera": ("camera", "com.android.camera", ""),
    "Play Store": ("play-store", "com.android.vending", ""),
}
DISPLAY = {"ByteByteGo | Technical Interview Prep": "ByteByteGo"}
EXCLUDE = {"PadStudio"}  # PadStudio's own icons are not app content for the preview

# The dock's recent-apps part is not in the launcher dump. Those icons are named by pixel match with
# the named icons of the same screenshot, or, when nothing matches, here, with the evidence.
RECENT_UNMATCHED = {
    ("A", 3): ("Camera", "Dock, recent-apps part (not in the launcher dump). Same icon as the one the launcher "
                         "names 'Camera' at page cell (3,3) (dumps 15:30, 15:31, 16:26; that page is "
                         "test-logs/2026-10-11-keymapper-restore-5.png) and as Key Mapper's 'Camera' group chip "
                         "(constraint com.android.camera)."),
    ("B", 1): ("PadStudio", "Dock, recent-apps part: PadStudio's violet-stroke brand test icon."),
    ("B", 2): ("Play Store", "Dock, recent-apps part (not in the launcher dump). Unmistakable Google Play icon."),
}


def rel(p):
    return os.path.join(REPO, p)


def load_rgb(path):
    arr = np.asarray(Image.open(rel(path)))
    if arr.ndim == 3 and arr.shape[2] == 4:
        assert arr[..., 3].min() == 255, f"{path} has transparent pixels"
    return arr[..., :3].astype(np.float64)


def coverage(n=N, h=HALF, dx=0.0, dy=0.0):
    o = (np.arange(SS) + 0.5) / SS
    t = (np.arange(SIZE)[:, None] + o[None, :]).ravel() - HALF
    X = (t - dx) / h
    Y = (t - dy) / h
    inside = (np.abs(X[None, :]) ** n + np.abs(Y[:, None]) ** n) <= 1.0
    return inside.reshape(SIZE, SS, SIZE, SS).mean(axis=(1, 3))


JJ, II = np.meshgrid(np.arange(SIZE) + 0.5, np.arange(SIZE) + 0.5)
UX, UY = (JJ - HALF) / HALF, (II - HALF) / HALF
R = np.maximum((np.abs(UX) ** N + np.abs(UY) ** N) ** (1.0 / N), 1e-6)
BAND = np.abs((R - 1.0) * HALF) <= 3.0  # pixels within 3 px of the outline


def samp(rgb, xs, ys):
    return np.stack([ndimage.map_coordinates(rgb[..., c], [ys - 0.5, xs - 0.5], order=1, mode="nearest")
                     for c in range(3)], -1)


def crop(rgb, cx, cy):
    return rgb[cy - SIZE // 2:cy + SIZE // 2, cx - SIZE // 2:cx + SIZE // 2].copy()


def background(rgb, cx, cy, dist=4.0):
    """Background behind every box pixel, read `dist` px outside the outline along the same ray."""
    s = (1.0 + dist / HALF) / R
    return samp(rgb, cx + UX * s * HALF, cy + UY * s * HALF)


def fill_from_nearest(col, ok):
    idx = ndimage.distance_transform_edt(~ok, return_distances=False, return_indices=True)
    return col[idx[0], idx[1]]


def matte_pair(rgbA, a, rgbB, b):
    """Difference matting of one icon seen on two backgrounds. Returns (alpha, colour, min |bgA-bgB|)."""
    CA, CB = crop(rgbA, a["cx"], a["cy"]), crop(rgbB, b["cx"], b["cy"])
    bA, bB = background(rgbA, a["cx"], a["cy"]), background(rgbB, b["cx"], b["cy"])
    d = bA - bB
    dn = (d ** 2).sum(-1)
    one_minus = ((CA - CB) * d).sum(-1) / np.maximum(dn, 1e-6)
    alpha = np.clip(1.0 - one_minus, 0.0, 1.0)
    alpha[~BAND & (R < 1.0)] = 1.0
    alpha[~BAND & (R > 1.0)] = 0.0
    col = ((CA + CB) - (1.0 - alpha)[..., None] * (bA + bB)) / (2.0 * np.maximum(alpha, 1e-6)[..., None])
    ok = alpha >= 0.25
    col = np.clip(fill_from_nearest(col, ok), 0, 255)
    return alpha, col, float(np.sqrt(dn[BAND & (R <= 1.0 + 1.0 / HALF)]).min())


def clean_single(rgb, box, a_launcher):
    C = crop(rgb, box["cx"], box["cy"])
    bg = background(rgb, box["cx"], box["cy"])
    a = a_launcher[..., None]
    col = (C - (1.0 - a) * bg) / np.maximum(a, 1e-6)
    ok = a_launcher >= 0.25
    return np.clip(fill_from_nearest(col, ok), 0, 255)


def rgba(col, alpha):
    return Image.fromarray(np.dstack([np.round(col), np.round(alpha * 255.0)]).astype(np.uint8), "RGBA")


def parse_dump():
    t = open(rel(DUMP), errors="replace").read()
    ev = []
    for m in re.finditer(r"\{ tag (\d+) = ([\w.$]+)\{[^}]*\}(\((.*?) cellX = (\d+) cellY = (\d+)\))?", t):
        ev.append((m.start(), "child", int(m.group(1)), m.group(2).split(".")[-1], m.group(4),
                   None if m.group(5) is None else (int(m.group(5)), int(m.group(6)))))
    for m in re.finditer(r"OccupiedCells:", t):
        ev.append((m.start(), "end", None, None, None, None))
    ev.sort(key=lambda e: e[0])
    pages, cur = [], None
    for _, kind, tag, cls, label, cell in ev:
        if kind == "child" and tag == 0:
            cur = []
        elif kind == "child" and cur is not None:
            cur.append((cls, label, cell))
        elif kind == "end" and cur is not None:
            pages.append(cur)
            cur = None
    hm = re.search(r"HotSeats Items:\s*\[(.*?)\]\n", t)
    hot = [(int(i), int(ty), lab) for i, ty, lab in
           re.findall(r"Item\(id=(\d+) type=(\d+) label = (.*?)\)(?=, Item|$)", hm.group(1))]
    rects = re.findall(r"Rect\(0, 0 - (\d+), (\d+)\) \|\s+drawable = ([\w.$]+)", t)
    return pages, hot, rects


def boxes(det_path):
    d = json.load(open(det_path))
    return [dict(cx=int(round(b["cx"])), cy=int(round(b["cy"])), fit=(b["cx"], b["cy"], b["size"]), score=b["score"])
            for b in d["boxes"] if b["accepted"]]


def assign(bxs):
    """Home-page cells and dock positions from the measured geometry only."""
    home = [b for b in bxs if b["cy"] < 1600]
    dock = sorted([b for b in bxs if b["cy"] >= 1600], key=lambda b: b["cx"])
    xs = sorted({b["cx"] for b in home})
    pitch = float(np.median(np.diff(xs)))
    x0, y0 = min(xs), min(b["cy"] for b in home)
    for b in home:
        c, r = (b["cx"] - x0) / pitch, (b["cy"] - y0) / pitch
        b["cell"] = (int(round(c)), int(round(r)))
        b["grid_resid"] = round(max(abs(c - round(c)), abs(r - round(r))) * pitch, 2)
    gaps = np.diff([b["cx"] for b in dock])
    dpitch = float(np.median(gaps))
    split = [i for i, g in enumerate(gaps) if g > 1.1 * dpitch]
    assert len(split) == 1, f"expected one separator gap in the dock, got {gaps}"
    s = split[0] + 1
    for i, b in enumerate(dock):
        b["dock"] = ("hot", i + 1) if i < s else ("recent", i - s + 1)
    return home, dock, pitch, dpitch, gaps.tolist(), sorted({b["cy"] for b in dock})


def inner_mad(p, q):
    m = R < 0.95
    return float(np.abs(p - q)[m].mean())


def main():
    detA, detB = sys.argv[1], sys.argv[2]
    write = "--write" in sys.argv
    pkgs = {l.strip()[len("package:"):] for l in open(rel(PACKAGES)) if l.startswith("package:")}
    launch = {l.split("/")[0].strip() for l in open(rel(LAUNCHABLES)) if "/" in l}
    pages, hot, rects = parse_dump()
    sizes = sorted({(w, h) for w, h, cls in rects if cls.endswith("LayerAdaptiveIconDrawable")})
    print(f"launcher dump: {len(pages)} page blocks, dock list {len(hot)} items; "
          f"adaptive icon drawable bounds {sizes}")

    rgb = {k: load_rgb(v) for k, v in SRC.items()}
    found = {}
    for key, det in (("A", detA), ("B", detB)):
        bx = boxes(det)
        home, dock, pitch, dpitch, gaps, dock_y = assign(bx)
        occ = {b["cell"] for b in home}
        match = [p for p in pages if all(cls == "ShortcutIcon" for cls, _, _ in p) and {c for _, _, c in p} == occ]
        print(f"\n[{key}] {SRC[key]}: {len(bx)} boxes; home {len(home)} (pitch {pitch} px, max grid residual "
              f"{max(b['grid_resid'] for b in home)} px), dock {len(dock)} (pitch {dpitch} px, gaps {gaps}, y {dock_y})")
        sigs = {tuple(sorted((c, l) for _, l, c in m)) for m in match}
        print(f"[{key}] launcher pages with exactly this occupancy {sorted(occ)}: {len(match)} "
              f"(distinct contents {len(sigs)})")
        assert len(sigs) == 1
        labels = {c: lab for _, lab, c in match[0]}
        hot_boxes = [b for b in dock if b["dock"][0] == "hot"]
        assert len(hot_boxes) == len(hot), (len(hot_boxes), len(hot))
        for b in home:
            b["label"] = labels[b["cell"]]
            b["slot"] = f"home screen, row {b['cell'][1] + 1}, column {b['cell'][0] + 1}"
            b["why"] = f"The launcher's dump (16:26) names cell ({b['cell'][0]},{b['cell'][1]}) '{b['label']}'."
        for b in hot_boxes:
            i = b["dock"][1]
            b["label"] = hot[i - 1][2]
            b["slot"] = f"dock {i}"
            b["why"] = (f"The launcher's dump (16:26) lists it as dock item {i} of {len(hot)} '{b['label']}' "
                        f"(type {hot[i - 1][1]}).")
        for b in bx:
            b["src"] = key
            b["raw"] = crop(rgb[key], b["cx"], b["cy"])
        named = [b for b in bx if "label" in b]
        for b in [b for b in dock if b["dock"][0] == "recent"]:
            i = b["dock"][1]
            mads = sorted((inner_mad(b["raw"], n["raw"]), n["label"]) for n in named)
            b["slot"] = f"dock, recent apps {i}"
            if mads[0][0] < 3.0:
                b["label"] = mads[0][1]
                b["why"] = (f"Dock, recent-apps part; identical inside the mask to the icon the launcher names "
                            f"'{mads[0][1]}' (mean abs diff {mads[0][0]:.2f} of 255; next best {mads[1][0]:.1f}).")
            else:
                b["label"], b["why"] = RECENT_UNMATCHED[(key, i)]
            print(f"[{key}] recent {i}: nearest named icons {[(round(m, 2), l) for m, l in mads[:3]]} -> {b['label']}")
        for b in sorted(bx, key=lambda b: (b["cy"], b["cx"])):
            print(f"[{key}]   {b['slot']:<32} box x={b['cx'] - 84} y={b['cy'] - 84}  fit=({b['fit'][0]}, {b['fit'][1]}, "
                  f"{b['fit'][2]}) score {b['score']:.3f}  {b['label']}")
        found[key] = bx

    print("\npackage checks:")
    for lab, (slug, pkg, _) in IDENTITY.items():
        if pkg is None:
            print(f"  {lab!r}: no package (not an app)")
            continue
        print(f"  {lab!r}: {pkg} installed={pkg in pkgs} launchable={pkg in launch}")
        assert pkg in pkgs and pkg in launch, lab

    # pair the icons seen on both backgrounds and matte them
    byslot = {k: {b["slot"]: b for b in found[k]} for k in found}
    pairs = []
    for slot, a in byslot["A"].items():
        b = byslot["B"].get(slot)
        if b is not None and not slot.startswith("dock, recent"):
            assert a["label"] == b["label"], (slot, a["label"], b["label"])
            pairs.append((a, b))
    print(f"\nicons seen on both backgrounds: {len(pairs)}")
    alphas = []
    for a, b in pairs:
        al, col, dmin = matte_pair(rgb["A"], a, rgb["B"], b)
        a["alpha_meas"], a["col"], b["col"] = al, col, col
        alphas.append(al)
        print(f"  {a['slot']:<32} {a['label'][:22]:<22} core |A-B| {inner_mad(a['raw'], b['raw']):.3f}; "
              f"min |bgA-bgB| at the edge {dmin:.0f}")
    a_launcher = np.median(np.stack(alphas), axis=0)
    spread = np.percentile(np.abs(np.stack(alphas) - a_launcher)[:, BAND], 95)
    print(f"launcher alpha map: median of {len(alphas)} matted icons; 95th percentile deviation of one icon "
          f"from the median, edge band: {spread:.3f}")

    # which superellipse fits the measured alpha? (instrument check: other n must fit worse)
    fits = []
    for n in np.arange(3.8, 5.21, 0.1):
        for h in np.arange(83.5, 84.51, 0.125):
            c = coverage(n, h)
            fits.append((float(np.sqrt(((c - a_launcher)[BAND] ** 2).mean())), round(float(n), 2), float(h)))
    fits.sort()
    r44 = min(f for f in fits if f[1] == 4.4)
    r44_84 = [f for f in fits if f[1] == 4.4 and abs(f[2] - 84.0) < 1e-9][0]
    print(f"mask fit to the measured alpha (RMS over the edge band): best n={fits[0][1]} h={fits[0][2]} rms {fits[0][0]:.4f}; "
          f"n=4.4 best h={r44[2]} rms {r44[0]:.4f}; n=4.4 h=84 (used) rms {r44_84[0]:.4f}; "
          f"worst tried {fits[-1][1]}/{fits[-1][2]} rms {fits[-1][0]:.4f}")
    for n in (4.0, 4.2, 4.4, 4.6, 5.0):
        f = min(x for x in fits if abs(x[1] - n) < 1e-9)
        print(f"   n={n}: best h={f[2]} rms {f[0]:.4f}")

    cov = coverage()
    print(f"output mask: |x|^{N} + |y|^{N} <= 1 over the {SIZE} px box (half-size {HALF}); "
          f"opaque px {int((cov >= 1).sum())}, partial px {int(((cov > 0) & (cov < 1)).sum())}")

    for key in found:
        for b in found[key]:
            if "col" not in b:
                b["col"] = clean_single(rgb[key], b, a_launcher)

    # check: the single-background cleaning against the two-background result, on the paired icons
    errs = []
    for a, b in pairs:
        for k, x in (("A", a), ("B", b)):
            single = clean_single(rgb[k], x, a_launcher)
            m = BAND & (cov > 0)
            errs.append(np.abs(single - a["col"])[m].mean())
    print(f"single-background cleaning vs matting on the paired icons, edge band: mean abs diff "
          f"{np.mean(errs):.2f}, worst icon {np.max(errs):.2f} (of 255)")

    # rim check: composite on mid grey; same icon from A and B, raw crop vs final
    grey = np.full((SIZE, SIZE, 3), 128.0)
    part = (cov > 0) & (cov < 1)
    def comp(col, al):
        return col * al[..., None] + grey * (1 - al[..., None])
    rim_raw = [np.abs(comp(a["raw"], cov) - comp(b["raw"], cov))[part].mean() for a, b in pairs]
    print(f"edge pixels, same icon cut from A vs from B, on grey: raw crops differ by {np.mean(rim_raw):.1f} "
          f"on average (old wallpapers bleeding in); final icons are one image per pair")
    # Camera: the dock's recent-apps icon vs the icon the launcher names 'Camera' at page cell (3,3),
    # on the page shown in keymapper-restore-5.png (same grid: origin (542, 301), pitch 423 px)
    k5 = load_rgb("docs/test-logs/2026-10-11-keymapper-restore-5.png")
    cam = [b for b in found["A"] if b["label"] == "Camera"][0]
    g = lambda im: im[30:-30, 30:-30].mean(-1)
    def ncc(p, q):
        p, q = p - p.mean(), q - q.mean()
        return float((p * q).sum() / np.sqrt((p * p).sum() * (q * q).sum()))
    cx5, cy5 = 542 + 3 * 423, 301 + 3 * 423
    best = max((ncc(g(cam["raw"]), g(crop(k5, cx5 + dx, cy5 + dy))), dx, dy) for dx in range(-4, 5) for dy in range(-4, 5))
    ctrl = max((ncc(g(cam["raw"]), g(b["raw"])), b["label"]) for b in found["A"] + found["B"] if b["label"] != "Camera")
    cam["why"] += (f" Correlation with that icon {best[0]:.3f}; with any other icon here at most {ctrl[0]:.3f}.")
    print(f"Camera check: NCC with the launcher-named 'Camera' icon in keymapper-restore-5.png {best[0]:.3f} "
          f"(shift {best[1]},{best[2]}); best NCC with any other icon {ctrl[0]:.3f} ({ctrl[1]})")

    chosen = {}
    for key in ("A", "B"):
        for b in sorted(found[key], key=lambda b: (b["cy"], b["cx"])):
            if b["label"] in EXCLUDE or b["label"] in chosen:
                continue
            chosen[b["label"]] = b
    print(f"\nicons for the preview: {len(chosen)}; excluded (PadStudio's own): "
          f"{sorted(b['src'] + ' ' + b['slot'] for k in found for b in found[k] if b['label'] in EXCLUDE)}")

    paired_slots = {a["slot"] for a, _ in pairs}
    manifest = []
    sheet = []
    for key in ("A", "B"):
        for b in sorted(found[key], key=lambda b: (b["cy"], b["cx"])):
            sheet.append(dict(tag=f"{key} {b['slot']}", label=DISPLAY.get(b["label"], b["label"]),
                              excluded=b["label"] in EXCLUDE, chosen=chosen.get(b["label"]) is b))
            rgba(b["col"], cov).save(os.path.join(SCRATCH, f"all-{key}-{b['cx']}-{b['cy']}.png"))
            sheet[-1]["png"] = f"all-{key}-{b['cx']}-{b['cy']}.png"
    json.dump(sheet, open(os.path.join(SCRATCH, "sheet.json"), "w"), ensure_ascii=False, indent=1)

    for lab, b in chosen.items():
        slug, pkg, extra = IDENTITY[lab]
        if b["src"] == "A" and b["slot"] in paired_slots:
            other = byslot["B"][b["slot"]]
            edge = (f"Edge colours separated from the wallpaper by matting against the same icon in {SRC['B']} "
                    f"(box x={other['cx'] - 84}, y={other['cy'] - 84}).")
        else:
            edge = "Edge colours cleaned with the launcher's alpha measured on the icons seen on both backgrounds."
        evidence = " ".join(x for x in (b["why"], f"Package {pkg} is installed (packages.txt)." if pkg else "",
                                        extra, edge) if x)
        manifest.append(dict(file=f"assets/icons/{slug}.png", label=DISPLAY.get(lab, lab), package=pkg,
                             confidence="high", source=SRC[b["src"]], slot=b["slot"],
                             box=[b["cx"] - 84, b["cy"] - 84, SIZE, SIZE], evidence=evidence))
        rgba(b["col"], cov).save(os.path.join(SCRATCH, f"crop-{slug}.png"))
    json.dump(manifest, open(os.path.join(SCRATCH, "manifest.preview.json"), "w"), ensure_ascii=False, indent=2)
    print(f"manifest entries: {len(manifest)}")

    if write:
        os.makedirs(rel(ICON_DIR), exist_ok=True)
        os.makedirs(rel(BEFORE_DIR), exist_ok=True)
        for e, b in zip(manifest, chosen.values()):
            rgba(b["col"], cov).save(rel(os.path.join("docs/redesign", e["file"])), optimize=True)
        text = json.dumps(manifest, ensure_ascii=False, indent=2)
        text = re.sub(r"\[\s+(\d+),\s+(\d+),\s+(\d+),\s+(\d+)\s+\]", r"[\1, \2, \3, \4]", text)
        assert json.loads(text) == manifest
        with open(rel(MANIFEST), "w", encoding="utf-8") as f:
            f.write(text + "\n")
        for src, name in BEFORE:
            im = Image.open(rel(src))
            arr = np.asarray(im)
            assert arr.ndim == 3 and (arr.shape[2] == 3 or arr[..., 3].min() == 255), src
            w, h = im.size
            out = im.convert("RGB").resize((1600, round(h * 1600 / w)), Image.LANCZOS)
            out.save(rel(os.path.join(BEFORE_DIR, name)), "JPEG", quality=85, optimize=True)
        print("written:", rel(ICON_DIR), rel(MANIFEST), rel(BEFORE_DIR))


if __name__ == "__main__":
    main()
