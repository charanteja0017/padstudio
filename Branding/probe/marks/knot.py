"""Mark 4, Knot (plan §9): one closed pen line that weaves over and under itself, like a knot.

Recipe: the curve x = 0.47 cos 3t, y = 0.37 sin 2t, t in [0, 2 pi), with width
w = 0.06 + 0.10 (1 - v / vmax)^1.5, where v = |dP/dt| is the pen's speed (the line swells where the pen
slows). The self-crossings are found by segment intersection on the sampled polyline, and there must be
exactly 7 (asserted at import). Walking the curve in t from t = 0, successive crossing visits alternate
over, under, over, ... (the alternating-knot rule). The full stroke is drawn; then, at each crossing's
under-visit, a disc is erased (value 0) and a short window of the over strand is redrawn through the
crossing. Plate units: -1..1, y up; widths are diameters.

Recipe v1.1, angle-aware gaps: theta in (0, 90 deg] is the angle between the two strands' tangents at a
crossing, and every gap must be at least CLEARANCE = 0.06 across, from the cut-back under strand's ink to
the over strand's ink. The table's radius, (w_over / 2) / sin(theta) + 0.06, gives that 0.06 only along
the under strand's centre line; the under strand's edge on the acute side ends nearer. Measured on this
curve, that radius leaves the edges overlapping by 0.0007 at the two 29.4 deg crossings and 0.028-0.032
apart at the others, and the mark renders as 3 pieces, not 7. So each radius starts from the table's
value and is raised by bisection to the smallest disc whose gap, measured on the real strands (their own
widths and curvature, vector geometry, see gap()), is at least 0.06. For straight strands of constant
width that radius would be hypot((w_over / 2 + 0.06 + (w_under / 2) cos theta) / sin theta, w_under / 2)
(straight_radius(), the bisection's first upper bracket). Solved radii (table radius in brackets):
0.1334 (0.0965) at the 55.4 deg crossing in the centre, 0.1302 and 0.1314 (0.1001) at the four 65.5 deg
ones, 0.2564 (0.1236) at the two 29.4 deg ones. Measured: 7 pieces at 168 and 66 px, 4- and
8-connected; the smallest gap renders 1.56 px at 66 px.

The discs at the 29.4 deg crossings are large enough to overlap their neighbours' discs and to graze the
strands running out from the centre. So the erase and the redraw are composed per crossing on the
supersampled mask: a disc removes ink except where that crossing's redraw lies (its over strand's window,
plus any third strand the disc touches), ink &= ~(disc & ~redraw). That is the same erase-and-redraw, but
one crossing's redraw can never fill another crossing's gap back in, whatever the order.
"""

import sys
from pathlib import Path

import numpy as np

_PROBE = str(Path(__file__).resolve().parents[1])     # Branding/probe, home of canvas.py
if _PROBE not in sys.path:
    sys.path.insert(0, _PROBE)
from canvas import Canvas  # noqa: E402

NUMBER = 4
NAME = "Knot"
SLUG = "knot"
ABOUT = ("One closed pen line, x = cos 3t and y = sin 2t, that weaves over and under itself at its seven "
         "crossings like a knot, and swells where the pen slows down.")
SIZE_CLASS = "line"
PIECES = 7
GLOW = (0.0, 0.0)

AX, AY = 0.47, 0.37          # x = AX cos 3t, y = AY sin 2t
W_MIN, W_RANGE = 0.06, 0.10  # w = W_MIN + W_RANGE (1 - v / vmax)^1.5
CLEARANCE = 0.06             # Recipe v1.1: every gap at least this across (plate units)
LOCAL = 0.02                 # a gap is searched this far past the cut (the strands diverge beyond it;
                             # farther out, two strands can close in again toward another crossing)
RIM = 1440                   # points on an erase disc's rim when measuring a gap
CROSSINGS_EXPECTED = 7
# Samples sit half a step off t = k * 2 pi / SAMPLES. The crossings lie at multiples of pi / 12, and with
# SAMPLES = 2400 no sample lands exactly on one, so segment intersection never meets a shared vertex.
SAMPLES = 2400
VMAX = float(np.hypot(3 * AX, 2 * AY))   # |dP/dt| peaks at t = pi / 2, where sin^2 3t = cos^2 2t = 1


def curve(t):
    t = np.asarray(t, float)
    return np.c_[AX * np.cos(3 * t), AY * np.sin(2 * t)]


def speed(t):
    t = np.asarray(t, float)
    return np.hypot(-3 * AX * np.sin(3 * t), 2 * AY * np.cos(2 * t))


def width(t):
    return W_MIN + W_RANGE * np.clip(1.0 - speed(t) / VMAX, 0.0, None) ** 1.5


def tangent(t):
    """dP/dt, the pen's direction of travel."""
    t = np.asarray(t, float)
    return np.c_[-3 * AX * np.sin(3 * t), 2 * AY * np.cos(2 * t)]


