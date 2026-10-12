package com.charan.padstudio.home.kit

import com.charan.padstudio.core.home.Density
import com.charan.padstudio.core.home.GridSpec
import com.charan.padstudio.core.home.GroupPlanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The Default widget grid picker's logic (plan §6b; mock-homekit.html). Labels come from one 11 sp line at 1.0. */
class WidgetGridLogicTest {
    private val labelLine = 14.3f

    /** Widgets 42 and 49 as dumpsys showed them on 11 Oct: no grid key. Sizes are examples. */
    private val wide42 = PlacedWidget(42, WidgetKind.WIDE, group = "STUDY", sizes = listOf(SizeDp(776f, 300f), SizeDp(412f, 560f)))
    private val large49 = PlacedWidget(49, WidgetKind.LARGE, group = "GAMES", sizes = listOf(SizeDp(776f, 600f), SizeDp(412f, 1000f)))

    // --- Which widgets the Default reaches -------------------------------------------------------

    @Test fun `a missing key, default and anything unreadable follow the Default`() {
        listOf(null, "default", "DEFAULT", "", "junk", "11x4", "3x4", "8X4", " 8x4", "8 × 4").forEach {
            assertTrue(followsDefault(it), "\"$it\" should follow the Default")
        }
        listOf("8x4", "4x2", "10x8", "auto", "AUTO", "Auto").forEach {
            assertFalse(followsDefault(it), "\"$it\" is the widget's own grid")
        }
    }

    @Test fun `followsDefault agrees with GridSpec_resolve, the rule the widget uses`() {
        val values = listOf(null, "default", "auto", "Auto", "8x4", "6x3", "11x4", "junk", "", "4x2", "4X2", "10x5 ")
        val marker = GridSpec(6, 3)
        for (value in values) {
            // A widget follows the Default exactly when changing the Default changes what it shows.
            val follows = GridSpec.resolve(value, marker.toString()) == marker && GridSpec.resolve(value, null) == null
            assertEquals(follows, followsDefault(value), "\"$value\"")
        }
    }

    @Test fun `the line counts the widgets set to Default, or leaves the count out while unknown`() {
        assertEquals("Applies to all widgets set to Default", defaultScopeLine(null))
        assertEquals("Applies to all widgets set to Default (2)", defaultScopeLine(listOf(wide42, large49)))
        assertEquals("Applies to all widgets set to Default (1)", defaultScopeLine(listOf(wide42, large49.copy(grid = "8x4"))))
        assertEquals("Applies to all widgets set to Default (1)", defaultScopeLine(listOf(wide42, large49.copy(grid = "auto"))))
        assertEquals("Applies to all widgets set to Default (2)", defaultScopeLine(listOf(wide42.copy(grid = "default"), large49.copy(grid = "nonsense"))))
        assertEquals("Applies to all widgets set to Default (0)", defaultScopeLine(emptyList()))
        assertNull(defaultCount(null))
    }

    // --- The size shown now ----------------------------------------------------------------------

    @Test fun `landscape takes the widest reported shape, portrait the tallest, unknown sizes are ignored`() {
        val sizes = listOf(SizeDp(412f, 560f), SizeDp(0f, 0f), SizeDp(776f, 300f))
        assertEquals(SizeDp(776f, 300f), sizeNow(sizes, landscape = true))
        assertEquals(SizeDp(412f, 560f), sizeNow(sizes, landscape = false))
        assertEquals(SizeDp(300f, 200f), sizeNow(listOf(SizeDp(300f, 200f)), landscape = false))
        assertNull(sizeNow(listOf(SizeDp(0f, 0f)), landscape = true))
        assertNull(sizeNow(emptyList(), landscape = true))
    }

    @Test fun `the preview is the biggest widget set to Default, else an example that says why`() {
        assertEquals(PreviewTarget(EXAMPLE_WIDGET, null, ExampleReason.NOT_READ), previewTarget(null, landscape = true))
        assertEquals(ExampleReason.NONE_PLACED, previewTarget(emptyList(), landscape = true).example)
        assertEquals(ExampleReason.NONE_ON_DEFAULT, previewTarget(listOf(wide42.copy(grid = "8x4")), landscape = true).example)
        assertEquals(ExampleReason.NO_SIZES, previewTarget(listOf(wide42.copy(sizes = emptyList())), landscape = true).example)

        val both = previewTarget(listOf(wide42, large49), landscape = true)
        assertSame(large49, both.widget)
        assertEquals(SizeDp(776f, 600f), both.size)
        assertNull(both.example)

        // A bigger widget with its own grid isn't what the Default changes.
        val ownGrid = previewTarget(listOf(wide42, large49.copy(grid = "10x5")), landscape = true)
        assertSame(wide42, ownGrid.widget)
        assertEquals(SizeDp(776f, 300f), ownGrid.size)
    }

