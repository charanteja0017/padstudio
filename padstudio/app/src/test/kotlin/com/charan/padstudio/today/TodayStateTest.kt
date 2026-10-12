package com.charan.padstudio.today

import com.charan.padstudio.HealthCheck
import com.charan.padstudio.core.home.AppFacts
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.core.home.GridSpec
import com.charan.padstudio.core.home.GroupPlanner
import com.charan.padstudio.core.home.PlannedGroup
import com.charan.padstudio.core.pen.Binding
import com.charan.padstudio.core.pen.DefaultProfiles
import com.charan.padstudio.core.pen.HomeScreenFacts
import com.charan.padstudio.core.pen.PenControl
import com.charan.padstudio.core.pen.PenIntent
import com.charan.padstudio.core.pen.PenKind
import com.charan.padstudio.core.pen.PenMode
import com.charan.padstudio.core.pen.PenProfile
import com.charan.padstudio.core.pen.PenStatus
import com.charan.padstudio.core.pen.Press
import com.charan.padstudio.data.IconStyle
import com.charan.padstudio.data.StudioConfig
import com.charan.padstudio.ui.components.StatusLevel
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodayStateTest {
    private val focusDefaults = DefaultProfiles.bindings(PenKind.FOCUS_PEN_PRO, PenMode.EXPERT)

    private fun status(listening: Boolean? = true, sent: String? = "abc", plan: String? = "abc", at: Long? = 1L, on: Boolean = true) =
        PenStatus(listening, sent, plan, at, on)

    // ---- Hero status -----------------------------------------------------------------------------------

    @Test fun `the status is built from the config, the plan hash and Key Mapper's service`() {
        val config = StudioConfig(appliedProfileHash = "bfa1ea74a187", appliedAt = 1_760_000_000_000, penEnabled = false)
        val status = todayPenStatus(listening = true, config = config, planHash = "bfa1ea74a187")
        assertEquals(PenStatus(true, "bfa1ea74a187", "bfa1ea74a187", 1_760_000_000_000, false), status)
        assertEquals(PenStatus.Sent.SENT, status.sent)
        assertEquals(PenStatus.PenActions.OFF_AS_REQUESTED, status.penActions)
        assertFalse(status.needsSend)
    }

    @Test fun `the hero's three lines are the pieces of configuredText`() {
        val text = heroText(status(), "Focus Pen Pro", PenMode.EXPERT, controlsSet = 4)
        assertEquals(HeroText("Your pen · Expert setup", "Focus Pen Pro", "4 controls set"), text)
        val basic = heroText(status(), "Two-button pen", PenMode.BASIC, controlsSet = 1)
        assertEquals(HeroText("Your pen · Basic setup", "Two-button pen", "1 control set"), basic)
    }

    @Test fun `without a profile the pen still titles the hero and the subtitle says it isn't ready`() {
        val text = heroText(status(plan = null), "Focus Pen Pro", PenMode.EXPERT, controlsSet = 4)
        assertEquals(HeroText("Your pen", "Focus Pen Pro", "Pen profile not ready"), text)
    }

    @Test fun `controls set counts only this pen's controls that have a trigger`() {
        assertEquals(3, controlsSet(PenKind.FOCUS_PEN_PRO, setOf("pinch", "slide_up", "double_tap", "upper")))
        assertEquals(1, controlsSet(PenKind.TWO_BUTTON, setOf("upper", "pinch")))
        assertEquals(0, controlsSet(PenKind.TWO_BUTTON, emptySet()))
    }

    @Test fun `the headline pill is bad when Key Mapper is off, unknown without a profile, warn when changes aren't sent`() {
        assertEquals(StatusLevel.Good, headlineLevel(status()))
        assertEquals(StatusLevel.Bad, headlineLevel(status(listening = false)))
        assertEquals(StatusLevel.Bad, headlineLevel(status(listening = false, plan = null)))
        assertEquals(StatusLevel.Unknown, headlineLevel(status(plan = null, sent = null)))
        assertEquals(StatusLevel.Warn, headlineLevel(status(plan = "new")))
        assertEquals(StatusLevel.Warn, headlineLevel(status(sent = null)))
        assertEquals(StatusLevel.Unknown, headlineLevel(status(listening = null)))
        // Pen actions off is only what was asked for: it doesn't colour the headline.
        assertEquals(StatusLevel.Good, headlineLevel(status(on = false)))
    }

    @Test fun `the sent time is the time today, and the day too on another day`() {
        val india = ZoneId.of("Asia/Kolkata")
        fun ms(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, india).toInstant().toEpochMilli()
        fun text(at: Long, now: Long, zone: ZoneId) = sentTimeText(
            at = at,
            now = now,
            zone = zone,
            time = { "T" + ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), zone).toLocalTime() },
            day = { ms, sameYear -> (if (sameYear) "D" else "DY") + ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), zone).toLocalDate() },
        )
        val now = ms(2026, 10, 12, 9, 30)
        assertEquals("T00:05", text(ms(2026, 10, 12, 0, 5), now, india))
        assertEquals("D2026-10-11, T23:55", text(ms(2026, 10, 11, 23, 55), now, india))
        assertEquals("DY2025-10-12, T09:30", text(ms(2025, 10, 12, 9, 30), now, india))
        // The day boundary is the tablet's: 00:05 on the 12th in India is still the 11th in UTC.
        assertEquals("D2026-10-11, T18:35", text(ms(2026, 10, 12, 0, 5), now, ZoneOffset.UTC))
    }

    // ---- Callout gestures ------------------------------------------------------------------------------

    @Test fun `the Focus Pen Pro callouts are double tap and squeeze with what they do everywhere else`() {
        val gestures = heroGestures(PenKind.FOCUS_PEN_PRO, focusDefaults)
        assertEquals(listOf("double_tap", "pinch"), gestures.map { it.controlId })
        assertEquals(listOf("Double tap", "Squeeze"), gestures.map { it.label })
        assertEquals(listOf("Last app", "Back"), gestures.map { it.action })
        assertEquals(listOf(PenIntent.LAST_APP, PenIntent.BACK), gestures.map { it.intent })
        assertEquals(listOf("double_tap", "squeeze"), gestures.map { it.clip })
        assertEquals(listOf("marker_double_tap", "marker_squeeze"), gestures.map { it.marker })
    }

    @Test fun `a gesture with nothing everywhere else falls back to Camera, then to Not set`() {
        val onlyCamera = listOf(Binding(DefaultProfiles.CAMERA.id, PenControl.PINCH.id, Press.SHORT, PenIntent.SHUTTER))
        val squeeze = heroGestures(PenKind.FOCUS_PEN_PRO, onlyCamera).single { it.controlId == "pinch" }
        assertEquals("Shutter in Camera", squeeze.action)
        assertEquals(PenIntent.SHUTTER, squeeze.intent)
        val tap = heroGestures(PenKind.FOCUS_PEN_PRO, onlyCamera).single { it.controlId == "double_tap" }
        assertEquals("Not set", tap.action)
        assertNull(tap.intent)
        // A double press everywhere else isn't the gesture's plain press.
        val doubleOnly = listOf(Binding(PenProfile.EVERYWHERE, PenControl.PINCH.id, Press.DOUBLE, PenIntent.SCREENSHOT))
        assertEquals("Not set", heroGestures(PenKind.FOCUS_PEN_PRO, doubleOnly).single { it.controlId == "pinch" }.action)
    }

    @Test fun `a two-button pen lists its buttons, which the model can't show`() {
        val gestures = heroGestures(PenKind.TWO_BUTTON, DefaultProfiles.bindings(PenKind.TWO_BUTTON, PenMode.BASIC))
        assertEquals(listOf("Upper button", "Lower button"), gestures.map { it.label })
        assertEquals(listOf("Screenshot", "Back"), gestures.map { it.action })
        assertTrue(gestures.all { it.clip == null && it.marker == null })
    }

    @Test fun `the bindings in force are the saved ones, else this pen's defaults`() {
        val saved = listOf(Binding("camera", "pinch", Press.SHORT, PenIntent.SCREENSHOT))
        assertEquals(saved, currentBindings(StudioConfig(bindings = saved)))
        assertEquals(focusDefaults, currentBindings(StudioConfig(penKind = PenKind.FOCUS_PEN_PRO, penMode = PenMode.EXPERT)))
    }

    // ---- Pen by app ------------------------------------------------------------------------------------

    @Test fun `each context's bindings read as one sentence, same actions sharing it`() {
        fun words(context: String) = bindingsInWords(focusDefaults.filter { it.contextId == context })
        assertEquals("Squeeze or double tap for a photo", words("camera"))
        assertEquals("Slide up for previous page, slide down for next page, double tap for the last app", words("reading"))
        assertEquals("Squeeze for play or pause, double tap for next track", words("media"))
        assertEquals("Squeeze for the app library, squeeze twice for the camera, double tap for the last app", words("home"))
        assertEquals("No pen actions set yet", words("notes_other"))
    }

    @Test fun `button presses read in control then press order, holds and doubles named`() {
        val expert = DefaultProfiles.bindings(PenKind.TWO_BUTTON, PenMode.EXPERT).filter { it.contextId == PenProfile.EVERYWHERE }
        assertEquals(
            "Upper button or hold upper button for a screenshot, upper button twice for the camera, lower button for back, " +
                "hold lower button for your notes app, lower button twice for recent apps",
            bindingsInWords(expert),
        )
    }

    @Test fun `every pen action has words`() {
        PenIntent.entries.forEach { assertTrue(actionWords(it).isNotBlank(), "$it") }
    }

    @Test fun `Pen by app lists installed contexts in order, pass-through notes last, with display titles`() {
        val contexts = listOf(
            DefaultProfiles.CAMERA.copy(packages = listOf("com.android.camera")),
            DefaultProfiles.READING.copy(packages = emptyList()),
            DefaultProfiles.NATIVE_NOTES.copy(packages = listOf("com.miui.creation")),
            DefaultProfiles.OTHER_NOTES.copy(packages = listOf("com.miui.notes", "com.google.android.keep")),
            DefaultProfiles.MEDIA.copy(packages = listOf("com.google.android.youtube")),
            DefaultProfiles.HOME,
        )
        val lines = penByApp(contexts, focusDefaults)
        assertEquals(listOf("Camera", "Notes", "Video & music", "Home screen", "Own pen settings"), lines.map { it.title })
        assertEquals("No pen actions set yet", lines.single { it.id == "notes_other" }.words)
        assertEquals("Leaves the pen to the app", lines.single { it.id == "notes_native" }.words)
        assertEquals(listOf("com.miui.notes", "com.google.android.keep"), lines.single { it.id == "notes_other" }.packages)
        // Core's labels are untouched: only the screen's titles differ.
        assertEquals("Other notes", DefaultProfiles.OTHER_NOTES.title)
        assertEquals("Notes with their own pen settings", DefaultProfiles.NATIVE_NOTES.title)
    }

    // ---- Actions ---------------------------------------------------------------------------------------

    @Test fun `a failed send says why, in the order applyToKeyMapper checks, with Fix or Retry`() {
        assertEquals(HeroNote.Failed("The pen profile isn't ready yet.", Remedy.RETRY, HeroAction.SEND), sendFailure(false, false, false))
        assertEquals(HeroNote.Failed("Key Mapper isn't installed.", Remedy.OPEN_SETUP, HeroAction.SEND), sendFailure(false, true, true))
        assertEquals(HeroNote.Failed("No pen buttons are recorded or measured yet.", Remedy.OPEN_PEN, HeroAction.SEND), sendFailure(true, true, false))
        assertEquals(Remedy.RETRY, sendFailure(true, true, true).remedy)
    }

    @Test fun `pause and resume are only requested, and say so with the time`() {
        assertEquals(HeroNote.Requested("Pause requested at 14:05. Key Mapper can't confirm it."), penRequestNote(false, false, true, "14:05"))
        assertEquals(HeroNote.Requested("Resume requested at 2:05 PM. Key Mapper can't confirm it."), penRequestNote(true, true, true, "2:05 PM"))
        val noKeyMapper = penRequestNote(false, true, false, "14:05")
        assertIs<HeroNote.Failed>(noKeyMapper)
        assertEquals(Remedy.OPEN_SETUP, noKeyMapper.remedy)
        assertEquals(HeroNote.Failed("The pen profile isn't ready yet.", Remedy.RETRY, HeroAction.PEN_ON_OFF), penRequestNote(false, true, true, "14:05"))
        assertEquals("Pause pen", penToggleLabel(true))
        assertEquals("Resume pen", penToggleLabel(false))
    }

    // ---- Home screen -----------------------------------------------------------------------------------

    @Test fun `the launcher grid is the recorded baseline with its day`() {
        val facts = HomeScreenFacts(columns = 6, rows = 4, namesHidden = true, recordedAt = "2026-10-11", source = "Gate G5")
        val day = recordedDayText(facts.recordedAt, LocalDate.of(2026, 10, 12)) { date, sameYear -> "${date.dayOfMonth} Oct" + if (sameYear) "" else " ${date.year}" }
        assertEquals("Launcher grid: 6 × 4, names hidden · recorded 11 Oct", launcherGridText(facts, day))
        assertEquals("Launcher grid: 6 × 5 · recorded 11 Oct", launcherGridText(facts.copy(rows = 5, namesHidden = false), day))
    }

    @Test fun `a recorded day from another year shows the year, and a non-date stays as recorded`() {
        val format: (LocalDate, Boolean) -> String = { date, sameYear -> if (sameYear) "${date.dayOfMonth}/${date.monthValue}" else "$date" }
        assertEquals("11/10", recordedDayText("2026-10-11", LocalDate.of(2026, 12, 31), format))
        assertEquals("2025-10-11", recordedDayText("2025-10-11", LocalDate.of(2026, 1, 1), format))
        assertEquals("11/10", recordedDayText("2026-10-11T17:47:00+05:30", LocalDate.of(2026, 10, 12), format))
        assertEquals("last week", recordedDayText("last week", LocalDate.of(2026, 10, 12), format))
    }

    @Test fun `the default grid reads as Home Kit names it, unreadable values as Auto`() {
        assertEquals("Auto", defaultGridText(null))
        assertEquals("8 × 4", defaultGridText("8x4"))
        assertEquals("Auto", defaultGridText("11x4"))
        assertEquals("Auto", defaultGridText("8 × 4"))
    }

    @Test fun `the preview widget is laid out by core at the mock's size, grown so an exact grid fits`() {
        val auto = previewWidget(null, Density.COMPACT, labelLineDp = 16f)
        assertEquals(412f to 222f, auto.widthDp to auto.heightDp)
        // Auto is today's gridFor on the size less the 20 dp padding.
        assertEquals(GroupPlanner.gridFor(392, 202, Density.COMPACT), auto.layout.columns to auto.layout.rows)
        val big = previewWidget(GridSpec(10, 5), Density.COMPACT, labelLineDp = 16f)
        assertEquals(500f to 288f, big.widthDp to big.heightDp)
        assertTrue(big.layout.fits)
        assertEquals(10 to 5, big.layout.columns to big.layout.rows)
        val small = previewWidget(GridSpec(6, 3), Density.COMPACT, labelLineDp = 16f)
        assertEquals(412f to 222f, small.widthDp to small.heightDp)
        assertEquals(6 to 3, small.layout.columns to small.layout.rows)
        assertNull(small.layout.note)
    }

    @Test fun `the preview shows the fullest group, the first of equals`() {
        fun group(g: AppGroup, n: Int) = PlannedGroup(g, List(n) { AppFacts("p$g$it", "A", "App $it") })
        assertEquals(AppGroup.WORK, previewGroup(listOf(group(AppGroup.STUDY, 2), group(AppGroup.WORK, 5), group(AppGroup.GAMES, 5)))?.group)
        assertNull(previewGroup(emptyList()))
    }

    // ---- Make it yours and Health ----------------------------------------------------------------------

    @Test fun `the icon style line names the pack once the packs are known`() {
        assertEquals("Themed icons", iconStyleText(IconStyle.THEMED, "app.lawnchair.lawnicons", null))
        assertEquals("Original icons", iconStyleText(IconStyle.ORIGINAL, null, null))
        assertEquals("Icon pack", iconStyleText(IconStyle.ICON_PACK, "app.lawnchair.lawnicons", null, packChecked = false))
        assertEquals("Icon pack: Lawnicons", iconStyleText(IconStyle.ICON_PACK, "app.lawnchair.lawnicons", "Lawnicons"))
        assertEquals("Icon pack not installed", iconStyleText(IconStyle.ICON_PACK, "app.lawnchair.lawnicons", null))
        assertEquals("No icon pack chosen", iconStyleText(IconStyle.ICON_PACK, null, null))
        assertEquals("PadStudio wallpaper applied", wallpaperText(true))
        assertEquals("Your own wallpaper", wallpaperText(false))
        assertEquals("Colours from your wallpaper", coloursText(true))
        assertEquals("PadStudio colours", coloursText(false))
    }

    @Test fun `health counts ready, needing attention and by hand, and names the first that needs attention`() {
        val checks = listOf(
            HealthCheck("Key Mapper installed", true, "Version 4.6.1"),
            HealthCheck("Key Mapper can see the pen", false, "Its accessibility service must be on."),
            HealthCheck("Pen profile is current", true, ""),
            HealthCheck("HyperOS keeps Key Mapper running", null, "Can't be read by apps."),
            HealthCheck("Device facts imported", true, ""),
            HealthCheck("Group widgets on the home screen", true, "2 placed."),
            HealthCheck("Themed icon source", true, ""),
            HealthCheck("PadStudio wallpaper", false, "Optional: Looks → Apply wallpaper."),
        )
        val summary = healthSummary(checks)
        assertEquals(HealthSummary(ready = 5, total = 8, attention = 2, byHand = 1, top = checks[1]), summary)
        assertEquals("5 of 8 ready", summary.title)
        assertEquals("2 need attention · 1 to check by hand", summary.breakdown)
        assertEquals("1 needs attention", healthSummary(checks.take(2)).breakdown)
        assertNull(healthSummary(checks.filter { it.ok == true }).breakdown)
        assertNull(healthSummary(checks.filter { it.ok == true }).top)
    }

    @Test fun `the top issue reads as Setup words it, in PenStatus's words and without profile hashes`() {
        val profile = HealthCheck("Pen profile is current", false, "Applied bfa1ea74a187; current 9d3c0e1f2a3b.")
        val changed = status(sent = "bfa1ea74a187", plan = "9d3c0e1f2a3b")
        val text = healthIssueText(profile, changed) { "14:05" }
        assertEquals("Changes not sent yet. Use Send to Key Mapper on Today or Pen.", text)
        assertFalse("bfa1ea74a187" in text || "9d3c0e1f2a3b" in text)
        val never = HealthCheck("Pen profile is current", false, "Not applied yet: Pen → Apply to Key Mapper.")
        assertEquals("Not sent to Key Mapper yet. Use Send to Key Mapper on Today or Pen.", healthIssueText(never, status(sent = null)) { "14:05" })
        // The other checks that can need attention read as on Setup, e.g. no stale tools/probe-device.sh copy.
        val facts = HealthCheck("Device facts imported", false, "Optional. … tools/probe-device.sh, then tools/facts.sh.")
        assertEquals(
            "Optional. Adds your camera’s shutter button and the pen’s measured controls: see Device facts under Developer.",
            healthIssueText(facts, status()) { "14:05" },
        )
        // A check Setup doesn't know keeps the view model's own words.
        assertEquals("Something new.", healthIssueText(HealthCheck("A new check", false, "Something new."), status()) { "14:05" })
    }

    // ---- Layout ----------------------------------------------------------------------------------------

    @Test fun `two columns from 840 dp while the side column holds 14 lines of body text`() {
        assertTrue(useTwoColumns(contentWidthDp = 900f, bodyEmDp = 16f))
        assertTrue(useTwoColumns(contentWidthDp = 900f, bodyEmDp = 20f)) // 125 %
        assertFalse(useTwoColumns(contentWidthDp = 900f, bodyEmDp = 28f)) // 200 %: the mock's one column
        assertFalse(useTwoColumns(contentWidthDp = 839f, bodyEmDp = 16f))
        assertTrue(useTwoColumns(contentWidthDp = 840f, bodyEmDp = 16f))
    }

    @Test fun `the hero puts the pen beside the text while the text keeps 13 ems, else under it`() {
        val wide = heroGeometry(heroWidthDp = 532f, bodyEmDp = 20f)
        assertEquals(HeroArrangement.SIDE, wide.initial)
        assertEquals(266f, wide.sideStageWidthDp)
        assertEquals(239.4f, wide.sideStageHeightDp, 0.01f)
        assertEquals(HeroArrangement.STACKED, heroGeometry(heroWidthDp = 532f, bodyEmDp = 23f).initial)
        val single = heroGeometry(heroWidthDp = 900f, bodyEmDp = 28f)
        assertEquals(HeroArrangement.SIDE, single.initial)
        assertEquals(360f, single.sideStageWidthDp)
        assertEquals(320f, single.sideStageHeightDp)
        assertEquals(220f, heroGeometry(heroWidthDp = 420f, bodyEmDp = 10f).sideStageHeightDp)
        val narrow = heroGeometry(heroWidthDp = 400f, bodyEmDp = 16f)
        assertEquals(HeroArrangement.STACKED, narrow.initial)
        assertEquals(400f, narrow.stackedStageWidthDp)
    }
}
