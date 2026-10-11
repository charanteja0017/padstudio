"""Detect launcher icon boxes in a 3200x2136 home-screen screenshot.

Method (no hand-entered coordinates):
  1. Edge map G = max over R,G,B of the Sobel gradient magnitude (after a sigma-1 blur), then
     saturated, Gs = G / (G + K), so that a complete but faint outline (a dark icon plate on a
     dark wallpaper) is not outvoted by strong but scattered edges inside the icon art.
  2. Correlate Gs with a zero-mean ring template: the outline of a 168 px superellipse
     |x|^n + |y|^n = 1 (n = 4.4), blurred across the boundary. Local maxima are seeds.
  3. Refine every seed coarse-to-fine: centre and size, maximising the mean of Gs sampled on
     the superellipse outline minus the mean of Gs 3 px inside and 3 px outside it.
  4. Merge refined boxes that landed on the same icon; keep the best.
  5. Accept a box only if its fitted size is 164..171 px and its outline score >= SCORE.
     Measured on the two home screenshots: accepted scores 0.399..0.577, best rejected 0.186.

Usage: python3 -I -B detect.py <screenshot.png> <out.json> [overlay.png]
"""
import json
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage, signal

N = 4.4
NOMINAL = 168.0
K = 50.0          # edge saturation constant (Sobel units)
SEED_REL = 0.05   # seeds: local maxima above this fraction of the best response
SCORE = 0.30
SIZE_RANGE = (164.0, 171.0)


def edge_map(rgb):
    g = np.zeros(rgb.shape[:2], np.float32)
    for c in range(3):
        ch = ndimage.gaussian_filter(rgb[..., c], 1.0)
        gx = ndimage.sobel(ch, axis=1)
        gy = ndimage.sobel(ch, axis=0)
        g = np.maximum(g, np.hypot(gx, gy))
    return g


def ring_template(size=NOMINAL, n=N, sigma=1.5, pad=16):
    h = size / 2.0
    half = int(h + pad)
    ys, xs = np.mgrid[-half:half + 1, -half:half + 1].astype(np.float64)
    r = (np.abs(xs / h) ** n + np.abs(ys / h) ** n) ** (1.0 / n)
    t = np.exp(-(((r - 1.0) * h) ** 2) / (2 * sigma ** 2))
    return (t - t.mean()).astype(np.float32)


def outline_points(n=N, count=720):
    th = np.linspace(0, 2 * np.pi, count, endpoint=False)
    c, s = np.cos(th), np.sin(th)
    x = np.sign(c) * np.abs(c) ** (2.0 / n)
    y = np.sign(s) * np.abs(s) ** (2.0 / n)
    nx = np.sign(x) * np.abs(x) ** (n - 1)
    ny = np.sign(y) * np.abs(y) ** (n - 1)
    nl = np.hypot(nx, ny)
    return x, y, nx / nl, ny / nl


UX, UY, NX, NY = outline_points()


def sample(img, xs, ys):
    # continuous coords: pixel i covers [i, i+1); its centre is i + 0.5
    return ndimage.map_coordinates(img, [ys - 0.5, xs - 0.5], order=1, mode="nearest")


def fit_scores(g, cxs, cys, s):
    """Outline metrics for many centres at one size. cxs, cys: 1-D arrays."""
    h = s / 2.0
    px = cxs[:, None] + UX[None, :] * h
    py = cys[:, None] + UY[None, :] * h
    on = sample(g, px.ravel(), py.ravel()).reshape(px.shape).mean(1)
    inside = sample(g, (px - NX * 3.0).ravel(), (py - NY * 3.0).ravel()).reshape(px.shape).mean(1)
    outside = sample(g, (px + NX * 3.0).ravel(), (py + NY * 3.0).ravel()).reshape(px.shape).mean(1)
    return on - 0.5 * (inside + outside), on, inside, outside


def search(g, cx0, cy0, offs, sizes):
    dxs, dys = np.meshgrid(offs, offs)
    cxs, cys = cx0 + dxs.ravel(), cy0 + dys.ravel()
    best = None
    for s in sizes:
        sc, on, ins, out = fit_scores(g, cxs, cys, s)
        k = int(np.argmax(sc))
        if best is None or sc[k] > best[0]:
            best = (float(sc[k]), float(cxs[k]), float(cys[k]), float(s), float(on[k]), float(ins[k]), float(out[k]))
    return best


