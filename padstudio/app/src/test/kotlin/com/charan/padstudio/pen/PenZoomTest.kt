package com.charan.padstudio.pen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The nib close-up's timeline (round 2). It is pure, so the tests drive it with a millisecond clock they set: a gesture
 * 1.7 s long starts at [t0], eases in over 450 ms, holds to the end of the clip and eases out over 450 ms.
 */
class PenZoomTest {
    private val t0 = 10_000L
    private val clip = 1_700L

    private fun started(zooms: Boolean = true, reducedMotion: Boolean = false, clipMs: Long = clip) =
        ZoomTimeline().also { it.start(t0, clipMs, zooms, reducedMotion) }

    /** z [offsetMs] after the start, on a fresh timeline: z is a function of time, not of how often it was polled. */
    private fun zAt(offsetMs: Long, zooms: Boolean = true, reducedMotion: Boolean = false, clipMs: Long = clip): Float =
        started(zooms, reducedMotion, clipMs).update(t0 + offsetMs)

    private fun assertZ(expected: Float, actual: Float, tolerance: Float = 0f, message: String? = null) =
        assertEquals(expected, actual, tolerance, message)

    @Test fun `before any start it is at rest with z 0 and not busy`() {
        val zoom = ZoomTimeline()
        assertEquals(ZoomTimeline.State.REST, zoom.state)
        assertFalse(zoom.busy)
        assertZ(0f, zoom.update(t0))
    }

    @Test fun `z reaches 1 at 450 ms and not before`() {
        assertZ(0f, zAt(0))
        assertTrue(zAt(449) < 1f, "z is already 1 a millisecond early")
        assertZ(1f, zAt(450))
        assertEquals(ZoomTimeline.State.IN, started().also { it.update(t0 + 449) }.state)
        assertEquals(ZoomTimeline.State.HOLD, started().also { it.update(t0 + 450) }.state)
    }

    @Test fun `the zoom in is eased in and out and never goes backwards`() {
        assertZ(0.5f, zAt(225), 1e-6f) // ease-in-out is symmetric about its middle
        assertTrue(zAt(45) < 0.05f, "a linear ramp would be at 0.1 after 45 ms")
        assertTrue(zAt(405) > 0.95f, "a linear ramp would be at 0.9 after 405 ms")
        val zoom = started()
        var previous = 0f
        for (ms in 0..450L) {
            val z = zoom.update(t0 + ms)
            assertTrue(z >= previous && z <= 1f, "z went from $previous to $z at +$ms ms")
            previous = z
        }
    }

    @Test fun `it holds fully zoomed until the clip ends`() {
        val zoom = started()
        for (ms in 450 until clip) {
            assertZ(1f, zoom.update(t0 + ms), message = "z at +$ms ms")
            assertEquals(ZoomTimeline.State.HOLD, zoom.state, "state at +$ms ms")
        }
        assertEquals(ZoomTimeline.State.OUT, zoom.also { it.update(t0 + clip) }.state)
    }

    @Test fun `z is back to 0 by 450 ms after the clip, easing out`() {
        assertZ(1f, zAt(clip)) // the way out starts from fully in
        assertZ(0.5f, zAt(clip + 225), 1e-6f)
        assertTrue(zAt(clip + 449) > 0f, "z is already 0 a millisecond early")
        val zoom = started()
        var previous = 1f
        for (ms in clip..clip + 450) {
            val z = zoom.update(t0 + ms)
            assertTrue(z <= previous && z >= 0f, "z went from $previous to $z at +$ms ms")
            previous = z
        }
        assertZ(0f, previous)
        assertEquals(ZoomTimeline.State.REST, zoom.state)
        assertFalse(zoom.busy)
    }

