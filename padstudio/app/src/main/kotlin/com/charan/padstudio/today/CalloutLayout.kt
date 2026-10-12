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

/** A pill's side and how far its centre is moved up (negative) or down from its marker, in px. */
internal data class PillPlacement(val side: PillSide, val shift: Float)

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
 * The pen's axis from two of its markers, extended to the top by the model's proportions: the nib is
 * 82.5 mm below the middle and the double-tap spot 40 mm above it, on a 165 mm pen with a 10 mm barrel
 * (APP-BRIEF, the marker nodes of focus_pen_pro.glb). Null until both markers are known.
 */
internal fun penBar(markers: Map<String, Offset>): PenBar? {
    val nib = markers[NIB] ?: return null
    val tap = markers[DOUBLE_TAP] ?: return null
    val axis = tap - nib
    if (axis.getDistance() < 1f) return null
    val top = nib + axis * (PEN_LENGTH_MM / NIB_TO_DOUBLE_TAP_MM)
    return PenBar(nib, top, (top - nib).getDistance() * BARREL_MM / 2f / PEN_LENGTH_MM)
}

/** The pill of [size] for a marker at [anchor], placed as [placement]. */
internal fun pillRect(anchor: Offset, size: Size, placement: PillPlacement, metrics: CalloutMetrics): Rect {
    val reach = metrics.dotRadius + metrics.leader
    val left = when (placement.side) {
        PillSide.START -> anchor.x - reach - size.width
        PillSide.END -> anchor.x + reach
    }
    val top = anchor.y + placement.shift - size.height / 2f
    return Rect(left, top, left + size.width, top + size.height)
}

/**
 * Places one pill per anchor inside the [stage], or says they don't fit (null): each pill stays [inset]
 * from the stage edge, [gap] clear of the other pills, of the [obstacles] (the Pause button) and of the
 * pen's barrel. Each pill tries its [preferred] side first, then the other side, then the same with its
 * centre moved up or down by whole [CalloutMetrics.shiftStep]s; the first combination that fits wins, so a
 * level leader is chosen whenever one fits.
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
    val bounds = Rect(metrics.inset, metrics.inset, stage.width - metrics.inset, stage.height - metrics.inset)
    if (bounds.width <= 0f || bounds.height <= 0f) return null
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

    fun clear(rect: Rect): Boolean =
        rect.left >= bounds.left && rect.top >= bounds.top && rect.right <= bounds.right && rect.bottom <= bounds.bottom &&
            obstacles.none { it.inflate(metrics.gap).overlaps(rect) } &&
            (pen == null || capsuleDistance(pen.nib, pen.top, rect) >= pen.halfWidth + metrics.gap)

    fun place(index: Int, placed: List<Rect>): List<PillPlacement>? {
        if (index == anchors.size) return emptyList()
        for (candidate in candidates[index]) {
            val rect = pillRect(anchors[index], pills[index], candidate, metrics)
            if (!clear(rect) || placed.any { it.inflate(metrics.gap).overlaps(rect) }) continue
            place(index + 1, placed + rect)?.let { return listOf(candidate) + it }
        }
        return null
    }
    return place(0, emptyList())
}

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
private const val PEN_LENGTH_MM = 165f
private const val NIB_TO_DOUBLE_TAP_MM = 122.5f
private const val BARREL_MM = 10f
