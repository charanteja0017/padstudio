package com.charan.padstudio.today

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The pen the tests draw: the model's markers as docs/redesign/ROUND2-BRIEF.md tabulates them, scaled to a pen
// of any length in px. The grip markers sit 26 to 48 mm from the nib on the 165 mm pen (16 to 29 percent of the
// way up), the end cap at 165 mm.
private val GRIP_MM = mapOf("marker_slide_bottom" to 26f, "marker_squeeze" to 32f, "marker_double_tap" to 44f, "marker_slide_top" to 48f)
private const val PEN_MM = 165f
private const val SQUEEZE = "marker_squeeze"

/** The pen leaning 36 degrees from vertical, its cap to the upper right, as PenModelView frames it. */
private val LEAN = Offset(sin(Math.toRadians(36.0)).toFloat(), -cos(Math.toRadians(36.0)).toFloat())

/** The markers of a pen drawn [length] px long from [nib] along the unit vector [axis]; [withTop] false is a model without `marker_top`. */
private fun penMarkers(nib: Offset, axis: Offset, length: Float, withTop: Boolean = true): Map<String, Offset> = buildMap {
    put(NIB, nib)
    GRIP_MM.forEach { (name, mm) -> put(name, nib + axis * (length * mm / PEN_MM)) }
    if (withTop) put(TOP, nib + axis * length)
}

/** The pen at rest in [stage]: centred, covering 0.86 of the stage's height as PenModelView's FILL_HEIGHT does, leaning along [axis]. */
private fun penIn(stage: Size, axis: Offset = LEAN): Map<String, Offset> {
    val length = 0.86f * stage.height / -axis.y
    val centre = Offset(stage.width / 2f, stage.height / 2f)
    return penMarkers(nib = centre - axis * (length / 2f), axis = axis, length = length)
}

/** The side stage the hero gives the pen (in dp, which are px in these tests) for a hero [heroWidthDp] wide at [bodyEmDp] body text. */
private fun sideStage(heroWidthDp: Float, bodyEmDp: Float): Size =
    heroGeometry(heroWidthDp, bodyEmDp).let { Size(it.sideStageWidthDp, it.sideStageHeightDp) }

/** Where the Pause button sits in [stage]: 48 dp square, 12 dp in from the corner. */
private fun pauseRect(stage: Size): Rect = Rect(stage.width - 60f, stage.height - 60f, stage.width - 12f, stage.height - 12f)

class CalloutLayoutTest {
    // 1 px = 1 dp here: the mock's 6 dp dot and 22 dp leader, 8 dp inset and gap, shifts of 12 dp.
    private val metrics = CalloutMetrics(dotRadius = 6f, leader = 22f, inset = 8f, gap = 8f, shiftStep = 12f, shiftSteps = 2)
    private val reach = 28f

    // Double tap, then squeeze, as the hero lists them; two pills of the size the labels measure to at 16 dp body text.
    private val pills = listOf(Size(92f, 48f), Size(80f, 48f))

    private fun rects(anchors: List<Offset>, placements: List<PillPlacement>): List<Rect> =
        anchors.indices.map { pillRect(anchors[it], pills[it], placements[it], metrics) }

    private fun anchorsOf(markers: Map<String, Offset>): List<Offset> = listOf(markers.getValue(DOUBLE_TAP), markers.getValue(SQUEEZE))

    /** Every pill inside the stage's inset, clear of the Pause button and the pen, and clear of each other. */
    private fun assertFits(rects: List<Rect>, stage: Size, pen: PenBar?, obstacles: List<Rect>) {
        rects.forEach { rect ->
            assertTrue(
                rect.left >= metrics.inset - 0.001f && rect.top >= metrics.inset - 0.001f &&
                    rect.right <= stage.width - metrics.inset + 0.001f && rect.bottom <= stage.height - metrics.inset + 0.001f,
                "$rect is outside the stage $stage",
            )
            obstacles.forEach { assertFalse(it.inflate(metrics.gap - 0.001f).overlaps(rect), "$rect is too near $it") }
            if (pen != null) {
                assertTrue(capsuleDistance(pen.nib, pen.top, rect) >= pen.halfWidth + metrics.gap - 0.001f, "$rect is too near the pen")
            }
        }
        rects.forEachIndexed { i, a -> rects.drop(i + 1).forEach { b -> assertFalse(a.overlaps(b), "$a overlaps $b") } }
    }

