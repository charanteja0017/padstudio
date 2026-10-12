package com.charan.padstudio.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.charan.padstudio.core.pen.PenStatus
import com.charan.padstudio.pen.PenModelView
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.BindingChip
import com.charan.padstudio.ui.components.CardTone
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.StatusLevel
import com.charan.padstudio.ui.components.StatusPill
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.SymIcon
import com.charan.padstudio.ui.components.SymIconButton
import com.charan.padstudio.ui.components.statusInk
import com.charan.padstudio.ui.components.symbol
import com.charan.padstudio.ui.theme.Tokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What the hero's buttons do; each is wired by TodayScreen to existing code only. */
internal class HeroActions(
    val onTestPen: () -> Unit,
    val onTogglePen: () -> Unit,
    val onEditControls: () -> Unit,
    val onSend: () -> Unit,
    val onFixKeyMapper: () -> Unit,
    val onRemedy: (HeroNote.Failed) -> Unit,
)

/**
 * The hero (plan §7, §10 Today): the pen on a dark brand card, its status in PenStatus's words, the quick
 * actions, and the live pen with callout pills at the model's markers.
 *
 * The callouts are placed from PenModelView's onMarkers offsets. Those arrive at display rate while the pen
 * moves, so they live in [CalloutController.markers], which only the overlay's placement and drawing read:
 * the hero itself recomposes only when the fit is decided again. Callouts go beside the text when they fit
 * there, else under it, else the gestures become a list of binding chips beside a smaller pen
 * (SCREENS-BRIEF), judged from the real marker offsets and the pills' measured sizes.
 *
 * [heroWidth] is the card's width, from the page layout.
 */
