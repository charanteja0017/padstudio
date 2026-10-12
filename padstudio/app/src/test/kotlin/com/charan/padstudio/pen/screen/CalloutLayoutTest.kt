package com.charan.padstudio.pen.screen

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where the callouts go, on synthetic anchors clustered like round 2's grip band: the nib at (300, 520), the end
 * cap at (520, 120) and the gesture markers 26 to 48 mm up the 165 mm pen, which is 16 to 29 % of the way from nib
 * to cap. Those four sit within 55 px of each other and a chip is 61 px tall (125 % bold text), so the chips can't
 * each stand at their marker's height: they fan out from the cluster in one column.
 */
class CalloutLayoutTest {
    private val nib = StagePoint(300f, 520f)
    private val top = StagePoint(520f, 120f)
    private val topToBottom = listOf("double_tap", "slide_up", "slide_down", "pinch")

    /** The point [mm] up the 165 mm pen from its nib [from] towards its end cap [to]. */
    private fun along(mm: Float, from: StagePoint, to: StagePoint) =
        StagePoint(from.x + (to.x - from.x) * mm / PEN_MM, from.y + (to.y - from.y) * mm / PEN_MM)

    /** The four controls' anchors, listed out of top-to-bottom order on purpose: the order the chips come out in is the layout's doing. */
    private fun grip(from: StagePoint = nib, to: StagePoint = top) = linkedMapOf(
        "slide_up" to along(41f, from, to),
        "pinch" to along(26f, from, to),
        "double_tap" to along(48f, from, to),
        "slide_down" to along(33f, from, to),
    )

    private fun chips(width: Int, height: Int = 61, at: Map<String, StagePoint> = grip()) =
        at.map { (id, anchor) -> CalloutChip(id, width, height, anchor) }

    /** The rules as PenStage sets them for a pen from [from] to [to]: 5 mm of its 165 mm is its half-width. */
    private fun rulesFor(from: StagePoint = nib, to: StagePoint = top, obstacles: List<StageRect> = emptyList()) = CalloutRules(
        margin = 12,
        gap = 8,
        lead = 20,
        penHalfWidth = 5f / PEN_MM * hypot(to.x - from.x, to.y - from.y),
        obstacles = obstacles,
    )

    /** The pen as a band, worked out here on its own: its axis, widened by the half-width measured across a horizontal line. */
    private class PenBand(private val from: StagePoint, to: StagePoint, private val rules: CalloutRules) {
        private val slope = (to.x - from.x) / (to.y - from.y)
        private val across = rules.penHalfWidth * sqrt(1f + slope * slope)

        private fun axisX(y: Float) = from.x + (y - from.y) * slope

        /** The furthest right a chip spanning [upper]..[lower] may reach on the pen's left. */
        fun leftReach(upper: Float, lower: Float) = min(axisX(upper), axisX(lower)) - across - rules.lead

        /** The furthest left a chip spanning [upper]..[lower] may start on the pen's right. */
        fun rightReach(upper: Float, lower: Float) = max(axisX(upper), axisX(lower)) + across + rules.lead
    }

    private fun assertNoOverlaps(spots: List<CalloutSpot>) {
        for (a in spots) for (b in spots) if (a !== b) assertTrue(!a.rect.intersects(b.rect), "${a.id} overlaps ${b.id}")
    }

    /** Every chip inside the stage, a margin from its edges. */
    private fun assertInside(spots: List<CalloutSpot>, width: Int, height: Int, rules: CalloutRules) {
        spots.forEach { spot ->
            assertTrue(spot.x >= rules.margin && spot.x + spot.width <= width - rules.margin, "${spot.id} leaves the stage across: $spot")
            assertTrue(spot.y >= rules.margin && spot.y + spot.height <= height - rules.margin, "${spot.id} leaves the stage down: $spot")
        }
    }

