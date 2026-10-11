#!/usr/bin/env python3
"""PadStudio brand artwork, plotted rather than drawn (the approach of WiFiBar's brand kit).

Concept "Nib": the mark is the letter P written with a broad nib. A broad nib is a straight edge
held at a fixed angle phi; sweeping it along the pen's path gives calligraphy's thick and thin
strokes with nothing drawn by hand: where the path runs at angle theta the stroke is
W |sin(theta - phi)| wide. The path is a straight stem and a superellipse bowl,

        stem:  (0, -1) -> (0, 1)
        bowl:  x = a sgn(cos t) |cos t|^(2/n),   y = c + b sgn(sin t) |sin t|^(2/n),   t: 90° -> -90°,

with n = 2.4 and the nib at phi = 35°. The pen presses in over the first 4% of the path (width x
min(u / 0.04, 1)^0.5, u = arc length / total); the bowl ends inside the stem, so it needs no lift-out. The icon body is the
superellipse |x|^5 + |y|^5 = 1 (the bowl's family), 824 px on a 1024 px canvas, a vertical gradient
with an orchid glow behind the bowl (one hue family: the violet blends cleanly into it).

  python3 -I Branding/make_brand.py

Writes Branding/out/ and PadStudio's adaptive-icon layers (res/mipmap-xxxhdpi PNGs plus the vector
background in res/drawable). Deterministic: no randomness, so re-running gives identical files.
"""

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
RES = HERE.parent / "padstudio/app/src/main/res"

# Palette (BRAND.md).
INK = "#120A2E"       # icon gradient (bottom), mark on light, text
VIOLET = "#5A3FD6"    # icon gradient (top)
ORCHID = "#C38BFF"    # glow behind the bowl; sparing accents only (a neighbour of the violet, so blends stay clean)
LILAC = "#CDBEFF"     # foot of the mark's gradient (it lightens to white at the bowl)
WHITE = "#FFFFFF"     # the mark on the icon and on dark
MIST = "#F5F5F7"      # light neutral background
GRAPHITE = "#1C1C1E"  # dark neutral background
GREY = "#6E6E73"
PALETTE = [
    ("Ink Indigo", INK, "Icon gradient (bottom), mark on light, text"),
    ("Studio Violet", VIOLET, "Icon gradient (top)"),
    ("Glow Orchid", ORCHID, "Glow behind the bowl; sparing accents"),
    ("Stroke Lilac", LILAC, "Foot of the mark's gradient"),
    ("White", WHITE, "The mark on the icon and on dark"),
    ("Graphite", GRAPHITE, "Dark neutral background"),
]

CANVAS = 1024
BODY = 824                   # Apple icon grid, as WiFiBar: 100 px margin on a 1024 canvas
SQUIRCLE_N = 5.0

# The letter, in path units (the stem runs from y = -1 to y = 1).
BOWL_C, BOWL_A, BOWL_B, BOWL_N = 0.42, 0.72, 0.58, 2.4
NIB_ANGLE = np.deg2rad(35.0)
NIB_W = 0.38                 # nib edge length, in path units
TAPER = 0.04                 # press in over this share of the path
MARK_H = 0.55                # mark height as a share of the icon body


# --- The mark ----------------------------------------------------------------------------------

def letter_path():
    """Centre line of the P: stem up, then the superellipse bowl clockwise back to the stem."""
    t = np.linspace(np.pi / 2, -np.pi / 2, 600)
    bowl = np.c_[
        BOWL_A * np.sign(np.cos(t)) * np.abs(np.cos(t)) ** (2 / BOWL_N),
        BOWL_C + BOWL_B * np.sign(np.sin(t)) * np.abs(np.sin(t)) ** (2 / BOWL_N),
    ]
    stem = np.c_[np.zeros(520), np.linspace(-1.0, bowl[0, 1], 520)]
    return np.vstack([stem, bowl[1:]])


def nib_offsets(path):
    """Half the nib edge at each point: fixed direction, length scaled by pressure."""
    s = np.r_[0, np.cumsum(np.hypot(*np.diff(path, axis=0).T))]
    u = s / s[-1]
    pressure = np.clip(u / TAPER, 0, 1) ** 0.5
    return np.c_[np.cos(NIB_ANGLE), np.sin(NIB_ANGLE)] * (NIB_W / 2) * pressure[:, None]


