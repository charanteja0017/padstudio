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
 * - **Text:** [StudioTypography], the token type scale in the system font (MiSans on HyperOS).
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
    CompositionLocalProvider(LocalSemanticColors provides semantic) {
        MaterialTheme(colorScheme = colorScheme, typography = StudioTypography, shapes = StudioShapes, content = content)
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
 * Material 3's 15 text styles, built from the token type scale.
 * - **Font:** [FontFamily.Default], the system font (MiSans on HyperOS).
 * - **Weights:** 400 and 500 only, never above 600, because the tablet's Bold text adds 300.
 * - **Letter spacing:** 0, as in the preview.
 * - **Line height and font padding:** kept from Material.
 *
 * Each style uses a size the way the preview does (docs/redesign/kit.css):
 *
 * | Style | Token | sp | Weight | Preview use |
 * |---|---|---|---|---|
 * | display Large, Medium, Small | display | 36 / 44 | 400 | big readouts |
 * | headline Large, Medium, Small | headline | 28 / 36 | 400 | page and dialog titles |
 * | titleLarge | title | 20 / 28 | 500 | section labels, sheet titles |
 * | titleMedium | body | 16 / 24 | 500 | emphasised body |
 * | titleSmall | label | 13 / 18 | 500 | small headings |
 * | bodyLarge | body | 16 / 24 | 400 | default text, list headlines |
 * | bodyMedium | label | 13 / 18 | 400 | list supporting text, snackbars |
 * | bodySmall | labelSmall | 11 / 16 | 400 | captions, meta text |
 * | labelLarge, labelMedium | label | 13 / 18 | 500 | buttons, chips, navigation |
 * | labelSmall | labelSmall | 11 / 16 | 500 | badges, widget labels |
 *
 * Material's AlertDialog sets its text in bodyMedium, but the preview's dialog body is 16 / 24, so pass
 * bodyLarge to a dialog's text.
 */
internal val StudioTypography: Typography = Typography().let { m3 ->
    Typography(
        displayLarge = m3.displayLarge.token(Tokens.DisplaySize, Tokens.DisplayLineHeight, Tokens.WeightRegular),
        displayMedium = m3.displayMedium.token(Tokens.DisplaySize, Tokens.DisplayLineHeight, Tokens.WeightRegular),
        displaySmall = m3.displaySmall.token(Tokens.DisplaySize, Tokens.DisplayLineHeight, Tokens.WeightRegular),
        headlineLarge = m3.headlineLarge.token(Tokens.HeadlineSize, Tokens.HeadlineLineHeight, Tokens.WeightRegular),
        headlineMedium = m3.headlineMedium.token(Tokens.HeadlineSize, Tokens.HeadlineLineHeight, Tokens.WeightRegular),
        headlineSmall = m3.headlineSmall.token(Tokens.HeadlineSize, Tokens.HeadlineLineHeight, Tokens.WeightRegular),
        titleLarge = m3.titleLarge.token(Tokens.TitleSize, Tokens.TitleLineHeight, Tokens.WeightMedium),
        titleMedium = m3.titleMedium.token(Tokens.BodySize, Tokens.BodyLineHeight, Tokens.WeightMedium),
        titleSmall = m3.titleSmall.token(Tokens.LabelSize, Tokens.LabelLineHeight, Tokens.WeightMedium),
        bodyLarge = m3.bodyLarge.token(Tokens.BodySize, Tokens.BodyLineHeight, Tokens.WeightRegular),
        bodyMedium = m3.bodyMedium.token(Tokens.LabelSize, Tokens.LabelLineHeight, Tokens.WeightRegular),
        bodySmall = m3.bodySmall.token(Tokens.LabelSmallSize, Tokens.LabelSmallLineHeight, Tokens.WeightRegular),
        labelLarge = m3.labelLarge.token(Tokens.LabelSize, Tokens.LabelLineHeight, Tokens.WeightMedium),
        labelMedium = m3.labelMedium.token(Tokens.LabelSize, Tokens.LabelLineHeight, Tokens.WeightMedium),
        labelSmall = m3.labelSmall.token(Tokens.LabelSmallSize, Tokens.LabelSmallLineHeight, Tokens.WeightMedium),
    )
}

private fun TextStyle.token(size: TextUnit, lineHeight: TextUnit, weight: FontWeight) =
    copy(fontFamily = FontFamily.Default, fontWeight = weight, fontSize = size, lineHeight = lineHeight, letterSpacing = 0.sp)

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
