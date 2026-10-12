package com.charan.padstudio.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.theme.Tokens

/** The four button looks of the reference and the kit's `.btn`. */
enum class PillStyle {
    /** Primary container colour: the one main action ("Test pen"). */
    Filled,

    /** Secondary container: a strong second action ("Pause pen"). */
    Tonal,

    /** Outline-variant edge, on-surface text: page actions and quieter choices ("Edit controls", "Open library"). */
    Outlined,

    /** Primary text, no container: links such as "Manage ›". */
    Text,
}

/** Shapes for [PillButton]. */
object PillButtonDefaults {
    /** The reference's buttons: rounded rectangles with 12 dp corners (the default). */
    val Shape: Shape = RoundedCornerShape(Tokens.CornerS)

    /** A full pill, as the kit's `.btn`. */
    val FullPill: Shape = RoundedCornerShape(Tokens.CornerFull)
}

/**
 * A Material 3 button in one of the reference's looks ([style]), with an optional leading [icon] and
 * [trailingIcon] (a chevron for "Manage ›"; it mirrors in right-to-left layouts). The visible shape is
 * at least 40 dp tall inside a 48 dp touch target and grows with large text; [shape] is the reference's
 * 12 dp rounded rectangle unless you pass [PillButtonDefaults.FullPill]. Keyboard focus draws the kit's
 * ring 2 dp outside the shape.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Filled,
    @DrawableRes icon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
    enabled: Boolean = true,
    shape: Shape = PillButtonDefaults.Shape,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val side = if (style == PillStyle.Text) 12.dp else 24.dp
    val padding = PaddingValues(
        start = if (icon != null) 16.dp.coerceAtMost(side) else side,
        end = if (trailingIcon != null) 16.dp.coerceAtMost(side) else side,
        top = 10.dp,
        bottom = 10.dp,
    )
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            SymIcon(icon, contentDescription = null, size = ButtonDefaults.IconSize)
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(text = text, textAlign = TextAlign.Center)
        if (trailingIcon != null) {
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            SymIcon(trailingIcon, contentDescription = null, size = ButtonDefaults.IconSize, autoMirror = trailingIcon == Sym.chevron)
        }
    }
    TouchTargetBox(modifier) {
        val ring = Modifier.focusRing(focused, shape, scheme.secondary, gap = FocusRingGap)
        when (style) {
            PillStyle.Filled -> Button(
                onClick = onClick,
                modifier = ring,
                enabled = enabled,
                shape = shape,
                contentPadding = padding,
                interactionSource = interaction,
                content = content,
            )
            PillStyle.Tonal -> FilledTonalButton(
                onClick = onClick,
                modifier = ring,
                enabled = enabled,
                shape = shape,
                contentPadding = padding,
                interactionSource = interaction,
                content = content,
            )
            PillStyle.Outlined -> OutlinedButton(
                onClick = onClick,
                modifier = ring,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface),
                border = BorderStroke(1.dp, if (enabled) scheme.outlineVariant else scheme.onSurface.copy(alpha = DISABLED_EDGE_ALPHA)),
                contentPadding = padding,
                interactionSource = interaction,
                content = content,
            )
            PillStyle.Text -> TextButton(
                onClick = onClick,
                modifier = ring,
                enabled = enabled,
                shape = shape,
                contentPadding = padding,
                interactionSource = interaction,
                content = content,
            )
        }
    }
}

/**
 * An icon-only button, as the kit's `.icon-btn`: a 40 dp circle in a 48 dp target. [contentDescription]
 * is required, because the symbol is all a TalkBack user gets. [style] Text is the plain on-surface-variant
 * icon (Close, More), Tonal and Filled add the container, Outlined an outline-variant ring.
 */
@Composable
fun SymIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Text,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // The 48 dp target is 4 dp wider than the 40 dp circle all round, so a ring inside it sits 1 dp out.
    val ringed = modifier.focusRing(focused, CircleShape, scheme.secondary)
    val glyph: @Composable () -> Unit = { SymIcon(icon, contentDescription = contentDescription) }
    when (style) {
        PillStyle.Filled -> FilledIconButton(onClick = onClick, modifier = ringed, enabled = enabled, interactionSource = interaction, content = glyph)
        PillStyle.Tonal -> FilledTonalIconButton(onClick = onClick, modifier = ringed, enabled = enabled, interactionSource = interaction, content = glyph)
        PillStyle.Outlined -> OutlinedIconButton(
            onClick = onClick,
            modifier = ringed,
            enabled = enabled,
            border = BorderStroke(1.dp, if (enabled) scheme.outlineVariant else scheme.onSurface.copy(alpha = DISABLED_EDGE_ALPHA)),
            interactionSource = interaction,
            content = glyph,
        )
        PillStyle.Text -> IconButton(
            onClick = onClick,
            modifier = ringed,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(contentColor = scheme.onSurfaceVariant),
            interactionSource = interaction,
            content = glyph,
        )
    }
}

