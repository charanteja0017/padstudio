package com.charan.padstudio.home.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.charan.padstudio.core.home.GroupPlanner
import com.charan.padstudio.core.home.WidgetLayout
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.AppIcon
import com.charan.padstudio.ui.components.SuperellipseShape
import com.charan.padstudio.ui.components.SymIcon
import com.charan.padstudio.ui.theme.Tokens
import kotlin.math.min

/**
 * A widget of [widgetSize] laid out as [layout] (from GroupPlanner.layoutFor), drawn to scale on a wallpaper
 * tint: the header with [title], then the grid with [apps]' real icons, the "+N" tile in the last cell when
 * they don't all fit, and faint empty spaces. Label lines show as bars when [WidgetLayout.showLabels].
 *
 * It is a picture: TalkBack reads [description] once, and the caption under it carries the numbers. Its text
 * is sized with the drawing, not the text setting, like the widget it shrinks.
 */
@Composable
internal fun WidgetPreview(
    layout: WidgetLayout,
    widgetSize: SizeDp,
    apps: List<InstalledApp>,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val wall = if (dark) {
        Brush.linearGradient(listOf(lerp(scheme.primaryContainer, scheme.surfaceContainerLowest, 0.4f), lerp(scheme.tertiaryContainer, scheme.surfaceContainerLowest, 0.55f)))
    } else {
        Brush.linearGradient(listOf(lerp(scheme.primaryContainer, scheme.background, 0.6f), lerp(scheme.tertiaryContainer, scheme.background, 0.68f)))
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.CornerM))
            .background(wall)
            .clearAndSetSemantics { contentDescription = description }
            .padding(WALL_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        val scale = min(1f, maxWidth.value / widgetSize.width)
        val k: (Float) -> Dp = { (it * scale).dp }
        Column(
            Modifier
                .size(k(widgetSize.width), k(widgetSize.height))
                .clip(RoundedCornerShape(k(Tokens.CornerL.value)))
                .background(scheme.surfaceContainerHigh)
                .padding(k(GroupPlanner.PADDING_DP.toFloat())),
        ) {
            PreviewHeader(title, k(GroupPlanner.HEADER_DP.toFloat()))
            val overflow = layout.overflow(apps.size)
            val icon = k(layout.iconDp)
            for (row in 0 until layout.rows) {
                Row(Modifier.fillMaxWidth().height(k(layout.cellHeightDp))) {
                    for (column in 0 until layout.columns) {
                        val index = row * layout.columns + column
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            when {
                                index < overflow.shown -> PreviewApp(apps[index], icon, layout.showLabels)
                                index == overflow.moreIndex -> MoreTile(overflow.more, icon)
                                else -> EmptySpace(icon)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The widget's header: a pill with the widgets symbol and the group's name, at the header's scale. */
@Composable
private fun PreviewHeader(title: String, height: Dp) {
    val scheme = MaterialTheme.colorScheme
    val textSize = with(LocalDensity.current) { (height * HEADER_TEXT_SHARE).toSp() }
    Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(scheme.secondaryContainer)
                .padding(horizontal = height * 0.3f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(height * 0.15f),
        ) {
            SymIcon(Sym.widgets, contentDescription = null, size = height * 0.45f, tint = scheme.onSecondaryContainer)
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = textSize, lineHeight = textSize),
                color = scheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PreviewApp(app: InstalledApp, icon: Dp, labels: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppIcon(app = app, size = icon, contentDescription = null)
        if (labels) {
            Spacer(Modifier.height(icon * 0.08f))
            Box(
                Modifier
                    .width(icon * 0.8f)
                    .height(icon * 0.1f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = LABEL_ALPHA)),
            )
        }
    }
}

/** The "+N" tile in the last cell: the rest of the group opens in the folder panel. */
@Composable
private fun MoreTile(count: Int, icon: Dp) {
    val scheme = MaterialTheme.colorScheme
    val textSize = with(LocalDensity.current) { (icon * MORE_TEXT_SHARE).toSp() }
    Box(
        Modifier
            .size(icon)
            .clip(SuperellipseShape())
            .background(scheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+$count",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = textSize, lineHeight = textSize),
            color = scheme.onSecondaryContainer,
            maxLines = 1,
        )
    }
}

/** A space with no app yet, so the grid's shape shows even when the group is small. */
@Composable
private fun EmptySpace(icon: Dp) {
    Box(
        Modifier
            .size(icon)
            .clip(SuperellipseShape())
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = SLOT_ALPHA)),
    )
}

private val WALL_PADDING = 14.dp
private const val HEADER_TEXT_SHARE = 0.42f
private const val MORE_TEXT_SHARE = 0.36f
private const val LABEL_ALPHA = 0.32f
private const val SLOT_ALPHA = 0.1f
