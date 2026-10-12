package com.charan.padstudio.pen.screen

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.charan.padstudio.pen.PenModelView
import com.charan.padstudio.ui.components.BindingChip
import com.charan.padstudio.ui.components.BindingChipDefaults
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** One callout on the pen: a control, its main press's action here (null: not set), and the marker it points at. */
@Immutable
data class StageChip(
    val controlId: String,
    val control: String,
    val action: String?,
    @param:DrawableRes val icon: Int?,
    val marker: String,
)

/**
 * The pen drawing with its callouts (plan §10 "Pen", right pane): [PenModelView] filling the stage, a dot on
 * each control's marker, a hairline to that control's binding chip, and the chip beside the pen. Tapping a chip
 * calls [onChip] with its control. The live pen's markers move every animated frame; they reach only the dots and
 * hairlines, in the draw phase. The chips are placed from a steady copy that changes only when the pen moves
 * further than a sway ([STEADY_THRESHOLD], a resize does), so they never jump or flip while the pen sways.
 * When the stage itself is resized (the side sheet opening, a window resize), the pen view re-frames a frame or
 * two later; until its new markers arrive the chips are hidden rather than placed from the old ones.
 *
 * When the chips can't fit beside the pen (large text, a narrow stage), the stage shows them as a list next to
 * a smaller pen instead: a measured decision ([placeCallouts] with the chips' measured sizes), not a font-scale
 * threshold. The pen view keeps its full-stage layout in both forms (the list form only scales its drawing), so
 * its markers, and the decision, don't change when the form does.
 *
 * [reserveBottomEnd] is a corner the chips keep clear of (the animation's pause button). Bumping [replay] plays
 * [clip] again from its start (PenModelView's replay).
 */
