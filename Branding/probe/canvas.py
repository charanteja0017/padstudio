"""Shared drawing helpers for the logo probe (plan §9). Marks in Branding/probe/marks/ draw with these.

Plate units: the icon plate spans -1..1 on both axes, y up. Stroke widths are diameters. A value of 0
knocks pixels out of the mark (a cut), 255 is full mark, anything between is a tone.

  from canvas import Canvas, icon, PALETTES                    (run with python3 -I -B, path set by the caller)
"""

import importlib.util
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent

_spec = importlib.util.spec_from_file_location("make_brand", HERE.parent / "make_brand.py")
mb = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mb)

# name: (gradient top, gradient bottom, glow, pale end of the mark, glow opacity). One hue family each.
PALETTES = {
    "Violet": ("#5A3FD6", "#120A2E", "#C38BFF", "#CDBEFF", 0.30),
    "Ocean": ("#0F8B8D", "#041A22", "#59F0D6", "#B9FBEF", 0.28),
    "Rose": ("#C2185B", "#22051A", "#FF7EB6", "#FFD1E6", 0.28),
    "Emerald": ("#138A55", "#03170E", "#62F0A8", "#C6F8DC", 0.26),
    "Graphite": ("#4A4A52", "#111114", "#FFFFFF", "#D1D1D6", 0.14),
}
SS = 4                     # supersampling for every mask
BODY_SHARE = 412 / 1024    # plate half-size on sheets: 824 px body on a 1024 canvas, as the brand kit
LAUNCHER_MASK_N = 4.4      # HyperOS dock mask, fitted from six dock icons (plan §9)


class Canvas:
    """A mask drawn in plate units at px x px (supersampled SS x SS).

    scale and offset map what a mark draws onto the plate (p * scale + offset), so the probe can bring
    every mark to its size class without the mark knowing; widths and radii scale with it."""

    def __init__(self, px, half, scale=1.0, offset=(0.0, 0.0)):
        self.px, self.half, self.scale = px, half, scale
        self.offset = np.asarray(offset, float)
        self.big = Image.new("L", (px * SS, px * SS), 0)
        self.d = ImageDraw.Draw(self.big)

    def at(self, p):
        p = np.atleast_2d(np.asarray(p, float)) * self.scale + self.offset
        return np.c_[(self.px / 2 + p[:, 0] * self.half) * SS, (self.px / 2 - p[:, 1] * self.half) * SS]

    def poly(self, pts, value=255):
        self.d.polygon([tuple(q) for q in self.at(pts)], fill=value)

    def disc(self, centre, r, value=255):
        (x, y), = self.at(centre)
        rr = r * self.scale * self.half * SS
        self.d.ellipse((x - rr, y - rr, x + rr, y + rr), fill=value)

    def stroke(self, pts, widths, value=255):
        """A round brush swept along pts; widths are diameters, one per point or one for all."""
        P = self.at(pts)
        R = np.broadcast_to(np.asarray(widths, float), (len(P),)) / 2 * self.scale * self.half * SS
        seg = np.diff(P, axis=0)
        length = np.hypot(seg[:, 0], seg[:, 1])
        length[length == 0] = 1
        normal = np.c_[-seg[:, 1], seg[:, 0]] / length[:, None]
        for i in range(len(P) - 1):
            a, b, na, nb = P[i], P[i + 1], normal[i] * R[i], normal[i] * R[i + 1]
            self.d.polygon([tuple(a + na), tuple(b + nb), tuple(b - nb), tuple(a - na)], fill=value)
        for (x, y), r in zip(P, R):
            if r > 0.3:
                self.d.ellipse((x - r, y - r, x + r, y + r), fill=value)

    def array(self):
        """The supersampled mask as floats in 0..1 (for mask arithmetic such as XOR)."""
        return np.asarray(self.big, np.float32) / 255.0

    def set_array(self, a):
        self.big = Image.fromarray(np.clip(np.round(a * 255), 0, 255).astype(np.uint8), "L")
        self.d = ImageDraw.Draw(self.big)

    def mask(self):
        return self.big.resize((self.px, self.px), Image.LANCZOS)


def superellipse_pts(cx, cy, hx, hy, n, count=480, rotate_deg=0.0):
    t = np.linspace(0, 2 * np.pi, count, endpoint=False)
    c, s = np.cos(t), np.sin(t)
    p = np.c_[hx * np.sign(c) * np.abs(c) ** (2 / n), hy * np.sign(s) * np.abs(s) ** (2 / n)]
    if rotate_deg:
        a = np.deg2rad(rotate_deg)
        p = p @ np.array([[np.cos(a), np.sin(a)], [-np.sin(a), np.cos(a)]])
    return p + (cx, cy)


def share(p):
    """Arc length along p as a share of the whole, 0..1."""
    s = np.r_[0, np.cumsum(np.hypot(*np.diff(p, axis=0).T))]
    return s / s[-1]


def densify(p, step=0.004):
    out = [p[0]]
    for a, b in zip(p[:-1], p[1:]):
        k = max(1, int(np.ceil(np.hypot(*(b - a)) / step)))
        out.extend(a + (b - a) * (np.arange(1, k + 1) / k)[:, None])
    return np.array(out)


