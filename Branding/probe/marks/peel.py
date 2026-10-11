"""Mark 8, Peel (plan §9): a page with its lower-right corner folded over onto it.

Recipe: page = superellipse n 5, a 0.42 (x), b 0.46 (y). Fold line x - y = 0.40. The page keeps tone 1.0
only where x - y < 0.40; the corner beyond the fold is removed. The flap is that removed corner reflected
across the fold line, (x, y) -> (y + 0.40, x - 0.40), so it lies on the page; it is drawn at tone 0.6,
with a 0.06 band of value 0 between flap and page (the flap's footprint grown by 0.06 is knocked out
of the page first). Recipe v1.1: the band went from 0.035 (0.93 px at 66 px) to 0.06, the plan's
floor. Plate units: -1..1, y up.
"""

import sys
from pathlib import Path

import numpy as np

_PROBE = str(Path(__file__).resolve().parents[1])     # Branding/probe, home of canvas.py
if _PROBE not in sys.path:
    sys.path.insert(0, _PROBE)
from canvas import superellipse_pts  # noqa: E402

NUMBER = 8
NAME = "Peel"
SLUG = "peel"
ABOUT = "A page with its lower-right corner folded over onto it, like a page halfway through being turned."
SIZE_CLASS = "tile"
PIECES = 2
GLOW = (0.0, 0.0)

PAGE_N, PAGE_A, PAGE_B = 5.0, 0.42, 0.46
FOLD = 0.40        # the fold line is x - y = FOLD
FLAP_TONE = 0.6
GAP = 0.06         # width of the knocked-out band between flap and page (Recipe v1.1)


def clip(poly, side):
    """The part of a closed polygon where side * (x - y - FOLD) >= 0 (Sutherland-Hodgman, one half-plane).
    side = -1 keeps the page side of the fold, side = +1 the corner beyond it."""
    f = side * (poly[:, 0] - poly[:, 1] - FOLD)
    out = []
    for i in range(len(poly)):
        j = (i + 1) % len(poly)
        if f[i] >= 0:
            out.append(poly[i])
        if (f[i] >= 0) != (f[j] >= 0):
            out.append(poly[i] + f[i] / (f[i] - f[j]) * (poly[j] - poly[i]))
    return np.array(out)


def reflect(p):
    """Mirror across the fold line x - y = FOLD."""
    return np.c_[p[:, 1] + FOLD, p[:, 0] - FOLD]


OUTLINE = superellipse_pts(0.0, 0.0, PAGE_A, PAGE_B, PAGE_N, count=2400)
PAGE = clip(OUTLINE, -1)
FLAP = reflect(clip(OUTLINE, +1))


def draw(c):
    c.poly(PAGE)
    c.poly(FLAP, value=0)
    c.stroke(np.vstack([FLAP, FLAP[:1]]), 2 * GAP, value=0)   # round brush, radius GAP: the band
    c.poly(FLAP, value=round(255 * FLAP_TONE))
