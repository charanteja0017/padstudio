package com.charan.padstudio.today

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.charan.padstudio.HealthCheck
import com.charan.padstudio.core.home.PlannedGroup
import com.charan.padstudio.core.pen.DefaultProfiles
import com.charan.padstudio.data.HomeSnapshot
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.AppIcon
import com.charan.padstudio.ui.components.AppIconStack
import com.charan.padstudio.ui.components.ListRow
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.ProgressRing
import com.charan.padstudio.ui.components.RowLeading
import com.charan.padstudio.ui.components.RowTrailing
import com.charan.padstudio.ui.components.SectionHeader
import com.charan.padstudio.ui.components.SegmentedChoice
import com.charan.padstudio.ui.components.StatusLevel
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.SuperellipseShape
import com.charan.padstudio.ui.components.SymIcon
import com.charan.padstudio.ui.components.cardBleed
import com.charan.padstudio.ui.components.statusInk
import com.charan.padstudio.ui.theme.Tokens

// ---- Pen by app --------------------------------------------------------------------------------------------

/**
 * Where the pen does what: each context with its apps' icons and its bindings in words. [lines] is null while
 * the profile is being built. Every row, and Manage, opens the Pen tab.
 */
@Composable
internal fun PenByAppCard(
    lines: List<ContextLine>?,
    apps: (List<String>) -> List<InstalledApp>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    StudioCard(modifier.fillMaxWidth()) {
        SectionHeader(title = "Pen by app", actionLabel = "Manage", onAction = onOpen)
        when {
            lines == null -> Text("Reading your apps…", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            lines.isEmpty() -> Text(
                "None of the apps the pen profile knows are installed. The pen still works everywhere else.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            else -> Column(Modifier.cardBleed()) {
                lines.forEachIndexed { index, line ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = scheme.outlineVariant.copy(alpha = 0.8f))
                    ListRow(
                        headline = line.title,
                        supporting = line.words,
                        leading = RowLeading.Custom { ContextIcons(line.id, apps(line.packages)) },
                        trailing = RowTrailing.Chevron,
                        onClick = onOpen,
                    )
                }
            }
        }
    }
}

/** A context's apps as an icon stack, in a fixed-width slot so the rows' text lines up; a symbol tile when none has an app-drawer entry. */
@Composable
private fun ContextIcons(contextId: String, apps: List<InstalledApp>) {
    Box(Modifier.width(STACK_SLOT), contentAlignment = Alignment.CenterStart) {
        if (apps.isNotEmpty()) {
            AppIconStack(apps = apps, size = STACK_ICON, max = 2)
        } else {
            CategoryTile(contextSymbol(contextId))
        }
    }
}

/** A symbol on the HyperOS icon shape, for a context without app icons (the mock's Video & music tile). */
@Composable
private fun CategoryTile(symbol: Int) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(STACK_ICON)
            .clip(SuperellipseShape())
            .background(Brush.linearGradient(listOf(scheme.tertiaryContainer, scheme.primaryContainer))),
        contentAlignment = Alignment.Center,
    ) {
        SymIcon(symbol, contentDescription = null, size = 22.dp, tint = scheme.onPrimaryContainer)
    }
}

private fun contextSymbol(contextId: String): Int = when (contextId) {
    DefaultProfiles.CAMERA.id -> Sym.openCamera
    DefaultProfiles.READING.id -> Sym.nextPage
    DefaultProfiles.NATIVE_NOTES.id, DefaultProfiles.OTHER_NOTES.id -> Sym.openNotes
    DefaultProfiles.MEDIA.id -> Sym.play
    DefaultProfiles.HOME.id -> Sym.homeAction
    else -> Sym.pen
}

// ---- Home screen -------------------------------------------------------------------------------------------

/** Which picture the Home screen card shows. */
internal enum class HomePreview(val label: String) {
    GROUPS("Groups"),
    WIDGETS("Widgets"),
}

/**
 * The home screen in brief: a widget laid out by core's layoutFor with your real icons (or your groups), the
 * default widget grid, and HyperOS's launcher grid as recorded (ADR-005), never as a live reading.
 */