/**
 * One choice out of a few, as Material 3's single-choice segmented buttons (outlined segments; the chosen
 * one is secondary container with a check, the kit's `.seg`). [label] names each option; [icon] may give
 * an option a symbol, shown while it isn't chosen. Each segment is a 48 dp+ radio target; add
 * `Modifier.fillMaxWidth()` for equal segments across the width. Labels wrap at large text sizes. A
 * focused segment's edge becomes the 3 dp secondary ring.
 *
 * Material 3 1.4.0 has these APIs stable: no `@OptIn(ExperimentalMaterial3Api::class)` is needed (checked
 * by compiling without it).
 */
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ((T) -> Int?)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = SegmentedButtonDefaults.colors()
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, option ->
            val chosen = option == selected
            val symbol = icon?.invoke(option)
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val edge = when {
                focused -> scheme.secondary
                !enabled && chosen -> colors.disabledActiveBorderColor
                !enabled -> colors.disabledInactiveBorderColor
                chosen -> colors.activeBorderColor
                else -> colors.inactiveBorderColor
            }
            SegmentedButton(
                selected = chosen,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled = enabled,
                colors = colors,
                border = SegmentedButtonDefaults.borderStroke(edge, if (focused) FocusRingWidth else SegmentedButtonDefaults.BorderWidth),
                interactionSource = interaction,
                icon = {
                    if (symbol == null) {
                        SegmentedButtonDefaults.Icon(active = chosen)
                    } else {
                        SegmentedButtonDefaults.Icon(
                            active = chosen,
                            inactiveContent = { SymIcon(symbol, contentDescription = null, size = SegmentedButtonDefaults.IconSize) },
                        )
                    }
                },
                label = { Text(text = label(option), textAlign = TextAlign.Center) },
            )
        }
    }
}

/**
 * What a [SegmentedChoice] needs around its labels, so a screen can decide whether the control fits before
 * showing it (round 2: screens never copy Material's sizes, because the control's look can change here).
 */
object SegmentedChoiceDefaults {
    /** The narrowest a segment gets. */
    val MinSegmentWidth: Dp = ButtonDefaults.MinWidth

    /** Space a segment adds beside its label: padding on both sides, the check and its gap. */
    val SegmentChrome: Dp
        get() = SegmentedButtonDefaults.ContentPadding.let {
            it.calculateLeftPadding(LayoutDirection.Ltr) + it.calculateRightPadding(LayoutDirection.Ltr)
        } + SegmentedButtonDefaults.IconSize + ButtonDefaults.IconSpacing

    /** Width [count] equal segments need when the widest label measures [widestLabel] on one line. */
    fun requiredWidth(widestLabel: Dp, count: Int): Dp = maxOf(MinSegmentWidth, widestLabel + SegmentChrome) * count
}

/**
 * A filter chip (the kit's `.chip--filter`): an 8 dp-cornered 32 dp chip in a 48 dp target, outline
 * variant edge; when [selected] it turns secondary container with a check. Use it for on/off filters
 * such as the Library's group chips. [icon] shows while it isn't selected.
 */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Tokens.CornerXs)
    TouchTargetBox(modifier) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(text = label) },
            modifier = Modifier.focusRing(focused, shape, scheme.secondary, gap = FocusRingGap),
            enabled = enabled,
            leadingIcon = when {
                selected -> {
                    { SymIcon(Sym.ok, contentDescription = null, filled = true, size = FilterChipDefaults.IconSize) }
                }
                icon != null -> {
                    { SymIcon(icon, contentDescription = null, size = FilterChipDefaults.IconSize) }
                }
                else -> null
            },
            shape = shape,
            border = FilterChipDefaults.filterChipBorder(enabled = enabled, selected = selected, borderColor = scheme.outlineVariant),
            interactionSource = interaction,
        )
    }
}

/**
 * An assist chip (the kit's `.chip--assist`): an 8 dp-cornered 32 dp chip in a 48 dp target with a
 * primary [icon] and on-surface [label], for a quick related action ("Don't show text", "Open in
 * HyperOS").
 */
@Composable
fun ActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Tokens.CornerXs)
    TouchTargetBox(modifier) {
        AssistChip(
            onClick = onClick,
            label = { Text(text = label) },
            modifier = Modifier.focusRing(focused, shape, scheme.secondary, gap = FocusRingGap),
            enabled = enabled,
            leadingIcon = if (icon != null) {
                { SymIcon(icon, contentDescription = null, size = AssistChipDefaults.IconSize) }
            } else {
                null
            },
            shape = shape,
            border = AssistChipDefaults.assistChipBorder(enabled = enabled, borderColor = scheme.outlineVariant),
            interactionSource = interaction,
        )
    }
}

