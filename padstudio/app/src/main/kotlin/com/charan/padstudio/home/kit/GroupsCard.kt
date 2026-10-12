package com.charan.padstudio.home.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.home.PlannedGroup
import com.charan.padstudio.data.HomeSnapshot
import com.charan.padstudio.home.WidgetSize
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.AppIconStack
import com.charan.padstudio.ui.components.EmptyState
import com.charan.padstudio.ui.components.SectionHeader
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.SuperellipseShape
import com.charan.padstudio.ui.components.SymIcon
import com.charan.padstudio.ui.components.SymIconButton
import com.charan.padstudio.ui.theme.Tokens

/**
 * "Your groups": each group with an icon preview, Add widget in three sizes ([onPin], MainViewModel.pinWidget
 * with its own failure message) and Open (the folder panel), then Hidden, last, which opens in Organize.
 *
 * Rows sit on one line while the group names have room beside the buttons, measured at the current text
 * size; otherwise the buttons move under the name.
 */
@Composable
internal fun GroupsCard(
    snapshot: HomeSnapshot?,
    hiddenCount: Int,
    onPin: (AppGroup, WidgetSize) -> Unit,
    onOpen: (AppGroup) -> Unit,
    onOpenHidden: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    StudioCard(modifier, spacing = 4.dp) {
        SectionHeader(title = "Your groups", actionLabel = "Open library", onAction = onOpenLibrary)
        if (snapshot == null) {
            Text("Sorting your apps…", style = type.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            return@StudioCard
        }
        if (snapshot.groups.isEmpty()) {
            EmptyState(title = "No apps to sort yet", icon = Sym.home)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val oneLine = maxWidth - FIXED_PARTS >= minNameRoom(snapshot.groups.map { it.group.title } + AppGroup.HIDDEN.title)
            Column {
                if (snapshot.groups.isNotEmpty()) HintRow(oneLine)
                snapshot.groups.forEach { planned ->
                    GroupLine(planned, snapshot, oneLine, onPin, onOpen)
                    RowDivider()
                }
                HiddenLine(hiddenCount, oneLine, onOpenHidden)
            }
        }
    }
}

/** Lead (the 88 dp icon preview), the three 48 dp size buttons, Open, and the [Tokens.GapS] gaps between them. */
private val LEAD = 88.dp
private val SIZE_TARGET = 48.dp
private val FIXED_PARTS = LEAD + SIZE_TARGET * 3 + SIZE_TARGET + Tokens.GapS * 3

/** A name column needs at least 120 dp, and room for its longest word so no word breaks. */
@Composable
private fun minNameRoom(titles: List<String>): Dp {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyLarge
    val density = LocalDensity.current
    return remember(titles, style, density) {
        val widest = titles.flatMap { it.split(' ') }.maxOfOrNull { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width } ?: 0
        maxOf(MIN_NAME, with(density) { widest.toDp() })
    }
}

private val MIN_NAME = 120.dp

/** "S small · M wide · L large", with the column names over the buttons when rows are on one line. */
@Composable
private fun HintRow(oneLine: Boolean) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    // Each button tells TalkBack what it adds; this line is for the eye.
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp).clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
        Text("S small · M wide · L large", style = style, color = color, modifier = Modifier.weight(1f))
        if (oneLine) {
            Spacer(Modifier.width(Tokens.GapS))
            Text("Add widget", style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.width(SIZE_TARGET * 3))
            Spacer(Modifier.width(Tokens.GapS))
            Text("Open", style = style, color = color, textAlign = TextAlign.Center, softWrap = false, overflow = TextOverflow.Visible, modifier = Modifier.width(SIZE_TARGET))
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA))
}

private const val DIVIDER_ALPHA = 0.6f

@Composable
private fun GroupLine(
    planned: PlannedGroup,
    snapshot: HomeSnapshot,
    oneLine: Boolean,
    onPin: (AppGroup, WidgetSize) -> Unit,
    onOpen: (AppGroup) -> Unit,
) {
    val title = planned.group.title
    val apps = planned.apps.mapNotNull { snapshot.app(it) }
    val lead: @Composable () -> Unit = {
        Box(Modifier.width(LEAD), contentAlignment = Alignment.CenterStart) {
            AppIconStack(apps = apps, size = 36.dp, max = 2)
        }
    }
    val open: @Composable () -> Unit = {
        SymIconButton(icon = Sym.chevron, contentDescription = "Open $title", onClick = { onOpen(planned.group) })
    }
    if (oneLine) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Tokens.GapS),
        ) {
            lead()
            GroupName(title, appCount(planned.apps.size), Modifier.weight(1f))
            SizeButtons(planned.group, Modifier.width(SIZE_TARGET * 3), onPin)
            open()
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.GapS)) {
                lead()
                GroupName(title, appCount(planned.apps.size), Modifier.weight(1f))
                open()
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Add widget",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp),
                )
                SizeButtons(planned.group, Modifier, onPin)
            }
        }
    }
}

@Composable
private fun GroupName(title: String, supporting: String, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** S, M and L: the three widget sizes, each one tap to the launcher's pin request. */
@Composable
private fun SizeButtons(group: AppGroup, modifier: Modifier, onPin: (AppGroup, WidgetSize) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        SIZES.forEach { (letter, size) ->
            val kind = WidgetKind.entries.first { it.name == size.name }
            SizeButton(text = letter, contentDescription = addWidgetDescription(kind, group.title), onClick = { onPin(group, size) })
        }
    }
}

private val SIZES = listOf("S" to WidgetSize.SMALL, "M" to WidgetSize.WIDE, "L" to WidgetSize.LARGE)

/**
 * The mock's small outlined size button: at least 42 × 40 dp with 12 dp corners in a 48 dp target (Material's
 * own minimum), the letter in primary, and the kit's 3 dp focus ring in secondary. Built on Material 3's
 * OutlinedButton because PillButton's fixed padding can't hold a single letter in 48 dp and an ActionChip
 * can't carry [contentDescription].
 */
@Composable
private fun SizeButton(text: String, contentDescription: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring = scheme.secondary
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .drawWithContent {
                drawContent()
                if (focused) {
                    // The 48 dp target is a little larger than the visible button: the ring just inside it sits outside the shape.
                    val stroke = FOCUS_RING.toPx()
                    drawRoundRect(
                        color = ring,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius((Tokens.CornerS + 3.dp).toPx()),
                        style = Stroke(stroke),
                    )
                }
            }
            .widthIn(min = 42.dp)
            .heightIn(min = 40.dp)
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(Tokens.CornerS),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.primary),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        interactionSource = interaction,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

private val FOCUS_RING = 3.dp

/** Hidden, last: apps kept out of every widget. It has no widget; Open shows it in Organize. */
@Composable
private fun HiddenLine(count: Int, oneLine: Boolean, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.GapS),
    ) {
        Box(Modifier.width(LEAD), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier.size(36.dp).clip(SuperellipseShape()).background(scheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                SymIcon(Sym.namesHidden, contentDescription = null, size = 20.dp, tint = scheme.onSecondaryContainer)
            }
        }
        GroupName(AppGroup.HIDDEN.title, hiddenLine(count), Modifier.weight(1f))
        if (oneLine) Spacer(Modifier.width(SIZE_TARGET * 3))
        SymIconButton(icon = Sym.chevron, contentDescription = "Open Hidden in Organize", onClick = onOpen)
    }
}
