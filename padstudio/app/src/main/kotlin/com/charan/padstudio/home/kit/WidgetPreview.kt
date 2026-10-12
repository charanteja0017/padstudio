package com.charan.padstudio.home.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.charan.padstudio.core.home.GroupPlanner
import com.charan.padstudio.core.home.WidgetLayout
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.home.GlassPalette
import com.charan.padstudio.home.WallpaperLook
import com.charan.padstudio.home.WidgetGlass
import com.charan.padstudio.home.glassFill
import com.charan.padstudio.home.glassPalette
import com.charan.padstudio.home.readWallpaper
import com.charan.padstudio.home.widgetGlass
import com.charan.padstudio.studio
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.AppIcon
import com.charan.padstudio.ui.components.SuperellipseShape
import com.charan.padstudio.ui.components.SymIcon
import com.charan.padstudio.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * A widget of [widgetSize] laid out as [layout] (from GroupPlanner.layoutFor), drawn to scale in the real
 * widget's glass: the tint, fill, sheen and edge GroupWidget picks for this wallpaper, with no stand-in
 * wallpaper behind it. Then the header with [title], the grid with [apps]' real icons, the "+N" tile in the last
 * cell when they don't all fit, and faint empty spaces. Label lines show as bars when [WidgetLayout.showLabels].
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
    val look = rememberWidgetLook()
    val ink = glassPalette(look.glass)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val scale = min(1f, maxWidth.value / widgetSize.width)
        val k: (Float) -> Dp = { (it * scale).dp }
        Column(
            Modifier
                .size(k(widgetSize.width), k(widgetSize.height))
                .widgetGlass(look, RoundedCornerShape(k(Tokens.CornerL.value)))
                .padding(k(GroupPlanner.PADDING_DP.toFloat())),
        ) {
            PreviewHeader(title, k(GroupPlanner.HEADER_DP.toFloat()), ink)
            val overflow = layout.overflow(apps.size)
            val icon = k(layout.iconDp)
            for (row in 0 until layout.rows) {
                Row(Modifier.fillMaxWidth().height(k(layout.cellHeightDp))) {
                    for (column in 0 until layout.columns) {
                        val index = row * layout.columns + column
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            when {
                                index < overflow.shown -> PreviewApp(apps[index], icon, layout.showLabels, ink)
                                index == overflow.moreIndex -> MoreTile(overflow.more, icon, ink)
                                else -> EmptySpace(icon, ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The glass the real widget draws over this wallpaper: its tint and its fill. */
private class WidgetLook(val glass: WidgetGlass, val fill: Color)

/**
 * The tint and fill GroupWidget picks: from the home wallpaper's colour hints (read on IO; dark glass until they
 * arrive or when they can't be read), with the wallpaper's main colour mixed in while "Match wallpaper colours"
 * is on.
 */
@Composable
private fun rememberWidgetLook(): WidgetLook {
    val context = LocalContext.current
    val config by context.studio.config.config.collectAsState()
    val wallpaper by produceState<WallpaperLook?>(null) {
        value = withContext(Dispatchers.IO) { readWallpaper(context) }
    }
    val glass = widgetGlass(wallpaper?.hints)
    return WidgetLook(glass, glassFill(glass, if (config.matchWallpaperColours) wallpaper?.primary else null))
}

/**
 * The fill with the widget's sheen and edge over it, as res/drawable/widget_glass_light and widget_glass_dark
 * draw them: a white sheen (30 % on light glass, 10 % on dark) fading out by 35 % of the height, and a 1 dp
 * white edge (60 % / 14 %).
 */
private fun Modifier.widgetGlass(look: WidgetLook, shape: Shape): Modifier {
    val light = look.glass == WidgetGlass.LIGHT
    val sheen = Color.White.copy(alpha = if (light) 0.30f else 0.10f)
    val edge = Color.White.copy(alpha = if (light) 0.60f else 0.14f)
    return this
        .clip(shape)
        .background(look.fill)
        .background(Brush.verticalGradient(0f to sheen, SHEEN_END to Color.Transparent))
        .border(1.dp, edge, shape)
}

/** The widget's header: a glass chip with the widgets symbol and the group's name, at the header's scale. */
@Composable
private fun PreviewHeader(title: String, height: Dp, ink: GlassPalette) {
    val textSize = with(LocalDensity.current) { (height * HEADER_TEXT_SHARE).toSp() }
    Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(ink.chip)
                .padding(horizontal = height * 0.3f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(height * 0.15f),
        ) {
            SymIcon(Sym.widgets, contentDescription = null, size = height * 0.45f, tint = ink.text)
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = textSize, lineHeight = textSize),
                color = ink.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PreviewApp(app: InstalledApp, icon: Dp, labels: Boolean, ink: GlassPalette) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppIcon(app = app, size = icon, contentDescription = null)
        if (labels) {
            Spacer(Modifier.height(icon * 0.08f))
            Box(
                Modifier
                    .width(icon * 0.8f)
                    .height(icon * 0.1f)
                    .clip(CircleShape)
                    .background(ink.text.copy(alpha = LABEL_ALPHA)),
            )
        }
    }
}

/** The "+N" tile in the last cell, a glass chip: the rest of the group opens in the folder panel. */
@Composable
private fun MoreTile(count: Int, icon: Dp, ink: GlassPalette) {
    val textSize = with(LocalDensity.current) { (icon * MORE_TEXT_SHARE).toSp() }
    Box(
        Modifier
            .size(icon)
            .clip(SuperellipseShape())
            .background(ink.chip),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+$count",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = textSize, lineHeight = textSize),
            color = ink.text,
            maxLines = 1,
        )
    }
}

/** A space with no app yet, so the grid's shape shows even when the group is small. */
@Composable
private fun EmptySpace(icon: Dp, ink: GlassPalette) {
    Box(
        Modifier
            .size(icon)
            .clip(SuperellipseShape())
            .background(ink.text.copy(alpha = SLOT_ALPHA)),
    )
}

private const val SHEEN_END = 0.35f
private const val HEADER_TEXT_SHARE = 0.42f
private const val MORE_TEXT_SHARE = 0.36f
private const val LABEL_ALPHA = 0.32f
private const val SLOT_ALPHA = 0.1f
