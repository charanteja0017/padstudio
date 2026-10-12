"""Build the Focus Pen Pro 3D asset for PadStudio: one glTF binary with markers, effects and six clips.

Run headless with Blender 5.1.2 from the repository root:

    /Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup \
        --python Branding/pen3d/build_pen.py -- [--source FILE] [--out DIR ...] [--save-blend FILE]

Without --source it builds a PLACEHOLDER pen from math: a lathe profile scaled from the owner's
motion kit (docs/redesign/pen-motion-kit.html) to a NOMINAL length of 0.165 m (not a measurement).
With --source it wraps the owner's own model (.glb, .gltf, .fbx or .blend), which must contain the
six marker empties. Either way the script attaches the gesture effects to the markers, makes sure
the clips idle, squeeze, slide_up, slide_down, double_tap and write exist, and writes
focus_pen_pro.glb into every output directory (default: docs/redesign/assets/pen and
padstudio/app/src/main/assets/pen). See Branding/pen3d/README.md.
"""

import argparse
import hashlib
import json
import math
import os
import re
import shutil
import struct
import sys
import traceback

import bpy
from mathutils import Matrix, Quaternion, Vector
from mathutils.geometry import delaunay_2d_cdt

# ------------------------------------------------------------------------------------------------
# Contract shared with the preview (<model-viewer>) and the app (SceneView)
# ------------------------------------------------------------------------------------------------
CLIPS = ("idle", "squeeze", "slide_up", "slide_down", "double_tap", "write")
CLIP_SECONDS = {"idle": 6.0, "squeeze": 1.7, "slide_up": 1.7, "slide_down": 1.7,
                "double_tap": 1.7, "write": 1.7}
MARKERS = ("marker_squeeze", "marker_slide_top", "marker_slide_bottom", "marker_double_tap",
           "marker_nib", "marker_top")  # six: four gesture spots, then the two ends of the pen
FX_PREFIX = "fx_"  # objects with this prefix belong to this script and are rebuilt on every run
FX_ARC_L, FX_ARC_R = "fx_squeeze_arc_left", "fx_squeeze_arc_right"
FX_DOT, FX_RING = "fx_slide_dot", "fx_tap_ring"
FX_ORDER = (FX_ARC_L, FX_ARC_R, FX_DOT, FX_RING)
FX_PARENT = {FX_ARC_L: "marker_squeeze", FX_ARC_R: "marker_squeeze",
             FX_DOT: "marker_slide_bottom", FX_RING: "marker_double_tap"}
FPS = 30
OUT_NAME = "focus_pen_pro.glb"
MAX_TRIANGLES = 50000
HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
DEFAULT_OUTS = (os.path.join(REPO, "docs", "redesign", "assets", "pen"),
                os.path.join(REPO, "padstudio", "app", "src", "main", "assets", "pen"))

# ------------------------------------------------------------------------------------------------
# Placeholder proportions, from the owner's motion kit (Focus Pen Pro SVG, viewBox 300 x 640):
#   body   rect x 134..166 (32 wide), y 48..534 (486 long), rx 12: a rounded top end
#   collar rect y 520..534 (14 units) that squares off the tip end of the body
#   tip    path 534 -> 568, half-width 16 -> 3 (34 long), then the nib, a quadratic to y 575.5
#   seams  hairlines at y 65 (end cap) ; wordmark at y 109 ; squeeze arcs centred on y 372 (the squeeze
#          marker no longer follows that: every gesture sits in the grip band, see "Gesture spots")
# Kit y runs down the pen. Pen space here uses glTF axes: +Y toward the end cap, tip at -Y,
# +Z toward the default camera, origin at the pen centre, metres.
# ------------------------------------------------------------------------------------------------
KIT_TOP, KIT_APEX = 48.0, 575.5
NOMINAL_LENGTH = 0.165  # metres, end-cap top to nib apex. NOMINAL, not measured.
K = NOMINAL_LENGTH / (KIT_APEX - KIT_TOP)  # metres per kit unit
KIT_MID = 0.5 * (KIT_TOP + KIT_APEX)
MM = 0.001


def kit_y(v):
    """Kit SVG y -> pen-space Y in metres."""
    return (KIT_MID - v) * K


R = 16.0 * K                # barrel radius (kit half-width 16)
Y_TOP = kit_y(48.0)         # end-cap top
Y_CAP_ARC = kit_y(60.0)     # centre height of the rx 12 corner
Y_SEAM = kit_y(65.0)        # end-cap seam
Y_BAND = kit_y(109.0)       # logo band (the kit's wordmark position)
Y_COLLAR = kit_y(520.0)     # collar groove
Y_BODY_END = kit_y(534.0)   # body / tip cone
Y_CONE_END = kit_y(568.0)   # tip cone / nib
Y_APEX = kit_y(KIT_APEX)    # nib apex

# Gesture spots. The kit put them up the barrel; the owner wants every function close to the nib, so
# they sit in the grip band, 24 to 50 mm above the apex (docs/redesign/ROUND2-BRIEF.md has the table).
# The barrel markers are at z = 0.005 exactly, as in that table (the barrel surface is at 0.0050047).
Y_SLIDE_BOTTOM, Y_SLIDE_TOP = -56.5 * MM, -34.5 * MM  # 2 mm inside the strip's two ends
Y_SQUEEZE = -50.5 * MM
Y_DOUBLE_TAP = -38.5 * MM
MARKER_Z = 5.0 * MM

# Design choices that the kit does not fix (placeholder only)
SEAM_W, SEAM_D = 0.20 * MM, 0.12 * MM       # end-cap seam: a V-groove, half on each part
GROOVE_HW, GROOVE_D = 0.25 * MM, 0.12 * MM  # collar groove
BAND_W, BAND_D = 0.90 * MM, 0.15 * MM       # logo band: an engraved ring, no text
STRIP_Y0, STRIP_Y1 = -58.5 * MM, -32.5 * MM  # slide strip (a pill) on the camera-facing side
STRIP_HW = 1.7 * MM                          # half-width, as arc length on the barrel
STRIP_D, STRIP_C = 0.22 * MM, 0.15 * MM      # recess depth and chamfer inset
PILL_Y0, PILL_Y1 = STRIP_Y0 + STRIP_HW, STRIP_Y1 - STRIP_HW  # centres of the pill's two end caps
# Grip band: six shallow ring grooves, each a cosine dip (smooth, so the strip's window can follow it),
# 4 mm apart and centred in the band between Y -58 and -34 mm. The strip's recess cuts through them.
GRIP_YS = tuple((-46.0 + 4.0 * (k - 2.5)) * MM for k in range(6))
GRIP_W, GRIP_D = 0.4 * MM, 0.1 * MM          # groove width (rim to rim) and depth
GRIP_STEPS = 4                               # profile intervals across one groove (rings 0.1 mm apart)
SEGS = 128                                   # lathe segments (2.8125 degrees)
WIN_HALF = 11                                # strip window half-width in segments (about 31 degrees)
WIN_Y0, WIN_Y1 = STRIP_Y0 - 1.5 * MM, STRIP_Y1 + 1.5 * MM
WIN_DY = 1.0 * MM                            # largest ring spacing inside the window band
assert WIN_Y0 < GRIP_YS[0] - GRIP_W and GRIP_YS[-1] + GRIP_W < WIN_Y1, "grip grooves leave the window"
assert PILL_Y0 < GRIP_YS[0] - GRIP_W and GRIP_YS[-1] + GRIP_W < PILL_Y1, "grip grooves reach a strip cap"

