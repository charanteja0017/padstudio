package com.charan.padstudio.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.theme.Tokens

/** The glass a [StudioCard] is made of, as the kit's `.card` variants. */
enum class CardTone {
    /** Glass at the theme's glass opacity with the highlight edge: the default card. */
    Low,

    /** Glass 8 points more opaque: a card inside a card, or one that must stand out a little. */
    High,

    /** Primary container, solid, with the highlight edge: the one hero card of a page. */
    Hero,

    /** Secondary container at 55 % with the highlight edge: a callout such as "Changes not sent yet · Send". */
    Tonal,

    /** No fill, an outline-variant edge: optional or empty content. */
    Outlined,
}

/**
 * A glass card (round 2): translucent over the [AmbientBackdrop], 28 dp corners ([GlassDefaults.Shape]), a 1 dp
 * highlight edge, no shadow, 24 dp padding, [content] in a column with [spacing] between items. The default
 * [tone] is plain glass; see [CardTone] for the others.
 *
 * With [onClick] the whole card is one button (ripple clipped to the corners, focus ring, 48 dp minimum).
 */
@Composable
fun StudioCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Low,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Tokens.CardPadding),
    spacing: Dp = Tokens.GapS,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = GlassDefaults.alpha()
    val fill = when (tone) {
        CardTone.Low -> GlassDefaults.fill().copy(alpha = glass)
        CardTone.High -> GlassDefaults.fill().copy(alpha = glass + HIGH_EXTRA_ALPHA)
        CardTone.Hero -> scheme.primaryContainer
        CardTone.Tonal -> scheme.secondaryContainer.copy(alpha = TONAL_ALPHA)
        CardTone.Outlined -> Color.Transparent
    }
    val edge = if (tone == CardTone.Outlined) scheme.outlineVariant else GlassDefaults.edge()
    val contentColor = when (tone) {
        CardTone.Hero -> scheme.onPrimaryContainer
        CardTone.Tonal -> scheme.onSecondaryContainer
        else -> scheme.onSurface
    }
    val shape = GlassDefaults.Shape
    val body: @Composable BoxScope.() -> Unit = {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
    // A glass panel is a plain Box, so the content colour that Material's Surface used to set is set here,
    // around the panel like Surface did: the ripple takes it too.
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        if (onClick != null) {
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            GlassSurface(
                modifier = modifier
                    .focusRing(focused, shape, scheme.secondary, gap = FocusRingGap)
                    .minimumInteractiveComponentSize(),
                shape = shape,
                fill = fill,
                edge = edge,
                onClick = onClick,
                interactionSource = interaction,
                content = body,
            )
        } else {
            GlassSurface(modifier = modifier, shape = shape, fill = fill, edge = edge, content = body)
        }
    }
}

/**
 * The heading of a card or a section: [title] in the title role, on-surface like the reference's card
 * titles, or primary when [accent] (the WiFiBar-style section label). An optional [overline] sits above it
 * in small capitals ("YOUR PEN") and [supporting] text under it.
 *
 * With [actionLabel] and [onAction] a text link with a chevron sits at the end, like the reference's
 * "Manage ›". For any other trailing control (a [SegmentedChoice], a switch), put the header in a Row with
 * `Modifier.weight(1f)` and the control after it.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    overline: String? = null,
    accent: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (overline != null) {
                Text(
                    text = overline.uppercase(),
                    style = type.labelMedium.copy(letterSpacing = 0.08.em),
                    color = scheme.onSurfaceVariant,
                )
            }
            Text(
                text = title,
                style = type.titleLarge,
                color = if (accent) scheme.primary else scheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (supporting != null) {
                Text(text = supporting, style = type.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        if (actionLabel != null && onAction != null) {
            PillButton(
                text = actionLabel,
                onClick = onAction,
                style = PillStyle.Text,
                trailingIcon = Sym.chevron,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Scope of [CardGrid]: [span] lets a card take several columns. */
@Stable
interface CardGridScope {
    /** This card takes [columns] columns (clamped to the grid's column count). */
    fun Modifier.span(columns: Int): Modifier
}

/**
 * Cards in equal columns: as many as fit at [minColumnWidth], at most [maxColumns], filled row by row and
 * top-aligned (a row is as tall as its tallest card). On the tablet's content column (948 dp next to the
 * expanded rail, 900 dp inside the gutters) the defaults give 2 columns; [minColumnWidth] 280 dp gives 3.
 * A narrow window drops to 1 column.
 *
 * It is not lazy: use it for a page's handful of cards, inside [ScreenScaffold]'s scrolling column. A
 * card can take several columns with `Modifier.span(n)`.
 */
