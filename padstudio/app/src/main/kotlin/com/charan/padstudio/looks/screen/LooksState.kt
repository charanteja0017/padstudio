package com.charan.padstudio.looks.screen

import android.os.Build
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.charan.padstudio.core.home.AppFacts
import com.charan.padstudio.core.home.PlannedGroup
import com.charan.padstudio.data.IconStyle
import com.charan.padstudio.icons.IconPack
import com.charan.padstudio.looks.WallpaperTarget
import com.charan.padstudio.ui.theme.BrandPalette

// The Looks screen's words and decisions, kept free of composition and Android state so unit tests can pin them
// (LooksStateTest). The screen itself is in ../LooksScreen.kt; its cards are next to this file.

/** How many of the user's own apps each icon-style preview draws (plan §10 Looks). */
internal const val PREVIEW_APP_COUNT = 12

/** The style previews in the mock-up's order: the apps' own icons first. Every IconStyle appears once. */
internal val STYLE_ORDER: List<IconStyle> = listOf(IconStyle.ORIGINAL, IconStyle.THEMED, IconStyle.ICON_PACK)

/**
 * Up to [count] of the user's apps for the icon-style previews, taken one per group in turn (Study, Work, …,
 * More; Hidden is never planned), so the previews show a mix instead of one group's look. Each package appears
 * once, and [exclude] (PadStudio itself) never. The order is stable for the same groups.
 */
internal fun previewApps(
    groups: List<PlannedGroup>,
    exclude: Set<String> = emptySet(),
    count: Int = PREVIEW_APP_COUNT,
): List<AppFacts> {
    val picked = ArrayList<AppFacts>(count)
    val seen = HashSet<String>()
    val depth = groups.maxOfOrNull { it.apps.size } ?: 0
    for (index in 0 until depth) {
        for (group in groups) {
            if (picked.size >= count) return picked
            val app = group.apps.getOrNull(index) ?: continue
            if (app.packageName in exclude || !seen.add(app.packageName)) continue
            picked += app
        }
    }
    return picked
}

/** "Violet" for [BrandPalette.VIOLET]: the palette's name as the screen says it. */
internal fun paletteLabel(palette: BrandPalette): String = palette.name.lowercase().replaceFirstChar { it.uppercase() }

/**
 * Wallpaper colours (Material You) need Android 12, API 31. PadStudio's minSdk is 31 today, so this only
 * matters if that is ever lowered; [sdkInt] is a parameter so tests can check both sides.
 */
