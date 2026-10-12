package com.charan.padstudio.home

import com.charan.padstudio.core.home.AppFacts
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.core.home.GridSpec
import com.charan.padstudio.core.home.GroupPlanner
import com.charan.padstudio.core.home.PlannedGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Edit widget's grid picker (plan §6b): which grids are offered, which are refused and why, and what
 * Default stands for. Expected numbers are worked out by hand from the fit rule (every cell at least
 * 48 × 48 dp inside 20 dp of padding and the 28 dp header), so a picker that stopped asking
 * GroupPlanner.layoutFor, or a layoutFor that lost its 48 dp floor, fails here.
 */
class GridPickerTest {
    /** The mock's example widget, 412 × 300 dp, in landscape. */
    private val mockWidget = WidgetSizeDp(widthDp = 412, heightDp = 300, landscape = true)

    /** One 11 sp label line at 100 % text is 11 dp; at 200 % Android's nonlinear scale gives about 22 dp. */
    private val line100 = 11f
    private val line200 = 22f

    private fun options(
        size: WidgetSizeDp? = mockWidget,
        density: Density = Density.COMPACT,
        defaultGrid: String? = null,
        labelLineDp: Float = line100,
        saved: GridChoice = GridChoice.Default,
    ) = GridPicker.options(size, density, defaultGrid, labelLineDp, saved)

    private fun List<GridOption>.preset(columns: Int, rows: Int) = single { it.preset == GridSpec(columns, rows) }

    private fun List<GridOption>.of(kind: GridOptionKind) = single { it.kind == kind }

    // --- Reading and writing GroupWidget.GRID ---

    @Test fun `saved values read the way the widget resolves them`() {
        assertEquals(SavedGrid(GridChoice.Default, invalid = false), GridPicker.read(null))
        assertEquals(SavedGrid(GridChoice.Default, invalid = false), GridPicker.read("default"))
        assertEquals(SavedGrid(GridChoice.Default, invalid = false), GridPicker.read("DEFAULT"))
        assertEquals(SavedGrid(GridChoice.Auto, invalid = false), GridPicker.read("auto"))
        assertEquals(SavedGrid(GridChoice.Auto, invalid = false), GridPicker.read("AUTO"))
        assertEquals(SavedGrid(GridChoice.Exact(GridSpec(8, 4)), invalid = false), GridPicker.read("8x4"))
        assertEquals(SavedGrid(GridChoice.Exact(GridSpec(7, 3)), invalid = false), GridPicker.read("7x3"))
    }

    @Test fun `an invalid saved value shows as Default and is flagged, never a crash`() {
        for (junk in listOf("", " ", "8X4", "8 × 4", " 8x4", "08x4", "11x4", "3x4", "4x9", "0x0", "x", "garbage", "8x4x2")) {
            assertEquals(SavedGrid(GridChoice.Default, invalid = true), GridPicker.read(junk), "\"$junk\"")
        }
    }

    @Test fun `what the picker shows as chosen is what the widget will draw`() {
        val saved = listOf(null, "default", "auto", "Auto", "6x3", "8x4", "10x5", "7x5", "4x2", "11x4", "8X4", "junk", "")
        val defaults = listOf(null, "8x4", "10x5", "4x8", "12x4", "auto")
        for (value in saved) {
            for (default in defaults) {
                assertEquals(
                    GridSpec.resolve(value, default),
                    GridPicker.effective(GridPicker.read(value).choice, default),
                    "saved \"$value\", default \"$default\"",
                )
            }
        }
    }

    @Test fun `each choice is stored as default, auto or CxR and reads back unchanged`() {
        assertEquals("default", GridChoice.Default.stored)
        assertEquals("auto", GridChoice.Auto.stored)
        assertEquals("6x3", GridChoice.Exact(GridSpec(6, 3)).stored)
        assertEquals("10x8", GridChoice.Exact(GridSpec(10, 8)).stored)
        for (choice in listOf(GridChoice.Default, GridChoice.Auto, GridChoice.Exact(GridSpec(6, 3)), GridChoice.Exact(GridSpec(7, 5)))) {
            assertEquals(SavedGrid(choice, invalid = false), GridPicker.read(choice.stored))
        }
    }