# Gesture effects, in units of the barrel radius (the kit's half-width 16), from the kit's SVG:
#   squeeze arcs "M114 352 Q99 372 114 392": chord 40, sagitta 7.5, apex 43.5 from the axis,
#   moving 13 inward; tap ring r 18..30; slide dot r 6 (smaller here so it sits on the strip).
# Two effects are drawn smaller than the kit's for the close-up on the grip band: the tap ring has
# radius 6 mm (the kit's 9.38), and the squeeze arcs keep their shape at 0.588 of the kit's size, apex
# 8 mm from the axis (the kit's 13.61). The kit's 4.07 mm of inward travel no longer fits (it would
# carry an 8 mm apex to 3.9 mm, inside the 5 mm barrel), so the arcs close in only until their tube
# is 0.1 mm off the barrel at the arcs' ends, the nearest point: about 1.0 mm, and never inside the pen.
FX_TUBE = 0.10
_ARC_CHORD, _ARC_SAG = 40.0, 7.5
_ARC_RHO_KIT = _ARC_CHORD ** 2 / (8 * _ARC_SAG) + _ARC_SAG / 2   # kit units
ARC_HALF_ANGLE = math.asin((_ARC_CHORD / 2) / _ARC_RHO_KIT)      # 82 degree span, as in the kit
ARC_APEX = 8.0 * MM / R                                          # apex distance from the axis
ARC_CLOSE = ARC_APEX / (43.5 / 16.0)                             # close-up scale against the kit
ARC_RHO = _ARC_RHO_KIT / 16.0 * ARC_CLOSE
ARC_GAP = 0.1 * MM / R                                           # clearance left at the squeeze's peak
ARC_TRAVEL = ARC_APEX - 1.0 - FX_TUBE - ARC_RHO * (1.0 - math.cos(ARC_HALF_ANGLE)) - ARC_GAP
RING_RADIUS = 6.0 * MM / R
RING_LIFT = 0.12
DOT_RADIUS = 0.26
DOT_LIFT = 0.076

# Clip motion (absolute sizes, so an owner's model at real scale moves the same way)
IDLE_SWAY_DEG = 12.0         # about the pen axis (Y)
IDLE_FLOAT = 2.0 * MM        # rises 2 mm and settles, once per loop
TAP_TILT_DEG = 1.2           # nudge per tap, in the picture plane
TAP_RECOIL = 0.5 * MM        # nudge per tap, away from the camera
WRITE_TILT_DEG = -22.0       # the top leans toward +X, pivoting on the nib
WRITE_WOBBLE_DEG = 3.0
WRITE_LOOP = (3.0 * MM, 1.5 * MM)  # the nib traces an ellipse 6 x 3 mm in the picture plane

MATERIALS = {  # name: (base colour sRGB, metallic, roughness, emissive sRGB or None)
    "satin_white": ("#E9ECF2", 0.0, 0.35, None),
    "matte_dark_grey": ("#2B2F36", 0.0, 0.55, None),
    "tip_light": ("#D5DAE2", 0.0, 0.40, None),  # the owner's reference: a light tip cone, dark nib
    "strip_gloss": ("#DDE1E8", 0.0, 0.20, None),
    "band_engraved": ("#AEB5C1", 0.0, 0.50, None),
    "fx_lavender": ("#BBAAFF", 0.0, 0.40, "#BBAAFF"),
}

# Blender is Z-up; the glTF exporter maps Blender (x, y, z) to glTF (x, z, -y).
C_TO_BL = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
C_TO_PEN = C_TO_BL.inverted()


def bl(v):
    """Pen-space (glTF axes) vector -> Blender vector."""
    return Vector((v[0], -v[2], v[1]))


def pen(v):
    """Blender vector -> pen space (glTF axes)."""
    return Vector((v[0], v[2], -v[1]))


def log(msg):
    print("[build_pen] " + msg)
    sys.stdout.flush()


class BuildError(Exception):
    def __init__(self, msg, code=1):
        super().__init__(msg)
        self.code = code


def srgb_to_linear(hexstr):
    out = []
    for i in (1, 3, 5):
        c = int(hexstr[i:i + 2], 16) / 255.0
        out.append(c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4)
    return tuple(out)


# ------------------------------------------------------------------------------------------------
# Geometry helpers (pen space)
# ------------------------------------------------------------------------------------------------
class Geo:
    """Positions, per-vertex normals and polygons in pen space. Polygons are wound
    counter-clockwise seen from the side their normals point to."""

    def __init__(self):
        self.p, self.n, self.f = [], [], []

    def vert(self, p, n):
        nn = Vector(n)
        nn.normalize()
        self.p.append(Vector(p))
        self.n.append(nn)
        return len(self.p) - 1

    def poly(self, idx):
        clean = []
        for i in idx:
            if not clean or clean[-1] != i:
                clean.append(i)
        if len(clean) > 1 and clean[0] == clean[-1]:
            clean.pop()
        if len(clean) < 3:
            return
        fn = Vector((0.0, 0.0, 0.0))  # Newell normal
        for a, b in zip(clean, clean[1:] + clean[:1]):
            pa, pb = self.p[a], self.p[b]
            fn.x += (pa.y - pb.y) * (pa.z + pb.z)
            fn.y += (pa.z - pb.z) * (pa.x + pb.x)
            fn.z += (pa.x - pb.x) * (pa.y + pb.y)
        mean = Vector((0.0, 0.0, 0.0))
        for i in clean:
            mean += self.n[i]
        if fn.dot(mean) < 0.0:
            clean.reverse()
        self.f.append(clean)

    def triangles(self):
        return sum(len(f) - 2 for f in self.f)

    def compact(self):
        """Drop vertices no polygon uses (the lathe rings inside the strip window)."""
        used = sorted({i for f in self.f for i in f})
        remap = {old: new for new, old in enumerate(used)}
        self.p = [self.p[i] for i in used]
        self.n = [self.n[i] for i in used]
        self.f = [[remap[i] for i in f] for f in self.f]
        return self


def ring_angle(j):
    return 2.0 * math.pi * j / SEGS


def signed_angle(j):
    a = ring_angle(j % SEGS)
    return a - 2.0 * math.pi if a > math.pi else a


def lathe_ring(g, r, y, nr, ny):
    if r <= 1e-12:  # a pole: one vertex
        return [g.vert((0.0, y, 0.0), (0.0, 1.0 if ny >= 0 else -1.0, 0.0))] * SEGS
    ids = []
    for j in range(SEGS):
        a = ring_angle(j)
        s, c = math.sin(a), math.cos(a)
        ids.append(g.vert((r * s, y, r * c), (nr * s, ny, nr * c)))
    return ids


def lathe(g, profile, skip=None):
    """profile: [(r, y, nr, ny), ...] for one smooth piece. Angle 0 faces +Z (the camera)."""
    rings = [lathe_ring(g, *pt) for pt in profile]
    for i in range(len(rings) - 1):
        a, b = rings[i], rings[i + 1]
        for j in range(SEGS):
            if skip is not None and skip(i, j):
                continue
            k = (j + 1) % SEGS
            g.poly((a[j], a[k], b[k], b[j]))
    return rings


def seg_line(p0, p1, n=1):
    """A straight profile piece from p0 = (r, y) to p1 with its outward normal."""
    dr, dy = p1[0] - p0[0], p1[1] - p0[1]
    nr, ny = -dy, dr
    ln = math.hypot(nr, ny)
    return [(p0[0] + dr * i / n, p0[1] + dy * i / n, nr / ln, ny / ln) for i in range(n + 1)]