@Composable
internal fun TodayHero(
    status: PenStatus,
    text: HeroText,
    gestures: List<HeroGesture>,
    calloutsPossible: Boolean,
    sentLine: String,
    penEnabled: Boolean,
    note: HeroNote?,
    animationsOn: Boolean,
    heroWidth: Dp,
    actions: HeroActions,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val type = MaterialTheme.typography
    val bodyEm = with(density) { type.bodyLarge.fontSize.toDp().value }
    val geometry = remember(heroWidth, bodyEm) { heroGeometry(heroWidth.value, bodyEm) }
    val pen = rememberPenStageState()
    val pills = rememberPillSizes(gestures)
    val metrics = with(density) {
        CalloutMetrics(
            dotRadius = DOT_RADIUS.toPx(),
            leader = LEADER.toPx(),
            inset = STAGE_INSET.toPx(),
            gap = CALLOUT_GAP.toPx(),
            shiftStep = SHIFT_STEP.toPx(),
            shiftSteps = SHIFT_STEPS,
        )
    }
    val pauseRect: (Size) -> Rect = with(density) {
        val inset = PAUSE_INSET.toPx()
        val side = PAUSE_TARGET.toPx()
        val keepOut: (Size) -> Rect = { stage -> Rect(stage.width - inset - side, stage.height - inset - side, stage.width - inset, stage.height - inset) }
        keepOut
    }
    val stageSize: (HeroArrangement) -> Size = with(density) {
        val side = Size(geometry.sideStageWidthDp.dp.toPx(), geometry.sideStageHeightDp.dp.toPx())
        val stacked = Size(geometry.stackedStageWidthDp.dp.toPx(), geometry.stackedStageHeightDp.dp.toPx())
        val pick: (HeroArrangement) -> Size = { if (it == HeroArrangement.SIDE) side else stacked }
        pick
    }
    val controller = remember { CalloutController() }
    val key = listOf(geometry, pills.px, gestures, calloutsPossible)
    val clip = pen.clip
    SideEffect {
        controller.writing = clip == WRITE
        controller.configure(
            key,
            CalloutInputs(
                geometry = geometry,
                possible = calloutsPossible,
                markerNames = gestures.mapNotNull { it.marker },
                pillSizes = pills.px,
                preferred = gestures.map { if (it.marker == DOUBLE_TAP) PillSide.START else PillSide.END },
                metrics = metrics,
                moveThreshold = with(density) { MOVE_THRESHOLD.toPx() },
                stageSize = stageSize,
                pauseRect = pauseRect,
            ),
        )
    }
    // Decide once the pen has settled at its new size (a report can still describe the old one), and fall
    // back to the list when no markers arrive at all.
    LaunchedEffect(controller.arrangement, controller.epoch) {
        delay(SETTLE_MS)
        controller.settle()
        delay(GIVE_UP_MS)
        controller.giveUp()
    }

    val ink = remember { Animatable(1f) }
    LaunchedEffect(clip == WRITE) {
        if (clip == WRITE && animationsOn) {
            ink.snapTo(0f)
            ink.animateTo(1f, tween(durationMillis = (GESTURE_MS * INK_SHARE).toInt(), easing = LinearEasing))
        } else {
            ink.snapTo(1f)
        }
    }

    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val glow = if (dark) {
        listOf(scheme.primaryContainer.copy(alpha = 0.38f), Color.Transparent)
    } else {
        listOf(scheme.primaryFixedDim.copy(alpha = 0.7f), scheme.primaryFixed.copy(alpha = 0.45f))
    }
    // Until the controller has these inputs (first frame, new size or text), lay out as it will start.
    val arrangement = controller.arrangementFor(key, if (calloutsPossible) geometry.initial else HeroArrangement.LIST)
    StudioCard(modifier.fillMaxWidth(), tone = CardTone.Low, contentPadding = PaddingValues(0.dp), spacing = 0.dp) {
        HeroLayout(
            arrangement = arrangement,
            geometry = geometry,
            controller = controller,
            modifier = Modifier.drawBehind {
                val stage = controller.stageRect
                if (stage.isEmpty) return@drawBehind
                val radius = max(stage.width, stage.height) * 0.75f
                drawRect(Brush.radialGradient(glow, center = stage.center, radius = radius))
            },
            identity = { HeroIdentity(status = status, text = text, onFixKeyMapper = actions.onFixKeyMapper) },
            quickActions = {
                HeroQuickActions(status = status, sentLine = sentLine, penEnabled = penEnabled, pending = note is HeroNote.Pending, actions = actions)
            },
            note = {
                if (note != null) NoteLine(note, actions.onRemedy, Modifier.padding(horizontal = CARD_PADDING))
            },
            pen = {
                val sink = controller.sink
                val ignore = remember { { _: Map<String, Offset> -> } }
                PenModelView(
                    // PenModelView's own test for "on screen", so the gesture clock stops when the pen's does.
                    modifier = Modifier.onVisibilityChanged(minFractionVisible = 0.01f) { pen.visible = it },
                    clip = clip,
                    playing = pen.playing,
                    onMarkers = if (arrangement == HeroArrangement.LIST) ignore else sink,
                    replay = pen.replay,
                )
            },
            overlay = {
                if (arrangement == HeroArrangement.LIST) {
                    GestureList(gestures, onPlay = { gesture -> gesture.clip?.let(pen::play) ?: actions.onEditControls() }, onEdit = actions.onEditControls)
                } else {
                    CalloutOverlay(
                        controller = controller,
                        pen = pen,
                        gestures = gestures,
                        pills = pills,
                        metrics = metrics,
                        ink = ink,
                        onPlay = { gesture -> gesture.clip?.let(pen::play) },
                        onWrite = { pen.play(WRITE) },
                    )
                }
            },
            pause = {
                if (animationsOn) {
                    SymIconButton(
                        icon = if (pen.userPaused) Sym.play else Sym.pause,
                        contentDescription = if (pen.userPaused) "Play the pen animation" else "Pause the pen animation",
                        onClick = { pen.userPaused = !pen.userPaused },
                        style = PillStyle.Outlined,
                    )
                }
            },
        )
    }
}

// ---- The hero's arrangement --------------------------------------------------------------------------------

/**
 * The hero's parts, arranged as [arrangement] says:
 * - SIDE: the pen's name and status top left, the pen on the right. The quick actions go under the name when
 *   they fit beside the pen (its intrinsic height at that width), else full width under both.
 * - STACKED: name, actions, then the pen across the card.
 * - LIST: name, actions, then a smaller pen with the gesture chips beside it.
 *
 * The outcome line ([note]) always goes full width under the rest, so a "Requested" or failure line never
 * moves the pen or its callouts. One layout for every arrangement keeps the pen at the same place in the
 * composition, so the 3D view isn't torn down and restarted when the arrangement changes.
 */
