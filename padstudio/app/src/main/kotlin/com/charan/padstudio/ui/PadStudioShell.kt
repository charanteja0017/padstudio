package com.charan.padstudio.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.charan.padstudio.ui.components.AmbientBackdrop
import com.charan.padstudio.ui.components.GlassDefaults
import com.charan.padstudio.ui.components.GlassSurface
import com.charan.padstudio.ui.theme.Tokens

/**
 * One place in the shell's navigation: its label and Material Symbol, outline and filled. [selectedIcon] shows
 * while it's selected, but the indicator marks the selection, not the fill (some fills look like the outline).
 * [key] identifies it; MainActivity uses its Tab enum.
 */
@Immutable
class ShellDestination<out T>(
    val key: T,
    val label: String,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
)

/** Sizes and colours of the shell (plan §10), for screens that line up with it. */
object ShellDefaults {
    /** Windows narrower than this get the floating pill along the bottom instead of the rail at the start. */
    val RailMinWindowWidth: Dp = 600.dp

    /** Screen content is never wider than this (plan §10); the shell leaves the rest of a wider window empty. */
    val ContentMaxWidth: Dp = 1040.dp

    /**
     * The 24 dp gutters (plan §10). The shell doesn't pad screens, so their content can scroll to the edges:
     * use this as your scrolling container's contentPadding (it fits inside the shell's width cap).
     */
    val ContentPadding: PaddingValues = PaddingValues(Tokens.Gutter)

    /** The page colour behind the rail and every screen: the mock-ups' violet-tinted canvas, mixed from scheme roles. */
    @Composable
    @ReadOnlyComposable
    fun canvasColor(): Color {
        val colors = MaterialTheme.colorScheme
        return if (colors.isDark()) {
            lerp(colors.surfaceContainerLowest, colors.primaryContainer, 0.06f)
        } else {
            lerp(colors.background, colors.primaryContainer, 0.05f)
        }
    }
}

/**
 * PadStudio's frame (round 2): navigation is a floating glass pill of icon buttons (no labels; each has a tooltip
 * and a content description) over the [AmbientBackdrop], with the content and the snackbar beside or above it.
 * By window width:
 * - at least [ShellDefaults.RailMinWindowWidth]: a 96 dp column at the start holding a vertical pill, centred
 *   vertically, then the content area;
 * - narrower: the content area with 96 dp of bottom padding, and a horizontal pill along the bottom, 16 dp above
 *   the bottom insets.
 *
 * [content] goes in the content area. The shell handles the system bars, display cutouts and the keyboard there,
 * and caps the width at [ShellDefaults.ContentMaxWidth] plus the gutters, start aligned. Screens own their layout:
 * fill the area and use [ShellDefaults.ContentPadding] for the gutters. [selected] is the destination key to
 * highlight; [snackbarHost] sits at the bottom of the content area.
 */
@Composable
fun <T> PadStudioShell(
    destinations: List<ShellDestination<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    AmbientBackdrop(modifier.fillMaxSize()) {
        // The backdrop is not a Surface: give text without its own colour the theme's on-surface.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= ShellDefaults.RailMinWindowWidth) {
                    Row(Modifier.fillMaxSize()) {
                        FloatingRail(destinations, selected, onSelect)
                        // The rail takes the start side.
                        ContentArea(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End),
                            snackbarHost,
                            content,
                            Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        // The pill floats over the bottom 80 dp (16 dp above the insets, 64 dp tall); the content ends 16 dp above it.
                        ContentArea(WindowInsets.safeDrawing, snackbarHost, content, Modifier.fillMaxSize().padding(bottom = FloatSpan))
                        FloatingBar(destinations, selected, onSelect, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
    }
}

/** The pill's thickness: the rail pill's width and the bottom pill's height. */
private val PillThickness = 64.dp

/** The gap between a pill and the window edge (or the insets) and between a pill and the content. */
private val FloatMargin = 16.dp

/** What a pill takes of the window across its thickness: the rail column's width, the content's bottom padding. */
private val FloatSpan = PillThickness + FloatMargin * 2

private val PillPadding = 8.dp
private val ButtonGap = 4.dp
private val PillShape = RoundedCornerShape(32.dp)

private val ButtonSize = 48.dp
private val IconSize = 24.dp

private val FocusRingWidth = 3.dp
private val FocusRingGap = 2.dp

private fun ColorScheme.isDark() = surface.luminance() < 0.5f

/**
 * The rail: a [FloatSpan] wide column at the start, under the system bars, with one glass pill of icon buttons
 * centred in it (scrolling when the window is shorter than the pill).
 */
@Composable
private fun <T> FloatingRail(destinations: List<ShellDestination<T>>, selected: T, onSelect: (T) -> Unit) {
    Box(
        Modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
            .width(FloatSpan),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(shape = PillShape, alpha = GlassDefaults.OVER_CONTENT_ALPHA) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .selectableGroup()
                    .padding(PillPadding),
                verticalArrangement = Arrangement.spacedBy(ButtonGap),
            ) {
                destinations.forEach { destination ->
                    RailIconButton(destination, destination.key == selected, TooltipAnchorPosition.End) { onSelect(destination.key) }
                }
            }
        }
    }
}