def refine(anchors, step):
    """Ascending heights -> the same heights with extra ones between, so no gap exceeds `step`.
    The anchors themselves are kept exactly."""
    out = [anchors[0]]
    for a, b in zip(anchors, anchors[1:]):
        n = max(1, math.ceil((b - a) / step - 1e-9))
        out += [a + (b - a) * i / n for i in range(1, n)] + [b]
    return out


def grip_rows():
    """Ascending heights of the profile rings across the six grip grooves (rim, quarter, centre,
    quarter, rim: 0.1 mm apart)."""
    return [yc + 0.5 * GRIP_W * (2.0 * i / GRIP_STEPS - 1.0) for yc in GRIP_YS for i in range(GRIP_STEPS + 1)]


def grip_depth(y):
    """How far the grip grooves cut the barrel in at height y, and the slope of that cut (d depth / dy)."""
    d = ds = 0.0
    for yc in GRIP_YS:
        t = (y - yc) / (0.5 * GRIP_W)
        if -1.0 < t < 1.0:
            d += 0.5 * GRIP_D * (1.0 + math.cos(math.pi * t))
            ds -= GRIP_D * math.pi / GRIP_W * math.sin(math.pi * t)
    return d, ds


def barrel_point(y):
    """The barrel's profile point at height y, grooves included: (r, y, nr, ny) with the exact unit
    normal. The surface is r = R - depth(y), so its outward normal is (1, depth'(y)) / length."""
    d, ds = grip_depth(y)
    n = math.hypot(1.0, ds)
    return (R - d, y, 1.0 / n, ds / n)


def barrel_vert(g, s, y):
    """A vertex on the barrel surface at arc length s (measured at radius R) and height y."""
    r, _y, nr, ny = barrel_point(y)
    a = s / R
    return g.vert((r * math.sin(a), y, r * math.cos(a)), (nr * math.sin(a), ny, nr * math.cos(a)))


def window_levels():
    """Heights of the barrel rings in the strip window, top to bottom: at most WIN_DY apart, with the
    rings that shape each grip groove among them."""
    return refine([WIN_Y0] + grip_rows() + [WIN_Y1], WIN_DY)[::-1]


def on_barrel(s, y, rho):
    """(arc length s at radius R, y) -> point at radius rho."""
    a = s / R
    return Vector((rho * math.sin(a), y, rho * math.cos(a)))


def radial(s):
    a = s / R
    return Vector((math.sin(a), 0.0, math.cos(a)))


def pill_outline(hw):
    """Counter-clockwise outline of the slide strip in (s, y): (s, y, outward ns, outward ny). Each
    straight side has a vertex at every grip-groove ring, so the strip's rim follows the grooves."""
    yc0, yc1 = PILL_Y0, PILL_Y1
    ys = refine([yc0] + grip_rows() + [yc1], 0.5 * MM)
    n_cap = 12
    pts = []
    for y in ys[:-1]:  # right side, up (the top corner belongs to the cap)
        pts.append((hw, y, 1.0, 0.0))
    for i in range(n_cap):
        a = math.pi * i / n_cap
        pts.append((hw * math.cos(a), yc1 + hw * math.sin(a), math.cos(a), math.sin(a)))
    for y in reversed(ys[1:]):  # left side, down (the bottom corner belongs to the cap)
        pts.append((-hw, y, -1.0, 0.0))
    for i in range(n_cap):
        a = math.pi + math.pi * i / n_cap
        pts.append((hw * math.cos(a), yc0 + hw * math.sin(a), math.cos(a), math.sin(a)))
    return pts


def pill_distance(s, y):
    """Distance from (s, y) to the strip's centre line; < half-width means inside the strip."""
    yc = min(max(y, PILL_Y0), PILL_Y1)
    return math.hypot(s, y - yc)


def cdt(points, loop, constraint_loops):
    """Constrained Delaunay triangulation inside `loop` (input indices), in millimetres.
    Returns triangles as tuples of input indices. Refuses to invent vertices."""
    co = [Vector((p[0] / MM, p[1] / MM)) for p in points]
    edges = []
    for lp in constraint_loops:
        edges += [(lp[i], lp[(i + 1) % len(lp)]) for i in range(len(lp))]
    _v, _e, faces, orig_v, _oe, _of = delaunay_2d_cdt(co, edges, [list(loop)], 1, 1e-6)
    remap = []
    for ov in orig_v:
        if not ov:
            raise BuildError("triangulation invented a vertex; strip geometry is inconsistent")
        remap.append(min(ov))
    return [tuple(remap[i] for i in f) for f in faces]


def build_barrel_window(g, rings, i_top, i_bot):
    """Fill the body's window (rings i_top..i_bot, +-WIN_HALF segments) around the strip's hole."""
    pts, ids = [], []  # (s, y) and the body vertex for each CDT input point

    def add(s, y, vid=None):
        pts.append((s, y))
        ids.append(vid)
        return len(pts) - 1

    y_of = window_levels()
    loop = []
    for j in list(range(SEGS - WIN_HALF, SEGS)) + list(range(0, WIN_HALF + 1)):  # bottom, left->right
        loop.append(add(R * signed_angle(j), y_of[-1], rings[i_bot][j % SEGS]))
    for i in range(i_bot - 1, i_top, -1):  # right side, up
        loop.append(add(R * signed_angle(WIN_HALF), y_of[i - i_top], rings[i][WIN_HALF]))
    for j in list(range(WIN_HALF, -1, -1)) + list(range(SEGS - 1, SEGS - WIN_HALF - 1, -1)):  # top
        loop.append(add(R * signed_angle(j), y_of[0], rings[i_top][j % SEGS]))
    for i in range(i_top + 1, i_bot):  # left side, down
        loop.append(add(R * signed_angle(SEGS - WIN_HALF), y_of[i - i_top], rings[i][SEGS - WIN_HALF]))

    outline = []
    for s, y, _ns, _ny in pill_outline(STRIP_HW):
        outline.append(add(s, y, barrel_vert(g, s, y)))

    margin = 0.2 * MM
    for y in y_of[1:-1]:
        for j in range(-(WIN_HALF - 1), WIN_HALF):
            s = R * 2.0 * math.pi * j / SEGS
            if pill_distance(s, y) < STRIP_HW + margin:
                continue
            add(s, y, barrel_vert(g, s, y))

    for tri in cdt(pts, loop, [outline]):
        cs = sum(pts[i][0] for i in tri) / 3.0
        cy = sum(pts[i][1] for i in tri) / 3.0
        if pill_distance(cs, cy) < STRIP_HW:
            continue  # inside the hole: that is the strip's pocket
        g.poly([ids[i] for i in tri])