@Composable
fun PenStage(
    chips: List<StageChip>,
    selectedControl: String?,
    clip: String,
    playing: Boolean,
    onChip: (String) -> Unit,
    modifier: Modifier = Modifier,
    reserveBottomEnd: DpSize = DpSize.Zero,
    replay: Int = 0,
) {
    val board = remember { MarkerBoard() }
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MARKER_WAIT_MS)
        waited = true
    }
    // After a resize, should the pen view's re-framed markers not come (a parked renderer), the last ones it
    // reported stand after the same wait, so the chips never stay hidden.
    LaunchedEffect(board) {
        snapshotFlow { board.awaiting }.collectLatest { size ->
            if (size != null) {
                delay(MARKER_WAIT_MS)
                board.settle()
            }
        }
    }
    val scheme = MaterialTheme.colorScheme
    val lineColor = scheme.onSurface.copy(alpha = LINE_ALPHA)
    val dotColor = scheme.primary
    val ringColor = scheme.surfaceContainerLow.copy(alpha = RING_ALPHA)
    val selectedColor = scheme.primary
    val hideCallouts = clip == WRITE_CLIP
    val reportMarkers: (Map<String, Offset>) -> Unit = remember(board) { { markers -> board.report(markers) } }

    SubcomposeLayout(modifier) { constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else FALLBACK_SIZE.roundToPx()
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else FALLBACK_SIZE.roundToPx()
        val full = Constraints.fixed(width, height)
        board.threshold = STEADY_THRESHOLD.toPx()
        // Markers taken at another stage size (before a resize) would misplace the chips: the pen re-frames.
        val stale = !board.measured(IntSize(width, height))

        // The chips' natural sizes, from copies that are never placed (no semantics, so TalkBack never finds them).
        val sizes = subcompose(StageSlot.Measure) {
            chips.forEach { chip ->
                Box(Modifier.clearAndSetSemantics { }) { StageBindingChip(chip, selected = false, selectedColor, onClick = {}) }
            }
        }.map { it.measure(Constraints()) }

        val markers = board.steady
        val haveMarkers = !stale && chips.isNotEmpty() && chips.all { it.marker in markers }
        val spots = if (haveMarkers && !hideCallouts) {
            val anchors = chips.map { chip -> markers.getValue(chip.marker) }
            val from = markers[NIB_MARKER] ?: anchors.maxBy { it.y }
            val to = markers[TOP_MARKER] ?: anchors.minBy { it.y }
            val span = hypot(to.x - from.x, to.y - from.y)
            val reserve = StageRect(
                left = (width - reserveBottomEnd.width.toPx()),
                top = (height - reserveBottomEnd.height.toPx()),
                right = width.toFloat(),
                bottom = height.toFloat(),
            )
            val rules = CalloutRules(
                margin = EDGE_MARGIN.roundToPx(),
                gap = CHIP_GAP.roundToPx(),
                lead = LEAD.roundToPx(),
                penHalfWidth = max(PEN_HALF_WIDTH_SHARE * span, MIN_PEN_HALF_WIDTH.toPx()),
                obstacles = if (reserveBottomEnd == DpSize.Zero) emptyList() else listOf(reserve),
            )
            val callouts = chips.mapIndexed { i, chip ->
                CalloutChip(chip.controlId, sizes[i].width, sizes[i].height, StagePoint(anchors[i].x, anchors[i].y))
            }
            placeCallouts(width, height, callouts, StagePoint(from.x, from.y), StagePoint(to.x, to.y), rules)
        } else {
            null
        }
        val form = when {
            hideCallouts -> StageForm.PenOnly
            spots != null -> StageForm.Callouts
            stale -> StageForm.PenOnly // just resized: the pen's markers at its new size are a frame or two away
            haveMarkers || waited || chips.isEmpty() -> StageForm.List
            else -> StageForm.PenOnly // the first frames, before the pen has reported its markers
        }

        val pen = subcompose(StageSlot.Pen) {
            PenModelView(modifier = Modifier.fillMaxSize(), clip = clip, playing = playing, onMarkers = reportMarkers, replay = replay)
        }.map { it.measure(full) }

        when (form) {
            StageForm.Callouts -> {
                val placed = requireNotNull(spots).associateBy { it.id }
                val lines = subcompose(StageSlot.Lines) {
                    CalloutLines(chips, placed, board, lineColor, dotColor, ringColor)
                }.map { it.measure(full) }
                // Composed top to bottom as placed, so keyboard focus moves down the chips in reading order.
                val ordered = chips.sortedWith(compareBy({ placed.getValue(it.controlId).y }, { placed.getValue(it.controlId).x }))
                val chipPlaceables = subcompose(StageSlot.Chips) {
                    ordered.forEach { chip ->
                        StageBindingChip(chip, chip.controlId == selectedControl, selectedColor) { onChip(chip.controlId) }
                    }
                }.map { it.measure(Constraints()) }
                layout(width, height) {
                    pen.forEach { it.place(0, 0) }
                    lines.forEach { it.place(0, 0) }
                    chipPlaceables.forEachIndexed { i, placeable ->
                        val spot = placed.getValue(ordered[i].controlId)
                        placeable.place(spot.x, spot.y)
                    }
                }
            }
            StageForm.List -> {
                val gap = CHIP_GAP.roundToPx() * 2
                val penSlot = (width * LIST_PEN_SHARE).roundToInt().coerceAtLeast(MIN_LIST_PEN.roundToPx()).coerceAtMost(width / 2)
                val listWidth = (width - penSlot - gap).coerceAtLeast(0)
                // Top to bottom as the controls sit on the pen, when its markers are known.
                val listed = chips.sortedBy { markers[it.marker]?.y ?: 0f }
                val list = subcompose(StageSlot.List) {
                    StageList(listed, selectedControl, selectedColor, onChip)
                }.map { it.measure(Constraints(maxWidth = listWidth, maxHeight = height)) }
                val scale = penSlot.toFloat() / width
                layout(width, height) {
                    pen.forEach {
                        it.placeWithLayer(0, 0) {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                    }
                    list.forEach { it.place(penSlot + gap, ((height - it.height) / 2).coerceAtLeast(0)) }
                }
            }
            StageForm.PenOnly -> layout(width, height) { pen.forEach { it.place(0, 0) } }
        }
    }
}

/** A binding chip as the stage shows it; the chosen control's chip gets the kit's 2 dp primary edge. */
@Composable
private fun StageBindingChip(chip: StageChip, selected: Boolean, selectedColor: Color, onClick: () -> Unit) {
    val edge = if (selected) Modifier.border(2.dp, selectedColor, BindingChipDefaults.Shape) else Modifier
    BindingChip(
        onClick = onClick,
        modifier = edge.semantics { this.selected = selected },
        action = chip.action,
        icon = chip.icon,
        control = chip.control,
        addLabel = NOT_SET,
    )
}

/** The callouts as a list beside the smaller pen. */
@Composable
private fun StageList(chips: List<StageChip>, selectedControl: String?, selectedColor: Color, onChip: (String) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
        chips.forEach { chip ->
            Box(Modifier.fillMaxWidth()) {
                StageBindingChip(chip, chip.controlId == selectedControl, selectedColor) { onChip(chip.controlId) }
            }
        }
    }
}

/** The dots on the markers and the hairlines to the chips, redrawn (never re-laid-out) as the pen moves. */
@Composable
private fun CalloutLines(
    chips: List<StageChip>,
    spots: Map<String, CalloutSpot>,
    board: MarkerBoard,
    lineColor: Color,
    dotColor: Color,
    ringColor: Color,
) {
    Canvas(Modifier.fillMaxSize()) {
        val markers = board.live
        val stroke = LINE_WIDTH.toPx()
        val dot = DOT_RADIUS.toPx()
        chips.forEach { chip ->
            val marker = markers[chip.marker] ?: return@forEach
            val spot = spots[chip.controlId] ?: return@forEach
            val end = spot.lineEnd
            drawLine(lineColor, start = marker, end = Offset(end.x, end.y), strokeWidth = stroke)
            drawCircle(ringColor, radius = dot + RING_WIDTH.toPx(), center = marker)
            drawCircle(dotColor, radius = dot, center = marker)
        }
    }
}

