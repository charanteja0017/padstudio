package com.charan.padstudio.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.charan.padstudio.ui.PadStudioTheme
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.theme.Tokens

/** How a status reads (plan §7). Each level has its own symbol, so a state never depends on colour alone. */
enum class StatusLevel {
    /** Done or working as far as the app can tell: "Key Mapper is listening". */
    Good,

    /** Needs a look: "Changes not sent yet". */
    Warn,

    /** Broken or off: "Key Mapper is off". */
    Bad,

    /** The app can't know: "Can't be checked from the tablet". Never shown as good. */
    Unknown,
}

/** The symbol each level shows: check circle, warning, error, help. */
val StatusLevel.symbol: Int
    @DrawableRes get() = when (this) {
        StatusLevel.Good -> Sym.ok
        StatusLevel.Warn -> Sym.warning
        StatusLevel.Bad -> Sym.error
        StatusLevel.Unknown -> Sym.help
    }

/**
 * The colour of a level's symbol (a status dot, a pill's icon): the good and warn inks, the scheme's
 * error colour for bad, on-surface-variant for unknown. Text next to it stays on-surface.
 *
 * Good and warn: Material 3 has no such roles, so they come from the theme's `PadStudioTheme.semantic`
 * (ui/Theme.kt): the base colours in dark themes, darker inks in light ones that keep 3 : 1 on every
 * surface container (make_tokens.mjs checks).
 */
@Composable
@ReadOnlyComposable
fun statusInk(level: StatusLevel): Color {
    val semantic = PadStudioTheme.semantic
    return when (level) {
        StatusLevel.Good -> semantic.good
        StatusLevel.Warn -> semantic.warn
        StatusLevel.Bad -> semantic.bad
        StatusLevel.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * A status as a pill: the level's symbol in its colour, then [text] in on-surface, on a container lightly
 * tinted with the level (the kit's `.status-pill`). Not a button; TalkBack reads it as one phrase. [icon]
 * replaces the level's symbol when a more specific one exists (send, time).
 */
@Composable
fun StatusPill(
    text: String,
    level: StatusLevel,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = level.symbol,
) {
    val scheme = MaterialTheme.colorScheme
    val tone = when (level) {
        StatusLevel.Good -> Tokens.Good
        StatusLevel.Warn -> Tokens.Warn
        StatusLevel.Bad -> scheme.error
        StatusLevel.Unknown -> scheme.outline
    }
    val container = tone.copy(alpha = PILL_TINT).compositeOver(scheme.surfaceContainerHigh)
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .clip(RoundedCornerShape(Tokens.CornerFull))
            .background(container)
            .heightIn(min = 32.dp)
            .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SymIcon(icon, contentDescription = null, size = 18.dp, tint = statusInk(level))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
    }
}

/**
 * A progress ring with a big number in the middle (WiFiBar-style readout), as the kit's `.ring`: a
 * hairline outline-variant track and a [strokeWidth] arc in primary from 12 o'clock, clockwise, for
 * [progress] 0..1. [value] is the centred number ("7"); [contentDescription] is what TalkBack says for
 * the whole ring ("7 of 8 ready"), defaulting to [value]. The arc animates when [progress] changes.
 */
@Composable
fun ProgressRing(
    progress: Float,
    value: String,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    strokeWidth: Dp = 6.dp,
    contentDescription: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val target = progress.coerceIn(0f, 1f)
    val shown by animateFloatAsState(target, label = "progress ring")
    val track = scheme.outlineVariant
    val arc = scheme.primary
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription ?: value
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
            }
            .drawBehind {
                val stroke = strokeWidth.toPx()
                val radius = (this.size.minDimension - stroke) / 2f
                drawCircle(color = track, radius = radius, style = Stroke(width = 1.dp.toPx()))
                if (shown > 0f) {
                    drawArc(
                        color = arc,
                        startAngle = -90f,
                        sweepAngle = 360f * shown,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
            color = scheme.onSurface,
        )
    }
}

/**
 * A designed empty state (plan §6b: never a blank): an optional [icon] on a tonal circle, [title], an
 * optional [body] and an optional tonal action button ([actionLabel] + [onAction]), centred. For example
 * "No apps in Games right now" · Edit.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.GapM, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Box(Modifier.size(56.dp).background(scheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                SymIcon(icon, contentDescription = null, size = 28.dp, tint = scheme.onSecondaryContainer)
            }
        }
        Text(text = title, style = type.titleMedium, color = scheme.onSurface, textAlign = TextAlign.Center)
        if (body != null) Text(text = body, style = type.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            PillButton(text = actionLabel, onClick = onAction, style = PillStyle.Tonal, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private const val PILL_TINT = 0.16f

