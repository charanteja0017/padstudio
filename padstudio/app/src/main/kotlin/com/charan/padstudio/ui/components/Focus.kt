package com.charan.padstudio.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

// Keyboard focus, as the kit draws it (kit.css "focus"): a 3 dp ring in the scheme's secondary colour,
// 2 dp outside the visible shape. Material 3 1.4.0 shows focus only as a faint state layer, and the
// acceptance checks (plan §13, "Keyboard") need a ring you can see. Compose moves focus only for keyboard
// and D-pad input, never for touch, so the ring never shows under a finger or the pen.

/**
 * Draws the focus ring over the content while [focused]: a [FocusRingWidth] ring in [color] following
 * [shape]. With no [gap] it lies just inside the bounds, where a clipping parent (a card) can't cut it;
 * with a [gap] it starts that far outside them, as the kit's `outline-offset`.
 */
internal fun Modifier.focusRing(
    focused: Boolean,
    shape: Shape,
    color: Color,
    gap: Dp = Dp.Unspecified,
): Modifier = this.drawWithContent {
    drawContent()
    if (focused) {
        val stroke = FocusRingWidth.toPx()
        // A stroke is centred on its outline: put the outline mid-ring, outside or inside the bounds.
        val centre = if (gap.isSpecified) gap.toPx() + stroke / 2f else -stroke / 2f
        val ring = Size(size.width + 2f * centre, size.height + 2f * centre)
        if (ring.width > 0f && ring.height > 0f) {
            translate(left = -centre, top = -centre) {
                drawOutline(shape.createOutline(ring, layoutDirection, this), color, style = Stroke(width = stroke))
            }
        }
    }
}

/**
 * Holds a Material control in a box of at least 48 × 48 dp while the control itself is laid out at its
 * visible size, so [focusRing] can follow the visible shape. The touch target stays 48 dp: Compose widens
 * the hit area of a smaller pointer target to the system minimum, and the box keeps the layout room for
 * it, as `minimumInteractiveComponentSize()` does. The box passes its minimum size on, so a fill or
 * weight modifier from the caller still stretches the control.
 */
@Composable
internal fun TouchTargetBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center, propagateMinConstraints = true) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp, content = content)
    }
}

internal val FocusRingWidth: Dp = 3.dp

/** The kit's `outline-offset`: the ring starts this far outside a control's visible shape. */
internal val FocusRingGap: Dp = 2.dp
