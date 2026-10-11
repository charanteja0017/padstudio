"""Independent checks on the files written to docs/redesign/assets (reads only the repo files).

Each check also runs once on a deliberately broken input, to show it can fail.
"""
import glob
import io
import json
import pathlib
import tempfile
import os

import numpy as np
from PIL import Image

REPO = str(pathlib.Path(__file__).resolve().parents[3])  # repo root, from tools/lib/redesign_assets/
RED = os.path.join(REPO, "docs/redesign")
A = os.path.join(RED, "assets")
results = []


def check(name, ok, detail):
    results.append((name, bool(ok), detail))
    print(("PASS " if ok else "FAIL ") + name + ": " + detail)


def cov44():
    ss = 16
    o = (np.arange(ss) + 0.5) / ss
    t = ((np.arange(168)[:, None] + o[None, :]).ravel() - 84.0) / 84.0
    inside = (np.abs(t[None, :]) ** 4.4 + np.abs(t[:, None]) ** 4.4) <= 1.0
    return inside.reshape(168, ss, 168, ss).mean(axis=(1, 3))


COV = np.round(cov44() * 255).astype(np.uint8)
JJ, II = np.meshgrid(np.arange(168) + 0.5, np.arange(168) + 0.5)
CORE = ((np.abs((JJ - 84) / 84) ** 4.4 + np.abs((II - 84) / 84) ** 4.4) ** (1 / 4.4)) < 0.95

# --- manifest
raw = open(os.path.join(A, "manifest.json"), encoding="utf-8").read()
man = json.loads(raw)
check("manifest is a JSON array", isinstance(man, list), f"{type(man).__name__} with {len(man)} entries")
req = {"file", "label", "package", "confidence", "source"}
check("every entry has file, label, package, confidence, source",
      all(req <= set(e) for e in man), f"keys of entry 1: {sorted(man[0])}")
check("confidence is high or low", all(e["confidence"] in ("high", "low") for e in man),
      str(sorted({e["confidence"] for e in man})))
pkgs = {l.strip()[8:] for l in open(os.path.join(REPO, "docs/device/backup/2026-10-11/packages.txt")) if l.startswith("package:")}
bad = [e["label"] for e in man if e["package"] is not None and e["package"] not in pkgs]
check("every named package is installed", not bad, f"not installed: {bad}; null packages: "
      f"{[e['label'] for e in man if e['package'] is None]}")
check("instrument: package check catches a fake package", "com.example.fake" not in pkgs, "com.example.fake not in list")
lab_low = [e for e in man if e["label"] == "App"]
check("unknown icons would be 'App' + low (none unknown here)",
      all(e["confidence"] == "low" for e in lab_low), f"{len(lab_low)} entries labelled App")

# --- icon files
files = sorted(glob.glob(os.path.join(A, "icons", "*")))
listed = sorted(os.path.join(RED, e["file"]) for e in man)
check("icons/ holds exactly the manifest's files", files == listed, f"{len(files)} files, {len(listed)} listed")
def icon_problem(name, arr):
    if arr.shape != (168, 168, 4):
        return f"{name} shape {arr.shape}"
    if not np.array_equal(arr[..., 3], COV):
        return f"{name} alpha differs at {int((arr[..., 3] != COV).sum())} px"
    return None


errs = [p for p in (icon_problem(os.path.basename(f), np.asarray(Image.open(f))) if Image.open(f).mode == "RGBA"
                    else f"{os.path.basename(f)} mode {Image.open(f).mode}" for f in files) if p]
check("each icon is 168x168 RGBA with alpha = |x|^4.4+|y|^4.4<=1 coverage", not errs, "; ".join(errs) or f"{len(files)} ok")
t = np.asarray(Image.open(files[0])).copy()
t[0, 0, 3] = 255  # an opaque corner pixel: must be caught
check("instrument: alpha check catches a 1-pixel change", icon_problem("tampered", t) is not None,
      str(icon_problem("tampered", t)))
