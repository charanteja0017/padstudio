"""Mark 6, Beyond (plan §9): a pen stroke that cuts through a tile and carries on past its edge.

Recipe: tile = superellipse n 5, a 0.40 (x), b 0.27 (y), rotated -8° (clockwise, y up), centred on the
plate. Stroke = a round brush of width 0.10 along the cubic Bézier (-0.24, -0.06), (0.02, -0.30),
(0.30, 0.12), (0.66, 0.06). Each is rendered to its own canvas of the same size, and the mark is their
absolute difference |tile - stroke| on the supersampled arrays: the stroke is a channel cut out of the
tile where it crosses it, and solid where it runs beyond it. Recipe v1.1: the stroke's tail outside the
tile is kept EDGE_CLEAR = 0.06 clear of the tile's edge. The tile's outline is stroked with a round
brush 2 x 0.06 wide (everything within 0.06 of it), and that band is knocked out of the tail only,
|tile - stroke| - tail * band with tail = stroke * (1 - tile); the tile itself keeps its full edge.
Without it the tail met the tile at single points, so the two pieces joined under 8-connectivity.
Plate units: -1..1, y up; widths are diameters.
"""

import sys
from pathlib import Path

import numpy as np

_PROBE = str(Path(__file__).resolve().parents[1])     # Branding/probe, home of canvas.py
if _PROBE not in sys.path:
    sys.path.insert(0, _PROBE)
from canvas import Canvas, cubic_bezier, superellipse_pts  # noqa: E402

NUMBER = 6
NAME = "Beyond"
SLUG = "beyond"
ABOUT = "A rounded tile with a pen stroke that cuts a channel through it and carries on beyond its edge."
SIZE_CLASS = "line"
PIECES = 2
GLOW = (0.0, 0.0)

TILE = superellipse_pts(0.0, 0.0, 0.40, 0.27, 5, count=2400, rotate_deg=-8.0)
PATH = cubic_bezier((-0.24, -0.06), (0.02, -0.30), (0.30, 0.12), (0.66, 0.06))
STROKE_W = 0.10
EDGE_CLEAR = 0.06   # Recipe v1.1: the tail outside the tile stays this far from the tile's edge


def draw(c):
    tile = Canvas(c.px, c.half, c.scale, c.offset)       # same size and placement as c
    tile.poly(TILE)
    pen = Canvas(c.px, c.half, c.scale, c.offset)
    pen.stroke(PATH, STROKE_W)
    band = Canvas(c.px, c.half, c.scale, c.offset)      # within EDGE_CLEAR of the tile's outline
    band.stroke(np.vstack([TILE, TILE[:1]]), 2 * EDGE_CLEAR)
    t, s, b = tile.array(), pen.array(), band.array()
    tail = s * (1 - t)                                  # the stroke where it runs beyond the tile
    c.set_array(np.abs(t - s) - tail * b)
