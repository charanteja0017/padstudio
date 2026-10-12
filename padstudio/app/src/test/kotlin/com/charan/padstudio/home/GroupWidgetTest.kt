package com.charan.padstudio.home

import android.app.WallpaperColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.charan.padstudio.core.home.AppFacts
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.core.home.GroupPlanner
import com.charan.padstudio.core.home.Placement
import java.io.File
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.w3c.dom.Element

/** The group widget's rules (plan §6b, §10 Widget): grid choice, designed states, header, glass colours, picker. */
class GroupWidgetTest {
    /** About one 11 sp label line at 100 % text. */
    private val line = 13f

    // --- Grid choice -----------------------------------------------------------------------------

    @Test fun `Default and Auto draw today's grid, across a sweep of sizes`() {
        // Every saved value that ends in Auto: no key or "default" while PadStudio's default is Auto, "auto"
        // over any default, and invalid values (treated as Default, here Auto).
        val autoCases = listOf(
            null to null, "default" to null, "DEFAULT" to null, "auto" to null, "Auto" to "8x4", "auto" to "10x5",
            "11x4" to null, "8X4" to null, "" to null, "banana" to null, null to "junk",
        )
        var compared = 0
        for ((saved, default) in autoCases) {
            for (density in Density.entries) {
                for (w in 0..1300 step 13) for (h in 0..900 step 11) for (fraction in listOf(0f, 0.5f)) {
                    val width = w + fraction
                    val height = h + fraction
                    val layout = widgetLayout(width, height, saved, default, density, line)
                    // GroupWidget before plan §6b: gridFor(width - 2 * PADDING_DP, height - 2 * PADDING_DP, density).
                    val today = GroupPlanner.gridFor(width.toInt() - 20, height.toInt() - 20, density)
                    val at = "saved $saved, default $default, $density at $width x $height"
                    assertEquals(today, layout.columns to layout.rows, at)
                    assertNull(layout.note, at)
                    compared++
                }
            }
        }
        assertEquals(11 * 3 * 101 * 82 * 2, compared)
    }

    @Test fun `the sweep can tell an exact grid from today's grid`() {
        // Instrument check for the sweep above: with an 8 x 4 default it must see a difference.
        var differ = 0
        for (w in 0..1300 step 13) for (h in 0..900 step 11) {
            val layout = widgetLayout(w.toFloat(), h.toFloat(), null, "8x4", Density.COMPACT, line)
            if (layout.columns to layout.rows != GroupPlanner.gridFor(w - 20, h - 20, Density.COMPACT)) differ++
        }
        assertTrue(differ > 1000, "an 8 x 4 default must differ from Auto at many sizes, saw $differ")
    }

    @Test fun `widgets 42 and 49 have no grid key, so they follow Default`() {
        // The tablet's Wide and Large widgets, at mock-widget.html's sizes, with the owner's density (Standard).
        assertEquals(9 to 3, widgetLayout(600f, 292f, null, null, Density.COMPACT, line).let { it.columns to it.rows })
        assertEquals(9 to 8, widgetLayout(600f, 600f, null, null, Density.COMPACT, line).let { it.columns to it.rows })
        // Changing the Default changes them too (plan §6b, "Applies to all widgets set to Default").
        assertEquals(8 to 4, widgetLayout(600f, 292f, null, "8x4", Density.COMPACT, line).let { it.columns to it.rows })
        assertEquals(8 to 4, widgetLayout(600f, 600f, null, "8x4", Density.COMPACT, line).let { it.columns to it.rows })
    }

    @Test fun `a widget's own grid wins, then auto, then PadStudio's default`() {
        fun grid(saved: String?, default: String?) = widgetLayout(412f, 300f, saved, default, Density.COMPACT, line).let { it.columns to it.rows }
        val auto = GroupPlanner.gridFor(392, 280, Density.COMPACT)
        assertEquals(6 to 3, grid("6x3", "8x4"))
        assertEquals(8 to 4, grid(null, "8x4"))
        assertEquals(8 to 4, grid("default", "8x4"))
        assertEquals(auto, grid("auto", "8x4"))
        assertEquals(auto, grid(null, null))
        assertEquals(8 to 4, grid("11x4", "8x4"), "an invalid grid counts as Default")
    }