PATH = letter_path()
HALF = nib_offsets(PATH)


def geometry(px, height_px, centre):
    """The mark placed in a px-square image: nib edge ends, glow and gradient points, bounding box."""
    pts = np.vstack([PATH - HALF, PATH + HALF])
    lo, hi = pts.min(0), pts.max(0)
    k = height_px / (hi[1] - lo[1])
    mid = (lo + hi) / 2

    def place(p):
        p = np.atleast_2d(p)
        return np.c_[centre[0] + (p[:, 0] - mid[0]) * k, centre[1] - (p[:, 1] - mid[1]) * k]

    return {
        "a": place(PATH - HALF), "b": place(PATH + HALF), "k": k, "px": px,
        "glow": place([BOWL_A * 0.55, BOWL_C])[0],
        "grad0": place([0.0, -1.0])[0], "grad1": place([BOWL_A, BOWL_C + BOWL_B])[0],
        "box": (place(lo)[0], place(hi)[0]),
    }


def mark_mask(geo, ss=4):
    """Union of the nib's swept quads (anti-aliased, supersampled ss x ss)."""
    px = geo["px"]
    big = Image.new("L", (px * ss, px * ss), 0)
    d = ImageDraw.Draw(big)
    a, b = geo["a"] * ss, geo["b"] * ss
    for i in range(len(a) - 1):
        d.polygon([tuple(a[i]), tuple(a[i + 1]), tuple(b[i + 1]), tuple(b[i])], fill=255)
    return big.resize((px, px), Image.LANCZOS)


# --- Raster helpers ----------------------------------------------------------------------------

def rgba(hex_color, a=255):
    h = hex_color.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def superellipse(cx, cy, half, n=SQUIRCLE_N, count=2400):
    t = np.linspace(0, 2 * np.pi, count, endpoint=False)
    c, s = np.cos(t), np.sin(t)
    return np.c_[cx + half * np.sign(c) * np.abs(c) ** (2 / n), cy + half * np.sign(s) * np.abs(s) ** (2 / n)]


def polygon_mask(px, pts, ss=4):
    big = Image.new("L", (px * ss, px * ss), 0)
    ImageDraw.Draw(big).polygon([(x * ss, y * ss) for x, y in pts], fill=255)
    return big.resize((px, px), Image.LANCZOS)


def linear_gradient(size, p0, p1, c0, c1):
    w, h = size if isinstance(size, tuple) else (size, size)
    yy, xx = np.mgrid[0:h, 0:w] + 0.5
    p0, p1 = np.asarray(p0, float), np.asarray(p1, float)
    d = p1 - p0
    t = np.clip(((xx - p0[0]) * d[0] + (yy - p0[1]) * d[1]) / (d @ d), 0, 1)[..., None]
    a, b = np.array(rgba(c0), float), np.array(rgba(c1), float)
    return Image.fromarray((a + (b - a) * t).astype(np.uint8), "RGBA")


def glow(size, centre, colour, sigma, alpha):
    w, h = size if isinstance(size, tuple) else (size, size)
    yy, xx = np.mgrid[0:h, 0:w] + 0.5
    g = np.exp(-((xx - centre[0]) ** 2 + (yy - centre[1]) ** 2) / (2 * sigma ** 2))
    layer = np.zeros((h, w, 4), np.uint8)
    layer[..., :3] = rgba(colour)[:3]
    layer[..., 3] = np.round(255 * alpha * g).astype(np.uint8)
    return Image.fromarray(layer, "RGBA")


def clear(size):
    return Image.new("RGBA", size if isinstance(size, tuple) else (size, size), (0, 0, 0, 0))


def paint(img, geo, colour=None):
    """The mark onto img, in one colour or the lilac-to-white gradient."""
    fill = Image.new("RGBA", img.size, rgba(colour)) if colour else linear_gradient(img.size, geo["grad0"], geo["grad1"], LILAC, WHITE)
    img.alpha_composite(Image.composite(fill, clear(img.size), mark_mask(geo)))