@Composable
private fun HeroLayout(
    arrangement: HeroArrangement,
    geometry: HeroGeometry,
    controller: CalloutController,
    modifier: Modifier,
    identity: @Composable () -> Unit,
    quickActions: @Composable () -> Unit,
    note: @Composable () -> Unit,
    pen: @Composable () -> Unit,
    overlay: @Composable () -> Unit,
    pause: @Composable () -> Unit,
) {
    Layout(contents = listOf(identity, quickActions, note, pen, overlay, pause), modifier = modifier) { slots, constraints ->
        val (identities, actionBlocks, notes, pens, overlays) = slots
        val pauses = slots[5]
        val width = constraints.maxWidth
        val padding = CARD_PADDING.roundToPx()
        val full = Constraints(maxWidth = width)
        val pause = pauses.firstOrNull()?.measure(Constraints())
        val pauseInset = PAUSE_INSET.roundToPx()
        fun stageRect(x: Int, y: Int, w: Int, h: Int): Rect {
            val left = if (layoutDirection == LayoutDirection.Rtl) width - x - w else x
            return Rect(left.toFloat(), y.toFloat(), (left + w).toFloat(), (y + h).toFloat())
        }
        when (arrangement) {
            HeroArrangement.SIDE -> {
                val stageW = geometry.sideStageWidthDp.dp.roundToPx().coerceAtMost(width)
                val stageH = geometry.sideStageHeightDp.dp.roundToPx()
                val leftW = (width - stageW).coerceAtLeast(0)
                val idBlock = identities.first().measure(Constraints(maxWidth = leftW))
                val actionsMeasurable = actionBlocks.first()
                val actionsBeside = idBlock.height + actionsMeasurable.maxIntrinsicHeight(leftW) <= stageH
                val actionsBlock = actionsMeasurable.measure(if (actionsBeside) Constraints(maxWidth = leftW) else full)
                val noteBlock = notes.firstOrNull()?.measure(full)
                val penBlock = pens.first().measure(Constraints.fixed(stageW, stageH))
                val overlayBlock = overlays.firstOrNull()?.measure(Constraints.fixed(stageW, stageH))
                val leftBottom = idBlock.height + if (actionsBeside) actionsBlock.height else 0
                val topH = max(stageH, leftBottom)
                var y = topH
                val actionsY = if (actionsBeside) idBlock.height else y.also { y += actionsBlock.height }
                val noteY = y.also { y += noteBlock?.height ?: 0 }
                // The pen runs to the card's bottom edge unless something sits under it or the text is longer.
                val height = if (y == stageH && leftBottom + padding <= stageH) stageH else y + padding
                val stageX = width - stageW
                val stageY = (topH - stageH) / 2
                controller.stageRect = stageRect(stageX, stageY, stageW, stageH)
                layout(width, height) {
                    penBlock.placeRelative(stageX, stageY)
                    overlayBlock?.placeRelative(stageX, stageY)
                    pause?.placeRelative(stageX + stageW - pauseInset - pause.width, stageY + stageH - pauseInset - pause.height)
                    idBlock.placeRelative(0, 0)
                    actionsBlock.placeRelative(0, actionsY)
                    noteBlock?.placeRelative(0, noteY)
                }
            }
            HeroArrangement.STACKED -> {
                val stageW = geometry.stackedStageWidthDp.dp.roundToPx().coerceAtMost(width)
                val stageH = geometry.stackedStageHeightDp.dp.roundToPx()
                val idBlock = identities.first().measure(full)
                val actionsBlock = actionBlocks.first().measure(full)
                val noteBlock = notes.firstOrNull()?.measure(full)
                val penBlock = pens.first().measure(Constraints.fixed(stageW, stageH))
                val overlayBlock = overlays.firstOrNull()?.measure(Constraints.fixed(stageW, stageH))
                val actionsY = idBlock.height
                val noteY = actionsY + actionsBlock.height
                val stageY = noteY + (noteBlock?.height ?: 0)
                val stageX = (width - stageW) / 2
                controller.stageRect = stageRect(stageX, stageY, stageW, stageH)
                layout(width, stageY + stageH) {
                    penBlock.placeRelative(stageX, stageY)
                    overlayBlock?.placeRelative(stageX, stageY)
                    pause?.placeRelative(stageX + stageW - pauseInset - pause.width, stageY + stageH - pauseInset - pause.height)
                    idBlock.placeRelative(0, 0)
                    actionsBlock.placeRelative(0, actionsY)
                    noteBlock?.placeRelative(0, noteY)
                }
            }
            HeroArrangement.LIST -> {
                val idBlock = identities.first().measure(full)
                val actionsBlock = actionBlocks.first().measure(full)
                val noteBlock = notes.firstOrNull()?.measure(full)
                val penW = LIST_PEN_WIDTH.roundToPx()
                val gap = Tokens.GapM.roundToPx()
                val listMax = (width - 2 * padding - penW - gap).coerceIn(0, LIST_MAX_WIDTH.roundToPx())
                val list = overlays.firstOrNull()?.measure(Constraints(maxWidth = listMax))
                val rowH = max(list?.height ?: 0, LIST_PEN_MIN_HEIGHT.roundToPx())
                val penBlock = pens.first().measure(Constraints.fixed(penW, rowH))
                val actionsY = idBlock.height
                val noteY = actionsY + actionsBlock.height
                val rowY = noteY + (noteBlock?.height ?: 0) + Tokens.GapS.roundToPx()
                controller.stageRect = stageRect(padding, rowY, penW, rowH)
                layout(width, rowY + rowH + padding) {
                    penBlock.placeRelative(padding, rowY)
                    list?.placeRelative(padding + penW + gap, rowY + (rowH - list.height) / 2)
                    pause?.placeRelative(padding + penW - pause.width, rowY + rowH - pause.height)
                    idBlock.placeRelative(0, 0)
                    actionsBlock.placeRelative(0, actionsY)
                    noteBlock?.placeRelative(0, noteY)
                }
            }
        }
    }
}

