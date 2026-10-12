package com.charan.padstudio.home.kit

import com.charan.padstudio.core.home.AndroidCategory
import com.charan.padstudio.core.home.AppFacts
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.home.Categorizer
import com.charan.padstudio.core.home.CuratedApps
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.core.home.Flowerpot
import com.charan.padstudio.core.home.FlowerpotPot
import com.charan.padstudio.core.home.GroupPlanner
import com.charan.padstudio.core.home.Placement
import com.charan.padstudio.core.pen.FactsFile
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Home Kit's words (ADR-005 wording, plan §7 truthfulness) and Organize's lists. */
class HomeKitTextTest {
    private val today = LocalDate.of(2026, 10, 12)

    /** docs/device/home-grid.json as recorded at gate G5, inside a device-facts.json, through the real parser. */
    private val recorded = assertNotNull(
        FactsFile.parse(
            """
            {"penKind": "FOCUS_PEN_PRO", "homeScreen": {"columns": 6, "rows": 4, "namesHidden": true, "orientation": "landscape",
              "recordedAt": "2026-10-11",
              "source": "Gate G5 (docs/decisions/ADR-005-home-grid.md): icon-centre lattice on docs/test-logs/2026-10-11-g5-home-baseline.png; a recorded baseline, not live state",
              "hyperosGridSetting": false,
              "hyperosOptions": ["Home screen: Classic | With App drawer", "Fill cells of uninstalled apps", "Lock Home screen layout", "Don't show text", "Background colour (for compatible icons)"]}}
            """.trimIndent(),
        ).homeScreen,
    )

    // --- Home screen grid (G5-B) -----------------------------------------------------------------

    @Test fun `the recorded baseline reads as ADR-005 words it`() {
        assertEquals("Recorded 11 Oct: 6 × 4, names hidden", recordedLine(recorded, today))
        assertEquals("6 × 4", recordedReadout(recorded))
        assertEquals("Recorded 11 Oct: 6 × 4, names shown", recordedLine(recorded.copy(namesHidden = false), today))
    }

    @Test fun `the record's day, with the year only when it isn't this year`() {
        assertEquals("11 Oct", recordedDay("2026-10-11", today))
        assertEquals("11 Oct 2026", recordedDay("2026-10-11", LocalDate.of(2027, 1, 2)))
        assertEquals("3 Sep", recordedDay("2026-09-03", today))
        assertEquals("11 Oct", recordedDay("2026-10-11T14:09:00Z", today))
        assertEquals("last Tuesday", recordedDay(" last Tuesday ", today))
    }

    @Test fun `the baseline is never called live`() {
        assertEquals("Landscape · recorded baseline, not live", baselineTag("landscape"))
        assertEquals("Portrait · recorded baseline, not live", baselineTag("Portrait"))
        assertEquals("Recorded baseline, not live", baselineTag(null))
        assertEquals("Recorded baseline, not live", baselineTag("upside down"))
        val everything = listOf(recordedLine(recorded, today), NOT_RECORDED_LINE, NOT_RECORDED_HINT) +
            listOf("landscape", "portrait", null, "x").map(::baselineTag) +
            listOf(true, false, null).flatMap { listOfNotNull(hyperosGridLine(it), hyperosGridNote(it)) }
        for (line in everything) {
            assertFalse(Regex("""(?<!not )\blive\b""", RegexOption.IGNORE_CASE).containsMatchIn(line), "claims live state: $line")
        }
    }

    @Test fun `G5-B says HyperOS has no grid setting unless the record says it has one`() {
        assertEquals("HyperOS on this tablet has no grid setting.", hyperosGridLine(recorded.hyperosGridSetting))
        assertEquals("HyperOS on this tablet has no grid setting.", hyperosGridLine(null))
        assertNull(hyperosGridNote(false))
        assertEquals("HyperOS on this tablet has a grid setting.", hyperosGridLine(true))
        assertEquals("PadStudio can't read HyperOS's grid. Record it again after you change it.", hyperosGridNote(true))
    }

    @Test fun `the HyperOS links open the two recorded screens`() {
        assertEquals("com.miui.home.Setting", HyperOsScreen.HOME_SETTINGS.action)
        assertEquals("com.miui.home.action.ICON_CUSTOMIZE", HyperOsScreen.ICON_BACKGROUND.action)
        assertEquals(
            listOf("Don't show text", "Lock Home screen layout", "Classic or App drawer", "Icon background colour"),
            HyperOsLink.entries.map { it.label },
        )
        assertEquals(HyperOsScreen.ICON_BACKGROUND, HyperOsLink.ICON_BACKGROUND.screen)
        assertTrue(HyperOsLink.entries.filter { it != HyperOsLink.ICON_BACKGROUND }.all { it.screen == HyperOsScreen.HOME_SETTINGS })
    }