    // ---- One pill ----------------------------------------------------------------------------------

    @Test fun `a pill sits a dot and a leader from its marker, centred on it unless shifted`() {
        val anchor = Offset(200f, 100f)
        val size = Size(100f, 50f)
        assertEquals(Rect(72f, 75f, 172f, 125f), pillRect(anchor, size, PillPlacement(PillSide.START, 0f), metrics))
        assertEquals(Rect(228f, 75f, 328f, 125f), pillRect(anchor, size, PillPlacement(PillSide.END, 0f), metrics))
        assertEquals(Rect(228f, 63f, 328f, 113f), pillRect(anchor, size, PillPlacement(PillSide.END, -12f), metrics))
    }

    @Test fun `a pushed pill stands farther from its marker, on either side`() {
        val anchor = Offset(200f, 100f)
        val size = Size(100f, 50f)
        assertEquals(Rect(62f, 75f, 162f, 125f), pillRect(anchor, size, PillPlacement(PillSide.START, 0f, push = 10f), metrics))
        assertEquals(Rect(238f, 75f, 338f, 125f), pillRect(anchor, size, PillPlacement(PillSide.END, 0f, push = 10f), metrics))
    }

    @Test fun `pills that fit level on their preferred sides stay there`() {
        val placements = fitCallouts(
            stage = Size(400f, 300f),
            anchors = listOf(Offset(250f, 100f), Offset(150f, 200f)),
            pills = listOf(Size(100f, 50f), Size(100f, 50f)),
            preferred = listOf(PillSide.START, PillSide.END),
            obstacles = emptyList(),
            pen = null,
            metrics = metrics,
        )
        assertEquals(listOf(PillPlacement(PillSide.START, 0f), PillPlacement(PillSide.END, 0f)), placements)
    }

    @Test fun `a pill with no room on its preferred side goes to the other side before it moves up or down`() {
        val placements = fitCallouts(Size(400f, 300f), listOf(Offset(100f, 100f)), listOf(Size(100f, 50f)), listOf(PillSide.START), emptyList(), null, metrics)
        assertEquals(listOf(PillPlacement(PillSide.END, 0f)), placements)
    }

    @Test fun `pills that would overlap move apart by whole steps`() {
        // Both markers near the left edge: neither pill fits on the left, and level on the right they overlap.
        val placements = fitCallouts(
            stage = Size(360f, 300f),
            anchors = listOf(Offset(60f, 100f), Offset(60f, 130f)),
            pills = listOf(Size(100f, 40f), Size(100f, 40f)),
            preferred = listOf(PillSide.END, PillSide.END),
            obstacles = emptyList(),
            pen = null,
            metrics = metrics,
        )
        assertEquals(listOf(PillPlacement(PillSide.END, 0f), PillPlacement(PillSide.END, 24f)), placements)
    }

    @Test fun `pills that can't fit anywhere give no placement, so the screen shows the list`() {
        assertNull(fitCallouts(Size(100f, 100f), listOf(Offset(50f, 50f)), listOf(Size(100f, 40f)), listOf(PillSide.START), emptyList(), null, metrics))
        assertNull(fitCallouts(Size(10f, 10f), listOf(Offset(5f, 5f)), listOf(Size(1f, 1f)), listOf(PillSide.START), emptyList(), null, metrics))
    }