def refine(g, cx0, cy0):
    b = search(g, cx0, cy0, np.arange(-8.0, 8.01, 1.0), np.arange(150.0, 186.01, 1.0))
    b = search(g, b[1], b[2], np.arange(-1.0, 1.01, 0.25), np.arange(b[3] - 1.0, b[3] + 1.01, 0.25))
    return b


def detect(path):
    arr = np.asarray(Image.open(path))
    if arr.shape[2] == 4:
        assert arr[..., 3].min() == 255, f"{path}: screenshot has transparent pixels"
    rgb = arr[..., :3].astype(np.float32)
    g = edge_map(rgb)
    gs = g / (g + K)
    resp = signal.fftconvolve(gs, ring_template()[::-1, ::-1], mode="same")
    peaks = (resp == ndimage.maximum_filter(resp, size=41)) & (resp > SEED_REL * resp.max())
    ys, xs = np.nonzero(peaks)
    seeds = sorted(zip(resp[ys, xs].tolist(), xs.tolist(), ys.tolist()), reverse=True)
    fits = []
    for r, x, y in seeds:
        sc, cx, cy, s, on, ins, out = refine(gs, x + 0.5, y + 0.5)
        raw = fit_scores(g, np.array([cx]), np.array([cy]), s)
        fits.append(dict(cx=cx, cy=cy, size=s, score=round(sc, 4), on=round(on, 4), inside=round(ins, 4),
                         outside=round(out, 4), ratio=round(on / max(ins, out, 1e-6), 2),
                         raw_on=round(float(raw[1][0]), 1), seed_resp_rel=round(r / float(resp.max()), 3)))
    # merge fits that converged onto the same object
    fits.sort(key=lambda d: -d["score"])
    merged = []
    for f in fits:
        if all(abs(f["cx"] - m["cx"]) > 12 or abs(f["cy"] - m["cy"]) > 12 for m in merged):
            merged.append(f)
    for m in merged:
        # ratio is reported but not used: icons whose art runs to the edge (a tank scene) have
        # strong edges just inside the outline, so on/inside is low for them although the fit is right.
        m["accepted"] = bool(SIZE_RANGE[0] <= m["size"] <= SIZE_RANGE[1] and m["score"] >= SCORE)
    merged.sort(key=lambda d: (round(d["cy"] / 100), d["cx"]))
    return rgb, len(seeds), merged


def main():
    path, out_json = sys.argv[1], sys.argv[2]
    rgb, nseeds, dets = detect(path)
    json.dump(dict(source=path, seeds=nseeds, params=dict(n=N, K=K, seed_rel=SEED_REL, score=SCORE,
                                                       size_range=SIZE_RANGE), boxes=dets), open(out_json, "w"), indent=1)
    acc = [d for d in dets if d["accepted"]]
    rej = sorted([d for d in dets if not d["accepted"]], key=lambda d: -d["score"])
    print(f"seeds {nseeds}, distinct fits {len(dets)}, accepted {len(acc)}")
    for d in acc:
        print("  ACCEPT", d["cx"], d["cy"], d["size"], "score", d["score"], "ratio", d["ratio"], "raw_on", d["raw_on"])
    for d in rej[:5]:
        print("  best rejected", d["cx"], d["cy"], d["size"], "score", d["score"], "ratio", d["ratio"])
    if len(sys.argv) > 3:
        im = Image.fromarray(rgb.astype(np.uint8))
        dr = ImageDraw.Draw(im)
        for k, d in enumerate(acc):
            h = d["size"] / 2
            dr.rectangle([d["cx"] - h, d["cy"] - h, d["cx"] + h, d["cy"] + h], outline=(0, 255, 0), width=4)
            dr.text((d["cx"] - h + 4, d["cy"] + h + 6), f"{k}", fill=(0, 255, 0))
        im.resize((1600, 1068), Image.LANCZOS).save(sys.argv[3])


if __name__ == "__main__":
    main()
