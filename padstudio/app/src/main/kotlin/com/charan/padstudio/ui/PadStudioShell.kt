package com.charan.padstudio.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.charan.padstudio.R
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

/** The colour of a status line's dot (plan §7). */
enum class ShellStatusTone {
    /** As configured, for example "Key Mapper is listening". */
    Good,

    /** Needs a look, for example "Key Mapper is off" or "Changes not sent yet". */
    Attention,

    /** Can't be known from the tablet. */
    Unknown,
}

/** Sizes and colours of the shell (plan §10), for screens that line up with it. */
object ShellDefaults {
    /** Windows narrower than this get a bottom bar instead of a rail. */
    val RailMinWindowWidth: Dp = 600.dp

    /** From this window width the rail is expanded, as in the reference design; below it, icons sit over labels. */
    val ExpandedRailMinWindowWidth: Dp = 840.dp

    /** The expanded rail: the PadStudio mark and wordmark, labelled items, the status line and Settings. */
    val ExpandedRailWidth: Dp = 216.dp

    /** The compact rail (M3's collapsed rail width): mark, icons over labels, the status line. */
    val CollapsedRailWidth: Dp = 96.dp

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
 * PadStudio's frame (plan §10 "Shell"): navigation, a content area and the snackbar. By window width:
 * - at least [ShellDefaults.ExpandedRailMinWindowWidth]: the expanded rail of the reference design, 216 dp:
 *   the mark and wordmark, labelled items with a full-width indicator, and at its foot [statusLine] and a
 *   Settings row that calls [onSettings];
 * - at least [ShellDefaults.RailMinWindowWidth]: a 96 dp rail, icons in a pill over their labels, [statusLine] at its foot;
 * - narrower: a bottom bar (M3 ShortNavigationBar), with no status line.
 *
 * The rails are drawn here rather than with M3 WideNavigationRail: in Material 3 1.4.0 its expanded item's
 * indicator is a fixed full pill (CornerFull, no shape parameter) that hugs the icon and label, its labels are
 * LabelLarge (14 sp), it is at least 220 dp wide, and it lays out its own items, so a footer can't be pinned to
 * the bottom. The reference needs a full-width 16 dp-corner indicator, 16 sp labels, 216 dp and a pinned footer.
 *
 * [content] goes in the area beside the rail (or above the bar). The shell handles the system bars, display
 * cutouts and the keyboard there, and caps the width at [ShellDefaults.ContentMaxWidth] plus the gutters, start
 * aligned. Screens own their layout: fill the area and use [ShellDefaults.ContentPadding] for the gutters.
 * [selected] is the destination key to highlight; [snackbarHost] sits at the bottom of the content area.
 */
@Composable
fun <T> PadStudioShell(
    destinations: List<ShellDestination<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onSettings: () -> Unit,
    statusLine: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Surface(modifier.fillMaxSize(), color = ShellDefaults.canvasColor(), contentColor = MaterialTheme.colorScheme.onSurface) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val layout = when {
                maxWidth >= ShellDefaults.ExpandedRailMinWindowWidth -> ShellLayout.ExpandedRail
                maxWidth >= ShellDefaults.RailMinWindowWidth -> ShellLayout.CollapsedRail
                else -> ShellLayout.BottomBar
            }
            // One tree for every layout, so the content keeps its place (and state) when the window crosses a breakpoint.
            Row(Modifier.fillMaxSize()) {
                when (layout) {
                    ShellLayout.ExpandedRail -> {
                        ExpandedRail(destinations, selected, onSelect, onSettings, statusLine)
                        RailEdge()
                    }
                    ShellLayout.CollapsedRail -> {
                        CollapsedRail(destinations, selected, onSelect, statusLine)
                        RailEdge()
                    }
                    ShellLayout.BottomBar -> Unit
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    val insets = if (layout == ShellLayout.BottomBar) {
                        // The bar below takes the navigation bar; the top and sides are the content's.
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    } else {
                        // The rail takes the start side.
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                    }
                    ContentArea(insets, snackbarHost, content, Modifier.weight(1f))
                    if (layout == ShellLayout.BottomBar) BottomBar(destinations, selected, onSelect)
                }
            }
        }
    }
}

/**
 * A line for [PadStudioShell]'s statusLine slot: a 10 dp dot in [tone]'s colour, then [text]. Keep it short and
 * truthful (plan §7): it may say Key Mapper is listening, never that Expert mode is running.
 */
