/*
 * PadStudio 2 preview: Android's nonlinear font scaling, for the mock frames.
 *
 * Source: AOSP frameworks/base, GitHub mirror aosp-mirror/platform_frameworks_base at commit
 * 64130047e019cee612a85dde07755efd8f356f12 (2024-08-20), Apache License 2.0:
 *   core/java/android/content/res/FontScaleConverterFactory.java  lookup tables (verbatim below),
 *                                                                 forScale(), createInterpolatedTableBetween()
 *   core/java/android/content/res/FontScaleConverterImpl.java     lookupAndInterpolate() within one table
 *   core/java/android/util/MathUtils.java                         lerp(), lerpInv(), constrainedMap()
 *   core/java/android/util/TypedValue.java                        sp -> dp: the converter when there is one,
 *                                                                 otherwise sp * scaledDensity (linear)
 *   core/java/android/content/res/ResourcesImpl.java              fontScaleConverter = forScale(fontScale)
 *
 * The code below follows that Java step by step, in 32-bit float arithmetic (Math.fround) as Java's
 * float does. That matters in one place: table keys are (int) (scale * 100f), and 1.05f * 100f rounds to
 * 104.99999f in float, so the first table's key is 104. Hence nonlinear scaling starts at 1.03 (key 104
 * as a scale, minus 0.01), and scales from 1.04 to 1.1 interpolate from 1.04, not 1.05.
 * Not copied: forScale() caches each interpolated table under its key, so on a device a later scale with
 * the same key (1.25 and 1.2549 are both key 125) reuses the first one's table. Here a result depends
 * only on the scale asked for. For the switch values (1, 1.25, 2) the two agree.
 *
 * Inside a mock frame 1 dp = 1 CSS px, so a converted dp value is used directly as px.
 *
 *   PadFontScale.apply(frameEl, scale)  sets --t-<role>-size and --t-<role>-line (px) on frameEl for the
 *                                       type roles in docs/redesign/BRIEF.md; line heights keep their
 *                                       ratio to the size. Returns the sizes it set.
 *   PadFontScale.spToDp(sp, scale)      one value, as TypedValue.applyDimension(COMPLEX_UNIT_SP) gives it
 *   PadFontScale.forScale(scale)        the converter (convertSpToDp / convertDpToSp), or null
 *   PadFontScale.roleSizes(scale)       {role: {sp, line_sp, size, line}} without touching the DOM
 *
 * Works as a classic <script> (window.PadFontScale) and from Node (require) for unit checks.
 */
