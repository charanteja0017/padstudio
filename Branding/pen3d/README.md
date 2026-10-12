# Focus Pen Pro 3D asset

`build_pen.py` makes the live 3D pen used by the PadStudio 2 preview (`<model-viewer>`) and the app
(SceneView). It runs headless in Blender 5.1.2 and writes one glTF binary, `focus_pen_pro.glb`
(plan decision 15).

It has two modes:

- **Placeholder (default).** A pen generated from math: a lathe profile with the proportions of
  the Focus Pen Pro drawing in the owner's motion kit, `docs/redesign/pen-motion-kit.html`.
- **The owner's model (`--source FILE`).** It imports the model, checks the six markers, attaches
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
- **The placeholder** has lathe parts with 128 segments and exact (analytic) smooth normals. It has no
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
| Slide strip (`slide_strip`) | not in the kit (design choice) | pill-shaped recess on +Z, Y −58.5 to −32.5, 3.4 wide, 0.22 deep with a 0.15 chamfer |
| Grip grooves (in `body`) | not in the kit (design choice) | six rings at Y −56, −52, −48, −44, −40 and −36, each 0.4 wide (rim to rim) and 0.1 deep; a cosine dip, so the surface and its normals stay smooth |

The body is a single lathe with a window cut around the strip. Blender's constrained Delaunay
triangulation fills that window, so the recess is real geometry, not a decal. The window follows the
grooves, so the recess cuts through them and the rings run right up to its rim.

The gesture spots are not from the kit. The owner wants every function close to the nib, so the strip,
the grooves and the four gesture markers all sit in the grip band, 24 to 50 mm above the nib apex.

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
`<model-viewer>` takes in `data-position`. There are six. The same table is in
`docs/redesign/ROUND2-BRIEF.md`.

| Marker | Position (x, y, z) m | Above the nib apex | Where | Suggested `data-normal` |
|---|---|---|---|---|
| `marker_nib` | 0, −0.0825, 0 | 0 | nib apex | `0m -1m 0m` |
| `marker_slide_bottom` | 0, −0.0565, 0.005 | 26 mm | bottom end of the slide strip (2 mm inside it); the slide dot starts here | `0m 0m 1m` |
| `marker_squeeze` | 0, −0.0505, 0.005 | 32 mm | between the slide markers; the squeeze arcs sit here | `0m 0m 1m` |
| `marker_double_tap` | 0, −0.0385, 0.005 | 44 mm | the tap ring sits here | `0m 0m 1m` |
| `marker_slide_top` | 0, −0.0345, 0.005 | 48 mm | top end of the slide strip (2 mm inside it); the slide dot ends here | `0m 0m 1m` |
| `marker_top` | 0, +0.0825, 0 | 165 mm | centre of the end cap's top | `0m 1m 0m` |

The four gesture markers are at z = 0.005 exactly, as in that table (the barrel surface is at
0.0050047). The slide strip runs from Y −0.0585 to −0.0325, so it covers the whole grip band on +Z.

Example: `<button slot="hotspot-squeeze" data-position="0m -0.0505m 0.005m" data-normal="0m 0m 1m">`.

These positions are for the rest pose. A static hotspot drifts from its marker by at most:

- **`idle`:** 2.0 mm, at every marker;
- **`double_tap`:** 1.8 mm at `marker_nib` and `marker_top`, and 1.3 mm or less at the four gesture
  markers;
- **`write`:** 14.6 mm at `marker_slide_bottom`, 17.1 mm at `marker_squeeze`, 22.3 mm at
  `marker_double_tap`, 24.1 mm at `marker_slide_top` and 74.6 mm at `marker_top`; 3.5 mm at the nib,
  which traces its loop.

These figures were measured from the exported clips. Hide the callouts while `write` plays, or let
them follow the ink overlay.

## Effects