// ---- Name, status and quick actions ------------------------------------------------------------------------

/** "YOUR PEN · EXPERT SETUP", the pen's name, "4 controls recorded", and the headline pill (with Fix when Key Mapper is off). */
@Composable
private fun HeroIdentity(status: PenStatus, text: HeroText, onFixKeyMapper: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Column(Modifier.padding(start = CARD_PADDING, top = CARD_PADDING, end = 12.dp)) {
        Text(
            text = text.overline.uppercase(),
            style = type.labelMedium.copy(letterSpacing = 0.08.em),
            color = scheme.onSurfaceVariant,
        )
        Text(
            text = text.title,
            style = type.headlineMedium.copy(fontWeight = Tokens.WeightMedium),
            color = scheme.onSurface,
            modifier = Modifier.padding(top = 4.dp).semantics { heading() },
        )
        Text(text = text.subtitle, style = type.bodyLarge, color = scheme.onSurfaceVariant)
        FlowRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(text = status.headline, level = headlineLevel(status))
            if (status.keyMapper == PenStatus.KeyMapper.OFF) {
                PillButton(text = "Fix", onClick = onFixKeyMapper, style = PillStyle.Text)
            }
        }
    }
}

/** Test pen, Pause or Resume pen, Edit controls, then when it was sent, with Send when there is something to send. */
@Composable
private fun HeroQuickActions(status: PenStatus, sentLine: String, penEnabled: Boolean, pending: Boolean, actions: HeroActions) {
    Column(Modifier.padding(start = CARD_PADDING, top = 12.dp, end = CARD_PADDING)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(text = "Test pen", onClick = actions.onTestPen, icon = Sym.testPen)
            PillButton(
                text = penToggleLabel(penEnabled),
                onClick = actions.onTogglePen,
                style = PillStyle.Outlined,
                icon = if (penEnabled) Sym.pause else Sym.play,
            )
            PillButton(text = "Edit controls", onClick = actions.onEditControls, style = PillStyle.Outlined, icon = Sym.edit)
        }
        SentLine(status, sentLine)
        if (status.needsSend) {
            PillButton(
                text = "Send to Key Mapper",
                onClick = actions.onSend,
                style = PillStyle.Tonal,
                icon = Sym.send,
                enabled = !pending,
            )
        }
    }
}

