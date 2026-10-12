package com.charan.padstudio.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.charan.padstudio.ui.theme.Tokens

/**
 * One PadStudio page, as in the reference (`docs/redesign/reference-today.png`): a large [title], a short
 * [subtitle] under it, [actions] at the top right (an outlined [PillButton] such as "Open library"), then
 * [content] in a column with 16 dp between items.
 *
 * Content is at most [ScreenDefaults.MaxContentWidth] (1040 dp) wide, start-aligned, with 24 dp gutters
 * (plan §10). When the window is too narrow for the title and the actions side by side, or the text is
 * large, the actions move under the title instead of squeezing it.
 *
 * The page scrolls with [scrollState]. Pass `scrollState = null` for a fixed page whose content gets the
 * remaining height: use that when the content scrolls itself (a lazy list or grid, or list–detail panes),
 * because a lazy list can't sit inside a scrolling column.
 *
 * It applies no window insets: the shell's Scaffold does.
 */
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scrollState: ScrollState? = rememberScrollState(),
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val page = modifier
        .fillMaxSize()
        .then(if (scrollState != null) Modifier.verticalScroll(scrollState) else Modifier)
        .padding(
            start = ScreenDefaults.Gutter,
            end = ScreenDefaults.Gutter,
            top = ScreenDefaults.TopPadding,
            bottom = ScreenDefaults.BottomPadding,
        )
    Column(page) {
        val column = Modifier.widthIn(max = ScreenDefaults.MaxContentWidth).fillMaxWidth()
        ScreenHeader(title, subtitle, actions, column)
        Spacer(Modifier.height(ScreenDefaults.HeaderGap))
        Column(
            modifier = if (scrollState != null) column else column.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Tokens.GapM),
            content = content,
        )
    }
}

/** Title block on the left, actions on the right, top-aligned; the actions wrap under a title that needs the room. */
@Composable
private fun ScreenHeader(
    title: String,
    subtitle: String?,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = Tokens.WeightMedium),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val actions = measurables[1].measure(loose)
        val gap = Tokens.GapM.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val besideRoom = if (width == Constraints.Infinity) Constraints.Infinity else width - actions.width - gap
        val beside = actions.width == 0 || besideRoom >= ScreenDefaults.MinTitleWidth.roundToPx()
        if (beside) {
            val titleMax = if (actions.width == 0) width else besideRoom
            val titleBlock = measurables[0].measure(loose.copy(maxWidth = titleMax))
            val actionsTop = 4.dp.roundToPx()
            val layoutWidth = if (width == Constraints.Infinity) titleBlock.width + gap + actions.width else width
            val height = maxOf(titleBlock.height, if (actions.width == 0) 0 else actions.height + actionsTop)
            layout(layoutWidth, height) {
                titleBlock.placeRelative(0, 0)
                if (actions.width > 0) actions.placeRelative(layoutWidth - actions.width, actionsTop)
            }
        } else {
            val titleBlock = measurables[0].measure(loose.copy(maxWidth = width))
            val between = Tokens.GapS.roundToPx()
            layout(width, titleBlock.height + between + actions.height) {
                titleBlock.placeRelative(0, 0)
                actions.placeRelative(0, titleBlock.height + between)
            }
        }
    }
}