Each effect is a node with identity rotation, hidden by `scale 0` except while its clip plays. Effects
move only by translation and scale. Nothing in the file animates a material, so every glTF viewer
plays the clips.

| Node | Parent | Shape (barrel radius R = 5.005 mm) |
|---|---|---|
| `fx_squeeze_arc_left`, `fx_squeeze_arc_right` | `marker_squeeze` | Partial tori, "( )" either side of the barrel in the picture plane. Arc radius 5.59 mm, 82° span, tube 0.50 mm. At rest the apexes are 8.00 mm from the axis. |
| `fx_slide_dot` | `marker_slide_bottom` | A sphere of radius 1.30 mm, its centre 0.38 mm outside the barrel surface. It travels 22.0 mm to `marker_slide_top`. |
| `fx_tap_ring` | `marker_double_tap` | A torus facing the camera, radius 6.00 mm at full size, tube 0.50 mm, 0.60 mm in front of the surface. |

The effects are children of their markers, so they follow them. The sizes come from the motion kit's
SVG (arcs `M114 352 Q99 372 114 392`; tap ring r 18–30), scaled with the barrel, except that two are
drawn smaller for the close-up on the grip band:

- the tap ring is 6 mm, where the kit's would be 9.38 mm;
- the squeeze arcs keep the kit's shape at 0.588 of its size: apex 8 mm from the axis (the kit's
  13.61 mm), arc radius 5.59 mm (9.51 mm).

The kit moves the arcs 4.07 mm inward. That would carry an 8 mm apex to 3.9 mm, inside the 5 mm
barrel, so here they close in 1.01 mm and stop with the tube 0.10 mm off the barrel at its nearest
point. The dot is smaller than the kit's because here it sits on the strip. In a `--source` model the
sizes scale with the measured barrel radius.

## Clips

The file holds six named glTF animations, sampled at 30 fps with LINEAR interpolation. Every clip
keys the same 10 channels: `pen` translation and rotation, plus each effect's translation and scale.
Channels that don't move are written as two STEP keys, so a viewer that doesn't reset nodes between
clips (Filament) still hides the other effects. Every clip starts and ends with the pen at rest and
every effect hidden (scale 0), so a looping clip doesn't jump.

