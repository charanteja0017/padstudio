#!/usr/bin/env python3
"""PadStudio logo probe, round 1 (plan §9): nine directions, all in Violet, so you judge shape alone.

Each mark is a module in Branding/probe/marks/ with NUMBER, NAME, SLUG, ABOUT, SIZE_CLASS, PIECES, GLOW and
draw(c), drawing with the Canvas in canvas.py. This script loads every one of them, sorted by NUMBER, and
names any of the nine that is missing or broken instead of skipping it. It brings each mark to its size
class, runs the gates, and only then renders.

  python3 -I -B Branding/probe/make_probe.py [--export DIR] [--allow-failures]

Output goes to Branding/probe/out/ (git-ignored), or to DIR with --export:
  NN-slug-440.png           the mark on its plate, as on a brand card
  NN-slug-{168,110,66}.png  the launcher icon (full bleed, HyperOS mask n 4.4); 168 px is your dock size
  NN-slug-dock.png          pasted into your real dock, above your real home-screen top row with the
                            candidate standing next to WiFiBar (the family check)
  sheet.png, dock.png, palettes.png, gates.json

Gates. Every value goes to gates.json. All of them read the mark's silhouette, where every tone counts as
mark (a 0.45 pane is still a visible piece) and knockouts do not. For a mark drawn in full tone only, this
is exactly mark_mask(px, draw, half=px * BODY_SHARE) > 0.5.
  size      long side of the bounding box 1.06 (line) or 0.92 (tile), within 0.03, after normalising
  pieces    connected pieces at 168 and 66 px as declared in PIECES; a piece under 2 % of the mark is a
            fragment, and any fragment fails
  feature   at 66 px (measured at 264 px), opening or closing with a disc of radius 3 px (6 px across, so
            1.5 px at 66) leaves the piece count unchanged
  beam      silhouette IoU with WiFiBar's beam r(phi) = exp(-phi^2 / 2 sigma^2), sigma = 25 deg, is below
            0.85, after equal area and centroid, best over mirror x 5 degree rotations
  radial    angular FFT of the mass around the centroid: |F_k| / F_0 < 0.45 for every k >= 3
  grid      no 4 or more pieces alike (area within 3 %, IoU >= 0.9 after centring)
  distinct  pairwise silhouette IoU < 0.7 (normalised as for the beam); the later number fails. As the plan
            says, Beyond (#6) is dropped, not failed, if it scores >= 0.7 against Pad stroke (#1)
  contrast  WCAG contrast of the mark's palest colour against the plate's top colour >= 3:1

The beam gate must reject the old Ink drop (fixtures.py). If it does not, the gate is broken and the script
raises; it is never loosened. Small synthetic self-checks prove every other gate can fail too.

If any candidate fails, or one of the nine is missing or broken, the script writes nothing but gates.json
and exits with status 1. With --allow-failures it renders the candidates that pass and lists the rest. The
same inputs give byte-identical files.
"""

import argparse
import hashlib
import importlib.util
import json
import math
import re
import sys
from collections import Counter
from pathlib import Path
from types import SimpleNamespace

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))      # python3 -I leaves the script's folder off sys.path; marks import canvas

import fixtures  # noqa: E402
from canvas import (BODY_SHARE, LAUNCHER_MASK_N, PALETTES, SS, Canvas, bbox_plate_units, icon,  # noqa: E402
                    mb, superellipse_pts)

MARKS = HERE / "marks"
OUT = HERE / "out"
DOCK_SHOT = ROOT / "docs/test-logs/2026-10-11-home-brand-icon.png"
PALETTE = "Violet"

# The nine directions in the plan (§9), so a missing one is named, not skipped.
EXPECTED = {1: "Pad stroke", 2: "Hilbert", 3: "Arch", 4: "Knot", 5: "Morph", 6: "Beyond", 7: "Maze",
            8: "Peel", 9: "Monoline P"}
PAD_STROKE, BEYOND = 1, 6

SIZE_TARGET = {"line": 1.06, "tile": 0.92}
SIZE_TOL = 0.03
PIECE_SIZES = (168, 66)
FRAGMENT_SHARE = 0.02
FEATURE_PX, FEATURE_RADIUS = 264, 3.0      # 4x the 66 px icon; radius 3 px there is 0.75 px at 66
SHAPE_PX = 440                             # silhouettes for the beam, radial, grid and distinct gates
BEAM_SIGMA = np.deg2rad(25.0)              # WiFiBar's direction-sweep smoothing (its make_brand.py)
BEAM_MAX = 0.85
RADIAL_BINS, RADIAL_K_MIN, RADIAL_MAX = 360, 3, 0.45
GRID_MIN, GRID_AREA_TOL, GRID_IOU = 4, 0.03, 0.9
DISTINCT_MAX = 0.7
CONTRAST_MIN = 3.0
ANGLES = tuple(range(0, 360, 5))
CANON_N, CANON_L, CANON_SS = 256, 2.5, 2   # unit-area shapes on a 256 px grid spanning +-2.5, 2x2 samples
DOCK_PX = 168                              # the 72 dp launcher viewport at 440 dpi (plan §9)
ICON_SIZES = (168, 110, 66)
CARD_PX = 440

LUMA = np.array([0.2126, 0.7152, 0.0722])
INK, GREY, MIST = (mb.rgba(c) for c in (mb.INK, mb.GREY, mb.MIST))
LINE = (0xD1, 0xD1, 0xD6, 255)

OUTPUT_NAME = re.compile(r"^(\d{2}-[a-z0-9_-]+-(440|168|110|66|dock)\.png|sheet\.png|dock\.png|palettes\.png|gates\.json"
                         r"|probe-sheet\.png|probe-dock\.png)$")      # the last two: the old ten-candidate probe


def r4(x):
    return round(float(x), 4)


def rel(path):
    try:
        return str(Path(path).resolve().relative_to(ROOT))
    except ValueError:
        return str(path)


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


# --- Loading the marks ---------------------------------------------------------------------------

def parse_pieces(value):
    """PIECES as {168: n, 66: n}: None, one count for both sizes, a pair (168 px, 66 px) or a dict."""
    if value is None:
        return None
    if isinstance(value, dict):
        value = (value[168], value[66])
    elif isinstance(value, int):
        value = (value, value)
    a, b = value
    if not all(isinstance(v, int) and not isinstance(v, bool) and v >= 1 for v in (a, b)):
        raise ValueError(value)
    return {168: a, 66: b}


def interface(mod):
    """The module's declared interface as a namespace, plus a list of what is wrong with it."""
    errors = []
    number = getattr(mod, "NUMBER", None)
    if not (isinstance(number, int) and not isinstance(number, bool) and 1 <= number <= 9):
        errors.append("NUMBER must be an integer 1-9")
        number = None
    for attr in ("NAME", "SLUG", "ABOUT"):
        if not (isinstance(getattr(mod, attr, None), str) and getattr(mod, attr).strip()):
            errors.append(f"{attr} must be a non-empty string")
    slug = getattr(mod, "SLUG", "")
    if isinstance(slug, str) and slug and not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", slug):
        errors.append("SLUG may use only a-z, 0-9, - and _")
    if getattr(mod, "SIZE_CLASS", None) not in SIZE_TARGET:
        errors.append("SIZE_CLASS must be 'line' or 'tile'")
    pieces = None
    if not hasattr(mod, "PIECES"):
        errors.append("PIECES is missing (None means no declared count)")
    else:
        try:
            pieces = parse_pieces(mod.PIECES)
        except Exception:
            errors.append("PIECES must be None, a positive integer, or one per size (168 px, 66 px)")
    glow = None
    try:
        glow = tuple(float(v) for v in mod.GLOW)
        if len(glow) != 2 or not all(math.isfinite(v) for v in glow):
            raise ValueError
    except Exception:
        errors.append("GLOW must be (x, y) in plate units")
    if not callable(getattr(mod, "draw", None)):
        errors.append("draw(c) is missing")
    m = SimpleNamespace(number=number, name=str(getattr(mod, "NAME", "")).strip(), slug=slug,
                        about=" ".join(str(getattr(mod, "ABOUT", "")).split()),
                        size_class=getattr(mod, "SIZE_CLASS", None), pieces=pieces, glow=glow,
                        draw=getattr(mod, "draw", None))
    return m, errors