/**
 * What one pen control does in one app, as the kit's `.binding`: the action's [icon] in primary and its
 * name ([action]), with the [control] ("Squeeze") above it in small text, on surface container high,
 * 12 dp corners, 48 dp+ tall. With no [action] it is the dashed "Add action" chip ([addLabel]).
 * Use [com.charan.padstudio.ui.Sym.forIntent] for the icon of a PenIntent.
 */
@Composable
fun BindingChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    action: String? = null,
    @DrawableRes icon: Int? = null,
    control: String? = null,
    enabled: Boolean = true,
    addLabel: String = "Add action",
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val adding = action == null
    val shape = BindingChipDefaults.Shape
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val surface = if (adding) Modifier.dashedOutline(scheme.outline, BindingChipDefaults.Corner) else Modifier.background(scheme.surfaceContainerHigh)
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = Tokens.TouchTarget, minHeight = Tokens.TouchTarget)
            .focusRing(focused, shape, scheme.secondary, gap = FocusRingGap)
            .clip(shape)
            .then(surface)
            .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SymIcon(
            id = icon ?: Sym.add,
            contentDescription = null,
            size = 20.dp,
            tint = if (adding) scheme.onSurfaceVariant else scheme.primary,
        )
        Column {
            if (control != null) Text(text = control, style = type.bodySmall, color = scheme.onSurfaceVariant)
            Text(text = action ?: addLabel, style = type.labelLarge, color = if (adding) scheme.onSurfaceVariant else scheme.onSurface)
        }
    }
}

/** One colour in a [SwatchRow]: its [color], the [contentColor] of the check drawn on it, and a spoken [label]. */
@Immutable
data class Swatch(val label: String, val color: Color, val contentColor: Color)

/**
 * A row of colour swatches to pick one from (Looks; the reference's "Make it yours"): 36 dp dots in 48 dp
 * radio targets; the chosen one gets an on-surface ring and a check. Pass each palette's own colours,
 * e.g. `Swatch("Ocean", scheme.primary, scheme.onPrimary)` from `Tokens.darkScheme(BrandPalette.OCEAN)`.
 * When not [enabled] (wallpaper colours on) the row fades to 38 %.
 */
@Composable
fun SwatchRow(
    swatches: List<Swatch>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp),
) {
    val ring = MaterialTheme.colorScheme.onSurface
    val focusColor = MaterialTheme.colorScheme.secondary
    Row(
        modifier = modifier
            .selectableGroup()
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        swatches.forEachIndexed { index, swatch ->
            val chosen = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            Box(
                Modifier
                    .size(Tokens.TouchTarget)
                    .focusRing(focused, CircleShape, focusColor, gap = 1.dp)
                    .clip(CircleShape)
                    .selectable(
                        selected = chosen,
                        interactionSource = interaction,
                        indication = ripple(),
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(index) },
                    )
                    .semantics { contentDescription = swatch.label }
                    .drawBehind {
                        val dot = SWATCH_DOT.toPx() / 2f
                        drawCircle(color = swatch.color, radius = dot)
                        if (chosen) {
                            val ringWidth = 2.dp.toPx()
                            drawCircle(color = ring, radius = dot + 3.dp.toPx() + ringWidth / 2f, style = Stroke(width = ringWidth))
                            // The kit's check: M4.5 12.5 l5 5 L19.5 7 in a 24-unit box, drawn 20 dp wide.
                            val unit = 20.dp.toPx() / 24f
                            val left = center.x - 10.dp.toPx()
                            val top = center.y - 10.dp.toPx()
                            val check = Path().apply {
                                moveTo(left + 4.5f * unit, top + 12.5f * unit)
                                lineTo(left + 9.5f * unit, top + 17.5f * unit)
                                lineTo(left + 19.5f * unit, top + 7f * unit)
                            }
                            drawPath(check, color = swatch.contentColor, style = Stroke(width = 2.4f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    },
            )
        }
    }
}

/** A dashed outline drawn inside the bounds, for the "Add action" chip. */
private fun Modifier.dashedOutline(color: Color, corner: Dp): Modifier = this.drawBehind {
    val stroke = 1.5.dp.toPx()
    val half = stroke / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(half, half),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(corner.toPx() - half),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

private val SWATCH_DOT = 36.dp
private const val DISABLED_EDGE_ALPHA = 0.12f

/** A [BindingChip]'s corners, for screens that draw beside or around it (a selected edge, a leader line). */
object BindingChipDefaults {
    val Corner: Dp = Tokens.CornerS
    val Shape: Shape = RoundedCornerShape(Corner)
}