    @Test fun `busy turns on once at the start and off once when z is back to 0`() {
        val zoom = ZoomTimeline()
        val edges = mutableListOf<Pair<Long, Boolean>>()
        var last = zoom.busy
        fun watch(at: Long) {
            zoom.update(at)
            if (zoom.busy != last) {
                last = zoom.busy
                edges += at to last
            }
        }
        for (at in t0 - 50 until t0) watch(at)
        zoom.start(t0, clip, zooms = true, reducedMotion = false)
        for (at in t0..t0 + 3_000) watch(at)
        assertEquals(listOf(t0 to true, (t0 + clip + 450) to false), edges)
    }

    @Test fun `write is busy for the clip length with z 0 throughout`() {
        val zoom = started(zooms = false)
        for (ms in 0 until clip) {
            assertZ(0f, zoom.update(t0 + ms), message = "z at +$ms ms")
            assertTrue(zoom.busy, "not busy at +$ms ms")
        }
        assertZ(0f, zoom.update(t0 + clip))
        assertFalse(zoom.busy, "still busy when the clip is over")
        assertZ(0f, zoom.update(t0 + clip + 450))
        assertFalse(zoom.busy)
    }

    @Test fun `with reduced motion z jumps to 1 at the start and to 0 at the end`() {
        val zoom = started(reducedMotion = true)
        assertZ(1f, zoom.z, message = "z right after start")
        assertZ(1f, zoom.update(t0), message = "z at the start")
        assertZ(1f, zoom.update(t0 + 1), message = "z a millisecond in: no easing")
        assertZ(1f, zoom.update(t0 + clip - 1))
        assertTrue(zoom.busy)
        assertZ(0f, zoom.update(t0 + clip), message = "z at the end: no easing out")
        assertFalse(zoom.busy)
    }

    @Test fun `with reduced motion write stays at 0 and is busy for the clip`() {
        val zoom = started(zooms = false, reducedMotion = true)
        assertZ(0f, zoom.update(t0 + 1))
        assertTrue(zoom.busy)
        assertZ(0f, zoom.update(t0 + clip - 1))
        assertTrue(zoom.busy)
        zoom.update(t0 + clip)
        assertFalse(zoom.busy)
    }

    @Test fun `pause freezes time and resume carries on from the same point`() {
        val zoom = started()
        val before = zoom.update(t0 + 100)
        assertTrue(before > 0f && before < 1f, "z should be part way up at +100 ms, was $before")
        zoom.pause(t0 + 100)
        assertTrue(zoom.paused)
        assertZ(before, zoom.update(t0 + 400), message = "z moved while paused")
        assertZ(before, zoom.update(t0 + 1_000_000), message = "z moved while paused for a long time")
        assertTrue(zoom.busy, "a paused run is still busy")

        val resumed = t0 + 1_000_000
        zoom.resume(resumed)
        assertFalse(zoom.paused)
        assertZ(1f, zoom.update(resumed + 350), message = "100 ms before the pause + 350 ms after is the 450 ms ease")
        // The whole run is 2150 ms of timeline time and 100 are used, so it ends 2050 ms after the resume.
        zoom.update(resumed + 2_049)
        assertTrue(zoom.busy, "ended early after a pause")
        zoom.update(resumed + 2_050)
        assertFalse(zoom.busy, "ended late after a pause")
    }

    @Test fun `every pause and resume pair is left out of the timeline`() {
        val zoom = started()
        zoom.pause(t0 + 200)
        val first = zoom.update(t0 + 700)
        zoom.resume(t0 + 1_200) // 1000 ms paused, timeline at 200
        assertZ(first, zoom.update(t0 + 1_200), message = "z jumped on the resume")
        zoom.pause(t0 + 1_300)
        val second = zoom.update(t0 + 3_000)
        assertTrue(second > first, "100 ms of running between the pauses should have moved z up")
        zoom.resume(t0 + 5_300) // 100 ms more running (timeline 300), 4000 ms paused
        assertZ(second, zoom.update(t0 + 5_300), message = "z jumped on the second resume")
        assertZ(1f, zoom.update(t0 + 5_300 + 150), message = "300 + 150 ms is the 450 ms ease")
        assertTrue(zoom.busy)
    }