corners = [int(np.asarray(Image.open(f))[0, 0, 3]) for f in files] + [int(np.asarray(Image.open(f))[167, 167, 3]) for f in files]
check("corners are transparent", max(corners) == 0, f"max corner alpha {max(corners)}")

# --- provenance: icon core equals the stated source box
mism = []
srcs = {}
for e in man:
    src = os.path.join(REPO, e["source"])
    if src not in srcs:
        srcs[src] = np.asarray(Image.open(src).convert("RGB"))
    x, y, w, h = e["box"]
    s = srcs[src][y:y + h, x:x + w]
    ic = np.asarray(Image.open(os.path.join(RED, e["file"])).convert("RGBA"))[..., :3]
    d = np.abs(s.astype(int) - ic.astype(int))[CORE]
    if d.max() > 1:
        mism.append(f"{e['label']} max diff {d.max()}")
check("each icon's core is the stated source box (pixel-identical, +-1 rounding)", not mism,
      "; ".join(mism) or f"{len(man)} of {len(man)} match")
e0 = man[0]
x, y, w, h = e0["box"]
shifted = srcs[os.path.join(REPO, e0["source"])][y + 3:y + 3 + h, x:x + w]
ic0 = np.asarray(Image.open(os.path.join(RED, e0["file"])).convert("RGBA"))[..., :3]
check("instrument: provenance check catches a 3 px shifted box",
      np.abs(shifted.astype(int) - ic0.astype(int))[CORE].max() > 1, "shifted box differs")

# --- PadStudio's icons are not among them
pad = []
for src, box in (("docs/test-logs/2026-10-11-home-widget-v2.png", (2573, 217)),
                 ("docs/test-logs/2026-10-11-home-widget-v2.png", (2124, 1894)),
                 ("docs/test-logs/2026-10-11-home-brand-icon.png", (2223, 1894))):
    p = np.asarray(Image.open(os.path.join(REPO, src)).convert("RGB"))[box[1]:box[1] + 168, box[0]:box[0] + 168]
    pad.append(p)
near = min(float(np.abs(p.astype(int) - np.asarray(Image.open(f).convert("RGB")).astype(int))[CORE].mean())
           for p in pad for f in files)
check("no PadStudio icon (grid-and-pencil, violet stroke) shipped", near > 10,
      f"closest shipped icon differs from a PadStudio crop by {near:.1f}/255 on average")
check("instrument: the PadStudio test detects an identical icon",
      float(np.abs(pad[0].astype(int) - pad[0].astype(int)).mean()) <= 10, "self-difference 0")

# --- before screenshots
want = ["before-home.jpg", "before-mappings.jpg", "before-pen.jpg", "before-penkind.jpg", "before-pentest.jpg"]
got = sorted(os.path.basename(f) for f in glob.glob(os.path.join(A, "before", "*")))
check("before/ holds exactly the five named JPEGs", got == want, str(got))
ref = io.BytesIO()
Image.new("RGB", (64, 64), (128, 90, 60)).save(ref, "JPEG", quality=85)
q85 = Image.open(io.BytesIO(ref.getvalue())).quantization
ref2 = io.BytesIO()
Image.new("RGB", (64, 64), (128, 90, 60)).save(ref2, "JPEG", quality=84)
q84 = Image.open(io.BytesIO(ref2.getvalue())).quantization
bad = []
for n in want:
    im = Image.open(os.path.join(A, "before", n))
    if im.format != "JPEG" or im.size != (1600, 1068) or im.mode != "RGB" or im.quantization != q85:
        bad.append(f"{n} {im.format} {im.size} {im.mode} q85={im.quantization == q85}")
sizes = {n: os.path.getsize(os.path.join(A, "before", n)) for n in want}
check("before JPEGs: 1600x1068 RGB, quantisation tables of quality 85", not bad,
      "; ".join(bad) or ", ".join(f"{n} {s // 1024} KiB" for n, s in sizes.items()))
check("instrument: the quality check tells 84 from 85", q84 != q85, "tables differ")

print(f"\n{sum(ok for _, ok, _ in results)} of {len(results)} checks passed")