def render_icon(px=CANVAS):
    """App icon: superellipse body, vertical gradient, orchid glow behind the bowl, the mark, a faint rim."""
    k = px / CANVAS
    half = BODY / 2 * k
    geo = geometry(px, MARK_H * BODY * k, (px / 2, px / 2))
    art = linear_gradient(px, (0, px / 2 - half), (0, px / 2 + half), VIOLET, INK)
    art.alpha_composite(glow(px, geo["glow"], ORCHID, 165 * k, 0.30))
    paint(art, geo)
    body = superellipse(px / 2, px / 2, half)
    icon = clear(px)
    icon.paste(art, (0, 0), polygon_mask(px, body))
    rim = clear(px)
    ImageDraw.Draw(rim).line([tuple(p) for p in body] + [tuple(body[0])], fill=(255, 255, 255, 31), width=max(1, round(4 * k)))
    icon.alpha_composite(Image.composite(rim, clear(px), polygon_mask(px, body)))
    return icon


def render_mark(px, height_px, colour=None, with_glow=False):
    geo = geometry(px, height_px, (px / 2, px / 2))
    img = clear(px)
    if with_glow:
        img.alpha_composite(glow(px, geo["glow"], ORCHID, 0.35 * height_px, 0.24))
    paint(img, geo, colour)
    return img, geo


# --- Android adaptive-icon layers --------------------------------------------------------------

def android_layers():
    """432 px layers (108dp at xxxhdpi): the launcher shows the middle 288 px, which is the body."""
    A, VIS = 432, 288
    geo = geometry(A, MARK_H * VIS, (A / 2, A / 2))
    fg = clear(A)
    paint(fg, geo)
    mono = clear(A)
    paint(mono, geo, WHITE)
    folder = RES / "mipmap-xxxhdpi"
    folder.mkdir(parents=True, exist_ok=True)
    fg.save(folder / "ic_launcher_foreground.png")
    mono.save(folder / "ic_launcher_monochrome.png")
    (OUT / "android").mkdir(parents=True, exist_ok=True)
    fg.save(OUT / "android/ic_launcher_foreground.png")
    mono.save(OUT / "android/ic_launcher_monochrome.png")

    # Background: vector, so the gradient and glow stay smooth at every density.
    to_dp = 108 / A
    gx, gy = geo["glow"] * to_dp
    sigma = 165 * (VIS / BODY) * to_dp
    stops = "".join(
        f'<item android:offset="{t:.2f}" android:color="#{round(255 * 0.30 * np.exp(-((t * 3) ** 2) / 2)):02X}{ORCHID[1:]}" />'
        for t in np.linspace(0, 1, 7)
    )
    (RES / "drawable/ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Generated by Branding/make_brand.py; edit the script, not this file. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="108dp"\n    android:height="108dp"\n'
        '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
        '    <path android:pathData="M0,0 H108 V108 H0 Z">\n'
        '        <aapt:attr name="android:fillColor">\n'
        f'            <gradient android:type="linear" android:startX="54" android:startY="18" android:endX="54" android:endY="90" '
        f'android:startColor="{VIOLET}" android:endColor="{INK}" />\n'
        "        </aapt:attr>\n    </path>\n"
        '    <path android:pathData="M0,0 H108 V108 H0 Z">\n'
        '        <aapt:attr name="android:fillColor">\n'
        f'            <gradient android:type="radial" android:centerX="{gx:.2f}" android:centerY="{gy:.2f}" '
        f'android:gradientRadius="{3 * sigma:.2f}">{stops}</gradient>\n'
        "        </aapt:attr>\n    </path>\n</vector>\n"
    )

    # What a launcher shows: the body, masked (rounded square here).
    preview = Image.new("RGBA", (VIS * 2 + 80, VIS * 2 + 80), rgba(MIST))
    preview.alpha_composite(render_icon(VIS * 2), (40, 40))
    preview.save(OUT / "android/preview.png")


# --- Brand sheet (layout follows WiFiBar's kit) -------------------------------------------------

SF_PRO = "/System/Library/Fonts/SFNS.ttf"
HELVETICA = "/System/Library/Fonts/HelveticaNeue.ttc"


