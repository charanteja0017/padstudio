package com.charan.padstudio.setup

import com.charan.padstudio.HealthCheck
import com.charan.padstudio.core.pen.FactsFile
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupChecksTest {
    /** MainViewModel.healthChecks()'s eight titles, in its order. */
    private val vmTitles = listOf(
        "Key Mapper installed",
        "Key Mapper can see the pen",
        "Pen profile is current",
        "HyperOS keeps Key Mapper running",
        "Device facts imported",
        "Group widgets on the home screen",
        "Themed icon source",
        "PadStudio wallpaper",
    )

    /** The mock-up's states: everything ready but HyperOS (by hand) and the wallpaper (needs attention). */
    private val mockChecks = vmTitles.map { title ->
        val ok = when (title) {
            CheckTitles.HYPEROS_KEEPS_KEY_MAPPER -> null
            CheckTitles.WALLPAPER -> false
            else -> true
        }
        HealthCheck(title, ok, "detail of $title")
    }

    @Test fun `the eight checks group as Pen, Home and Looks in the view model's order`() {
        val groups = groupByArea(mockChecks) { it.title }
        assertEquals(listOf(CheckArea.PEN, CheckArea.HOME, CheckArea.LOOKS), groups.map { it.area })
        assertEquals(vmTitles.subList(0, 5), groups[0].items.map { it.title })
        assertEquals(listOf("Group widgets on the home screen"), groups[1].items.map { it.title })
        assertEquals(listOf("Themed icon source", "PadStudio wallpaper"), groups[2].items.map { it.title })
    }

    @Test fun `every check MainViewModel builds has an area, so nothing lands in Other by accident`() {
        // Read the titles from the source, so renaming a check there fails here instead of quietly moving it to Other.
        val source = listOf("src/main/kotlin", "app/src/main/kotlin", "padstudio/app/src/main/kotlin")
            .map { File(it, "com/charan/padstudio/MainViewModel.kt") }
            .firstOrNull { it.isFile }
            ?: error("MainViewModel.kt not found from ${File(".").absoluteFile}")
        val titles = Regex("""HealthCheck\(\s*(?:title\s*=\s*)?"([^"]+)"""").findAll(source.readText()).map { it.groupValues[1] }.toList()
        assertEquals(vmTitles, titles, "MainViewModel.healthChecks() changed: update CheckTitles and this list")
        titles.forEach { assertTrue(checkArea(it) != CheckArea.OTHER, "\"$it\" has no area") }
    }

    @Test fun `a title Setup doesn't know goes to Other instead of being dropped`() {
        val extra = HealthCheck("Pen battery above 20 %", false, "Charge the pen.")
        val groups = groupByArea(mockChecks + extra) { it.title }
        assertEquals(listOf(CheckArea.PEN, CheckArea.HOME, CheckArea.LOOKS, CheckArea.OTHER), groups.map { it.area })
        assertEquals(listOf(extra), groups.last().items)
        assertEquals(9, groups.sumOf { it.items.size }, "every check is shown once")
        // Its words are the view model's own, and it still counts.
        assertEquals("Charge the pen.", plainWords(extra.title, extra.ok, extra.detail, CheckFacts()))
        assertEquals("6 of 9 ready", tally((mockChecks + extra).map { it.ok }).headline)
    }

    @Test fun `titles match ignoring case and surrounding spaces, and empty areas are left out`() {
        assertEquals(CheckArea.LOOKS, checkArea("  padstudio WALLPAPER "))
        assertTrue(isCheck("key mapper installed", CheckTitles.KEY_MAPPER_INSTALLED))
        assertFalse(isCheck("Key Mapper", CheckTitles.KEY_MAPPER_INSTALLED))
        val onlyLooks = groupByArea(listOf("Themed icon source", "PadStudio wallpaper")) { it }
        assertEquals(listOf(AreaGroup(CheckArea.LOOKS, listOf("Themed icon source", "PadStudio wallpaper"))), onlyLooks)
        assertEquals(emptyList(), groupByArea(emptyList<String>()) { it })
    }

    @Test fun `the ring counts only ready checks, as the mock-up's 6 of 8`() {
        val all = tally(mockChecks.map { it.ok })
        assertEquals(SetupTally(ready = 6, attention = 1, byHand = 1), all)
        assertEquals("6 of 8 ready", all.headline)
        assertEquals(0.75f, all.progress)
        assertEquals(
            listOf(
                CheckState.READY to "6 ready",
                CheckState.ATTENTION to "1 needs attention",
                CheckState.BY_HAND to "1 to check by hand",
            ),
            all.legend,
        )
        assertEquals("6 of 8 ready, 1 needs attention, 1 to check by hand", all.spoken)
        // Per area, as the cards' "4 of 5 ready": the HyperOS check by hand isn't ready.
        val pen = groupByArea(mockChecks) { it.title }.first()
        assertEquals("4 of 5 ready", tally(pen.items.map { it.ok }).headline)
    }

    @Test fun `a check by hand is never counted as ready, and the legend leaves out empty states`() {
        assertEquals(SetupTally(0, 0, 1), tally(listOf(null)))
        val two = tally(listOf(false, false, true))
        assertEquals(listOf(CheckState.READY to "1 ready", CheckState.ATTENTION to "2 need attention"), two.legend)
        val none = tally(emptyList())
        assertEquals("0 of 0 ready", none.headline)
        assertEquals(0f, none.progress)
    }

    @Test fun `the summary's issue is the first check that needs attention`() {
        assertEquals(CheckTitles.WALLPAPER, topIssue(mockChecks) { it.ok }?.title)
        val penFirst = mockChecks.map { if (it.title == CheckTitles.KEY_MAPPER_SEES_PEN) it.copy(ok = false) else it }
        assertEquals(CheckTitles.KEY_MAPPER_SEES_PEN, topIssue(penFirst) { it.ok }?.title)
        assertNull(topIssue(mockChecks.map { it.copy(ok = true) }) { it.ok })
        assertNull(topIssue(listOf(HealthCheck("HyperOS keeps Key Mapper running", null, ""))) { it.ok }, "by hand isn't an issue")
    }

    @Test fun `each check reads in plain words, with the view model's detail when a fact is missing`() {
        val known = CheckFacts(
            keyMapperVersion = "4.6.1-foss",
            sentText = "Sent to Key Mapper 14:05",
            widgetsPlaced = 2,
            lawnicons = true,
            facts = FactsFile.parse(FACTS).summary(),
        )
        fun words(title: String, ok: Boolean?, facts: CheckFacts = known) = plainWords(title, ok, "the detail", facts)

        assertEquals("Version 4.6.1-foss runs your pen’s buttons.", words(CheckTitles.KEY_MAPPER_INSTALLED, true))
        assertTrue("tools/install.sh keymapper" in words(CheckTitles.KEY_MAPPER_INSTALLED, false))
        assertEquals("Its accessibility service is turned on.", words(CheckTitles.KEY_MAPPER_SEES_PEN, true))
        assertTrue(words(CheckTitles.KEY_MAPPER_SEES_PEN, false).startsWith("Turn on its accessibility service."))
        assertEquals("Sent to Key Mapper 14:05.", words(CheckTitles.PROFILE_CURRENT, true))
        assertEquals(
            "Changes not sent yet. Use Send to Key Mapper on Today or Pen.",
            words(CheckTitles.PROFILE_CURRENT, false, known.copy(sentText = "Changes not sent yet", needsSend = true)),
        )
        assertEquals("the detail", words(CheckTitles.PROFILE_CURRENT, false, known.copy(sentText = null)))
        assertEquals(
            "PadStudio can’t check this, so turn on Autostart, set Battery saver to No restrictions and lock Key Mapper in Recents.",
            words(CheckTitles.HYPEROS_KEEPS_KEY_MAPPER, null),
        )
        assertEquals(
            "PadStudio knows the pen’s 4 controls, 1 camera app and the home-screen grid.",
            words(CheckTitles.FACTS_IMPORTED, true),
        )
        assertEquals("Imported.", words(CheckTitles.FACTS_IMPORTED, true, known.copy(facts = null)))
        assertEquals("2 placed on your home screen.", words(CheckTitles.WIDGETS_PLACED, true))
        assertEquals("the detail", words(CheckTitles.WIDGETS_PLACED, true, known.copy(widgetsPlaced = null)))
        assertTrue(words(CheckTitles.WIDGETS_PLACED, false).startsWith("None placed yet."))
        assertEquals("Lawnicons is installed and supplies themed icons.", words(CheckTitles.THEMED_ICONS, true))
        assertTrue(words(CheckTitles.THEMED_ICONS, true, known.copy(lawnicons = false)).startsWith("Android’s own monochrome icons"))
        assertEquals("the detail", words(CheckTitles.THEMED_ICONS, true, known.copy(lawnicons = null)))
        assertEquals("A PadStudio wallpaper is applied.", words(CheckTitles.WALLPAPER, true))
        assertEquals("Not applied yet, and it’s optional: pick one in Looks for Home, Lock or both.", words(CheckTitles.WALLPAPER, false))
    }

    @Test fun `no sentence claims what the app can't see`() {
        val known = CheckFacts("4.6.1", "Sent to Key Mapper 14:05", false, 2, true, FactsFile.parse(FACTS).summary())
        for (title in vmTitles) for (ok in listOf(true, false, null)) {
            val words = plainWords(title, ok, "", known).lowercase()
            listOf("verified", "running", "expert mode on", "probe-device.sh").forEach { banned ->
                assertFalse(banned in words, "\"$title\" ($ok) says \"$banned\": $words")
            }
        }
    }

    @Test fun `the facts summary and its recorded date come from the file`() {
        val summary = FactsFile.parse(FACTS).summary()
        assertEquals(listOf("pinch", "slide_up", "slide_down", "double_tap"), summary.controls)
        assertEquals(1, summary.cameras)
        assertEquals(listOf("Xiaomi Focus Pen Pro Keyboard"), summary.penDevices)
        assertEquals(LocalDate.of(2026, 10, 11), summary.recordedOn)
        // An older file has no home screen block, so no date.
        val old = FactsFile.parse("""{"cameras": [{"packageName": "com.android.camera"}]}""").summary()
        assertNull(old.recordedOn)
        assertEquals("1 camera app", old.contents())
        assertEquals("no pen, camera or home-screen facts yet", FactsFile.parse("{}").summary().contents())
    }

    @Test fun `recorded dates are read leniently and never guessed`() {
        assertEquals(LocalDate.of(2026, 10, 11), recordedDate("2026-10-11"))
        assertEquals(LocalDate.of(2026, 10, 11), recordedDate(" 2026-10-11T09:30:00 "))
        assertEquals(LocalDate.of(2026, 10, 11), recordedDate("2026-10-11T09:30:00+05:30"))
        assertNull(recordedDate("11 Oct"))
        assertNull(recordedDate("2026-13-40"))
        assertNull(recordedDate(""))
        assertNull(recordedDate(null))
    }

    @Test fun `the device facts row says when they were recorded and how to refresh them with facts sh`() {
        assertEquals("Recorded 11 Oct 2026. Run tools/facts.sh on your computer to refresh.", factsRowText(true, "11 Oct 2026"))
        assertEquals("Imported. Run tools/facts.sh on your computer to refresh.", factsRowText(true, null))
        assertEquals("Not imported. Optional: run tools/facts.sh on your computer to add them.", factsRowText(false, null))
        assertEquals("Reading device-facts.json…", factsRowText(null, null))
    }

    @Test fun `the how-to names facts sh as the writer, and probe-device sh only as its input`() {
        val text = FACTS_HOW_TO.joinToString(" ")
        assertTrue("tools/facts.sh rebuilds docs/device/device-facts.json" in text)
        assertTrue("pushes it to PadStudio’s files/device-facts.json" in text)
        assertTrue("tools/facts.sh --no-push" in text)
        assertTrue("from the newest tools/probe-device.sh run" in text)
        assertFalse(Regex("""probe-device\.sh (writes|rebuilds|pushes)""").containsMatchIn(text))
    }

    @Test fun `the developer lines spell out the facts file`() {
        val lines = factsDetails(FactsFile.parse(FACTS)) { "KEY$it" }
        assertEquals(
            listOf(
                "device-facts.json, recorded 2026-10-11 (homeScreen.recordedAt).",
                "Pen: FOCUS_PEN_PRO, EXPERT.",
                "Controls: Squeeze KEY332 (scan 189) · Slide up KEY153 (scan 73) · Slide down KEY147 (scan 81) · Double tap KEY160 (scan 96).",
                "Pen devices: Xiaomi Focus Pen Pro Keyboard.",
                "Cameras: com.android.camera.",
                "Home screen: 6 × 4, names hidden, landscape.",
            ),
            lines,
        )
        assertEquals(listOf("device-facts.json, with no recorded date."), factsDetails(FactsFile.parse("{}")) { "$it" })
    }

    @Test fun `the subtitle counts the checks in words`() {
        assertEquals("Eight checks for your pen, home screen and looks.", setupSubtitle(8))
        assertEquals("One check for your pen, home screen and looks.", setupSubtitle(1))
        assertEquals("Checks for your pen, home screen and looks.", setupSubtitle(0))
        assertEquals("13 checks for your pen, home screen and looks.", setupSubtitle(13))
    }

    @Test fun `small wording helpers`() {
        assertEquals("", joinAnd(emptyList()))
        assertEquals("a", joinAnd(listOf("a")))
        assertEquals("a and b", joinAnd(listOf("a", "b")))
        assertEquals("a, b and c", joinAnd(listOf("a", "b", "c")))
        assertEquals("Open Key Mapper’s app info", typographic("Open Key Mapper's app info"))
    }

    private companion object {
        /** docs/device/device-facts.json as of 11 Oct 2026, trimmed: the four Focus Pen Pro controls, one camera, the home screen. */
        val FACTS = """
            {
              "cameras": [{"packageName": "com.android.camera"}],
              "penDevices": [{"name": "Xiaomi Focus Pen Pro Keyboard", "bus": 5, "vendor": 34, "product": 20609}],
              "controls": {
                "pinch": {"type": "evdev", "keyCode": 332, "scanCode": 189, "device": {"name": "Xiaomi Focus Pen Pro Keyboard", "bus": 5, "vendor": 34, "product": 20609}},
                "slide_up": {"type": "evdev", "keyCode": 153, "scanCode": 73, "device": {"name": "Xiaomi Focus Pen Pro Keyboard", "bus": 5, "vendor": 34, "product": 20609}},
                "slide_down": {"type": "evdev", "keyCode": 147, "scanCode": 81, "device": {"name": "Xiaomi Focus Pen Pro Keyboard", "bus": 5, "vendor": 34, "product": 20609}},
                "double_tap": {"type": "evdev", "keyCode": 160, "scanCode": 96, "device": {"name": "Xiaomi Focus Pen Pro Keyboard", "bus": 5, "vendor": 34, "product": 20609}}
              },
              "penKind": "FOCUS_PEN_PRO",
              "penMode": "EXPERT",
              "homeScreen": {"columns": 6, "rows": 4, "namesHidden": true, "orientation": "landscape", "recordedAt": "2026-10-11", "source": "Gate G5"}
            }
        """.trimIndent()
    }
}
