# Focus Pen Pro 3D asset

`build_pen.py` makes the live 3D pen used by the PadStudio 2 preview (`<model-viewer>`) and the app
(SceneView). It runs headless in Blender 5.1.2 and writes one glTF binary, `focus_pen_pro.glb`
(plan decision 15).

It has two modes:

- **Placeholder (default).** A pen generated from math: a lathe profile with the proportions of
  the Focus Pen Pro drawing in the owner's motion kit, `docs/redesign/pen-motion-kit.html`.
- **The owner's model (`--source FILE`).** It imports the model, checks the five markers, attaches
  the same gesture effects and makes sure the six clips exist.

## Run it

From the repository root:

```sh
# placeholder -> docs/redesign/assets/pen/ and padstudio/app/src/main/assets/pen/
/Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup \
    --python Branding/pen3d/build_pen.py

# the owner's model (.glb, .gltf, .fbx or .blend), written to other folders
/Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup \
    --python Branding/pen3d/build_pen.py -- --source path/to/pen.glb --out some/dir other/dir
```

| Option | Meaning |
|---|---|
| `--source FILE` | Wrap this model instead of building the placeholder. |
| `--out DIR [DIR ...]` | Output folders. The default is the two asset folders above. Every folder gets byte-identical copies. |
| `--save-blend FILE` | Also save the built scene as a `.blend`, for inspection or for editing by hand. |

Exit codes: `0` built; `2` the model is missing markers (the message lists them); `1` any other error.

The export goes to a temporary file and is read back and checked before anything is replaced. A
failed build leaves existing `.glb` files untouched. `--save-blend` is the exception: it is written
before the export. The script prints a summary from that read-back: markers, clips, triangles, size
and sha256.

## Conventions

- glTF axes, metres. **+Y runs along the pen toward the end cap, and the tip is at −Y.** The origin
  is the pen's centre.
- +Z is the camera-facing side, the side `<model-viewer>`'s default camera (theta 0) looks at.
- One root node, `pen`, holds everything. At rest it is the identity.
- The output never uses Draco or meshopt compression.
- **The placeholder** has lathe parts with 72 segments and exact (analytic) smooth normals. It has no
  textures, no UVs and no glTF extensions.

## The placeholder is nominal

The length is **0.165 m, a nominal figure, not a measurement** of the real pen: end-cap top to nib
apex. Every other size follows from the kit drawing at that scale, 1 kit unit = 0.3128 mm. None of
these sizes are measured either.

| Part | From the kit (SVG units, y runs down the pen) | Pen space (mm) |
|---|---|---|
| Body (`body`) | rect 32 wide, y 48–534, corner radius 12 (rounded top) | radius 5.005; the `body` mesh runs from the end-cap seam (Y +77.182) to Y −69.519, collar included |
| End cap (`end_cap`) | the rounded top, up to the hairline at y 65 | Y +82.500 to +77.182; flat top r 1.251; corner r 3.754 |
| Logo band (`logo_band`) | the wordmark at y 109 | engraved ring at Y +63.419, 0.9 wide, 0.15 deep, no text |
| Collar | y 520–534 (14 units) | groove at Y −65.140 |
| Tip cone (`tip_cone`) | y 534–568, half-width 16 to 3 | Y −69.519 to −80.154, radius 5.005 to 0.938 |
| Nib (`nib`) | quadratic, y 568 to 575.5 | parabolic dome to the apex at Y −82.500 |
| Slide strip (`slide_strip`) | not in the kit (design choice) | pill-shaped recess on +Z, Y −6 to +22, 3.4 wide, 0.22 deep with a 0.15 chamfer |

The body is a single lathe with a window cut around the strip. Blender's constrained Delaunay
triangulation fills that window, so the recess is real geometry, not a decal.

### Materials

These are PBR metallic-roughness materials. Base colours are given in sRGB and stored as linear factors.

| Material | Used by | Colour | Metallic | Roughness |
|---|---|---|---|---|
| `satin_white` | body, end_cap | #E9ECF2 | 0 | 0.35 |
| `matte_dark_grey` | nib | #2B2F36 (your reference: a dark nib) | 0 | 0.55 |
| `tip_light` | tip_cone | #D5DAE2 (your reference: a light tip) | 0 | 0.40 |
| `strip_gloss` | slide_strip | #DDE1E8, slightly glossier | 0 | 0.20 |
| `band_engraved` | logo_band | #AEB5C1 | 0 | 0.50 |
| `fx_lavender` | the four effects | #BBAAFF, emissive #BBAAFF (strength 1) | 0 | 0.40 |