def build_strip():
    """The slide strip: a chamfered wall from the barrel surface down to a recessed floor."""
    g = Geo()
    outer = pill_outline(STRIP_HW)
    inner = pill_outline(STRIP_HW - STRIP_C)
    top_ids, bot_ids = [], []
    for (s, y, ns, ny), (s2, y2, _a, _b) in zip(outer, inner):
        d_rim, _ds = grip_depth(y)  # the rim follows the barrel, so a groove under it shortens the wall
        h = STRIP_D - d_rim
        a = s / R
        e_s = Vector((math.cos(a), 0.0, -math.sin(a)))
        n_out = e_s * ns + Vector((0.0, ny, 0.0))
        wall_n = (-n_out * h + radial(s) * STRIP_C).normalized()
        top_ids.append(g.vert(on_barrel(s, y, R - d_rim), wall_n))
        a2 = s2 / R
        e_s2 = Vector((math.cos(a2), 0.0, -math.sin(a2)))
        n_out2 = e_s2 * ns + Vector((0.0, ny, 0.0))
        bot_ids.append(g.vert(on_barrel(s2, y2, R - STRIP_D),
                              (-n_out2 * h + radial(s2) * STRIP_C).normalized()))
    n = len(top_ids)
    for i in range(n):
        k = (i + 1) % n
        g.poly((top_ids[i], top_ids[k], bot_ids[k], bot_ids[i]))

    pts, ids, loop = [], [], []
    for s, y, _ns, _ny in inner:
        pts.append((s, y))
        ids.append(g.vert(on_barrel(s, y, R - STRIP_D), radial(s)))
        loop.append(len(pts) - 1)
    hw_in = STRIP_HW - STRIP_C
    y = PILL_Y0 - hw_in
    while y <= PILL_Y1 + hw_in + 1e-9:
        for j in range(-4, 5):
            s = R * math.radians(5.0 * j)
            if pill_distance(s, y) < hw_in - 0.12 * MM:
                pts.append((s, y))
                ids.append(g.vert(on_barrel(s, y, R - STRIP_D), radial(s)))
        y += 1.0 * MM
    for tri in cdt(pts, loop, []):
        g.poly([ids[i] for i in tri])
    return g


def end_cap_profiles():
    rc, rho = 4.0 * K, 12.0 * K
    smooth = [(0.0, Y_TOP, 0.0, 1.0), (rc * 0.5, Y_TOP, 0.0, 1.0), (rc, Y_TOP, 0.0, 1.0)]
    n = 12
    for i in range(1, n + 1):
        a = 0.5 * math.pi * (1.0 - i / n)
        smooth.append((rc + rho * math.cos(a), Y_CAP_ARC + rho * math.sin(a), math.cos(a), math.sin(a)))
    smooth.append((R, Y_SEAM + SEAM_W, 1.0, 0.0))
    return [smooth, seg_line((R, Y_SEAM + SEAM_W), (R - SEAM_D, Y_SEAM))]


def nib_profile():
    pts = []
    n = 10
    for i in range(n + 1):
        t = 0.5 * i / n
        r = 3.0 * K * (1.0 - 2.0 * t)
        y = kit_y(568.0 + 30.0 * t - 30.0 * t * t)
        dr, dy = -6.0 * K, -(30.0 - 60.0 * t) * K
        nr, ny = -dy, dr
        ln = math.hypot(nr, ny)
        pts.append((max(r, 0.0), y, nr / ln, ny / ln))
    return pts


def tube(g, pts, tans, n1s, n2s, rt, around=12, closed=False, cap_rings=4):
    """A round tube along a path, with hemispherical caps when open (like a round line cap)."""
    def ring_at(c, a, b, rad, t=None, beta=0.0):
        ids = []
        for k in range(around):
            psi = 2.0 * math.pi * k / around
            d = a * math.cos(psi) + b * math.sin(psi)
            nrm = d if t is None else d * math.cos(beta) + t * math.sin(beta)
            ids.append(g.vert(c + d * rad + (t * (rt * math.sin(beta)) if t is not None else Vector()), nrm))
        return ids

    def connect(r0, r1):
        for k in range(around):
            k1 = (k + 1) % around
            g.poly((r0[k], r0[k1], r1[k1], r1[k]))

    rings = [ring_at(c, a, b, rt) for c, a, b in zip(pts, n1s, n2s)]
    for r0, r1 in zip(rings, rings[1:]):
        connect(r0, r1)
    if closed:
        connect(rings[-1], rings[0])
        return
    for end, sgn in ((0, -1.0), (-1, 1.0)):
        c, t, a, b = pts[end], tans[end] * sgn, n1s[end], n2s[end]
        prev = rings[end]
        for m in range(1, cap_rings):
            beta = 0.5 * math.pi * m / cap_rings
            ring = ring_at(c, a, b, rt * math.cos(beta), t, beta)
            connect(prev, ring)
            prev = ring
        tip = g.vert(c + t * rt, t)
        for k in range(around):
            g.poly((prev[k], prev[(k + 1) % around], tip))


def sphere(g, radius, n_lon=24, n_lat=12):
    top = g.vert((0.0, radius, 0.0), (0.0, 1.0, 0.0))
    rings = []
    for i in range(1, n_lat):
        lat = 0.5 * math.pi - math.pi * i / n_lat
        ring = []
        for j in range(n_lon):
            lon = 2.0 * math.pi * j / n_lon
            d = Vector((math.cos(lat) * math.sin(lon), math.sin(lat), math.cos(lat) * math.cos(lon)))
            ring.append(g.vert(d * radius, d))
        rings.append(ring)
    bot = g.vert((0.0, -radius, 0.0), (0.0, -1.0, 0.0))
    for j in range(n_lon):
        g.poly((top, rings[0][j], rings[0][(j + 1) % n_lon]))
    for r0, r1 in zip(rings, rings[1:]):
        for j in range(n_lon):
            j1 = (j + 1) % n_lon
            g.poly((r0[j], r0[j1], r1[j1], r1[j]))
    for j in range(n_lon):
        g.poly((rings[-1][j], bot, rings[-1][(j + 1) % n_lon]))


# ------------------------------------------------------------------------------------------------
# Blender objects
# ------------------------------------------------------------------------------------------------
def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.fps, sc.render.fps_base = FPS, 1.0
    sc.frame_start, sc.frame_current = 0, 0


def scene_fps():
    sc = bpy.context.scene
    return sc.render.fps / sc.render.fps_base


def material(name):
    base, metallic, rough, emissive = MATERIALS[name]
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    bsdf = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Base Color"].default_value = (*srgb_to_linear(base), 1.0)
    bsdf.inputs["Metallic"].default_value = metallic
    bsdf.inputs["Roughness"].default_value = rough
    if emissive:
        bsdf.inputs["Emission Color"].default_value = (*srgb_to_linear(emissive), 1.0)
        bsdf.inputs["Emission Strength"].default_value = 1.0
    else:
        bsdf.inputs["Emission Strength"].default_value = 0.0
    m.diffuse_color = (*srgb_to_linear(base), 1.0)
    return m


def make_mesh(name, g, mat, lin=None):
    """Geo (pen space) -> Blender mesh with exact custom normals. `lin` (Blender 3x3) is the
    linear part of the parent chain; it is baked out so the object can keep identity rotation."""
    g.compact()
    inv = lin.inverted() if lin is not None else None
    tr = lin.transposed() if lin is not None else None
    pos, nrm = [], []
    for p, n in zip(g.p, g.n):
        pb, nb = bl(p), bl(n)
        if inv is not None:
            pb, nb = inv @ pb, (tr @ nb).normalized()
        pos.append(tuple(pb))
        nrm.append(tuple(nb))
    faces = g.f if lin is None or lin.determinant() > 0 else [list(reversed(f)) for f in g.f]
    me = bpy.data.meshes.new(name)
    me.from_pydata(pos, [], faces)
    me.shade_smooth()
    me.normals_split_custom_set_from_vertices(nrm)
    me.materials.append(mat)
    me.update()
    if me.name != name:
        raise BuildError(f"mesh name clash: wanted {name}, got {me.name}")
    return me


def make_object(name, data, parent=None, location=(0.0, 0.0, 0.0)):
    ob = bpy.data.objects.new(name, data)
    bpy.context.scene.collection.objects.link(ob)
    if ob.name != name:
        raise BuildError(f"object name clash: wanted {name}, got {ob.name}")
    if parent is not None:
        ob.parent = parent
        ob.matrix_parent_inverse = Matrix.Identity(4)
    ob.location = location
    return ob