def load_marks(folder):
    """Every marks/*.py, sorted by NUMBER; anything that cannot be used is reported, never dropped quietly."""
    marks, problems = [], []
    for path in sorted(Path(folder).glob("*.py")):
        if path.name == "__init__.py":
            continue
        name = f"padstudio_probe_mark_{path.stem}"
        try:
            spec = importlib.util.spec_from_file_location(name, path)
            mod = importlib.util.module_from_spec(spec)
            sys.modules[name] = mod
            spec.loader.exec_module(mod)
        except Exception as e:
            sys.modules.pop(name, None)
            problems.append(dict(file=rel(path), number=None, problem=f"could not be loaded: {type(e).__name__}: {e}"))
            continue
        m, errors = interface(mod)
        if errors:
            problems.append(dict(file=rel(path), number=m.number, problem="; ".join(errors)))
            continue
        m.file, m.sha256 = rel(path), sha256(path)
        marks.append(m)
    numbers, slugs = Counter(m.number for m in marks), Counter(m.slug for m in marks)
    for m in marks:
        if numbers[m.number] > 1:
            problems.append(dict(file=m.file, number=m.number, problem=f"NUMBER {m.number} is claimed by {numbers[m.number]} files"))
        elif slugs[m.slug] > 1:
            problems.append(dict(file=m.file, number=m.number, problem=f"SLUG {m.slug!r} is used by {slugs[m.slug]} files"))
    marks = [m for m in marks if numbers[m.number] == 1 and slugs[m.slug] == 1]
    return sorted(marks, key=lambda m: m.number), problems


# --- Silhouettes and size ------------------------------------------------------------------------

def silhouette(draw, px, scale=1.0, half=None):
    """The mark at px as coverage 0..1, with half = px * BODY_SHARE as on the icon. Every tone counts as mark,
    knockouts do not; for a mark drawn in full tone only this is exactly canvas.mark_mask."""
    c = Canvas(px, px * BODY_SHARE if half is None else half, scale)
    draw(c)
    solid = c.big.point(lambda v: 255 if v else 0)
    return np.asarray(solid.resize((px, px), Image.LANCZOS), np.float32) / 255.0


def bbox(draw, scale=1.0, px=880):
    """Bounding box in plate units (x0, y0, x1, y1), measured exactly as canvas.bbox_plate_units does, but
    on the silhouette, so a pale tone (a 0.45 pane) is inside the box."""
    ys, xs = np.nonzero(silhouette(draw, px, scale, half=px / 2) > 0.5)
    if not len(xs):
        return None
    u = lambda v: v / (px / 2) - 1  # noqa: E731
    return (u(xs.min()), -u(ys.max() + 1), u(xs.max() + 1), -u(ys.min()))


def long_side(box):
    return max(box[2] - box[0], box[3] - box[1])


# --- Shape comparison: equal area, equal centroid, best of mirror x rotation -----------------------

_S = (np.arange(CANON_N * CANON_SS) + 0.5) / (CANON_N * CANON_SS) * 2 * CANON_L - CANON_L
_QX, _QY = np.meshgrid(_S, -_S)


def canonical(cov, all_poses=True):
    """cov (coverage 0..1, any pixel grid) scaled to unit area about its centroid on a CANON_N grid that spans
    +-CANON_L. With all_poses: every mirror x rotation in ANGLES (mirror first), as one stack."""
    cov = np.asarray(cov, np.float64)
    area = cov.sum()
    if area <= 0:
        raise ValueError("empty silhouette")
    yy, xx = np.mgrid[0:cov.shape[0], 0:cov.shape[1]] + 0.5
    cx, cy = (cov * xx).sum() / area, (cov * yy).sum() / area
    k = 1 / math.sqrt(area)                                   # canonical units per pixel
    reach = math.sqrt(((xx - cx) ** 2 + (yy - cy) ** 2)[cov > 0].max()) * k
    if reach > CANON_L - 0.05:
        raise ValueError(f"silhouette reaches {reach:.2f} from its centroid; the comparison grid spans {CANON_L}")
    poses = []
    for mirror in ((False, True) if all_poses else (False,)):
        qx = -_QX if mirror else _QX
        for deg in (ANGLES if all_poses else (0,)):
            a = math.radians(deg)
            sx = math.cos(a) * qx + math.sin(a) * _QY             # sample the shape at R(-a) q
            sy = -math.sin(a) * qx + math.cos(a) * _QY
            v = ndimage.map_coordinates(cov, [cy - sy / k - 0.5, cx + sx / k - 0.5], order=1, cval=0.0)
            poses.append(v.reshape(CANON_N, CANON_SS, CANON_N, CANON_SS).mean(axis=(1, 3)).astype(np.float32))
    return np.stack(poses)


def best_pose(ref, poses):
    """Highest IoU (sum of min over sum of max, i.e. IoU of coverages) of ref against any pose."""
    inter = np.minimum(ref[None], poses).sum(axis=(1, 2), dtype=np.float64)
    union = np.maximum(ref[None], poses).sum(axis=(1, 2), dtype=np.float64)
    iou = inter / union
    i = int(np.argmax(iou))
    return float(iou[i]), dict(mirrored=bool(i >= len(ANGLES)), rotation=ANGLES[i % len(ANGLES)])


def beam_lobe(c):
    """WiFiBar's beam as its make_brand.py plots it: r(phi) = exp(-phi^2 / 2 sigma^2), phi in [-pi, pi],
    filled, tip at the origin (drawn 0.9 long and centred on the plate)."""
    phi = np.linspace(-np.pi, np.pi, 4001)
    r = 0.9 * np.exp(-phi ** 2 / (2 * BEAM_SIGMA ** 2))
    c.poly(np.c_[r * np.cos(phi) - 0.45, r * np.sin(phi)])


_LOBE = []


def lobe():
    if not _LOBE:
        _LOBE.append(canonical(silhouette(beam_lobe, SHAPE_PX), all_poses=False)[0])
    return _LOBE[0]


# --- The gates -----------------------------------------------------------------------------------

_d = np.arange(2 * FEATURE_RADIUS) - (FEATURE_RADIUS - 0.5)
FEATURE_DISC = _d[:, None] ** 2 + _d[None, :] ** 2 <= FEATURE_RADIUS ** 2       # 6 px across
_d7 = np.arange(-3, 4)
DISC_7PX = _d7[:, None] ** 2 + _d7[None, :] ** 2 <= 9                          # 7 px across (advisory)


def count(binary):
    return int(ndimage.label(binary)[1])


def gate_pieces(sil, expected):
    lab, n = ndimage.label(sil > 0.5)
    shares = np.bincount(lab.ravel())[1:] / max(1, int((lab > 0).sum()))
    fragments = int((shares < FRAGMENT_SHARE).sum())
    ok = n > 0 and fragments == 0 and (expected is None or n == expected)
    return dict(count=int(n), expected=expected, fragments=fragments,
                smallest_share=r4(shares.min()) if n else None, **{"pass": bool(ok)})


def gate_feature(sil):
    b = np.pad(sil > 0.5, 8)
    n = count(b)
    opened, closed = count(ndimage.binary_opening(b, FEATURE_DISC)), count(ndimage.binary_closing(b, FEATURE_DISC))
    strict = (count(ndimage.binary_opening(b, DISC_7PX)), count(ndimage.binary_closing(b, DISC_7PX)))
    return {"pieces_at_264": n, "after_opening": opened, "after_closing": closed,
            "advisory_7px_disc": dict(after_opening=strict[0], after_closing=strict[1], unchanged=bool(strict == (n, n))),
            "pass": bool(opened == n == closed)}