    /**
     * Every chip outside the pen's band and its lead. The default [slack] is float noise, not rounding: the fan
     * rounds away from the pen, so a chip rounded towards it fails. The older arrangements round to the nearest pixel.
     */
    private fun assertClearOfPen(spots: List<CalloutSpot>, band: PenBand, slack: Float = NOISE) {
        spots.forEach { spot ->
            val upper = spot.y.toFloat()
            val lower = (spot.y + spot.height).toFloat()
            if (spot.beforePen) {
                assertTrue(spot.x + spot.width <= band.leftReach(upper, lower) + slack, "${spot.id} is too near the pen: $spot")
            } else {
                assertTrue(spot.x >= band.rightReach(upper, lower) - slack, "${spot.id} is too near the pen: $spot")
            }
        }
    }

    /** True when the pen's left has more room than its right across the height of [spots]. */
    private fun leftIsRoomier(spots: List<CalloutSpot>, width: Int, band: PenBand, rules: CalloutRules): Boolean {
        val upper = spots.minOf { it.y }.toFloat()
        val lower = spots.maxOf { it.y + it.height }.toFloat()
        val left = band.leftReach(upper, lower) - rules.margin
        val right = width - rules.margin - band.rightReach(upper, lower)
        assertTrue(left != right, "the scene should have a roomier side")
        return left > right
    }

    /** One column on one side: the chips' edges nearest the pen line up and each is a gap below the one above. */
    private fun assertColumn(spots: List<CalloutSpot>, rules: CalloutRules) {
        val before = spots.first().beforePen
        assertTrue(spots.all { it.beforePen == before }, "all on one side of the pen")
        val edges = spots.map { if (before) it.x + it.width else it.x }
        assertEquals(1, edges.distinct().size, "the chips line up in one column: $edges")
        spots.sortedBy { it.y }.zipWithNext { upper, lower ->
            assertEquals(rules.gap, lower.y - (upper.y + upper.height), "${upper.id} and ${lower.id} are a gap apart")
        }
    }

    /** The column's middle is at the anchors' mean height (to the pixel, which is all rounding allows). */
    private fun assertCentred(spots: List<CalloutSpot>, anchors: Collection<StagePoint>) {
        val byY = spots.sortedBy { it.y }
        val middle = (byY.first().y + byY.last().y + byY.last().height) / 2.0
        assertEquals(anchors.map { it.y }.average(), middle, 1.0, "centred on the anchors' mean height")
    }

    @Test fun `the chips fan out in one column on the pen's roomier side`() {
        // The pen leans right and the grip band is low on it: in a 640 wide stage the left has more room than the right.
        val rules = rulesFor()
        val band = PenBand(nib, top, rules)
        val spots = assertNotNull(placeCallouts(640, 640, chips(170), nib, top, rules))
        assertEquals(4, spots.size)
        assertTrue(leftIsRoomier(spots, 640, band, rules), "this scene has more room on the left")
        assertTrue(spots.all { it.beforePen }, "so the column is on the left")
        assertColumn(spots, rules)
        assertCentred(spots, grip().values)
        assertNoOverlaps(spots)
        assertInside(spots, 640, 640, rules)
        assertClearOfPen(spots, band)
    }

    @Test fun `with more room on the right the column goes to the right`() {
        // The same pen in a 900 wide stage.
        val rules = rulesFor()
        val band = PenBand(nib, top, rules)
        val spots = assertNotNull(placeCallouts(900, 640, chips(170), nib, top, rules))
        assertEquals(4, spots.size)
        assertTrue(!leftIsRoomier(spots, 900, band, rules), "this scene has more room on the right")
        assertTrue(spots.none { it.beforePen }, "so the column is on the right")
        assertColumn(spots, rules)
        assertCentred(spots, grip().values)
        assertNoOverlaps(spots)
        assertInside(spots, 900, 640, rules)
        assertClearOfPen(spots, band)
    }

