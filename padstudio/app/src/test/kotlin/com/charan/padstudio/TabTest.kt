package com.charan.padstudio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TabTest {
    @Test fun `the tab extra names a tab in any case`() {
        assertEquals(Tab.TODAY, Tab.fromExtra("today"))
        assertEquals(Tab.PEN, Tab.fromExtra("PEN"))
        assertEquals(Tab.HOME, Tab.fromExtra("Home"))
        assertEquals(Tab.LOOKS, Tab.fromExtra("looks"))
        assertEquals(Tab.SETUP, Tab.fromExtra("sEtUp"))
        assertEquals(Tab.ABOUT, Tab.fromExtra("about"))
    }

    @Test fun `an unknown, empty or missing tab extra opens nothing`() {
        assertNull(Tab.fromExtra("settings"))
        assertNull(Tab.fromExtra(""))
        assertNull(Tab.fromExtra(null))
        // Exact names only, as before the redesign: no trimming.
        assertNull(Tab.fromExtra("pen "))
    }

    @Test fun `Today comes first and the protected names keep their order`() {
        assertEquals(listOf("TODAY", "PEN", "HOME", "LOOKS", "SETUP", "ABOUT"), Tab.entries.map { it.name })
    }
}