    @Test fun `a pill keeps clear of the Pause button`() {
        val pause = Rect(340f, 240f, 388f, 288f)
        val placements = fitCallouts(Size(400f, 300f), listOf(Offset(300f, 250f)), listOf(Size(60f, 40f)), listOf(PillSide.END), listOf(pause), null, metrics)
        assertEquals(listOf(PillPlacement(PillSide.START, 0f)), placements)
        // Without the button the preferred side is free.
        assertEquals(listOf(PillPlacement(PillSide.END, 0f)), fitCallouts(Size(400f, 300f), listOf(Offset(300f, 250f)), listOf(Size(60f, 40f)), listOf(PillSide.END), emptyList(), null, metrics))
    }

    @Test fun `a pill never lies across the pen`() {
        // A level pen 400 px long through the marker: its barrel is 400 x 5 / 165 = 12.1 px either side of the axis,
        // so a pill (half height 20) must move off it by 20 + 12.1 + the 8 gap, which takes four steps of 12.
        val pen = penBar(penMarkers(nib = Offset(0f, 100f), axis = Offset(1f, 0f), length = 400f))!!
        assertEquals(400f * 5f / 165f, pen.halfWidth, 0.001f)
        val placements = fitCallouts(
            stage = Size(400f, 300f),
            anchors = listOf(Offset(200f, 100f)),
            pills = listOf(Size(100f, 40f)),
            preferred = listOf(PillSide.START),
            obstacles = emptyList(),
            pen = pen,
            metrics = metrics.copy(shiftSteps = 4),
        )
        assertEquals(listOf(PillPlacement(PillSide.START, -48f)), placements)
        assertNull(fitCallouts(Size(400f, 300f), listOf(Offset(200f, 100f)), listOf(Size(100f, 40f)), listOf(PillSide.START), emptyList(), pen, metrics.copy(shiftSteps = 3)))
    }

    @Test fun `a tall pill sits level beside a pen leaning 36 degrees, as at 125 percent with Bold`() {
        // The pill's bounding box would graze the barrel at its corner; its rounded end keeps 17 dp clear of the axis.
        val anchor = Offset(200f, 200f)
        val pen = penBar(penMarkers(nib = anchor - LEAN * 150f, axis = LEAN, length = 300f))!!
        assertEquals(9.09f, pen.halfWidth, 0.01f)
        val pill = Size(100f, 55f)
        val placements = fitCallouts(Size(400f, 400f), listOf(anchor), listOf(pill), listOf(PillSide.START), emptyList(), pen, metrics)
        assertEquals(listOf(PillPlacement(PillSide.START, 0f)), placements)
        val clearance = capsuleDistance(pen.nib, pen.top, pillRect(anchor, pill, PillPlacement(PillSide.START, 0f), metrics))
        assertEquals(17.4f, clearance, 0.1f)
    }

    // ---- The pen's axis ----------------------------------------------------------------------------

    @Test fun `the pen's axis runs from the nib to the end cap marker, its barrel 5 mm in 165 mm of its length`() {
        val markers = penMarkers(nib = Offset(100f, 300f), axis = Offset(0f, -1f), length = 165f)
        // The double-tap marker plays no part once the cap is there.
        val bar = penBar(markers + (DOUBLE_TAP to Offset(1f, 1f)))!!
        assertEquals(Offset(100f, 300f), bar.nib)
        assertEquals(100f, bar.top.x, 0.001f)
        assertEquals(135f, bar.top.y, 0.001f)
        assertEquals(5f, bar.halfWidth, 0.001f)
        // The barrel grows with the pen's drawn length.
        assertEquals(10f, penBar(penMarkers(Offset(0f, 0f), Offset(1f, 0f), length = 330f))!!.halfWidth, 0.001f)
    }

    @Test fun `without an end cap marker the pen's top is the double-tap marker`() {
        val bar = penBar(penMarkers(nib = Offset(100f, 300f), axis = Offset(0f, -1f), length = 165f, withTop = false))!!
        // 44 mm up a pen drawn at one px a mm.
        assertEquals(100f, bar.top.x, 0.001f)
        assertEquals(256f, bar.top.y, 0.001f)
        assertEquals(44f * 5f / 165f, bar.halfWidth, 0.001f)
    }

