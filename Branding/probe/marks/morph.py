"""Mark 5, Morph (plan §9): three overlapping app tiles that morph from a circle to a square.

Recipe: three superellipses |x|^n + |y|^n = a^n with a 0.27 and n = 2, 4, 12, centred at (-0.19, 0.19),
(0, 0) and (0.19, -0.19), at tones 0.55, 0.75 and 1.0 (value = round(255 tone)). They are drawn back to
front, and before each front pane the same shape grown to a + 0.06 is knocked out (value 0), which
leaves a ring at least that wide between the pane and the panes behind it, so the panes stay separate.
Recipe v1.1: the back tone went from 0.45 to 0.55, so every pane stays above a 0.5 coverage threshold
(0.45 dropped the back pane when counted at 0.5), and the ring from 0.055 to 0.06, the plan's floor.
Plate units: -1..1, y up.
"""

import sys
from pathlib import Path

_PROBE = str(Path(__file__).resolve().parents[1])     # Branding/probe, home of canvas.py
if _PROBE not in sys.path:
    sys.path.insert(0, _PROBE)
from canvas import superellipse_pts  # noqa: E402

NUMBER = 5
NAME = "Morph"
SLUG = "morph"
ABOUT = ("Three overlapping app tiles that change shape from a circle to a rounded square to a square, "
         "each one brighter than the one behind it.")
SIZE_CLASS = "tile"
PIECES = 3
GLOW = (0.0, 0.0)

A = 0.27          # half-size of every pane
RING = 0.06       # the knockout before a front pane is the pane grown to A + RING
PANES = (         # back to front: centre, superellipse exponent n, tone
    ((-0.19, 0.19), 2.0, 0.55),
    ((0.0, 0.0), 4.0, 0.75),
    ((0.19, -0.19), 12.0, 1.0),
)
COUNT = 2400      # outline points; dense so the n = 12 corners stay smooth at 440 px


def draw(c):
    for k, ((cx, cy), n, tone) in enumerate(PANES):
        if k:   # a front pane: clear its grown outline first, leaving a ring around it
            c.poly(superellipse_pts(cx, cy, A + RING, A + RING, n, count=COUNT), value=0)
        c.poly(superellipse_pts(cx, cy, A, A, n, count=COUNT), value=round(255 * tone))