    @Test fun `a saved grid that doesn't fit shows what it can and says so`() {
        assertEquals("8 × 4 doesn't fit · showing 6 × 3", widgetLayout(330f, 230f, "8x4", null, Density.COMPACT, line).note)
        assertEquals("8 × 4 doesn't fit · showing 6 × 3", widgetLayout(330f, 230f, null, "8x4", Density.COMPACT, line).note, "a default grid too")
        // mock-widget.html's 400 dp example: the real rule keeps 7 columns and all 4 rows.
        assertEquals("8 × 4 doesn't fit · showing 7 × 4", widgetLayout(400f, 292f, "8x4", null, Density.COMPACT, line).note)
        // Resized back: the saved grid returns, without a note.
        val back = widgetLayout(412f, 300f, "8x4", null, Density.COMPACT, line)
        assertEquals(8 to 4, back.columns to back.rows)
        assertNull(back.note)
    }

    @Test fun `invalid saved grids are reported, and a default only when it's used`() {
        assertEquals(emptyList(), invalidGrids(null, null))
        assertEquals(emptyList(), invalidGrids("default", "8x4"))
        assertEquals(emptyList(), invalidGrids("Default", null))
        assertEquals(emptyList(), invalidGrids("auto", "junk"), "auto never reads the default")
        assertEquals(emptyList(), invalidGrids("6x3", "junk"), "a grid of its own never reads the default")
        assertEquals(listOf("its grid \"11x4\""), invalidGrids("11x4", null))
        assertEquals(listOf("its grid \"8X4\"", "the default grid \"junk\""), invalidGrids("8X4", "junk"))
        assertEquals(listOf("the default grid \"\""), invalidGrids(null, ""))
    }

    @Test fun `every cell, so every +N tile, is a 48 dp target in Auto and in any grid that fits`() {
        for (density in Density.entries) {
            for (w in 20 + density.cellDp..1300 step 7) for (h in 56 + density.cellDp..900 step 9) {
                val auto = widgetLayout(w.toFloat(), h.toFloat(), null, null, density, line)
                assertTrue(auto.cellWidthDp >= 48f && auto.cellHeightDp >= 48f, "$density at $w x $h")
            }
        }
        for (w in 60..1300 step 23) for (h in 60..900 step 19) for (spec in listOf("4x2", "6x3", "8x4", "10x5", "10x8")) {
            val layout = widgetLayout(w.toFloat(), h.toFloat(), spec, null, Density.COMPACT, line)
            if (layout.fits) assertTrue(layout.cellWidthDp >= 48f && layout.cellHeightDp >= 48f, "$spec at $w x $h")
        }
    }

    // --- Cells -------------------------------------------------------------------------------------

    @Test fun `the +N tile takes the last cell, and short groups end in empty cells`() {
        val apps = (1..15).map { "app$it" }
        val small = gridCells(apps, columns = 4, rows = 3)
        assertEquals(3, small.size)
        assertTrue(small.all { it.size == 4 })
        assertEquals(Cell.More(4), small.last().last(), "mock-widget.html: Small 2 x 2, the last cell is +4")
        assertEquals((1..11).map { Cell.App("app$it") }, small.flatten().dropLast(1))

        val wide = gridCells(apps, columns = 9, rows = 3)
        assertEquals(apps, wide.flatten().filterIsInstance<Cell.App<*>>().map { it.app })
        assertTrue(wide.flatten().none { it is Cell.More })
        assertEquals(Cell.Empty, wide[1][6])
        assertTrue(wide[2].all { it == Cell.Empty }, "an empty row is still drawn, so cells keep their height")

        assertTrue(gridCells(apps.take(12), 4, 3).flatten().all { it is Cell.App }, "exactly full: no tile")
        assertEquals(Cell.More(2), gridCells(apps.take(13), 4, 3).last().last(), "one too many: the tile replaces the last app")
        assertEquals(listOf(listOf(Cell.More(5))), gridCells(apps.take(5), 1, 1), "one cell holds only the tile")
        assertTrue(gridCells(emptyList<String>(), 4, 3).flatten().all { it == Cell.Empty })
    }

