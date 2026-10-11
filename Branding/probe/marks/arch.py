"""Mark 3, Arch (plan §9): a rounded slab with an arched doorway, lit from inside.

Recipe: slab = superellipse n 5, a 0.38 (x), b 0.46 (y). The doorway is knocked out of the slab where
abs(x) <= 0.22 and y <= 0.10 - 0.08 (cosh(x / 0.08) - 1), a catenary arch open at the slab's base.
The plate's glow sits at (0, -0.22), inside the doorway. Plate units: -1..1, y up.
"""

import sys
from pathlib import Path

import numpy as np

_PROBE = str(Path(__file__).resolve().parents[1])     # Branding/probe, home of canvas.py
if _PROBE not in sys.path:
    sys.path.insert(0, _PROBE)
from canvas import superellipse_pts  # noqa: E402

NUMBER = 3
NAME = "Arch"
SLUG = "arch"
ABOUT = "A rounded slab with an arched doorway cut into its base and a soft light glowing through it."
SIZE_CLASS = "tile"
PIECES = 1
GLOW = (0.0, -0.22)


def draw(c):
    c.poly(superellipse_pts(0.0, 0.0, 0.38, 0.46, 5))
    x = np.linspace(-0.22, 0.22, 801)
    top = 0.10 - 0.08 * (np.cosh(x / 0.08) - 1.0)
    # Everything under the arch, down past the slab's base (y = -0.46), within abs(x) <= 0.22. The slab
    # is the only thing drawn, so the knockout only ever removes slab pixels ("inside the slab").
    door = np.r_[np.c_[x, top], [[0.22, -1.0], [-0.22, -1.0]]]
    c.poly(door, value=0)
