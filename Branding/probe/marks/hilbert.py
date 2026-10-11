"""Mark 2, Hilbert (plan §9): one unbroken line through every cell of a 4 x 4 grid.

Recipe: hilbert_points(2) minus 1.5, scaled by g = 0.30; every corner filleted with radius 0.45 g;
drawn at one constant width, 0.17. Plate units: -1..1, y up; widths are diameters.
"""

import sys
from pathlib import Path

_PROBE = str(Path(__file__).resolve().parents[1])     # Branding/probe, home of canvas.py
if _PROBE not in sys.path:
    sys.path.insert(0, _PROBE)
from canvas import fillet, hilbert_points  # noqa: E402

NUMBER = 2
NAME = "Hilbert"
SLUG = "hilbert"
ABOUT = "One unbroken pen line that passes through every square of a 4 by 4 grid, with softly rounded corners."
SIZE_CLASS = "line"
PIECES = 1
GLOW = (0.0, 0.0)


def draw(c):
    g = 0.30
    path = fillet((hilbert_points(2) - 1.5) * g, 0.45 * g)
    c.stroke(path, 0.17)
