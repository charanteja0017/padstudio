package com.charan.padstudio.pen.screen

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.charan.padstudio.MainViewModel
import com.charan.padstudio.core.pen.PenControl
import com.charan.padstudio.core.pen.PenTrigger
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.CardTone
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.StatusLevel
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.SymIcon
import com.charan.padstudio.ui.components.SymIconButton
import com.charan.padstudio.ui.components.statusInk
import kotlinx.coroutines.delay

/** A capture this screen started: which control, and when (SystemClock.elapsedRealtime, so it survives rotation). */
data class RecordingSession(val controlId: String, val startedAt: Long)

/** How the last capture ended, for the card: what happened, to which control, and when (wall clock, for "recorded 14:05"). */
data class RecordingResult(val controlId: String, val kind: Kind, val reason: String? = null, val at: Long = 0L) {
    enum class Kind { RECORDED, TIMED_OUT, FAILED, STOPPED }
}

/**
 * Starts and cancels captures through [MainViewModel] (startCapture, cancelCapture) and works out how each one
 * ended ([recordingOutcome]), for the recording card. The key itself still reaches the view model through
 * MainActivity.dispatchKeyEvent, as before.
 */
@Stable
class RecordingTracker internal constructor(
    private val model: MainViewModel,
    private val sessionState: MutableState<RecordingSession?>,
    private val resultState: MutableState<RecordingResult?>,
) {
    /** The capture in progress that this screen started, if any. */
    val session: RecordingSession? get() = sessionState.value

    /** How the last capture ended, until dismissed or a new one starts. */
    val result: RecordingResult? get() = resultState.value

    private var before: PenTrigger? = null
    private var messageAtStart: String? = null
    private var userCancelled = false
    internal var stoppedMeanwhile = false

    /** Records [controlId]: Key Mapper pauses and the next key pressed in PadStudio becomes its trigger. */
    fun start(controlId: String) {
        resultState.value = null
        before = model.config.value.controls[controlId]
        messageAtStart = model.message.value
        userCancelled = false
        stoppedMeanwhile = false
        sessionState.value = RecordingSession(controlId, SystemClock.elapsedRealtime())
        model.startCapture(controlId)
    }

    /** Cancel, Back or leaving the tab: ends the capture with nothing to report. */
    fun cancel() {
        userCancelled = true
        model.cancelCapture()
    }

    fun dismiss() {
        resultState.value = null
    }

    /**
     * A saved capture the view model no longer runs (the process was restored, or it ended while the screen was
     * being re-created): how it ended can't be known, so it's dropped rather than guessed.
     */
    internal fun dropSession() {
        sessionState.value = null
    }

    /** The view model's capture changed from [previous] to [now]. */
    internal fun onCapturing(previous: String?, now: String?) {
        if (now != null && sessionState.value?.controlId != now) {
            // A capture this screen didn't see start (it was re-created mid-capture): count from now.
            before = model.config.value.controls[now]
            messageAtStart = model.message.value
            sessionState.value = RecordingSession(now, SystemClock.elapsedRealtime())
        }
        if (previous == null || now != null) return
        val session = sessionState.value ?: return
        sessionState.value = null
        val outcome = recordingOutcome(
            userCancelled = userCancelled,
            elapsedMs = SystemClock.elapsedRealtime() - session.startedAt,
            before = before,
            after = model.config.value.controls[session.controlId],
            messageAtStart = messageAtStart,
            messageNow = model.message.value,
            stoppedMeanwhile = stoppedMeanwhile,
        )
        userCancelled = false
        resultState.value = when (outcome) {
            RecordingOutcome.Cancelled -> null
            is RecordingOutcome.Recorded -> RecordingResult(session.controlId, RecordingResult.Kind.RECORDED, at = System.currentTimeMillis())
            RecordingOutcome.TimedOut -> RecordingResult(session.controlId, RecordingResult.Kind.TIMED_OUT)
            is RecordingOutcome.Failed -> RecordingResult(session.controlId, RecordingResult.Kind.FAILED, outcome.reason)
            RecordingOutcome.Stopped -> RecordingResult(session.controlId, RecordingResult.Kind.STOPPED)
        }
    }
}