## Markers (for hotspots)

Markers are empties directly under `pen`. Positions are in model units (metres), the same units
`<model-viewer>` takes in `data-position`.

| Marker | Position (x, y, z) m | Where | Suggested `data-normal` |
|---|---|---|---|
| `marker_squeeze` | 0, −0.01885, 0.00500 | lower barrel, where the kit's squeeze arcs sit (kit y 372) | `0m 0m 1m` |
| `marker_slide_top` | 0, +0.02030, 0.00500 | top end of the slide strip | `0m 0m 1m` |
| `marker_slide_bottom` | 0, −0.00430, 0.00500 | bottom end of the slide strip | `0m 0m 1m` |
| `marker_double_tap` | 0, +0.04000, 0.00500 | upper barrel | `0m 0m 1m` |
| `marker_nib` | 0, −0.08250, 0 | nib apex | `0m -1m 0m` |

Example: `<button slot="hotspot-squeeze" data-position="0m -0.01885m 0.005m" data-normal="0m 0m 1m">`.

These positions are for the rest pose. A static hotspot drifts from its marker by at most:

- **`idle`:** 2.0 mm;
- **`double_tap`:** 1.8 mm;
- **`write`:** 56 mm at `marker_double_tap`, and 3.5 mm at the nib, which traces its loop.

These figures were measured from the exported clips. Hide the callouts while `write` plays, or let
them follow the ink overlay.

## Effects

Each effect is a node with identity rotation, hidden by `scale 0` except while its clip plays. Effects
move only by translation and scale. Nothing in the file animates a material, so every glTF viewer
plays the clips.

| Node | Parent | Shape (barrel radius R = 5.005 mm) |
|---|---|---|
| `fx_squeeze_arc_left`, `fx_squeeze_arc_right` | `marker_squeeze` | Partial tori, "( )" either side of the barrel in the picture plane. Arc radius 9.51 mm, 82° span, tube 0.50 mm. At rest the apexes are 13.61 mm from the axis. |
| `fx_slide_dot` | `marker_slide_bottom` | A sphere of radius 1.30 mm, its centre 0.38 mm outside the barrel surface. It travels 24.6 mm to `marker_slide_top`. |
| `fx_tap_ring` | `marker_double_tap` | A torus facing the camera, radius 9.38 mm at full size, tube 0.50 mm, 0.60 mm in front of the surface. |

The sizes come from the motion kit's SVG (arcs `M114 352 Q99 372 114 392` moving 13 units inward;
tap ring r 18–30), scaled with the barrel. The dot is smaller than the kit's because here it sits on
the strip.

## Clips

The file holds six named glTF animations, sampled at 30 fps with LINEAR interpolation. Every clip
keys the same 10 channels: `pen` translation and rotation, plus each effect's translation and scale.
Channels that don't move are written as two STEP keys, so a viewer that doesn't reset nodes between
clips (Filament) still hides the other effects. Every clip starts and ends with the pen at rest and
every effect hidden (scale 0), so a looping clip doesn't jump.

| Clip | Length | Motion (u = progress 0–1) |
|---|---|---|
| `idle` | 6.0 s, loops | Sway 12°·sin(2πu) about Y; rises 1 mm·(1 − cos 2πu), so 2 mm at the middle. |
| `squeeze` | 1.7 s | As the kit: p = half-sine between u 0.12 and 0.64. The arcs move 4.07 mm inward (13 kit units)·p, at scale √p. |
| `slide_up` | 1.7 s | The dot appears at the bottom marker, eases to the top (u 0.14–0.80) and vanishes. |
| `slide_down` | 1.7 s | The same, from top to bottom. |
| `double_tap` | 1.7 s | Two ring ripples in the kit's windows, u 0.10–0.31 and 0.42–0.63. Each tap nudges the pen 1.2° in the picture plane and 0.5 mm away from the camera. |
| `write` | 1.7 s | Pivoting on the nib, the pen tilts 22° (top toward +X) with a ±3° wobble, and the nib traces a 6 × 3 mm loop in the picture plane. The ink line is a 2D overlay drawn by the UI. |

## Using it in `<model-viewer>`

```html
<script type="module" src="https://cdn.jsdelivr.net/npm/@google/model-viewer@4.3.1/dist/model-viewer.min.js"></script>
<model-viewer src="assets/pen/focus_pen_pro.glb" animation-name="idle" autoplay
              camera-orbit="0deg 75deg auto" orientation="-40deg 0deg 0deg"
              interaction-prompt="none" disable-zoom></model-viewer>
```

