package com.charan.padstudio.looks.screen

import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.charan.padstudio.looks.WallpaperGenerator
import com.charan.padstudio.looks.WallpaperTarget
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.Disclosure
import com.charan.padstudio.ui.components.ListRow
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.RowTrailing
import com.charan.padstudio.ui.components.SectionHeader
import com.charan.padstudio.ui.components.SegmentedChoice
import com.charan.padstudio.ui.components.StatusLevel
import com.charan.padstudio.ui.components.StatusPill
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.cardBleed
import com.charan.padstudio.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How the last wallpaper change from this screen ended, when it needs saying in the card. */
internal enum class ApplyOutcome {
    /** Nothing to say: none yet, or it worked (that is a snackbar). */
    None,

    /** It failed: a plain reason and Retry. */
    Failed,

    /** No answer within the wait: "Requested". */
    Requested,
}

/**
 * Looks → the wallpaper (plan §10, mock-looks.html "Colours and wallpaper"): a preview of what Apply sets, drawn
 * by WallpaperGenerator from [seed], [dark] and [accent] exactly as MainViewModel.applyWallpaper will draw it;
 * Shuffle ([onShuffle]) for another, Light or Dark ([onDarkChange]), Apply to Home, Lock or Both ([target],
 * [onTargetChange]), and Apply ([onApply]).
 *
 * Apply's states (plan §13): [applying] shows "Applying…" with a spinner; [ApplyOutcome.Failed] a plain reason
 * from [failureDetail] and Retry; [ApplyOutcome.Requested] when no answer came. Success is the caller's snackbar.
 * When the system doesn't let apps set the wallpaper ([allowed] false), Apply is disabled and the card says so.
 * [palette] names the brand colours; null while wallpaper colours are matched. The card may be given a minimum
 * height to line up with the column beside it: the Apply row then sits at its bottom.
 */
@Composable
internal fun WallpaperCard(
    seed: Long,
    dark: Boolean,
    accent: Int,
    target: WallpaperTarget,
    palette: String?,
    allowed: Boolean,
    applying: Boolean,
    outcome: ApplyOutcome,
    failureDetail: String?,
    wallpaperApplied: Boolean,
    onShuffle: () -> Unit,
    onDarkChange: (Boolean) -> Unit,
    onTargetChange: (WallpaperTarget) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val name = wallpaperName(palette, dark)
    // The card's own spacing is 0 so the stretch Spacer below adds no gap of its own: when the card isn't
    // stretched, the Apply area sits the usual 12 dp under the choices, not 24.
    StudioCard(modifier, spacing = 0.dp) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.GapS)) {
            SectionHeader(
                title = "Wallpaper",
                supporting = if (palette != null) "A calm gradient in $palette." else "A calm gradient in your wallpaper's colours.",
            )
            WallpaperPreview(seed = seed, dark = dark, accent = accent, name = name, onShuffle = onShuffle)
            Text(name, style = type.titleMedium, color = scheme.onSurface)
            SegmentedChoice(
                options = listOf(false, true),
                selected = dark,
                onSelect = onDarkChange,
                label = { if (it) "Dark" else "Light" },
                icon = { if (it) Sym.dark else Sym.light },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Apply to", style = type.bodyMedium, color = scheme.onSurfaceVariant)
                TargetChoice(target = target, onSelect = onTargetChange)
            }
        }
        // Takes the room left when the card is stretched beside the icon cards; nothing otherwise.
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Tokens.GapS),
            verticalArrangement = Arrangement.spacedBy(Tokens.GapS),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (allowed) {
                        "Sets your ${targetScreens(target)}. Apps can't copy your current wallpaper, so note it first."
                    } else {
                        "This tablet doesn't let apps change the wallpaper."
                    },
                    style = type.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                // Each state is a new live region: Compose reports a new node's first layout with the node itself
                // as the source, so TalkBack says it when it appears.
                if (outcome != ApplyOutcome.None && !applying) {
                    key(outcome) {
                        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (outcome == ApplyOutcome.Failed) {
                                StatusPill("Couldn't set the wallpaper", StatusLevel.Bad)
                                Text(plainWallpaperFailure(failureDetail), style = type.bodyMedium, color = scheme.onSurface)
                            } else {
                                StatusPill("Requested", StatusLevel.Unknown)
                                val look = if (target == WallpaperTarget.LOCK) "lock screen" else "home screen"
                                Text("No answer from the tablet yet. Look at your $look.", style = type.bodyMedium, color = scheme.onSurface)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (applying) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Applying…", style = type.bodyMedium, color = scheme.onSurface)
                        }
                    }
                    val retry = outcome == ApplyOutcome.Failed && !applying
                    PillButton(
                        text = if (retry) "Retry" else "Apply",
                        onClick = onApply,
                        icon = if (retry) Sym.refresh else null,
                        enabled = allowed && !applying,
                    )
                }
            }
            Disclosure {
                val context = LocalContext.current
                val longSide = remember(context) { runCatching { WallpaperGenerator.longSide(context) }.getOrNull() }
                Text("Seed $seed · ${if (dark) "dark" else "light"} · accent #${hexRgb(accent)} · target ${target.name}")
                Text(
                    "Apply draws it ${longSide?.let { "$it px" } ?: "at the screen's long side"} square and sets " +
                        "${targetFlags(target)}.",
                )
                Text("Saved: wallpaperApplied $wallpaperApplied")
                if (failureDetail != null) Text("Last error: $failureDetail")
            }
        }
    }
}

