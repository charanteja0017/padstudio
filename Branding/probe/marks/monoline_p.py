"""Mark 9, Monoline P (plan §9): the letter P drawn with one even, round-ended pen line.

Recipe: stem, bowl = letter_path(bowl_n=3.2, gap=GAP) (gap is in letter_path's own units: the bowl's
returning tail is cut where its centre line comes back within GAP of the stem's, x < GAP). Both are
stroked at width 0.19 with the round brush. The path is scaled so the cap height, stem bottom to bowl top
including the stroke, is 1.06, then moved so the ink's area centroid sits at the plate centre. Plate
units: -1..1, y up; widths are diameters.

Recipe v1.1: the bowl's round end leaves a visible opening of OPENING = 0.06 plate units between its
cap's edge and the stem's edge, so the counter is open. v1.0 passed gap=0.06 straight through: the
bowl's centre line then ended 0.028 plate units from the stem's, inside its 0.095 half width, and the
counter rendered closed (one enclosed hole at 168 and 66 px). GAP below is solved for the opening.
"""

import functools
import sys
from pathlib import Path

import numpy as np

_PROBE = str(Path(__file__).resolve().parents[1])     # Branding/probe, home of canvas.py
if _PROBE not in sys.path:
    sys.path.insert(0, _PROBE)
from canvas import Canvas, letter_path  # noqa: E402

NUMBER = 9
NAME = "Monoline P"
SLUG = "monoline_p"
ABOUT = "The letter P drawn in one even pen line with rounded ends."
SIZE_CLASS = "line"
PIECES = 1
GLOW = (0.0, 0.0)

BOWL_N = 3.2
WIDTH = 0.19        # stroke diameter, plate units
CAP_HEIGHT = 1.06   # stem bottom to bowl top, outer edges of the stroke, plate units
OPENING = 0.06      # Recipe v1.1: bowl cap edge to stem edge, plate units
# GAP, in letter_path's units, solved for OPENING. SCALE = (CAP_HEIGHT - WIDTH) / 2.0 = 0.435 for any gap:
# the stem runs y = -1 .. 1 in path units, up to where the bowl starts. The bowl's end is beside the
# stem (y = -0.05, inside the stem's -1 .. 1), so the opening is x_end * SCALE - WIDTH: the bowl's last
# centre point x_end, scaled, less the two round ends' half widths. Bisection on gap in [0.06, 0.70]
# (30 steps) of that analytic opening, measured over the bowl's lower half, gives the smallest gap
# 0.574498. There the sampled tail jumps from x = 0.574498 (opening 0.0599) to x = 0.576430 (opening
# 0.0607), so every gap in (0.574498, 0.576430] draws the same P. letter_path keeps only points with
# x >= gap, so gap >= (WIDTH + OPENING) / SCALE = 0.574713 holds the opening whatever the sampling.
# GAP is that bound rounded up, inside the interval. Measured: the bowl ends at x = 0.5764, opening 0.0607
# plate units = 1.61 px at 66 px (1.56 px on a 16x render); no enclosed hole at 168 or 66 px.
GAP = 0.5748

STEM, BOWL = letter_path(bowl_n=BOWL_N, gap=GAP)
_LOW = min(STEM[:, 1].min(), BOWL[:, 1].min())
_HIGH = max(STEM[:, 1].max(), BOWL[:, 1].max())
SCALE = (CAP_HEIGHT - WIDTH) / (_HIGH - _LOW)     # round caps add WIDTH / 2 at the bottom and at the top
if BOWL[-1, 0] * SCALE - WIDTH < OPENING:         # the bowl ends beside the stem, at x = BOWL[-1, 0]
    raise AssertionError(f"monoline_p: the bowl leaves {BOWL[-1, 0] * SCALE - WIDTH:.4f}, under {OPENING}")


def _strokes(c, offset):
    for part in (STEM, BOWL):
        c.stroke(part * SCALE + offset, WIDTH)


@functools.cache
def ink_centroid():
    """The ink's area centroid before centring, in plate units, from a 1024 px render (4096 supersampled)."""
    probe = Canvas(1024, 512)
    _strokes(probe, (0.0, 0.0))
    a = probe.array().astype(np.float64)
    u = (np.arange(a.shape[0]) + 0.5) / (a.shape[0] / 2) - 1    # pixel centres -> plate units
    total = a.sum()
    return float(a.sum(axis=0) @ u / total), float(-(a.sum(axis=1) @ u) / total)


def draw(c):
    cx, cy = ink_centroid()
    _strokes(c, (-cx, -cy))