def font(size, weight=400):
    """SF Pro (variable) at the given weight and a matching optical size; Helvetica Neue fallback."""
    try:
        f = ImageFont.truetype(SF_PRO, size)
        values = []
        for axis in f.get_variation_axes():
            name = axis["name"].decode() if isinstance(axis["name"], bytes) else axis["name"]
            if name == "Weight":
                values.append(weight)
            elif name == "Optical Size":
                values.append(min(max(size * 0.75, axis["minimum"]), axis["maximum"]))
            else:
                values.append(axis["default"])
        f.set_variation_by_axes(values)
        return f
    except Exception:
        return ImageFont.truetype(HELVETICA, size, index={400: 0, 500: 10, 600: 10, 700: 1}.get(weight, 0))


def rounded(size, radius, fill, art=None, art_offset=(0, 0)):
    card = Image.new("RGBA", size, rgba(fill))
    if art is not None:
        card.alpha_composite(art, art_offset)
    big = Image.new("L", (size[0] * 4, size[1] * 4), 0)
    ImageDraw.Draw(big).rounded_rectangle((0, 0, size[0] * 4 - 1, size[1] * 4 - 1), radius * 4, fill=255)
    out = clear(size)
    out.paste(card, (0, 0), big.resize(size, Image.LANCZOS))
    return out


def card_text(draw, x, y, text, size, weight, fill, right):
    """Text on a card; refuses to draw past the card's right padding instead of clipping silently."""
    f = font(size, weight)
    if x + draw.textlength(text, font=f) > right:
        raise ValueError(f"brand sheet: {text!r} overflows its card by {x + draw.textlength(text, font=f) - right:.0f} px")
    draw.text((x, y), text, font=f, fill=fill, anchor="ls")