def crossing_angle(t_a, t_b):
    """The angle between the two strands at a crossing (their tangents at t_a and t_b), folded into
    (0, pi / 2]: the acute angle between the two lines."""
    a, b = tangent(t_a)[0], tangent(t_b)[0]
    cos = abs(float(a @ b)) / (np.hypot(*a) * np.hypot(*b))
    return float(np.arccos(min(1.0, cos)))


def self_crossings(p):
    """Where the closed polyline p (its last point joins its first) crosses itself.

    Returns (i, s, j, u) per crossing: segment i (p[i] -> p[i + 1], wrapping) meets segment j > i at
    fraction s along i and u along j. Neighbouring segments share an endpoint and are not tested."""
    n = len(p)
    d = np.roll(p, -1, axis=0) - p
    found = []
    for i in range(n - 2):
        j = np.arange(i + 2, n if i else n - 1)      # segment n - 1 closes the loop and touches segment 0
        ca = p[j] - p[i]
        den = d[i, 0] * d[j, 1] - d[i, 1] * d[j, 0]
        with np.errstate(divide="ignore", invalid="ignore"):
            s = (ca[:, 0] * d[j, 1] - ca[:, 1] * d[j, 0]) / den
            u = (ca[:, 0] * d[i, 1] - ca[:, 1] * d[i, 0]) / den
        hit = (den != 0) & (s >= 0) & (s < 1) & (u >= 0) & (u < 1)
        found.extend((i, float(s[k]), int(j[k]), float(u[k])) for k in np.flatnonzero(hit))
    return found


def _window(seg, centre, reach):
    """Sample indices (mod SAMPLES) of the strand through segment seg, out to the first sample on each
    side that lies farther than reach from centre."""
    lo, hi = seg, seg + 1
    while np.hypot(*(P[lo % SAMPLES] - centre)) <= reach and hi - lo < SAMPLES:
        lo -= 1
    while np.hypot(*(P[hi % SAMPLES] - centre)) <= reach and hi - lo < SAMPLES:
        hi += 1
    if hi - lo >= SAMPLES:
        raise AssertionError("knot: an over-strand window grew to the whole curve")
    return np.arange(lo, hi + 1) % SAMPLES


def _brush_gap(q, idx):
    """Distance from points q to the ink of the brush laid at samples idx (negative inside it)."""
    c, r = P[idx], W[idx] / 2
    return (np.hypot(q[:, None, 0] - c[None, :, 0], q[:, None, 1] - c[None, :, 1]) - r).min(axis=1)


def gap(point, seg_over, seg_under, radius):
    """The narrowest gap an erase disc of this radius leaves at a crossing: from the under strand's ink
    outside the disc to the over strand's ink, in plate units (vector geometry, not a render)."""
    under = _window(seg_under, point, radius + LOCAL)
    over = _window(seg_over, point, radius + LOCAL)
    d = np.gradient(P[under], axis=0)
    offset = np.c_[-d[:, 1], d[:, 0]] / np.hypot(*d.T)[:, None] * (W[under, None] / 2)
    pts = []
    for edge in (P[under] + offset, P[under] - offset):        # the under strand's two edges
        dist = np.hypot(*(edge - point).T)
        pts.append(edge[dist >= radius])
        for a in np.flatnonzero((dist[1:] >= radius) != (dist[:-1] >= radius)):   # where an edge meets the rim
            pts.append(edge[a] + (radius - dist[a]) / (dist[a + 1] - dist[a]) * (edge[a + 1] - edge[a]))
    ang = np.linspace(0, 2 * np.pi, RIM, endpoint=False)
    rim = point + radius * np.c_[np.cos(ang), np.sin(ang)]
    pts.append(rim[_brush_gap(rim, under) <= 0])                # the cut itself, across the under strand
    return float(_brush_gap(np.vstack([np.atleast_2d(p) for p in pts]), over).min())


def straight_radius(theta, w_over, w_under):
    """The radius that leaves CLEARANCE for straight strands of constant width: the under strand's edge on
    the acute side meets the disc's rim s from the crossing, where s sin(theta) - (w_under / 2) cos(theta)
    = w_over / 2 + CLEARANCE."""
    s = (w_over / 2 + CLEARANCE + (w_under / 2) * np.cos(theta)) / np.sin(theta)
    return float(np.hypot(s, w_under / 2))


def solve_radius(point, seg_over, seg_under, r0, r1):
    """The smallest erase radius >= r0 whose gap() is at least CLEARANCE, by bisection: the gap grows with
    the radius, as the cut moves back along the under strand, away from the over strand. r1 (the
    straight-strand radius) is the first upper bracket; it rises in small steps so the search stays local
    to this crossing."""
    if gap(point, seg_over, seg_under, r0) >= CLEARANCE:
        return r0
    lo, hi = r0, max(r0, r1)
    while gap(point, seg_over, seg_under, hi) < CLEARANCE:
        lo, hi = hi, hi + 0.01
        if hi > r1 + 0.05:
            raise AssertionError(f"knot: no erase radius clears the crossing at {point.round(3)}")
    while hi - lo > 1e-6:
        mid = (lo + hi) / 2
        lo, hi = (lo, mid) if gap(point, seg_over, seg_under, mid) >= CLEARANCE else (mid, hi)
    return hi