    @Test fun `no axis until the nib and a top are known and apart`() {
        assertNull(penBar(mapOf(NIB to Offset(1f, 1f))))
        assertNull(penBar(mapOf(TOP to Offset(50f, 50f), DOUBLE_TAP to Offset(50f, 50f))))
        assertNull(penBar(mapOf(NIB to Offset(1f, 1f), TOP to Offset(1f, 1f))))
        assertNull(penBar(mapOf(NIB to Offset(1f, 1f), DOUBLE_TAP to Offset(1f, 1f))))
    }

    // ---- The fanned column -------------------------------------------------------------------------

    @Test fun `the two callouts stand in one column beside the grip, the higher marker's pill on top`() {
        val stage = sideStage(heroWidthDp = 900f, bodyEmDp = 16f)
        assertEquals(Size(360f, 320f), stage)
        val markers = penIn(stage)
        val anchors = anchorsOf(markers)
        val pen = penBar(markers)!!
        val pause = pauseRect(stage)

        val placements = assertNotNull(fanCallouts(stage, anchors, pills, listOf(pause), pen, metrics))
        val column = rects(anchors, placements)

        // Double tap is the higher marker (smaller y), and there is more room on the right than on the left.
        assertTrue(anchors[0].y < anchors[1].y)
        assertEquals(listOf(PillSide.END, PillSide.END), placements.map { it.side })
        // One column: the pills share their near edge, which is at least a dot and a leader past every marker.
        assertEquals(column[0].left, column[1].left, 0.001f)
        anchors.indices.forEach { assertTrue(column[it].left - anchors[it].x >= reach - 0.001f, "leader $it is shorter than usual") }
        // Ordered by marker height: the double-tap pill is the upper one, an 8 dp gap above the squeeze pill.
        assertEquals(column[0].bottom + metrics.gap, column[1].top, 0.001f)
        // Centred on the mean height of the two markers.
        assertEquals((anchors[0].y + anchors[1].y) / 2f, (column[0].top + column[1].bottom) / 2f, 0.001f)
        assertFits(column, stage, pen, listOf(pause))
    }

    @Test fun `the column moves out from the grip only as far as it takes to clear the barrel`() {
        val stage = sideStage(900f, 16f)
        val markers = penIn(stage)
        val anchors = anchorsOf(markers)
        val pen = penBar(markers)!!
        val placements = fanCallouts(stage, anchors, pills, listOf(pauseRect(stage)), pen, metrics)!!
        val needed = pen.halfWidth + metrics.gap
        // The upper pill's near end would graze the barrel at the usual leader length (the pen leans toward it) ...
        val usual = placements[0].copy(push = 0f)
        assertTrue(capsuleDistance(pen.nib, pen.top, pillRect(anchors[0], pills[0], usual, metrics)) < needed)
        // ... and the column stands out in half-gap steps until it clears, no more: 16 is the first that does.
        assertEquals(16f, placements[0].push, 0.001f)
        val oneStepLess = placements[0].copy(push = placements[0].push - metrics.gap / 2f)
        assertTrue(capsuleDistance(pen.nib, pen.top, pillRect(anchors[0], pills[0], oneStepLess, metrics)) < needed)
        // The squeeze pill lines up with it: its marker is that much farther left.
        assertEquals(16f + (anchors[0].x - anchors[1].x), placements[1].push, 0.001f)
    }

    @Test fun `with no pen known the column stays at the usual leader length`() {
        val stage = sideStage(900f, 16f)
        val anchors = anchorsOf(penIn(stage))
        val placements = fanCallouts(stage, anchors, pills, emptyList(), null, metrics)!!
        assertEquals(0f, placements[0].push, 0.001f)
        assertEquals(anchors[0].x - anchors[1].x, placements[1].push, 0.001f)
        assertEquals(rects(anchors, placements)[0].left, rects(anchors, placements)[1].left, 0.001f)
    }