    @Test fun `a pen leaning the other way gets its column on the right`() {
        // The scene mirrored in a 640 wide stage: nib at (340, 520), end cap at (120, 120).
        val from = StagePoint(340f, 520f)
        val to = StagePoint(120f, 120f)
        val rules = rulesFor(from, to)
        val band = PenBand(from, to, rules)
        val spots = assertNotNull(placeCallouts(640, 640, chips(170, at = grip(from, to)), from, to, rules))
        assertTrue(!leftIsRoomier(spots, 640, band, rules), "this scene has more room on the right")
        assertTrue(spots.none { it.beforePen })
        assertColumn(spots, rules)
        assertCentred(spots, grip(from, to).values)
        assertNoOverlaps(spots)
        assertInside(spots, 640, 640, rules)
        assertClearOfPen(spots, band)
    }

    @Test fun `the chips are in the order of their anchors' heights`() {
        val input = chips(170)
        assertTrue(input.map { it.id } != topToBottom, "the input is not already in order")
        listOf(640, 900).forEach { width ->
            listOf(input, input.reversed()).forEach { given ->
                val spots = assertNotNull(placeCallouts(width, 640, given, nib, top, rulesFor()))
                assertEquals(topToBottom, spots.sortedBy { it.y }.map { it.id }, "$width wide, given ${given.map { it.id }}")
            }
        }
    }

    @Test fun `the column is centred on the anchors' mean height whatever the chips' sizes`() {
        val rules = rulesFor()
        val anchors = grip()
        val mixed = listOf(
            CalloutChip("double_tap", 200, 61, anchors.getValue("double_tap")),
            CalloutChip("slide_up", 120, 48, anchors.getValue("slide_up")),
            CalloutChip("slide_down", 160, 61, anchors.getValue("slide_down")),
            CalloutChip("pinch", 140, 48, anchors.getValue("pinch")),
        )
        val spots = assertNotNull(placeCallouts(640, 640, mixed, nib, top, rules))
        assertColumn(spots, rules)
        assertCentred(spots, anchors.values)
        assertNoOverlaps(spots)
        // One chip stands level with its anchor.
        val only = anchors.getValue("slide_up")
        val single = assertNotNull(placeCallouts(640, 640, listOf(CalloutChip("slide_up", 170, 61, only)), nib, top, rules))
        assertCentred(single, listOf(only))
    }

    @Test fun `a column that centring would push off the stage is moved inside it`() {
        // Centred on the anchors the column spans y 296 to 564, so a 560 tall stage has to take it up to 548.
        val rules = rulesFor()
        val band = PenBand(nib, top, rules)
        listOf(560, 550, 540).forEach { height ->
            val spots = assertNotNull(placeCallouts(640, height, chips(170), nib, top, rules), "$height tall")
            assertEquals(4, spots.size)
            assertColumn(spots, rules)
            assertInside(spots, 640, height, rules)
            assertEquals(height - rules.margin, spots.maxOf { it.y + it.height }, "$height tall: as near the centre as the margin allows")
            assertNoOverlaps(spots)
            assertClearOfPen(spots, band)
        }
    }

    @Test fun `no chip is closer to the pen than the rules allow, wherever its pixels fall`() {
        // Moving the pen by tenths of a pixel changes how the column's edge rounds, on the left and on the right.
        for (tenth in 0..9) {
            val from = StagePoint(nib.x + tenth / 10f, nib.y)
            val to = StagePoint(top.x + tenth / 10f, top.y)
            val rules = rulesFor(from, to)
            val band = PenBand(from, to, rules)
            listOf(640, 900).forEach { width ->
                val spots = assertNotNull(placeCallouts(width, 640, chips(170, at = grip(from, to)), from, to, rules), "$tenth tenths, $width wide")
                assertColumn(spots, rules)
                assertClearOfPen(spots, band)
            }
        }
    }