| Clip | Length | Motion (u = progress 0–1) |
|---|---|---|
| `idle` | 6.0 s, loops | Sway 12°·sin(2πu) about Y; rises 1 mm·(1 − cos 2πu), so 2 mm at the middle. |
| `squeeze` | 1.7 s | As the kit: p = half-sine between u 0.12 and 0.64. The arcs move 1.01 mm inward·p (the kit's 13 units would be 4.07 mm; see Effects), at scale √p. |
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
- The model must contain six empties named exactly `marker_squeeze`, `marker_slide_top`,
  `marker_slide_bottom`, `marker_double_tap`, `marker_nib` and `marker_top`. Put them on the spots in
  the Markers table: the four gesture markers on the camera-facing surface in the grip band just above
  the nib, `marker_nib` on the apex and `marker_top` on the centre of the end cap. If any are missing,
  the build stops with exit 2 and lists them. A model made for the earlier five-marker contract needs
  `marker_top` added.
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

## Verification (12 Oct 2026, Blender 5.1.2, exporter 5.1.20)

Run on the build in this commit. The toolchain was checked first: the unmodified script from the
previous commit reproduced that commit's `.glb` byte for byte.

- **Placeholder output:** 545,532 bytes, sha256 `57bd31fb0db2674c99b50be844d9f6c12ff687402a82d422da1a8445fc5ffb7c`.
  It has 17 nodes, 10 meshes, 6 materials, 14,895 vertices, 26,046 triangles (limit 50,000) and 6
  animations (idle 6.000 s; the others 1.700 s).
- **Khronos glTF Validator 2.0.0-dev.3.10:** 0 errors, 0 warnings, 3 infos. The infos are
  `NODE_EMPTY` on `marker_nib`, `marker_slide_top` and `marker_top`, the markers with no children,
  which are deliberately empty. The validator does catch faults: on a deliberately corrupted copy (an
  invalid `componentType`) it reported errors and a warning.
- **Determinism:** five separate Blender runs of this script gave byte-identical files (the sha256
  above), and so did the `.blend` round trip below. Both output folders hold byte-identical copies.
- **Geometry,** read back from the exported bytes with a separate reader:
  - the six skin parts (`end_cap`, `body`, `logo_band`, `slide_strip`, `tip_cone`, `nib`), welded by
    position, form a closed surface: all 33,453 edges are shared by exactly two triangles, so the
    strip plugs the body's window exactly;
  - no triangle is degenerate, every triangle winds with its vertex normals, and every normal is a
    unit vector;
  - on the back of the barrel there are six grooves at Y −56, −52, −48, −44, −40 and −36 mm, each
    0.4 mm rim to rim and 0.1 mm deep. Every `body` vertex between Y −60.5 and −30.5 mm, the strip
    window included, lies on that profile to within 0.002 µm (6,334 vertices);
  - the six markers are at the table positions; the strip spans Y −58.5 to −32.5 mm, 3.4 mm wide and
    0.22 mm deep; the arcs' apexes are 8.000 mm from the axis, the tap ring's radius is 6.000 mm and
    the dot travels 22.000 mm;
  - at no keyframe of `squeeze` does an arc come nearer the axis than 5.108 mm (the barrel is
    5.0047 mm).

  These checks fail when they should: one flipped triangle, one opened corner (4 loose edges) and the
  previous `.glb` (no grooves, other markers) were each reported.
- **Round trips with `--source`:**
  - **This `.glb`:** node names, order, hierarchy and rest transforms are identical, and so are the
    materials, the animation names, durations and channel targets, and the triangle count. The file
    grows to 557,008 bytes. Blender's importer stores normals in a quantised form, so the exporter
    splits some shared vertices (`end_cap` 2,177 → 2,471, `logo_band` 768 → 952). Every vertex
    position matches, and the worst normal deviation is 0.033°.
  - **`.blend`:** byte-identical output.
  - **FBX exported by Blender** (every action baked as a take): it imports, and nodes, vertices and
    normals match (worst normal deviation 0.038°). But only one of the six takes, `double_tap`, is
    kept as the source's clip; the other five are generated. Blender 5.1.2 names the actions it imports
    from that FBX `pen|pen|squeeze`, and the code that binds a loose action to its object strips only
    the last `|` part, so it looks for an object called `pen|pen`. (Stripping everything after the
    first `|` instead kept all six; that is not in this script.) The previous commit's script did the
    same on an FBX exported from its own build, so the earlier note that all six takes are kept does
    not hold for this export. Prefer `.glb` or `.blend`.
- **Failure paths:**
  - The previous (five-marker) `.glb`, a source with `marker_top` renamed, one with two markers
    renamed and one with no markers all exit 2 and list exactly what's missing.
  - A failed check after export exits 1 and leaves the existing output untouched (tried with the
    triangle budget lowered to 20,000 on a scratch copy, and a file already in the output folder).
- **Rendering:** checked in `<model-viewer>` 4.3.1 under headless Chrome (SwiftShader WebGL), served
  over http, at the mid-times of `idle` (3.0 s), `squeeze` (0.633 s), `double_tap` (0.42 s),
  `slide_up`, `slide_down` and `write` (0.85 s). The pen and effects were visible and framed, and
  hotspots placed from the Markers table landed on the six markers. Close-ups in Blender (Eevee) of
  the grip band and of each effect at its peak looked right: the grooves run round the barrel and up
  to the recess's rim, and the squeeze arcs stay fully in view.
- **Not re-run for this build:** the rotated and scaled marker checks of 11 Oct (the code that reads
  the marker frames is unchanged) and the diagonal hero view.