    @Test fun `the column goes to the left when the pen leans the other way and the room is there`() {
        val stage = sideStage(900f, 16f)
        val markers = penIn(stage, axis = Offset(-LEAN.x, LEAN.y))
        val anchors = anchorsOf(markers)
        val pen = penBar(markers)!!
        val placements = assertNotNull(fanCallouts(stage, anchors, pills, listOf(pauseRect(stage)), pen, metrics))
        val column = rects(anchors, placements)
        assertEquals(listOf(PillSide.START, PillSide.START), placements.map { it.side })
        // The pills share their right edge, a dot and a leader left of the leftmost marker or farther.
        assertEquals(column[0].right, column[1].right, 0.001f)
        anchors.indices.forEach { assertTrue(anchors[it].x - column[it].right >= reach - 0.001f) }
        assertEquals(column[0].bottom + metrics.gap, column[1].top, 0.001f)
        assertFits(column, stage, pen, listOf(pauseRect(stage)))
    }

    @Test fun `the column's order follows the markers' heights, not the order the callouts are listed in`() {
        val stage = sideStage(900f, 16f)
        val markers = penIn(stage)
        val anchors = anchorsOf(markers).reversed() // squeeze (lower) first, double tap (higher) second
        val sizes = pills.reversed()
        val placements = fanCallouts(stage, anchors, sizes, emptyList(), null, metrics)!!
        val column = anchors.indices.map { pillRect(anchors[it], sizes[it], placements[it], metrics) }
        assertTrue(column[1].bottom < column[0].top, "the higher marker's pill (listed second) is the upper one")
        assertEquals(column[1].bottom + metrics.gap, column[0].top, 0.001f)
        // Markers at the same height keep the callouts' own order.
        val level = listOf(Offset(100f, 150f), Offset(120f, 150f))
        val flat = fanCallouts(Size(400f, 300f), level, pills, emptyList(), null, metrics)!!
        val flatRects = level.indices.map { pillRect(level[it], pills[it], flat[it], metrics) }
        assertTrue(flatRects[0].bottom < flatRects[1].top)
    }

    @Test fun `a column that would not fit gives none, and the callouts go back to a pill beside each marker`() {
        // A hero 532 dp wide at 125 percent text: a 266 x 239 dp stage, where the column meets the Pause button on the
        // right and the stage edge on the left.
        val stage = sideStage(heroWidthDp = 532f, bodyEmDp = 20f)
        val markers = penIn(stage)
        val anchors = anchorsOf(markers)
        val pen = penBar(markers)
        val pause = listOf(pauseRect(stage))
        assertNull(fanCallouts(stage, anchors, pills, pause, pen, metrics))
        assertNull(placeCallouts(stage, anchors, pills, listOf(PillSide.START, PillSide.END), pause, pen, metrics))

        // Markers too far apart for a column (as the old model's were) are placed one by one, as before.
        val apart = listOf(Offset(250f, 100f), Offset(150f, 200f))
        val big = listOf(Size(100f, 50f), Size(100f, 50f))
        val preferred = listOf(PillSide.START, PillSide.END)
        assertNull(fanCallouts(Size(400f, 300f), apart, big, emptyList(), null, metrics))
        assertEquals(
            listOf(PillPlacement(PillSide.START, 0f), PillPlacement(PillSide.END, 0f)),
            placeCallouts(Size(400f, 300f), apart, big, preferred, emptyList(), null, metrics),
        )
        // Where a column does fit, it is the answer.
        val fitting = sideStage(900f, 16f)
        val fitMarkers = penIn(fitting)
        assertEquals(
            fanCallouts(fitting, anchorsOf(fitMarkers), pills, listOf(pauseRect(fitting)), penBar(fitMarkers), metrics),
            placeCallouts(fitting, anchorsOf(fitMarkers), pills, preferred, listOf(pauseRect(fitting)), penBar(fitMarkers), metrics),
        )
    }