    @Test fun `Glance never gets more than 10 rows, and every app has a cell or sits behind +N`() {
        // Glance drops a Column's children after the 10th, and Auto (gridFor) doesn't cap rows.
        var tall = 0
        for (density in Density.entries) for (w in 100..1300 step 50) for (h in 100..1400 step 47) {
            val layout = widgetLayout(w.toFloat(), h.toFloat(), null, null, density, line)
            if (layout.rows > 10) tall++
            val cells = layout.columns * layout.rows
            for (count in listOf(1, 15, 10 * layout.columns, 10 * layout.columns + 1, cells, cells + 7)) {
                val apps = List(count) { it }
                val rows = drawnRows(apps, layout.columns, layout.rows)
                val at = "$density at $w x $h (${layout.columns} x ${layout.rows}), $count apps"
                assertTrue(rows.size <= 10 && rows.all { it.size <= 10 }, "Glance draws 10 children at most: ${rows.size} rows, $at")
                val drawn = rows.flatten()
                val shown = drawn.filterIsInstance<Cell.App<Int>>().map { it.app }
                val more = drawn.filterIsInstance<Cell.More>().sumOf { it.count }
                assertEquals(apps.take(shown.size), shown, at)
                assertEquals(count, shown.size + more, "no app dropped without a +N tile, $at")
                if (more > 0) assertEquals(Cell.More(more), drawn.last(), "+N is the last cell drawn, $at")
            }
        }
        assertTrue(tall > 100, "the sweep must reach Auto grids taller than 10 rows, saw $tall")
    }

    @Test fun `a tall Auto grid keeps its rows and leaves out the empty ones at the end`() {
        // 700 x 700 dp at Dense: Auto is 10 x 12 (gridFor), two rows more than Glance draws.
        val layout = widgetLayout(700f, 700f, null, null, Density.DENSE, line)
        assertEquals(10 to 12, layout.columns to layout.rows)
        val apps = (1..15).toList()
        assertEquals(2, drawnRows(apps, 10, 12).size, "15 apps: two rows, the rest of the widget stays empty")
        val rows = drawnRows((1..130).toList(), 10, 12)
        assertEquals(10, rows.size)
        assertEquals(Cell.More(31), rows.last().last(), "99 apps, then +31 on the 10th row")
        assertEquals(gridCells(apps, 9, 3), drawnRows(apps, 9, 3), "10 rows or fewer: every row, empty ones too")
    }

    @Test fun `a missing icon's tile shows the app's initial`() {
        assertEquals("W", appInitial("WhatsApp", "com.whatsapp"))
        assertEquals("K", appInitial("key Mapper", "io.github.sds100.keymapper"))
        assertEquals("7", appInitial("  7-Zip", "org.zip"))
        assertEquals("С", appInitial("Сonquistadorio", "com.game"), "a Cyrillic first letter stays itself")
        assertEquals("É", appInitial("éclair", "com.cake"))
        assertEquals("S", appInitial("😀 Smile", "com.smile"), "emoji are skipped, surrogate pairs and all")
        assertEquals("F", appInitial("", "com.example.foo"), "no label: the package's last part")
        assertEquals("?", appInitial("—", ""))
    }

    @Test fun `the +N tile says how many apps it opens`() {
        assertEquals("1 more app, opens the folder", moreDescription(1))
        assertEquals("4 more apps, opens the folder", moreDescription(4))
        assertEquals("1 app", appsPhrase(1))
        assertEquals("0 apps", appsPhrase(0))
        assertEquals("15 apps", appsPhrase(15))
    }

    @Test fun `a cell centres its content only when icon and label fit whole`() {
        // The tablet's Large widget at Standard: 69 dp cells, a 50.3 dp icon.
        assertTrue(cellHolds(81.3f, 50.3f, 18.3f), "Wide: room for the label at 125 %")
        assertFalse(cellHolds(69f, 50.3f, 18.3f), "Large at 125 %: top-aligned, so only the label's foot is cut")
        assertTrue(cellHolds(69f, 50.3f, 14.6f), "Large at 100 %")
        assertTrue(cellHolds(40f, 40f, null))
        assertFalse(cellHolds(40f, 41f, null))
    }