def _runs(idx):
    """Sorted sample indices, split into runs that are contiguous along the closed curve."""
    parts = np.split(idx, np.flatnonzero(np.diff(idx) > 1) + 1)
    if len(parts) > 1 and parts[0][0] == 0 and parts[-1][-1] == SAMPLES - 1:
        parts = [np.r_[parts[-1], parts[0]]] + parts[1:-1]
    return parts


STEP = 2 * np.pi / SAMPLES
T = STEP * (np.arange(SAMPLES) + 0.5)
P = curve(T)
W = width(T)
if speed(T).max() > VMAX + 1e-12:
    raise AssertionError("knot: a sampled speed exceeds the analytic vmax")

_hits = self_crossings(P)
if len(_hits) != CROSSINGS_EXPECTED:
    raise AssertionError(f"knot: expected exactly {CROSSINGS_EXPECTED} self-crossings, found {len(_hits)}")

# Every crossing is visited twice. Walk the curve in t and alternate over / under at successive visits.
_visits = []                                            # (t, crossing id, segment index)
for k, (i, s, j, u) in enumerate(_hits):
    _visits.append(((T[i] + s * STEP) % (2 * np.pi), k, i))
    _visits.append(((T[j] + u * STEP) % (2 * np.pi), k, j))
_visits.sort()
CROSSINGS = []
for k, (i, s, j, u) in enumerate(_hits):
    order = [n for n, v in enumerate(_visits) if v[1] == k]
    if len(order) != 2 or (order[0] - order[1]) % 2 == 0:
        raise AssertionError(f"knot: crossing {k} cannot alternate (visit order {order})")
    over, under = (_visits[n] for n in sorted(order, key=lambda n: n % 2))   # even visit = over
    point = P[i] + s * (P[(i + 1) % SAMPLES] - P[i])
    w_over, w_under = float(width(over[0])), float(width(under[0]))
    theta = crossing_angle(over[0], under[0])
    if not 0.0 < theta <= np.pi / 2:
        raise AssertionError(f"knot: crossing {k} has angle {np.degrees(theta):.3f} deg, outside (0, 90]")
    # The table's angle-aware radius: along the under strand's centre line the over strand's edge lies
    # (w_over / 2) / sin(theta) from the crossing, and the disc reaches CLEARANCE beyond it. Raised until
    # the under strand's edges clear the over strand by CLEARANCE too.
    radius_table = (w_over / 2) / np.sin(theta) + CLEARANCE
    radius = solve_radius(point, over[2], under[2], radius_table, straight_radius(theta, w_over, w_under))
    gap_left = gap(point, over[2], under[2], radius)
    if gap_left < CLEARANCE:
        raise AssertionError(f"knot: the gap at {point.round(3)} is {gap_left:.4f}, under {CLEARANCE}")
    # Over-strand ink inside the erased disc comes from samples within radius + w / 2 of the crossing.
    # Reach first with the widest brush the curve has, then tighten to the widest brush in that stretch,
    # so the redraw restores the disc and no more.
    window = _window(over[2], point, radius + (W_MIN + W_RANGE) / 2)
    window = _window(over[2], point, radius + W[window].max() / 2)
    # The disc cuts only the under strand: a third strand whose ink it touches is redrawn too (the samples
    # whose ink enters the disc, plus one on each side for the segments joining them).
    touched = np.flatnonzero(np.hypot(*(P - point).T) - W / 2 < radius)
    third = [np.arange(run[0] - 1, run[0] + len(run) + 1) % SAMPLES for run in _runs(touched)
             if not {over[2], under[2]} & set(run.tolist())]
    CROSSINGS.append(dict(
        point=point, t_over=float(over[0]), t_under=float(under[0]), seg_over=over[2], seg_under=under[2],
        theta=theta, w_over=w_over, w_under=w_under, radius_table=float(radius_table), radius=float(radius),
        gap=gap_left, window=window, redraw=[window] + third,
    ))
CROSSINGS.sort(key=lambda x: min(x["t_over"], x["t_under"]))

LOOP = np.vstack([P, P[:1]])
LOOP_W = np.r_[W, W[:1]]


def draw(c):
    def layer(paint):
        t = Canvas(c.px, c.half, c.scale, c.offset)       # same size and placement as c
        paint(t)
        return np.asarray(t.big) > 127                    # strokes and discs are drawn without blending

    ink = layer(lambda t: t.stroke(LOOP, LOOP_W))
    for x in CROSSINGS:
        disc = layer(lambda t: t.disc(x["point"], x["radius"]))
        redraw = layer(lambda t: [t.stroke(P[part], W[part]) for part in x["redraw"]])
        ink &= ~(disc & ~redraw)                          # erase the disc, keep what the redraw lays back
    c.set_array(ink.astype(np.float32))
