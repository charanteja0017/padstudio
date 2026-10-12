package com.charan.padstudio.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.charan.padstudio.PadStudioApp
import com.charan.padstudio.ui.theme.Tokens

/**
 * PadStudio's look (plan §3.1, §3.2). Light or dark follows the system.
 * - **Colours:** the brand schemes from [Tokens] for [Tokens.DefaultPalette]. While Looks → "Match wallpaper
 *   colours" ([com.charan.padstudio.data.StudioConfig.matchWallpaperColours]) is on, Material You colours from
 *   the wallpaper instead (minSdk 31 always has them).
 * - **Text:** [studioTypography] in PadStudio's own typeface, [StudioFont.DEFAULT] (Plus Jakarta Sans), not the system font.
 * - **Corners:** [StudioShapes], the token radii.
 * - **Status colours:** [PadStudioTheme.semantic].
 */
@Composable
fun PadStudioTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    // A layout preview runs outside PadStudio's process and has no settings to read: brand colours.
    val store = (context.applicationContext as? PadStudioApp)?.config
    val matchWallpaper = store?.config?.collectAsState()?.value?.matchWallpaperColours == true
    val colorScheme = when {
        matchWallpaper && dark -> dynamicDarkColorScheme(context)
        matchWallpaper -> dynamicLightColorScheme(context)
        dark -> Tokens.darkScheme(Tokens.DefaultPalette)
        else -> Tokens.lightScheme(Tokens.DefaultPalette)
    }
    val semantic = remember(dark, colorScheme.error) {
        SemanticColors(good = Tokens.goodInk(dark), warn = Tokens.warnInk(dark), bad = colorScheme.error)
    }
    val font = remember(context) { com.charan.padstudio.debug.DebugOverrides.font(context) ?: StudioFont.DEFAULT }
    val typography = remember(font) { studioTypography(studioFontFamily(font)) }
    CompositionLocalProvider(LocalSemanticColors provides semantic) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = StudioShapes, content = content)
    }
}

/** What [PadStudioTheme] adds to [MaterialTheme]; read it the same way, as `PadStudioTheme.semantic.good`. */
object PadStudioTheme {
    /** Status colours for icons and short labels in the current theme. */
    val semantic: SemanticColors
        @Composable @ReadOnlyComposable
        get() = LocalSemanticColors.current
}

/**
 * Colours for status icons and short status labels, chosen for the current theme. [good] and [warn] are
 * [Tokens.goodInk] and [Tokens.warnInk]. On every brand scheme they reach at least 3:1 on the background,
 * the surface and the five surface containers (make_tokens.mjs checks). [bad] is the scheme's error colour.
 * For tints and fills, use the base colours [Tokens.Good] and [Tokens.Warn] at a low alpha, or
 * `colorScheme.errorContainer`.
 */
@Immutable
data class SemanticColors(val good: Color, val warn: Color, val bad: Color)

private val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColors(Tokens.GoodInkLight, Tokens.WarnInkLight, Tokens.lightScheme(Tokens.DefaultPalette).error)
}

/**
 * PadStudio's own typefaces (plan round 2, §3.1: "it should have its own font"), bundled in res/font as static
 * instances cut from google/fonts' variable files at pinned commits (OFL 1.1; texts in assets/licenses). The
 * variable files rendered at their default instance on the tablet (Manrope ExtraLight, Outfit Thin) because the
 * `wght` variation was ignored, so each weight is its own file. The owner asked for one to be picked (12 Oct):
 * Plus Jakarta Sans, the most legible of the three at small sizes and a little narrower than Manrope or Outfit.
 */
enum class StudioFont(val label: String, private val files: Map<Int, Int>) {
    JAKARTA(
        "Plus Jakarta Sans",
        mapOf(
            400 to com.charan.padstudio.R.font.jakarta_w400, 500 to com.charan.padstudio.R.font.jakarta_w500,
            600 to com.charan.padstudio.R.font.jakarta_w600, 700 to com.charan.padstudio.R.font.jakarta_w700,
            800 to com.charan.padstudio.R.font.jakarta_w800,
        ),
    ),
    ;

    /** The heaviest bundled instance; Bold text (+300) on a 500 or 600 request is clamped to it. */
    val maxWeight: Int get() = files.keys.max()

    /** The file for [weight]: its own instance, or the heaviest one above [maxWeight]. */
    fun resFor(weight: Int): Int = files[weight] ?: files.getValue(maxWeight)

    companion object {
        val DEFAULT = JAKARTA
    }
}

/** Every weight Compose may ask for: the theme's 400-600, plus up to 300 more from the system's Bold text. */
internal val STUDIO_FONT_WEIGHTS = (400..900 step 100).toList()

/**
 * [font] as a Compose family with one static file per weight in [STUDIO_FONT_WEIGHTS]. Bold text needs the
 * 700-900 entries, or a 400 + 300 request falls back to the nearest lighter one.
 */
internal fun studioFontFamily(font: StudioFont): FontFamily = FontFamily(
    STUDIO_FONT_WEIGHTS.map { w -> Font(resId = font.resFor(w), weight = FontWeight(w)) },
)