def dashed_rect(draw, box, colour, dash=10, gap=8, width=3):
    x0, y0, x1, y1 = box
    for (ax, ay, bx, by) in ((x0, y0, x1, y0), (x1, y0, x1, y1), (x1, y1, x0, y1), (x0, y1, x0, y0)):
        length = np.hypot(bx - ax, by - ay)
        for i in range(int(length // (dash + gap)) + 1):
            s, e = i * (dash + gap) / length, min(1, (i * (dash + gap) + dash) / length)
            draw.line((ax + (bx - ax) * s, ay + (by - ay) * s, ax + (bx - ax) * e, ay + (by - ay) * e), fill=colour, width=width)


def brand_sheet(master):
    W, H = 2800, 1900
    sheet = Image.new("RGBA", (W, H), rgba(MIST))
    d = ImageDraw.Draw(sheet)
    ink, grey = rgba(INK), rgba(GREY)

    def label(x, y, text, size=26, weight=500, fill=grey, anchor="ls"):
        d.text((x, y), text, font=font(size, weight), fill=fill, anchor=anchor)

    label(80, 110, "PadStudio", 52, 600, ink)
    label(80 + d.textlength("PadStudio", font=font(52, 600)) + 24, 110, "Brand kit", 52, 400, grey)
    sheet.alpha_composite(master, (80, 150))
    label(180, 1200, "App icon  ·  1024 px", 28)

    x0, cw = 1180, 755
    label(x0, 235, "Sizes  ·  128, 48 and 32 px at actual size", 28)
    for i, (bgc, fg) in enumerate(((WHITE, grey), (GRAPHITE, rgba("#A1A1A6")))):
        cx = x0 + i * (cw + 30)
        sheet.alpha_composite(rounded((cw, 300), 28, bgc), (cx, 260))
        mid = 260 + 128
        for size, left in ((128, 70), (48, 330), (32, 470)):
            sheet.alpha_composite(master.resize((size, size), Image.LANCZOS), (cx + left, mid - size // 2))
            label(cx + left + size // 2, 525, str(size), 24, 500, fg, anchor="ms")
        label(cx + cw - 40, 525, "Light" if i == 0 else "Dark", 24, 500, fg, anchor="rs")

    label(x0, 640, "Mark", 28)
    light_mark, _ = render_mark(380, 250, colour=INK)
    dark_mark, _ = render_mark(380, 250, with_glow=True)
    for i, (bgc, mk) in enumerate(((WHITE, light_mark), (GRAPHITE, dark_mark))):
        cx = x0 + i * (cw + 30)
        sheet.alpha_composite(rounded((cw, 380), 28, bgc, mk, ((cw - 380) // 2, 0)), (cx, 665))

    label(x0, 1125, "Colour", 28)
    sw, gap = 240, 20
    for i, (name, hx, use) in enumerate(PALETTE):
        sx = x0 + i * (sw + gap)
        sheet.alpha_composite(rounded((sw, 150), 22, hx), (sx, 1150))
        if hx == WHITE:
            d.rounded_rectangle((sx, 1150, sx + sw - 1, 1299), 22, outline=rgba("#D2D2D7"), width=2)
        label(sx, 1338, name, 26, 600, ink)
        label(sx, 1372, hx.upper(), 24, 400, grey)
        lines, line = [], ""
        for word in use.split():
            trial = (line + " " + word).strip()
            if d.textlength(trial, font=font(20, 400)) > sw - 4:
                lines.append(line)
                line = word
            else:
                line = trial
        lines.append(line)
        for k, ln in enumerate(lines):
            label(sx, 1404 + k * 26, ln, 20, 400, grey)

    label(80, 1300, "Typography  ·  SF Pro (system)", 28)
    lock, _ = render_mark(170, 150, colour=INK)
    sheet.alpha_composite(lock, (80, 1400))
    d.text((80 + 170 + 20, 1530), "PadStudio", font=font(170, 600), fill=ink, anchor="ls")
    label(80, 1610, "Wordmark: SF Pro Semibold, tracking default. Labels: SF Pro Regular and Medium.", 24, 400)

    label(x0, 1515, "Clear space and minimum size", 28)
    card_h, cs_px = 250, 250
    cs_mark, cs_geo = render_mark(cs_px, 150, colour=INK)
    card = rounded((cw, card_h), 28, WHITE, cs_mark, (40, (card_h - cs_px) // 2))
    cd = ImageDraw.Draw(card)
    (bx0, by1), (bx1, by0) = cs_geo["box"]
    nib = NIB_W * cs_geo["k"]
    oy = (card_h - cs_px) // 2
    dashed_rect(cd, (40 + bx0 - nib, oy + by0 - nib, 40 + bx1 + nib, oy + by1 + nib), rgba(ORCHID), width=3)
    dashed_rect(cd, (40 + bx0, oy + by0, 40 + bx1, oy + by1), rgba("#C7C7CC"), dash=6, gap=6, width=2)
    tx = 40 + cs_px + 20
    card_text(cd, tx, 105, "Keep one nib width (N) clear", 26, 600, ink, cw - 32)
    card_text(cd, tx, 140, "on every side of the mark.", 26, 400, ink, cw - 32)
    card_text(cd, tx, 185, "Orchid dashes mark the boundary.", 22, 400, grey, cw - 32)
    sheet.alpha_composite(card, (x0, 1540))

    card2 = rounded((cw, card_h), 28, WHITE)
    c2 = ImageDraw.Draw(card2)
    card2.alpha_composite(master.resize((32, 32), Image.LANCZOS), (52, 84))
    m24, _ = render_mark(32, 24, colour=INK)
    card2.alpha_composite(m24, (142, 84))
    c2.text((68, 150), "icon", font=font(20, 400), fill=grey, anchor="ms")
    c2.text((158, 150), "mark", font=font(20, 400), fill=grey, anchor="ms")
    card_text(c2, 250, 105, "Smallest sizes: icon 32 px,", 26, 600, ink, cw - 32)
    card_text(c2, 250, 140, "mark 24 px tall (shown actual size).", 26, 400, ink, cw - 32)
    card_text(c2, 250, 185, "Smaller than that, use the icon.", 22, 400, grey, cw - 32)
    sheet.alpha_composite(card2, (x0 + cw + 30, 1540))

    card_text(d, 80, 1850, "The mark is plotted, not drawn:  a broad nib at φ = 35° swept along a stem and the superellipse bowl "
              "|x/a|^2.4 + |(y − c)/b|^2.4 = 1;  width = N·|sin(θ − φ)|, pressing in over the first 4% of the path.",
              28, 400, ink, W - 80)
    return sheet


def main():
    OUT.mkdir(exist_ok=True)
    master = render_icon()
    master.save(OUT / "AppIcon-1024.png")
    mono, _ = render_mark(512, 400, colour="#000000")
    mono.save(OUT / "mark-monochrome.png")
    android_layers()
    brand_sheet(master).save(OUT / "brand-sheet.png")
    print("wrote", OUT, "and the launcher layers under", RES)


if __name__ == "__main__":
    main()
