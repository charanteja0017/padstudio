package com.charan.padstudio.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.theme.Tokens

/** What sits at the start of a [ListRow], in a 40 dp box. */
@Immutable
sealed interface RowLeading {
    /** A symbol in on-surface-variant, or on a secondary-container circle when [tonal]; filled when the row is selected. */
    data class Symbol(@param:DrawableRes val id: Int, val tonal: Boolean = false) : RowLeading

    /** The app's own icon, in the user's icon style. */
    data class App(val app: InstalledApp) : RowLeading

    /** Anything else, centred in a box of at least 40 dp. */
    class Custom(val content: @Composable () -> Unit) : RowLeading
}

/** What sits at the end of a [ListRow]. */
@Immutable
sealed interface RowTrailing {
    /** Nothing. */
    data object None : RowTrailing

    /** A chevron: the row opens something. */
    data object Chevron : RowTrailing

    /** A symbol, such as [Sym.ok] on the chosen row of a picker or [Sym.open] for "opens another app". */
    data class Symbol(@param:DrawableRes val id: Int) : RowTrailing

    /** A short value in on-surface-variant, such as "6 × 4". */
    data class Value(val text: String) : RowTrailing

    /** A switch. The whole row toggles it (one 48 dp+ target, read as a switch). */
    data class Toggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) : RowTrailing

    /** Anything else; it gets at most half of the width left after the leading box. */
    class Custom(val content: @Composable () -> Unit) : RowTrailing
}

/**
 * A list row as the kit's `.list-row`: [leading] in a 40 dp box, [headline] in the body role, optional
 * [supporting] text under it, and [trailing] at the end. At least 56 dp tall (72 dp with supporting text);
 * it grows with large text instead of clipping.
 *
 * - With [onClick] the row is a button.
 * - With [onClick] and [selected] it is one option of a picker: when chosen, the row is secondary
 *   container and its leading symbol filled. Put the rows of one picker in a `Modifier.selectableGroup()`.
 * - With [RowTrailing.Toggle] the whole row toggles the switch and [onClick] isn't used.
 * - When not [enabled], the headline and leading content fade to 38 % but [supporting] stays readable, so
 *   it can say why ("Unavailable: needs camera permission").
 *
 * Inside a [StudioCard], give the rows `Modifier.cardBleed()` so their text lines up with the card's.
 */
@Composable
fun ListRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: RowLeading? = null,
    trailing: RowTrailing = RowTrailing.None,
    onClick: (() -> Unit)? = null,
    selected: Boolean? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val chosen = selected == true
    val shape = RoundedCornerShape(Tokens.CornerM)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val action = when {
        trailing is RowTrailing.Toggle -> Modifier.toggleable(
            value = trailing.checked,
            interactionSource = interaction,
            indication = ripple(),
            enabled = enabled,
            role = Role.Switch,
            onValueChange = trailing.onCheckedChange,
        )
        onClick != null && selected != null -> Modifier.selectable(
            selected = selected,
            interactionSource = interaction,
            indication = ripple(),
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        )
        onClick != null -> Modifier.clickable(
            interactionSource = interaction,
            indication = ripple(),
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
        else -> Modifier.semantics(mergeDescendants = true) {}
    }
    val ink = if (chosen) scheme.onSecondaryContainer else scheme.onSurface
    val quiet = if (chosen) scheme.onSecondaryContainer else scheme.onSurfaceVariant
    val fade = if (enabled) 1f else DISABLED_ALPHA
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (supporting != null) 72.dp else 56.dp)
            .clip(shape)
            .focusRing(focused, shape, scheme.secondary)
            .then(if (chosen) Modifier.background(scheme.secondaryContainer) else Modifier)
            .then(action)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        content = {
            if (leading != null) {
                Box(Modifier.layoutId(LEADING), contentAlignment = Alignment.Center) {
                    RowLeadingContent(leading, chosen, fade, quiet)
                }
            }
            Column(Modifier.layoutId(TEXT)) {
                Text(text = headline, style = type.bodyLarge, color = ink.copy(alpha = ink.alpha * fade))
                if (supporting != null) Text(text = supporting, style = type.bodyMedium, color = quiet)
            }
            if (trailing !is RowTrailing.None) {
                Box(Modifier.layoutId(TRAILING), contentAlignment = Alignment.CenterEnd) {
                    RowTrailingContent(trailing, enabled, quiet)
                }
            }
        },
    ) { measurables, constraints ->
        val gap = Tokens.GapM.roundToPx()
        val bounded = constraints.hasBoundedWidth
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val lead = measurables.firstOrNull { it.layoutId == LEADING }?.measure(loose)
        val start = lead?.let { it.width + gap } ?: 0
        val room = if (bounded) (constraints.maxWidth - start).coerceAtLeast(0) else Constraints.Infinity
        val trailMax = if (bounded) ((room - gap) / 2).coerceAtLeast(0) else Constraints.Infinity
        val trail = measurables.firstOrNull { it.layoutId == TRAILING }?.measure(loose.copy(maxWidth = trailMax))
        val end = trail?.let { it.width + gap } ?: 0
        val textMeasurable = measurables.first { it.layoutId == TEXT }
        val text = if (bounded) {
            val textWidth = (room - end).coerceAtLeast(0)
            textMeasurable.measure(Constraints(minWidth = textWidth, maxWidth = textWidth, maxHeight = constraints.maxHeight))
        } else {
            textMeasurable.measure(loose)
        }
        val width = if (bounded) constraints.maxWidth else start + text.width + end
        val height = maxOf(constraints.minHeight, lead?.height ?: 0, text.height, trail?.height ?: 0)
        layout(width, height) {
            lead?.placeRelative(0, (height - lead.height) / 2)
            text.placeRelative(start, (height - text.height) / 2)
            trail?.placeRelative(width - trail.width, (height - trail.height) / 2)
        }
    }
}

