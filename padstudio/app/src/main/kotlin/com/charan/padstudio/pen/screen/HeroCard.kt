package com.charan.padstudio.pen.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.ActionChip
import com.charan.padstudio.ui.components.CardTone
import com.charan.padstudio.ui.components.Disclosure
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.SymIcon
import com.charan.padstudio.ui.theme.Tokens

/**
 * The right pane (plan §10 "Pen"): the chosen context's picture, name and "2 of 4 have actions" ([meta]), the
 * "Needs a look" chip when core warns about it, then [body] (the pen with its callouts, the button grid, or the
 * pass-through note). The chip opens the warnings in plain words, with the generator's own wording under
 * "Original wording".
 */
@Composable
fun HeroCard(
    item: ContextItem,
    meta: String,
    needsOpen: Boolean,
    onNeedsOpen: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable ColumnScope.() -> Unit,
) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    StudioCard(modifier, contentPadding = PaddingValues(16.dp), spacing = 12.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ContextBadge(item, size = 40.dp, max = 3)
            Column(Modifier.weight(1f)) {
                Text(text = item.labels.title, style = type.titleLarge, color = scheme.onSurface, modifier = Modifier.semantics { heading() })
                Text(text = meta, style = type.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        if (item.warnings.isNotEmpty()) {
            ActionChip(
                label = "$NEEDS_A_LOOK (${item.warnings.size})",
                onClick = { onNeedsOpen(!needsOpen) },
                modifier = Modifier.semantics { stateDescription = if (needsOpen) "Expanded" else "Collapsed" },
                icon = Sym.warning,
            )
            if (needsOpen) NeedsALook(item.warnings)
        }
        body()
    }
}

/** The warnings in plain words, then the generator's own text in a collapsed "Original wording". */
@Composable
private fun NeedsALook(warnings: List<PenWarning>) {
    val type = MaterialTheme.typography
    StudioCard(Modifier.fillMaxWidth(), tone = CardTone.High, contentPadding = PaddingValues(16.dp)) {
        // Scrolls inside a capped height, so long warnings at large text never push the pen out of the card.
        Column(Modifier.heightIn(max = NEEDS_MAX_HEIGHT).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            warnings.forEach { warning ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SymIcon(Sym.warning, contentDescription = null, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = warning.plain, style = type.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Disclosure(title = "Original wording") {
                warnings.forEach { Text(text = it.original) }
            }
        }
    }
}

/**
 * The pen's backdrop, as the mock-up draws it: in dark themes a primary-container glow behind the pen; in light
 * ones a deeper primary-container tint, so the white pen keeps its edge.
 */
fun Modifier.penBackdrop(container: Color, dark: Boolean): Modifier = this.drawBehind {
    if (dark) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(container.copy(alpha = GLOW_ALPHA), Color.Transparent),
                center = center.copy(x = size.width * 0.55f),
                radius = size.maxDimension * 0.6f,
            ),
        )
    } else {
        drawRoundRect(container.copy(alpha = TINT_ALPHA), cornerRadius = CornerRadius(Tokens.CornerM.toPx()))
    }
}

/** True when [color] (the scheme's surface) is dark. */
fun isDark(color: Color): Boolean = color.luminance() < 0.5f

private val NEEDS_MAX_HEIGHT = 240.dp
private const val GLOW_ALPHA = 0.7f
private const val TINT_ALPHA = 0.55f
