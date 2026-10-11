"""Gate fixture for the logo probe (plan §9, "Proof the gates can fail").

The old Ink-drop candidate, kept only so the WiFiBar-beam gate has a known look-alike to reject. Its
silhouette scores about 0.86 against WiFiBar's beam lobe (the review's measurement), above the 0.85 limit.
make_probe.py runs it through the same beam gate as every candidate and raises an error if the gate lets it
through: then the gate is broken, and it is never loosened to make the fixture pass.

INK_DROP has the same interface as a mark module in marks/, so the harness treats it exactly like one.
"""

from types import SimpleNamespace

import numpy as np


def draw_ink_drop(c):
    """A filled drop, x = 0.42 sin t |sin(t/2)|^1.3, y = 0.58 cos t + 0.06, t in [0, 2 pi], with a highlight
    arc knocked out of its lower left (radius 0.22 about (0, -0.16), 200-250 degrees, width 0.05)."""
    t = np.linspace(0, 2 * np.pi, 900)
    c.poly(np.c_[0.42 * np.sin(t) * np.abs(np.sin(t / 2)) ** 1.3, 0.58 * np.cos(t) + 0.06])
    ang = np.deg2rad(np.linspace(200, 250, 200))
    c.stroke(np.c_[0.22 * np.cos(ang), -0.16 + 0.22 * np.sin(ang)], 0.05, value=0)


INK_DROP = SimpleNamespace(
    NUMBER=0,
    NAME="Ink drop (fixture)",
    SLUG="ink_drop_fixture",
    ABOUT="A drop of ink with a highlight arc; kept only as a WiFiBar look-alike the beam gate must reject.",
    SIZE_CLASS="tile",
    PIECES=1,
    GLOW=(0.0, -0.2),
    draw=draw_ink_drop,
    MUST_FAIL="beam",       # the gate that has to reject it
    REVIEW_IOU=0.86,        # what the review measured against the beam lobe
)