    @Test fun `a link that can't open says why`() {
        assertNull(unavailableLine(HyperOsScreen.HOME_SETTINGS, LinkState.READY))
        assertEquals(
            "Can't open HyperOS's Home screen settings: this tablet's launcher doesn't offer that screen.",
            unavailableLine(HyperOsScreen.HOME_SETTINGS, LinkState.MISSING),
        )
        assertEquals(
            "Can't open HyperOS's icon background setting: HyperOS doesn't let other apps open it.",
            unavailableLine(HyperOsScreen.ICON_BACKGROUND, LinkState.BLOCKED),
        )
        assertEquals(
            "Couldn't open HyperOS's Home screen settings: HyperOS refused the request.",
            openFailedLine(HyperOsScreen.HOME_SETTINGS, OpenFailure.REFUSED),
        )
        // ActivityNotFoundException after a successful resolve: the screen went away, nothing timed out.
        assertEquals(
            "Couldn't open HyperOS's icon background setting: the launcher no longer offers that screen.",
            openFailedLine(HyperOsScreen.ICON_BACKGROUND, OpenFailure.NOT_FOUND),
        )
    }

    // --- The widget-grid preview's density line --------------------------------------------------

    @Test fun `the Auto line says whose density it used`() {
        assertEquals("Auto at Standard", densityLead(Density.COMPACT, widgetOwns = false, groupTitle = "Study"))
        assertEquals("Auto at Roomy", densityLead(Density.CALM, widgetOwns = false, groupTitle = null))
        assertEquals("Your Study widget uses Dense", densityLead(Density.DENSE, widgetOwns = true, groupTitle = "Study"))
        assertEquals("Your Watch & Listen widget uses Roomy", densityLead(Density.CALM, widgetOwns = true, groupTitle = "Watch & Listen"))
        assertEquals("Your widget uses Standard", densityLead(Density.COMPACT, widgetOwns = true, groupTitle = null))
        // Instrument check: a widget's own density is never called Auto, and PadStudio's is never put on a widget.
        for (density in Density.entries) {
            assertFalse("Auto" in densityLead(density, widgetOwns = true, groupTitle = "Study"))
            assertFalse("widget" in densityLead(density, widgetOwns = false, groupTitle = "Study"))
        }
    }

    @Test fun `a Dense widget under a Standard control reads as the widget's, not as Auto`() {
        // The owner's case: the density control below the preview shows Standard, the previewed widget saved Dense.
        val widget = PlacedWidget(42, WidgetKind.WIDE, group = "STUDY", density = "DENSE", sizes = listOf(SizeDp(776f, 300f)))
        val control = Density.COMPACT
        val shown = densityFor(widget, control)
        assertEquals(Density.DENSE, shown)
        val layout = GroupPlanner.layoutFor(776f, 300f, shown, null, 14.3f)
        assertEquals(
            "Your Study widget uses Dense: 10 × 4 · 40 spaces · names hidden",
            previewCaption(layout, null, shown, widget, "Study"),
        )
        // Without a density of its own the same widget follows the control, and Auto is the right word.
        val following = widget.copy(density = null)
        val followed = densityFor(following, control)
        val followedLayout = GroupPlanner.layoutFor(776f, 300f, followed, null, 14.3f)
        assertTrue(previewCaption(followedLayout, null, followed, following, "Study").startsWith("Auto at Standard: "))
    }

    // --- Groups ----------------------------------------------------------------------------------

    @Test fun `counts and the Hidden line`() {
        assertEquals("1 app", appCount(1))
        assertEquals("12 apps", appCount(12))
        assertEquals("0 apps", appCount(0))
        assertEquals("Kept out of every widget. None yet.", hiddenLine(0))
        assertEquals("Kept out of every widget · 3 apps", hiddenLine(3))
        assertEquals("Add a small Study widget", addWidgetDescription(WidgetKind.SMALL, "Study"))
        assertEquals("Add a wide Shop & Pay widget", addWidgetDescription(WidgetKind.WIDE, "Shop & Pay"))
    }

    @Test fun `every reason the categoriser gives reads in plain words`() {
        val pot = Flowerpot.GROUP_OF.keys.first()
        val pots = listOf(FlowerpotPot(pot, setOf("org.example.potted"), emptyList(), emptyList(), emptyList()))
        val curated = CuratedApps.GROUP_OF.keys.first()
        val categorizer = Categorizer(pots, overrides = mapOf("org.example.moved" to AppGroup.WORK))
        val apps = listOf(
            app("org.example.moved"),
            app(curated),
            app("org.example.potted"),
            app("org.example.intent", intentCategories = setOf(pot)),
            app("org.example.game", isGame = true),
            app("org.example.video", androidCategory = AndroidCategory.VIDEO),
            app("org.example.weather", label = "Weather"),
            app("com.android.quiet", label = "Zz", isSystem = true),
            app("org.example.nothing", label = "Zz"),
        )
        val reasons = categorizer.placeAll(apps).map { it.reason }
        assertEquals(apps.size, reasons.toSet().size, "each case should give its own reason: $reasons")
        for (reason in reasons) {
            assertNotEquals(reason, placementReasonText(reason), "\"$reason\" isn't put in plain words")
        }
        assertEquals("Moved here by you", placementReasonText("your choice"))
        assertEquals("Something new", placementReasonText("Something new"))
    }

