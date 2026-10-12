package com.charan.padstudio.looks.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.charan.padstudio.data.IconStyle
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.icons.IconPack
import com.charan.padstudio.studio
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.AppIcon
import com.charan.padstudio.ui.components.Disclosure
import com.charan.padstudio.ui.components.EmptyState
import com.charan.padstudio.ui.components.IconAppearance
import com.charan.padstudio.ui.components.ListRow
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.RowLeading
import com.charan.padstudio.ui.components.RowTrailing
import com.charan.padstudio.ui.components.SectionHeader
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.SuperellipseShape
import com.charan.padstudio.ui.components.SymIcon
import com.charan.padstudio.ui.components.cardBleed
import com.charan.padstudio.ui.theme.Tokens

/**
 * What Developer details and the pack captions know about the preview apps' icons, counted off the main thread
 * by [loadIconFacts] from IconRepository's own answer for each app and style.
 */
@Immutable
internal data class IconFacts(
    /** How many preview apps were checked. */
    val total: Int,
    /** For each installed pack (by package name), how many preview apps it has an icon for. */
    val coverage: Map<String, Int>,
    /** Labels of the preview apps the saved pack has no icon for; they keep their own. */
    val packOriginals: List<String>,
    /** How many preview apps Themed draws in one colour. */
    val oneColour: Int,
    /** Labels of the preview apps Themed leaves as they are (no monochrome layer and no Lawnicons glyph). */
    val themedOriginals: List<String>,
    /** Each installed pack's versionName, by package name. */
    val versions: Map<String, String?>,
)

/** Asks IconRepository where each preview icon comes from in each style. Slow (it reads packs' appfilter.xml): call it off the main thread. */
internal fun loadIconFacts(context: Context, apps: List<InstalledApp>, packs: List<IconPack>, saved: String?): IconFacts {
    val icons = context.studio.icons
    fun sourceOf(app: InstalledApp, style: IconStyle, pack: String?): IconSource =
        runCatching { iconSourceOf(icons.source(app, style, pack)) }.getOrDefault(IconSource.ORIGINAL)
    val coverage = packs.associate { pack ->
        pack.packageName to apps.count { sourceOf(it, IconStyle.ICON_PACK, pack.packageName) == IconSource.PACK }
    }
    val packOriginals = if (saved == null) emptyList() else apps.filter { sourceOf(it, IconStyle.ICON_PACK, saved) != IconSource.PACK }
    val themed = apps.map { app -> app to sourceOf(app, IconStyle.THEMED, saved) }
    val versions = packs.associate { pack ->
        pack.packageName to runCatching { context.packageManager.getPackageInfo(pack.packageName, 0).versionName }.getOrNull()
    }
    return IconFacts(
        total = apps.size,
        coverage = coverage,
        packOriginals = packOriginals.map { it.facts.label },
        oneColour = themed.count { it.second == IconSource.ONE_COLOUR },
        themedOriginals = themed.filter { it.second != IconSource.ONE_COLOUR }.map { it.first.facts.label },
        versions = versions,
    )
}

/**
 * Looks → "Icon style" (plan §10, mock-looks.html): the three styles as a radio group, each previewed on the same
 * [apps] (null while the app list loads), the icon-pack row that opens the pack picker, and Developer details.
 * Choosing a style calls [onSelectStyle], choosing a pack [onSelectPack]; the caller passes them to
 * MainViewModel.setIconStyle exactly as before. [packs] is null while the installed packs are looked up.
 */
