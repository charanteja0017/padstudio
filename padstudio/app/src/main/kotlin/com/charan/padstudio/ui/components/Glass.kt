package com.charan.padstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.charan.padstudio.ui.theme.Tokens

/**
 * Round 2's glass (plan §3.1: "its own UI, not the phone's"): translucent panels over the [AmbientBackdrop]
 * instead of flat Material cards. These are Step 0's first versions with their final signatures, so every
 * screen can use them; the design-system work (round 2, A1) tunes the colours and adds the contrast gate.
 */
object GlassDefaults {
    /** Panel opacity over the backdrop: about 62 % in dark themes, 70 % in light ones. */
    @Composable
    @ReadOnlyComposable
    fun alpha(): Float = if (isDark()) 0.62f else 0.70f

    /** Opacity for glass that floats over arbitrary content (menus, sheets, floating bars): at least 90 %. */
    const val OVER_CONTENT_ALPHA: Float = 0.92f

    /** The panel's fill before [alpha] is applied. */
    @Composable
    @ReadOnlyComposable
    fun fill(): Color = MaterialTheme.colorScheme.surfaceContainerLow

    /** The 1 dp highlight edge: white at 10 % in dark themes, 60 % in light ones. */
    @Composable
    @ReadOnlyComposable
    fun edge(): Color = Color.White.copy(alpha = if (isDark()) 0.10f else 0.60f)

    /** Cards and sheets are 28 dp; controls use their own shapes. */
    val Shape: Shape = RoundedCornerShape(Tokens.CornerXl)

    /** Whether the theme is a dark one: its surface is darker than mid grey. */
    @Composable
    @ReadOnlyComposable
    internal fun isDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
}

/** A glass panel: [GlassDefaults.fill] at [alpha], clipped to [shape], with the 1 dp highlight edge. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassDefaults.Shape,
    alpha: Float = GlassDefaults.alpha(),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.glassPanel(shape, GlassDefaults.fill().copy(alpha = alpha), GlassDefaults.edge()),
        content = content,
    )
}

/**
 * A glass panel with its own [fill] and 1 dp [edge] instead of the plain glass, for [StudioCard], which
 * behaves like Material's Surface did. With [onClick] the whole panel is one button whose ripple is clipped to
 * [shape], and [interactionSource] reports its focus. Without it the panel is a group for TalkBack's reading
 * order and keeps touches from reaching what lies under it. The content gets the panel's minimum size, so a
 * card its parent stretches is filled.
 */
@Composable
internal fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassDefaults.Shape,
    fill: Color,
    edge: Color,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    // The ripple sits inside the clip, so it follows the corners; the edge is drawn over it.
    val press = if (onClick != null) {
        Modifier.clickable(interactionSource = interactionSource, indication = ripple(), role = Role.Button, onClick = onClick)
    } else {
        Modifier.semantics { isTraversalGroup = true }.pointerInput(Unit) {}
    }
    Box(
        modifier = modifier.glassPanel(shape, fill, edge).then(press),
        propagateMinConstraints = true,
        content = content,
    )
}

/** Clipped to [shape], [fill] behind the content and the 1 dp [edge] over it. */
private fun Modifier.glassPanel(shape: Shape, fill: Color, edge: Color): Modifier =
    this.clip(shape).background(fill).border(1.dp, edge, shape)

/**
 * The app's page background: the theme's background with two soft glows, primary from the top start and
 * tertiary from the bottom end, so glass panels have something to sit on.
 */
@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val glowA = scheme.primary.copy(alpha = if (dark) 0.22f else 0.16f)
    val glowB = scheme.tertiary.copy(alpha = if (dark) 0.16f else 0.12f)
    Box(
        modifier = modifier
            .background(scheme.background)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glowA, Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.08f),
                        radius = size.maxDimension * 0.7f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glowB, Color.Transparent),
                        center = Offset(size.width * 0.92f, size.height * 0.95f),
                        radius = size.maxDimension * 0.6f,
                    ),
                )
            },
        content = content,
    )
}