def build_placeholder():
    """The math pen: named parts, materials and the six markers, under one root."""
    root = make_object("pen", None)
    root.empty_display_type, root.empty_display_size = "PLAIN_AXES", 0.02
    root.rotation_mode = "QUATERNION"
    parts = []

    g = Geo()
    for prof in end_cap_profiles():
        lathe(g, prof)
    parts.append(("end_cap", g, "satin_white"))

    g = Geo()
    lathe(g, seg_line((R - SEAM_D, Y_SEAM), (R, Y_SEAM - SEAM_W)))
    lathe(g, [(R, Y_SEAM - SEAM_W, 1.0, 0.0), (R, Y_BAND + BAND_W / 2, 1.0, 0.0)])
    levels = window_levels()
    barrel = ([(R, Y_BAND - BAND_W / 2, 1.0, 0.0)] + [barrel_point(y) for y in levels]
              + [(R, Y_COLLAR + GROOVE_HW, 1.0, 0.0)])
    i_top, i_bot = 1, len(levels)
    win = set(range(SEGS - WIN_HALF, SEGS)) | set(range(0, WIN_HALF))
    rings = lathe(g, barrel, skip=lambda i, j: i_top <= i < i_bot and j in win)
    build_barrel_window(g, rings, i_top, i_bot)
    lathe(g, seg_line((R, Y_COLLAR + GROOVE_HW), (R - GROOVE_D, Y_COLLAR)))
    lathe(g, seg_line((R - GROOVE_D, Y_COLLAR), (R, Y_COLLAR - GROOVE_HW)))
    lathe(g, [(R, Y_COLLAR - GROOVE_HW, 1.0, 0.0), (R, Y_BODY_END, 1.0, 0.0)])
    parts.append(("body", g, "satin_white"))

    g = Geo()
    lathe(g, seg_line((R, Y_BAND + BAND_W / 2), (R - BAND_D, Y_BAND + BAND_W / 2)))
    lathe(g, seg_line((R - BAND_D, Y_BAND + BAND_W / 2), (R - BAND_D, Y_BAND - BAND_W / 2)))
    lathe(g, seg_line((R - BAND_D, Y_BAND - BAND_W / 2), (R, Y_BAND - BAND_W / 2)))
    parts.append(("logo_band", g, "band_engraved"))

    parts.append(("slide_strip", build_strip(), "strip_gloss"))

    g = Geo()
    lathe(g, seg_line((R, Y_BODY_END), (3.0 * K, Y_CONE_END)))
    parts.append(("tip_cone", g, "tip_light"))

    g = Geo()
    lathe(g, nib_profile())
    parts.append(("nib", g, "matte_dark_grey"))

    for name, g, mat in parts:
        make_object(name, make_mesh(name, g, material(mat)), parent=root)
        log(f"part {name}: {len(g.p)} vertices, {g.triangles()} triangles")

    for name, p in (("marker_squeeze", (0.0, Y_SQUEEZE, MARKER_Z)),
                    ("marker_slide_top", (0.0, Y_SLIDE_TOP, MARKER_Z)),
                    ("marker_slide_bottom", (0.0, Y_SLIDE_BOTTOM, MARKER_Z)),
                    ("marker_double_tap", (0.0, Y_DOUBLE_TAP, MARKER_Z)),
                    ("marker_nib", (0.0, Y_APEX, 0.0)),
                    ("marker_top", (0.0, Y_TOP, 0.0))):
        e = make_object(name, None, parent=root, location=bl(p))
        e.empty_display_type, e.empty_display_size = "SPHERE", 0.0015


# ------------------------------------------------------------------------------------------------
# Source models
# ------------------------------------------------------------------------------------------------
def load_source(src):
    if not os.path.isfile(src):
        raise BuildError(f"source not found: {src}")
    ext = os.path.splitext(src)[1].lower()
    if ext == ".blend":
        bpy.ops.wm.open_mainfile(filepath=src)
        bpy.context.scene.frame_set(0)
        log(f"opened {src} ({scene_fps():g} fps, kept from the file)")
        return
    reset_scene()
    if ext in (".glb", ".gltf"):
        res = bpy.ops.import_scene.gltf(filepath=src)
    elif ext == ".fbx":
        res = bpy.ops.import_scene.fbx(filepath=src, use_anim=True, anim_offset=0.0)
    else:
        raise BuildError(f"unsupported source type {ext!r}; use .glb, .gltf, .fbx or .blend")
    if "FINISHED" not in res:
        raise BuildError(f"import failed: {src}")
    log(f"imported {src}")


def remove_old_effects():
    """Effects from an earlier build (for example a placeholder fed back in) are rebuilt."""
    for ob in [o for o in bpy.data.objects if o.name.startswith(FX_PREFIX)]:
        bpy.data.objects.remove(ob, do_unlink=True)
    for me in [m for m in bpy.data.meshes if m.users == 0 and m.name.startswith(FX_PREFIX)]:
        bpy.data.meshes.remove(me)
    for act in [a for a in bpy.data.actions if re.search(r"__fx(\.\d+)?$", a.name)]:
        bpy.data.actions.remove(act)


def find_root():
    parts = [o for o in bpy.context.scene.objects
             if o.parent is None and o.type not in ("CAMERA", "LIGHT")]
    if len(parts) == 1:
        return parts[0]
    name = "pen" if "pen" not in bpy.data.objects else "pen_root"
    root = make_object(name, None)
    root.rotation_mode = "QUATERNION"
    for ob in sorted(parts, key=lambda o: o.name):
        ob.parent = root
        ob.matrix_parent_inverse = Matrix.Identity(4)
    log(f"wrapped {len(parts)} top-level objects in a new root '{name}'")
    return root


def descendants(ob):
    out = []
    for ch in sorted(ob.children, key=lambda o: o.name):
        out.append(ch)
        out += descendants(ch)
    return out


def check_markers(root):
    scene_names = {o.name for o in bpy.context.scene.objects}
    missing = [m for m in MARKERS if m not in scene_names]
    if missing:
        near = sorted(n for n in scene_names if "marker" in n.lower())
        raise BuildError("the model is missing marker empties: " + ", ".join(missing)
                         + f" (marker-like objects present: {', '.join(near) or 'none'})", code=2)
    under = {o.name for o in descendants(root)}
    stray = [m for m in MARKERS if m not in under]
    if stray:
        raise BuildError("markers must sit under the pen's root '%s': %s" % (root.name, ", ".join(stray)), 2)


class Cfg:
    pass


def measure(root):
    """Barrel radius and marker frames, in pen space (root-local, glTF axes)."""
    bpy.context.view_layer.update()
    rinv = root.matrix_world.inverted()
    dg = bpy.context.evaluated_depsgraph_get()
    pts = []
    for ob in [root] + descendants(root):
        if ob.type != "MESH" or ob.name.startswith(FX_PREFIX):
            continue
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        m = rinv @ ob.matrix_world
        pts += [pen(m @ v.co) for v in me.vertices]
        ev.to_mesh_clear()
    if not pts:
        raise BuildError("the model has no mesh under its root")
    y0, y1 = min(p.y for p in pts), max(p.y for p in pts)
    span = y1 - y0
    barrel = [math.hypot(p.x, p.z) for p in pts if y0 + 0.15 * span <= p.y <= y1 - 0.05 * span]
    cfg = Cfg()
    cfg.R = max(barrel) if barrel else 5.0 * MM
    cfg.length = span
    cfg.markers = {}
    for name in MARKERS:
        mat = rinv @ bpy.data.objects[name].matrix_world
        cfg.markers[name] = (pen(mat.translation), mat)
    nib = cfg.markers["marker_nib"][0]
    cfg.nib = nib.copy()

    def axis_point(y):
        return Vector((nib.x, y, nib.z))

    def facing(p):
        d = Vector((p.x - nib.x, 0.0, p.z - nib.z))
        return d.normalized() if d.length > 0.3 * cfg.R else Vector((0.0, 0.0, 1.0))

    cfg.axis_point, cfg.facing = axis_point, facing
    log(f"pen length {span / MM:.2f} mm, barrel radius {cfg.R / MM:.3f} mm")
    if not 0.05 <= span <= 0.5:
        log(f"WARNING: the pen is {span:.4g} model units long; glTF units are metres (about 0.165). "
            "Effects scale with the barrel, but the clip motion is in absolute millimetres.")
    return cfg