/** A [RecordingTracker] whose session and last result survive rotation and process restore. */
@Composable
fun rememberRecordingTracker(model: MainViewModel): RecordingTracker {
    val session = rememberSaveable(stateSaver = SessionSaver) { mutableStateOf<RecordingSession?>(null) }
    val result = rememberSaveable(stateSaver = ResultSaver) { mutableStateOf<RecordingResult?>(null) }
    val tracker = remember(model) { RecordingTracker(model, session, result) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(tracker) {
        var previous: String? = session.value?.controlId
        var first = true
        model.capturing.collect { now ->
            if (first && previous != null && now != previous) {
                tracker.dropSession()
                previous = null
            }
            first = false
            // MainActivity.onStop cancels a capture: an end seen while stopped is that, not a key or a timeout.
            if (now == null && previous != null && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) tracker.stoppedMeanwhile = true
            tracker.onCapturing(previous, now)
            previous = now
        }
    }
    return tracker
}

/**
 * The recording card (plan §13 "Recording"): while listening, the instruction and a 15 s countdown with Cancel;
 * then the result: "Nothing pressed" with Try again after a timeout, the reason after a failure, or the time the
 * control was recorded. [label] names a control id; [timeLabel] formats the time.
 */
@Composable
fun RecordingCard(
    tracker: RecordingTracker,
    capturing: String?,
    label: (String) -> PenControl,
    timeLabel: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val session = tracker.session
    val result = tracker.result
    if (capturing != null) {
        val control = label(capturing)
        val fallbackStart = remember(capturing) { SystemClock.elapsedRealtime() }
        val startedAt = session?.takeIf { it.controlId == capturing }?.startedAt ?: fallbackStart
        var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
        LaunchedEffect(startedAt) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay((1000L - (now - startedAt).mod(1000L)).coerceAtLeast(MIN_TICK_MS))
            }
        }
        StudioCard(modifier, tone = CardTone.High, spacing = 4.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SymIcon(Sym.forControl(control.id), contentDescription = null, tint = scheme.primary, size = 20.dp)
                Text(
                    text = recordPrompt(control),
                    style = type.labelLarge,
                    color = scheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${secondsLeft(now - startedAt)} s",
                    style = type.titleLarge.copy(fontFeatureSettings = "tnum"),
                    color = scheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                PillButton(text = "Cancel", onClick = tracker::cancel, style = PillStyle.Text)
            }
        }
        return
    }
    if (result == null) return
    val control = label(result.controlId)
    LaunchedEffect(result) {
        if (result.kind == RecordingResult.Kind.RECORDED) {
            delay(RECORDED_SHOWN_MS)
            tracker.dismiss()
        }
    }
    val (level, icon, title) = when (result.kind) {
        RecordingResult.Kind.RECORDED -> Triple(StatusLevel.Good, Sym.ok, "${control.label} recorded ${timeLabel(result.at)}")
        RecordingResult.Kind.TIMED_OUT -> Triple(StatusLevel.Warn, Sym.time, "Nothing pressed")
        RecordingResult.Kind.FAILED -> Triple(StatusLevel.Bad, Sym.error, "${control.label} not recorded")
        RecordingResult.Kind.STOPPED -> Triple(StatusLevel.Unknown, Sym.info, "Recording stopped")
    }
    StudioCard(modifier, tone = CardTone.High, spacing = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SymIcon(icon, contentDescription = null, tint = statusInk(level), size = 20.dp)
            Text(
                text = title,
                style = type.labelLarge,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            SymIconButton(icon = Sym.close, contentDescription = "Dismiss", onClick = tracker::dismiss)
        }
        result.reason?.let { Text(text = it, style = type.bodyMedium, color = scheme.onSurfaceVariant) }
        if (result.kind != RecordingResult.Kind.RECORDED) {
            PillButton(text = "Try again", onClick = { tracker.start(result.controlId) }, style = PillStyle.Tonal, icon = Sym.refresh)
        }
    }
}

private val SessionSaver = listSaver<RecordingSession?, Any>(
    save = { session -> if (session == null) emptyList() else listOf(session.controlId, session.startedAt) },
    restore = { saved -> if (saved.size < 2) null else RecordingSession(saved[0] as String, saved[1] as Long) },
)

private val ResultSaver = listSaver<RecordingResult?, Any>(
    save = { result -> if (result == null) emptyList() else listOf(result.controlId, result.kind.name, result.reason.orEmpty(), result.at) },
    restore = { saved ->
        if (saved.size < 4) {
            null
        } else {
            RecordingResult(
                controlId = saved[0] as String,
                kind = RecordingResult.Kind.valueOf(saved[1] as String),
                reason = (saved[2] as String).ifEmpty { null },
                at = saved[3] as Long,
            )
        }
    },
)

private const val MIN_TICK_MS = 50L

/** A successful recording's card goes away by itself after this long; the others wait to be dismissed. */
private const val RECORDED_SHOWN_MS = 5_000L
