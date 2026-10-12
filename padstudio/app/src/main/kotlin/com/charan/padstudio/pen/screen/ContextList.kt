package com.charan.padstudio.pen.screen

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.charan.padstudio.core.pen.DefaultProfiles
import com.charan.padstudio.core.pen.PenContext
import com.charan.padstudio.core.pen.PenProfile
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.AppIcon
import com.charan.padstudio.ui.components.AppIconStack
import com.charan.padstudio.ui.components.CardTone
import com.charan.padstudio.ui.components.ListRow
import com.charan.padstudio.ui.components.RowLeading
import com.charan.padstudio.ui.components.RowTrailing
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
 * Where the pen works: one row per context with its first app's icon (and a "+N" badge when it has more apps) or a
 * symbol tile, its supporting line, and "Needs a look" with a warning symbol when core warns about it. The chosen
 * row is highlighted.
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
                // One icon, and the rest of the apps as a "+N" badge on its corner: the narrow pane keeps its room for the name.
                leading = RowLeading.Custom { ContextBadge(item, size = ROW_ICON_SIZE, max = 1) },
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

/**
 * A context's picture. With one icon to show ([max] 1, the list's rows) it is the first app's icon at [size], and
 * when the context has more apps a small "+N" badge on the icon's bottom-end corner. With a larger [max] (the
 * hero) it is a stack of up to [max] icons and a "+N" tile. A context with no apps gets a symbol on a tonal tile in
 * the HyperOS icon shape.
 */
@Composable
fun ContextBadge(item: ContextItem, size: Dp, max: Int = 2) {
    if (item.apps.isNotEmpty()) {
        if (max <= 1) {
            AppWithMoreBadge(item.apps, size)
        } else {
            AppIconStack(apps = item.apps, size = size, max = max, contentDescription = null)
        }
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

/** The first app's icon at [size], with a "+N" badge on its bottom-end corner for the apps beyond it. */
@Composable
private fun AppWithMoreBadge(apps: List<InstalledApp>, size: Dp) {
    val first = apps.first()
    val more = apps.size - 1
    // TalkBack reads it as one image ("ReadEra and 2 more"), as the icon stack does.
    Box(Modifier.clearAndSetSemantics { contentDescription = appsDescription(first.facts.label, more) }) {
        AppIcon(app = first, size = size, contentDescription = null)
        if (more > 0) {
            MoreBadge(more, Modifier.align(Alignment.BottomEnd).offset(x = BADGE_OFFSET, y = BADGE_OFFSET))
        }
    }
}

/**
 * The "+N" badge: a 16 dp circle in secondary container (a pill when the number is wider than that) with
 * label-small text in on-secondary-container.
 */
@Composable
private fun MoreBadge(more: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val label = MaterialTheme.typography.labelSmall
    // Sized with the badge, not the text setting (as the icon stack's "+N" tile is): at 200 % text it must stay a
    // small mark on a 40 dp icon, and the row's "3 apps" line carries the count at the user's size.
    val glyph = with(LocalDensity.current) { label.fontSize.value.dp.toSp() }
    Box(
        modifier
            .defaultMinSize(minWidth = BADGE_SIZE, minHeight = BADGE_SIZE)
            .background(scheme.secondaryContainer, CircleShape)
            .padding(horizontal = BADGE_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = moreAppsBadge(more),
            style = label.copy(fontSize = glyph, lineHeight = glyph),
            color = scheme.onSecondaryContainer,
            maxLines = 1,
            softWrap = false,
        )
    }
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

/** A row's app icon: the size of a list row's other leading pictures. */
private val ROW_ICON_SIZE = 40.dp
private val BADGE_SIZE = 16.dp
private val BADGE_PADDING = 2.dp

/** How far the badge sits past the icon's bottom-end corner, on each axis. */
private val BADGE_OFFSET = 4.dp