/** "Sent to Key Mapper 14:05", or what still needs sending, with its symbol. */
@Composable
private fun SentLine(status: PenStatus, sentLine: String) {
    val level = status.sent.tone.level()
    Row(
        modifier = Modifier.padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (status.sent == PenStatus.Sent.SENT) {
            SymIcon(Sym.send, contentDescription = null, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant, autoMirror = true)
        } else {
            SymIcon(level.symbol, contentDescription = null, size = 18.dp, tint = statusInk(level))
        }
        Text(
            text = sentLine,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The outcome of the last quick action, announced politely: pending, requested (unknown) or failed with Fix or Retry. */
@Composable
private fun NoteLine(note: HeroNote, onRemedy: (HeroNote.Failed) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    FlowRow(
        modifier = modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        when (note) {
            is HeroNote.Pending -> {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(text = note.text, style = type.bodyMedium, color = scheme.onSurface)
            }
            is HeroNote.Done -> {
                SymIcon(Sym.ok, contentDescription = null, size = 18.dp, tint = statusInk(StatusLevel.Good))
                Text(text = note.text, style = type.bodyMedium, color = scheme.onSurface)
            }
            is HeroNote.Requested -> {
                SymIcon(Sym.time, contentDescription = null, size = 18.dp, tint = statusInk(StatusLevel.Unknown))
                Text(text = note.text, style = type.bodyMedium, color = scheme.onSurface)
            }
            is HeroNote.Failed -> {
                SymIcon(Sym.error, contentDescription = null, size = 18.dp, tint = statusInk(StatusLevel.Bad))
                Text(text = note.reason, style = type.bodyMedium, color = scheme.onSurface)
                PillButton(text = note.remedy.label, onClick = { onRemedy(note) }, style = PillStyle.Text)
            }
        }
    }
}

// ---- The pen's clip and playing state ----------------------------------------------------------------------

/** One request to play a clip; [serial] makes a repeat of the same clip a new request. */
private data class ClipRequest(val clip: String, val serial: Int)

/**
 * The clip PenModelView plays, its replay counter, and whether it plays. A callout's gesture plays once
 * (even while the idle sway is paused, as the mock does) and then the pen rests; Pause holds the pen. Each
 * request bumps [replay], so tapping the same callout again plays the gesture again from its start. [clip]
 * goes back to `idle` when the gesture is over, which the view never treats as cutting it short. How long a
 * gesture lasts is counted as the pen counts it ([GestureClock]): in frames, while it plays and is [visible].
 */
@Stable
private class PenStageState(private val paused: MutableState<Boolean>) {
    /** Whether any of the pen is on screen, as PenModelView judges it (it stops its clock otherwise). */
    var visible by mutableStateOf(true)

    var clip by mutableStateOf(IDLE)
        private set

    /** PenModelView's `replay`: a new value plays [clip] again from its first frame. */
    var replay by mutableIntStateOf(0)
        private set

    var userPaused: Boolean
        get() = paused.value
        set(value) {
            paused.value = value
        }

    private var oneShot by mutableStateOf(false)

    val playing: Boolean get() = !paused.value || oneShot

    private var request by mutableStateOf(ClipRequest(IDLE, 0))

    fun play(clip: String) {
        request = ClipRequest(clip, request.serial + 1)
    }

    suspend fun run() {
        snapshotFlow { request }.collectLatest { next ->
            if (next.serial == 0) return@collectLatest
            try {
                perform(next.clip)
            } finally {
                clip = IDLE
                oneShot = false
            }
        }
    }

    private suspend fun perform(target: String) {
        oneShot = paused.value
        clip = target
        replay++
        // The clip lasts as long as the pen takes to play it: frames only, while it plays and is on screen. A
        // wall-clock timer ran on while the pen stood still (the app in the background, the pen scrolled away),
        // so `clip` went back to idle mid-gesture: the callouts showed again over a pen still tilted by `write`,
        // and its moved markers could push the hero into the stacked or list layout for good.
        val time = GestureClock(GESTURE_MS * NANOS_PER_MS)
        while (!time.done) {
            if (!(playing && visible)) {
                time.hold()
                snapshotFlow { playing && visible }.first { it }
            }
            withFrameNanos(time::frame)
        }
    }
}

@Composable
private fun rememberPenStageState(): PenStageState {
    val paused = rememberSaveable { mutableStateOf(false) }
    val state = remember(paused) { PenStageState(paused) }
    LaunchedEffect(state) { state.run() }
    return state
}

// ---- Callouts ----------------------------------------------------------------------------------------------

/** Everything the fit needs besides the markers, refreshed from each composition. */
private class CalloutInputs(
    val geometry: HeroGeometry,
    val possible: Boolean,
    val markerNames: List<String>,
    val pillSizes: List<Size>,
    val preferred: List<PillSide>,
    val metrics: CalloutMetrics,
    val moveThreshold: Float,
    val stageSize: (HeroArrangement) -> Size,
    val pauseRect: (Size) -> Rect,
)

/**
 * Holds the pen's marker offsets and decides where the callouts go.
 *
 * - [markers] changes at display rate while the pen moves: only the callout overlay's placement and drawing
 *   read it, so it never recomposes anything.
 * - [arrangement] and [placements] change only when the fit is decided again: when the inputs change, when
 *   the pen first reports at a new size, or when the markers move further than a sway would (never during
 *   `write`, whose 22° tilt moves them by design).
 */
@Stable
private class CalloutController {
    var markers by mutableStateOf<Map<String, Offset>>(emptyMap())
        private set
    var arrangement by mutableStateOf(HeroArrangement.SIDE)
        private set
    var placements by mutableStateOf<List<PillPlacement>?>(null)
        private set

    /** Bumped when the inputs change, so the settle timer restarts even if the arrangement is the same. */
    var epoch by mutableIntStateOf(0)
        private set

    /** Where the pen stands in the hero, for the glow behind it; written by the hero's layout. */
    var stageRect by mutableStateOf(Rect.Zero)

    var writing = false
    private var key: Any? = null
    private var inputs: CalloutInputs? = null
    private var settled = false
    private var decidedFrom: Map<String, Offset> = emptyMap()

    /** [arrangement] once these inputs are in; before that (first frame, new size or text), [initial]. */
    fun arrangementFor(key: Any, initial: HeroArrangement): HeroArrangement = if (key == this.key) arrangement else initial

    fun configure(key: Any, inputs: CalloutInputs) {
        this.inputs = inputs
        if (key == this.key) return
        this.key = key
        settled = false
        placements = null
        arrangement = if (inputs.possible) inputs.geometry.initial else HeroArrangement.LIST
        epoch++
    }

    val sink: (Map<String, Offset>) -> Unit = { reported ->
        if (arrangement != HeroArrangement.LIST) {
            markers = reported
            if (settled) {
                val current = placements
                if (current == null) {
                    decide(reported)
                } else if (!writing && markersMoved(decidedFrom, reported, inputs?.markerNames.orEmpty(), inputs?.moveThreshold ?: Float.MAX_VALUE)) {
                    decide(reported)
                }
            }
        }
    }

    /** The pen has had time to report at its current size: decide with what it last said. */
    fun settle() {
        settled = true
        if (placements == null && arrangement != HeroArrangement.LIST && markers.isNotEmpty()) decide(markers)
    }

    /** No usable markers came: the list needs none. */
    fun giveUp() {
        if (placements == null && arrangement != HeroArrangement.LIST) arrangement = HeroArrangement.LIST
    }

    private fun decide(reported: Map<String, Offset>) {
        val input = inputs ?: return
        val current = arrangement
        if (current == HeroArrangement.LIST) return
        val anchors = input.markerNames.map { reported[it] ?: return }
        if (anchors.size != input.pillSizes.size) return
        val stage = input.stageSize(current)
        val fit = fitCallouts(stage, anchors, input.pillSizes, input.preferred, listOf(input.pauseRect(stage)), penBar(reported), input.metrics)
        when {
            fit != null -> {
                decidedFrom = reported
                if (fit != placements) placements = fit
            }
            current == HeroArrangement.SIDE -> {
                // Try under the text, where the stage is wider; the pen reports again at its new size.
                placements = null
                settled = false
                arrangement = HeroArrangement.STACKED
            }
            else -> {
                placements = null
                arrangement = HeroArrangement.LIST
            }
        }
    }
}

/** The pills' sizes, measured from their text: [px] for the fit, [dp] for the pills themselves. */
private class PillSizes(val px: List<Size>, val dp: List<Pair<Dp, Dp>>)

@Composable
private fun rememberPillSizes(gestures: List<HeroGesture>): PillSizes {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelLarge
    val actionStyle = MaterialTheme.typography.bodySmall
    return remember(gestures, labelStyle, actionStyle, density, measurer) {
        val padH = with(density) { PILL_PADDING_H.toPx() }
        val padV = with(density) { PILL_PADDING_V.toPx() }
        val minH = with(density) { PILL_MIN_HEIGHT.toPx() }
        val px = gestures.map { gesture ->
            val label = measurer.measure(gesture.label, labelStyle, maxLines = 1, softWrap = false).size
            val action = measurer.measure(gesture.action, actionStyle, maxLines = 1, softWrap = false).size
            Size(
                ceil(max(label.width, action.width) + 2 * padH),
                ceil(max(minH, label.height + action.height + 2 * padV)),
            )
        }
        PillSizes(px, px.map { with(density) { it.width.toDp() to it.height.toDp() } })
    }
}

/**
 * The callout pills, their leaders and marker dots, and the ink stroke at the nib. Placement and drawing
 * read the live marker offsets; measuring doesn't, so a moving pen only re-places and redraws.
 */
@Composable
private fun CalloutOverlay(
    controller: CalloutController,
    pen: PenStageState,
    gestures: List<HeroGesture>,
    pills: PillSizes,
    metrics: CalloutMetrics,
    ink: Animatable<Float, *>,
    onPlay: (HeroGesture) -> Unit,
    onWrite: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val primary = scheme.primary
    val inkTarget = with(density) { Size(INK_TARGET_W.toPx(), INK_TARGET_H.toPx()) }
    val unit = with(density) { 1.dp.toPx() }
    val currentGestures by rememberUpdatedState(gestures)
    Layout(
        modifier = Modifier.drawBehind {
            val markers = controller.markers
            drawInk(markers, ink.value, primary, unit, metrics.inset)
            val placements = controller.placements ?: return@drawBehind
            if (pen.clip == WRITE) return@drawBehind
            currentGestures.forEachIndexed { index, gesture ->
                val anchor = markers[gesture.marker] ?: return@forEachIndexed
                val size = pills.px.getOrNull(index) ?: return@forEachIndexed
                val placement = placements.getOrNull(index) ?: return@forEachIndexed
                val rect = pillRect(anchor, size, placement, metrics)
                val end = Offset(if (placement.side == PillSide.START) rect.right else rect.left, rect.center.y)
                drawLine(primary.copy(alpha = 0.8f), anchor, end, strokeWidth = 1.5f * unit, cap = StrokeCap.Round)
                drawCircle(primary.copy(alpha = 0.28f), radius = metrics.dotRadius + 4f * unit, center = anchor)
                drawCircle(primary, radius = metrics.dotRadius, center = anchor)
            }
        },
        content = {
            gestures.forEachIndexed { index, gesture ->
                val (width, height) = pills.dp[index]
                CalloutPill(gesture, width, height, onClick = { onPlay(gesture) })
            }
            InkTarget(onClick = onWrite)
        },
    ) { measurables, constraints ->
        val pillCount = gestures.size
        val pillPlaceables = measurables.take(pillCount).mapIndexed { index, measurable ->
            val size = pills.px[index]
            measurable.measure(Constraints.fixed(size.width.roundToInt(), size.height.roundToInt()))
        }
        val inkPlaceable = measurables.getOrNull(pillCount)?.measure(Constraints.fixed(inkTarget.width.roundToInt(), inkTarget.height.roundToInt()))
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            // Read here, in placement: a moving pen re-places the pills without measuring or composing anything.
            val markers = controller.markers
            inkBounds(markers, unit, metrics.inset)?.let { bounds ->
                inkPlaceable?.place(
                    (bounds.center.x - inkTarget.width / 2f).roundToInt().coerceIn(0, (width - inkTarget.width.roundToInt()).coerceAtLeast(0)),
                    (bounds.center.y - inkTarget.height / 2f).roundToInt().coerceIn(0, (height - inkTarget.height.roundToInt()).coerceAtLeast(0)),
                )
            }
            val placements = controller.placements ?: return@layout
            if (pen.clip == WRITE) return@layout
            pillPlaceables.forEachIndexed { index, placeable ->
                val anchor = markers[gestures[index].marker] ?: return@forEachIndexed
                val placement = placements.getOrNull(index) ?: return@forEachIndexed
                val rect = pillRect(anchor, pills.px[index], placement, metrics)
                placeable.place(
                    rect.left.roundToInt().coerceIn(0, (width - placeable.width).coerceAtLeast(0)),
                    rect.top.roundToInt().coerceIn(0, (height - placeable.height).coerceAtLeast(0)),
                )
            }
        }
    }
}

/** An outlined pill as the reference draws its callouts: the gesture, and under it what it does. */
@Composable
private fun CalloutPill(gesture: HeroGesture, width: Dp, height: Dp, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier = Modifier
            .size(width, height)
            .focusOutline(focused, shape, scheme.secondary)
            .clip(shape)
            .background(scheme.surfaceContainerLow.copy(alpha = 0.88f))
            .border(1.5.dp, scheme.primary.copy(alpha = 0.75f), shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClickLabel = "Show the gesture",
                onClick = onClick,
            )
            .padding(horizontal = PILL_PADDING_H, vertical = PILL_PADDING_V),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = gesture.label, style = type.labelLarge, color = scheme.onSurface, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
        Text(text = gesture.action, style = type.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
    }
}

/** A touch target over the ink stroke: the pen writes it again. */
@Composable
private fun InkTarget(onClick: () -> Unit) {
    val shape = RoundedCornerShape(Tokens.CornerS)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier
            .focusOutline(focused, shape, MaterialTheme.colorScheme.secondary)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Show the pen writing" },
    )
}

/** The gestures as binding chips, when callouts don't fit or the pen has buttons the model can't show. */
@Composable
private fun GestureList(gestures: List<HeroGesture>, onPlay: (HeroGesture) -> Unit, onEdit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        gestures.forEach { gesture ->
            BindingChip(
                onClick = { if (gesture.intent == null) onEdit() else onPlay(gesture) },
                modifier = Modifier.fillMaxWidth(),
                action = gesture.action.takeIf { gesture.intent != null },
                icon = gesture.intent?.let(Sym::forIntent),
                control = gesture.label,
                addLabel = "Choose an action",
            )
        }
    }
}

