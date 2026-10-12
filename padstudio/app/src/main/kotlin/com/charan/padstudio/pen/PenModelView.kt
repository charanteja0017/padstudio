package com.charan.padstudio.pen

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * TEMPORARY STAND-IN for W2 task E's live 3D pen (plan decision 15), so the screen agents can build against
 * the contract while task E finishes. Same signature as the contract in docs/redesign/APP-BRIEF.md; task E's
 * SceneView implementation replaces this whole file at merge.
 *
 * Draws a flat diagonal pen (tip lower-left, 35° from vertical, filling the height) and reports where the
 * five marker nodes of assets/pen/focus_pen_pro.glb fall on it, using the same axis positions (metres along
 * the pen, tip at −Y) so callouts land in roughly the right places. [clip] and [playing] are ignored.
 */
@Composable
fun PenModelView(
    modifier: Modifier,
    clip: String = "idle",
    playing: Boolean = true,
    onMarkers: (Map<String, Offset>) -> Unit = {},
) {
    Canvas(modifier.onSizeChanged { onMarkers(markerOffsets(Size(it.width.toFloat(), it.height.toFloat()))) }) {
        val axis = PenAxis(size)
        drawLine(Color(0xFFE9EAF0), axis.at(-0.066f), axis.at(0.0825f), strokeWidth = axis.length * 0.065f, cap = StrokeCap.Round)
        drawLine(Color(0xFFD5DAE2), axis.at(-0.0825f), axis.at(-0.066f), strokeWidth = axis.length * 0.03f, cap = StrokeCap.Round)
    }
}

/** The pen's axis on a canvas of [size]: tip lower-left, 35° from vertical, 90 % of the height (or width). */
private class PenAxis(size: Size) {
    private val angle = Math.toRadians(35.0)
    private val along = Offset(sin(angle).toFloat(), -cos(angle).toFloat()) // tip → top, screen y down
    val length = min(size.height * 0.9f / cos(angle).toFloat(), size.width * 0.9f / sin(angle).toFloat())
    private val centre = Offset(size.width / 2f, size.height / 2f)

    /** The screen point [metres] along the pen from its centre (tip at −0.0825). */
    fun at(metres: Float) = centre + along * (metres / PEN_LENGTH_M * length)
}

private fun markerOffsets(size: Size): Map<String, Offset> {
    val axis = PenAxis(size)
    return MARKERS.mapValues { (_, y) -> axis.at(y) }
}

private const val PEN_LENGTH_M = 0.165f

/** Marker nodes of focus_pen_pro.glb: position along the pen axis in metres (docs/redesign/APP-BRIEF.md). */
private val MARKERS = mapOf(
    "marker_squeeze" to -0.01885f,
    "marker_slide_top" to 0.0203f,
    "marker_slide_bottom" to -0.0043f,
    "marker_double_tap" to 0.040f,
    "marker_nib" to -0.0825f,
)