@Composable
fun ShellStatusLine(text: String, tone: ShellStatusTone, modifier: Modifier = Modifier) {
    val dot = statusDotColor(tone)
    val style = LocalTextStyle.current
    if (LocalRailExpanded.current) {
        // The dot sits on the first line when the text wraps (it does at 200 % or with Bold text).
        val lineHeight = with(LocalDensity.current) { if (style.lineHeight.isSp) style.lineHeight.toDp() else DotSize }
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.padding(top = ((lineHeight - DotSize) / 2).coerceAtLeast(0.dp)).size(DotSize).background(dot, CircleShape))
            Text(text, style = style)
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(DotSize).background(dot, CircleShape))
            Text(text, style = style)
        }
    }
}

private enum class ShellLayout { ExpandedRail, CollapsedRail, BottomBar }

/** Whether the status line is in the expanded rail (a row) or the compact one (a centred column). */
private val LocalRailExpanded = staticCompositionLocalOf { true }

private val DotSize = 10.dp
private val MarkSize = 28.dp

/** An adaptive-icon layer is 108 units square with the mark in its middle. */
private const val LAYER_UNITS = 108f

/**
 * How many layer units span [MarkSize]: the interim P is 48 units tall, so it shows 24 dp tall, close to the
 * reference. Revisit when the picked logo lands (plan §9, W4).
 */
private const val MARK_UNITS = 56f

private val FocusRingWidth = 3.dp
private val FocusRingGap = 2.dp

private fun ColorScheme.isDark() = surface.luminance() < 0.5f

@Composable
@ReadOnlyComposable
private fun statusDotColor(tone: ShellStatusTone): Color {
    val colors = MaterialTheme.colorScheme
    val dark = colors.isDark()
    // Good and Warn are fixed colours too light for a 3:1 dot on light surfaces, so light schemes darken them
    // the way kit.css mixes its status inks (Compose's lerp mixes in Oklab, like color-mix(in oklab, …)).
    return when (tone) {
        ShellStatusTone.Good -> if (dark) Tokens.Good else lerp(Tokens.Good, Color.Black, 0.38f)
        ShellStatusTone.Attention -> if (dark) Tokens.Warn else lerp(Tokens.Warn, Color.Black, 0.45f)
        ShellStatusTone.Unknown -> colors.outline
    }
}

@Composable
private fun railInsets(): WindowInsets =
    WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)

/** A rail column under the system bars that scrolls when the window is short, with its foot pinned to the bottom otherwise. */
@Composable
private fun RailColumn(width: Dp, padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxHeight().width(width).windowInsetsPadding(railInsets())) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
private fun <T> ExpandedRail(
    destinations: List<ShellDestination<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onSettings: () -> Unit,
    statusLine: @Composable () -> Unit,
) {
    RailColumn(ShellDefaults.ExpandedRailWidth, PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        RailBrand()
        Spacer(Modifier.height(8.dp))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            destinations.forEach { destination ->
                val isSelected = destination.key == selected
                ExpandedRailItem(
                    label = destination.label,
                    icon = if (isSelected) destination.selectedIcon else destination.icon,
                    selected = isSelected,
                    role = Role.Tab,
                    onClick = { onSelect(destination.key) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(8.dp))
        RailStatus(expanded = true, statusLine)
        ExpandedRailItem(label = "Settings", icon = R.drawable.sym_settings, selected = false, role = Role.Button, quiet = true, onClick = onSettings)
    }
}

@Composable
private fun <T> CollapsedRail(
    destinations: List<ShellDestination<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    statusLine: @Composable () -> Unit,
) {
    RailColumn(ShellDefaults.CollapsedRailWidth, PaddingValues(top = 16.dp, bottom = 12.dp)) {
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) { BrandMark() }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            destinations.forEach { destination ->
                val isSelected = destination.key == selected
                CollapsedRailItem(
                    label = destination.label,
                    icon = if (isSelected) destination.selectedIcon else destination.icon,
                    selected = isSelected,
                    onClick = { onSelect(destination.key) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        RailStatus(expanded = false, statusLine)
    }
}

/** The mark and the "PadStudio" wordmark, as in the reference's top-left corner. */
@Composable
private fun RailBrand() {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 12.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandMark()
        Text("PadStudio", style = wordmarkStyle(), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
    }
}

/**
 * The wordmark is part of the logo lockup: it follows the text size up to 23 dp (its size at 125 %), then stops,
 * so at 200 % it still fits the 216 dp rail on one line (mock-setup.html).
 */
@Composable
private fun wordmarkStyle(): TextStyle {
    val density = LocalDensity.current
    val size = with(density) { if (Tokens.TitleSize.toDp() > 23.dp) 23.dp.toSp() else Tokens.TitleSize }
    val line = with(density) { if (Tokens.TitleLineHeight.toDp() > 32.dp) 32.dp.toSp() else Tokens.TitleLineHeight }
    return TextStyle(fontSize = size, lineHeight = line, fontWeight = Tokens.WeightMedium)
}

/** The launcher icon's monochrome layer in primary, drawn larger than its [MarkSize] box (see [MARK_UNITS]). */
@Composable
private fun BrandMark() {
    Box(Modifier.size(MarkSize), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_monochrome),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.requiredSize(MarkSize * (LAYER_UNITS / MARK_UNITS)),
        )
    }
}

/** An expanded-rail row: icon and label in a 56 dp row; selected is a full-width secondary-container shape. */
@Composable
private fun ExpandedRailItem(
    label: String,
    @DrawableRes icon: Int,
    selected: Boolean,
    role: Role,
    onClick: () -> Unit,
    quiet: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val labelColor = when {
        selected -> colors.onSecondaryContainer
        quiet -> colors.onSurfaceVariant
        else -> colors.onSurface
    }
    val shape = RoundedCornerShape(Tokens.CornerM)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val action = if (role == Role.Tab) {
        Modifier.selectable(selected, interaction, ripple(), role = Role.Tab, onClick = onClick)
    } else {
        Modifier.clickable(interaction, ripple(), role = role, onClick = onClick)
    }
    CompositionLocalProvider(LocalContentColor provides labelColor) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .focusRing(focused, colors.secondary, Tokens.CornerM)
                .clip(shape)
                .background(if (selected) colors.secondaryContainer else Color.Transparent)
                .then(action)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                label,
                style = TextStyle(
                    fontSize = Tokens.BodySize,
                    lineHeight = Tokens.BodyLineHeight,
                    fontWeight = if (quiet) Tokens.WeightRegular else Tokens.WeightMedium,
                ),
            )
        }
    }
}

