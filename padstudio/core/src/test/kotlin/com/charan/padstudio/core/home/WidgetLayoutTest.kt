package com.charan.padstudio.core.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** GroupPlanner.layoutFor and the "+N" tile: redesign plan §6b's fit rule, labels rule and overflow. */
class WidgetLayoutTest {
    /** About one 11 sp label line at 100 % text; the label tests set their own. */
    private val line = 13f

    @Test fun `auto is today's widget, across a sweep of sizes`() {
        var compared = 0
        for (density in Density.entries) {
            for (w in 0..1300 step 7) for (h in 0..900 step 9) for (fraction in listOf(0f, 0.4f, 0.99f)) {
                val width = w + fraction
                val height = h + fraction
                val layout = GroupPlanner.layoutFor(width, height, density, spec = null, labelLineDp = line)
                // GroupWidget.WidgetContent before plan §6b (PADDING_DP 10, ICON_SHARE 0.78, MAX_ICON_GROWTH 1.3):
                val (columns, rows) = GroupPlanner.gridFor(width.toInt() - 2 * 10, height.toInt() - 2 * 10, density)
                val cellDp = (width - 2 * 10) / columns
                val iconDp = (cellDp * 0.78f).coerceIn(density.iconDp.toFloat(), density.iconDp * 1.3f)
                val at = "$density at $width x $height"
                assertEquals(columns to rows, layout.columns to layout.rows, at)
                assertEquals(iconDp, layout.iconDp, at)
                assertEquals(density.showLabels, layout.showLabels, at)
                assertTrue(layout.fits, at)
                assertNull(layout.reason, at)
                assertNull(layout.trimmedFrom, at)
                assertNull(layout.note, at)
                compared++
            }
        }
        assertEquals(3 * 186 * 101 * 3, compared)
    }

    @Test fun `auto keeps the density's labels whatever the label line`() {
        assertTrue(GroupPlanner.layoutFor(412f, 300f, Density.CALM, null, labelLineDp = 1000f).showLabels)
        assertTrue(GroupPlanner.layoutFor(412f, 300f, Density.COMPACT, null, labelLineDp = 1000f).showLabels)
        assertFalse(GroupPlanner.layoutFor(412f, 300f, Density.DENSE, null, labelLineDp = 0f).showLabels)
    }

    @Test fun `an exact grid is respected when it fits`() {
        // Edit widget's example widget, 412 x 300 dp (docs/redesign/mock-panel.html).
        val layout = GroupPlanner.layoutFor(412f, 300f, Density.COMPACT, GridSpec(8, 4), line)
        assertEquals(8 to 4, layout.columns to layout.rows)
        assertTrue(layout.fits)
        assertNull(layout.reason)
        assertNull(layout.trimmedFrom)
        assertNull(layout.note)
        assertEquals(49f, layout.cellWidthDp, "(412 - 20) / 8")
        assertEquals(63f, layout.cellHeightDp, "(300 - 20 - 28) / 4")
        assertEquals(0.78f * 49f, layout.iconDp, "0.78 x the shorter side")
        for (density in Density.entries) {
            assertEquals(layout, GroupPlanner.layoutFor(412f, 300f, density, GridSpec(8, 4), line), "density only steers Auto")
        }

        // The plan's numbers: 10 columns need 500 dp, 4 rows need 240 dp; exactly that much fits.
        assertEquals(500, GroupPlanner.widthNeededDp(10))
        assertEquals(240, GroupPlanner.heightNeededDp(4))
        assertEquals(404, GroupPlanner.widthNeededDp(8), "mock-widget.html: 8 columns need 404 dp")
        val tight = GroupPlanner.layoutFor(500f, 240f, Density.DENSE, GridSpec(10, 4), line)
        assertTrue(tight.fits)
        assertEquals(10 to 4, tight.columns to tight.rows)
        assertEquals(48f, tight.cellWidthDp)
        assertEquals(48f, tight.cellHeightDp)
        assertEquals(0.78f * 48f, tight.iconDp)

        // Every grid in range, in a widget with room for the biggest (10 x 8).
        for (columns in 4..10) for (rows in 2..8) {
            val spec = GridSpec(columns, rows)
            val roomy = GroupPlanner.layoutFor(500f, 432f, Density.CALM, spec, line)
            assertTrue(roomy.fits, "$spec")
            assertEquals(columns to rows, roomy.columns to roomy.rows, "$spec")
            assertTrue(roomy.cellWidthDp >= 48f && roomy.cellHeightDp >= 48f, "$spec")
        }
    }