    // --- Designed states ---------------------------------------------------------------------------

    @Test fun `a widget shows its group, an empty state, or asks for a group`() {
        val chess = AppFacts("com.chess", "com.chess.Main", "Chess")
        val docs = AppFacts("com.docs", "com.docs.Main", "Docs")
        val mail = AppFacts("com.mail", "com.mail.Main", "Mail")
        val placements = listOf(
            Placement(chess, AppGroup.GAMES, "test"),
            Placement(docs, AppGroup.WORK, "test"),
            Placement(mail, AppGroup.WORK, "test"),
        )
        // The planner folds a one-app group into More, as the app does.
        val groups = GroupPlanner.plan(placements)

        assertEquals(WidgetShows.ChooseGroup, widgetShows(null, groups, placements), "no group saved: new or restored")
        assertEquals(WidgetShows.ChooseGroup, widgetShows("", groups, placements))
        assertEquals(WidgetShows.ChooseGroup, widgetShows("Games", groups, placements), "names are saved as AppGroup names")
        assertEquals(WidgetShows.ChooseGroup, widgetShows("PODCASTS", groups, placements), "a group that no longer exists")
        assertEquals(WidgetShows.ChooseGroup, widgetShows("HIDDEN", groups, placements), "Hidden is never a widget")

        val work = assertIs<WidgetShows.Apps>(widgetShows("WORK", groups, placements))
        assertEquals(listOf(docs, mail), work.group.apps)
        assertEquals(listOf(chess), assertIs<WidgetShows.Apps>(widgetShows("MORE", groups, placements)).group.apps)

        assertEquals(WidgetShows.NoApps(AppGroup.STUDY, inMore = 0), widgetShows("STUDY", groups, placements), "No apps in Study right now")
        assertEquals(WidgetShows.NoApps(AppGroup.GAMES, inMore = 1), widgetShows("GAMES", groups, placements), "its one app is in More")
    }

    @Test fun `a designed state drops the symbol, then the help line, then the button`() {
        fun fit(height: Float, titleLines: Int = 1, supportingLines: Int = 1) =
            stateFit(height, titleLines, titleLineDp = 21f, supportingLines = supportingLines, supportingLineDp = 18f, buttonDp = 48f)
        // Everything: 21 + (48 + 10) + (4 + 18) + (4 + 48) = 153 dp.
        assertEquals(StateFit(icon = true, supportingLines = 1, button = true, titleLines = 1), fit(153f))
        assertEquals(StateFit(icon = false, supportingLines = 1, button = true, titleLines = 1), fit(152f))
        assertEquals(StateFit(icon = false, supportingLines = 1, button = true, titleLines = 1), fit(95f))
        assertEquals(StateFit(icon = false, supportingLines = 0, button = true, titleLines = 1), fit(80f))
        assertEquals(StateFit(icon = false, supportingLines = 0, button = false, titleLines = 1), fit(40f), "title alone; the whole area is the button")
        assertEquals(StateFit(icon = true, supportingLines = 0, button = true, titleLines = 2), fit(200f, titleLines = 2, supportingLines = 0), "no help line")
        assertEquals(2, fit(50f, titleLines = 3).titleLines, "a title cut to the lines there is room for")
        assertEquals(1, fit(5f, titleLines = 2).titleLines, "the title always keeps one line")
    }

    @Test fun `line count wraps at spaces, and long words across lines`() {
        val tenPerChar: (String) -> Float = { it.length * 10f }
        assertEquals(1, lineCount("Choose a group", 200f, tenPerChar))
        assertEquals(1, lineCount("Choose a group", 140f, tenPerChar), "exactly fits")
        assertEquals(2, lineCount("Choose a group", 139f, tenPerChar))
        assertEquals(3, lineCount("Choose a group", 55f, tenPerChar), "\"Choose\" alone needs two lines")
        assertEquals(2, lineCount("No apps in Games right now", 160f, tenPerChar), "\"No apps in Games\" is exactly 160")
        assertEquals(3, lineCount("No apps in Games right now", 140f, tenPerChar))
        assertEquals(0, lineCount("   ", 100f, tenPerChar))
        assertEquals(3, lineCount("Choose a group", 0f, tenPerChar))
    }

