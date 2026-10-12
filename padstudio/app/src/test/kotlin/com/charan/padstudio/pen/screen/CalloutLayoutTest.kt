package com.charan.padstudio.pen.screen

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalloutLayoutTest {
    // The mock-up's stage (docs/redesign/mock-pen.html): 348 × 559 dp, markers measured there at density 1.
    private val nib = StagePoint(162f, 497f)
    private val doubleTap = StagePoint(259.3f, 199.4f)
    private val markers = mapOf(
        "double_tap" to doubleTap,
        "slide_up" to StagePoint(243.5f, 247.8f),
        "slide_down" to StagePoint(223.9f, 308.3f),
        "pinch" to StagePoint(212.3f, 344f),
    )
    private val rules = CalloutRules(margin = 12, gap = 8, lead = 20, penHalfWidth = 0.05f * hypot(doubleTap.x - nib.x, doubleTap.y - nib.y))

    private fun chips(width: Int, height: Int) = markers.map { (id, anchor) -> CalloutChip(id, width, height, anchor) }

    private fun assertNoOverlaps(spots: List<CalloutSpot>) {
        for (a in spots) for (b in spots) if (a !== b) assertTrue(!a.rect.intersects(b.rect), "${a.id} overlaps ${b.id}")
    }

    @Test fun `the mock-up's chips fit in one column left of the pen`() {
        val spots = assertNotNull(placeCallouts(348, 559, chips(120, 48), nib, doubleTap, rules))
        assertEquals(4, spots.size)
        assertTrue(spots.all { it.beforePen })
        assertEquals(1, spots.map { it.x + it.width }.distinct().size, "right edges line up")
        assertTrue(spots.all { it.x >= rules.margin })
        assertNoOverlaps(spots)
        // Top to bottom in the markers' order.
        assertEquals(listOf("double_tap", "slide_up", "slide_down", "pinch"), spots.sortedBy { it.y }.map { it.id })
        // Each chip stays clear of the pen: its right edge is left of the axis at its height.
        spots.forEach { spot ->
            val axisX = nib.x + (spot.y + spot.height - nib.y) * (doubleTap.x - nib.x) / (doubleTap.y - nib.y)
            assertTrue(spot.x + spot.width <= axisX - rules.penHalfWidth, "${spot.id} touches the pen")
        }
    }

    @Test fun `chips too wide for either side make a list`() {
        assertNull(placeCallouts(348, 559, chips(300, 48), nib, doubleTap, rules))
    }

    @Test fun `chips too tall to stack make a list`() {
        assertNull(placeCallouts(348, 300, chips(100, 90), nib, doubleTap, rules))
    }

    @Test fun `wide chips beside a centred pen split between both sides`() {
        // A centred pen in a 600 dp stage, as the live view frames it: chips 230 dp wide don't all fit on one side.
        val from = StagePoint(220f, 520f)
        val to = StagePoint(380f, 120f)
        val anchors = mapOf(
            "double_tap" to StagePoint(372f, 140f),
            "slide_up" to StagePoint(340f, 220f),
            "slide_down" to StagePoint(300f, 320f),
            "pinch" to StagePoint(280f, 370f),
        )
        val wide = anchors.map { (id, anchor) -> CalloutChip(id, 230, 64, anchor) }
        val spots = assertNotNull(placeCallouts(600, 560, wide, from, to, rules.copy(penHalfWidth = 18f)))
        assertTrue(spots.any { it.beforePen } && spots.any { !it.beforePen })
        assertTrue(spots.all { it.x >= rules.margin && it.x + it.width <= 600 - rules.margin })
        assertNoOverlaps(spots)
    }

    @Test fun `close markers are pushed apart by the gap`() {
        val crowded = listOf(
            CalloutChip("a", 100, 48, StagePoint(250f, 200f)),
            CalloutChip("b", 100, 48, StagePoint(248f, 205f)),
            CalloutChip("c", 100, 48, StagePoint(246f, 210f)),
        )
        val spots = assertNotNull(placeCallouts(348, 559, crowded, nib, doubleTap, rules)).sortedBy { it.y }
        spots.zipWithNext { upper, lower -> assertTrue(lower.y - (upper.y + upper.height) >= rules.gap - 1) }
    }

    @Test fun `obstacles are avoided`() {
        val pauseButton = StageRect(0f, 0f, 348f, 559f)
        assertNull(placeCallouts(348, 559, chips(120, 48), nib, doubleTap, rules.copy(obstacles = listOf(pauseButton))))
    }

    @Test fun `a flat pen has no axis to place beside`() {
        assertNull(placeCallouts(348, 559, chips(120, 48), StagePoint(10f, 300f), StagePoint(300f, 300.5f), rules))
    }

    @Test fun `stack pushes down, then up, and gives up when full`() {
        assertEquals(listOf(10f, 66f), stack(listOf(0f, 20f), listOf(48, 48), 10f, 200f, 8f))
        assertEquals(listOf(96f, 152f), stack(listOf(180f, 190f), listOf(48, 48), 10f, 200f, 8f))
        assertNull(stack(listOf(0f, 0f, 0f), listOf(80, 80, 80), 10f, 200f, 8f))
    }
}