    // --- The widget's size ---

    @Test fun `the size is the launcher's portrait or landscape pair`() {
        // AppWidgetManager: portrait is minWidth × maxHeight, landscape maxWidth × minHeight.
        assertEquals(WidgetSizeDp(412, 300, landscape = true), GridPicker.sizeFor(minWidth = 300, maxWidth = 412, minHeight = 300, maxHeight = 420, landscape = true))
        assertEquals(WidgetSizeDp(300, 420, landscape = false), GridPicker.sizeFor(minWidth = 300, maxWidth = 412, minHeight = 300, maxHeight = 420, landscape = false))
    }

    @Test fun `a missing bound is filled from the other and said, and no size at all is null`() {
        assertEquals(
            WidgetSizeDp(300, 300, landscape = true, partial = true),
            GridPicker.sizeFor(minWidth = 300, maxWidth = 0, minHeight = 300, maxHeight = 420, landscape = true),
        )
        assertNull(GridPicker.sizeFor(0, 0, 300, 420, landscape = true))
        assertNull(GridPicker.sizeFor(300, 412, 0, 0, landscape = false))
        assertEquals("This widget: 412 × 300 dp", GridPicker.sizeText(mockWidget))
        assertEquals("Landscape size, as the launcher reports it", GridPicker.sizeSource(mockWidget))
        assertEquals("Portrait size, partly reported by the launcher", GridPicker.sizeSource(WidgetSizeDp(300, 420, landscape = false, partial = true)))
        assertEquals("This widget's size isn't known yet", GridPicker.sizeText(null))
        assertNull(GridPicker.sizeSource(null))
    }

    // --- The tiles on the mock's 412 × 300 dp widget ---

    @Test fun `the mock's widget gets layoutFor's numbers, not the mock's 6 x 4`() {
        val tiles = options()

        // Auto is today's gridFor(392, 280, Standard): 392 / 64 = 6 columns, (280 − 36) / 64 = 3 rows.
        val auto = tiles.of(GridOptionKind.AUTO)
        assertEquals("6 × 3 here", auto.detail)
        assertEquals(Cells(6, 3), auto.mini)
        val default = tiles.of(GridOptionKind.DEFAULT)
        assertEquals("Default", default.title)
        assertEquals("Auto · 6 × 3 here", default.detail)
        assertEquals("Default · Auto, 6 × 3 here", default.description)
        assertTrue(default.enabled)
    }

    @Test fun `grids that fit are offered with their names verdict`() {
        val tiles = options()
        // 6 × 3 needs 308 × 192 dp and 8 × 4 needs 404 × 240 dp; both fit 412 × 300.
        assertEquals("Fits, with names", tiles.preset(6, 3).detail)
        assertEquals("Fits, with names", tiles.preset(8, 4).detail)
        assertTrue(tiles.preset(6, 3).enabled && tiles.preset(8, 4).enabled)
        assertNull(tiles.preset(8, 4).reason)
    }

    @Test fun `at 200 percent text 8 x 4 loses its names and 6 x 3 keeps them`() {
        val tiles = options(labelLineDp = line200)
        // 8 × 4: cells 49 × 63 dp, icon 0.78 × 49 = 38.2; 63 < 38.2 + 4 + 22.
        assertEquals("Fits, no names", tiles.preset(8, 4).detail)
        // 6 × 3: cells 65.3 × 84 dp, icon 51.0; 84 ≥ 51.0 + 4 + 22.
        assertEquals("Fits, with names", tiles.preset(6, 3).detail)
    }

    @Test fun `a grid that doesn't fit is disabled with layoutFor's reason, worded as the mock`() {
        val tiles = options()
        val big = tiles.preset(10, 5)
        assertFalse(big.enabled)
        assertTrue(big.warning)
        assertEquals("Doesn't fit", big.detail)
        assertEquals("10 columns need a 500 dp wide widget; this one is 412 dp.", big.reason)
        assertEquals("10 columns need a 500 dp wide widget; this one is 412 dp. Resize it first.", GridPicker.reasonText(tiles))
    }