# ------------------------------------------------------------------------------------------------
# Effects
# ------------------------------------------------------------------------------------------------
def build_effects(root, cfg):
    mat = material("fx_lavender")
    Rb = cfg.R
    rt = FX_TUBE * Rb
    p_sq = cfg.markers["marker_squeeze"][0]
    d_sq = cfg.facing(p_sq)
    side = Vector((0.0, 1.0, 0.0)).cross(d_sq)  # in the picture plane for a camera-facing marker
    cfg.side = side
    y_hat = Vector((0.0, 1.0, 0.0))
    specs = {}

    rho = ARC_RHO * Rb
    for name, sign in ((FX_ARC_L, -1.0), (FX_ARC_R, 1.0)):
        g = Geo()
        pts, tans, n1s, n2s = [], [], [], []
        n = 24
        for i in range(n + 1):
            phi = -ARC_HALF_ANGLE + 2.0 * ARC_HALF_ANGLE * i / n
            pts.append(side * (-sign * rho * (1.0 - math.cos(phi))) + y_hat * (rho * math.sin(phi)))
            tans.append((side * (-sign * math.sin(phi)) + y_hat * math.cos(phi)).normalized())
            n1s.append((side * (sign * math.cos(phi)) + y_hat * math.sin(phi)).normalized())
            n2s.append(d_sq.copy())
        tube(g, pts, tans, n1s, n2s, rt)
        origin = cfg.axis_point(p_sq.y) + side * (sign * ARC_APEX * Rb)
        specs[name] = (g, origin)

    p_dt = cfg.markers["marker_double_tap"][0]
    d_dt = cfg.facing(p_dt)
    side_dt = y_hat.cross(d_dt)
    g = Geo()
    rr = RING_RADIUS * Rb
    pts, tans, n1s, n2s = [], [], [], []
    for i in range(72):
        phi = 2.0 * math.pi * i / 72
        d = side_dt * math.cos(phi) + y_hat * math.sin(phi)
        pts.append(d * rr)
        tans.append(side_dt * -math.sin(phi) + y_hat * math.cos(phi))
        n1s.append(d)
        n2s.append(d_dt.copy())
    tube(g, pts, tans, n1s, n2s, rt, closed=True)
    specs[FX_RING] = (g, cfg.axis_point(p_dt.y) + d_dt * (Rb * (1.0 + RING_LIFT)))

    p_sb = cfg.markers["marker_slide_bottom"][0]
    p_st = cfg.markers["marker_slide_top"][0]
    lift = Rb * (1.0 + DOT_LIFT)
    dot_bottom = cfg.axis_point(p_sb.y) + cfg.facing(p_sb) * lift
    dot_top = cfg.axis_point(p_st.y) + cfg.facing(p_st) * lift
    cfg.dot_travel = dot_top - dot_bottom
    g = Geo()
    sphere(g, DOT_RADIUS * Rb)
    specs[FX_DOT] = (g, dot_bottom)

    fx = {}
    for name in FX_ORDER:
        g, origin = specs[name]
        parent = bpy.data.objects[FX_PARENT[name]]
        p_m = cfg.markers[FX_PARENT[name]][1]  # parent marker, root-local (Blender axes)
        lin = p_m.to_3x3()
        ob = make_object(name, make_mesh(name, g, mat, lin), parent=parent)
        p_inv = p_m.inverted()
        ob.location = p_inv @ bl(origin)
        ob.rotation_mode = "QUATERNION"
        ob.scale = (0.0, 0.0, 0.0)  # hidden until its clip scales it up
        fx[name] = (ob, origin, p_inv)
        log(f"effect {name}: {g.triangles()} triangles, under {parent.name}")
    return fx


# ------------------------------------------------------------------------------------------------
# Clips
# ------------------------------------------------------------------------------------------------
def smoothstep(a, b, x):
    t = 0.0 if x <= a else 1.0 if x >= b else (x - a) / (b - a)
    return t * t * (3.0 - 2.0 * t)


def bump(u, a, b):
    """The motion kit's pulse(): half a sine wave between a and b."""
    return math.sin(math.pi * (u - a) / (b - a)) if a <= u <= b else 0.0


def ripple(u, a, b):
    if not a <= u <= b:
        return 0.0
    v = (u - a) / (b - a)
    return (0.45 + 0.55 * v) * smoothstep(0.0, 0.12, v) * (1.0 - smoothstep(0.78, 1.0, v))


def root_delta(clip, u, cfg):
    """The pen's own motion for a clip, as a pen-space matrix (identity = rest)."""
    tw = 2.0 * math.pi
    if clip == "idle":
        return (Matrix.Translation((0.0, 0.5 * IDLE_FLOAT * (1.0 - math.cos(tw * u)), 0.0))
                @ Matrix.Rotation(math.radians(IDLE_SWAY_DEG * math.sin(tw * u)), 4, "Y"))
    if clip == "double_tap":
        a = bump(u, 0.10, 0.31) + bump(u, 0.42, 0.63)
        return (Matrix.Translation((0.0, 0.0, -TAP_RECOIL * a))
                @ Matrix.Rotation(math.radians(-TAP_TILT_DEG * a), 4, "Z"))
    if clip == "write":
        w = smoothstep(0.0, 0.22, u) * (1.0 - smoothstep(0.80, 1.0, u))
        v = min(1.0, max(0.0, (u - 0.18) / 0.64))
        d = Vector((WRITE_LOOP[0] * math.sin(tw * v), WRITE_LOOP[1] * (math.cos(tw * v) - 1.0), 0.0))
        tilt = WRITE_TILT_DEG * w - WRITE_WOBBLE_DEG * math.sin(tw * v) * w
        return (Matrix.Translation(cfg.nib + d) @ Matrix.Rotation(math.radians(tilt), 4, "Z")
                @ Matrix.Translation(-cfg.nib))
    return Matrix.Identity(4)


def fx_state(clip, u, cfg):
    """Each effect's pen-space offset from its rest origin and its uniform scale (0 = hidden)."""
    zero = Vector((0.0, 0.0, 0.0))
    st = {name: (zero, 0.0) for name in FX_ORDER}
    if clip == "squeeze":
        p = bump(u, 0.12, 0.64)
        inward = ARC_TRAVEL * cfg.R * p
        st[FX_ARC_L] = (cfg.side * inward, math.sqrt(p))
        st[FX_ARC_R] = (cfg.side * -inward, math.sqrt(p))
    elif clip in ("slide_up", "slide_down"):
        f = smoothstep(0.14, 0.80, u)
        if clip == "slide_down":
            f = 1.0 - f
        st[FX_DOT] = (cfg.dot_travel * f, smoothstep(0.02, 0.14, u) * (1.0 - smoothstep(0.80, 0.94, u)))
    elif clip == "double_tap":
        st[FX_RING] = (zero, ripple(u, 0.10, 0.31) + ripple(u, 0.42, 0.63))
    return st