    @Test fun `the preview says where its size comes from`() {
        val real = previewTarget(listOf(wide42), landscape = true)
        assertEquals("On your Wide widget (Study), 776 × 300 dp as HyperOS reports it.", previewSourceLine(real, "Study"))
        assertEquals("On your Wide widget, 776 × 300 dp as HyperOS reports it.", previewSourceLine(real, null))
        assertEquals("On an example widget, 472 × 252 dp.", previewSourceLine(previewTarget(null, true), null))
        assertEquals(
            "On an example widget, 472 × 252 dp. Add a group widget to preview your own.",
            previewSourceLine(previewTarget(emptyList(), true), null),
        )
        assertEquals(
            "On an example widget, 472 × 252 dp. HyperOS hasn't reported your widgets' sizes.",
            previewSourceLine(previewTarget(listOf(wide42.copy(sizes = emptyList())), true), null),
        )
    }

    @Test fun `a widget's own density is used for its Auto preview`() {
        assertEquals(Density.DENSE, densityFor(wide42.copy(density = "DENSE"), Density.COMPACT))
        assertEquals(Density.COMPACT, densityFor(wide42.copy(density = "HUGE"), Density.COMPACT))
        assertEquals(Density.CALM, densityFor(null, Density.CALM))
    }

    @Test fun `a widget has a density of its own only when it saved a valid one`() {
        assertEquals(Density.DENSE, ownDensity(wide42.copy(density = "DENSE")))
        assertEquals(Density.CALM, ownDensity(wide42.copy(density = "CALM")))
        assertNull(ownDensity(wide42))
        assertNull(ownDensity(wide42.copy(density = "HUGE")))
        assertNull(ownDensity(wide42.copy(density = "dense")))
        assertNull(ownDensity(null))
        // The two agree: PadStudio's density only stands in where the widget has none.
        for (density in listOf(null, "DENSE", "HUGE")) {
            val widget = wide42.copy(density = density)
            assertEquals(ownDensity(widget) ?: Density.COMPACT, densityFor(widget, Density.COMPACT))
        }
    }

    // --- The chips -------------------------------------------------------------------------------

    @Test fun `the saved Default selects its chip, and anything unreadable selects Auto as the widgets show it`() {
        assertEquals(GridOption.Auto, selectedOption(null))
        assertEquals(GridOption.Auto, selectedOption("junk"))
        assertEquals(GridOption.Auto, selectedOption("11x4"))
        assertEquals(GridOption.Preset(GridSpec(8, 4)), selectedOption("8x4"))
        assertEquals(GridOption.Custom(GridSpec(7, 4)), selectedOption("7x4"))
    }

    @Test fun `the chips are Auto, the three presets and Custom, which shows a saved custom grid`() {
        assertEquals(listOf("Auto", "6 × 3", "8 × 4", "10 × 5", "Custom"), gridOptions(null).map(::optionLabel))
        assertEquals(listOf("Auto", "6 × 3", "8 × 4", "10 × 5", "Custom"), gridOptions("8x4").map(::optionLabel))
        assertEquals(listOf("Auto", "6 × 3", "8 × 4", "10 × 5", "Custom · 7 × 4"), gridOptions("7x4").map(::optionLabel))
        assertTrue(selectedOption("7x4") in gridOptions("7x4"))
        assertTrue(selectedOption("10x5") in gridOptions("10x5"))
    }

    @Test fun `each chip saves GridSpec's stored form, which reads back as the same chip`() {
        assertNull(storedValue(GridOption.Auto))
        assertEquals("6x3", storedValue(GridOption.Preset(GridSpec(6, 3))))
        assertEquals("7x4", storedValue(GridOption.Custom(GridSpec(7, 4))))
        for (option in gridOptions("9x2").filter { it != GridOption.Custom(null) }) {
            assertEquals(option, selectedOption(storedValue(option)), "$option")
        }
    }