// ---- Ink at the nib ------------------------------------------------------------------------------------------

/** The stroke at the nib, scaled to the pen and kept inside the stage. [progress] draws part of it (the `write` clip). */
private fun DrawScope.drawInk(markers: Map<String, Offset>, progress: Float, color: Color, unit: Float, inset: Float) {
    val path = inkPath(markers, unit, inset) ?: return
    val shown = if (progress >= 1f) {
        path
    } else {
        val measure = PathMeasure().apply { setPath(path, false) }
        Path().also { measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), it, true) }
    }
    drawPath(shown, color.copy(alpha = 0.25f), style = Stroke(width = 7f * unit, cap = StrokeCap.Round))
    drawPath(shown, color, style = Stroke(width = 2.5f * unit, cap = StrokeCap.Round))
}

/** The ink stroke's bounds, for its touch target. */
private fun inkBounds(markers: Map<String, Offset>, unit: Float, inset: Float): Rect? = inkPath(markers, unit, inset)?.getBounds()

/**
 * The mock's ink loop (docs/redesign/mock-today.html), ending at the nib: as long as the mock draws it next
 * to its 250 dp pen, scaled with this pen, and smaller when the nib is near the stage's edge.
 */
private fun inkPath(markers: Map<String, Offset>, unit: Float, inset: Float): Path? {
    val bar = penBar(markers) ?: return null
    val nib = bar.nib
    val penLength = (bar.top - bar.nib).getDistance()
    val room = (nib.x - inset) / (INK_REACH * unit)
    val scale = min(penLength / (INK_PEN_LENGTH * unit), room)
    if (scale <= 0.2f) return null
    val s = scale * unit
    return Path().apply {
        moveTo(nib.x - 54f * s, nib.y - 8f * s)
        cubicTo(nib.x - 93f * s, nib.y - 1.5f * s, nib.x - 119f * s, nib.y + 11.5f * s, nib.x - 102f * s, nib.y + 20f * s)
        cubicTo(nib.x - 82f * s, nib.y + 28.5f * s, nib.x - 39f * s, nib.y + 16.5f * s, nib.x, nib.y)
    }
}

