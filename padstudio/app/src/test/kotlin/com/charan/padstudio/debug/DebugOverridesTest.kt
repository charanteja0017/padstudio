package com.charan.padstudio.debug

import com.charan.padstudio.ui.StudioFont
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DebugOverridesTest {
    @Test fun `theme takes dark, light or system in any case`() {
        assertEquals(OverrideChange.SetTo(true), parseTheme("dark"))
        assertEquals(OverrideChange.SetTo(false), parseTheme("LIGHT"))
        assertEquals(OverrideChange.Reset, parseTheme("system"))
        assertEquals(OverrideChange.Reset, parseTheme("reset"))
        assertIs<OverrideChange.Invalid>(parseTheme("blue"))
    }

    @Test fun `fontscale takes a number from 0_5 to 3, or reset`() {
        assertEquals(OverrideChange.SetTo(1.25f), parseFontScale("1.25"))
        assertEquals(OverrideChange.SetTo(2f), parseFontScale("2"))
        assertEquals(OverrideChange.SetTo(0.5f), parseFontScale("0.5"))
        assertEquals(OverrideChange.SetTo(3f), parseFontScale("3"))
        assertEquals(OverrideChange.Reset, parseFontScale("RESET"))
        listOf("0", "0.49", "3.01", "-1", "NaN", "Infinity", "big", "").forEach { value ->
            assertIs<OverrideChange.Invalid>(parseFontScale(value), "fontscale \"$value\"")
        }
    }

    @Test fun `font takes jakarta by short or full name or reset, and the dropped candidates are invalid`() {
        assertEquals(OverrideChange.SetTo(StudioFont.JAKARTA), parseFont("Jakarta"))
        assertEquals(OverrideChange.SetTo(StudioFont.JAKARTA), parseFont("plus jakarta sans"))
        assertEquals(OverrideChange.Reset, parseFont("reset"))
        assertEquals(OverrideChange.Reset, parseFont("default"))
        listOf("", "manrope", "outfit", "comic").forEach { assertIs<OverrideChange.Invalid>(parseFont(it), "font \"$it\"") }
    }

    @Test fun `boldtext on adds 300, off forces 0, reset follows the system`() {
        assertEquals(OverrideChange.SetTo(300), parseBoldText("on"))
        assertEquals(OverrideChange.SetTo(0), parseBoldText("Off"))
        assertEquals(OverrideChange.Reset, parseBoldText("reset"))
        assertIs<OverrideChange.Invalid>(parseBoldText("bold"))
    }

    @Test fun `extras change only the fields they name`() {
        val start = Appearance(night = true, fontScale = 1.25f, fontWeightAdjustment = 300)
        assertEquals(start, start.withExtras({ null }))
        assertEquals(start.copy(fontScale = 2f), start.withExtras(mapOf("fontscale" to "2")::get))
        assertEquals(
            Appearance(night = false, fontScale = null, fontWeightAdjustment = 0),
            start.withExtras(mapOf("theme" to "light", "fontscale" to "reset", "boldtext" to "off")::get),
        )
    }

    @Test fun `reset clears an override rather than keeping it`() {
        val start = Appearance(night = true, fontScale = 2f, fontWeightAdjustment = 300)
        assertEquals(Appearance(), start.withExtras(mapOf("theme" to "system", "fontscale" to "reset", "boldtext" to "reset")::get))
    }

    @Test fun `an invalid value keeps the old one and is reported`() {
        val reported = mutableListOf<String>()
        val start = Appearance(fontScale = 1.25f)
        assertEquals(start, start.withExtras(mapOf("fontscale" to "huge")::get) { reported += it })
        assertEquals(1, reported.size)
    }
}