    @Test fun `a grid that doesn't fit is refused with its reason and trimmed to the largest that fits`() {
        // mock-panel.html: 10 x 5 is disabled on the 412 x 300 dp widget.
        val wide = GroupPlanner.layoutFor(412f, 300f, Density.COMPACT, GridSpec(10, 5), line)
        assertFalse(wide.fits)
        assertEquals("10 columns need a 500 dp wide widget; this one is 412 dp.", wide.reason)
        assertEquals(GridSpec(10, 5), wide.trimmedFrom)
        assertEquals(8 to 5, wide.columns to wide.rows, "392 dp hold 8 columns; 5 rows need 288 dp of 300")
        assertEquals("10 × 5 doesn't fit · showing 8 × 5", wide.note)

        // The plan's own example of a widget resized smaller: "8 × 4 doesn't fit · showing 6 × 3".
        val small = GroupPlanner.layoutFor(330f, 230f, Density.COMPACT, GridSpec(8, 4), line)
        assertEquals("8 × 4 doesn't fit · showing 6 × 3", small.note)
        assertEquals(
            "8 columns need a 404 dp wide widget; this one is 330 dp. 4 rows need a 240 dp tall widget; this one is 230 dp.",
            small.reason,
        )

        // Short by 1 dp, or by a fraction: refused, and the reason never rounds the widget up to the need.
        for (width in listOf(499f, 499.99f)) {
            val short = GroupPlanner.layoutFor(width, 240f, Density.COMPACT, GridSpec(10, 4), line)
            assertFalse(short.fits, "$width")
            assertEquals("10 columns need a 500 dp wide widget; this one is 499 dp.", short.reason)
            assertEquals(9 to 4, short.columns to short.rows)
        }
        val low = GroupPlanner.layoutFor(500f, 239.5f, Density.COMPACT, GridSpec(10, 4), line)
        assertEquals("4 rows need a 240 dp tall widget; this one is 239 dp.", low.reason)
        assertEquals(10 to 3, low.columns to low.rows)
    }

    @Test fun `every verdict follows the 48 dp rule, and a trimmed grid is the largest that fits`() {
        var refused = 0
        for (w in 60..1200 step 13) for (h in 60..900 step 11) for (columns in 4..10) for (rows in 2..8) {
            val spec = GridSpec(columns, rows)
            val layout = GroupPlanner.layoutFor(w.toFloat(), h.toFloat(), Density.COMPACT, spec, line)
            val usableWidth = w - 20
            val usableHeight = h - 20 - 28
            val at = "$spec at $w x $h"
            assertEquals(48 * columns <= usableWidth && 48 * rows <= usableHeight, layout.fits, at)
            if (layout.fits) {
                assertEquals(columns to rows, layout.columns to layout.rows, at)
                assertNull(layout.reason, at)
                assertNull(layout.trimmedFrom, at)
            } else {
                refused++
                assertEquals(spec, layout.trimmedFrom, at)
                assertTrue(layout.reason!!.isNotBlank(), at)
                assertTrue(layout.columns <= columns && layout.rows <= rows, at)
                // Largest: one more column or row would break the 48 dp floor, unless it was all asked for.
                assertTrue(layout.columns == columns || 48 * (layout.columns + 1) > usableWidth, at)
                assertTrue(layout.rows == rows || 48 * (layout.rows + 1) > usableHeight, at)
                if (usableWidth >= 48) assertTrue(layout.cellWidthDp >= 48f, at)
                if (usableHeight >= 48) assertTrue(layout.cellHeightDp >= 48f, at)
            }
        }
        assertTrue(refused > 1000, "the sweep must reach refusals, saw $refused")
    }