    @Test fun `custom offers up to the largest grid that fits`() {
        // (412 − 20) / 48 = 8 columns, (300 − 48) / 48 = 5 rows.
        assertEquals("Up to 8 × 5", options().of(GridOptionKind.CUSTOM).detail)
    }

    @Test fun `on a small widget every refusal is named and explained`() {
        val tiles = options(size = WidgetSizeDp(300, 200, landscape = true))
        assertFalse(tiles.preset(6, 3).enabled)
        assertEquals("6 columns need a 308 dp wide widget; this one is 300 dp.", tiles.preset(6, 3).reason)
        assertEquals(
            "8 columns need a 404 dp wide widget; this one is 300 dp. 4 rows need a 240 dp tall widget; this one is 200 dp.",
            tiles.preset(8, 4).reason,
        )
        assertEquals("Up to 5 × 3", tiles.of(GridOptionKind.CUSTOM).detail)
        assertEquals(
            "6 × 3: 6 columns need a 308 dp wide widget; this one is 300 dp.\n" +
                "8 × 4: 8 columns need a 404 dp wide widget; this one is 300 dp. 4 rows need a 240 dp tall widget; this one is 200 dp.\n" +
                "10 × 5: 10 columns need a 500 dp wide widget; this one is 300 dp. 5 rows need a 288 dp tall widget; this one is 200 dp.\n" +
                "Resize it first.",
            GridPicker.reasonText(tiles),
        )
    }

    @Test fun `a widget too small for any exact grid refuses Custom too`() {
        val custom = options(size = WidgetSizeDp(200, 150, landscape = false)).of(GridOptionKind.CUSTOM)
        assertFalse(custom.enabled)
        assertEquals("4 columns need a 212 dp wide widget; this one is 200 dp.", custom.reason)
    }

    @Test fun `when the size isn't reported nothing is refused and nothing is claimed`() {
        val tiles = options(size = null)
        assertTrue(tiles.all { it.enabled })
        assertTrue(tiles.none { it.warning || it.reason != null })
        assertEquals("Fit not known yet", tiles.preset(10, 5).detail)
        assertEquals("Columns 4–10, rows 2–8", tiles.of(GridOptionKind.CUSTOM).detail)
        assertNull(GridPicker.reasonText(tiles))
    }

    // --- Default stands for StudioConfig.widgetGrid ---

    @Test fun `Default shows the grid it resolves to`() {
        val default = options(defaultGrid = "8x4").of(GridOptionKind.DEFAULT)
        assertEquals("8 × 4 · fits, with names", default.detail)
        assertEquals("Default · 8 × 4, fits, with names", default.description)
        assertEquals("Auto · 6 × 3 here", options(defaultGrid = "12x4").of(GridOptionKind.DEFAULT).detail)
    }

    @Test fun `a Default that doesn't fit stays choosable and says what the widget shows instead`() {
        val tiles = options(defaultGrid = "10x5")
        val default = tiles.of(GridOptionKind.DEFAULT)
        assertTrue(default.enabled)
        assertTrue(default.warning)
        assertEquals("10 × 5 doesn't fit · showing 8 × 5", default.detail)
        assertEquals(
            "Default (10 × 5): 10 columns need a 500 dp wide widget; this one is 412 dp.\n" +
                "10 × 5: 10 columns need a 500 dp wide widget; this one is 412 dp.\n" +
                "Resize it first.",
            GridPicker.reasonText(tiles),
        )
    }

    // --- The saved choice ---

    @Test fun `the saved grid is the chosen tile`() {
        val presetSaved = GridChoice.Exact(GridSpec(8, 4))
        val tiles = options(saved = presetSaved)
        assertEquals(listOf(GridOptionKind.PRESET), tiles.filter { GridPicker.isSelected(it, presetSaved, customOpen = false) }.map { it.kind })
        assertTrue(GridPicker.isSelected(tiles.preset(8, 4), presetSaved, customOpen = false))

        for (choice in listOf(GridChoice.Default, GridChoice.Auto)) {
            val chosen = options(saved = choice).filter { GridPicker.isSelected(it, choice, customOpen = false) }
            assertEquals(1, chosen.size, "$choice")
        }
    }