- `orientation="-40deg 0deg 0deg"` gives the reference design's diagonal: top at the upper right,
  nib at the lower left.
- To show one frame: set `animationName`, wait for `updateComplete`, then call `pause()` and set
  `currentTime`.
- For reduced motion or no WebGL, use the motion kit's SVG pens.
- **`file://` caveat.** In the headless Chrome used for checks, a relative `src` from a `file://`
  page failed to load (`loadfailure`), while the same bytes as a `data:` URI loaded. A page opened
  from disk, such as an offline copy, needs the GLB inlined as a `data:model/gltf-binary;base64,…`
  URI. A page served over http(s) can use the relative path.

## The owner's model (`--source`)

**Requirements**

- `.glb`/`.gltf` and `.fbx` are imported into an empty scene at 30 fps. A `.blend` is opened as-is
  and keeps its own frame rate.
- The model must contain empties named exactly `marker_squeeze`, `marker_slide_top`,
  `marker_slide_bottom`, `marker_double_tap` and `marker_nib`. Put them on the gesture spots; the
  barrel markers sit on the camera-facing surface. If any are missing, the build stops with exit 2
  and lists them.
- Use metres, +Y along the pen with the tip at −Y, and +Z as the side shown to the camera. The script
  warns if the pen isn't between 0.05 and 0.5 units long.

**What the script does**

- **Root.** One top-level object becomes the animated root. Several top-level objects are wrapped in
  a new `pen` root.
- **Removed.** Cameras and lights, and any objects whose names start with `fx_`. That prefix belongs
  to this script, so a model that has been through it once can be fed back in.
- **Clips kept.** Clips named exactly `idle`, `squeeze`, `slide_up`, `slide_down`, `double_tap` or
  `write` are kept. So are the FBX form `object|squeeze` and Blender's `squeeze.001`. The effects
  are added to them, stretched to the clip's length.
- **Clips added.** Missing clips are generated with the motion above.
- **Clips dropped.** Any other clip is dropped and named in the log.
- **Effect sizing.** Effects are sized from the measured barrel radius and placed by the markers. An
  effect stays upright in the picture plane even if a marker is rotated.

## Verification (11 Oct 2026, Blender 5.1.2, exporter 5.1.20)

- **Placeholder output:** 306,140 bytes, sha256 `e745908ba9d9652eb0e914fd57057dbbafb27cae91dadddf3d73e9b78ccb2873`.
  It has 16 nodes, 10 meshes, 6 materials, 13,758 triangles (limit 50,000) and 6 animations
  (idle 6.000 s; the others 1.700 s).
- **Khronos glTF Validator 2.0.0-dev.3.10:** 0 errors, 0 warnings, 2 infos. The infos are
  `NODE_EMPTY` on `marker_nib` and `marker_slide_top`, which are deliberately empty markers. The
  validator does catch faults: on a deliberately corrupted copy it reported `ROTATION_NON_UNIT` and
  `UNRESOLVED_REFERENCE`.
- **Determinism:** eight separate Blender runs since the last geometry change gave byte-identical
  files (the sha256 above). Blender's exporter was byte-deterministic for this scene.
- **Round trips with `--source`:**
  - **This `.glb`:** node names, order, hierarchy and rest transforms are identical, and so are the
    materials, the animation names, durations and channel targets, and the triangle count. The file
    grows to 311,564 bytes. Blender's importer stores normals in a quantised form, so the exporter
    splits a few shared vertices (`end_cap` 1,225 → 1,391, `logo_band` 432 → 492). Every vertex
    position matches, and the worst normal deviation is 0.037°.
  - **`.blend`:** byte-identical output.
  - **FBX exported by Blender:** all six takes are kept and the motion is the same. FBX bakes every
    node, so each clip carries 44 channels instead of 10. Prefer `.glb` or `.blend`.
- **Rotated and scaled markers:** markers were rotated (45°/20°, 30° and 90°) and scaled (uniform
  2×, and non-uniform 0.5 / 1 / 1.5). The effects' world-space geometry and animated positions still
  matched the upright build within 0.00001 mm.
- **Failure paths:**
  - A source with two markers renamed, and one with no markers, both exit 2 and list exactly what's
    missing.
  - A failed check after export, such as the triangle budget, exits 1 and leaves any existing output
    untouched.
- **Rendering:** checked in `<model-viewer>` 4.3.1 under headless Chrome (SwiftShader WebGL) at the
  mid-times of `idle`, `squeeze` and `double_tap`, plus `slide_up`, `slide_down`, `write` and a
  diagonal hero view. The pen and effects were visible and framed.
