package com.charan.padstudio.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charan.padstudio.data.IconStyle
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.studio
import com.charan.padstudio.ui.themedIconColors
import com.charan.padstudio.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An icon style to draw with instead of the saved one: Looks previews every style side by side. */
@Immutable
data class IconAppearance(val style: IconStyle, val pack: String?)

/**
 * An installed app's icon at [size], drawn by PadStudio's IconRepository in the user's icon style (themed,
 * icon pack or original, from the saved config) or in [appearance] when given. It is rendered off the main
 * thread at the exact pixel size and kept in a small in-memory cache, so lists and stacks scroll without
 * redrawing.
 *
 * While it loads, a neutral tile holds the place; if the icon can't be loaded, the app's initial on a
 * tertiary tile in the HyperOS shape stands in (plan §6b: never a blank). [contentDescription] defaults to
 * the app's label; pass null where the row already says it.
 */
@Composable
fun AppIcon(
    app: InstalledApp,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    contentDescription: String? = app.facts.label,
    appearance: IconAppearance? = null,
) {
    if (LocalInspectionMode.current) {
        InitialTile(app.facts.label, size, contentDescription, modifier)
        return
    }
    val context = LocalContext.current
    val config by context.studio.config.config.collectAsStateWithLifecycle()
    val style = appearance?.style ?: config.iconStyle
    val pack = if (appearance != null) appearance.pack else config.iconPack
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val uiConfiguration = LocalConfiguration.current
    val colors = remember(uiConfiguration) { themedIconColors(context) }
    val key = remember(app, style, pack, px, colors) {
        listOf(
            app.component.flattenToShortString(),
            app.info.applicationInfo.sourceDir,
            style.name,
            pack.orEmpty(),
            colors.background,
            colors.glyph,
            px,
        ).joinToString("|")
    }
    // A cached icon shows in the first frame; otherwise the previous icon (or the placeholder) stays until the new one is drawn.
    val icon by produceState<IconLoad>(IconBitmaps.get(key)?.let { IconLoad.Ready(it) } ?: IconLoad.Loading, key) {
        val cached = IconBitmaps.get(key)
        value = if (cached != null) {
            IconLoad.Ready(cached)
        } else {
            withContext(Dispatchers.Default) { load(context, app, style, pack, colors.background, colors.glyph, px, key) }
        }
    }
    when (val loaded = icon) {
        is IconLoad.Ready -> Image(bitmap = loaded.bitmap, contentDescription = contentDescription, modifier = modifier.size(size))
        IconLoad.Missing -> InitialTile(app.facts.label, size, contentDescription, modifier)
        IconLoad.Loading -> Box(
            modifier
                .size(size)
                .clip(SuperellipseShape())
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        )
    }
}

/**
 * A few app icons overlapping by 30 %, each in a 2 dp ring of [ringColor] (the colour of what it sits on;
 * the card's surface container low by default), then a "+N" tile for the rest (the kit's `.app-stack`).
 * At most [max] icons show. TalkBack reads it as one image: [contentDescription], or "A, B, C and N more".
 */
@Composable
fun AppIconStack(
    apps: List<InstalledApp>,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    max: Int = 3,
    ringColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentDescription: String? = null,
    appearance: IconAppearance? = null,
) {
    val shown = apps.take(max.coerceAtLeast(0))
    val more = apps.size - shown.size
    val description = contentDescription ?: describe(shown.map { it.facts.label }, more)
    val tile = SuperellipseShape()
    val ring = 2.dp
    Row(
        modifier = modifier.clearAndSetSemantics { this.contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(-size * STACK_OVERLAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEach { app ->
            Box(Modifier.size(size).background(ringColor, tile).padding(ring), contentAlignment = Alignment.Center) {
                AppIcon(app = app, size = size - ring * 2, contentDescription = null, appearance = appearance)
            }
        }
        if (more > 0) {
            val label = "+$more"
            // Sized with the tile, not the text setting (as InitialTile is): "+12" must fit a 28 dp tile at 200 % text.
            val inner = size - ring * 2
            val glyph = with(LocalDensity.current) { minOf(inner * (0.8f / (0.6f * label.length)), inner * 0.5f).toSp() }
            Box(Modifier.size(size).background(ringColor, tile).padding(ring)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(tile)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum", fontSize = glyph, lineHeight = glyph),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A missing icon: the label's first letter on a tertiary tile in the HyperOS shape, sized with the tile, not the text setting. */
@Composable
private fun InitialTile(label: String, size: Dp, contentDescription: String?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val trimmed = label.trim()
    val initial = if (trimmed.isEmpty()) "?" else String(Character.toChars(trimmed.codePointAt(0))).uppercase()
    val glyph = with(LocalDensity.current) { (size * INITIAL_SCALE).toSp() }
    Box(
        modifier = modifier
            .size(size)
            .clip(SuperellipseShape())
            .background(scheme.tertiaryContainer)
            .clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = glyph, lineHeight = glyph, fontWeight = Tokens.WeightMedium),
            color = scheme.onTertiaryContainer,
            maxLines = 1,
        )
    }
}

private sealed interface IconLoad {
    data object Loading : IconLoad
    data object Missing : IconLoad
    data class Ready(val bitmap: ImageBitmap) : IconLoad
}

private fun load(
    context: Context,
    app: InstalledApp,
    style: IconStyle,
    pack: String?,
    background: Int,
    glyph: Int,
    px: Int,
    key: String,
): IconLoad = runCatching {
    IconBitmaps.get(key) ?: context.studio.icons.drawable(app, style, pack, background, glyph)
        .toBitmap(px, px)
        .asImageBitmap()
        .also { IconBitmaps.put(key, it) }
}.fold(onSuccess = { IconLoad.Ready(it) }, onFailure = { IconLoad.Missing })

private fun describe(labels: List<String>, more: Int): String = when {
    labels.isEmpty() -> if (more > 0) "$more apps" else ""
    more > 0 -> labels.joinToString(", ") + " and $more more"
    labels.size == 1 -> labels.single()
    else -> labels.dropLast(1).joinToString(", ") + " and " + labels.last()
}

/** Rendered icons by app, style, colours and pixel size; about 1/16 of the heap, at most 24 MB. */
private object IconBitmaps {
    private val cache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 16).coerceAtMost(24L * 1024 * 1024).toInt(),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }
}

private const val STACK_OVERLAP = 0.3f
private const val INITIAL_SCALE = 0.42f