/**
 * The kit's focus ring for the pills and the ink target (ui/components keeps its own private): 3 dp in
 * [color], 2 dp outside [shape], only while [focused] by keyboard.
 */
private fun Modifier.focusOutline(focused: Boolean, shape: Shape, color: Color): Modifier = this.drawWithContent {
    drawContent()
    if (focused) {
        val stroke = 3.dp.toPx()
        val offset = 2.dp.toPx() + stroke / 2f
        val ring = Size(size.width + 2f * offset, size.height + 2f * offset)
        translate(left = -offset, top = -offset) {
            drawOutline(shape.createOutline(ring, layoutDirection, this), color, style = Stroke(width = stroke))
        }
    }
}

internal const val IDLE = "idle"
internal const val WRITE = "write"

/** The bundled model's gesture clips last 1.7 s (task E's PenModelView); a little more so the pen is back at rest. */
private const val GESTURE_MS = 1_850L
private const val NANOS_PER_MS = 1_000_000L

/** The ink is drawn over the first 68 % of `write`, as the motion kit draws it. */
private const val INK_SHARE = 0.68f
private const val SETTLE_MS = 160L
private const val GIVE_UP_MS = 2_000L
private val CARD_PADDING = 20.dp
private val DOT_RADIUS = 6.dp
private val LEADER = 22.dp
private val STAGE_INSET = 8.dp
private val CALLOUT_GAP = 8.dp
private val SHIFT_STEP = 12.dp
private const val SHIFT_STEPS = 2
private val MOVE_THRESHOLD = 12.dp
private val PILL_PADDING_H = 14.dp
private val PILL_PADDING_V = 6.dp
private val PILL_MIN_HEIGHT = 48.dp
private val PAUSE_INSET = 12.dp
private val PAUSE_TARGET = 48.dp
private val LIST_PEN_WIDTH = 140.dp
private val LIST_PEN_MIN_HEIGHT = 176.dp
private val LIST_MAX_WIDTH = 340.dp
private val INK_TARGET_W = 72.dp
private val INK_TARGET_H = 48.dp

/** The mock's ink loop reaches 119 dp left of the nib, beside a 250 dp pen. */
private const val INK_REACH = 119f
private const val INK_PEN_LENGTH = 250f
