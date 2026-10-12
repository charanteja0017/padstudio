package com.charan.padstudio.pen.screen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The marker board behind PenStage's callouts, on synthetic markers clustered like round 2's grip band: the nib at
 * (300, 520), the end cap (`marker_top`) at (520, 120) and the four gesture markers 26 to 48 mm up the 165 mm pen,
 * 16 to 29 % of the way from nib to cap. The stage with the action picker closed is 640 × 640; open, it is 360
 * wide and the pen view re-frames the pen centred in it, 140 px to the left. There the idle sway moves a marker by
 * at most 5.1 px on either axis.
 */
class MarkerBoardTest {
    private val closed = IntSize(640, 640)
    private val open = IntSize(360, 640)

    private fun along(mm: Float, nib: Offset, top: Offset) =
        Offset(nib.x + (top.x - nib.x) * mm / 165f, nib.y + (top.y - nib.y) * mm / 165f)

    private fun pen(nib: Offset, top: Offset) = mapOf(
        "marker_squeeze" to along(26f, nib, top),
        "marker_slide_bottom" to along(33f, nib, top),
        "marker_slide_top" to along(41f, nib, top),
        "marker_double_tap" to along(48f, nib, top),
        "marker_nib" to nib,
        "marker_top" to top,
    )

    private val atClosed = pen(nib = Offset(300f, 520f), top = Offset(520f, 120f))
    private val atOpen = pen(nib = Offset(160f, 520f), top = Offset(380f, 120f))

    private fun swayed(markers: Map<String, Offset>, dx: Float, dy: Float) = markers.mapValues { (_, o) -> Offset(o.x + dx, o.y + dy) }

    private fun board(size: IntSize, markers: Map<String, Offset>, busy: () -> Boolean = { false }) = MarkerBoard(busy).apply {
        threshold = 12f
        assertTrue(measured(size))
        report(markers)
    }

    @Test fun `the sway reaches the dots but not the chips`() {
        val board = board(closed, atClosed)
        board.report(swayed(atClosed, 5.1f, -5.1f))
        assertEquals(atClosed, board.steady)
        assertEquals(swayed(atClosed, 5.1f, -5.1f), board.live)
        assertTrue(board.measured(closed))
    }

    @Test fun `a move past the threshold is taken`() {
        val board = board(closed, atClosed)
        board.report(swayed(atClosed, 0f, 12.5f))
        assertEquals(swayed(atClosed, 0f, 12.5f), board.steady)
    }

    @Test fun `opening the picker waits for the re-framed pen`() {
        val board = board(closed, atClosed)
        assertFalse(board.measured(open), "markers from the wider stage would misplace the chips")
        assertEquals(open, board.awaiting)
        // One animated frame still runs with the old framing before the pen view recomposes: not taken.
        board.report(swayed(atClosed, -3f, 2f))
        assertFalse(board.measured(open))
        assertEquals(atClosed, board.steady)
        // The re-framed pen.
        board.report(atOpen)
        assertTrue(board.measured(open))
        assertEquals(atOpen, board.steady)
        assertNull(board.awaiting)
    }

    @Test fun `closing it again waits the same way`() {
        val board = board(open, atOpen)
        assertFalse(board.measured(closed))
        board.report(atClosed)
        assertTrue(board.measured(closed))
        assertEquals(atClosed, board.steady)
    }

    @Test fun `a small resize keeps the steady copy`() {
        // A status line wrapping in the send bar: 24 px shorter, so the pen's markers move about 12 px.
        val board = board(closed, atClosed)
        assertTrue(board.measured(IntSize(640, 616)))
        assertNull(board.awaiting)
    }

    @Test fun `without new markers the last ones stand after the wait`() {
        val board = board(closed, atClosed)
        assertFalse(board.measured(open))
        board.settle()
        assertTrue(board.measured(open))
        assertEquals(atClosed, board.steady)
        assertNull(board.awaiting)
    }

    @Test fun `before any markers nothing is stale`() {
        val board = MarkerBoard().apply { threshold = 12f }
        assertTrue(board.measured(closed))
        assertTrue(board.measured(open))
        assertNull(board.awaiting)
    }

    @Test fun `while the pen is busy the steady markers do not move`() {
        var busy = false
        val board = board(closed, atClosed) { busy }
        busy = true
        // The gesture swings the pen and the camera zooms: every marker is far past the threshold, frame after frame.
        board.report(swayed(atClosed, 60f, -45f))
        board.report(swayed(atClosed, 95f, -70f))
        assertEquals(atClosed, board.steady)
        assertEquals(atClosed, board.live, "a busy pen isn't drawn, and the dots come back where they were")
        assertTrue(board.measured(closed))
    }

    @Test fun `once the pen is back at rest the board follows it again`() {
        var busy = false
        val board = board(closed, atClosed) { busy }
        busy = true
        board.report(swayed(atClosed, 60f, -45f))
        busy = false
        // At rest, within a sway of where it was: the dots follow, the chips stay.
        board.report(swayed(atClosed, 2f, -1f))
        assertEquals(swayed(atClosed, 2f, -1f), board.live)
        assertEquals(atClosed, board.steady)
        // And a real move is taken once more.
        board.report(swayed(atClosed, 0f, 12.5f))
        assertEquals(swayed(atClosed, 0f, 12.5f), board.steady)
    }

    @Test fun `a resize while the pen is busy waits for it to be at rest`() {
        var busy = false
        val board = board(closed, atClosed) { busy }
        busy = true
        assertFalse(board.measured(open))
        // The re-framed pen reports, and the wait for it runs out, but a busy pen's pose is not where it rests.
        board.report(atOpen)
        board.settle()
        assertFalse(board.measured(open))
        assertEquals(atClosed, board.steady)
        assertEquals(open, board.awaiting)
        busy = false
        board.report(atOpen)
        assertTrue(board.measured(open))
        assertEquals(atOpen, board.steady)
        assertNull(board.awaiting)
    }
}
