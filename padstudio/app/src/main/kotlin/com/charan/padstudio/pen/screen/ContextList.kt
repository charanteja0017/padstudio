package com.charan.padstudio.pen.screen

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.charan.padstudio.core.pen.DefaultProfiles
import com.charan.padstudio.core.pen.PenContext
import com.charan.padstudio.core.pen.PenKind
import com.charan.padstudio.core.pen.PenMode
import com.charan.padstudio.core.pen.PenProfile
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.ActionChip
import com.charan.padstudio.ui.components.AppIconStack
import com.charan.padstudio.ui.components.CardTone
import com.charan.padstudio.ui.components.ListRow
import com.charan.padstudio.ui.components.RowLeading
import com.charan.padstudio.ui.components.RowTrailing
import com.charan.padstudio.ui.components.SegmentedChoice
import com.charan.padstudio.ui.components.StatusLevel
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.SuperellipseShape
import com.charan.padstudio.ui.components.SymIcon
import com.charan.padstudio.ui.components.cardBleed
import com.charan.padstudio.ui.components.statusInk

/** One context as the list and the hero show it. */
@Immutable
data class ContextItem(
    val context: PenContext,
    val labels: ContextLabels,
    val apps: List<InstalledApp>,
    val warnings: List<PenWarning>,
)

/**
 * The pen kind (plan §10, left pane: a segmented choice), a line on what that pen has, and the setup-mode chip
 * ("Expert setup"), which opens Advanced, where the setup is chosen.
 */
@Composable
fun PenChoice(kind: PenKind, mode: PenMode, onKind: (PenKind) -> Unit, onSetupChip: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentedChoice(
            options = PenKind.entries,
            selected = kind,
            onSelect = onKind,
            label = ::penKindChoice,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(text = penKindDetail(kind), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ActionChip(label = setupName(mode), onClick = onSetupChip, icon = Sym.settings)
    }
}

/**
 * Where the pen works: one row per context with its apps' icons (an icon stack) or a symbol tile, its supporting
 * line, and "Needs a look" with a warning symbol when core warns about it. The chosen row is highlighted.
 */
@Composable
fun ContextList(
    items: List<ContextItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    bleed: Dp = 16.dp,
) {
    Column(modifier.cardBleed(bleed).selectableGroup()) {
        items.forEach { item ->
            val needsLook = item.warnings.isNotEmpty()
            ListRow(
                headline = item.labels.title,
                supporting = if (needsLook) NEEDS_A_LOOK else item.labels.supporting,
                // One icon and a "+N" tile: the narrow pane keeps its room for the name.
                leading = RowLeading.Custom { ContextBadge(item, size = 36.dp, max = 1) },
                trailing = if (needsLook) {
                    RowTrailing.Custom { SymIcon(Sym.warning, contentDescription = null, tint = statusInk(StatusLevel.Warn)) }
                } else {
                    RowTrailing.None
                },
                selected = item.context.id == selectedId,
                onClick = { onSelect(item.context.id) },
            )
        }
    }
}

/** A context's picture: its apps' icons, or a symbol on a tonal tile in the HyperOS icon shape. */
@Composable
fun ContextBadge(item: ContextItem, size: Dp, max: Int = 2) {
    if (item.apps.isNotEmpty()) {
        AppIconStack(apps = item.apps, size = size, max = max, contentDescription = null)
        return
    }
    val scheme = MaterialTheme.colorScheme
    val (icon, container, content) = when {
        item.context.id == PenProfile.EVERYWHERE -> Triple(Sym.grid, scheme.primaryContainer, scheme.onPrimaryContainer)
        item.context.id == DefaultProfiles.HOME.id -> Triple(Sym.homeAction, scheme.secondaryContainer, scheme.onSecondaryContainer)
        item.context.passThrough -> Triple(Sym.openNotes, scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
        else -> Triple(Sym.widgets, scheme.tertiaryContainer, scheme.onTertiaryContainer)
    }
    SymbolTile(icon, container, content, size)
}

@Composable
private fun SymbolTile(@DrawableRes icon: Int, container: Color, content: Color, size: Dp) {
    Box(
        Modifier.size(size).clip(SuperellipseShape()).background(container),
        contentAlignment = Alignment.Center,
    ) {
        SymIcon(icon, contentDescription = null, tint = content, size = size * 0.55f)
    }
}

/** Warnings about the pen as a whole, in plain words (their originals are under Developer details). */
@Composable
fun PenNotice(warnings: List<PenWarning>, modifier: Modifier = Modifier) {
    StudioCard(modifier.fillMaxWidth(), tone = CardTone.Tonal, contentPadding = PaddingValues(12.dp), spacing = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SymIcon(Sym.warning, contentDescription = null, size = 20.dp)
            Text(text = NEEDS_A_LOOK, style = MaterialTheme.typography.labelLarge)
        }
        warnings.forEach { Text(text = it.plain, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** The words of the "Needs a look" chip and row line (the mock-up's). */
const val NEEDS_A_LOOK = "Needs a look"
