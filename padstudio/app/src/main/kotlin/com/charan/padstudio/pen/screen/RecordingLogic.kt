package com.charan.padstudio.pen.screen

import com.charan.padstudio.core.pen.PenTrigger
import kotlin.math.ceil

// Recording a pen control (plan §13 "Recording"): MainViewModel.startCapture listens for 15 s, and
// MainActivity.dispatchKeyEvent hands it the next key. The view model says only that the capture ended
// (capturing goes back to null), so the screen works out why from what changed.

/**
 * How long a capture listens: MainViewModel's private CAPTURE_TIMEOUT_MS (15 s), repeated here for the
 * countdown because the view model doesn't expose it.
 */
const val RECORDING_WINDOW_MS: Long = 15_000L

/** The whole seconds left to show on the countdown after [elapsedMs]: 15 at the start, never below 0. */
fun secondsLeft(elapsedMs: Long): Int {
    val left = (RECORDING_WINDOW_MS - elapsedMs.coerceAtLeast(0L)).coerceAtLeast(0L)
    return ceil(left / 1000.0).toInt()
}

/** How a capture ended. */
sealed interface RecordingOutcome {
    /** Cancel, Back or leaving the tab: nothing to show. */
    data object Cancelled : RecordingOutcome

    /** A key arrived and was saved as the control's trigger. */
    data class Recorded(val trigger: PenTrigger) : RecordingOutcome

    /** 15 s passed with no key: "Nothing pressed · Try again". */
    data object TimedOut : RecordingOutcome

    /** A key arrived but couldn't become a trigger; [reason] is the view model's message, when there was one. */
    data class Failed(val reason: String?) : RecordingOutcome

    /** The capture stopped without a key (PadStudio left the screen: MainActivity.onStop cancels it). */
    data object Stopped : RecordingOutcome
}

/**
 * Why a capture ended, from what the screen saw:
 * - [userCancelled]: the screen cancelled it (Cancel, Back, another tab);
 * - [elapsedMs]: time since the screen started it;
 * - [before] and [after]: the control's saved trigger (StudioConfig.controls) at the start and now;
 * - [messageAtStart] and [messageNow]: MainViewModel.message then and now (it reports a failed key there);
 * - [stoppedMeanwhile]: the screen went below STARTED (MainActivity.onStop cancels a capture).
 *
 * A new trigger always means it worked. The same trigger again with no new message also does (the same key
 * recorded twice leaves the config unchanged). Otherwise the time decides between a timeout and a failure.
 */
fun recordingOutcome(
    userCancelled: Boolean,
    elapsedMs: Long,
    before: PenTrigger?,
    after: PenTrigger?,
    messageAtStart: String?,
    messageNow: String?,
    stoppedMeanwhile: Boolean,
): RecordingOutcome {
    val newMessage = messageNow?.takeIf { it != messageAtStart }
    return when {
        userCancelled -> RecordingOutcome.Cancelled
        after != null && after != before -> RecordingOutcome.Recorded(after)
        elapsedMs >= RECORDING_WINDOW_MS - TIMER_SLACK_MS -> RecordingOutcome.TimedOut
        newMessage != null -> RecordingOutcome.Failed(newMessage)
        stoppedMeanwhile -> RecordingOutcome.Stopped
        after != null -> RecordingOutcome.Recorded(after)
        else -> RecordingOutcome.Stopped
    }
}

/**
 * The screen notes the start just before the view model's timer starts, so a timeout is seen at 15 s or later;
 * this only absorbs clock rounding.
 */
private const val TIMER_SLACK_MS = 250L
