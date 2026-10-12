package com.charan.padstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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

    @Composable
    @ReadOnlyComposable
    private fun isDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
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
        modifier = modifier
            .clip(shape)
            .background(GlassDefaults.fill().copy(alpha = alpha))
            .border(1.dp, GlassDefaults.edge(), shape),
        content = content,
    )
}

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