@Composable
internal fun HomeScreenCard(
    snapshot: HomeSnapshot?,
    preview: PreviewWidget,
    defaultGrid: String,
    launcherGrid: String?,
    wallpaperColors: List<Color>?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    var shown by rememberSaveable { mutableStateOf(HomePreview.WIDGETS) }
    StudioCard(modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Home screen",
                style = type.titleLarge,
                color = scheme.onSurface,
                modifier = Modifier.padding(end = 12.dp).semantics { heading() },
            )
            SegmentedChoice(
                options = HomePreview.entries,
                selected = shown,
                onSelect = { shown = it },
                label = { it.label },
            )
        }
        when {
            snapshot == null -> PreviewPlaceholder("Sorting your apps…")
            snapshot.groups.isEmpty() -> PreviewPlaceholder("No app groups yet.")
            shown == HomePreview.WIDGETS -> WidgetPreview(snapshot, previewGroup(snapshot.groups), preview, defaultGrid, wallpaperColors)
            else -> GroupTiles(snapshot)
        }
        ListRow(
            headline = "Default widget grid",
            modifier = Modifier.cardBleed(),
            trailing = RowTrailing.Custom {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = defaultGrid,
                        style = type.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        color = scheme.onSurface,
                    )
                    SymIcon(Sym.chevron, contentDescription = null, tint = scheme.onSurfaceVariant, autoMirror = true)
                }
            },
            onClick = onOpen,
        )
        if (launcherGrid != null) {
            Text(text = launcherGrid, style = type.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PreviewPlaceholder(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.CornerM))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/**
 * The fullest group as a widget set to Default would show it: core's layout at the preview size, its first
 * apps' real icons, and "+N" in the last cell when they don't all fit; scaled down to the card, over your
 * wallpaper's colours. One picture for TalkBack.
 */
@Composable
private fun WidgetPreview(
    snapshot: HomeSnapshot,
    group: PlannedGroup?,
    preview: PreviewWidget,
    defaultGrid: String,
    wallpaperColors: List<Color>?,
) {
    val scheme = MaterialTheme.colorScheme
    val layout = preview.layout
    val apps = group?.apps.orEmpty()
    val overflow = layout.overflow(apps.size)
    val shownApps = apps.take(overflow.shown)
    val description = buildString {
        append("Preview: a widget set to Default (")
        append(defaultGrid)
        append(") showing ")
        append(group?.group?.title ?: "a group")
        append(", ${layout.columns} × ${layout.rows}: ")
        val named = shownApps.take(6)
        append(named.joinToString { it.label })
        if (apps.size > named.size) append(" and ${apps.size - named.size} more")
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.CornerM))
            .background(wallpaperBrush(wallpaperColors))
            .padding(PREVIEW_MARGIN)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val scale = (maxWidth.value / preview.widthDp).coerceAtMost(1f)
        fun Float.scaled(): Dp = (this * scale).dp
        Column(
            Modifier
                .size(preview.widthDp.scaled(), preview.heightDp.scaled())
                .clip(RoundedCornerShape(WIDGET_CORNER_DP.scaled()))
                .background(scheme.surfaceContainer.copy(alpha = 0.92f))
                .padding(PADDING_DP.scaled()),
        ) {
            Row(Modifier.height(HEADER_DP.scaled()), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(scheme.secondaryContainer)
                        .padding(horizontal = 8f.scaled(), vertical = 3f.scaled()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4f.scaled()),
                ) {
                    SymIcon(Sym.widgets, contentDescription = null, size = 14f.scaled(), tint = scheme.onSecondaryContainer)
                    FixedText(group?.group?.title ?: "Group", 12f * scale, scheme.onSecondaryContainer)
                }
            }
            val cellW = layout.cellWidthDp.scaled()
            val cellH = layout.cellHeightDp.scaled()
            val icon = layout.iconDp.scaled()
            for (row in 0 until layout.rows) {
                Row {
                    for (column in 0 until layout.columns) {
                        val index = row * layout.columns + column
                        Column(
                            Modifier.size(cellW, cellH),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            when {
                                overflow.moreIndex == index -> MoreTile(overflow.more, icon)
                                index < shownApps.size -> {
                                    val app = snapshot.app(shownApps[index])
                                    if (app != null) AppIcon(app, size = icon, contentDescription = null) else Spacer(Modifier.size(icon))
                                    if (layout.showLabels) {
                                        Spacer(Modifier.height(4f.scaled()))
                                        Box(
                                            Modifier
                                                .size(cellW * 0.6f, 3f.scaled())
                                                .clip(RoundedCornerShape(50))
                                                .background(scheme.onSurfaceVariant.copy(alpha = 0.35f)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The "+N" tile in the last cell, at the icons' size and shape. */
@Composable
private fun MoreTile(count: Int, size: Dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .clip(SuperellipseShape())
            .background(scheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        FixedText("+$count", size.value * 0.34f, scheme.onSecondaryContainer)
    }
}

/** Text drawn at a fixed size in dp: part of a scaled-down picture, so it follows the picture, not the text setting. */
@Composable
private fun FixedText(text: String, sizeDp: Float, color: Color) {
    val size = with(LocalDensity.current) { sizeDp.dp.toSp() }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = size, lineHeight = size, fontFeatureSettings = "tnum"),
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

/** Up to six groups with their apps' icons, three to a row. */
@Composable
private fun GroupTiles(snapshot: HomeSnapshot) {
    val scheme = MaterialTheme.colorScheme
    val groups = snapshot.groups.take(6)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.CornerM))
            .background(scheme.surfaceContainer)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.chunked(GROUP_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { group ->
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .semantics(mergeDescendants = true) {},
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppIconStack(
                            apps = group.apps.mapNotNull { snapshot.app(it) },
                            size = 32.dp,
                            max = 2,
                            ringColor = scheme.surfaceContainer,
                        )
                        Text(
                            text = group.group.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                repeat(GROUP_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A soft diagonal blend of the wallpaper's main colours; the theme's containers when Android gives none. */
@Composable
private fun wallpaperBrush(colors: List<Color>?): Brush {
    val scheme = MaterialTheme.colorScheme
    val stops = colors?.takeIf { it.isNotEmpty() } ?: listOf(scheme.primaryContainer, scheme.tertiaryContainer)
    return Brush.linearGradient(if (stops.size == 1) listOf(stops[0], stops[0]) else stops)
}

// ---- Make it yours -----------------------------------------------------------------------------------------

/** Icon style, wallpaper and colour source in brief, with a swatch of the wallpaper's real colours. Opens Looks. */
@Composable
internal fun MakeItYoursCard(
    iconLine: String,
    wallpaperLine: String,
    coloursLine: String,
    wallpaperColors: List<Color>?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    StudioCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.GapM)) {
            Box(
                Modifier
                    .size(THUMB)
                    .clip(RoundedCornerShape(Tokens.CornerM))
                    .background(wallpaperBrush(wallpaperColors)),
                contentAlignment = Alignment.Center,
            ) {
                if (wallpaperColors.isNullOrEmpty()) SymIcon(Sym.wallpaper, contentDescription = null, tint = scheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text("Make it yours", style = type.titleLarge, color = scheme.onSurface, modifier = Modifier.semantics { heading() })
                Text(iconLine, style = type.bodyMedium, color = scheme.onSurface)
                Text(wallpaperLine, style = type.bodyMedium, color = scheme.onSurfaceVariant)
                Text(coloursLine, style = type.bodyMedium, color = scheme.onSurfaceVariant)
            }
            SymIcon(Sym.chevron, contentDescription = null, tint = scheme.onSurfaceVariant, autoMirror = true)
        }
    }
}

// ---- Health ------------------------------------------------------------------------------------------------

/**
 * The eight Setup checks as a ring, "N of 8 ready", and the first one that needs attention with its existing
 * Fix intent (through onStart). A check without one points to Setup. [issueText] words the top issue as
 * Setup does ([healthIssueText]).
 */
@Composable
internal fun HealthCard(
    summary: HealthSummary,
    issueText: (HealthCheck) -> String,
    onFix: (Intent) -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val loading = summary.total == 0
    StudioCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.GapM)) {
            ProgressRing(
                progress = if (loading) 0f else summary.ready.toFloat() / summary.total,
                value = if (loading) "…" else "${summary.ready}",
                size = RING,
                strokeWidth = 5.dp,
                contentDescription = if (loading) "Checking your setup" else summary.title,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (loading) "Checking your setup…" else summary.title,
                    style = type.titleLarge,
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                summary.breakdown?.let { Text(it, style = type.bodyMedium, color = scheme.onSurfaceVariant) }
            }
        }
        val top = summary.top
        if (top != null) {
            TopIssue(top, issueText(top))
        } else if (!loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SymIcon(Sym.ok, contentDescription = null, size = 20.dp, tint = statusInk(StatusLevel.Good))
                Text("Nothing needs attention", style = type.bodyLarge, color = scheme.onSurface)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val fix = top?.fixIntent
            if (top != null && fix != null) {
                PillButton(text = top.fix ?: "Fix", onClick = { onFix(fix) }, style = PillStyle.Tonal, icon = Sym.open)
            }
            PillButton(text = "Open Setup", onClick = onOpenSetup, style = PillStyle.Text, trailingIcon = Sym.chevron)
        }
    }
}

@Composable
private fun TopIssue(check: HealthCheck, words: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SymIcon(Sym.warning, contentDescription = "Needs attention", size = 20.dp, tint = statusInk(StatusLevel.Warn), modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(check.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = Tokens.WeightMedium), color = scheme.onSurface)
            Text(words, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}

private val STACK_ICON = 36.dp

/** Room for two icons and a "+N" tile overlapping by 30 % (AppIconStack). */
private val STACK_SLOT = 88.dp
private val PREVIEW_MARGIN = 16.dp
private val THUMB = 80.dp
private val RING = 72.dp
private const val GROUP_COLUMNS = 3

// The widget's own measures (GroupWidget and core's GroupPlanner): padding, header and corners, in dp before scaling.
private const val PADDING_DP = 10f
private const val HEADER_DP = 28f
private const val WIDGET_CORNER_DP = 24f