    @Test fun `no column crosses the stage edge, meets the Pause button, or stands for a single pill`() {
        val stage = sideStage(900f, 16f)
        val markers = penIn(stage)
        val anchors = anchorsOf(markers)
        val pen = penBar(markers)
        // The Pause button over where the lower pill goes: the right is blocked, and the left has no room.
        assertNull(fanCallouts(stage, anchors, pills, listOf(Rect(200f, 240f, 248f, 288f)), pen, metrics))
        // Markers near the top: the column would start above the stage's inset.
        val high = anchors.map { it - Offset(0f, 190f) }
        assertNull(fanCallouts(stage, high, pills, emptyList(), null, metrics))
        // Taller than the stage.
        assertNull(fanCallouts(Size(200f, 100f), listOf(Offset(100f, 50f), Offset(110f, 60f)), listOf(Size(80f, 48f), Size(80f, 48f)), emptyList(), null, metrics))
        // One pill has no column.
        assertNull(fanCallouts(stage, listOf(anchors[0]), listOf(pills[0]), emptyList(), null, metrics))
    }

    // ---- Moves and distances -----------------------------------------------------------------------

    @Test fun `markers count as moved only past the threshold, or when one appears or goes`() {
        val before = mapOf("a" to Offset(0f, 0f), "b" to Offset(10f, 10f))
        assertFalse(markersMoved(before, mapOf("a" to Offset(5f, -5f), "b" to Offset(10f, 10f)), listOf("a", "b"), 12f))
        assertTrue(markersMoved(before, mapOf("a" to Offset(13f, 0f), "b" to Offset(10f, 10f)), listOf("a", "b"), 12f))
        assertTrue(markersMoved(before, mapOf("b" to Offset(10f, 10f)), listOf("a", "b"), 12f))
        assertFalse(markersMoved(before, mapOf("a" to Offset(50f, 50f), "b" to Offset(10f, 10f)), listOf("b"), 12f))
    }

    @Test fun `distances to a capsule and between segments`() {
        val pill = Rect(20f, 40f, 120f, 60f)
        assertEquals(20f, capsuleDistance(Offset(0f, 0f), Offset(0f, 100f), pill), 0.001f)
        assertEquals(-10f, capsuleDistance(Offset(50f, 0f), Offset(50f, 100f), pill), 0.001f)
        assertEquals(5f, segmentDistance(Offset(0f, 0f), Offset(10f, 0f), Offset(0f, 5f), Offset(10f, 5f)), 0.001f)
        assertEquals(0f, segmentDistance(Offset(0f, 0f), Offset(10f, 10f), Offset(0f, 10f), Offset(10f, 0f)), 0.001f)
    }
}

/** The hero's callout controller: what it does with the pen's reports, and that it does nothing while the pen is busy. */
class CalloutControllerTest {
    private val metrics = CalloutMetrics(dotRadius = 6f, leader = 22f, inset = 8f, gap = 8f, shiftStep = 12f, shiftSteps = 2)
    private val geometry = heroGeometry(heroWidthDp = 900f, bodyEmDp = 16f)
    private val side = Size(geometry.sideStageWidthDp, geometry.sideStageHeightDp)

    /** The pen at rest in the side stage, 360 x 320 dp: both pills fit in a column on its right. */
    private val rest = penIn(side)

    /**
     * What a zoomed pen reports mid-gesture: the grip markers flung to the stage's corners. Nothing fits there, in
     * the side stage or in the stacked one, so a fit decided from it steps the hero down to the list.
     */
    private val gesture = rest + (DOUBLE_TAP to Offset(352f, 4f)) + (SQUEEZE to Offset(30f, 300f))