    @Test fun `Custom starts from the saved grid, else from what the preview shows, kept in range`() {
        val shown = GroupPlanner.layoutFor(472f, 252f, Density.COMPACT, null, labelLine)
        assertEquals(GridSpec(7, 4), customStart("7x4", shown))
        assertEquals(GridSpec(7, 3), customStart(null, shown))
        val tiny = GroupPlanner.layoutFor(150f, 120f, Density.CALM, null, labelLine)
        assertEquals(GridSpec(4, 2), customStart(null, tiny))
    }

    // --- Refusals --------------------------------------------------------------------------------

    @Test fun `a preset no widget set to Default can show is refused, by layoutFor's 48 dp rule`() {
        val small = PlacedWidget(1, WidgetKind.WIDE, sizes = listOf(SizeDp(472f, 252f)))
        assertEquals(listOf(GridSpec(10, 5)), refusedPresets(listOf(small), landscape = true, Density.COMPACT, labelLine))
        // One widget big enough is enough: the others show the largest grid that fits, with a note.
        assertEquals(emptyList(), refusedPresets(listOf(small, large49), landscape = true, Density.COMPACT, labelLine))
        // A big widget with its own grid doesn't count.
        assertEquals(listOf(GridSpec(10, 5)), refusedPresets(listOf(small, large49.copy(grid = "6x3")), true, Density.COMPACT, labelLine))
        // Nothing to measure against: nothing is refused.
        assertEquals(emptyList(), refusedPresets(null, true, Density.COMPACT, labelLine))
        assertEquals(emptyList(), refusedPresets(listOf(small.copy(sizes = emptyList())), true, Density.COMPACT, labelLine))
        assertNull(fitsSomeDefaultWidget(GridSpec(10, 5), emptyList(), true, Density.COMPACT, labelLine))
    }

    @Test fun `the refusal follows the fit boundary exactly (instrument check)`() {
        // 10 × 5 needs 500 × 288 dp: at the boundary it fits, a dp less in either direction and it doesn't.
        fun refused(width: Float, height: Float) =
            GridSpec(10, 5) in refusedPresets(listOf(PlacedWidget(7, WidgetKind.LARGE, sizes = listOf(SizeDp(width, height)))), true, Density.COMPACT, labelLine)
        assertFalse(refused(500f, 288f))
        assertTrue(refused(499f, 288f))
        assertTrue(refused(500f, 287f))
        assertEquals(500, GroupPlanner.widthNeededDp(10))
        assertEquals(288, GroupPlanner.heightNeededDp(5))
    }

    @Test fun `refusal wording follows the mock`() {
        assertNull(refusalLine(emptyList()))
        assertEquals(
            "10 × 5 needs bigger widgets: its spaces would be too small to tap. Resize one first.",
            refusalLine(listOf(GridSpec(10, 5))),
        )
        assertEquals(
            "8 × 4 and 10 × 5 need bigger widgets: their spaces would be too small to tap. Resize one first.",
            refusalLine(listOf(GridSpec(8, 4), GridSpec(10, 5))),
        )
        assertEquals(
            "6 × 3, 8 × 4 and 10 × 5 need bigger widgets: their spaces would be too small to tap. Resize one first.",
            refusalLine(GridSpec.PRESETS),
        )
    }

    @Test fun `the refusal detail is layoutFor's reason for a real widget, never for the example`() {
        val small = PlacedWidget(1, WidgetKind.WIDE, sizes = listOf(SizeDp(472f, 252f)))
        val target = previewTarget(listOf(small), landscape = true)
        assertEquals(
            "On your biggest widget set to Default: 10 columns need a 500 dp wide widget; this one is 472 dp. " +
                "5 rows need a 288 dp tall widget; this one is 252 dp.",
            refusalDetail(GridSpec(10, 5), target, Density.COMPACT, labelLine),
        )
        assertNull(refusalDetail(GridSpec(8, 4), target, Density.COMPACT, labelLine))
        assertNull(refusalDetail(GridSpec(10, 5), previewTarget(null, true), Density.COMPACT, labelLine))
    }

    // --- The preview's words ---------------------------------------------------------------------

