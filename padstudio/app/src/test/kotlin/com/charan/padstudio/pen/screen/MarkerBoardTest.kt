package com.charan.padstudio.pen.screen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The marker board behind PenStage's callouts. Marker positions are PenModelView's own framing (the pen centred,
 * 36° from vertical) for the tablet's Pen tab at density 1: the stage with the action picker closed (585 × 388)
 * and open (303 × 388). There the idle sway moves a marker by at most 5.1 px on either axis.
 */
class MarkerBoardTest {
    private val closed = IntSize(585, 388)
    private val open = IntSize(303, 388)
    private val atClosed = mapOf(
        "marker_squeeze" to Offset(260.8f, 237.2f),
        "marker_slide_top" to Offset(316.2f, 161.0f),
        "marker_slide_bottom" to Offset(281.2f, 209.1f),
        "marker_double_tap" to Offset(344.6f, 121.8f),
        "marker_nib" to Offset(176.6f, 353.1f),
    )
    private val atOpen = mapOf(
        "marker_squeeze" to Offset(133.5f, 218.6f),
        "marker_slide_top" to Offset(166.4f, 173.3f),
        "marker_slide_bottom" to Offset(145.6f, 201.8f),
        "marker_double_tap" to Offset(183.2f, 150.2f),
        "marker_nib" to Offset(82.4f, 288.9f),
    )

    private fun swayed(markers: Map<String, Offset>, dx: Float, dy: Float) = markers.mapValues { (_, o) -> Offset(o.x + dx, o.y + dy) }

    private fun board(size: IntSize, markers: Map<String, Offset>) = MarkerBoard().apply {
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
        assertTrue(board.measured(IntSize(585, 364)))
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
}