/**
 * The narrow window's navigation: the same buttons in a horizontal glass pill, 16 dp above the bottom insets
 * (give [modifier] the alignment; scrolls sideways when the window is narrower than the pill).
 */
@Composable
private fun <T> FloatingBar(
    destinations: List<ShellDestination<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(bottom = FloatMargin)
            .height(PillThickness),
        shape = PillShape,
        alpha = GlassDefaults.OVER_CONTENT_ALPHA,
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState())
                .selectableGroup()
                .padding(horizontal = PillPadding),
            horizontalArrangement = Arrangement.spacedBy(ButtonGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                RailIconButton(destination, destination.key == selected, TooltipAnchorPosition.Above) { onSelect(destination.key) }
            }
        }
    }
}

/**
 * One navigation button: a 48 dp circle, primary when [selected], holding the destination's 24 dp icon (filled
 * while selected). The label is the icon's content description and the tooltip (long press, or hover with the pen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RailIconButton(destination: ShellDestination<*>, selected: Boolean, tooltipAt: TooltipAnchorPosition, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(tooltipAt),
        tooltip = { PlainTooltip { Text(destination.label) } },
        state = rememberTooltipState(),
    ) {
        Box(
            Modifier
                .size(ButtonSize)
                .focusRing(focused, colors.secondary, ButtonSize / 2)
                .clip(CircleShape)
                .background(if (selected) colors.primary else Color.Transparent)
                .selectable(selected, interaction, ripple(), role = Role.Tab, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (selected) destination.selectedIcon else destination.icon),
                contentDescription = destination.label,
                tint = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                modifier = Modifier.size(IconSize),
            )
        }
    }
}

/** The content beside the rail: inset from the system bars, cutouts and keyboard, at most 1040 dp plus gutters. */
@Composable
private fun ContentArea(
    insets: WindowInsets,
    snackbarHost: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().windowInsetsPadding(insets)) {
        Box(Modifier.fillMaxHeight().widthIn(max = ShellDefaults.ContentMaxWidth + Tokens.Gutter * 2).fillMaxWidth()) {
            content()
        }
        Box(Modifier.align(Alignment.BottomCenter)) { snackbarHost() }
    }
}

/** The kit's keyboard focus ring (kit.css "focus"): 3 dp of secondary, 2 dp outside the item's shape. */
private fun Modifier.focusRing(visible: Boolean, color: Color, corner: Dp): Modifier =
    if (!visible) {
        this
    } else {
        drawWithContent {
            drawContent()
            val stroke = FocusRingWidth.toPx()
            val outset = FocusRingGap.toPx() + stroke / 2
            drawRoundRect(
                color = color,
                topLeft = Offset(-outset, -outset),
                size = Size(size.width + 2 * outset, size.height + 2 * outset),
                cornerRadius = CornerRadius(corner.toPx() + outset),
                style = Stroke(stroke),
            )
        }
    }