/**
 * Material 3's 15 text styles from the token type scale (round 2: smaller and looser than round 1's).
 * - **Font:** [family], PadStudio's bundled typeface.
 * - **Weights:** 400 and 500 only, never above 600, because the tablet's Bold text adds 300.
 * - **Letter spacing:** 0. Line heights come from the tokens.
 *
 * | Style | Token | sp | Weight |
 * |---|---|---|---|
 * | display Large, Medium, Small | display | 30 / 38 | 400 (page titles set 500) |
 * | headline Large, Medium, Small | headline | 24 / 32 | 400 |
 * | titleLarge | title | 18 / 26 | 500 |
 * | titleMedium | titleMedium | 15 / 22 | 500 |
 * | titleSmall | bodyMedium | 13 / 20 | 500 |
 * | bodyLarge | body | 15 / 22 | 400 |
 * | bodyMedium | bodyMedium | 13 / 20 | 400 |
 * | bodySmall | labelSmall | 11 / 16 | 400 |
 * | labelLarge | bodyMedium | 13 / 20 | 500 (buttons) |
 * | labelMedium | label | 12 / 16 | 500 |
 * | labelSmall | labelSmall | 11 / 16 | 500 |
 */
internal fun studioTypography(family: FontFamily): Typography = Typography().let { m3 ->
    fun TextStyle.token(size: TextUnit, lineHeight: TextUnit, weight: FontWeight) =
        copy(fontFamily = family, fontWeight = weight, fontSize = size, lineHeight = lineHeight, letterSpacing = 0.sp)
    Typography(
        displayLarge = m3.displayLarge.token(Tokens.DisplaySize, Tokens.DisplayLineHeight, Tokens.WeightRegular),
        displayMedium = m3.displayMedium.token(Tokens.DisplaySize, Tokens.DisplayLineHeight, Tokens.WeightRegular),
        displaySmall = m3.displaySmall.token(Tokens.DisplaySize, Tokens.DisplayLineHeight, Tokens.WeightRegular),
        headlineLarge = m3.headlineLarge.token(Tokens.HeadlineSize, Tokens.HeadlineLineHeight, Tokens.WeightRegular),
        headlineMedium = m3.headlineMedium.token(Tokens.HeadlineSize, Tokens.HeadlineLineHeight, Tokens.WeightRegular),
        headlineSmall = m3.headlineSmall.token(Tokens.HeadlineSize, Tokens.HeadlineLineHeight, Tokens.WeightRegular),
        titleLarge = m3.titleLarge.token(Tokens.TitleSize, Tokens.TitleLineHeight, Tokens.WeightMedium),
        titleMedium = m3.titleMedium.token(Tokens.TitleMediumSize, Tokens.TitleMediumLineHeight, Tokens.WeightMedium),
        titleSmall = m3.titleSmall.token(Tokens.BodyMediumSize, Tokens.BodyMediumLineHeight, Tokens.WeightMedium),
        bodyLarge = m3.bodyLarge.token(Tokens.BodySize, Tokens.BodyLineHeight, Tokens.WeightRegular),
        bodyMedium = m3.bodyMedium.token(Tokens.BodyMediumSize, Tokens.BodyMediumLineHeight, Tokens.WeightRegular),
        bodySmall = m3.bodySmall.token(Tokens.LabelSmallSize, Tokens.LabelSmallLineHeight, Tokens.WeightRegular),
        labelLarge = m3.labelLarge.token(Tokens.BodyMediumSize, Tokens.BodyMediumLineHeight, Tokens.WeightMedium),
        labelMedium = m3.labelMedium.token(Tokens.LabelSize, Tokens.LabelLineHeight, Tokens.WeightMedium),
        labelSmall = m3.labelSmall.token(Tokens.LabelSmallSize, Tokens.LabelSmallLineHeight, Tokens.WeightMedium),
    )
}

/** The app's typography in its default typeface; tests and previews use it. */
internal val StudioTypography: Typography = studioTypography(studioFontFamily(StudioFont.DEFAULT))

/**
 * Material 3's corner sizes from the token radii, slot for slot: extraSmall = xs 8, small = s 12,
 * medium = m 16, large = l 24 and extraLarge = xl 28 dp. With Material's defaults, that gives snackbars and
 * menus 8 dp, chips 12 dp, cards 16 dp, and dialogs and bottom sheets 28 dp. The preview's cards are l
 * (24 dp) and its chips xs (8 dp), so pass `shapes.large` or `shapes.extraSmall` where they should match it.
 */
internal val StudioShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(Tokens.CornerXs),
    small = RoundedCornerShape(Tokens.CornerS),
    medium = RoundedCornerShape(Tokens.CornerM),
    large = RoundedCornerShape(Tokens.CornerL),
    extraLarge = RoundedCornerShape(Tokens.CornerXl),
)

/**
 * Background and glyph colours for themed icons, taken from the same wallpaper palette Android
 * uses for its own themed icons: a light accent container with a dark glyph, inverted at night.
 */
data class ThemedIconColors(val background: Int, val glyph: Int)

fun themedIconColors(context: Context): ThemedIconColors {
    val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    // Dark plates sit on PadStudio's dark widget card, not the wallpaper, so they are a step lighter than
    // Android's own dark themed icons (accent1_800 measured as 48,48,48: invisible on the card).
    return if (night) {
        ThemedIconColors(context.getColor(android.R.color.system_accent1_600), context.getColor(android.R.color.system_accent1_50))
    } else {
        ThemedIconColors(context.getColor(android.R.color.system_accent1_100), context.getColor(android.R.color.system_accent1_700))
    }
}