    @Test fun `the caption gives layoutFor's grid, its spaces and whether names show`() {
        val exact = GroupPlanner.layoutFor(472f, 252f, Density.COMPACT, GridSpec(8, 4), labelLine)
        assertEquals("8 × 4 · 32 spaces · names hidden", previewCaption(exact, GridSpec(8, 4), Density.COMPACT))
        val auto = GroupPlanner.layoutFor(472f, 252f, Density.COMPACT, null, labelLine)
        assertEquals("Auto at Standard: 7 × 3 · 21 spaces · names shown", previewCaption(auto, null, Density.COMPACT))
        val trimmed = GroupPlanner.layoutFor(472f, 252f, Density.COMPACT, GridSpec(10, 5), labelLine)
        assertEquals("9 × 4 · 36 spaces · names hidden", previewCaption(trimmed, GridSpec(10, 5), Density.COMPACT))
        assertEquals("10 × 5 doesn't fit · showing 9 × 4", trimmed.note)
        val roomy = GroupPlanner.layoutFor(776f, 600f, Density.COMPACT, GridSpec(6, 3), labelLine)
        assertTrue(previewCaption(roomy, GridSpec(6, 3), Density.COMPACT).endsWith("names shown"))
        val one = GroupPlanner.layoutFor(80f, 120f, Density.CALM, null, labelLine)
        assertEquals("Auto at Roomy: 1 × 1 · 1 space · names shown", previewCaption(one, null, Density.CALM))
    }

    @Test fun `the Auto caption names a widget that has its own density, and only then`() {
        val dense = GroupPlanner.layoutFor(776f, 300f, Density.DENSE, null, labelLine)
        val own = wide42.copy(density = "DENSE")
        assertEquals("Your Study widget uses Dense: 10 × 4 · 40 spaces · names hidden", previewCaption(dense, null, Density.DENSE, own, "Study"))
        assertEquals("Your widget uses Dense: 10 × 4 · 40 spaces · names hidden", previewCaption(dense, null, Density.DENSE, own, null))
        // No density of its own, or one that isn't a density: the widget follows PadStudio's, as Auto says.
        val standard = GroupPlanner.layoutFor(776f, 300f, Density.COMPACT, null, labelLine)
        assertEquals("Auto at Standard: 10 × 3 · 30 spaces · names shown", previewCaption(standard, null, Density.COMPACT, wide42, "Study"))
        assertEquals("Auto at Standard: 10 × 3 · 30 spaces · names shown", previewCaption(standard, null, Density.COMPACT, wide42.copy(density = "HUGE"), "Study"))
        // The example widget (no widget) is PadStudio's too.
        assertEquals("Auto at Standard: 10 × 3 · 30 spaces · names shown", previewCaption(standard, null, Density.COMPACT, null, "Study"))
        // An exact grid doesn't depend on the density, so its line doesn't mention one.
        val exact = GroupPlanner.layoutFor(776f, 300f, Density.DENSE, GridSpec(8, 4), labelLine)
        val line = previewCaption(exact, GridSpec(8, 4), Density.DENSE, own, "Study")
        assertTrue(line.startsWith("8 × 4 · 32 spaces · names "), line)
        assertEquals(previewCaption(exact, GridSpec(8, 4), Density.DENSE), line)
        assertFalse("Dense" in line || "Your" in line || "Auto" in line, line)
    }

    @Test fun `Custom says the largest grid that fits, or why none does`() {
        assertEquals("Up to 9 × 4 on this widget.", upToLine(SizeDp(472f, 252f)))
        assertEquals("Up to 10 × 8 on this widget.", upToLine(SizeDp(1200f, 1000f)))
        assertEquals("This widget is too small for an exact grid: 4 × 2 needs 212 × 144 dp.", upToLine(SizeDp(200f, 140f)))
    }

    @Test fun `Developer details name the saved Default and each widget`() {
        assertEquals("Saved default grid: none, so Auto.", savedDefaultLine(null))
        assertEquals("Saved default grid: 8x4.", savedDefaultLine("8x4"))
        assertEquals("Saved default grid: \"9 by 4\" isn't a grid, so widgets set to Default use Auto.", savedDefaultLine("9 by 4"))
        assertEquals("42 Wide · Default (no grid key) · group STUDY · 776 × 300 dp, 412 × 560 dp", widgetDetailLine(wide42))
        assertEquals("49 Large · own grid: 8x4 · group GAMES · 776 × 600 dp, 412 × 1000 dp", widgetDetailLine(large49.copy(grid = "8x4")))
        assertEquals("5 Small · own grid: Auto · no group saved · size not reported", widgetDetailLine(PlacedWidget(5, WidgetKind.SMALL, grid = "auto")))
        assertEquals("6 Unknown size · Default (\"x\" isn't a grid) · no group saved · size not reported", widgetDetailLine(PlacedWidget(6, null, grid = "x")))
    }
}