/** "421EBE" for an ARGB colour: its RGB part, six hex digits. */
private fun hexRgb(argb: Int): String = Integer.toHexString(argb and 0xFFFFFF).uppercase().padStart(6, '0')

/** What Apply asks WallpaperManager for, for Developer details. */
private fun targetFlags(target: WallpaperTarget): String = when (target) {
    WallpaperTarget.HOME -> "home (WallpaperManager FLAG_SYSTEM)"
    WallpaperTarget.LOCK -> "lock (WallpaperManager FLAG_LOCK)"
    WallpaperTarget.BOTH -> "home and lock (WallpaperManager FLAG_SYSTEM | FLAG_LOCK)"
}

/**
 * Apply to: Home, Lock or Both (plan §10), as the mock-up's segmented buttons. When the labels can't each stay on
 * one line in a third of the card (large text in the narrow side pane), the same choice is a list of radio rows
 * instead, so no label breaks inside a word. The fit is measured with the text as drawn now (font scale and Bold
 * text included) and Material 3's own segment padding and check, for the widest label in the chosen segment.
 */
@Composable
private fun TargetChoice(target: WallpaperTarget, onSelect: (WallpaperTarget) -> Unit) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    val widest = remember(measurer, style, density) {
        with(density) {
            TARGET_ORDER.maxOf { measurer.measure(targetLabel(it), style, maxLines = 1, softWrap = false).size.width }.toDp()
        }
    }
    val padding = SegmentedButtonDefaults.ContentPadding.let {
        it.calculateLeftPadding(LayoutDirection.Ltr) + it.calculateRightPadding(LayoutDirection.Ltr)
    }
    val check = SegmentedButtonDefaults.IconSize + ButtonDefaults.IconSpacing
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (segmentsFit(widest, maxWidth, TARGET_ORDER.size, padding, check)) {
            SegmentedChoice(
                options = TARGET_ORDER,
                selected = target,
                onSelect = onSelect,
                label = ::targetLabel,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                TARGET_ORDER.forEach { option ->
                    val chosen = option == target
                    ListRow(
                        headline = targetLabel(option),
                        trailing = if (chosen) RowTrailing.Symbol(Sym.ok) else RowTrailing.None,
                        selected = chosen,
                        onClick = { onSelect(option) },
                        modifier = Modifier.cardBleed(),
                    )
                }
            }
        }
    }
}

/**
 * The wallpaper on a tablet-shaped frame (the screen's own proportions, landscape), cropped as the home screen
 * crops the square WallpaperGenerator draws, with Shuffle on its top right as in the mock-up. It is drawn off
 * the main thread at about the preview's own pixel width; the previous picture stays until the next is ready.
 */
@Composable
private fun WallpaperPreview(seed: Long, dark: Boolean, accent: Int, name: String, onShuffle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var widthPx by remember { mutableIntStateOf(0) }
    val renderPx = previewRenderSize(widthPx)
    val bitmap by produceState<ImageBitmap?>(null, seed, dark, accent, renderPx) {
        if (renderPx > 0) {
            value = withContext(Dispatchers.Default) { WallpaperGenerator.render(renderPx, seed, accent, dark).asImageBitmap() }
        }
    }
    val ratio = remember(context) {
        runCatching {
            val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            maxOf(bounds.width(), bounds.height()).toFloat() / minOf(bounds.width(), bounds.height())
        }.getOrNull()?.takeIf { it.isFinite() && it >= 1f } ?: DEFAULT_SCREEN_RATIO
    }
    val frame = RoundedCornerShape(18.dp)
    val picture = RoundedCornerShape(13.dp)
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .onSizeChanged { widthPx = it.width }
                .clip(frame)
                .background(lerp(scheme.scrim, scheme.onSurface, 0.1f))
                .border(1.dp, scheme.outlineVariant, frame)
                .padding(5.dp),
        ) {
            val shown = bitmap
            if (shown != null) {
                Image(
                    bitmap = shown,
                    contentDescription = "Preview of $name",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(picture),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(picture)
                        .background(scheme.surfaceContainerHighest)
                        .semantics { contentDescription = "Preview of $name, drawing" },
                )
            }
        }
        PillButton(
            text = "Shuffle",
            onClick = onShuffle,
            style = PillStyle.Tonal,
            icon = Sym.refresh,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 9.dp),
        )
    }
}

/** The tablet's proportions when the window can't be measured: 3:2, like the mock-up's 1164 × 777. */
private const val DEFAULT_SCREEN_RATIO = 1.5f
