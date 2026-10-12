package com.charan.padstudio.looks.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.charan.padstudio.ui.theme.Tokens
import kotlin.math.roundToInt

/** The start pane's share of the width beside the end pane: 1.7 : 1, as mock-looks.html's grid. */
private const val START_SHARE = 1.7f / 2.7f

/**
 * The Looks screen's panes (mock-looks.html). [sideBySide] (840 dp and wider): [start] (the icon cards) takes 1.7
 * parts of the width and [end] (the wallpaper card) 1 part, [Tokens.GapM] apart, and [end] is measured at least as
 * tall as [start], so the wallpaper card reaches down to the bottom of the cards beside it as the mock-up's columns
 * do (when [end] is the taller one, [start] keeps its own height). Otherwise one column: [start], then [end].
 *
 * Both arrangements are the same composition, so crossing 840 dp (split screen, resizing) keeps every card's
 * state, such as an open pack picker. Each slot must emit exactly one layout. Not built on intrinsic measurement:
 * ListRow (in [start]) has no intrinsic measurements of its own, and the defaults would size its text as if it
 * had no room.
 */
@Composable
internal fun LooksPanes(sideBySide: Boolean, start: @Composable () -> Unit, end: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Layout(content = { start(); end() }, modifier = modifier) { measurables, constraints ->
        check(measurables.size == 2) { "LooksPanes: start and end must each emit one layout, got ${measurables.size}" }
        val gap = Tokens.GapM.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 840.dp.roundToPx()
        if (sideBySide) {
            val startWidth = ((width - gap) * START_SHARE).roundToInt().coerceAtLeast(0)
            val endWidth = (width - gap - startWidth).coerceAtLeast(0)
            val first = measurables[0].measure(Constraints(minWidth = startWidth, maxWidth = startWidth))
            val second = measurables[1].measure(Constraints(minWidth = endWidth, maxWidth = endWidth, minHeight = first.height))
            layout(width, maxOf(first.height, second.height)) {
                first.placeRelative(0, 0)
                second.placeRelative(startWidth + gap, 0)
            }
        } else {
            val first = measurables[0].measure(Constraints(minWidth = width, maxWidth = width))
            val second = measurables[1].measure(Constraints(minWidth = width, maxWidth = width))
            layout(width, first.height + gap + second.height) {
                first.placeRelative(0, 0)
                second.placeRelative(0, first.height + gap)
            }
        }
    }
}

/**
 * The kit's keyboard focus ring (kit.css "focus") for this screen's own controls: 3 dp of [color], 2 dp outside
 * [shape]. Compose moves focus only for keyboard and D-pad input, so it never shows under a finger or the pen.
 * The component library draws the same ring for its components; its helper isn't part of the listed API.
 */
internal fun Modifier.keyboardFocusRing(focused: Boolean, shape: Shape, color: Color): Modifier = this.drawWithContent {
    drawContent()
    if (focused) {
        val stroke = 3.dp.toPx()
        val centre = 2.dp.toPx() + stroke / 2f
        val ring = Size(size.width + 2f * centre, size.height + 2f * centre)
        translate(left = -centre, top = -centre) {
            drawOutline(shape.createOutline(ring, layoutDirection, this), color, style = Stroke(width = stroke))
        }
    }
}

/**
 * The inset well a style preview sits in (mock-looks.html `--m-looks-well`): a step darker than the card it is
 * on, surface container lowest in dark schemes and surface container in light ones.
 */
@Composable
@ReadOnlyComposable
internal fun wellColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() < 0.5f) scheme.surfaceContainerLowest else scheme.surfaceContainer
}