    @Test fun `the fit note wins the header when both don't fit`() {
        assertTrue(headerShowsPill(roomDp = 300f, pillDp = 60f, noteDp = null), "no note: always the pill")
        assertTrue(headerShowsPill(roomDp = 10f, pillDp = 60f, noteDp = null), "a long title shortens instead")
        assertTrue(headerShowsPill(roomDp = 238f, pillDp = 60f, noteDp = 170f), "60 + 8 + 170")
        assertFalse(headerShowsPill(roomDp = 237f, pillDp = 60f, noteDp = 170f))
    }

    @Test fun `the fit note never pushes the edit button out of the header`() {
        assertNull(noteWidth(roomDp = 200f, textDp = 170f), "fits: its own width")
        assertNull(noteWidth(roomDp = 170f, textDp = 170f))
        assertEquals(120f, noteWidth(roomDp = 120f, textDp = 170f), "cut to the room left")
        assertEquals(24f, noteWidth(roomDp = 24f, textDp = 170f))
        assertEquals(0f, noteWidth(roomDp = 23f, textDp = 170f), "under 24 dp: the icon alone")
        assertEquals(0f, noteWidth(roomDp = -10f, textDp = 170f))
    }

    @Test fun `widget intents are told apart by widget, so two widgets of one group keep their own ids`() {
        assertEquals("group-widget-42-panel", widgetIntentId(42, "panel"))
        assertTrue(widgetIntentId(42, "panel") != widgetIntentId(49, "panel"), "widgets 42 and 49")
        assertTrue(widgetIntentId(42, "panel") != widgetIntentId(42, "edit"), "panel and Edit widget")
    }

    @Test fun `old widgets keep their group only after an update`() {
        assertTrue(isUpdate(firstInstallTime = 1_000L, lastUpdateTime = 2_000L))
        assertFalse(isUpdate(firstInstallTime = 1_000L, lastUpdateTime = 1_000L), "a first install has no old widgets")
    }

    @Test fun `the once-only group step waits until it can tell, and a first install is done at once`() {
        assertEquals(LegacyStep.Keep("STUDY"), legacyStep(updated = true, firstGroup = "STUDY"))
        assertEquals(LegacyStep.FirstInstall, legacyStep(updated = false, firstGroup = "STUDY"), "a first install has no old widgets")
        assertEquals(LegacyStep.FirstInstall, legacyStep(updated = false, firstGroup = null))
        // Marked done here, widgets 42 and 49 would ask for a group for good: try again on the next draw.
        assertEquals(LegacyStep.NotYet, legacyStep(updated = true, firstGroup = null), "no group to give yet")
        assertEquals(LegacyStep.NotYet, legacyStep(updated = null, firstGroup = "STUDY"), "install times unreadable")
    }

    @Test fun `keeping old groups is unfinished after a failed write, and never swallows cancellation`(): Unit = runBlocking {
        val kept = mutableListOf<Int>()
        assertTrue(keepAll(listOf(42, 49), keep = { kept += it }, onError = { _, _ -> }))
        assertEquals(listOf(42, 49), kept)

        kept.clear()
        val failed = mutableListOf<Int>()
        val done = keepAll(listOf(42, 49), keep = { if (it == 42) throw IOException("disk full") else kept += it }, onError = { id, _ -> failed += id })
        assertFalse(done, "widget 42 still has no group: not done, so the next draw tries again")
        assertEquals(listOf(42), failed)
        assertEquals(listOf(49), kept, "the other widgets still get theirs")

        // A cancelled session must not reach "done" with widgets left without their group.
        assertFailsWith<CancellationException> {
            keepAll(listOf(42, 49), keep = { throw CancellationException("session closed") }, onError = { _, _ -> })
        }
    }

    // --- Glass -------------------------------------------------------------------------------------

