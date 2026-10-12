package com.charan.padstudio.pen.screen

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Where the binding chips go around the pen drawing (plan §10 "Pen": callouts). Plain geometry in the stage's
// pixels, so it can be tested without a device. The pen comes from PenModelView's marker offsets, never from
// positions written here: its axis runs from marker_nib to marker_double_tap. The screen calls this with a
// steady copy of the markers (not every animated frame), so the chips don't move or flip while the pen sways.

/** A point in the stage, in pixels. */
data class StagePoint(val x: Float, val y: Float)

/** A rectangle in the stage, in pixels. */
data class StageRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun intersects(other: StageRect): Boolean = left < other.right && other.left < right && top < other.bottom && other.top < bottom
}

/** A chip to place: its measured size and the marker it points at. */
data class CalloutChip(val id: String, val width: Int, val height: Int, val anchor: StagePoint)

/**
 * Where a chip goes: its top-left corner and size. [beforePen] chips sit on the pen's left (the side its nib
 * points to); their leader line leaves from the right edge, the others' from the left edge.
 */
data class CalloutSpot(val id: String, val x: Int, val y: Int, val width: Int, val height: Int, val beforePen: Boolean) {
    /** Where the leader line meets the chip: the middle of the edge facing the pen. */
    val lineEnd: StagePoint get() = StagePoint(if (beforePen) (x + width).toFloat() else x.toFloat(), y + height / 2f)

    val rect: StageRect get() = StageRect(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())
}

/**
 * The spacing rules, in pixels: [margin] from the stage's edges, [gap] between chips, [lead] between a chip and
 * the pen's outline, the pen's [penHalfWidth] (perpendicular to its axis), and [obstacles] chips must not cover
 * (the animation's pause button).
 */
data class CalloutRules(
    val margin: Int,
    val gap: Int,
    val lead: Int,
    val penHalfWidth: Float,
    val obstacles: List<StageRect> = emptyList(),
)

/**
 * Places [chips] in a [width] × [height] stage beside the pen whose axis runs through [axisFrom] and [axisTo],
 * or returns null when they can't all fit without covering the pen, each other or an obstacle; the screen then
 * lists the bindings instead (the W1 note on callouts at large text).
 *
 * Chips keep the vertical order of their markers and sit as near their marker's height as the [CalloutRules.gap]
 * allows. All of them go on the pen's left if they fit, else all on its right, else each on the side with room
 * at its marker. On a side, chips line up in one column like the mock-up's when that fits, else each hugs the pen.
 */
fun placeCallouts(
    width: Int,
    height: Int,
    chips: List<CalloutChip>,
    axisFrom: StagePoint,
    axisTo: StagePoint,
    rules: CalloutRules,
): List<CalloutSpot>? {
    if (chips.isEmpty()) return emptyList()
    if (width <= 0 || height <= 0) return null
    val axis = PenAxis.of(axisFrom, axisTo, rules.penHalfWidth) ?: return null
    val sorted = chips.sortedBy { it.anchor.y }
    val fitsBefore = sorted.map { chip -> axis.roomBefore(chip.anchor.y, chip.height, rules) >= chip.width }
    val fitsAfter = sorted.map { chip -> axis.roomAfter(chip.anchor.y, chip.height, width, rules) >= chip.width }
    val assignments = listOf(
        sorted.map { true },
        sorted.map { false },
        sorted.indices.map { i -> fitsBefore[i] || !fitsAfter[i] },
        sorted.indices.map { i -> !fitsAfter[i] },
    ).distinct()
    for (assignment in assignments) {
        arrange(sorted, assignment, axis, width, height, rules)?.let { return it }
    }
    return null
}

/** The pen as a straight band: its axis and its half-width measured across a horizontal line. */
private class PenAxis private constructor(private val from: StagePoint, private val slope: Float, val halfAcross: Float) {
    /** Where the axis crosses the height [y]. */
    fun x(y: Float): Float = from.x + (y - from.y) * slope

    /** How wide a chip of [chipHeight] centred at [centerY] may be on the pen's left. */
    fun roomBefore(centerY: Float, chipHeight: Int, rules: CalloutRules): Float =
        rightLimit(centerY - chipHeight / 2f, centerY + chipHeight / 2f, rules) - rules.margin