internal fun dynamicColourSupported(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.S

/**
 * The supporting text under "Match wallpaper colours": the state in words (never the switch alone), the brand
 * palette, and why it is the default (plan §3.1: the tablet's wallpaper palette turned the whole app grey).
 */
internal fun matchColoursText(on: Boolean, supported: Boolean, palette: String): String = when {
    !supported -> "Needs Android 12 or later. PadStudio keeps its own $palette colours."
    // "Can": the grey came from one wallpaper (plan §3.1), and Looks itself can change the wallpaper.
    on -> "On: PadStudio and its widgets use your wallpaper's colours. Off is the default because they can turn the whole app grey."
    else -> "Off: PadStudio uses its own $palette colours. That's the default because wallpaper colours can turn the whole app grey."
}

/** Where an icon in a style preview comes from, read from IconRepository.source(). */
internal enum class IconSource {
    /** The chosen icon pack has a drawable for the app. */
    PACK,

    /** Themed: the app's monochrome layer (Android 13+) or its Lawnicons glyph, on one colour. */
    ONE_COLOUR,

    /** The app's own icon: the style has nothing for it, so IconRepository falls back. */
    ORIGINAL,
}

/**
 * Reads IconRepository.source(): "pack", "themed/monochrome", "themed/lawnicons", or one of the original branches
 * ("original/adaptive", "original/legacy-plate", "themed→original/…"). LooksStateTest checks these strings
 * against IconRepository.kt.
 */
internal fun iconSourceOf(source: String): IconSource = when {
    source == "pack" -> IconSource.PACK
    source.startsWith("themed/") -> IconSource.ONE_COLOUR
    else -> IconSource.ORIGINAL
}

/** The icon pack setting as the Looks screen explains it. */
internal sealed interface PackChoice {
    /** No installed app answers the ADW or Nova icon-pack intents. */
    data object NoneInstalled : PackChoice

    /** Packs are installed, but none has been picked yet: the Icon pack style shows the original icons. */
    data object NotChosen : PackChoice

    /** No installed pack has the saved name (uninstalled or disabled); IconRepository falls back to the original icons. */
    data class Missing(val packageName: String) : PackChoice

    /** The saved pack. [covered] of [total] preview apps have an icon in it; null while that is counted. */
    data class Chosen(val label: String, val packageName: String, val covered: Int?, val total: Int) : PackChoice
}

/** The pack setting from the installed [packs] (null while they are looked up) and the [saved] pack name. */
internal fun packChoice(packs: List<IconPack>?, saved: String?, covered: Int?, total: Int): PackChoice? {
    if (packs == null) return null
    val pack = saved?.let { name -> packs.firstOrNull { it.packageName == name } }
    return when {
        pack != null -> PackChoice.Chosen(pack.label, pack.packageName, covered, total)
        saved != null -> PackChoice.Missing(saved)
        packs.isEmpty() -> PackChoice.NoneInstalled
        else -> PackChoice.NotChosen
    }
}

/** The caption under a style's name in its preview, e.g. "Lawnicons has 11 of these 12". */
internal fun styleCaption(style: IconStyle, pack: PackChoice?): String = when (style) {
    IconStyle.ORIGINAL -> "As each app draws them"
    IconStyle.THEMED -> "One colour, from your wallpaper"
    IconStyle.ICON_PACK -> when (pack) {
        null -> "Looking for icon packs…"
        PackChoice.NoneInstalled -> "No icon pack installed"
        PackChoice.NotChosen -> "No pack chosen yet"
        is PackChoice.Missing -> "Your pack isn't installed"
        is PackChoice.Chosen -> coverageText(pack.label, pack.covered, pack.total)
    }
}

private fun coverageText(label: String, covered: Int?, total: Int): String = when {
    covered == null || total == 0 -> label
    covered >= total -> "$label has all $total"
    covered == 0 -> "$label has none of these $total"
    else -> "$label has $covered of these $total"
}

/** What the icon-pack row under the previews says, and whether it opens the pack picker. */
internal data class PackRowText(val headline: String, val supporting: String, val opensPicker: Boolean)

internal fun packRowText(pack: PackChoice?): PackRowText = when (pack) {
    null -> PackRowText("Icon pack", "Looking for installed packs…", opensPicker = false)
    PackChoice.NoneInstalled -> PackRowText("No icon pack installed", "Install Lawnicons or Arcticons, then come back here.", opensPicker = false)
    PackChoice.NotChosen -> PackRowText("Choose an icon pack", "New packs show up when you come back", opensPicker = true)
    is PackChoice.Missing -> PackRowText("Icon pack: not installed", "${pack.packageName} isn't installed · choose another", opensPicker = true)
    is PackChoice.Chosen -> PackRowText("Icon pack: ${pack.label}", "New packs show up when you come back", opensPicker = true)
}

/** "Has 11 of these 12 apps" for a pack in the picker; null while it is counted. */
internal fun pickerCoverageText(covered: Int?, total: Int): String? = when {
    covered == null || total == 0 -> null
    covered >= total -> "Has all $total of these apps"
    covered == 0 -> "Has none of these $total apps"
    else -> "Has $covered of these $total apps"
}

/**
 * The square size, in px, to draw the wallpaper preview at for a preview [widthPx] wide: rounded up to a multiple
 * of 128 so small layout changes don't redraw it, between 256 and 1280; 0 until the preview has been measured.
 */
internal fun previewRenderSize(widthPx: Int): Int = if (widthPx <= 0) 0 else ((widthPx + 127) / 128 * 128).coerceIn(256, 1280)

/** The generated wallpaper's name, as the reference's "Violet dusk": dark is dusk, light is dawn. */
internal fun wallpaperName(palette: String?, dark: Boolean): String {
    val time = if (dark) "dusk" else "dawn"
    return if (palette == null) "${time.replaceFirstChar { it.uppercase() }}, in your wallpaper's colours" else "$palette $time"
}

/** How MainViewModel.applyWallpaper ended, read back from its message (it reports nothing else). */
internal sealed interface WallpaperResult {
    /** One of [WALLPAPER_SET_MESSAGES]: the wallpaper was set on [target]. */
    data class Set(val target: WallpaperTarget) : WallpaperResult

    /** "Couldn't set the wallpaper: …", with the exception's message as [detail] (null when it had none). */
    data class Failed(val detail: String?) : WallpaperResult
}

/**
 * MainViewModel.applyWallpaper's success messages, one per target (the three-argument applyWallpaper is
 * [WallpaperTarget.BOTH]). LooksStateTest checks each against MainViewModel.kt.
 */
internal val WALLPAPER_SET_MESSAGES: Map<String, WallpaperTarget> = mapOf(
    "Wallpaper set on home and lock screen." to WallpaperTarget.BOTH,
    "Wallpaper set on the home screen." to WallpaperTarget.HOME,
    "Wallpaper set on the lock screen." to WallpaperTarget.LOCK,
)

/** The start of MainViewModel.applyWallpaper's failure message; the exception's message follows. */
internal const val WALLPAPER_FAILED_PREFIX = "Couldn't set the wallpaper:"

/** The wallpaper result in a MainViewModel message, or null for any other message. */
internal fun wallpaperResultOf(message: String?): WallpaperResult? = when {
    message == null -> null
    message in WALLPAPER_SET_MESSAGES -> WallpaperResult.Set(WALLPAPER_SET_MESSAGES.getValue(message))
    message.startsWith(WALLPAPER_FAILED_PREFIX) -> WallpaperResult.Failed(
        message.removePrefix(WALLPAPER_FAILED_PREFIX).trim().takeUnless { it.isEmpty() || it == "null" },
    )
    else -> null
}

/** Looks → Apply to: Home, Lock or Both, in the mock-up's order. */
internal val TARGET_ORDER: List<WallpaperTarget> = listOf(WallpaperTarget.HOME, WallpaperTarget.LOCK, WallpaperTarget.BOTH)

/** A target as the Apply to choice names it. */
internal fun targetLabel(target: WallpaperTarget): String = when (target) {
    WallpaperTarget.HOME -> "Home"
    WallpaperTarget.LOCK -> "Lock"
    WallpaperTarget.BOTH -> "Both"
}

/** The screens a target sets, for a sentence: "Sets your home and lock screen." */
internal fun targetScreens(target: WallpaperTarget): String = when (target) {
    WallpaperTarget.HOME -> "home screen"
    WallpaperTarget.LOCK -> "lock screen"
    WallpaperTarget.BOTH -> "home and lock screen"
}

/**
 * Whether [count] equal segments, [width] wide in all, each fit their widest label ([widestLabel]) on one line
 * with the chosen segment's check ([check]) and the segment's side padding ([padding]). Segmented buttons share
 * their 1 dp edges, so the row is 1 dp wider per joint than the segments laid end to end.
 */
internal fun segmentsFit(widestLabel: Dp, width: Dp, count: Int, padding: Dp, check: Dp): Boolean {
    if (count <= 0) return true
    val segment = (width + 1.dp * (count - 1)) / count
    return segment >= widestLabel + padding + check
}

/**
 * A plain reason for a failed wallpaper change, from the exception message MainViewModel passes on. The raw
 * message goes under Developer details; this is what everyone else reads next to Retry. MainViewModel passes
 * Throwable.message, which never names the exception class, so the words are matched, not the class: ART's
 * OutOfMemoryError says "Failed to allocate a … byte allocation with … until OOM".
 */
internal fun plainWallpaperFailure(detail: String?): String {
    val text = detail.orEmpty().lowercase()
    return when {
        listOf("permission", "denied", "not allowed", "securityexception").any { it in text } ->
            "PadStudio isn't allowed to change the wallpaper on this tablet."
        listOf("enospc", "no space").any { it in text } -> "The tablet is out of storage space."
        listOf("outofmemory", "out of memory", "failed to allocate", "until oom").any { it in text } ->
            "The tablet ran out of memory making the picture."
        else -> "The tablet didn't accept the new wallpaper."
    }
}

/** How long an Apply may still wait for its answer: until [deadline], from [now] (both SystemClock.elapsedRealtime). */
internal fun applyWaitLeft(deadline: Long, now: Long): Long = (deadline - now).coerceAtLeast(0L)

/**
 * Saves a state only while the activity is being recreated for a configuration change ([changingConfigurations]:
 * rotation, split screen, or the theme change a new wallpaper can bring). When Looks is left for another tab, or
 * the process ends, the state starts again from its initial value instead.
 *
 * Looks uses it for an Apply still waiting for its answer, and for the success snackbar not yet shown: the answer
 * comes from MainViewModel, which outlives a recreation but not the process, and once Looks is off screen the
 * shell's snackbar shows MainViewModel's message itself. Saved across a tab change, the card would say
 * "Applying…" for an answer it can no longer receive.
 */
internal fun <T : Any> keptAcrossConfigChange(changingConfigurations: () -> Boolean): Saver<MutableState<T?>, T> = Saver(
    save = { state -> state.value?.takeIf { changingConfigurations() } },
    restore = { saved -> mutableStateOf(saved) },
)

/**
 * The success snackbar: MainViewModel's own words for [target] with the time instead of the full stop, e.g.
 * "Wallpaper set on the home screen · 14:05".
 */
internal fun wallpaperSetText(target: WallpaperTarget, time: String): String =
    WALLPAPER_SET_MESSAGES.entries.first { it.value == target }.key.removeSuffix(".") + " · $time"

/** "Flipkart, Meesho and WiFiBar" for Developer details; "none" when [labels] is empty. */
internal fun labelList(labels: List<String>): String = when (labels.size) {
    0 -> "none"
    1 -> labels.single()
    else -> labels.dropLast(1).joinToString(", ") + " and " + labels.last()
}