def fillet(pts, r, steps=28):
    """Round every right-angle corner of a polyline with a quarter circle of radius r."""
    out = [pts[0]]
    for i in range(1, len(pts) - 1):
        a, p, b = pts[i - 1], pts[i], pts[i + 1]
        d1, d2 = (p - a) / np.linalg.norm(p - a), (b - p) / np.linalg.norm(b - p)
        if abs(d1[0] * d2[1] - d1[1] * d2[0]) < 1e-9:
            out.append(p)
            continue
        start, end = p - d1 * r, p + d2 * r
        centre = start + d2 * r
        a0 = math.atan2(*(start - centre)[::-1])
        a1 = math.atan2(*(end - centre)[::-1])
        turn = (a1 - a0 + math.pi) % (2 * math.pi) - math.pi
        ang = a0 + turn * np.linspace(0, 1, steps)
        out.extend(centre + r * np.c_[np.cos(ang), np.sin(ang)])
    out.append(pts[-1])
    return densify(np.array(out))


def hilbert_points(order):
    """The Hilbert curve's cells in visiting order (the classic d -> (x, y) conversion)."""
    n, pts = 2 ** order, []
    for d in range(n * n):
        x = y = 0
        t, s = d, 1
        while s < n:
            rx = 1 & (t // 2)
            ry = 1 & (t ^ rx)
            if ry == 0:
                if rx == 1:
                    x, y = s - 1 - x, s - 1 - y
                x, y = y, x
            x, y = x + s * rx, y + s * ry
            t //= 4
            s *= 2
        pts.append((x, y))
    return np.array(pts, float)


def cubic_bezier(p0, p1, p2, p3, count=600):
    t = np.linspace(0, 1, count)[:, None]
    p0, p1, p2, p3 = (np.asarray(p, float) for p in (p0, p1, p2, p3))
    return (1 - t) ** 3 * p0 + 3 * (1 - t) ** 2 * t * p1 + 3 * (1 - t) * t ** 2 * p2 + t ** 3 * p3


def letter_path(bowl_n=2.4, bowl_c=0.42, bowl_a=0.72, bowl_b=0.58, gap=0.0):
    """Centre line of a P (copied from make_brand.py, which W4 will change): stem up, superellipse bowl
    clockwise back toward the stem. gap > 0 stops the bowl that far short of the stem (an open counter)."""
    t = np.linspace(np.pi / 2, -np.pi / 2, 600)
    bowl = np.c_[
        bowl_a * np.sign(np.cos(t)) * np.abs(np.cos(t)) ** (2 / bowl_n),
        bowl_c + bowl_b * np.sign(np.sin(t)) * np.abs(np.sin(t)) ** (2 / bowl_n),
    ]
    if gap > 0:
        # Keep the bowl's start (it joins the stem at the top); trim only its returning tail where x < gap.
        tail_keep = len(bowl) - int(np.argmax(bowl[::-1, 0] >= gap))
        bowl = bowl[:tail_keep]
    stem = np.c_[np.zeros(520), np.linspace(-1.0, bowl[0, 1], 520)]
    return stem, bowl


def icon(px, draw, palette="Violet", glow=(0.0, 0.0), mask_n=5.0, full_bleed=False, scale=1.0, offset=(0.0, 0.0)):
    """A mark (draw(canvas)) on its plate. full_bleed fills the square, as a launcher shows an icon."""
    top, bottom, glow_c, pale, glow_a = PALETTES[palette]
    half = px / 2 if full_bleed else px * BODY_SHARE
    canvas = Canvas(px, half, scale, offset)
    draw(canvas)
    mark = canvas.mask()
    art = mb.linear_gradient(px, (0, px / 2 - half), (0, px / 2 + half), top, bottom)
    gx, gy = glow
    art.alpha_composite(mb.glow(px, (px / 2 + gx * half, px / 2 - gy * half), glow_c, 0.4 * half, glow_a))
    fill = mb.linear_gradient(px, (px / 2 - 0.6 * half, px / 2 + 0.6 * half), (px / 2 + 0.6 * half, px / 2 - 0.6 * half), pale, "#FFFFFF")
    art.alpha_composite(Image.composite(fill, mb.clear(px), mark))
    body = mb.superellipse(px / 2, px / 2, half, n=mask_n)
    out = mb.clear(px)
    out.paste(art, (0, 0), mb.polygon_mask(px, body))
    if not full_bleed:
        rim = mb.clear(px)
        ImageDraw.Draw(rim).line([tuple(q) for q in body] + [tuple(body[0])], fill=(255, 255, 255, 31), width=max(1, round(4 * px / 1024)))
        out.alpha_composite(Image.composite(rim, mb.clear(px), mb.polygon_mask(px, body)))
    return out


def mark_mask(px, draw, half=None, scale=1.0, offset=(0.0, 0.0)):
    """Just the mark as a float array 0..1 at px (no plate), for the gates."""
    c = Canvas(px, half if half is not None else px * BODY_SHARE, scale, offset)
    draw(c)
    return np.asarray(c.mask(), np.float32) / 255.0


def bbox_plate_units(draw, px=880):
    """The mark's bounding box in plate units (x0, y0, x1, y1), measured from a render at px."""
    m = mark_mask(px, draw, half=px / 2)
    ys, xs = np.nonzero(m > 0.5)
    to_u = lambda v: v / (px / 2) - 1
    return to_u(xs.min()), -to_u(ys.max() + 1), to_u(xs.max() + 1), -to_u(ys.min())