(function (root, factory) {
  "use strict";
  var api = factory();
  if (typeof module === "object" && module && module.exports) module.exports = api;
  if (root) root.PadFontScale = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  var f32 = Math.fround;

  var SOURCE = "AOSP aosp-mirror/platform_frameworks_base@64130047e019cee612a85dde07755efd8f356f12 " +
    "core/java/android/content/res/FontScaleConverterFactory.java";

  // FontScaleConverterFactory: SCALE_KEY_MULTIPLIER = 100f; every table maps these sp values.
  var SCALE_KEY_MULTIPLIER = 100;
  var COMMON_SP = [8, 10, 12, 14, 18, 20, 24, 30, 100];

  // The static initialiser's tables, verbatim and in source order: [scaleKey, toDp].
  var SOURCE_TABLES = [
    [1.05, [8.4, 10.5, 12.6, 14.8, 18.6, 20.6, 24.4, 30, 100]],
    [1.1, [8.8, 11, 13.2, 15.6, 19.2, 21.2, 24.8, 30, 100]],
    [1.15, [9.2, 11.5, 13.8, 16.4, 19.8, 21.8, 25.2, 30, 100]],
    [1.2, [9.6, 12, 14.4, 17.2, 20.4, 22.4, 25.6, 30, 100]],
    [1.3, [10.4, 13, 15.6, 18.8, 21.6, 23.6, 26.4, 30, 100]],
    [1.5, [12, 15, 18, 22, 24, 26, 28, 30, 100]],
    [1.8, [14.4, 18, 21.6, 24.4, 27.6, 30.8, 32.8, 34.8, 100]],
    [2, [16, 20, 24, 26, 30, 34, 36, 38, 100]],
  ];

  // ---- android.util.MathUtils, in float -----------------------------------------------------------

  function lerp(start, stop, amount) {
    return f32(start + f32(f32(stop - start) * amount));
  }

  function lerpInv(a, b, value) {
    return a !== b ? f32(f32(value - a) / f32(b - a)) : 0;
  }

  function saturate(value) { // constrain(value, 0f, 1f); NaN passes through, as in Java
    return value < 0 ? 0 : (value > 1 ? 1 : value);
  }

  function constrainedMap(rangeMin, rangeMax, valueMin, valueMax, value) {
    return lerp(rangeMin, rangeMax, saturate(lerpInv(valueMin, valueMax, value)));
  }

  // java.util.Arrays.binarySearch(float[], float) and SparseArray's ContainerHelpers.binarySearch:
  // the index of key, or -(insertion point) - 1. Java orders NaN above every number.
  function binarySearch(values, key) {
    var lo = 0;
    var hi = values.length - 1;
    if (key !== key) return -(values.length + 1);
    while (lo <= hi) {
      var mid = (lo + hi) >>> 1;
      var v = values[mid];
      if (v < key) lo = mid + 1;
      else if (v > key) hi = mid - 1;
      else return mid;
    }
    return -(lo + 1);
  }

  // ---- FontScaleConverterImpl -----------------------------------------------------------------------

  function Converter(fromSp, toDp) {
    if (fromSp.length !== toDp.length || fromSp.length === 0) {
      throw new Error("Array lengths must match and be nonzero");
    }
    this.fromSp = Float32Array.from(fromSp);
    this.toDp = Float32Array.from(toDp);
  }

  Converter.prototype.convertSpToDp = function (sp) {
    return lookupAndInterpolate(sp, this.fromSp, this.toDp);
  };

  Converter.prototype.convertDpToSp = function (dp) {
    return lookupAndInterpolate(dp, this.toDp, this.fromSp);
  };

  function lookupAndInterpolate(sourceValue, sourceValues, targetValues) {
    sourceValue = f32(sourceValue);
    var sourceValuePositive = Math.abs(sourceValue);
    var sign = Math.sign(sourceValue); // Math.signum: ±1, ±0 or NaN
    // Exact matches only; anything else is interpolated.
    var index = binarySearch(sourceValues, sourceValuePositive);
    if (index >= 0) return f32(sign * targetValues[index]);

    var lowerIndex = -(index + 1) - 1;
    var startSp, endSp, startDp, endDp;
    var last = sourceValues.length - 1;
    if (lowerIndex >= last) {
      // Past the table: use the last pair's scaling factor (linear beyond the last point).
      startSp = sourceValues[last];
      startDp = targetValues[last];
      if (startSp === 0) return 0;
      var scalingFactor = f32(startDp / startSp);
      return f32(sourceValue * scalingFactor);
    } else if (lowerIndex === -1) {
      // Below the first point: interpolate from 0.
      startSp = 0;
      startDp = 0;
      endSp = sourceValues[0];
      endDp = targetValues[0];
    } else {
      startSp = sourceValues[lowerIndex];
      endSp = sourceValues[lowerIndex + 1];
      startDp = targetValues[lowerIndex];
      endDp = targetValues[lowerIndex + 1];
    }
    return f32(sign * constrainedMap(startDp, endDp, startSp, endSp, sourceValuePositive));
  }

  // ---- FontScaleConverterFactory ---------------------------------------------------------------------

  function getKey(fontScale) { // (int) (fontScale * SCALE_KEY_MULTIPLIER), all in float
    var v = f32(f32(fontScale) * SCALE_KEY_MULTIPLIER);
    if (v !== v) return 0; // (int) NaN == 0
    return Math.max(-2147483648, Math.min(2147483647, Math.trunc(v)));
  }

  function getScaleFromKey(key) { // (float) key / SCALE_KEY_MULTIPLIER
    return f32(key / SCALE_KEY_MULTIPLIER);
  }

  // SparseArray<FontScaleConverter>: parallel arrays kept sorted by key.
  var lookupKeys = [];
  var lookupTables = [];
  SOURCE_TABLES.forEach(function (entry) {
    var key = getKey(entry[0]);
    var i = binarySearch(lookupKeys, key);
    if (i >= 0) {
      lookupTables[i] = new Converter(COMMON_SP, entry[1]);
    } else {
      i = -(i + 1);
      lookupKeys.splice(i, 0, key);
      lookupTables.splice(i, 0, new Converter(COMMON_SP, entry[1]));
    }
  });

  var MIN_SCALE_BEFORE_CURVES_APPLIED = f32(getScaleFromKey(lookupKeys[0]) - f32(0.01));
  if (!(MIN_SCALE_BEFORE_CURVES_APPLIED > 1)) {
    throw new Error("You should only apply non-linear scaling to font scales > 1");
  }

  function isNonLinearFontScalingActive(fontScale) {
    return f32(fontScale) >= MIN_SCALE_BEFORE_CURVES_APPLIED;
  }

  function createInterpolatedTableBetween(start, end, interpolationPoint) {
    var dpInterpolated = new Float32Array(COMMON_SP.length);
    for (var i = 0; i < COMMON_SP.length; i++) {
      var sp = COMMON_SP[i];
      dpInterpolated[i] = lerp(start.convertSpToDp(sp), end.convertSpToDp(sp), interpolationPoint);
    }
    return new Converter(COMMON_SP, dpInterpolated);
  }

  /** The converter Android uses at this font scale, or null when scaling is linear (below 1.03). */
  function forScale(fontScale) {
    fontScale = f32(fontScale);
    if (!isNonLinearFontScalingActive(fontScale)) return null;

    var index = binarySearch(lookupKeys, getKey(fontScale));
    if (index >= 0) return lookupTables[index];

    // No exact match: interpolate between the two nearest tables.
    var lowerIndex = -(index + 1) - 1;
    var higherIndex = lowerIndex + 1;
    if (lowerIndex < 0 || higherIndex >= lookupKeys.length) {
      // Outside the tables: a straight linear table (past its one point, x * fontScale).
      return new Converter([1], [fontScale]);
    }
    var startScale = getScaleFromKey(lookupKeys[lowerIndex]);
    var endScale = getScaleFromKey(lookupKeys[higherIndex]);
    var interpolationPoint = constrainedMap(0, 1, startScale, endScale, fontScale);
    return createInterpolatedTableBetween(lookupTables[lowerIndex], lookupTables[higherIndex], interpolationPoint);
  }

  /** sp -> dp as TypedValue.applyDimension(COMPLEX_UNIT_SP, sp, metrics) with density 1. */
  function spToDp(sp, fontScale) {
    var converter = forScale(fontScale);
    if (converter) return converter.convertSpToDp(sp);
    // scaledDensity = density * (fontScale != 0 ? fontScale : 1)   (ResourcesImpl)
    return f32(f32(sp) * f32(fontScale !== 0 ? fontScale : 1));
  }

  // ---- The preview's type roles ---------------------------------------------------------------------

  // [size sp, line height sp]. The first five are the BRIEF's roles. label-small (11/16) is an extra
  // for the widget's 11 sp app label (plan §6b); it isn't in tokens.css, so use var(--t-label-small-size, 11px).
  var ROLES = {
    display: [36, 44],
    headline: [28, 36],
    title: [20, 28],
    body: [16, 24],
    label: [13, 18],
    "label-small": [11, 16],
  };

  function roleSizes(fontScale) {
    var out = {};
    Object.keys(ROLES).forEach(function (name) {
      var sp = ROLES[name][0];
      var lineSp = ROLES[name][1];
      var size = spToDp(sp, fontScale);
      out[name] = { sp: sp, line_sp: lineSp, size: size, line: lineSp * (size / sp) };
    });
    return out;
  }

  function px(v) {
    return (Math.round(v * 1000) / 1000) + "px";
  }

  /** Writes the role sizes for this scale onto the element as inline custom properties. */
  function apply(frameEl, fontScale) {
    var scale = Number(fontScale);
    if (!(scale > 0) || !isFinite(scale)) {
      if (typeof console !== "undefined") console.warn("PadFontScale.apply: bad scale " + fontScale + ", using 1");
      scale = 1;
    }
    var sizes = roleSizes(scale);
    if (frameEl && frameEl.style && frameEl.style.setProperty) {
      Object.keys(sizes).forEach(function (name) {
        frameEl.style.setProperty("--t-" + name + "-size", px(sizes[name].size));
        frameEl.style.setProperty("--t-" + name + "-line", px(sizes[name].line));
      });
      frameEl.style.setProperty("--font-scale", String(scale));
    }
    return sizes;
  }

  return {
    SOURCE: SOURCE,
    COMMON_SP: COMMON_SP.slice(),
    TABLES: SOURCE_TABLES.map(function (e) { return { scale: e[0], key: getKey(e[0]), toDp: e[1].slice() }; }),
    MIN_SCALE_BEFORE_CURVES_APPLIED: MIN_SCALE_BEFORE_CURVES_APPLIED,
    ROLES: JSON.parse(JSON.stringify(ROLES)),
    forScale: forScale,
    isNonLinearFontScalingActive: isNonLinearFontScalingActive,
    spToDp: spToDp,
    roleSizes: roleSizes,
    apply: apply,
    // For unit checks only.
    _Converter: Converter,
    _getKey: getKey,
  };
});