@Composable
internal fun IconStyleCard(
    style: IconStyle,
    savedPack: String?,
    apps: List<InstalledApp>?,
    packs: List<IconPack>?,
    facts: IconFacts?,
    onSelectStyle: (IconStyle) -> Unit,
    onSelectPack: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = apps?.size ?: PREVIEW_APP_COUNT
    val choice = packChoice(packs, savedPack, savedPack?.let { facts?.coverage?.get(it) }, total)
    val captions = STYLE_ORDER.map { styleCaption(it, choice) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    StudioCard(modifier) {
        SectionHeader(
            title = "Icon style",
            supporting = "For PadStudio's widgets and panels. Home screen icons stay as HyperOS draws them.",
        )
        StyleOptions(
            minTileWidth = minTileWidth(captions),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            STYLE_ORDER.forEachIndexed { index, option ->
                StyleOption(
                    name = option.label,
                    caption = captions[index],
                    selected = option == style,
                    onSelect = { onSelectStyle(option) },
                    apps = apps,
                    appearance = IconAppearance(option, savedPack),
                )
            }
        }
        val row = packRowText(choice)
        ListRow(
            headline = row.headline,
            supporting = row.supporting,
            leading = RowLeading.Symbol(Sym.grid, tonal = true),
            trailing = if (row.opensPicker) RowTrailing.Chevron else RowTrailing.None,
            onClick = if (row.opensPicker) ({ pickerOpen = true }) else null,
            modifier = Modifier.cardBleed(),
        )
        Disclosure {
            Text("Saved: iconStyle ${style.name} · iconPack ${savedPack ?: "none"}")
            Text(packsLine(packs, facts))
            if (facts != null) {
                Text(
                    "Themed: ${facts.oneColour} of ${facts.total} in one colour (the app's monochrome layer on Android 13+, " +
                        "else its Lawnicons glyph); own icon: ${labelList(facts.themedOriginals)}.",
                )
                if (savedPack != null) {
                    Text(
                        "Icon pack: ${facts.coverage[savedPack] ?: 0} of ${facts.total} from $savedPack; " +
                            "own icon: ${labelList(facts.packOriginals)}.",
                    )
                }
            }
            if (apps != null) Text("Previews: ${labelList(apps.map { it.facts.label })}.")
        }
    }
    if (pickerOpen) {
        IconPackPicker(
            packs = packs,
            saved = savedPack,
            styleIsPack = style == IconStyle.ICON_PACK,
            coverage = facts?.coverage,
            total = total,
            onPick = { pack ->
                pickerOpen = false
                onSelectPack(pack)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

private fun packsLine(packs: List<IconPack>?, facts: IconFacts?): String = when {
    packs == null -> "Packs: looking…"
    packs.isEmpty() -> "Packs: none (no app answers the ADW or Nova icon-pack intents)"
    else -> "Packs: " + packs.joinToString(" · ") { pack ->
        val version = facts?.versions?.get(pack.packageName)
        "${pack.label} (${pack.packageName}${if (version != null) " $version" else ""})"
    }
}

/** A style preview's corner padding, mark and icon sizes (mock-looks.html `.m-looks-style`). */
private val TilePadding = 12.dp
private val MarkSize = 24.dp
private val MarkGap = 8.dp
private val IconGap = 6.dp
private val MinIconCell = 20.dp
private val MaxIconCell = 44.dp

/** Six icons to a row when each still gets this much; otherwise four. */
private val SixColumnMinCell = 34.dp

/** Icons are drawn at this size, then fitted to their cell, so a cell size change doesn't redraw them. */
private val IconRenderSize = 44.dp

/**
 * The narrowest a style preview can be while side by side with the others: its widest word (a name beside the
 * mark, or a caption) on one line, and four icons of at least [MinIconCell]. Measured with the text as it is
 * drawn now (font scale and Bold text included), so large text stacks the previews instead of breaking words.
 */
@Composable
private fun minTileWidth(captions: List<String>): Dp {
    val measurer = rememberTextMeasurer()
    val type = MaterialTheme.typography
    val density = LocalDensity.current
    return remember(measurer, type, density, captions) {
        fun widest(texts: List<String>, style: TextStyle): Int =
            texts.flatMap { it.split(' ') }.filter { it.isNotEmpty() }
                .maxOfOrNull { word -> measurer.measure(word, style, maxLines = 1, softWrap = false).size.width } ?: 0
        with(density) {
            val name = widest(STYLE_ORDER.map { it.label }, type.titleMedium).toDp() + MarkGap + MarkSize
            val caption = widest(captions, type.bodyMedium).toDp()
            val label = widest(listOf(IN_USE), type.labelLarge).toDp()
            val icons = MinIconCell * 4 + IconGap * 3
            maxOf(name, caption, label, icons) + TilePadding * 2 + 1.dp
        }
    }
}

private const val IN_USE = "In use"

/**
 * The style previews: side by side and equally tall when each gets at least [minTileWidth], otherwise one under
 * the other at full width. Decided while measuring, so it follows the card's real width in any window.
 */
@Composable
private fun StyleOptions(minTileWidth: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier.selectableGroup()) { measurables, constraints ->
        val gap = Tokens.GapS.roundToPx()
        val count = measurables.size
        val minTile = minTileWidth.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else minTile * count + gap * (count - 1)
        val tileWidth = if (count == 0) 0 else (width - gap * (count - 1)) / count
        if (count > 0 && tileWidth >= minTile) {
            val height = measurables.maxOf { it.maxIntrinsicHeight(tileWidth) }
            val placeables = measurables.map { it.measure(Constraints.fixed(tileWidth, height)) }
            layout(width, height) {
                placeables.forEachIndexed { index, placeable -> placeable.placeRelative(index * (tileWidth + gap), 0) }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
            val height = placeables.sumOf { it.height } + gap * (count - 1).coerceAtLeast(0)
            layout(width, height) {
                var y = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(0, y)
                    y += placeable.height + gap
                }
            }
        }
    }
}

/**
 * One style as a radio button (mock-looks.html `.m-looks-style`): its [name] beside a mark, a [caption], and the
 * preview [apps] drawn in [appearance], at the bottom. The chosen one has a primary edge, a filled check mark
 * and the words "In use", so it never relies on colour alone; TalkBack reads it as a selected radio button.
 */
@Composable
private fun StyleOption(
    name: String,
    caption: String,
    selected: Boolean,
    onSelect: () -> Unit,
    apps: List<InstalledApp>?,
    appearance: IconAppearance,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val shape = RoundedCornerShape(Tokens.CornerM)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier
            .keyboardFocusRing(focused, shape, scheme.secondary)
            .clip(shape)
            .background(wellColor())
            .then(if (selected) Modifier.border(2.dp, scheme.primary, shape) else Modifier)
            .selectable(selected = selected, interactionSource = interaction, indication = ripple(), role = Role.RadioButton, onClick = onSelect)
            .padding(TilePadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(name, style = type.titleMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(MarkGap))
            SelectionMark(selected)
        }
        Text(caption, style = type.bodyMedium, color = scheme.onSurfaceVariant)
        if (selected) {
            // Said already by the radio button's state; shown for everyone who doesn't hear it.
            Text(IN_USE, style = type.labelLarge, color = scheme.primary, modifier = Modifier.clearAndSetSemantics { })
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(8.dp))
        PreviewIcons(apps, appearance, Modifier.fillMaxWidth())
    }
}

/** The radio mark: an empty outline ring, or the filled check when chosen. */
@Composable
private fun SelectionMark(selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    if (selected) {
        SymIcon(Sym.ok, contentDescription = null, filled = true, tint = scheme.primary, size = MarkSize)
    } else {
        Box(Modifier.size(MarkSize).padding(2.dp).border(2.dp, scheme.outline, CircleShape))
    }
}

/**
 * The preview apps in a grid: four to a row in a narrow preview, six when each icon still gets [SixColumnMinCell].
 * While the app list loads, the same number of empty HyperOS-shaped tiles hold the place. Decorative: the
 * preview's radio button says what it is.
 */
@Composable
private fun PreviewIcons(apps: List<InstalledApp>?, appearance: IconAppearance, modifier: Modifier = Modifier) {
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
    Layout(
        modifier = modifier,
        content = {
            if (apps == null) {
                repeat(PREVIEW_APP_COUNT) { Box(Modifier.clip(SuperellipseShape()).background(placeholder)) }
            } else {
                apps.forEach { app -> AppIcon(app = app, size = IconRenderSize, contentDescription = null, appearance = appearance) }
            }
        },
        measurePolicy = IconGridPolicy,
    )
}

/** Lays out [PreviewIcons]: equal square cells in rows, the block centred when the cells reach [MaxIconCell]. */
private object IconGridPolicy : MeasurePolicy {
    private class Grid(val columns: Int, val cell: Int, val gap: Int) {
        fun rows(count: Int) = (count + columns - 1) / columns

        fun height(count: Int): Int = rows(count).let { rows -> if (rows == 0) 0 else rows * cell + (rows - 1) * gap }

        val blockWidth: Int get() = columns * cell + (columns - 1) * gap
    }

    private fun Density.grid(width: Int): Grid {
        val gap = IconGap.roundToPx()
        if (width == Constraints.Infinity) return Grid(4, MaxIconCell.roundToPx(), gap)
        val six = (width - gap * 5) / 6
        val columns = if (six >= SixColumnMinCell.roundToPx()) 6 else 4
        val cell = ((width - gap * (columns - 1)) / columns).coerceIn(MinIconCell.roundToPx(), MaxIconCell.roundToPx())
        return Grid(columns, cell, gap)
    }

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val grid = grid(constraints.maxWidth)
        val placeables = measurables.map { it.measure(Constraints.fixed(grid.cell, grid.cell)) }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else grid.blockWidth
        val start = ((width - grid.blockWidth) / 2).coerceAtLeast(0)
        val height = grid.height(placeables.size).coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val column = index % grid.columns
                val row = index / grid.columns
                placeable.placeRelative(start + column * (grid.cell + grid.gap), row * (grid.cell + grid.gap))
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        grid(width).height(measurables.size)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        grid(width).height(measurables.size)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        MinIconCell.roundToPx() * 4 + IconGap.roundToPx() * 3

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        MaxIconCell.roundToPx() * 6 + IconGap.roundToPx() * 5
}

/**
 * The installed icon packs to choose from, as radio rows; the [saved] one is chosen. Picking one switches the
 * style to Icon pack with it, as the old pack chips did. Closing changes nothing. [packs] is null while they are
 * looked up (the picker can be open from before a process restart).
 */
@Composable
private fun IconPackPicker(
    packs: List<IconPack>?,
    saved: String?,
    styleIsPack: Boolean,
    coverage: Map<String, Int>?,
    total: Int,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { PillButton(text = "Close", onClick = onDismiss, style = PillStyle.Text) },
        icon = { SymIcon(Sym.grid, contentDescription = null) },
        title = { Text("Icon pack") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (packs == null) {
                    Text("Looking for installed packs…", style = type.bodyLarge, color = scheme.onSurfaceVariant)
                } else if (packs.isEmpty()) {
                    EmptyState(
                        title = "No icon pack installed",
                        icon = Sym.grid,
                        body = "Install Lawnicons or Arcticons, then come back here.",
                    )
                } else {
                    if (!styleIsPack) {
                        Text("Choosing a pack also sets Icon style to Icon pack.", style = type.bodyLarge, color = scheme.onSurfaceVariant)
                    }
                    Column(Modifier.selectableGroup()) {
                        packs.forEach { pack ->
                            val chosen = pack.packageName == saved
                            ListRow(
                                headline = pack.label,
                                supporting = pickerCoverageText(coverage?.get(pack.packageName), total) ?: pack.packageName,
                                leading = RowLeading.Symbol(Sym.grid),
                                trailing = if (chosen) RowTrailing.Symbol(Sym.ok) else RowTrailing.None,
                                selected = chosen,
                                onClick = { onPick(pack.packageName) },
                                // Inside the dialog's 24 dp padding; a vertical scroll clips only 30 dp past its sides.
                                modifier = Modifier.cardBleed(),
                            )
                        }
                    }
                }
                Text("New packs show up when you come back to Looks.", style = type.bodyMedium, color = scheme.onSurfaceVariant)
            }
        },
        shape = RoundedCornerShape(Tokens.CornerXl),
    )
}