LINEAR = None


def keyed_action(name):
    act = bpy.data.actions.new(name)
    act.layers.new("layer").strips.new(type="KEYFRAME")
    return act


def bind(act, ob, clip, channels, f0, f1):
    """Write channels {(path, index): [values per frame]} into a slot for `ob`, and put the
    action on a muted NLA track named after the clip (as the glTF importer stashes clips):
    the exporter collects it, the scene's rest pose stays static."""
    global LINEAR
    if LINEAR is None:
        LINEAR = bpy.types.Keyframe.bl_rna.properties["interpolation"].enum_items["LINEAR"].value
    slot = act.slots.new(id_type="OBJECT", name=ob.name)
    cb = act.layers[0].strips[0].channelbags.new(slot)
    frames = list(range(f0, f1 + 1))
    for (path, index), values in channels.items():
        fc = cb.fcurves.new(path, index=index)
        fc.keyframe_points.add(len(frames))
        fc.keyframe_points.foreach_set("co", [c for f, v in zip(frames, values) for c in (float(f), v)])
        fc.keyframe_points.foreach_set("interpolation", [LINEAR] * len(frames))
        fc.update()
    act.use_frame_range = True
    act.frame_start, act.frame_end = f0, f1
    ad = ob.animation_data or ob.animation_data_create()
    tr = ad.nla_tracks.new()
    tr.name = clip
    st = tr.strips.new(act.name, f0, act)
    st.action_slot = slot
    tr.mute = True
    tr.lock = True
    if tr.name != clip:
        raise BuildError(f"{ob.name} already has a track named {clip}")


def root_channels(root, clip, frames, u_of, cfg):
    m0 = root.matrix_basis.copy()
    locs, quats = [], []
    for f in frames:
        m = m0 @ C_TO_BL @ root_delta(clip, u_of(f), cfg) @ C_TO_PEN
        loc, q, _s = m.decompose()
        if quats and quats[-1].dot(q) < 0.0:
            q.negate()
        locs.append(loc)
        quats.append(q)
    ch = {("location", i): [v[i] for v in locs] for i in range(3)}
    mode = root.rotation_mode
    if mode == "QUATERNION":
        ch.update({("rotation_quaternion", i): [q[i] for q in quats] for i in range(4)})
    elif mode == "AXIS_ANGLE":
        aa = [(q.to_axis_angle()[1], *q.to_axis_angle()[0]) for q in quats]
        ch.update({("rotation_axis_angle", i): [a[i] for a in aa] for i in range(4)})
    else:
        eul, prev = [], None
        for q in quats:
            e = q.to_euler(mode, prev) if prev is not None else q.to_euler(mode)
            eul.append(e)
            prev = e
        ch.update({("rotation_euler", i): [e[i] for e in eul] for i in range(3)})
    return ch


def fx_channels(fx, clip, frames, u_of, cfg):
    per = {name: {} for name in FX_ORDER}
    states = [fx_state(clip, u_of(f), cfg) for f in frames]
    for name in FX_ORDER:
        _ob, origin, p_inv = fx[name]
        locs = [p_inv @ bl(origin + st[name][0]) for st in states]
        scales = [st[name][1] for st in states]
        per[name] = {("location", i): [v[i] for v in locs] for i in range(3)}
        per[name].update({("scale", i): list(scales) for i in range(3)})
    return per


def clip_name(name):
    """'squeeze', 'squeeze.001' or the FBX 'object|squeeze' -> 'squeeze'; anything else -> None."""
    base = re.sub(r"\.\d{3}$", "", name.split("|")[-1])
    return base if base in CLIPS else None


def adopt_owner_clips():
    """Keep the source's clips that use the six names, on tracks named after the clip.
    Returns {clip: (first frame, last frame)}."""
    kept, dropped = {}, []
    for ob in sorted(bpy.context.scene.objects, key=lambda o: o.name):
        ad = ob.animation_data
        if ad is None:
            continue
        if ad.action is not None:
            c = clip_name(ad.action.name)
            has = any(clip_name(t.name) == c or any(s.action == ad.action for s in t.strips)
                      for t in ad.nla_tracks)
            if c and not has and ad.action_slot is not None:
                tr = ad.nla_tracks.new()
                tr.name = c
                st = tr.strips.new(ad.action.name, int(ad.action.frame_range[0]), ad.action)
                st.action_slot = ad.action_slot
            elif not c:
                dropped.append(f"{ob.name}: active action {ad.action.name} (not one of the six clips)")
            ad.action = None
        seen = set()
        for tr in list(ad.nla_tracks):
            strips = [s for s in tr.strips if s.action is not None and not s.mute]
            c = clip_name(tr.name) or (clip_name(strips[0].action.name) if len(strips) == 1 else None)
            why = ("not one of the six clips" if c is None else
                   f"{len(strips)} unmuted strips, expected 1" if len(strips) != 1 else
                   f"a second track for {c}" if c in seen else
                   "its strip has no action slot" if strips[0].action_slot is None else None)
            if why:
                dropped.append(f"{ob.name}: track {tr.name} ({why})")
                ad.nla_tracks.remove(tr)
                continue
            seen.add(c)
            tr.name = c
            tr.mute, tr.lock = True, True
            a0, a1 = strips[0].action.frame_range
            lo, hi = kept.get(c, (a0, a1))
            kept[c] = (min(lo, a0), max(hi, a1))
    bound = {s.action for o in bpy.context.scene.objects if o.animation_data
             for t in o.animation_data.nla_tracks for s in t.strips}
    for act in sorted(bpy.data.actions, key=lambda a: a.name):
        c = clip_name(act.name)
        if c is None or act in bound:
            continue
        for slot in act.slots:  # an unassigned clip: FBX names it "object|take", else bind by slot name
            owner = act.name.rsplit("|", 1)[0] if "|" in act.name else slot.name_display
            ob = bpy.context.scene.objects.get(owner)
            if slot.target_id_type != "OBJECT" or ob is None or ob.name.startswith(FX_PREFIX):
                continue
            ad = ob.animation_data or ob.animation_data_create()
            if any(t.name == c for t in ad.nla_tracks):
                continue
            tr = ad.nla_tracks.new()
            tr.name = c
            st = tr.strips.new(act.name, int(act.frame_range[0]), act)
            st.action_slot = slot
            tr.mute, tr.lock = True, True
            a0, a1 = act.frame_range
            lo, hi = kept.get(c, (a0, a1))
            kept[c] = (min(lo, a0), max(hi, a1))
    for d in dropped:
        log("dropped " + d)
    return {c: kept[c] for c in CLIPS if c in kept}


def make_clips(root, cfg, fx, kept):
    fps = scene_fps()
    for clip in CLIPS:
        if clip in kept:
            a0, a1 = kept[clip]
            f0, f1 = int(math.floor(a0)), int(math.ceil(a1))
            if f1 <= f0:
                f1 = f0 + max(1, round(CLIP_SECONDS[clip] * fps))
            act = keyed_action(clip + "__fx")
            how = "kept the source's clip, added the effects"
        else:
            f0, f1 = 0, max(1, round(CLIP_SECONDS[clip] * fps))
            act = keyed_action(clip)
            how = "generated"
        frames = list(range(f0, f1 + 1))
        span = float(f1 - f0)

        def u_of(f, f0=f0, span=span):
            return min(1.0, max(0.0, (f - f0) / span))

        if clip not in kept:
            bind(act, root, clip, root_channels(root, clip, frames, u_of, cfg), f0, f1)
        for name, ch in fx_channels(fx, clip, frames, u_of, cfg).items():
            bind(act, fx[name][0], clip, ch, f0, f1)
        log(f"clip {clip}: frames {f0}..{f1} at {fps:g} fps ({(f1 - f0) / fps:.3f} s), {how}")