def gate_beam(poses):
    iou, pose = best_pose(lobe(), poses)
    return dict(iou=r4(iou), limit=BEAM_MAX, **pose, **{"pass": bool(iou < BEAM_MAX)})


def gate_radial(cov):
    cov = np.asarray(cov, np.float64)
    yy, xx = np.mgrid[0:cov.shape[0], 0:cov.shape[1]] + 0.5
    area = cov.sum()
    cx, cy = (cov * xx).sum() / area, (cov * yy).sum() / area
    angle = np.arctan2(-(yy - cy), xx - cx)
    mass = np.histogram(angle, RADIAL_BINS, (-np.pi, np.pi), weights=cov)[0]
    f = np.abs(np.fft.rfft(mass))
    ratio = f[RADIAL_K_MIN:] / f[0]
    k = int(np.argmax(ratio))
    return dict(max_ratio=r4(ratio[k]), at_k=k + RADIAL_K_MIN, limit=RADIAL_MAX, **{"pass": bool(ratio[k] < RADIAL_MAX)})


def centred_iou(a, b):
    """IoU of two binary pieces after moving both centroids to the same point (sub-pixel). The frame is twice
    the larger piece, since a centroid can sit anywhere inside its piece's box."""
    h, w = 2 * max(a.shape[0], b.shape[0]) + 4, 2 * max(a.shape[1], b.shape[1]) + 4
    placed = []
    for m in (a, b):
        cy, cx = ndimage.center_of_mass(m)
        oy, ox = (h - 1) / 2 - cy, (w - 1) / 2 - cx
        iy, ix = int(math.floor(oy)), int(math.floor(ox))
        canvas = np.zeros((h, w))
        canvas[iy:iy + m.shape[0], ix:ix + m.shape[1]] = m
        placed.append(ndimage.shift(canvas, (oy - iy, ox - ix), order=1))
    return float(np.minimum(*placed).sum() / np.maximum(*placed).sum())


def gate_grid(cov):
    lab, n = ndimage.label(cov > 0.5)
    pieces = [lab[sl] == i + 1 for i, sl in enumerate(ndimage.find_objects(lab))]
    areas = [int(p.sum()) for p in pieces]
    parent = list(range(n))

    def root(i):
        while parent[i] != i:
            i = parent[i]
        return i

    for i in range(n):
        for j in range(i + 1, n):
            if abs(areas[i] - areas[j]) <= GRID_AREA_TOL * max(areas[i], areas[j]) and centred_iou(pieces[i], pieces[j]) >= GRID_IOU:
                parent[root(j)] = root(i)
    alike = max(Counter(root(i) for i in range(n)).values()) if n else 0
    return dict(pieces=int(n), most_alike=int(alike), limit=GRID_MIN, **{"pass": bool(alike < GRID_MIN)})


def relative_luminance(rgb):
    c = np.asarray(rgb, np.float64) / 255.0
    lin = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    return float(lin @ LUMA)


