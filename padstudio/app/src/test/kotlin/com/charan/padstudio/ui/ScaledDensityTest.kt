package com.charan.padstudio.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScaledDensityTest {
    /** The owner's tablet: 440 dpi, font scale 1.25 (nonlinear), plus the plain and largest settings. */
    private val bases = listOf(Density(2.75f, 1.25f), Density(2.75f, 1f), Density(2.75f, 2f))

    @Test fun `dp shrink to the UI scale`() {
        for (base in bases) {
            val scaled = ScaledDensity(base, UI_SCALE)
            assertEquals(with(base) { 48.dp.toPx() } * UI_SCALE, with(scaled) { 48.dp.toPx() }, 0.001f, "48 dp at ${base.fontScale}")
            assertEquals(with(base) { 24.dp.roundToPx() * UI_SCALE }, with(scaled) { 24.dp.toPx() }, 1f, "24 dp at ${base.fontScale}")
        }
    }

    @Test fun `text lands on the same pixels as without the scale, at every font scale`() {
        for (base in bases) {
            val scaled = ScaledDensity(base, UI_SCALE)
            for (size in listOf(9.sp, 13.sp, 19.sp, 23.sp, 30.sp)) {
                assertEquals(with(base) { size.toPx() }, with(scaled) { size.toPx() }, 0.01f, "$size at ${base.fontScale}")
            }
        }
    }

    @Test fun `sp converts to dp and back, and pixels back to the same sp`() {
        for (base in bases) {
            val scaled = ScaledDensity(base, UI_SCALE)
            for (size in listOf(9f, 13f, 23f)) {
                val dp = with(scaled) { size.sp.toDp() }
                assertEquals(size, with(scaled) { dp.toSp() }.value, 0.01f, "$size sp round trip at ${base.fontScale}")
                val px = with(base) { size.sp.toPx() }
                assertEquals(size, with(scaled) { px.toSp() }.value, 0.01f, "$size sp from pixels at ${base.fontScale}")
            }
        }
    }

    /** The checks above must run through Android's nonlinear curve, or they would pass for a linear one too. */
    @Test fun `the owner's font scale is nonlinear here, so the text check covers it`() {
        val base = bases.first()
        assertTrue(with(base) { 30.sp.toPx() } < 30f * 1.25f * 2.75f - 1f, "30 sp at 1.25 should grow less than 25 %")
    }
}
