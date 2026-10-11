"""Mark 1, Pad stroke (plan §9): an app tile cut in two by one calligraphic pen stroke.

Recipe: tile = superellipse n 5, a 0.46. The cut follows x in [-0.70, 0.70], y = 0.15 sin(pi x / 0.70),
rotated 24° (counter-clockwise, y up), with width w(u) = 0.035 + 0.085 sin(pi u)^0.8, where u runs 0..1
along the cut (u = (x + 0.70) / 1.40). The cut is drawn with value 0, so it knocks out of the tile.
Plate units: -1..1, y up; widths are diameters.
"""

import sys
from pathlib import Path

import numpy as np

_PROBE = str(Path(__file__).resolve().parents[1])     # Branding/probe, home of canvas.py
if _PROBE not in sys.path:
    sys.path.insert(0, _PROBE)
from canvas import superellipse_pts  # noqa: E402

NUMBER = 1
NAME = "Pad stroke"
SLUG = "pad_stroke"
ABOUT = "A rounded app tile split in two by a single pen stroke that swells in the middle and tapers at both ends."
SIZE_CLASS = "tile"
PIECES = 2
GLOW = (0.0, 0.0)


def draw(c):
    c.poly(superellipse_pts(0.0, 0.0, 0.46, 0.46, 5))
    u = np.linspace(0.0, 1.0, 1400)
    x = -0.70 + 1.40 * u
    y = 0.15 * np.sin(np.pi * x / 0.70)
    a = np.deg2rad(24.0)
    cut = np.c_[x * np.cos(a) - y * np.sin(a), x * np.sin(a) + y * np.cos(a)]
    c.stroke(cut, 0.035 + 0.085 * np.clip(np.sin(np.pi * u), 0.0, None) ** 0.8, value=0)