    @Test fun `a saved custom grid selects Custom and shows its numbers`() {
        val saved = GridChoice.Exact(GridSpec(7, 3))
        val tiles = options(saved = saved)
        val custom = tiles.of(GridOptionKind.CUSTOM)
        assertEquals("7 × 3 · fits, with names", custom.detail)
        assertEquals(listOf(custom), tiles.filter { GridPicker.isSelected(it, saved, customOpen = false) })
    }

    @Test fun `while Custom is open it alone is chosen, even on a preset's numbers`() {
        val saved = GridChoice.Exact(GridSpec(8, 4))
        val chosen = options(saved = saved).filter { GridPicker.isSelected(it, saved, customOpen = true) }
        assertEquals(listOf(GridOptionKind.CUSTOM), chosen.map { it.kind })
    }

    @Test fun `a saved grid the widget was shrunk under stays chosen and says what it shows`() {
        val saved = GridChoice.Exact(GridSpec(10, 5))
        val big = options(saved = saved).preset(10, 5)
        assertTrue(big.enabled)
        assertEquals("10 × 5 doesn't fit · showing 8 × 5", big.detail)
        assertEquals("10 columns need a 500 dp wide widget; this one is 412 dp.", big.reason)
    }

    @Test fun `tapping a tile saves its choice and Custom opens the editor instead`() {
        val tiles = options()
        assertEquals(GridChoice.Default, GridPicker.choiceFor(tiles.of(GridOptionKind.DEFAULT)))
        assertEquals(GridChoice.Auto, GridPicker.choiceFor(tiles.of(GridOptionKind.AUTO)))
        assertEquals(GridChoice.Exact(GridSpec(6, 3)), GridPicker.choiceFor(tiles.preset(6, 3)))
        assertNull(GridPicker.choiceFor(tiles.of(GridOptionKind.CUSTOM)))
    }

    // --- Sweeps: the picker never disagrees with layoutFor ---

    @Test fun `every refusal is exactly layoutFor's, across sizes, densities and text sizes`() {
        var refusals = 0
        var offers = 0
        for (width in 120..760 step 13) {
            for (height in 90..600 step 17) {
                for (density in Density.entries) {
                    for (line in listOf(line100, line200)) {
                        val size = WidgetSizeDp(width, height, landscape = true)
                        val tiles = options(size = size, density = density, labelLineDp = line)
                        for (spec in GridSpec.PRESETS) {
                            val layout = GroupPlanner.layoutFor(width.toFloat(), height.toFloat(), density, spec, line)
                            val tile = tiles.preset(spec.columns, spec.rows)
                            assertEquals(layout.fits, tile.enabled, "$spec on $width × $height")
                            assertEquals(layout.reason, tile.reason, "$spec on $width × $height")
                            if (layout.fits) offers++ else refusals++
                        }
                        val largest = GroupPlanner.largestFit(width.toFloat(), height.toFloat())
                        assertEquals(largest != null, tiles.of(GridOptionKind.CUSTOM).enabled, "Custom on $width × $height")
                    }
                }
            }
        }
        // The sweep has to see both outcomes, or it proves nothing.
        assertTrue(refusals > 100 && offers > 100, "refusals $refusals, offers $offers")
    }

    // --- Custom ---

    @Test fun `custom offers the counts that fit and explains the first that doesn't`() {
        val editor = GridPicker.custom(GridSpec(7, 3), mockWidget, Density.COMPACT, line100)
        assertEquals((4..8).toList(), editor.columns.filter { it.enabled }.map { it.value })
        assertEquals(listOf(9, 10), editor.columns.filterNot { it.enabled }.map { it.value })
        assertEquals((2..5).toList(), editor.rows.filter { it.enabled }.map { it.value })
        assertEquals("7 × 3 · fits, with names", editor.verdict)
        assertFalse(editor.warning)
        assertEquals(
            "9 columns need a 452 dp wide widget; this one is 412 dp. 6 rows need a 336 dp tall widget; this one is 300 dp. Resize the widget for more.",
            editor.reasons,
        )
    }

