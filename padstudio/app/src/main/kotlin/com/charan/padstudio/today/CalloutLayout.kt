package com.charan.padstudio.today

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.hypot

// Where the hero's callout pills go (unit-tested in CalloutLayoutTest). Everything is in the pen stage's
// pixels: the anchors are PenModelView's onMarkers offsets, never positions written down here
// (SCREENS-BRIEF: the marker layout is a design choice of the model, so it is always read from the view).

/** Which side of its marker a pill sits on, joined to it by a short leader. */
internal enum class PillSide {
    START,
    END,
    ;

    val other: PillSide get() = if (this == START) END else START
}

/**
 * A pill's side, how far its centre is moved up (negative) or down from its marker, and how much farther than
 * the usual dot and leader its near edge stands from the marker ([push], in px; only a column of pills sets it,
 * to line their edges up and to clear the pen).
 */
internal data class PillPlacement(val side: PillSide, val shift: Float, val push: Float = 0f)

/** The callout drawing's measures, in px: marker dot radius, leader length, keep-out inset at the stage edge, gap between things, and the vertical shifts tried. */
internal data class CalloutMetrics(
    val dotRadius: Float,
    val leader: Float,
    val inset: Float,
    val gap: Float,
    val shiftStep: Float,
    val shiftSteps: Int,
)

/** The pen as a thick line from its nib to its top, to keep pills off it. */
internal data class PenBar(val nib: Offset, val top: Offset, val halfWidth: Float)

/**
 * The pen's axis, from the nib to its top: the top is `marker_top`, the end cap, when the model has it, else
 * `marker_double_tap` (a model without the cap marker, which round 1's was). The barrel's half-width is the
 * pen's length times 5 mm over 165 mm: the Focus Pen Pro's barrel radius and length (ROUND2-BRIEF's marker
 * table, focus_pen_pro.glb). Null until the nib and a top are known.
 */
internal fun penBar(markers: Map<String, Offset>): PenBar? {
    val nib = markers[NIB] ?: return null
    val top = markers[TOP] ?: markers[DOUBLE_TAP] ?: return null
    val length = (top - nib).getDistance()
    if (length < 1f) return null
    return PenBar(nib, top, length * BARREL_RADIUS_MM / PEN_LENGTH_MM)
}

/** The pill of [size] for a marker at [anchor], placed as [placement]. */
internal fun pillRect(anchor: Offset, size: Size, placement: PillPlacement, metrics: CalloutMetrics): Rect {
    val reach = metrics.dotRadius + metrics.leader + placement.push
    val left = when (placement.side) {
        PillSide.START -> anchor.x - reach - size.width
        PillSide.END -> anchor.x + reach
    }
    val top = anchor.y + placement.shift - size.height / 2f
    return Rect(left, top, left + size.width, top + size.height)
}

/**
 * The hero's callouts: all the pills in one fanned column when that fits ([fanCallouts]), else each pill on
 * its own side as [fitCallouts] places it. Null when neither fits, so the screen tries its next arrangement.
 */
internal fun placeCallouts(
    stage: Size,
    anchors: List<Offset>,
    pills: List<Size>,
    preferred: List<PillSide>,
    obstacles: List<Rect>,
    pen: PenBar?,
    metrics: CalloutMetrics,
): List<PillPlacement>? =
    fanCallouts(stage, anchors, pills, obstacles, pen, metrics)
        ?: fitCallouts(stage, anchors, pills, preferred, obstacles, pen, metrics)

/**
 * Puts all the pills in ONE vertical column on the side of the pen with more room, or says it doesn't fit
 * (null). The gesture markers sit close together in the grip band, so pills level with each marker would
 * collide; in a column they stack, [CalloutMetrics.gap] apart:
 * - the column is centred on the mean height of the markers, its pills in the order of their markers' heights
 *   (the higher marker's pill on top, equal heights in the callouts' own order), so the leaders fan out from
 *   the grip without crossing;
 * - its near edge is a dot and a leader beyond the marker nearest that side, so every leader is at least the
 *   usual length and the pills' edges line up. If the pen's barrel is too close there, the whole column moves
 *   outward in half-gap steps until it clears. No leader grows more than two leader lengths beyond the usual
 *   (counting the markers' own spread across the column), so markers that are far apart get no column;
 * - the side with more room between its outermost marker and the stage's inset edge is tried first, then the
 *   other; a tie goes to [PillSide.END].
 * Every pill must stay inside the [stage] inset, a [CalloutMetrics.gap] clear of the [obstacles] and of the
 * pen, as in [fitCallouts]. A single pill has no column: null.
 */