@Composable
private fun RowLeadingContent(leading: RowLeading, chosen: Boolean, fade: Float, quiet: Color) {
    val scheme = MaterialTheme.colorScheme
    when (leading) {
        is RowLeading.Symbol -> {
            val tonal = leading.tonal
            val box = if (tonal) Modifier.size(40.dp).background(scheme.secondaryContainer, CircleShape) else Modifier.size(40.dp)
            val tint = if (tonal) scheme.onSecondaryContainer else quiet
            Box(box, contentAlignment = Alignment.Center) {
                SymIcon(leading.id, contentDescription = null, filled = chosen, tint = tint.copy(alpha = tint.alpha * fade))
            }
        }
        is RowLeading.App -> AppIcon(
            app = leading.app,
            size = 40.dp,
            contentDescription = null,
            modifier = if (fade < 1f) Modifier.alpha(fade) else Modifier,
        )
        is RowLeading.Custom -> Box(
            Modifier.defaultMinSize(40.dp, 40.dp).then(if (fade < 1f) Modifier.alpha(fade) else Modifier),
            contentAlignment = Alignment.Center,
        ) { leading.content() }
    }
}

@Composable
private fun RowTrailingContent(trailing: RowTrailing, enabled: Boolean, quiet: Color) {
    val type = MaterialTheme.typography
    when (trailing) {
        RowTrailing.None -> Unit
        RowTrailing.Chevron -> SymIcon(Sym.chevron, contentDescription = null, tint = quiet, autoMirror = true)
        is RowTrailing.Symbol -> SymIcon(trailing.id, contentDescription = null, tint = quiet)
        is RowTrailing.Value -> Text(text = trailing.text, style = type.bodyMedium, color = quiet, textAlign = TextAlign.End)
        is RowTrailing.Toggle -> Switch(checked = trailing.checked, onCheckedChange = null, enabled = enabled)
        is RowTrailing.Custom -> trailing.content()
    }
}

/**
 * A collapsible section, closed by default, for codes and raw data that most people never need (plan
 * §10: "Developer details"). The header is a 48 dp+ button with [icon], [title] and a chevron that turns
 * when open; [content] is set in monospace label text, on-surface-variant. It remembers being open across
 * recreation.
 */
@Composable
fun Disclosure(
    modifier: Modifier = Modifier,
    title: String = "Developer details",
    @DrawableRes icon: Int? = Sym.code,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Disclosure(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
        title = title,
        icon = icon,
        content = content,
    )
}

/** [Disclosure] with its open state hoisted to the caller. */
@Composable
fun Disclosure(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Developer details",
    @DrawableRes icon: Int? = Sym.code,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "disclosure chevron")
    val shape = RoundedCornerShape(Tokens.CornerM)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .focusRing(focused, shape, scheme.secondary)
                    .clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        onClickLabel = if (expanded) "Hide" else "Show",
                        role = Role.Button,
                    ) { onExpandedChange(!expanded) }
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Tokens.GapS),
            ) {
                if (icon != null) SymIcon(icon, contentDescription = null, tint = scheme.onSurfaceVariant)
                Text(text = title, style = type.bodyLarge, modifier = Modifier.weight(1f))
                SymIcon(Sym.expandMore, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.rotate(turn))
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                CompositionLocalProvider(
                    LocalTextStyle provides type.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = Tokens.WeightRegular),
                    LocalContentColor provides scheme.onSurfaceVariant,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

private const val LEADING = "leading"
private const val TEXT = "text"
private const val TRAILING = "trailing"
internal const val DISABLED_ALPHA = 0.38f
