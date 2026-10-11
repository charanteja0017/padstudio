"""Mark 7, Maze (plan §9): one unbroken line through every cell of an 8 x 8 grid.

Recipe: hilbert_points(3) minus 3.5, scaled by g = 0.13; every corner filleted with radius 0.45 g;
drawn at one constant width, 0.070 (Recipe v1.1; was 0.085), so the channels between neighbouring
runs are g - 0.070 = 0.06, the plan's floor (0.085 left 0.045, 1.19 px at 66 px). Plate units:
-1..1, y up; widths are diameters.
"""

import sys
from pathlib import Path

_PROBE = str(Path(__file__).resolve().parents[1])     # Branding/probe, home of canvas.py
if _PROBE not in sys.path:
    sys.path.insert(0, _PROBE)
from canvas import fillet, hilbert_points  # noqa: E402

NUMBER = 7
NAME = "Maze"
SLUG = "maze"
ABOUT = "One unbroken pen line that winds through every square of an 8 by 8 grid without crossing itself."
SIZE_CLASS = "line"
PIECES = 1
GLOW = (0.0, 0.0)

G = 0.13           # grid step
FILLET = 0.45 * G  # corner radius
WIDTH = 0.070      # stroke diameter (Recipe v1.1)

PATH = fillet((hilbert_points(3) - 3.5) * G, FILLET)


def draw(c):
    c.stroke(PATH, WIDTH)
