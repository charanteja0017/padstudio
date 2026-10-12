package com.charan.padstudio.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiScaledDensityTest {
    private val dpi440 = 2.75f
    private fun ratio(fontScale: Float, sp: Float): Float {
        val base = Density(dpi440, fontScale)
        val scaled = UiScaledDensity(base, UI_SCALE)
        // Compose's layout code sees only density and fontScale, so convert the way it does: from those two numbers.
        val asLayoutSeesIt = Density(scaled.density, scaled.fontScale)
        return with(asLayoutSeesIt) { sp.sp.toPx() } / with(base) { sp.sp.toPx() }
    }

    @Test fun `dp shrink to the UI scale`() {
        for (fontScale in listOf(1f, 1.25f, 2f)) {
            val base = Density(dpi440, fontScale)
            val scaled = UiScaledDensity(base, UI_SCALE)
            assertEquals(with(base) { 48.dp.toPx() } * UI_SCALE, with(scaled) { 48.dp.toPx() }, 0.001f, "48 dp at $fontScale")
        }
    }

    /** The owner's tablet (1.25) and the default (1): body text and smaller keep their size, headings shrink a little. */
    @Test fun `at font scale 1 and 1_25 text keeps its size and headings shrink at most 8 %`() {
        for (fontScale in listOf(1f, 1.25f)) {
            for (sp in listOf(9f, 10f, 11f, 13f, 15f)) {
                assertEquals(1f, ratio(fontScale, sp), 0.011f, "$sp sp at $fontScale")
            }
            for (sp in listOf(19f, 23f)) {
                val r = ratio(fontScale, sp)
                assertTrue(r in 0.92f..1.0f, "$sp sp at $fontScale: $r")
            }
        }
    }

    /** Above 2.0 Android scales text linearly, so an uncapped font scale would make text larger than before. */
    @Test fun `text never grows and never shrinks more than the UI, at any font scale`() {
        for (fontScale in listOf(1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)) {
            for (sp in listOf(9f, 11f, 13f, 15f, 19f, 23f, 30f)) {
                val r = ratio(fontScale, sp)
                assertTrue(r in (UI_SCALE - 0.005f)..1.011f, "$sp sp at $fontScale: $r")
            }
        }
    }

    /** The checks above must run through the nonlinear curve, or they would pass for a linear one too. */
    @Test fun `the owner's font scale is nonlinear here, so the checks cover it`() {
        val base = Density(dpi440, 1.25f)
        assertTrue(with(base) { 30.sp.toPx() } < 30f * 1.25f * dpi440 - 1f, "30 sp at 1.25 should grow less than 25 %")
    }
}