    @Test fun `a second pause or resume changes nothing`() {
        val zoom = started()
        zoom.pause(t0 + 100)
        zoom.pause(t0 + 900) // already paused: must not move the freeze point
        zoom.resume(t0 + 1_100) // 1000 ms paused
        zoom.resume(t0 + 9_000) // not paused: must not skip time
        assertZ(1f, zoom.update(t0 + 1_100 + 350))
    }

    @Test fun `time never runs backwards`() {
        val zoom = started()
        assertZ(1f, zoom.update(t0 + 1_000))
        assertZ(1f, zoom.update(t0 + 100), message = "an earlier time moved z back")
        assertEquals(ZoomTimeline.State.HOLD, zoom.state)
    }

    @Test fun `a gesture that starts during a zoom carries on from the current z and stays busy`() {
        val zoom = started()
        val mid = zoom.update(t0 + 225)
        zoom.start(t0 + 225, clip, zooms = true, reducedMotion = false)
        assertTrue(zoom.busy)
        assertZ(mid, zoom.z, message = "z snapped on the restart")
        var previous = mid
        for (ms in 0..450L) {
            val z = zoom.update(t0 + 225 + ms)
            assertTrue(z >= previous && z <= 1f, "z went from $previous to $z at +$ms ms after the restart")
            previous = z
        }
        assertZ(1f, previous)
        zoom.update(t0 + 225 + clip + 449)
        assertTrue(zoom.busy, "the second gesture ended early")
        zoom.update(t0 + 225 + clip + 450)
        assertFalse(zoom.busy)
    }

    @Test fun `a gesture that starts while fully zoomed stays fully zoomed and busy without a new edge`() {
        val zoom = started()
        assertZ(1f, zoom.update(t0 + 1_000))
        zoom.start(t0 + 1_000, clip, zooms = true, reducedMotion = false)
        assertTrue(zoom.busy)
        assertEquals(ZoomTimeline.State.HOLD, zoom.state)
        assertZ(1f, zoom.update(t0 + 1_001))
        // The first gesture would have ended at +2150; the second ends 2150 ms after its own start.
        zoom.update(t0 + 2_150)
        assertTrue(zoom.busy, "the first gesture's end cut the second short")
        assertZ(1f, zoom.update(t0 + 1_000 + clip - 1))
        zoom.update(t0 + 1_000 + clip + 449)
        assertTrue(zoom.busy)
        zoom.update(t0 + 1_000 + clip + 450)
        assertFalse(zoom.busy)
    }

    @Test fun `a write that starts during a zoom eases it out and is busy for its own clip`() {
        val zoom = started()
        assertZ(1f, zoom.update(t0 + 1_000))
        zoom.start(t0 + 1_000, clip, zooms = false, reducedMotion = false)
        assertZ(1f, zoom.z, message = "z snapped on the restart")
        assertZ(0.5f, zoom.update(t0 + 1_000 + 225), 1e-6f)
        assertZ(0f, zoom.update(t0 + 1_000 + 450))
        assertTrue(zoom.busy)
        assertZ(0f, zoom.update(t0 + 1_000 + clip - 1))
        assertTrue(zoom.busy)
        zoom.update(t0 + 1_000 + clip)
        assertFalse(zoom.busy)
    }

    @Test fun `a clip shorter than the ease still eases all the way in before it eases out`() {
        val zoom = started(clipMs = 100)
        assertZ(1f, zoom.update(t0 + 450))
        assertTrue(zoom.busy)
        zoom.update(t0 + 899)
        assertTrue(zoom.busy)
        assertZ(0f, zoom.update(t0 + 900))
        assertFalse(zoom.busy)
    }

    @Test fun `z stays within 0 to 1`() {
        for (zoom in listOf(started(), started(zooms = false), started(reducedMotion = true), started(clipMs = 0))) {
            for (at in t0 - 100..t0 + 3_000) {
                val z = zoom.update(at)
                assertTrue(z >= 0f && z <= 1f, "z = $z at $at")
            }
            assertFalse(zoom.busy)
        }
    }
}
