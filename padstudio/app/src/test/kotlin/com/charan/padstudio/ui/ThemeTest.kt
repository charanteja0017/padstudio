package com.charan.padstudio.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.unit.sp
import com.charan.padstudio.ui.theme.BrandPalette
import com.charan.padstudio.ui.theme.Tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeTest {
    private val styles: Map<String, TextStyle> = with(StudioTypography) {
        mapOf(
            "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
            "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
            "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
            "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
            "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
        )
    }

    /** Token size → its line height (body and titleMedium share 15 / 22). */
    private val tokenSizes = mapOf(
        Tokens.DisplaySize to Tokens.DisplayLineHeight,
        Tokens.HeadlineSize to Tokens.HeadlineLineHeight,
        Tokens.TitleSize to Tokens.TitleLineHeight,
        Tokens.TitleMediumSize to Tokens.TitleMediumLineHeight,
        Tokens.BodySize to Tokens.BodyLineHeight,
        Tokens.BodyMediumSize to Tokens.BodyMediumLineHeight,
        Tokens.LabelSize to Tokens.LabelLineHeight,
        Tokens.LabelSmallSize to Tokens.LabelSmallLineHeight,
    )

    @Test fun `every text style is a token size and line height in PadStudio's own typeface`() {
        val family = studioFontFamily(StudioFont.DEFAULT)
        for ((name, style) in styles) {
            assertTrue(style.fontSize in tokenSizes, "$name is ${style.fontSize}, not a token size")
            assertEquals(tokenSizes[style.fontSize], style.lineHeight, "$name line height")
            assertEquals(family, style.fontFamily, "$name font")
            assertTrue(style.fontFamily != FontFamily.Default, "$name must not fall back to the system font")
            assertEquals(0.sp, style.letterSpacing, "$name letter spacing")
        }
    }

    /** Without an explicit wght setting a variable font renders at its default instance (Manrope's is 200). */
    @Test fun `every font entry pins the wght axis to its own weight, clamped to the font's range`() {
        for (font in StudioFont.entries) {
            val family = studioFontFamily(font) as FontListFontFamily
            assertEquals(STUDIO_FONT_WEIGHTS, family.fonts.map { it.weight.weight }, "$font weights")
            for (entry in family.fonts) {
                val settings = (entry as ResourceFont).variationSettings.settings
                val wght = settings.single { it.axisName == "wght" }.toVariationValue(null)
                assertEquals(minOf(entry.weight.weight, font.maxWeight).toFloat(), wght, "$font ${entry.weight}")
                assertEquals(font.res, entry.resId, "$font resource")
            }
        }
    }

    @Test fun `no weight goes above 600, because Bold text adds 300`() {
        val tokenWeights = setOf(Tokens.WeightRegular, Tokens.WeightMedium, Tokens.WeightSemibold)
        for ((name, style) in styles) {
            val weight = style.fontWeight
            assertTrue(weight != null && weight in tokenWeights && weight.weight <= 600, "$name weight $weight")
        }
    }

    @Test fun `styles follow the preview's roles`() {
        with(StudioTypography) {
            assertEquals(Tokens.BodySize to Tokens.WeightRegular, bodyLarge.fontSize to bodyLarge.fontWeight)
            assertEquals(Tokens.BodyMediumSize to Tokens.WeightRegular, bodyMedium.fontSize to bodyMedium.fontWeight)
            assertEquals(Tokens.TitleMediumSize to Tokens.WeightMedium, titleMedium.fontSize to titleMedium.fontWeight)
            assertEquals(Tokens.TitleSize to Tokens.WeightMedium, titleLarge.fontSize to titleLarge.fontWeight)
            assertEquals(Tokens.BodyMediumSize to Tokens.WeightMedium, labelLarge.fontSize to labelLarge.fontWeight)
            assertEquals(Tokens.LabelSize to Tokens.WeightMedium, labelMedium.fontSize to labelMedium.fontWeight)
            assertEquals(Tokens.LabelSmallSize to Tokens.WeightMedium, labelSmall.fontSize to labelSmall.fontWeight)
            assertEquals(Tokens.HeadlineSize to Tokens.WeightRegular, headlineSmall.fontSize to headlineSmall.fontWeight)
        }
    }

    @Test fun `shapes take the token radii slot for slot`() {
        assertEquals(RoundedCornerShape(Tokens.CornerXs), StudioShapes.extraSmall)
        assertEquals(RoundedCornerShape(Tokens.CornerS), StudioShapes.small)
        assertEquals(RoundedCornerShape(Tokens.CornerM), StudioShapes.medium)
        assertEquals(RoundedCornerShape(Tokens.CornerL), StudioShapes.large)
        assertEquals(RoundedCornerShape(Tokens.CornerXl), StudioShapes.extraLarge)
    }

    @Test fun `dark themes keep the base status colours`() {
        assertEquals(Tokens.Good, Tokens.goodInk(dark = true))
        assertEquals(Tokens.Warn, Tokens.warnInk(dark = true))
        assertEquals(Tokens.GoodInkLight, Tokens.goodInk(dark = false))
        assertEquals(Tokens.WarnInkLight, Tokens.warnInk(dark = false))
    }

    /** Re-checks make_tokens.mjs's ink gate on the shipped Kotlin values, with Compose's own luminance. */
    @Test fun `status inks reach 3 to 1 on every background and surface container of every brand scheme`() {
        val failures = mutableListOf<String>()
        for (palette in BrandPalette.entries) for (dark in listOf(false, true)) {
            val s = if (dark) Tokens.darkScheme(palette) else Tokens.lightScheme(palette)
            val grounds = mapOf(
                "background" to s.background, "surface" to s.surface, "surfaceContainerLowest" to s.surfaceContainerLowest,
                "surfaceContainerLow" to s.surfaceContainerLow, "surfaceContainer" to s.surfaceContainer,
                "surfaceContainerHigh" to s.surfaceContainerHigh, "surfaceContainerHighest" to s.surfaceContainerHighest,
            )
            val inks = mapOf("good" to Tokens.goodInk(dark), "warn" to Tokens.warnInk(dark), "bad" to s.error)
            for ((ink, fg) in inks) for ((ground, bg) in grounds) {
                val ratio = contrast(fg, bg)
                if (ratio < 3.0) failures += "$palette ${if (dark) "dark" else "light"}: $ink on $ground = $ratio"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test fun `the contrast helper matches WCAG's reference values`() {
        assertEquals(21.0, contrast(Color.Black, Color.White), 1e-3)
        assertEquals(4.54, contrast(Color(0xFF767676), Color.White), 5e-3)
        assertTrue(contrast(Color(0xFF34C759), Color(0xFFF5F5F7)) < 3.0) // the base good colour on light, measured 2.03
    }

    private fun contrast(a: Color, b: Color): Double {
        val (x, y) = a.luminance().toDouble() to b.luminance().toDouble()
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
}