internal fun fanCallouts(
    stage: Size,
    anchors: List<Offset>,
    pills: List<Size>,
    obstacles: List<Rect>,
    pen: PenBar?,
    metrics: CalloutMetrics,
): List<PillPlacement>? {
    require(anchors.size == pills.size) { "one anchor and pill size per callout" }
    if (anchors.size < 2) return null
    val bounds = stageBounds(stage, metrics) ?: return null
    val order = anchors.indices.sortedBy { anchors[it].y }
    val columnHeight = pills.sumOf { it.height.toDouble() }.toFloat() + metrics.gap * (pills.size - 1)
    var top = (anchors.sumOf { it.y.toDouble() } / anchors.size).toFloat() - columnHeight / 2f
    val centres = FloatArray(pills.size)
    for (index in order) {
        centres[index] = top + pills[index].height / 2f
        top += pills[index].height + metrics.gap
    }
    val leftmost = anchors.minOf { it.x }
    val rightmost = anchors.maxOf { it.x }
    val spread = rightmost - leftmost
    val sides = if (bounds.right - rightmost >= leftmost - bounds.left) listOf(PillSide.END, PillSide.START) else listOf(PillSide.START, PillSide.END)
    val step = (metrics.gap / 2f).coerceAtLeast(1f)
    for (side in sides) {
        var out = 0f
        while (spread + out <= 2f * metrics.leader) {
            val placements = anchors.indices.map { i ->
                val flush = if (side == PillSide.END) rightmost - anchors[i].x else anchors[i].x - leftmost
                PillPlacement(side, centres[i] - anchors[i].y, flush + out)
            }
            if (anchors.indices.all { isClear(pillRect(anchors[it], pills[it], placements[it], metrics), bounds, obstacles, pen, metrics) }) return placements
            out += step
        }
    }
    return null
}

/**
 * Places one pill per anchor inside the [stage], or says they don't fit (null): each pill stays [inset]
 * from the stage edge, [gap] clear of the other pills, of the [obstacles] (the Pause button) and of the
 * pen's barrel. Each pill tries its [preferred] side first, then the other side, then the same with its
 * centre moved up or down by whole [CalloutMetrics.shiftStep]s; the first combination that fits wins, so a
 * level leader is chosen whenever one fits. [placeCallouts] falls back to this when the pills don't fit in
 * a column.
 */
internal fun fitCallouts(
    stage: Size,
    anchors: List<Offset>,
    pills: List<Size>,
    preferred: List<PillSide>,
    obstacles: List<Rect>,
    pen: PenBar?,
    metrics: CalloutMetrics,
): List<PillPlacement>? {
    require(anchors.size == pills.size && pills.size == preferred.size) { "one anchor, pill size and side per callout" }
    val bounds = stageBounds(stage, metrics) ?: return null
    val shifts = buildList {
        add(0f)
        for (k in 1..metrics.shiftSteps) {
            add(-k * metrics.shiftStep)
            add(k * metrics.shiftStep)
        }
    }
    val candidates = anchors.indices.map { i ->
        shifts.flatMap { shift -> listOf(PillPlacement(preferred[i], shift), PillPlacement(preferred[i].other, shift)) }
    }

    fun place(index: Int, placed: List<Rect>): List<PillPlacement>? {
        if (index == anchors.size) return emptyList()
        for (candidate in candidates[index]) {
            val rect = pillRect(anchors[index], pills[index], candidate, metrics)
            if (!isClear(rect, bounds, obstacles, pen, metrics) || placed.any { it.inflate(metrics.gap).overlaps(rect) }) continue
            place(index + 1, placed + rect)?.let { return listOf(candidate) + it }
        }
        return null
    }
    return place(0, emptyList())
}

