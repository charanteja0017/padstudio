package com.charan.padstudio.pen.screen

import com.charan.padstudio.core.pen.PenTrigger
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingLogicTest {
    private val old = PenTrigger.KeyEvent(92, 104, "d", "Pen")
    private val new = PenTrigger.KeyEvent(93, 109, "d", "Pen")

    private fun outcome(
        userCancelled: Boolean = false,
        elapsedMs: Long = 3_000,
        before: PenTrigger? = null,
        after: PenTrigger? = null,
        messageAtStart: String? = null,
        messageNow: String? = null,
        stopped: Boolean = false,
    ) = recordingOutcome(userCancelled, elapsedMs, before, after, messageAtStart, messageNow, stopped)

    @Test fun `the countdown starts at 15 and ends at 0`() {
        assertEquals(15, secondsLeft(0))
        assertEquals(15, secondsLeft(-5))
        assertEquals(12, secondsLeft(3_000))
        assertEquals(13, secondsLeft(2_001))
        assertEquals(1, secondsLeft(14_999))
        assertEquals(0, secondsLeft(15_000))
        assertEquals(0, secondsLeft(20_000))
    }

    @Test fun `cancel wins`() {
        assertEquals(RecordingOutcome.Cancelled, outcome(userCancelled = true, after = new))
    }

    @Test fun `a new trigger is recorded`() {
        assertEquals(RecordingOutcome.Recorded(new), outcome(before = old, after = new))
        assertEquals(RecordingOutcome.Recorded(new), outcome(after = new, messageNow = "unrelated"))
    }

    @Test fun `the same key again counts as recorded`() {
        assertEquals(RecordingOutcome.Recorded(old), outcome(before = old, after = old))
    }

    @Test fun `fifteen seconds with no key is a timeout, even with the view model's message`() {
        val message = "No key reached PadStudio, so HyperOS keeps that button for itself. Key Mapper is listening again."
        assertEquals(RecordingOutcome.TimedOut, outcome(elapsedMs = 15_020, messageNow = message))
        assertEquals(RecordingOutcome.TimedOut, outcome(elapsedMs = 14_900, before = old, after = old))
    }

    @Test fun `a new message is a failure with its reason`() {
        val reason = "Expert mode needs the pen's input device from the probe: run tools/probe-device.sh, then import device facts."
        assertEquals(RecordingOutcome.Failed(reason), outcome(before = old, after = old, messageNow = reason))
        assertEquals(RecordingOutcome.Recorded(old), outcome(before = old, after = old, messageAtStart = "earlier", messageNow = "earlier"))
    }

    @Test fun `leaving the screen stops it without a recording`() {
        assertEquals(RecordingOutcome.Stopped, outcome(before = old, after = old, stopped = true))
        assertEquals(RecordingOutcome.Stopped, outcome())
    }
}
