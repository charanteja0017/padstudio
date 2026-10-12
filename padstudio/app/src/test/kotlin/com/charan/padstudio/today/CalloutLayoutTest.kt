package com.charan.padstudio.today

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalloutLayoutTest {
    // 1 px = 1 dp here: the mock's 6 dp dot and 22 dp leader, 8 dp inset and gap, shifts of 12 dp.
    private val metrics = CalloutMetrics(dotRadius = 6f, leader = 22f, inset = 8f, gap = 8f, shiftStep = 12f, shiftSteps = 2)

    @Test fun `a pill sits a dot and a leader from its marker, centred on it unless shifted`() {
        val anchor = Offset(200f, 100f)
        val size = Size(100f, 50f)
        assertEquals(Rect(72f, 75f, 172f, 125f), pillRect(anchor, size, PillPlacement(PillSide.START, 0f), metrics))
        assertEquals(Rect(228f, 75f, 328f, 125f), pillRect(anchor, size, PillPlacement(PillSide.END, 0f), metrics))
        assertEquals(Rect(228f, 63f, 328f, 113f), pillRect(anchor, size, PillPlacement(PillSide.END, -12f), metrics))
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
        // A level pen through the marker: the pill must move off it by its half height plus the barrel and the gap.
        val pen = PenBar(Offset(0f, 100f), Offset(400f, 100f), halfWidth = 5f)
        val placements = fitCallouts(
            stage = Size(400f, 300f),
            anchors = listOf(Offset(200f, 100f)),
            pills = listOf(Size(100f, 40f)),
            preferred = listOf(PillSide.START),
            obstacles = emptyList(),
            pen = pen,
            metrics = metrics.copy(shiftSteps = 3),
        )
        assertEquals(listOf(PillPlacement(PillSide.START, -36f)), placements)
        assertNull(fitCallouts(Size(400f, 300f), listOf(Offset(200f, 100f)), listOf(Size(100f, 40f)), listOf(PillSide.START), emptyList(), pen, metrics))
    }

    @Test fun `a tall pill sits level beside a pen leaning 36 degrees, as at 125 % Bold`() {
        // The pill's bounding box would graze the barrel at its corner; its rounded end keeps 17 dp clear.
        val anchor = Offset(200f, 200f)
        val along = Offset(sin(Math.toRadians(36.0)).toFloat(), -cos(Math.toRadians(36.0)).toFloat())
        val pen = PenBar(anchor - along * 150f, anchor + along * 150f, halfWidth = 6.5f)
        val pill = Size(100f, 55f)
        val placements = fitCallouts(Size(400f, 400f), listOf(anchor), listOf(pill), listOf(PillSide.START), emptyList(), pen, metrics)
        assertEquals(listOf(PillPlacement(PillSide.START, 0f)), placements)
        val clearance = capsuleDistance(pen.nib, pen.top, pillRect(anchor, pill, PillPlacement(PillSide.START, 0f), metrics))
        assertEquals(17.4f, clearance, 0.1f)
    }

    @Test fun `the pen's axis runs from the nib to its top, scaled from the double-tap marker`() {
        val bar = penBar(mapOf("marker_nib" to Offset(100f, 300f), "marker_double_tap" to Offset(100f, 177.5f), "marker_squeeze" to Offset(1f, 1f)))
        assertEquals(Offset(100f, 300f), bar?.nib)
        assertEquals(100f, bar!!.top.x, 0.001f)
        assertEquals(135f, bar.top.y, 0.001f)
        assertEquals(5f, bar.halfWidth, 0.001f)
        assertNull(penBar(mapOf("marker_nib" to Offset(1f, 1f))))
        assertNull(penBar(mapOf("marker_nib" to Offset(1f, 1f), "marker_double_tap" to Offset(1f, 1f))))
    }

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