/** A compact-rail item (kit.css .rail): the icon in a 56 × 32 pill, the label under it. */
@Composable
private fun CollapsedRailItem(label: String, @DrawableRes icon: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(selected, interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 32.dp)
                .focusRing(focused, colors.secondary, 16.dp)
                .clip(CircleShape)
                .background(if (selected) colors.secondaryContainer else Color.Transparent)
                .indication(interaction, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            label,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            style = TextStyle(
                fontSize = Tokens.LabelSize,
                lineHeight = Tokens.LabelLineHeight,
                fontWeight = Tokens.WeightMedium,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** The rail's status slot: secondary text, a row in the expanded rail, a centred column in the compact one. */
@Composable
private fun RailStatus(expanded: Boolean, statusLine: @Composable () -> Unit) {
    val style = if (expanded) {
        TextStyle(fontSize = Tokens.LabelSize, lineHeight = Tokens.LabelLineHeight, fontWeight = Tokens.WeightRegular)
    } else {
        // kit.css .rail__status: the 11 sp label-small role (not in Tokens yet), so "Mapper" fits 88 dp at 200 %.
        TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = Tokens.WeightMedium, textAlign = TextAlign.Center)
    }
    val padding = if (expanded) PaddingValues(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp) else PaddingValues(start = 4.dp, end = 4.dp, top = 12.dp)
    CompositionLocalProvider(
        LocalRailExpanded provides expanded,
        LocalTextStyle provides style,
        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(
            Modifier.fillMaxWidth().padding(padding),
            contentAlignment = if (expanded) Alignment.CenterStart else Alignment.TopCenter,
        ) { statusLine() }
    }
}

/** The hairline between the rail and the content, full height like the reference's. */
@Composable
private fun RailEdge() {
    VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
}

@Composable
private fun <T> BottomBar(destinations: List<ShellDestination<T>>, selected: T, onSelect: (T) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val itemColors = ShortNavigationBarItemDefaults.colors(
        selectedIconColor = colors.onSecondaryContainer,
        selectedTextColor = colors.onSurface,
        selectedIndicatorColor = colors.secondaryContainer,
        unselectedIconColor = colors.onSurfaceVariant,
        unselectedTextColor = colors.onSurfaceVariant,
    )
    ShortNavigationBar {
        destinations.forEach { destination ->
            val isSelected = destination.key == selected
            ShortNavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(destination.key) },
                icon = { Icon(painterResource(if (isSelected) destination.selectedIcon else destination.icon), contentDescription = null) },
                label = { Text(destination.label) },
                colors = itemColors,
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
