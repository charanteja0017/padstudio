package com.charan.padstudio.pen.screen

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.charan.padstudio.core.pen.Press
import com.charan.padstudio.ui.components.BindingChip
import com.charan.padstudio.ui.components.SymIcon
import com.charan.padstudio.ui.theme.Tokens

/** One press of one control: what it does here ([action] null: not set), or why it can't be set ([unavailable]). */
@Immutable
data class MatrixCell(
    val press: Press,
    val label: String,
    val action: String?,
    @param:DrawableRes val icon: Int?,
    val unavailable: String? = null,
)

/** A control and its presses: one row of the grid. */
@Immutable
data class MatrixRow(val controlId: String, val control: String, @param:DrawableRes val icon: Int, val cells: List<MatrixCell>)

/**
 * The two-button pen's bindings as a grid (plan §10: "a 2 × 3 grid for two-button pens"): a row per button, a
 * cell per press (Press, Hold, Double press). Holds need Expert setup, so in Basic their cell is shown but can't
 * be chosen. A row whose cells don't fit side by side at their natural width (large text, a narrow pane) stacks
 * them instead, so nothing is clipped. Tapping a cell calls [onCell].
 */
@Composable
fun BindingsMatrix(
    rows: List<MatrixRow>,
    selected: Pair<String, Press>?,
    onCell: (controlId: String, press: Press) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Tokens.GapM)) {
        rows.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SymIcon(row.icon, contentDescription = null, tint = scheme.onSurfaceVariant, size = 20.dp)
                    Text(
                        text = row.control,
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                EqualCells(gap = 8.dp) {
                    row.cells.forEach { cell ->
                        val chosen = selected == row.controlId to cell.press
                        val edge = if (chosen) Modifier.border(2.dp, scheme.primary, RoundedCornerShape(Tokens.CornerS)) else Modifier
                        BindingChip(
                            onClick = { onCell(row.controlId, cell.press) },
                            modifier = edge.semantics { this.selected = chosen },
                            action = cell.action,
                            icon = cell.icon,
                            control = cell.label,
                            enabled = cell.unavailable == null,
                            addLabel = cell.unavailable ?: NOT_SET,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Lays its children side by side in equal columns when each fits at its natural width; otherwise each takes the
 * full width, one under another.
 */
@Composable
private fun EqualCells(gap: Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(constraints.minWidth, 0) {}
        val space = gap.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else measurables.sumOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val columns = measurables.size
        val cell = ((width - space * (columns - 1)) / columns).coerceAtLeast(0)
        val sideBySide = measurables.all { it.maxIntrinsicWidth(Constraints.Infinity) <= cell }
        if (sideBySide) {
            val placeables = measurables.map { it.measure(Constraints(minWidth = cell, maxWidth = cell)) }
            val height = placeables.maxOf { it.height }
            layout(width, height) {
                placeables.forEachIndexed { i, p -> p.placeRelative(i * (cell + space), (height - p.height) / 2) }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
            val height = placeables.sumOf { it.height } + space * (placeables.size - 1)
            layout(width, height) {
                var y = 0
                placeables.forEach { p ->
                    p.placeRelative(0, y)
                    y += p.height + space
                }
            }
        }
    }
}

/** A short row of extra binding chips (the presses beside the main one that have an action), wrapping as needed. */
@Composable
fun ChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