def contrast_ratio(c1, c2):
    hi, lo = sorted((relative_luminance(c1), relative_luminance(c2)), reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def gate_contrast(draw, scale, colours=PALETTES[PALETTE]):
    """The mark's palest colour on the 440 px card (the icon's pale-to-white fill at the mark's palest point)
    against the plate's top colour. Tones below 1 are blended over the plate top, as the icon composites
    them, and reported per tone; the gate itself is the full-tone pale end."""
    top, _, _, pale, _ = colours
    half = CARD_PX * BODY_SHARE
    c = Canvas(CARD_PX, half, scale)
    draw(c)
    v = np.asarray(c.big)
    ys, xs = np.nonzero(v)
    x, y = (xs + 0.5) / SS, (ys + 0.5) / SS
    p0 = np.array([CARD_PX / 2 - 0.6 * half, CARD_PX / 2 + 0.6 * half])     # as canvas.icon lays the fill
    d = np.array([1.2 * half, -1.2 * half])
    t = np.clip(((x - p0[0]) * d[0] + (y - p0[1]) * d[1]) / (d @ d), 0, 1)
    pale_rgb, top_rgb = np.array(mb.rgba(pale)[:3], float), np.array(mb.rgba(top)[:3], float)
    fill = lambda tt: pale_rgb + (255.0 - pale_rgb) * tt  # noqa: E731
    value = contrast_ratio(fill(t.min()), top_rgb)
    tones = {}
    levels, counts = np.unique(v[ys, xs], return_counts=True)
    for level, n in zip(levels, counts):
        if n >= 0.005 * len(xs):
            a = level / 255.0
            tones[f"{a:.3f}"] = r4(contrast_ratio(fill(t[v[ys, xs] == level].min()) * a + top_rgb * (1 - a), top_rgb))
    return dict(ratio=r4(value), limit=CONTRAST_MIN, pale_end=[int(round(q)) for q in fill(t.min())], plate_top=top,
                advisory_by_tone=tones, **{"pass": bool(value >= CONTRAST_MIN)})


def plain_reasons(m, names):
    """What failed, in words for the sheet (names: number -> name of every loaded mark)."""
    g, out = m.gates, []
    if "error" in g:
        return [f"its drawing failed: {g['error']}"]
    if not g["size"]["pass"]:
        out.append(f"the wrong size even after scaling: {g['size']['long_side']:.3f} across, needs {g['size']['target']}")
    for px in PIECE_SIZES:
        p = g[f"pieces_{px}"]
        if not p["pass"]:
            out.append(f"at {px}\u00a0px it comes out as {p['count']} piece{'' if p['count'] == 1 else 's'}"
                       + (f", not {p['expected']}" if p["expected"] and p["expected"] != p["count"] else "")
                       + (f", with {p['fragments']} stray speck{'' if p['fragments'] == 1 else 's'}" if p["fragments"] else ""))
    f = g["feature_66"]
    if f["after_closing"] != f["pieces_at_264"]:
        out.append("a gap is narrower than 1.5\u00a0px at 66\u00a0px, so pieces run together there")
    if f["after_opening"] != f["pieces_at_264"]:
        out.append("a stroke is thinner than 1.5\u00a0px at 66\u00a0px, so it breaks up there")
    if not g["beam"]["pass"]:
        out.append(f"too much like WiFiBar's beam (match {g['beam']['iou']:.2f})")
    if not g["radial"]["pass"]:
        out.append(f"reads as a {g['radial']['at_k']}-fold radial pattern")
    if not g["grid"]["pass"]:
        out.append(f"reads as a grid ({g['grid']['most_alike']} identical pieces)")
    if not g.get("distinct", {"pass": True})["pass"]:
        for other in g["distinct"]["too_close_to_earlier"]:
            out.append(f"too close to #{other['number']} {names.get(other['number'], '')} (match {other['iou']:.2f})")
    if not g["contrast"]["pass"]:
        out.append(f"too little contrast ({g['contrast']['ratio']:.1f}:1, needs 3:1)")
    return out


def gate_size(draw, size_class):
    """Scale that brings the bounding box's long side to its class, and the box measured again at that scale."""
    raw = bbox(draw)
    if raw is None:
        raise ValueError("draw(c) leaves the canvas empty")
    target = SIZE_TARGET[size_class]
    scale = target / long_side(raw)
    box = bbox(draw, scale)
    return scale, dict(target=target, long_side=r4(long_side(box)), bbox=[r4(v) for v in box],
                       before=dict(bbox=[r4(v) for v in raw], long_side=r4(long_side(raw)),
                                   bbox_plate_units=[r4(v) for v in bbox_plate_units(draw)]),
                       scale=r4(scale), **{"pass": bool(abs(long_side(box) - target) <= SIZE_TOL)})


def evaluate(m):
    """Size-normalise m and run every gate that needs only m. Keeps m.poses for the distinct gate."""
    m.scale, size = gate_size(m.draw, m.size_class)
    m.glow_scaled = (m.glow[0] * m.scale, m.glow[1] * m.scale)
    g = {"size": size}
    for px in PIECE_SIZES:
        g[f"pieces_{px}"] = gate_pieces(silhouette(m.draw, px, m.scale), m.pieces[px] if m.pieces else None)
    g["feature_66"] = gate_feature(silhouette(m.draw, FEATURE_PX, m.scale))
    sil = silhouette(m.draw, SHAPE_PX, m.scale)
    m.poses = canonical(sil)
    m.pose0 = m.poses[0].copy()
    g["beam"] = gate_beam(m.poses)
    g["radial"] = gate_radial(sil)
    g["grid"] = gate_grid(sil)
    g["contrast"] = gate_contrast(m.draw, m.scale)
    m.gates = g


# --- Instrument checks: every gate must be able to fail -------------------------------------------

def _fixture_namespace(f):
    m, errors = interface(f)
    m.number = f.NUMBER
    if errors and errors != ["NUMBER must be an integer 1-9"]:
        raise RuntimeError(f"fixture {f.NAME}: {errors}")
    return m


def _ignores_scale(c):      # draws in pixels, ignoring the canvas scale: the size gate must catch it
    r, mid = 0.3 * c.half * SS, c.px * SS / 2
    c.d.ellipse((mid - r, mid - r, mid + r, mid + r), fill=255)


def _lobe_at_45(c):
    phi = np.linspace(-np.pi, np.pi, 4001)
    r = 0.9 * np.exp(-phi ** 2 / (2 * BEAM_SIGMA ** 2))
    p = np.c_[r * np.cos(phi + np.pi / 4), r * np.sin(phi + np.pi / 4)]
    c.poly(p - p.mean(axis=0))


def _tile(c):
    c.poly(superellipse_pts(0, 0, 0.46, 0.46, 5))


def _tile_and_speck(c):
    c.poly(superellipse_pts(-0.05, 0, 0.40, 0.40, 5))
    c.disc((0.52, 0.45), 0.04)


def _thin_neck(c):          # two blobs joined by a 0.02 bar: 0.5 px at 66
    c.disc((-0.25, 0), 0.2)
    c.disc((0.25, 0), 0.2)
    c.poly([(-0.1, -0.01), (0.1, -0.01), (0.1, 0.01), (-0.1, 0.01)])


def _narrow_gap(c):         # two slabs 0.035 apart: 0.9 px at 66
    c.poly([(-0.4, -0.4), (-0.0175, -0.4), (-0.0175, 0.4), (-0.4, 0.4)])
    c.poly([(0.0175, -0.4), (0.4, -0.4), (0.4, 0.4), (0.0175, 0.4)])


def _minimum_ok(c):         # 0.06 strokes 0.06 apart (the plan's floor, 1.6 px at 66) must pass
    for x in (-0.15, -0.03, 0.09):
        c.poly([(x, -0.4), (x + 0.06, -0.4), (x + 0.06, 0.4), (x, 0.4)])
    c.poly([(-0.15, -0.46), (0.15, -0.46), (0.15, -0.4), (-0.15, -0.4)])


def _spokes(c):
    for k in range(6):
        a = k * np.pi / 3
        c.stroke(np.c_[np.linspace(0, 0.45, 50) * np.cos(a), np.linspace(0, 0.45, 50) * np.sin(a)], 0.08)


def _grid_2x2(c):
    for x in (-0.2, 0.2):
        for y in (-0.2, 0.2):
            c.poly(superellipse_pts(x, y, 0.14, 0.14, 5))


def _grid_3(c):
    for x in (-0.3, 0.0, 0.3):
        c.poly(superellipse_pts(x, 0, 0.11, 0.11, 5))


_MIRROR_TURN = np.array([[0.0, -1.0], [1.0, 0.0]]) @ np.diag([-1.0, 1.0])   # mirror x, then turn 90 degrees


def _hook(c, transform=np.eye(2)):
    """A chiral shape (no mirror symmetry): a 220 degree arc with a dot on one end."""
    t = np.linspace(0, np.deg2rad(220), 300)
    c.stroke(np.c_[0.3 * np.cos(t), 0.3 * np.sin(t)] @ transform.T, 0.12)
    c.disc(np.array([0.3, 0.0]) @ transform.T, 0.1)


def _hook_turned(c):        # the hook mirrored and turned: the distinct gate must find it, via a mirrored pose
    _hook(c, _MIRROR_TURN)


def instrument_checks():
    """Proof the gates can fail (plan §9). The Ink drop must be rejected by the beam gate; the self-checks
    show each other gate rejecting a known-bad shape and passing a known-good one. Raises on any miss."""
    results = []

    def check(name, value, ok, expect):
        results.append(dict(check=name, value=value, expect=expect, ok=bool(ok)))

    # The required fixture: the old Ink drop, size-normalised and measured exactly like a candidate.
    drop = _fixture_namespace(fixtures.INK_DROP)
    drop.scale, _ = gate_size(drop.draw, drop.size_class)
    beam = gate_beam(canonical(silhouette(drop.draw, SHAPE_PX, drop.scale)))
    ink = dict(fixture=drop.name, beam_iou=beam["iou"], review_iou=fixtures.INK_DROP.REVIEW_IOU,
               rejected=not beam["pass"], rotation=beam["rotation"], mirrored=beam["mirrored"])
    if beam["pass"]:
        raise RuntimeError(f"INSTRUMENT CHECK FAILED: the beam gate let the Ink drop through (IoU {beam['iou']:.4f} "
                           f"< {BEAM_MAX}); the review measured {fixtures.INK_DROP.REVIEW_IOU}. The gate is wrong; "
                           f"fix the measurement, do not loosen the limit.")

    _, fixed = gate_size(_ignores_scale, "tile")
    check("size: a mark that ignores the canvas scale is rejected", fixed["long_side"], not fixed["pass"], "not 0.92 +- 0.03")
    _, sized = gate_size(_tile, "tile")
    check("size: a plain tile is brought to 0.92", sized["long_side"], sized["pass"], "0.92 +- 0.03")
    beam45 = gate_beam(canonical(silhouette(_lobe_at_45, SHAPE_PX)))
    check("beam: WiFiBar's own beam at 45 degrees is rejected", beam45["iou"], not beam45["pass"] and beam45["iou"] > 0.98, "IoU > 0.98")
    tile = gate_beam(canonical(silhouette(_tile, SHAPE_PX)))
    check("beam: a plain tile passes", tile["iou"], tile["pass"], f"IoU < {BEAM_MAX}")
    speck = gate_pieces(silhouette(_tile_and_speck, 168), 1)
    check("pieces: a tile with a 1 % speck is rejected", speck["fragments"], speck["fragments"] == 1 and not speck["pass"], "1 fragment")
    plain = gate_pieces(silhouette(_tile, 66), 1)
    check("pieces: a plain tile passes at 66 px", plain["count"], plain["pass"], "1 piece")
    neck = gate_feature(silhouette(_thin_neck, FEATURE_PX))
    check("feature: a 0.5 px neck is rejected", [neck["pieces_at_264"], neck["after_opening"]], not neck["pass"], "opening splits it")
    gap = gate_feature(silhouette(_narrow_gap, FEATURE_PX))
    check("feature: a 0.9 px gap is rejected", [gap["pieces_at_264"], gap["after_closing"]], not gap["pass"], "closing joins it")
    floor = gate_feature(silhouette(_minimum_ok, FEATURE_PX))
    check("feature: 0.06 strokes and gaps (the plan's floor) pass", [floor["pieces_at_264"], floor["after_opening"], floor["after_closing"]],
          floor["pass"], "unchanged")
    spokes = gate_radial(silhouette(_spokes, SHAPE_PX))
    check("radial: six spokes are rejected", [spokes["max_ratio"], spokes["at_k"]], not spokes["pass"] and spokes["at_k"] == 6, "k = 6 over the limit")
    tile_r = gate_radial(silhouette(_tile, SHAPE_PX))
    check("radial: a plain tile passes", tile_r["max_ratio"], tile_r["pass"], f"< {RADIAL_MAX}")
    g4 = gate_grid(silhouette(_grid_2x2, SHAPE_PX))
    check("grid: four identical tiles are rejected", g4["most_alike"], not g4["pass"] and g4["most_alike"] == 4, "4 alike")
    g3 = gate_grid(silhouette(_grid_3, SHAPE_PX))
    check("grid: three identical tiles pass", g3["most_alike"], g3["pass"] and g3["most_alike"] == 3, "3 alike")
    hook0, turned = canonical(silhouette(_hook, SHAPE_PX), all_poses=False)[0], canonical(silhouette(_hook_turned, SHAPE_PX))
    same, pose = best_pose(hook0, turned)
    unmirrored, _ = best_pose(hook0, turned[:len(ANGLES)])
    check("distinct: a mirrored, turned copy is rejected (and needs the mirror)", [r4(same), r4(unmirrored)],
          same >= 0.95 and pose["mirrored"] and unmirrored < same - 0.05, "IoU >= 0.95 via a mirrored pose; unmirrored lower")
    other, _ = best_pose(canonical(silhouette(_hook, SHAPE_PX), all_poses=False)[0], canonical(silhouette(_tile, SHAPE_PX)))
    check("distinct: a hook and a tile pass", r4(other), other < DISTINCT_MAX, f"IoU < {DISTINCT_MAX}")
    dim = gate_contrast(_tile, 1.0, ("#5A3FD6", "#120A2E", "#C38BFF", "#7A62E0", 0.30))
    check("contrast: a pale end close to the plate is rejected", dim["ratio"], not dim["pass"], f"< {CONTRAST_MIN}")
    violet = gate_contrast(_tile, 1.0)
    check("contrast: the Violet palette passes", violet["ratio"], violet["pass"], f">= {CONTRAST_MIN}")
    check("contrast: white on black is 21:1", r4(contrast_ratio((255, 255, 255), (0, 0, 0))),
          abs(contrast_ratio((255, 255, 255), (0, 0, 0)) - 21) < 1e-9, "21")
    check("contrast: #767676 on white is 4.54:1", r4(contrast_ratio((0x76,) * 3, (255, 255, 255))),
          abs(contrast_ratio((0x76,) * 3, (255, 255, 255)) - 4.54) < 0.01, "4.54")
    failed = [r["check"] for r in results if not r["ok"]]
    if failed:
        raise RuntimeError("INSTRUMENT CHECK FAILED: " + "; ".join(failed))
    return dict(ink_drop=ink, self_checks=results)


# --- The home-screen screenshot: dock, top row, WiFiBar ---------------------------------------------

def gamma_area(n):
    """Area of |x|^n + |y|^n <= 1 as a share of its bounding square."""
    return math.gamma(1 + 1 / n) ** 2 / math.gamma(1 + 2 / n)


def find_padstudio_icon(a):
    """PadStudio's dock icon in the screenshot: what differs from the dock panel between the separator and
    Play. The launcher mask's exponent fitted to its area share is kept as a cross-check of n 4.4."""
    region = a[1875:2080, 2195:2415]
    panel = np.median(a[1872:1890, 2230:2380].reshape(-1, 3), axis=0)
    ys, xs = np.nonzero(np.sqrt(((region - panel) ** 2).sum(-1)) > 40)
    box = (2195 + int(xs.min()), 1875 + int(ys.min()), 2195 + int(xs.max()) + 1, 1875 + int(ys.max()) + 1)
    share_ = len(xs) / ((box[2] - box[0]) * (box[3] - box[1]))
    lo, hi = 2.0, 30.0                                   # fit the launcher's mask exponent to that area
    for _ in range(60):
        mid = (lo + hi) / 2
        lo, hi = (mid, hi) if gamma_area(mid) < share_ else (lo, mid)
    return box, (lo + hi) / 2


def fill_box(img, a, box, offset=(0, 0), pad=3):
    """Paint over an icon with what is around it: the median colour just above and just below the box,
    blended top to bottom (the dock panel, or the wallpaper). offset: where img sits in the screenshot."""
    x0, y0, x1, y1 = box
    above = np.median(a[y0 - 22:y0 - 5, x0:x1].reshape(-1, 3), axis=0)
    below = np.median(a[y1 + 5:y1 + 18, x0:x1].reshape(-1, 3), axis=0)
    h, w = y1 - y0 + 2 * pad, x1 - x0 + 2 * pad
    patch = (above + (below - above) * np.linspace(0, 1, h)[:, None, None]).repeat(w, axis=1)
    img.paste(Image.fromarray(patch.astype(np.uint8), "RGB").convert("RGBA"), (x0 - pad - offset[0], y0 - pad - offset[1]))


def otsu(values):
    hist, _ = np.histogram(values, 256, (0, 256))
    p = hist / hist.sum()
    w0, mu = np.cumsum(p), np.cumsum(p * (np.arange(256) + 0.5))
    between = (mu[-1] * w0 - mu) ** 2 / np.maximum(w0 * (1 - w0), 1e-12)
    return float(np.argmax(between) + 1)


def home_top_row(a, size, below):
    """The home screen's top row: blobs that differ from the wallpaper (each pixel row's median colour) by
    more than 40, about one icon in size, in the highest row there is. Only above the dock is searched."""
    band = a[:below]
    diff = np.sqrt(((band - np.median(band, axis=1, keepdims=True)) ** 2).sum(-1))
    lab, _ = ndimage.label(ndimage.binary_fill_holes(ndimage.binary_closing(diff > 40, np.ones((5, 5), bool))),
                           np.ones((3, 3), bool))
    icons = []
    for i, sl in enumerate(ndimage.find_objects(lab), 1):
        h, w = sl[0].stop - sl[0].start, sl[1].stop - sl[1].start
        if min(h, w) >= 0.8 * size and max(h, w) <= 1.25 * size:
            icons.append(((sl[1].start, sl[0].start, sl[1].stop, sl[0].stop), i))
    if not icons:
        raise RuntimeError("no home-screen icons found above the dock")
    top = min(box[1] for box, _ in icons)
    return sorted((ic for ic in icons if ic[0][1] < top + size / 2), key=lambda ic: ic[0][0]), lab


def score_icon(a, lab, box, label):
    """How much an icon's bright mark looks like WiFiBar's beam: Otsu split of its luminance inside the icon,
    then the largest bright piece through the beam gate's own comparison."""
    x0, y0, x1, y1 = box
    crop = a[y0:y1, x0:x1]
    inner = ndimage.binary_erosion(lab[y0:y1, x0:x1] == label, np.ones((9, 9), bool))
    lum = crop @ LUMA
    bright = inner & (lum >= otsu(lum[inner]))
    blab, n = ndimage.label(bright, np.ones((3, 3), bool))
    iou, pose = 0.0, dict(mirrored=False, rotation=0)
    if n:
        biggest = blab == int(np.argmax(np.bincount(blab.ravel())[1:])) + 1
        iou, pose = best_pose(lobe(), canonical(biggest.astype(np.float64)))
    plate = np.median(crop[inner & ~bright], axis=0) if (inner & ~bright).any() else np.zeros(3)
    return dict(box=[int(v) for v in box], beam_iou=r4(iou), beam_rotation=pose["rotation"], bright_pieces=int(n),
                plate=[int(round(v)) for v in plate])


class Home:
    """The 11 Oct home-screen screenshot: PadStudio's dock icon, the top row, and WiFiBar in it (detected)."""

    def __init__(self, path):
        self.path = path
        self.shot = Image.open(path).convert("RGBA")
        a = np.asarray(self.shot.convert("RGB"), float)
        self.box, self.mask_fit = find_padstudio_icon(a)
        x0, y0, x1, y1 = self.box
        self.size = max(x1 - x0, y1 - y0)
        if abs(self.size - DOCK_PX) > 1:
            raise RuntimeError(f"dock icon box is {self.size} px; the 72 dp viewport should be {DOCK_PX} +- 1 px (plan §9)")
        dock_crop = (x0 - 245, y0 - 40, x1 + 215, y1 + 34)      # the separator, PadStudio and Play

        row, lab = home_top_row(a, self.size, y0 - 2 * self.size)
        self.row = [score_icon(a, lab, box, label) for box, label in row]
        ranked = sorted(range(len(self.row)), key=lambda i: -self.row[i]["beam_iou"])
        w = self.row[ranked[0]]
        runner_up = self.row[ranked[1]]["beam_iou"] if len(ranked) > 1 else 0.0
        navy = w["plate"][2] > max(w["plate"][0], w["plate"][1])
        if not (w["beam_iou"] >= BEAM_MAX > runner_up and navy and w["bright_pieces"] >= 2):
            raise RuntimeError(f"could not single out WiFiBar in the top row (beam match, plate, bright pieces): {self.row}")
        self.wifi = ranked[0]
        nb = self.wifi + 1 if self.wifi + 1 < len(self.row) else self.wifi - 1
        if nb < 0:
            raise RuntimeError("WiFiBar has no neighbour in the top row for the candidate to stand in")
        self.neighbour = nb
        wb, nbox = self.row[self.wifi]["box"], self.row[nb]["box"]
        self.family_size = max(wb[2] - wb[0], wb[3] - wb[1])
        dock_h = dock_crop[3] - dock_crop[1]
        top = min(wb[1], nbox[1]) - (dock_h - self.family_size) // 2
        family_crop = (min(wb[0], nbox[0]) - 60, top, max(wb[2], nbox[2]) + 60, top + dock_h)

        # Both views the same size, so they stack cleanly: widen the narrower crop evenly on both sides.
        width = max(dock_crop[2] - dock_crop[0], family_crop[2] - family_crop[0])
        widen = lambda c: (c[0] - (width - (c[2] - c[0])) // 2, c[1], c[0] - (width - (c[2] - c[0])) // 2 + width, c[3])  # noqa: E731
        self.dock_crop, self.family_crop = widen(dock_crop), widen(family_crop)
        for c in (self.dock_crop, self.family_crop):
            if c[0] < 0 or c[1] < 0 or c[2] > self.shot.width or c[3] > self.shot.height:
                raise RuntimeError(f"crop {c} leaves the screenshot")
        self.dock_base = self.shot.crop(self.dock_crop)
        fill_box(self.dock_base, a, self.box, self.dock_crop[:2])
        self.family_base = self.shot.crop(self.family_crop)
        fill_box(self.family_base, a, tuple(nbox), self.family_crop[:2])
        cx, cy = (nbox[0] + nbox[2]) / 2, (nbox[1] + nbox[3]) / 2
        self.slot = (int(round(cx - self.family_size / 2)) - self.family_crop[0], int(round(cy - self.family_size / 2)) - self.family_crop[1])

    def dock(self, img=None):
        """The dock crop with img (the icon at self.size) in PadStudio's place, or as it is now."""
        if img is None:
            return self.shot.crop(self.dock_crop)
        out = self.dock_base.copy()
        out.alpha_composite(img, (self.box[0] - self.dock_crop[0], self.box[1] - self.dock_crop[1]))
        return out

    def family(self, img=None):
        """The top row around WiFiBar with img standing next to it, or as it is now."""
        if img is None:
            return self.shot.crop(self.family_crop)
        out = self.family_base.copy()
        out.alpha_composite(img, self.slot)
        return out

    def report(self):
        return dict(screenshot=rel(self.path), sha256=sha256(self.path),
                    dock=dict(box=list(self.box), size=self.size, size_check=f"{self.size} px, {DOCK_PX} +- 1 expected",
                              mask_n_used=LAUNCHER_MASK_N, mask_n_fitted=r4(self.mask_fit), crop=list(self.dock_crop)),
                    top_row=self.row, wifibar=dict(index=self.wifi, box=self.row[self.wifi]["box"],
                                                   beam_iou=self.row[self.wifi]["beam_iou"],
                                                   neighbour_box=self.row[self.neighbour]["box"],
                                                   crop=list(self.family_crop)))


# --- Rendering -----------------------------------------------------------------------------------

def render(m):
    """Every image of one candidate in Violet: the 440 px card and the launcher icon at 168, 110 and 66 px."""
    imgs = {CARD_PX: icon(CARD_PX, m.draw, PALETTE, m.glow_scaled, scale=m.scale)}
    for px in ICON_SIZES:
        imgs[px] = icon(px, m.draw, PALETTE, m.glow_scaled, mask_n=LAUNCHER_MASK_N, full_bleed=True, scale=m.scale)
    return imgs


def launcher_icon(m, imgs, px):
    return imgs[px] if px in imgs else icon(px, m.draw, PALETTE, m.glow_scaled, mask_n=LAUNCHER_MASK_N, full_bleed=True, scale=m.scale)


def stack(*imgs, gap=16):
    out = mb.clear((max(i.width for i in imgs), sum(i.height for i in imgs) + gap * (len(imgs) - 1)))
    y = 0
    for i in imgs:
        out.alpha_composite(i, (0, y))
        y += i.height + gap
    return out


def wrap(draw, text, f, width):
    lines, line = [], ""
    for word in (w for w in text.split(" ") if w):          # split on plain spaces only: NBSP keeps "110 px" whole
        trial = (line + " " + word).strip()
        if line and draw.textlength(trial, font=f) > width:
            lines.append(line)
            line = word
        else:
            line = trial
    return lines + [line] if line else lines


def paragraph(d, x, y, text, size, weight, fill, width, step):
    """Wrapped text from baseline y; returns the baseline after the last line."""
    for line in wrap(d, text, mb.font(size, weight), width):
        mb.card_text(d, x, y, line, size, weight, fill, x + width)
        y += step
    return y


CARD_W, PAD = CARD_PX + 64, 32


def sheet(entries, imgs):
    """Numbered cards, 1-9: the 440 px card, name, what it is, and the launcher icon at 168, 110 and 66 px.
    A number that is not shown gets a dashed card that says why."""
    W, margin, gap, cols = 80 * 2 + 3 * CARD_W + 2 * 30, 80, 30, 3
    probe = ImageDraw.Draw(Image.new("RGBA", (8, 8)))
    about_lines = max([len(wrap(probe, e["about"], mb.font(22, 400), CARD_W - 2 * PAD)) for e in entries if e["mark"]] + [3])
    icons_top = 20 + CARD_PX + 76 + 30 * about_lines           # name at +36, about from +76 in 30 px lines
    card_h = icons_top + max(ICON_SIZES) + 46
    head = 250
    rows = math.ceil(len(entries) / cols)
    shown = sum(1 for e in entries if e["mark"])
    footer_y = head + rows * (card_h + gap) + 30
    H = footer_y + 150
    img = Image.new("RGBA", (W, H), MIST)
    d = ImageDraw.Draw(img)
    mb.card_text(d, margin, 110, "PadStudio logo probe", 52, 600, INK, W - margin)
    mb.card_text(d, margin + d.textlength("PadStudio logo probe", font=mb.font(52, 600)) + 24, 110,
                 "round 1 · nine directions", 52, 400, GREY, W - margin)
    paragraph(d, margin, 165, "All in Violet, so you judge the shape alone. Each is shown large, then as the launcher shows it: "
              "168\u00a0px (your dock size), 110\u00a0px and 66\u00a0px.", 28, 400, GREY, W - 2 * margin, 38)
    for i, e in enumerate(entries):
        x, y = margin + (i % cols) * (CARD_W + gap), head + (i // cols) * (card_h + gap)
        if e["mark"]:
            m = e["mark"]
            card = mb.rounded((CARD_W, card_h), 28, mb.WHITE)
            card.alpha_composite(imgs[m.number][CARD_PX], (PAD, 20))
            cd = ImageDraw.Draw(card)
            mb.card_text(cd, PAD, 20 + CARD_PX + 36, f"{m.number}   {m.name}", 36, 600, INK, CARD_W - PAD)
            paragraph(cd, PAD, 20 + CARD_PX + 76, m.about, 22, 400, GREY, CARD_W - 2 * PAD, 30)
            left = PAD
            for px in ICON_SIZES:
                card.alpha_composite(imgs[m.number][px], (left, icons_top + max(ICON_SIZES) - px))
                label = f"{px} px" + (" · dock" if px == DOCK_PX else "")
                mb.card_text(cd, left, icons_top + max(ICON_SIZES) + 30, label, 18, 500, GREY, CARD_W - PAD)
                left += max(px, cd.textlength(label, font=mb.font(18, 500))) + 28
        else:
            card = mb.rounded((CARD_W, card_h), 28, mb.MIST)
            cd = ImageDraw.Draw(card)
            mb.dashed_rect(cd, (2, 2, CARD_W - 3, card_h - 3), LINE, dash=14, gap=10, width=3)
            mb.card_text(cd, PAD, 90, f"{e['number']}   {e['name']}", 36, 600, GREY, CARD_W - PAD)
            mb.card_text(cd, PAD, 140, e["state"], 26, 600, GREY, CARD_W - PAD)
            yy = 180
            for reason in e["why"]:
                yy = paragraph(cd, PAD, yy, reason, 22, 400, GREY, CARD_W - 2 * PAD, 30) + 8
        img.alpha_composite(card, (x, y))
    note = (f"Shown: {shown} of {len(entries)}. Every mark shown passed the probe's checks: it holds together at 168 and 66 px, "
            "nothing in it is thinner than 1.5 px at 66 px, it is not a look-alike of WiFiBar's beam, it has no radial or grid "
            "pattern, it is distinct from the others, and it has the right size and enough contrast. The numbers are in gates.json.")
    paragraph(d, margin, footer_y + 10, note, 24, 400, GREY, W - 2 * margin, 34)
    return img


def dock_sheet(home, tiles):
    """Each candidate in the real dock (left) and next to WiFiBar in the real top row (right), at true size."""
    tw = max(home.dock().width + 24 + home.family().width, 900)
    th = max(home.dock().height, home.family().height)
    cols, gap, head = 2, 40, 190
    rows = math.ceil(len(tiles) / cols)
    W, H = cols * tw + (cols + 1) * gap, head + rows * (th + 64 + gap)
    out = Image.new("RGBA", (W, H), MIST)
    d = ImageDraw.Draw(out)
    mb.card_text(d, gap, 70, "In your dock and next to WiFiBar, at true size", 44, 600, INK, W - gap)
    paragraph(d, gap, 118, f"Pasted into your home-screen screenshot of 11 Oct. Left: the dock, where PadStudio's icon box measures "
              f"{home.size} px and the launcher's icon shape fits |x|^{LAUNCHER_MASK_N} + |y|^{LAUNCHER_MASK_N} = 1. "
              f"Right: your top row, with the candidate standing next to WiFiBar.", 22, 400, GREY, W - 2 * gap, 30)
    for i, (label, dock_img, fam_img) in enumerate(tiles):
        x, y = gap + (i % cols) * (tw + gap), head + (i // cols) * (th + 64 + gap)
        mb.card_text(d, x, y + 40, label, 28, 600, INK, x + tw)
        out.alpha_composite(dock_img, (x, y + 56))
        out.alpha_composite(fam_img, (x + dock_img.width + 24, y + 56))
    return out


def palette_strip(m):
    """Candidate m (normally #1) on each of the five palettes."""
    size, gap, margin = 300, 40, 80
    W, H = 2 * margin + 5 * size + 4 * gap, 560
    out = Image.new("RGBA", (W, H), MIST)
    d = ImageDraw.Draw(out)
    mb.card_text(d, margin, 90, "Colours", 44, 600, INK, W - margin)
    mb.card_text(d, margin + d.textlength("Colours", font=mb.font(44, 600)) + 20, 90,
                 f"·  {m.number} {m.name} on each palette; any mark can take any of them", 30, 400, GREY, W - margin)
    for i, name in enumerate(PALETTES):
        x = margin + i * (size + gap)
        out.alpha_composite(icon(size, m.draw, name, m.glow_scaled, scale=m.scale), (x, 130))
        mb.card_text(d, x + 30, 130 + size + 50, name, 30, 600, INK, x + size)
    return out


# --- The run ---------------------------------------------------------------------------------------

def clear_outputs(out):
    """Remove this probe's own earlier outputs (by their exact name pattern), so nothing stale survives."""
    removed = []
    if out.is_dir():
        for p in sorted(out.iterdir()):
            if p.is_file() and OUTPUT_NAME.match(p.name):
                p.unlink()
                removed.append(p.name)
    return removed


def write_json(path, report):
    path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main(argv=None):
    ap = argparse.ArgumentParser(description="PadStudio logo probe, round 1 (plan §9).")
    ap.add_argument("--export", metavar="DIR", help="write the outputs to DIR instead of Branding/probe/out/")
    ap.add_argument("--allow-failures", action="store_true", help="render the candidates that pass and list the rest")
    ap.add_argument("--marks", metavar="DIR", default=str(MARKS), help=argparse.SUPPRESS)   # tests only
    args = ap.parse_args(argv)
    out = Path(args.export).resolve() if args.export else OUT
    out.mkdir(parents=True, exist_ok=True)

    report = dict(probe="PadStudio logo probe, round 1 (plan §9)", status=None, palette=PALETTE,
                  thresholds=dict(size_target=SIZE_TARGET, size_tolerance=SIZE_TOL, piece_sizes=list(PIECE_SIZES),
                                  fragment_share=FRAGMENT_SHARE,
                                  feature=f"opening and closing at {FEATURE_PX} px with a disc of radius {FEATURE_RADIUS:g} px "
                                          f"({FEATURE_DISC.shape[0]} px across = 1.5 px at 66 px) keep the piece count",
                                  beam_max=BEAM_MAX, beam_sigma_deg=25, rotation_step_deg=ANGLES[1], radial_max=RADIAL_MAX,
                                  radial_k_min=RADIAL_K_MIN, grid_min=GRID_MIN, grid_area_tolerance=GRID_AREA_TOL,
                                  grid_iou=GRID_IOU, distinct_max=DISTINCT_MAX, contrast_min=CONTRAST_MIN,
                                  silhouette="every tone counts as mark; knockouts do not",
                                  comparison=f"unit area about the centroid, {CANON_N} px grid spanning +-{CANON_L}, "
                                             f"{CANON_SS}x{CANON_SS} samples per pixel, from the {SHAPE_PX} px silhouette"))
    try:
        report["instrument"] = instrument_checks()
    except RuntimeError as e:
        clear_outputs(out)
        report.update(status="instrument check failed", error=str(e))
        write_json(out / "gates.json", report)
        raise

    marks, problems = load_marks(args.marks)
    report["marks_folder"] = rel(args.marks)
    # Distinct: every pair, each later mark's poses against each earlier mark's upright silhouette. The later
    # number fails, except under the plan's rule for Beyond against Pad stroke: then Beyond is dropped.
    pairs, dropped = [], {}
    for j, b in enumerate(marks):
        try:
            evaluate(b)
        except Exception as e:
            b.gates, b.poses, b.pose0 = dict(error=f"{type(e).__name__}: {e}"), None, None
            continue
        for a in marks[:j]:
            if a.pose0 is None:
                continue
            iou, pose = best_pose(a.pose0, b.poses)
            pairs.append(dict(a=a.number, b=b.number, iou=r4(iou), **pose))
            if iou >= DISTINCT_MAX and a.number == PAD_STROKE and b.number == BEYOND:
                dropped[b.number] = f"dropped by the plan's rule: its IoU with #{PAD_STROKE} {a.name} is {iou:.2f} (>= {DISTINCT_MAX})"
        b.poses = None                                   # 144 poses x 256 x 256: free them as we go
    for m in marks:
        if "error" in m.gates:
            continue
        mine = [p for p in pairs if m.number in (p["a"], p["b"])]
        closest = max(mine, key=lambda p: p["iou"], default=None)
        later = [dict(number=p["a"], iou=p["iou"]) for p in mine if p["b"] == m.number and p["iou"] >= DISTINCT_MAX
                 and not (m.number in dropped and p["a"] == PAD_STROKE)]
        m.gates["distinct"] = dict(iou=closest["iou"] if closest else None,
                                   closest=(closest["a"] if closest["b"] == m.number else closest["b"]) if closest else None,
                                   limit=DISTINCT_MAX, too_close_to_earlier=later, **{"pass": not later})

    for m in marks:
        failed = ["error"] if "error" in m.gates else [k for k, v in m.gates.items() if not v["pass"]]
        m.status = "dropped" if m.number in dropped else ("fail" if failed else "pass")
        m.failed = failed
    by_number = {m.number: m for m in marks}
    names = {m.number: m.name for m in marks}
    missing = [n for n in EXPECTED if n not in by_number]
    shown = [m for m in marks if m.status == "pass"]
    failing = [m for m in marks if m.status == "fail"]
    ok = not failing and not missing and not problems
    report["status"] = "pass" if ok else ("failures allowed" if args.allow_failures else "fail")
    unattributed = sum(1 for p in problems if p["number"] is None)
    no_file = f"no usable mark file; {unattributed} file(s) could not be loaded (see problems)" if unattributed else "no mark file"
    report["missing"] = [dict(number=n, name=EXPECTED[n],
                              why=next((p["problem"] for p in problems if p["number"] == n), no_file)) for n in missing]
    report["problems"] = problems
    report["dropped"] = [dict(number=n, name=by_number[n].name, why=w) for n, w in sorted(dropped.items())]
    report["candidates"] = [dict(number=m.number, name=m.name, slug=m.slug, file=m.file, sha256=m.sha256,
                                 size_class=m.size_class, pieces_declared=m.pieces, glow=list(m.glow),
                                 status=m.status, failed=m.failed, gates=m.gates) for m in marks]
    report["distinct_pairs"] = pairs
    report["palette_contrast"] = {name: r4(contrast_ratio(mb.rgba(p[3])[:3], mb.rgba(p[0])[:3])) for name, p in PALETTES.items()}

    print_table(marks, report)
    removed = clear_outputs(out)
    if removed:
        print(f"removed {len(removed)} earlier output(s) from {rel(out)}")
    try:
        home = Home(DOCK_SHOT)                 # the dock box, the top row and WiFiBar, checked on every run
    except RuntimeError as e:
        report.update(status="screenshot check failed", error=str(e))
        write_json(out / "gates.json", report)
        raise
    report["home"] = home.report()
    print(f"screenshot: dock icon box {home.size} px (mask n fitted {home.mask_fit:.2f}, used {LAUNCHER_MASK_N}); "
          f"WiFiBar found at {home.row[home.wifi]['box']} with beam IoU {home.row[home.wifi]['beam_iou']:.3f}")
    if not ok and not args.allow_failures:
        write_json(out / "gates.json", report)
        print(f"\nFAIL: wrote only {rel(out / 'gates.json')}. Use --allow-failures to render the candidates that pass.")
        return 1
    written = {}

    def save(img, name):
        img.save(out / name, format="PNG")
        written[name] = sha256(out / name)

    imgs, tiles = {}, [("Now", home.dock(), home.family())]
    for m in shown:
        imgs[m.number] = render(m)
        stem = f"{m.number:02d}-{m.slug}"
        for px in (CARD_PX,) + ICON_SIZES:
            save(imgs[m.number][px], f"{stem}-{px}.png")
        dock_img = home.dock(launcher_icon(m, imgs[m.number], home.size))
        fam_img = home.family(launcher_icon(m, imgs[m.number], home.family_size))
        save(stack(dock_img, fam_img), f"{stem}-dock.png")
        tiles.append((f"{m.number}   {m.name}", dock_img, fam_img))

    entries = []
    for n in sorted(set(EXPECTED) | set(by_number)):
        m = by_number.get(n)
        if m is not None and m.status == "pass":
            entries.append(dict(number=n, name=m.name, about=m.about, mark=m))
        elif m is None:
            why = next((p["problem"] for p in problems if p["number"] == n), None)
            entries.append(dict(number=n, name=EXPECTED[n], about="", mark=None, state="Not ready",
                                why=["Its mark file could not be used." if why else
                                     "No usable mark file yet." if unattributed else "Its mark file is not there yet."]))
        elif m.status == "dropped":
            entries.append(dict(number=n, name=m.name, about=m.about, mark=None, state="Left out",
                                why=[f"Too close to #{PAD_STROKE} {by_number[PAD_STROKE].name}, so the plan drops it."]))
        else:
            entries.append(dict(number=n, name=m.name, about=m.about, mark=None, state="Failed the checks",
                                why=[r[0].upper() + r[1:] + "." for r in plain_reasons(m, names)]))
    save(sheet(entries, imgs), "sheet.png")
    save(dock_sheet(home, tiles), "dock.png")
    first = by_number.get(PAD_STROKE) if PAD_STROKE in imgs else (shown[0] if shown else None)
    if first is not None:
        save(palette_strip(first), "palettes.png")
        report["palettes_strip"] = dict(candidate=first.number, note="" if first.number == PAD_STROKE else
                                        f"#{PAD_STROKE} is not shown, so #{first.number} stands in")
    report["outputs"] = written
    write_json(out / "gates.json", report)
    print(f"\nwrote {len(written) + 1} files to {rel(out)} ({report['status']})")
    return 0


def print_table(marks, report):
    ink = report["instrument"]["ink_drop"]
    print(f"instrument: Ink drop beam IoU {ink['beam_iou']:.4f} (review {ink['review_iou']}) -> "
          f"{'rejected, as it must be' if ink['rejected'] else 'NOT rejected'}; "
          f"{sum(c['ok'] for c in report['instrument']['self_checks'])}/{len(report['instrument']['self_checks'])} self-checks ok")
    print(f"\n{'#':>2}  {'name':<14} {'size':>6} {'p168':>5} {'p66':>5} {'feat':>5} {'beam':>6} {'radial':>9} {'grid':>4} "
          f"{'distinct':>11} {'contrast':>8}  status")
    for m in marks:
        g = m.gates
        if "error" in g:
            print(f"{m.number:>2}  {m.name:<14} ERROR {g['error']}")
            continue
        p = lambda k: f"{g[k]['count']}/{g[k]['expected'] if g[k]['expected'] else '-'}"  # noqa: E731
        f = g["feature_66"]
        dist = g.get("distinct", {})
        print(f"{m.number:>2}  {m.name:<14} {g['size']['long_side']:>6.3f} {p('pieces_168'):>5} {p('pieces_66'):>5} "
              f"{'ok' if f['pass'] else 'FAIL':>5} {g['beam']['iou']:>6.3f} {g['radial']['max_ratio']:>5.3f}@k{g['radial']['at_k']:<2} "
              f"{g['grid']['most_alike']:>4} {(str(dist.get('iou')) + ' #' + str(dist.get('closest'))) if dist.get('iou') is not None else '-':>11} "
              f"{g['contrast']['ratio']:>8.2f}  {m.status}{' (' + ', '.join(m.failed) + ')' if m.failed else ''}")
    for e in report["missing"]:
        print(f"{e['number']:>2}  {e['name']:<14} MISSING: {e['why']}")
    for p in report["problems"]:
        print(f"    problem in {p['file']}: {p['problem']}")
    for e in report["dropped"]:
        print(f"{e['number']:>2}  {e['name']:<14} {e['why']}")
    for m in marks:
        if "error" in m.gates:
            continue
        low = {t: v for t, v in m.gates["contrast"]["advisory_by_tone"].items() if v < CONTRAST_MIN}
        if low:
            print(f"    note: #{m.number} {m.name} has tones under {CONTRAST_MIN:g}:1 against the plate top: "
                  + ", ".join(f"tone {t} at {v:.2f}:1" for t, v in low.items())
                  + " (advisory; the gate reads the full-tone pale end)")


if __name__ == "__main__":
    sys.exit(main())