/**
 * Where the pen's markers are: [live] as last reported (read only while drawing), and [steady], which follows
 * only moves larger than [threshold] pixels, for laying out the chips.
 *
 * [steady] also remembers the stage size it was taken at. After the stage is resized by more than
 * [RESIZE_SLACK] thresholds, the pen view re-frames a frame or two later, and in between it may still report
 * markers from its old framing (an animated frame runs before it recomposes). Those have moved only by the sway,
 * so they're not taken; the first report that has moved further is the re-framed pen. Until then
 * [measured] says the steady copy is stale, and [awaiting] holds the new size for the fallback ([settle]).
 * A smaller resize moves the markers by less than about a threshold, the sway's own tolerance, so it waits for
 * nothing. Main thread only (measure and PenModelView's callbacks).
 */
@Stable
internal class MarkerBoard {
    var live: Map<String, Offset> by mutableStateOf(emptyMap())
        private set
    var steady: Map<String, Offset> by mutableStateOf(emptyMap())
        private set
    private var steadySize: IntSize by mutableStateOf(IntSize.Zero)

    /** A resized stage waiting for the pen's re-framed markers, or null. */
    var awaiting: IntSize? by mutableStateOf(null)
        private set
    var threshold: Float = 0f
    private var stageSize = IntSize.Zero

    /** The stage was measured at [size]; false while [steady] belongs to another size (wait for new markers). */
    fun measured(size: IntSize): Boolean {
        stageSize = size
        val current = steady.isEmpty() || fits(steadySize, size)
        awaiting = if (current) null else size
        return current
    }

    fun report(markers: Map<String, Offset>) {
        live = markers
        if (movedMoreThan(steady, markers, threshold)) take(markers)
    }

    /** No re-framed markers came: the last reported ones stand for the current size. */
    fun settle() {
        if (live.isNotEmpty()) take(live)
        awaiting = null
    }

    private fun take(markers: Map<String, Offset>) {
        steady = markers
        steadySize = stageSize
        awaiting = null
    }

    private fun fits(taken: IntSize, size: IntSize): Boolean {
        val slack = threshold * RESIZE_SLACK
        return abs(taken.width - size.width) <= slack && abs(taken.height - size.height) <= slack
    }

    private fun movedMoreThan(old: Map<String, Offset>, new: Map<String, Offset>, limit: Float): Boolean {
        if (old.keys != new.keys) return true
        return new.any { (name, offset) ->
            val before = old.getValue(name)
            abs(before.x - offset.x) > limit || abs(before.y - offset.y) > limit
        }
    }

    private companion object {
        /**
         * A resize bigger than this many thresholds re-frames the pen far enough that some marker moves by more
         * than one threshold (the frame's centre moves by half the change), so its new markers are always taken.
         */
        const val RESIZE_SLACK = 3f
    }
}

private enum class StageSlot { Measure, Pen, Lines, Chips, List }

private enum class StageForm { PenOnly, Callouts, List }

/** What an unset callout says (the mock-up's "Not set"). */
internal const val NOT_SET = "Not set"

private const val NIB_MARKER = "marker_nib"
private const val TOP_MARKER = "marker_double_tap"
private const val WRITE_CLIP = "write"

/** The idle clip twists the pen ±12° about its axis and lifts it 2 mm: markers drift a few dp; a resize moves them far more. */
private val STEADY_THRESHOLD = 12.dp

/** How long to wait for the pen's first markers before listing the bindings instead. */
private const val MARKER_WAIT_MS = 800L

/** The pen's half-width as a share of the nib-to-double-tap span (5 mm in 122.5 mm on the model), with room for the sway. */
private const val PEN_HALF_WIDTH_SHARE = 0.05f
private val MIN_PEN_HALF_WIDTH = 4.dp
private val EDGE_MARGIN = 12.dp
private val CHIP_GAP = 8.dp
private val LEAD = 20.dp
private val LINE_WIDTH = 1.5.dp
private val DOT_RADIUS = 5.dp
private val RING_WIDTH = 2.dp
private const val LINE_ALPHA = 0.55f
private const val RING_ALPHA = 0.7f

/** In the list form the pen keeps this share of the stage's width (at least [MIN_LIST_PEN]). */
private const val LIST_PEN_SHARE = 0.38f
private val MIN_LIST_PEN = 120.dp
private val FALLBACK_SIZE = 480.dp