    @Test fun `the wallpaper picks the glass, and dark is the default`() {
        assertEquals(WidgetGlass.DARK, widgetGlass(null), "hints that can't be read")
        assertEquals(WidgetGlass.DARK, widgetGlass(0), "no hints")
        assertEquals(WidgetGlass.LIGHT, widgetGlass(WallpaperColors.HINT_SUPPORTS_DARK_TEXT), "a wallpaper that asks for dark text")
        assertEquals(
            WidgetGlass.LIGHT,
            widgetGlass(WallpaperColors.HINT_SUPPORTS_DARK_TEXT or WallpaperColors.HINT_SUPPORTS_DARK_THEME),
            "other hints beside it don't hide it",
        )
        assertEquals(WidgetGlass.DARK, widgetGlass(WallpaperColors.HINT_SUPPORTS_DARK_THEME), "dark theme alone isn't dark text")
    }

    @Test fun `the fill is translucent white or black by tint, never fully transparent`() {
        assertEquals(Color.White.copy(alpha = 0.52f), glassFill(WidgetGlass.LIGHT))
        assertEquals(Color.Black.copy(alpha = 0.55f), glassFill(WidgetGlass.DARK))
        for (glass in WidgetGlass.entries) {
            assertTrue(glassFill(glass).alpha in 0.4f..0.7f, "$glass: translucent, neither transparent nor solid")
        }
    }

    @Test fun `Match wallpaper colours mixes 20 per cent of the wallpaper's colour into the fill and keeps its alpha`() {
        // Oklab mixes computed outside Compose (CSS Color 4's matrices in Python): 20 % of the colour into
        // black (dark glass) or white (light glass). The alphas are 55 % (0x8C) and 52 % (0x85) of 255.
        val cases = listOf(
            MixCase(WidgetGlass.DARK, Color.White, expected = 0xFF161616.toInt(), tolerance = 1),
            MixCase(WidgetGlass.DARK, Color(0xFFE53935), expected = 0xFF120101.toInt(), tolerance = 2),
            MixCase(WidgetGlass.LIGHT, Color.Black, expected = 0xFFBEBEBE.toInt(), tolerance = 1),
            MixCase(WidgetGlass.LIGHT, Color(0xFF3366CC), expected = 0xFFD4E1F7.toInt(), tolerance = 2),
        )
        for (case in cases) {
            val fill = glassFill(case.glass, case.wallpaper).toArgb()
            val at = "${case.glass} glass, wallpaper colour #%06X".format(case.wallpaper.toArgb() and 0xFFFFFF)
            assertClose(case.expected, fill, at, case.tolerance)
            assertEquals(if (case.glass == WidgetGlass.DARK) 0x8C else 0x85, (fill ushr 24) and 0xFF, "$at: the alpha is the glass's own")
        }
        assertEquals(glassFill(WidgetGlass.DARK), glassFill(WidgetGlass.DARK, null), "no wallpaper colour: the plain fill")
    }

    @Test fun `each glass has one fixed set of inks`() {
        val dark = glassPalette(WidgetGlass.DARK)
        assertEquals(Color.White, dark.text, "dark glass: white text and icons")
        assertEquals(Color.White.copy(alpha = 0.86f), dark.secondary, "dark glass: secondary text, white at 86 per cent")
        assertEquals(Color.White.copy(alpha = 0.14f), dark.chip, "dark glass: the pill and the +N tile, white at 14 per cent")
        val light = glassPalette(WidgetGlass.LIGHT)
        assertEquals(Color(0xFF1B1C1C), light.text, "light glass: dark text and icons")
        assertEquals(Color(0xFF34343E), light.secondary, "light glass: secondary text")
        assertEquals(Color.Black.copy(alpha = 0.08f), light.chip, "light glass: the pill and the +N tile, black at 8 per cent")
        // The main action (Choose) is the text colour turned over, so it reads on its own button.
        assertAtLeast(4.5f, dark.onAction, dark.action, "Choose on dark glass")
        assertAtLeast(4.5f, light.onAction, light.action, "Choose on light glass")
    }

