package com.charan.padstudio.pen.screen

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Where the binding chips go around the pen drawing (plan §10 "Pen": callouts). Plain geometry in the stage's
// pixels, so it can be tested without a device. The pen comes from PenModelView's marker offsets, never from
// positions written here: its axis runs from marker_nib to marker_top (marker_double_tap in a model without it).
// The screen calls this with a steady copy of the markers (not every animated frame), so the chips don't move
// or flip while the pen sways.

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
 * The first choice is the fan (round 2: the gesture markers sit together in the pen's grip band, closer than a
 * chip is tall): ALL the chips in ONE column on the side of the pen's axis with more horizontal room, in the
 * order of their markers' heights, [CalloutRules.gap] apart and clear of the pen's half-width band (and its
 * [CalloutRules.lead]), the column centred on the markers' mean height. Their leader lines then fan out from the
 * markers to it. The centring gives way only as far as the stage's margins need. When the column doesn't fit the
 * stage, or covers an obstacle, the arrangements below are tried.
 *
 * In those, chips keep the vertical order of their markers and sit as near their marker's height as the
 * [CalloutRules.gap] allows. All of them go on the pen's left if they fit, else all on its right, else each on the
 * side with room at its marker, else any other split between the sides (fewest changes of side first), so a layout
 * that fits is always found and narrower chips never fit worse than wider ones. On a side, chips line up in one
 * column like the mock-up's when that fits, else each hugs the pen.
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
    fannedColumn(sorted, axis, width, height, rules)?.let { return it }
    val fitsBefore = sorted.map { chip -> axis.roomBefore(chip.anchor.y, chip.height, rules) >= chip.width }
    val fitsAfter = sorted.map { chip -> axis.roomAfter(chip.anchor.y, chip.height, width, rules) >= chip.width }
    val assignments = (
        listOf(
            sorted.map { true },
            sorted.map { false },
            sorted.indices.map { i -> fitsBefore[i] || !fitsAfter[i] },
            sorted.indices.map { i -> !fitsAfter[i] },
        ) + otherSplits(sorted.size)
        ).distinct()
    for (assignment in assignments) {
        arrange(sorted, assignment, axis, width, height, rules)?.let { return it }
    }
    return null
}

/**
 * Every way to share [count] chips (top to bottom) between the sides, true for before the pen: one change of side
 * first, then two, and so on; then more chips before the pen; then the top chip before it. A pen has at most four
 * callouts (16 ways); more than [MAX_SPLIT_SEARCH] get none.
 */
private fun otherSplits(count: Int): List<List<Boolean>> {
    if (count > MAX_SPLIT_SEARCH) return emptyList()
    return (0 until (1 shl count))
        .map { bits -> List(count) { i -> bits and (1 shl i) != 0 } }
        .sortedWith(
            compareBy<List<Boolean>>(
                { sides -> sides.zipWithNext().count { (a, b) -> a != b } },
                { sides -> -sides.count { it } },
                { sides -> if (sides.first()) 0 else 1 },
            ),
        )
}

private const val MAX_SPLIT_SEARCH = 6

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
    if (coversObstacle(spots, rules)) return null
    return spots
}

/**
 * The fan: every chip of [sorted] (by marker height) in one column on the side of the pen with more room across
 * the column's height, or null when that column doesn't fit the stage or covers an obstacle. The column is a block,
 * [CalloutRules.gap] between its chips, centred on the markers' mean height, then moved up or down only as far as
 * the stage's margins need ([stack]; null when it is taller than the stage). Everything is whole pixels before
 * the pen's band is measured: the column's edge nearest the pen is as near as the band and the lead allow, rounded
 * away from the pen, so no chip is ever closer than the rules say.
 */
private fun fannedColumn(
    sorted: List<CalloutChip>,
    axis: PenAxis,
    width: Int,
    height: Int,
    rules: CalloutRules,
): List<CalloutSpot>? {
    val heights = sorted.map { it.height }
    val column = heights.sum() + rules.gap * (sorted.size - 1)
    val centre = sorted.map { it.anchor.y }.average().toFloat()
    val first = stack(listOf(centre - column / 2f), listOf(column), rules.margin.toFloat(), (height - rules.margin).toFloat(), 0f)
        ?.single()?.roundToInt() ?: return null
    val tops = heights.runningFold(first) { top, h -> top + h + rules.gap }
    val rightLimit = axis.rightLimit(first.toFloat(), (first + column).toFloat(), rules)
    val leftLimit = axis.leftLimit(first.toFloat(), (first + column).toFloat(), rules)
    val before = rightLimit - rules.margin >= width - rules.margin - leftLimit
    val edge = if (before) rightLimit else leftLimit
    if (sorted.any { chip -> !fitsAcross(before, edge, chip.width, width, rules) }) return null
    val spots = sorted.mapIndexed { i, chip ->
        val left = if (before) floor(edge - chip.width) else ceil(edge)
        CalloutSpot(chip.id, left.toInt(), tops[i], chip.width, chip.height, before)
    }
    return if (coversObstacle(spots, rules)) null else spots
}

private fun coversObstacle(spots: List<CalloutSpot>, rules: CalloutRules): Boolean =
    spots.any { spot -> rules.obstacles.any { it.intersects(spot.rect) } }

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