    /** How wide such a chip may be on the pen's right in a stage [width] wide. */
    fun roomAfter(centerY: Float, chipHeight: Int, width: Int, rules: CalloutRules): Float =
        width - rules.margin - leftLimit(centerY - chipHeight / 2f, centerY + chipHeight / 2f, rules)

    /** The furthest right a chip spanning [top]..[bottom] may reach on the pen's left. */
    fun rightLimit(top: Float, bottom: Float, rules: CalloutRules): Float = min(x(top), x(bottom)) - halfAcross - rules.lead

    /** The furthest left a chip spanning [top]..[bottom] may start on the pen's right. */
    fun leftLimit(top: Float, bottom: Float, rules: CalloutRules): Float = max(x(top), x(bottom)) + halfAcross + rules.lead

    companion object {
        /** Null for a pen lying flat (no usable axis), which the screen shows as a list. */
        fun of(from: StagePoint, to: StagePoint, halfWidth: Float): PenAxis? {
            val dy = to.y - from.y
            if (abs(dy) < 1f) return null
            val slope = (to.x - from.x) / dy
            return PenAxis(from, slope, halfWidth * sqrt(1f + slope * slope))
        }
    }
}

/** One try: the chips on the sides [beforePen] gives them (in [sorted] order), or null if that doesn't fit. */
private fun arrange(
    sorted: List<CalloutChip>,
    beforePen: List<Boolean>,
    axis: PenAxis,
    width: Int,
    height: Int,
    rules: CalloutRules,
): List<CalloutSpot>? {
    val spots = ArrayList<CalloutSpot>(sorted.size)
    for (before in listOf(true, false)) {
        val side = sorted.filterIndexed { i, _ -> beforePen[i] == before }
        if (side.isEmpty()) continue
        val tops = stack(
            desired = side.map { it.anchor.y - it.height / 2f },
            heights = side.map { it.height },
            minTop = rules.margin.toFloat(),
            maxBottom = (height - rules.margin).toFloat(),
            gap = rules.gap.toFloat(),
        ) ?: return null
        // Before the pen: the edge is each chip's right edge; after it, each chip's left edge.
        val limits = side.mapIndexed { i, chip ->
            val bottom = tops[i] + chip.height
            if (before) axis.rightLimit(tops[i], bottom, rules) else axis.leftLimit(tops[i], bottom, rules)
        }
        val column = if (before) limits.min() else limits.max()
        val columnFits = side.all { chip -> fitsAcross(before, column, chip.width, width, rules) }
        side.forEachIndexed { i, chip ->
            val edge = if (columnFits) column else limits[i]
            if (!fitsAcross(before, edge, chip.width, width, rules)) return null
            val left = if (before) edge - chip.width else edge
            spots += CalloutSpot(chip.id, left.roundToInt(), tops[i].roundToInt(), chip.width, chip.height, before)
        }
    }
    if (spots.any { spot -> rules.obstacles.any { it.intersects(spot.rect) } }) return null
    return spots
}

private fun fitsAcross(before: Boolean, edge: Float, chipWidth: Int, width: Int, rules: CalloutRules): Boolean =
    if (before) edge - chipWidth >= rules.margin else edge + chipWidth <= width - rules.margin

/**
 * Tops for boxes of [heights] that want to start at [desired] (in order), at least [gap] apart and within
 * [minTop]..[maxBottom]: pushed down past each other, then up from the bottom. Null when they don't fit.
 */
internal fun stack(desired: List<Float>, heights: List<Int>, minTop: Float, maxBottom: Float, gap: Float): List<Float>? {
    val tops = desired.toFloatArray()
    for (i in tops.indices) {
        val floor = if (i == 0) minTop else tops[i - 1] + heights[i - 1] + gap
        if (tops[i] < floor) tops[i] = floor
    }
    for (i in tops.indices.reversed()) {
        val ceiling = if (i == tops.lastIndex) maxBottom - heights[i] else tops[i + 1] - gap - heights[i]
        if (tops[i] > ceiling) tops[i] = ceiling
    }
    return if (tops.isEmpty() || tops[0] >= minTop) tops.toList() else null
}