    private fun controller(): CalloutController = CalloutController().also {
        it.configure(
            "inputs",
            CalloutInputs(
                geometry = geometry,
                possible = true,
                markerNames = listOf(DOUBLE_TAP, SQUEEZE),
                pillSizes = listOf(Size(92f, 48f), Size(80f, 48f)),
                preferred = listOf(PillSide.START, PillSide.END),
                metrics = metrics,
                moveThreshold = 12f,
                stageSize = { arrangement ->
                    if (arrangement == HeroArrangement.SIDE) side else Size(geometry.stackedStageWidthDp, geometry.stackedStageHeightDp)
                },
                pauseRect = { stage -> pauseRect(stage) },
            ),
        )
    }

    @Test fun `once the pen has settled the callouts are placed and the hero stays beside the text`() {
        val controller = controller()
        controller.sink(rest)
        assertNull(controller.placements, "nothing is decided before the pen has had time to settle")
        controller.settle()
        assertEquals(HeroArrangement.SIDE, controller.arrangement)
        assertEquals(2, controller.placements?.size)
        assertEquals(setOf(PillSide.END), controller.placements?.map { it.side }?.toSet())
    }

    @Test fun `while the pen is busy a pose that fits nowhere changes nothing, and after it rests the layout is as it was`() {
        val controller = controller()
        controller.sink(rest)
        controller.settle()
        val placed = controller.placements
        assertNotNull(placed)

        controller.busy = true
        controller.sink(gesture)
        controller.settle()
        controller.giveUp()
        controller.rested()
        assertEquals(HeroArrangement.SIDE, controller.arrangement)
        assertEquals(placed, controller.placements)

        controller.busy = false
        controller.sink(rest)
        controller.rested()
        assertEquals(HeroArrangement.SIDE, controller.arrangement)
        assertEquals(placed, controller.placements)
    }

    @Test fun `the same pose reported when the pen is not busy steps the hero down to the list for good`() {
        // The control for the test above: without the busy signal this is what a gesture did to the hero.
        val controller = controller()
        controller.sink(rest)
        controller.settle()
        assertEquals(HeroArrangement.SIDE, controller.arrangement)

        controller.sink(gesture)
        assertEquals(HeroArrangement.STACKED, controller.arrangement)
        assertNull(controller.placements)
        controller.settle()
        assertEquals(HeroArrangement.LIST, controller.arrangement)
    }

    @Test fun `a first fit asked for while the pen is busy is made once it rests`() {
        val controller = controller()
        controller.busy = true
        controller.sink(rest)
        controller.settle()
        controller.giveUp()
        assertNull(controller.placements)
        assertEquals(HeroArrangement.SIDE, controller.arrangement)

        controller.busy = false
        controller.rested()
        assertEquals(HeroArrangement.SIDE, controller.arrangement)
        assertEquals(2, controller.placements?.size)
    }

    @Test fun `with no markers the hero goes to the list, but only when the pen is not busy`() {
        val idle = controller()
        idle.settle()
        idle.giveUp()
        assertEquals(HeroArrangement.LIST, idle.arrangement)

        val busy = controller()
        busy.settle()
        busy.busy = true
        busy.giveUp()
        assertEquals(HeroArrangement.SIDE, busy.arrangement)
        busy.busy = false
        busy.rested()
        assertEquals(HeroArrangement.LIST, busy.arrangement)
    }

    @Test fun `a pose that moved while the pen was busy is fitted again once it rests`() {
        val controller = controller()
        controller.sink(rest)
        controller.settle()
        val placed = controller.placements
        assertNotNull(placed)

        // The pen comes back at rest with the double-tap marker 20 px lower, past the sway threshold, and its last
        // report was made while busy: nothing reports it again, so it is the rest that picks it up.
        val moved = rest + (DOUBLE_TAP to rest.getValue(DOUBLE_TAP) + Offset(0f, 20f))
        controller.busy = true
        controller.sink(moved)
        assertEquals(placed, controller.placements)
        controller.busy = false
        controller.rested()
        assertEquals(HeroArrangement.SIDE, controller.arrangement)
        assertNotNull(controller.placements)
        assertNotEquals(placed, controller.placements)
    }
}