    @Test fun `text stays readable on the glass, even over the worst wallpaper for each tint`() {
        // Dark glass is at its brightest over pure white: white text on 55 per cent black composited over white.
        val darkOverWhite = glassFill(WidgetGlass.DARK).compositeOver(Color.White)
        assertAtLeast(4.5f, glassPalette(WidgetGlass.DARK).text, darkOverWhite, "white text on dark glass over white")
        // Light glass is at its darkest over pure black: #1B1C1C text on 52 per cent white composited over black.
        val lightOverBlack = glassFill(WidgetGlass.LIGHT).compositeOver(Color.Black)
        assertAtLeast(4.5f, glassPalette(WidgetGlass.LIGHT).text, lightOverBlack, "dark text on light glass over black")
    }

    @Test fun `the contrast check can fail`() {
        // Instrument check for the test above: the helper must tell 21 from 1, and a thinner fill must fall under 4.5.
        assertEquals(21f, contrast(Color.White, Color.Black), 0.01f)
        assertEquals(1f, contrast(Color.White, Color.White), 0.001f)
        val thin = Color.Black.copy(alpha = 0.4f).compositeOver(Color.White)
        assertTrue(contrast(Color.White, thin) < 4.5f, "40 per cent black over white is too thin for white text: ${contrast(Color.White, thin)}")
        // And the worst cases are the greys they should be: 45 per cent white (#737373) and 52 per cent white (#858585).
        assertClose(0xFF737373.toInt(), glassFill(WidgetGlass.DARK).compositeOver(Color.White).toArgb(), "dark glass over white")
        assertClose(0xFF858585.toInt(), glassFill(WidgetGlass.LIGHT).compositeOver(Color.Black).toArgb(), "light glass over black")
    }

    @Test fun `the glass drawables are an edge and a sheen in the system's corner radius`() {
        val res = resDir()
        for ((name, edge, sheen) in listOf(Triple("dark", "#24FFFFFF", "#1AFFFFFF"), Triple("light", "#99FFFFFF", "#4DFFFFFF"))) {
            val root = parse(File(res, "drawable/widget_glass_$name.xml")).documentElement
            assertEquals("layer-list", root.tagName, name)
            val shapes = root.getElementsByTagName("shape")
            assertEquals(2, shapes.length, "$name: an edge and a sheen")
            for (index in 0 until shapes.length) {
                val corners = (shapes.item(index) as Element).first("corners")
                assertEquals("@android:dimen/system_app_widget_background_radius", corners.getAttribute("android:radius"), "$name shape $index")
            }
            val edgeShape = shapes.item(0) as Element
            assertEquals("1dp", edgeShape.first("stroke").getAttribute("android:width"), "$name edge width")
            assertEquals(edge, edgeShape.first("stroke").getAttribute("android:color"), "$name edge")
            assertEquals("#00000000", edgeShape.first("solid").getAttribute("android:color"), "$name: nothing inside the edge")
            val gradient = (shapes.item(1) as Element).first("gradient")
            assertEquals("270", gradient.getAttribute("android:angle"), "$name sheen: top to bottom")
            assertEquals(sheen, gradient.getAttribute("android:startColor"), "$name sheen")
            assertEquals("#00FFFFFF", gradient.getAttribute("android:centerColor"), "$name sheen centre")
            assertEquals("#00FFFFFF", gradient.getAttribute("android:endColor"), "$name sheen end")
            assertEquals("0.35", gradient.getAttribute("android:centerY"), "$name sheen fades out by 35 per cent")
        }
    }

    // --- Widget picker -----------------------------------------------------------------------------