    @Test fun `labels show only when the cell holds the icon, 4 dp and one label line`() {
        // 4 x 2 in 420 x 248 dp: 100 dp square cells, so the icon is 78 dp and a label line may take 18 dp.
        val cell = GroupPlanner.layoutFor(420f, 248f, Density.COMPACT, GridSpec(4, 2), 0f)
        assertEquals(100f, cell.cellWidthDp)
        assertEquals(100f, cell.cellHeightDp)
        assertEquals(78f, cell.iconDp)
        assertTrue(GroupPlanner.layoutFor(420f, 248f, Density.COMPACT, GridSpec(4, 2), 18f).showLabels, "exactly enough room")
        assertFalse(GroupPlanner.layoutFor(420f, 248f, Density.COMPACT, GridSpec(4, 2), 18.5f).showLabels, "half a dp too little")

        // mock-panel.html: 8 x 4 on the 412 x 300 dp widget keeps names at 100 % text and drops them at 200 %.
        assertTrue(GroupPlanner.layoutFor(412f, 300f, Density.COMPACT, GridSpec(8, 4), 13f).showLabels)
        assertFalse(GroupPlanner.layoutFor(412f, 300f, Density.COMPACT, GridSpec(8, 4), 22f).showLabels)

        // A tiny trimmed cell (40 x 42 dp) still gets a 32 dp icon, and no room for a label.
        val tiny = GroupPlanner.layoutFor(60f, 90f, Density.COMPACT, GridSpec(4, 2), line)
        assertEquals(1 to 1, tiny.columns to tiny.rows)
        assertEquals(32f, tiny.iconDp)
        assertFalse(tiny.showLabels)
    }

    @Test fun `the plus-N tile takes the last cell`() {
        // mock-widget.html: Small 2 x 2 in Auto shows 4 x 3; 15 apps don't fit in 12 cells, so the last cell is "+4".
        val small = GroupPlanner.overflow(appCount = 15, columns = 4, rows = 3)
        assertEquals(Overflow(shown = 11, more = 4), small)
        assertEquals(11, small.moreIndex)
        assertEquals(2 to 3, small.moreIndex!! / 4 to small.moreIndex!! % 4, "bottom row, last column")

        assertEquals(Overflow(12, 0), GroupPlanner.overflow(12, 4, 3), "exactly full: no tile")
        assertNull(GroupPlanner.overflow(12, 4, 3).moreIndex)
        assertEquals(Overflow(11, 2), GroupPlanner.overflow(13, 4, 3), "one too many: the tile replaces the last app")
        assertEquals(Overflow(3, 0), GroupPlanner.overflow(3, 4, 3))
        assertEquals(Overflow(0, 0), GroupPlanner.overflow(0, 4, 3))
        assertEquals(Overflow(0, 5), GroupPlanner.overflow(5, 1, 1), "a single cell holds only the tile")

        // Same as the widget always did (GroupWidget: apps.take(slots - 1) when they don't fit), and laid
        // out the way it lays cells out (apps, then the tile, in rows of `columns`), the tile ends the last row.
        for (columns in 1..10) for (rows in 1..8) for (apps in 0..90) {
            val slots = columns * rows
            val shown = if (apps > slots) slots - 1 else apps
            val overflow = GroupPlanner.overflow(apps, columns, rows)
            assertEquals(Overflow(shown, apps - shown), overflow, "$apps apps in $columns x $rows")
            if (apps > slots) {
                val cells = List(overflow.shown) { "app" } + "+${overflow.more}"
                val laidOut = cells.chunked(columns)
                assertEquals(rows, laidOut.size)
                assertEquals(columns, laidOut.last().size)
                assertEquals("+${overflow.more}", laidOut.last().last())
                assertEquals(slots - 1, overflow.moreIndex)
            }
        }

        // Through a layout: 8 x 4 = 32 cells for 40 apps.
        val layout = GroupPlanner.layoutFor(412f, 300f, Density.COMPACT, GridSpec(8, 4), line)
        assertEquals(Overflow(shown = 31, more = 9), layout.overflow(40))
    }

    @Test fun `the largest fit is the Custom picker's limit`() {
        assertEquals(GridSpec(8, 5), GroupPlanner.largestFit(412f, 300f), "mock-panel.html: Custom, up to 8 x 5")
        assertEquals(GridSpec(10, 8), GroupPlanner.largestFit(2000f, 2000f), "capped at 10 x 8")
        assertEquals(GridSpec(4, 2), GroupPlanner.largestFit(212f, 144f))
        assertNull(GroupPlanner.largestFit(211f, 300f), "3 columns: under the 4-column minimum")
        assertNull(GroupPlanner.largestFit(412f, 143f), "1 row: under the 2-row minimum")
        for (w in 200..900 step 17) for (h in 120..700 step 19) {
            val largest = GroupPlanner.largestFit(w.toFloat(), h.toFloat()) ?: continue
            assertTrue(GroupPlanner.layoutFor(w.toFloat(), h.toFloat(), Density.COMPACT, largest, line).fits, "$largest at $w x $h")
        }
    }
}
