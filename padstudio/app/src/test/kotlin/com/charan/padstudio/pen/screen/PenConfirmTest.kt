package com.charan.padstudio.pen.screen

import com.charan.padstudio.core.pen.PenKind
import com.charan.padstudio.core.pen.PenMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PenConfirmTest {
    @Test fun `confirmations survive being saved as text`() {
        val all = listOf(
            PenConfirm.Kind(PenKind.TWO_BUTTON),
            PenConfirm.Kind(PenKind.FOCUS_PEN_PRO),
            PenConfirm.Mode(PenMode.BASIC),
            PenConfirm.Mode(PenMode.EXPERT),
            PenConfirm.Reset,
            PenConfirm.Forget("slide_up"),
        )
        all.forEach { assertEquals(it, PenConfirm.decode(PenConfirm.encode(it))) }
    }

    @Test fun `unknown saved text is no confirmation`() {
        assertNull(PenConfirm.decode(null))
        assertNull(PenConfirm.decode("kind:QUILL"))
        assertNull(PenConfirm.decode("forget:"))
        assertNull(PenConfirm.decode("launch"))
    }

    @Test fun `a switch asks first only when something would be cleared`() {
        assertFalse(switchLoses(recordedControls = 0, ownActions = false))
        assertTrue(switchLoses(recordedControls = 2, ownActions = false))
        assertTrue(switchLoses(recordedControls = 0, ownActions = true))
    }

    @Test fun `the words say what is cleared`() {
        val both = confirmWords(PenConfirm.Mode(PenMode.BASIC), recordedControls = 2, ownActions = true)
        assertEquals("Switch to Basic setup?", both.title)
        assertEquals("Switching clears the 2 recorded buttons and your own action choices. Pen actions start again from the Basic setup defaults.", both.body)
        val one = confirmWords(PenConfirm.Kind(PenKind.FOCUS_PEN_PRO), recordedControls = 1, ownActions = false)
        assertEquals("Switch to Focus Pen Pro?", one.title)
        assertEquals("Switching clears the recorded button. Pen actions start again from this pen's defaults.", one.body)
        assertEquals("Reset", confirmWords(PenConfirm.Reset, 0, false).confirm)
        assertEquals("Forget Squeeze?", confirmWords(PenConfirm.Forget("pinch"), 0, false, controlLabel = "Squeeze").title)
    }
}