    @Test fun `chips too wide for the roomier side are placed by the older arrangements`() {
        // 230 wide is 2 px more than the pen's left offers a column, yet the chips fit split between the sides.
        val rules = rulesFor()
        val spots = assertNotNull(placeCallouts(640, 640, chips(230), nib, top, rules))
        assertEquals(4, spots.size)
        assertTrue(spots.any { it.beforePen } && spots.any { !it.beforePen }, "split between the sides")
        assertNoOverlaps(spots)
        assertInside(spots, 640, 640, rules)
        assertClearOfPen(spots, PenBand(nib, top, rules), ROUNDED)
    }

    @Test fun `a column over an obstacle is given up for the other side`() {
        // Without the obstacle the column is on the left (the first test). It covers the lowest chips there.
        val obstacle = StageRect(100f, 500f, 140f, 560f)
        val rules = rulesFor(obstacles = listOf(obstacle))
        val spots = assertNotNull(placeCallouts(640, 640, chips(170), nib, top, rules))
        assertEquals(4, spots.size)
        assertTrue(spots.none { it.beforePen })
        assertTrue(spots.none { obstacle.intersects(it.rect) }, "a chip covers the obstacle")
        assertNoOverlaps(spots)
        assertInside(spots, 640, 640, rules)
        assertClearOfPen(spots, PenBand(nib, top, rules), ROUNDED)
    }

    @Test fun `an obstacle over the whole stage leaves only the list`() {
        val everything = StageRect(0f, 0f, 640f, 640f)
        assertNull(placeCallouts(640, 640, chips(170), nib, top, rulesFor(obstacles = listOf(everything))))
    }

    @Test fun `chips too wide for either side make a list`() {
        assertNull(placeCallouts(640, 640, chips(300), nib, top, rulesFor()))
    }

    @Test fun `a stage too small for the column makes a list`() {
        // The action picker open: the stage is 360 wide and the pen is re-framed 140 px to the left of where it was.
        val from = StagePoint(160f, 520f)
        val to = StagePoint(380f, 120f)
        assertNull(placeCallouts(360, 640, chips(170, at = grip(from, to)), from, to, rulesFor(from, to)))
        // The list is the stage's doing: chips of 60 wide still fit there.
        assertNotNull(placeCallouts(360, 640, chips(60, at = grip(from, to)), from, to, rulesFor(from, to)))
        // A stage too short for four chips and their gaps: they need 4 × 61 + 3 × 8 = 268 and it has 260 - 2 × 12 = 236.
        assertNull(placeCallouts(640, 260, chips(170), nib, top, rulesFor()))
    }

    @Test fun `narrower chips never fall back to the list where wider ones fit`() {
        val fits = (80..320 step 10).map { w -> w to (placeCallouts(640, 640, chips(w), nib, top, rulesFor()) != null) }
        val widest = fits.last { it.second }.first
        assertTrue(widest >= 220, "fits up to $widest: $fits")
        fits.forEach { (w, fit) -> assertEquals(w <= widest, fit, "$w wide: $fits") }
    }

    @Test fun `a flat pen has no axis to place beside`() {
        val from = StagePoint(10f, 300f)
        val to = StagePoint(300f, 300.5f)
        assertNull(placeCallouts(640, 640, chips(170), from, to, rulesFor(from, to)))
    }

    @Test fun `stack pushes down, then up, and gives up when full`() {
        assertEquals(listOf(10f, 66f), stack(listOf(0f, 20f), listOf(48, 48), 10f, 200f, 8f))
        assertEquals(listOf(96f, 152f), stack(listOf(180f, 190f), listOf(48, 48), 10f, 200f, 8f))
        assertNull(stack(listOf(0f, 0f, 0f), listOf(80, 80, 80), 10f, 200f, 8f))
    }

    private companion object {
        /** The model's pen from nib to end cap, in millimetres. */
        const val PEN_MM = 165f

        /** Float noise in the assertions' own geometry, far under the pixel a chip rounded towards the pen would be off by. */
        const val NOISE = 1e-3f

        /** Half a pixel, which the older arrangements' rounding to the nearest pixel can cost (the lead leaves 20). */
        const val ROUNDED = 0.5f + NOISE
    }
}