    @Test fun `Organize lists the groups as the widgets show them, then Hidden, always last`() {
        val notes = app("com.example.notes", label = "Notes")
        val books = app("com.example.books", label = "Books")
        val pdf = app("com.example.pdf", label = "PDF")
        val mail = app("com.example.mail", label = "Mail")
        val zed = app("com.example.zed", label = "zed")
        val alpha = app("com.example.alpha", label = "Alpha")
        val placements = listOf(
            Placement(notes, AppGroup.STUDY, "PadStudio list"),
            Placement(books, AppGroup.STUDY, "name"),
            Placement(pdf, AppGroup.READING, "PadStudio list"),
            Placement(mail, AppGroup.MORE, "no rule matched"),
            Placement(zed, AppGroup.HIDDEN, "your choice"),
            Placement(alpha, AppGroup.HIDDEN, "your choice"),
        )
        val groups = organizeGroups(GroupPlanner.plan(placements), placements)
        assertEquals(listOf(AppGroup.STUDY, AppGroup.MORE, AppGroup.HIDDEN), groups.map { it.group })
        // Reading has one app, so it shows in More, and the line says where it goes.
        val more = groups.first { it.group == AppGroup.MORE }
        assertEquals("On PadStudio's app list. In More until Reading has 2 apps.", more.apps.first { it.facts == pdf }.reason)
        assertEquals("No rule matched it", more.apps.first { it.facts == mail }.reason)
        assertEquals(listOf("Alpha", "zed"), groups.last().apps.map { it.facts.label })
        assertEquals("Moved here by you", groups.last().apps.first().reason)

        val none = organizeGroups(GroupPlanner.plan(placements.take(2)), placements.take(2))
        assertEquals(AppGroup.HIDDEN, none.last().group)
        assertTrue(none.last().apps.isEmpty())
    }

    @Test fun `Organize keeps the chosen group while it exists`() {
        val groups = listOf(OrganizeGroup(AppGroup.STUDY, emptyList()), OrganizeGroup(AppGroup.HIDDEN, emptyList()))
        assertEquals(AppGroup.HIDDEN, organizeSelection(groups, "HIDDEN")?.group)
        assertEquals(AppGroup.STUDY, organizeSelection(groups, "GAMES")?.group)
        assertEquals(AppGroup.STUDY, organizeSelection(groups, null)?.group)
        assertNull(organizeSelection(emptyList(), "STUDY"))
    }

    // --- Messages and the reset confirmation -----------------------------------------------------

    @Test fun `success messages carry the time`() {
        assertEquals("Moved Notes to Work · 14:05", movedMessage("Notes", AppGroup.WORK, "14:05"))
        assertEquals("Undid your move · 2:05 PM", resetMessage(1, "2:05 PM"))
        assertEquals("Undid your 3 moves · 14:05", resetMessage(3, "14:05"))
        assertEquals("Added a wide Study widget · 14:05", addedMessage(WidgetKind.WIDE, "Study", "14:05"))
        assertEquals("Added a widget · 14:05", addedMessage(null, null, "14:05"))
    }

    @Test fun `the reset confirmation says what comes back`() {
        assertEquals("1 app goes back to the group PadStudio picks for it. Widgets update.", resetBody(mapOf("a" to AppGroup.WORK)))
        assertEquals(
            "3 apps go back to the groups PadStudio picks for them. That includes 1 hidden app, which comes back. Widgets update.",
            resetBody(mapOf("a" to AppGroup.WORK, "b" to AppGroup.HIDDEN, "c" to AppGroup.GAMES)),
        )
        assertEquals(
            "2 apps go back to the groups PadStudio picks for them. That includes 2 hidden apps, which come back. Widgets update.",
            resetBody(mapOf("a" to AppGroup.HIDDEN, "b" to AppGroup.HIDDEN)),
        )
        assertEquals("No moves yet", movesLine(0))
        assertEquals("1 app moved by you", movesLine(1))
        assertEquals("4 apps moved by you", movesLine(4))
    }

    private fun app(
        packageName: String,
        label: String = packageName.substringAfterLast('.'),
        isGame: Boolean = false,
        isSystem: Boolean = false,
        androidCategory: Int? = null,
        intentCategories: Set<String> = emptySet(),
    ) = AppFacts(
        packageName = packageName,
        activityName = "$packageName.Main",
        label = label,
        androidCategory = androidCategory,
        isGame = isGame,
        isSystem = isSystem,
        intentCategories = intentCategories,
    )
}