/** The stage less its keep-out [CalloutMetrics.inset]; null when nothing is left. */
private fun stageBounds(stage: Size, metrics: CalloutMetrics): Rect? =
    Rect(metrics.inset, metrics.inset, stage.width - metrics.inset, stage.height - metrics.inset).takeIf { it.width > 0f && it.height > 0f }

/** Whether a pill at [rect] is inside the [bounds], clear of the [obstacles] and clear of the [pen]'s barrel. */
private fun isClear(rect: Rect, bounds: Rect, obstacles: List<Rect>, pen: PenBar?, metrics: CalloutMetrics): Boolean =
    rect.left >= bounds.left && rect.top >= bounds.top && rect.right <= bounds.right && rect.bottom <= bounds.bottom &&
        obstacles.none { it.inflate(metrics.gap).overlaps(rect) } &&
        (pen == null || capsuleDistance(pen.nib, pen.top, rect) >= pen.halfWidth + metrics.gap)

/** Whether any of [names] moved more than [threshold] px between [before] and [now] (or appeared or went). */
internal fun markersMoved(before: Map<String, Offset>, now: Map<String, Offset>, names: List<String>, threshold: Float): Boolean =
    names.any { name ->
        val a = before[name]
        val b = now[name]
        when {
            a == null || b == null -> a != b
            else -> abs(a.x - b.x) > threshold || abs(a.y - b.y) > threshold
        }
    }

/**
 * How far the segment [a]–[b] stays from a pill drawn as a capsule (fully rounded ends) in [rect]: the
 * distance to the capsule's core line less its radius; 0 or less when they touch. A capsule's ends curve
 * away, so this lets a pill sit level beside a slanted pen where its bounding box would graze it.
 */
internal fun capsuleDistance(a: Offset, b: Offset, rect: Rect): Float {
    val radius = minOf(rect.width, rect.height) / 2f
    val center = rect.center
    val (coreStart, coreEnd) = if (rect.width >= rect.height) {
        Offset(rect.left + radius, center.y) to Offset(rect.right - radius, center.y)
    } else {
        Offset(center.x, rect.top + radius) to Offset(center.x, rect.bottom - radius)
    }
    return segmentDistance(a, b, coreStart, coreEnd) - radius
}

/** The shortest distance between the segments [a]–[b] and [c]–[d]; 0 when they cross. */
internal fun segmentDistance(a: Offset, b: Offset, c: Offset, d: Offset): Float {
    if (segmentsCross(a, b, c, d)) return 0f
    return minOf(pointToSegment(a, c, d), pointToSegment(b, c, d), pointToSegment(c, a, b), pointToSegment(d, a, b))
}

private fun segmentsCross(a: Offset, b: Offset, c: Offset, d: Offset): Boolean {
    fun cross(o: Offset, p: Offset, q: Offset) = (p.x - o.x) * (q.y - o.y) - (p.y - o.y) * (q.x - o.x)
    val d1 = cross(c, d, a)
    val d2 = cross(c, d, b)
    val d3 = cross(a, b, c)
    val d4 = cross(a, b, d)
    return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) && ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))
}

private fun pointToSegment(point: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val lengthSquared = ab.x * ab.x + ab.y * ab.y
    if (lengthSquared == 0f) return (point - a).getDistance()
    val t = (((point.x - a.x) * ab.x + (point.y - a.y) * ab.y) / lengthSquared).coerceIn(0f, 1f)
    return hypot(point.x - (a.x + ab.x * t), point.y - (a.y + ab.y * t))
}

internal const val NIB = "marker_nib"
internal const val DOUBLE_TAP = "marker_double_tap"

/** The end cap (round 2): the pen's top, so its axis never assumes a length. */
internal const val TOP = "marker_top"
private const val PEN_LENGTH_MM = 165f
private const val BARREL_RADIUS_MM = 5f