# ------------------------------------------------------------------------------------------------
# Export and self-check
# ------------------------------------------------------------------------------------------------
def export(tmp):
    for ob in bpy.context.scene.objects:  # nothing is clipped by viewport visibility
        ob.hide_viewport = False
        ob.hide_set(False)
    bpy.context.scene.frame_set(0)
    res = bpy.ops.export_scene.gltf(
        filepath=tmp, check_existing=False, export_format="GLB",
        use_selection=False, use_visible=False, use_renderable=False, use_active_collection=False,
        use_active_scene=True, export_yup=True, export_apply=True,
        export_texcoords=True, export_normals=True, export_tangents=False,
        export_materials="EXPORT", export_cameras=False, export_lights=False, export_extras=False,
        export_draco_mesh_compression_enable=False, export_use_gltfpack=False,
        export_animations=True, export_animation_mode="ACTIONS", export_merge_animation="NLA_TRACK",
        export_force_sampling=True, export_frame_step=1, export_frame_range=False,
        export_optimize_animation_size=True, export_optimize_animation_keep_anim_object=False,
        export_anim_slide_to_zero=True, export_negative_frame="SLIDE", export_current_frame=False,
        export_bake_animation=False, export_copyright="")
    if "FINISHED" not in res or not os.path.isfile(tmp):
        raise BuildError("glTF export failed")


def publish(tmp, outs):
    """Move the checked file into place; the other folders get byte-identical copies."""
    first = os.path.join(outs[0], OUT_NAME)
    os.replace(tmp, first)
    for d in outs[1:]:
        part = os.path.join(d, ".partial_" + OUT_NAME)
        shutil.copyfile(first, part)
        os.replace(part, os.path.join(d, OUT_NAME))
    data = open(first, "rb").read()
    log(f"wrote {len(data)} bytes, sha256 {hashlib.sha256(data).hexdigest()}")
    for d in outs:
        log(f"  -> {os.path.join(d, OUT_NAME)}")


def node_matrix(n):
    if "matrix" in n:
        m = n["matrix"]
        return Matrix([[m[c * 4 + r] for c in range(4)] for r in range(4)])
    t = n.get("translation", [0.0, 0.0, 0.0])
    x, y, z, w = n.get("rotation", [0.0, 0.0, 0.0, 1.0])
    s = n.get("scale", [1.0, 1.0, 1.0])
    return (Matrix.Translation(t) @ Quaternion((w, x, y, z)).to_matrix().to_4x4()
            @ Matrix.Diagonal((s[0], s[1], s[2], 1.0)))


def summarize(path):
    """Read the exported file back, print what it holds and refuse it if the contract is broken."""
    data = open(path, "rb").read()
    magic, _ver, total = struct.unpack_from("<4sII", data, 0)
    clen, ctype = struct.unpack_from("<I4s", data, 12)
    if magic != b"glTF" or ctype != b"JSON" or total != len(data):
        raise BuildError("the exported file is not a well-formed GLB")
    doc = json.loads(data[20:20 + clen].decode("utf-8"))
    nodes = doc.get("nodes", [])
    acc = doc.get("accessors", [])
    tris = 0
    for mesh in doc.get("meshes", []):
        for prim in mesh["primitives"]:
            cnt = acc[prim["indices"]]["count"] if "indices" in prim else acc[prim["attributes"]["POSITION"]]["count"]
            tris += cnt // 3 if prim.get("mode", 4) == 4 else 0
    world = {}

    def walk(i, parent):
        m = parent @ node_matrix(nodes[i])
        world[nodes[i].get("name", f"#{i}")] = m
        for c in nodes[i].get("children", []):
            walk(c, m)

    for scene in doc.get("scenes", []):
        for i in scene.get("nodes", []):
            walk(i, Matrix.Identity(4))
    anims = {}
    for a in doc.get("animations", []):
        anims[a["name"]] = (max(acc[s["input"]]["max"][0] for s in a["samplers"]), len(a["channels"]))
    log(f"nodes {len(nodes)}, meshes {len(doc.get('meshes', []))}, materials "
        + ", ".join(m["name"] for m in doc.get("materials", [])) + f", triangles {tris}")
    for name in MARKERS:
        if name not in world:
            raise BuildError(f"export lost {name}")
        p = world[name].translation
        log(f"{name:20s} position (m) {p.x:+.5f} {p.y:+.5f} {p.z:+.5f}")
    found = sum(1 for n in nodes if n.get("name") in MARKERS)
    if found != len(MARKERS):
        raise BuildError(f"expected {len(MARKERS)} marker nodes in the export, found {found}")
    log(f"markers {found} of {len(MARKERS)}")
    for name in CLIPS:
        if name not in anims:
            raise BuildError(f"export lost clip {name}")
        log(f"clip {name:10s} {anims[name][0]:.3f} s, {anims[name][1]} channels")
    extra = sorted(set(anims) - set(CLIPS))
    if extra:
        raise BuildError("unexpected extra animations: " + ", ".join(extra))
    if tris > MAX_TRIANGLES:
        raise BuildError(f"{tris} triangles is over the {MAX_TRIANGLES} budget")
    if doc.get("extensionsRequired"):
        raise BuildError("extensionsRequired present: " + ", ".join(doc["extensionsRequired"]))


def parse_args():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ap = argparse.ArgumentParser(prog="build_pen.py", description=__doc__.split("\n\n")[0])
    ap.add_argument("--source", metavar="FILE", help="the owner's model: .glb, .gltf, .fbx or .blend")
    ap.add_argument("--out", metavar="DIR", nargs="+", action="extend",
                    help="output directories (default: docs/redesign/assets/pen and "
                         "padstudio/app/src/main/assets/pen)")
    ap.add_argument("--save-blend", metavar="FILE", help="also save the built scene as a .blend")
    return ap.parse_args(argv)


def main():
    args = parse_args()
    outs = [os.path.abspath(d) for d in (args.out or DEFAULT_OUTS)]
    if args.source:
        load_source(os.path.abspath(args.source))
        for ob in [o for o in bpy.context.scene.objects if o.type in ("CAMERA", "LIGHT")]:
            log(f"removed {ob.type.lower()} {ob.name} (not part of the pen)")
            bpy.data.objects.remove(ob, do_unlink=True)
    else:
        reset_scene()
        build_placeholder()
    remove_old_effects()
    root = find_root()
    check_markers(root)
    kept = adopt_owner_clips() if args.source else {}
    cfg = measure(root)
    fx = build_effects(root, cfg)
    make_clips(root, cfg, fx, kept)
    if args.save_blend:
        bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(args.save_blend), copy=True)
        log(f"saved {os.path.abspath(args.save_blend)}")
    for d in outs:
        os.makedirs(d, exist_ok=True)
    tmp = os.path.join(outs[0], ".partial_" + OUT_NAME)  # replaces nothing until it passes the checks
    try:
        export(tmp)
        summarize(tmp)
    except BaseException:
        if os.path.exists(tmp):
            os.remove(tmp)
        raise
    publish(tmp, outs)


if __name__ == "__main__":
    try:
        main()
    except BuildError as e:
        print(f"[build_pen] ERROR: {e}", file=sys.stderr)
        sys.stderr.flush()
        sys.exit(e.code)
    except Exception:
        traceback.print_exc()
        sys.stderr.flush()
        sys.exit(1)