@Composable
fun CardGrid(
    modifier: Modifier = Modifier,
    minColumnWidth: Dp = 320.dp,
    maxColumns: Int = 3,
    spacing: Dp = Tokens.GapM,
    content: @Composable CardGridScope.() -> Unit,
) {
    Layout(content = { CardGridScopeInstance.content() }, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else minColumnWidth.roundToPx() * maxColumns.coerceAtLeast(1)
        val fit = ((width + gap) / (minColumnWidth.roundToPx() + gap).coerceAtLeast(1)).coerceAtLeast(1)
        val columns = fit.coerceAtMost(maxColumns.coerceAtLeast(1))
        val columnWidth = ((width - gap * (columns - 1)) / columns).coerceAtLeast(0)

        // Row-major packing: a card that doesn't fit what's left of a row starts the next one.
        class Cell(val index: Int, val column: Int, val row: Int, val span: Int)
        val cells = ArrayList<Cell>(measurables.size)
        var row = 0
        var column = 0
        measurables.forEachIndexed { index, measurable ->
            val span = ((measurable.parentData as? GridSpan)?.columns ?: 1).coerceIn(1, columns)
            if (column + span > columns) {
                row++
                column = 0
            }
            cells += Cell(index, column, row, span)
            column += span
            if (column >= columns) {
                row++
                column = 0
            }
        }
        val placeables = cells.map { cell ->
            val cellWidth = columnWidth * cell.span + gap * (cell.span - 1)
            measurables[cell.index].measure(Constraints(minWidth = cellWidth, maxWidth = cellWidth))
        }
        val rows = (cells.maxOfOrNull { it.row } ?: -1) + 1
        val rowHeights = IntArray(rows)
        cells.forEachIndexed { i, cell -> rowHeights[cell.row] = maxOf(rowHeights[cell.row], placeables[i].height) }
        val rowTops = IntArray(rows)
        for (r in 1 until rows) rowTops[r] = rowTops[r - 1] + rowHeights[r - 1] + gap
        val height = if (rows == 0) 0 else rowTops[rows - 1] + rowHeights[rows - 1]
        layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            cells.forEachIndexed { i, cell ->
                placeables[i].placeRelative(cell.column * (columnWidth + gap), rowTops[cell.row])
            }
        }
    }
}

/**
 * Two columns of cards, each stacking its own cards with [spacing] between them, as the reference's
 * Today page does (hero and "Pen by app" on the left, "Home screen" and "Make it yours" on the right). The
 * columns share the width [startWeight] : [endWeight] (3 : 2 like the reference), or the end column is a
 * fixed [endWidth] (the mock-ups' 344–372 dp side columns) when that is given. Below [stackBelow] wide, the
 * end column moves under the start column.
 */
@Composable
fun CardColumns(
    modifier: Modifier = Modifier,
    startWeight: Float = 3f,
    endWeight: Float = 2f,
    endWidth: Dp = Dp.Unspecified,
    stackBelow: Dp = 720.dp,
    spacing: Dp = Tokens.GapM,
    start: @Composable ColumnScope.() -> Unit,
    end: @Composable ColumnScope.() -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing), content = start)
            Column(verticalArrangement = Arrangement.spacedBy(spacing), content = end)
        },
    ) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else stackBelow.roundToPx()
        if (width < stackBelow.roundToPx()) {
            val first = measurables[0].measure(Constraints.fixedWidth(width))
            val second = measurables[1].measure(Constraints.fixedWidth(width))
            val height = first.height + gap + second.height
            layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
                first.placeRelative(0, 0)
                second.placeRelative(0, first.height + gap)
            }
        } else {
            val inner = width - gap
            val endPx = if (endWidth != Dp.Unspecified) {
                endWidth.roundToPx().coerceIn(0, inner)
            } else {
                val total = (startWeight + endWeight).takeIf { it > 0f } ?: 1f
                (inner * (endWeight / total)).toInt()
            }
            val startPx = inner - endPx
            val first = measurables[0].measure(Constraints.fixedWidth(startPx))
            val second = measurables[1].measure(Constraints.fixedWidth(endPx))
            val height = maxOf(first.height, second.height)
            layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
                first.placeRelative(0, 0)
                second.placeRelative(startPx + gap, 0)
            }
        }
    }
}

/** A [CardTone.High] card is this much more opaque than plain glass. */
private const val HIGH_EXTRA_ALPHA = 0.08f

/** How much of the secondary container a [CardTone.Tonal] card shows. */
private const val TONAL_ALPHA = 0.55f

@Immutable
private data class GridSpan(val columns: Int) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@GridSpan
}

private object CardGridScopeInstance : CardGridScope {
    override fun Modifier.span(columns: Int): Modifier = this.then(GridSpan(columns))
}
