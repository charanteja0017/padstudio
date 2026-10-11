package com.charan.padstudio.pen

import kotlin.test.Test
import kotlin.test.assertEquals

class PressureLevelsTest {
    @Test fun `an exact step gives the raw range`() {
        assertEquals(16_384, pressureLevels(1f / 16_383))
        assertEquals(8_192, pressureLevels(1f / 8_191))
    }

    @Test fun `float noise snaps to the power of two`() {
        // What the Pad 8 showed: 1/step landed one level high.
        assertEquals(16_384, pressureLevels(1f / 16_384.3f))
    }

    @Test fun `a range that is no power of two is reported as measured`() {
        assertEquals(1_001, pressureLevels(1f / 1_000))
    }
}