    @Test fun `custom counts match the room each needs, across sizes`() {
        for (width in 150..760 step 11) {
            for (height in 120..640 step 19) {
                val editor = GridPicker.custom(GridSpec(4, 2), WidgetSizeDp(width, height, landscape = false), Density.CALM, line100)
                assertEquals(
                    GridSpec.COLUMNS.filter { GroupPlanner.widthNeededDp(it) <= width },
                    editor.columns.filter { it.enabled }.map { it.value },
                    "columns on $width × $height",
                )
                assertEquals(
                    GridSpec.ROWS.filter { GroupPlanner.heightNeededDp(it) <= height },
                    editor.rows.filter { it.enabled }.map { it.value },
                    "rows on $width × $height",
                )
            }
        }
    }

    @Test fun `picking a count keeps the other one within what fits`() {
        val shrunk = GridPicker.custom(GridSpec(7, 7), mockWidget, Density.COMPACT, line100)
        assertTrue(shrunk.warning)
        assertEquals("7 × 7 doesn't fit · showing 7 × 5", shrunk.verdict)
        assertEquals(GridSpec(6, 5), shrunk.withColumns(6))
        assertEquals(GridSpec(7, 4), GridPicker.custom(GridSpec(7, 3), mockWidget, Density.COMPACT, line100).withRows(4))
    }

    @Test fun `custom starts from the grid the widget shows now, within what fits`() {
        fun start(saved: GridChoice, size: WidgetSizeDp? = mockWidget, default: String? = null) =
            GridPicker.initialCustom(saved, size, Density.COMPACT, default, line100)
        assertEquals(GridSpec(6, 3), start(GridChoice.Auto))
        assertEquals(GridSpec(6, 3), start(GridChoice.Default))
        assertEquals(GridSpec(8, 5), start(GridChoice.Default, default = "10x5"))
        assertEquals(GridSpec(7, 3), start(GridChoice.Exact(GridSpec(7, 3))))
        assertEquals(GridSpec(6, 3), start(GridChoice.Auto, size = null))
        // No exact grid fits at all: still a valid grid, never a crash.
        assertEquals(GridSpec(4, 2), start(GridChoice.Auto, size = WidgetSizeDp(150, 100, landscape = true)))
    }

    @Test fun `with no size the custom editor offers every count and claims no fit`() {
        val editor = GridPicker.custom(GridSpec(10, 8), null, Density.DENSE, line100)
        assertTrue(editor.columns.all { it.enabled } && editor.rows.all { it.enabled })
        assertEquals("10 × 8 · fit not known yet", editor.verdict)
        assertNull(editor.reasons)
    }

    // --- Words ---

    @Test fun `group and app counts read plainly`() {
        val more = PlannedGroup(AppGroup.MORE, List(5) { AppFacts("pkg.$it", "Main", "App $it") })
        val study = PlannedGroup(AppGroup.STUDY, listOf(AppFacts("pkg.notes", "Main", "Notes")))
        val groups = listOf(study, more)
        assertEquals("1 app", PanelText.appCount(1))
        assertEquals("0 apps", PanelText.appCount(0))
        assertEquals("Showing More · 5 apps", PanelText.groupMeta("MORE", groups))
        assertEquals("Showing Study · 1 app", PanelText.groupMeta("STUDY", groups))
        assertEquals("Showing Games · no apps right now", PanelText.groupMeta("GAMES", groups))
        assertEquals("Showing More", PanelText.groupMeta("MORE", null))
        assertEquals("No group chosen yet", PanelText.groupMeta(null, groups))
        assertEquals("No group chosen yet", PanelText.groupMeta("More", groups))
        assertEquals("6 apps in 2 groups", PanelText.librarySubtitle(groups))
        assertEquals("1 app in 1 group", PanelText.librarySubtitle(listOf(study)))
    }
}