    @Test fun `the picker previews are translucent dark glass in the system's corner radius`() {
        val res = resDir()
        val surface = parse(File(res, "drawable/widget_preview_surface.xml")).documentElement
        assertEquals("#8C000000", surface.first("solid").getAttribute("android:color"), "surface fill")
        assertEquals("#24FFFFFF", surface.first("stroke").getAttribute("android:color"), "surface edge, white at 14 per cent")
        assertEquals("@android:dimen/system_app_widget_background_radius", surface.first("corners").getAttribute("android:radius"))
        for (size in listOf("small", "wide", "large")) {
            // The image, in the light and the dark theme: the glass and its edge are the first two paths.
            for (folder in listOf("drawable", "drawable-night")) {
                val paths = parse(File(res, "$folder/widget_preview_$size.xml")).getElementsByTagName("path")
                assertEquals("#8C000000", (paths.item(0) as Element).getAttribute("android:fillColor"), "$folder $size glass")
                assertEquals("#24FFFFFF", (paths.item(1) as Element).getAttribute("android:strokeColor"), "$folder $size edge")
            }
            // The layout's text sits on the chips, so it's white.
            val texts = parse(File(res, "layout/widget_preview_$size.xml")).getElementsByTagName("TextView")
            assertEquals(3, texts.length, "$size: the group, its count and the +N tile")
            for (index in 0 until texts.length) {
                assertEquals("#FFFFFFFF", (texts.item(index) as Element).getAttribute("android:textColor"), "$size text $index")
            }
        }
    }

    @Test fun `each size has a picker preview, an image and its own description`() {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val strings = parse(File(res, "values/strings.xml")).getElementsByTagName("string").let { nodes ->
            (0 until nodes.length).associate { nodes.item(it).attributes.getNamedItem("name").nodeValue to nodes.item(it).textContent }
        }
        val descriptions = mutableSetOf<String>()
        for ((file, size) in listOf("group_widget_info" to "wide", "group_widget_small" to "small", "group_widget_large" to "large")) {
            val provider = parse(File(res, "xml/$file.xml")).documentElement
            fun attr(name: String) = provider.getAttribute("android:$name")
            assertEquals("@layout/widget_preview_$size", attr("previewLayout"), file)
            assertEquals("@drawable/widget_preview_$size", attr("previewImage"), file)
            assertTrue(File(res, "layout/widget_preview_$size.xml").isFile, "$file layout")
            assertTrue(File(res, "drawable/widget_preview_$size.xml").isFile, "$file image")
            assertTrue(File(res, "drawable-night/widget_preview_$size.xml").isFile, "$file dark image")
            val description = strings[attr("description").removePrefix("@string/")]
            assertTrue(!description.isNullOrBlank(), "$file description")
            descriptions += description
            // The flow the widget relies on stays: Edit widget is its configuration screen, and it can be reconfigured.
            assertEquals("com.charan.padstudio.home.EditGroupWidgetActivity", attr("configure"), file)
            assertEquals("reconfigurable|configuration_optional", attr("widgetFeatures"), file)
        }
        assertEquals(3, descriptions.size, "one description per size")
    }

    @Test fun `the three receivers keep their names, one per picker size`() {
        assertEquals(
            listOf(
                "com.charan.padstudio.home.GroupWidgetSmallReceiver",
                "com.charan.padstudio.home.GroupWidgetReceiver",
                "com.charan.padstudio.home.GroupWidgetLargeReceiver",
            ),
            WidgetSize.entries.map { it.receiver.name },
        )
        assertEquals(listOf("group", "density", "grid"), listOf(GroupWidget.GROUP, GroupWidget.DENSITY, GroupWidget.GRID).map { it.name })
    }

    private fun parse(file: File) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    private fun assertAtLeast(minimum: Float, foreground: Color, background: Color, what: String) {
        val ratio = contrast(foreground, background)
        assertTrue(ratio >= minimum, "$what: $ratio:1, needs $minimum:1")
    }

    /** Equal to within [tolerance] (1 by default) in each channel (Oklab round trips differ in the last bit). */
    private fun assertClose(expected: Int, actual: Int, what: String, tolerance: Int = 1) {
        for (shift in listOf(16, 8, 0)) {
            val e = (expected shr shift) and 0xFF
            val a = (actual shr shift) and 0xFF
            assertTrue(abs(e - a) <= tolerance, "$what: expected #%06X, was #%06X".format(expected and 0xFFFFFF, actual and 0xFFFFFF))
        }
    }

    /** A wallpaper colour mixed into a glass fill, and the fill's colour worked out outside Compose. */
    private class MixCase(val glass: WidgetGlass, val wallpaper: Color, val expected: Int, val tolerance: Int)

    private fun resDir(): File = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    /** The first descendant element with this tag. */
    private fun Element.first(tag: String): Element = getElementsByTagName(tag).item(0) as Element
}
