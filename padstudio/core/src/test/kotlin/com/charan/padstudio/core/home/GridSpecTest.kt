package com.charan.padstudio.core.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The exact widget grids of redesign plan §6b: the stored form, the ranges and the precedence. */
class GridSpecTest {
    @Test fun `every grid in range round-trips through its stored form`() {
        var count = 0
        for (columns in 4..10) for (rows in 2..8) {
            val spec = GridSpec(columns, rows)
            assertEquals("${columns}x$rows", spec.toString())
            assertEquals(spec, GridSpec.parse(spec.toString()))
            count++
        }
        assertEquals(49, count)
        assertEquals("8x4", GridSpec(8, 4).toString())
        assertEquals("8 × 4", GridSpec(8, 4).label)
        assertEquals(listOf("6x3", "8x4", "10x5"), GridSpec.PRESETS.map { it.toString() }, "the plan's presets")
    }

    @Test fun `11 columns and every other out-of-range grid are rejected`() {
        assertNull(GridSpec.parse("11x4"))
        assertFailsWith<IllegalArgumentException> { GridSpec(11, 4) }
        assertEquals(GroupPlanner.MAX_PER_ROW, GridSpec.COLUMNS.last, "10 columns: the Glance per-row limit")
        assertEquals(4..10, GridSpec.COLUMNS)
        assertEquals(2..8, GridSpec.ROWS)
        for (text in listOf("3x4", "4x1", "4x9", "10x9", "0x3", "11x8", "12x2", "99x99")) {
            assertNull(GridSpec.parse(text), text)
        }
        for ((columns, rows) in listOf(3 to 4, 4 to 1, 4 to 9, 0 to 3, -1 to 4, 11 to 8)) {
            assertFailsWith<IllegalArgumentException>("$columns x $rows") { GridSpec(columns, rows) }
        }
    }

    @Test fun `invalid strings parse to null and never throw`() {
        val invalid = listOf(
            null, "", " ", "x", "8x", "x4", "8x4 ", " 8x4", "8x4\n", "8X4", "8×4", "8 x 4", "8 × 4",
            "08x4", "8x04", "+8x4", "-8x4", "8x-4", "8x4x2", "8,4", "8*4", "8.0x4", "abc", "auto",
            "default", "８x４", "١٠x٤",
        )
        for (text in invalid) assertNull(GridSpec.parse(text), "\"$text\"")
    }

    @Test fun `precedence - a per-widget grid, then per-widget auto, then the default`() {
        // perWidget, defaultSpec (StudioConfig.widgetGrid), expected (null = Auto)
        val table = listOf(
            Triple("8x4", "6x3", GridSpec(8, 4)),
            Triple("8x4", null, GridSpec(8, 4)),
            Triple("8x4", "11x4", GridSpec(8, 4)),
            Triple("auto", "6x3", null),
            Triple("AUTO", "6x3", null),
            Triple("Auto", null, null),
            Triple("default", "6x3", GridSpec(6, 3)),
            Triple("DEFAULT", "10x5", GridSpec(10, 5)),
            Triple("default", null, null),
            Triple(null, "6x3", GridSpec(6, 3)),
            Triple(null, null, null),
            Triple("", "10x5", GridSpec(10, 5)),
            Triple("", null, null),
        )
        for ((perWidget, defaultSpec, expected) in table) {
            assertEquals(expected, GridSpec.resolve(perWidget, defaultSpec), "perWidget=$perWidget defaultSpec=$defaultSpec")
        }
    }

    @Test fun `unparsable or out-of-range per-widget values count as Default`() {
        for (bad in listOf("11x4", "0x3", "abc", "8×4", "8X4", " auto", "autox", "4x9")) {
            assertEquals(GridSpec(6, 3), GridSpec.resolve(bad, "6x3"), "\"$bad\" with Default 6x3")
            assertNull(GridSpec.resolve(bad, null), "\"$bad\" with Default Auto")
        }
    }

    @Test fun `an invalid default gives Auto`() {
        for (bad in listOf("11x4", "0x3", "abc", "8×4", "", "auto", "default", "8x4 ")) {
            assertNull(GridSpec.resolve("default", bad), "Default \"$bad\"")
            assertNull(GridSpec.resolve(null, bad), "no key, Default \"$bad\"")
        }
    }
}
