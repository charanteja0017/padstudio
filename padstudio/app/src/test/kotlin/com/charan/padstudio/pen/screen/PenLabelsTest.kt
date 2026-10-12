package com.charan.padstudio.pen.screen

import com.charan.padstudio.core.pen.Binding
import com.charan.padstudio.core.pen.DefaultProfiles
import com.charan.padstudio.core.pen.EvdevDevice
import com.charan.padstudio.core.pen.PenContext
import com.charan.padstudio.core.pen.PenControl
import com.charan.padstudio.core.pen.PenIntent
import com.charan.padstudio.core.pen.PenKind
import com.charan.padstudio.core.pen.PenMode
import com.charan.padstudio.core.pen.PenProfile
import com.charan.padstudio.core.pen.PenTrigger
import com.charan.padstudio.core.pen.Press
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PenLabelsTest {
    @Test fun `controls and presses are what PadStudio 1 offered`() {
        assertEquals(listOf("upper", "lower"), controlsFor(PenKind.TWO_BUTTON).map { it.id })
        assertEquals(listOf("pinch", "slide_up", "slide_down", "double_tap"), controlsFor(PenKind.FOCUS_PEN_PRO).map { it.id })
        assertEquals(listOf(Press.SHORT, Press.LONG, Press.DOUBLE), pressesFor(PenControl.PINCH, PenMode.EXPERT))
        assertEquals(listOf(Press.SHORT, Press.DOUBLE), pressesFor(PenControl.UPPER, PenMode.BASIC))
        assertEquals(listOf(Press.SHORT), pressesFor(PenControl.DOUBLE_TAP, PenMode.EXPERT))
    }

    @Test fun `press and binding labels`() {
        assertEquals(listOf("Press", "Hold", "Double press"), Press.entries.map(::pressLabel))
        assertEquals("Squeeze", bindingLabel(PenControl.PINCH, Press.SHORT))
        assertEquals("Squeeze double press", bindingLabel(PenControl.PINCH, Press.DOUBLE))
        assertEquals("Double tap", bindingLabel(PenControl.DOUBLE_TAP, Press.SHORT))
    }

    @Test fun `bound intent takes the first match like the old screen`() {
        val bindings = listOf(
            Binding("camera", "pinch", Press.SHORT, PenIntent.SHUTTER),
            Binding("camera", "pinch", Press.SHORT, PenIntent.OPEN_CAMERA),
            Binding("home", "pinch", Press.DOUBLE, PenIntent.OPEN_CAMERA),
        )
        assertEquals(PenIntent.SHUTTER, boundIntent(bindings, "camera", "pinch", Press.SHORT))
        assertEquals(PenIntent.OPEN_CAMERA, boundIntent(bindings, "home", "pinch", Press.DOUBLE))
        assertNull(boundIntent(bindings, "home", "pinch", Press.SHORT))
    }

    @Test fun `contexts list everywhere first and pass-through last, without empty ones`() {
        val reading = DefaultProfiles.READING.copy(packages = listOf("org.readera"))
        val notes = DefaultProfiles.NATIVE_NOTES.copy(packages = listOf("com.miui.creation"))
        val camera = DefaultProfiles.CAMERA.copy(packages = emptyList())
        val home = DefaultProfiles.HOME
        val listed = screenContexts(listOf(camera, reading, notes, home))
        assertEquals(listOf(PenProfile.EVERYWHERE, "reading", "home", "notes_native"), listed.map { it.id })
        assertEquals("Everywhere else", listed.first().title)
    }

    @Test fun `context labels shorten only the display title`() {
        assertEquals(ContextLabels("Everywhere else", "All other apps"), contextLabels(PenContext(PenProfile.EVERYWHERE, "x", emptyList())))
        assertEquals(ContextLabels("Own pen settings", "Leaves the pen to the app"), contextLabels(DefaultProfiles.NATIVE_NOTES))
        assertEquals("Notes with their own pen settings", DefaultProfiles.NATIVE_NOTES.title)
        assertEquals(ContextLabels("Home screen", "Your home screen"), contextLabels(DefaultProfiles.HOME))
        assertEquals(ContextLabels("Reading & PDF", "2 apps"), contextLabels(DefaultProfiles.READING.copy(packages = listOf("a", "b"))))
        assertEquals("1 app", appCount(1))
    }

    @Test fun `controls set lines`() {
        val bindings = DefaultProfiles.bindings(PenKind.FOCUS_PEN_PRO, PenMode.EXPERT)
        val controls = controlsFor(PenKind.FOCUS_PEN_PRO)
        assertEquals(2, controlsWithActions(bindings, "camera", controls))
        assertEquals(0, controlsWithActions(bindings, "notes_other", controls))
        assertEquals("2 of 4 have actions", controlsSetLine(2, 4))
        assertEquals("No pen actions set yet", controlsSetLine(0, 4))
    }

    @Test fun `triggers and devices read plainly`() {
        val device = EvdevDevice("Xiaomi Focus Pen Pro Keyboard", 5, 0x2717, 0x5081)
        assertEquals("Not recorded", describeTrigger(null))
        assertEquals("key 332 · scan 189 · Pen", describeTrigger(PenTrigger.KeyEvent(332, 189, "d", "Pen")))
        assertEquals("key 92 · Pen", describeTrigger(PenTrigger.KeyEvent(92, null, "d", "Pen")))
        assertEquals("evdev 332/189 · Xiaomi Focus Pen Pro Keyboard", describeTrigger(PenTrigger.Evdev(332, 189, device)))
        assertEquals(
            listOf("Xiaomi Focus Pen Pro Keyboard · bus 5 · 2717:5081"),
            describeDevices(listOf(PenTrigger.Evdev(332, 189, device), PenTrigger.Evdev(153, 73, device))),
        )
        assertEquals(
            controlsFor(PenKind.FOCUS_PEN_PRO).size,
            controlsKnown(controlsFor(PenKind.FOCUS_PEN_PRO), controlsFor(PenKind.FOCUS_PEN_PRO).associate { it.id to PenTrigger.Evdev(1, 1, device) }),
        )
    }

    @Test fun `record choice keeps the old rules`() {
        val trigger = PenTrigger.KeyEvent(92, 104, "d", "Pen")
        assertEquals(RecordChoice.MEASURED, recordChoice(recorded = null, measured = true, mode = PenMode.EXPERT))
        assertEquals(RecordChoice.RECORD, recordChoice(recorded = null, measured = true, mode = PenMode.BASIC))
        assertEquals(RecordChoice.RECORD, recordChoice(recorded = null, measured = false, mode = PenMode.EXPERT))
        assertEquals(RecordChoice.RECORD_AGAIN, recordChoice(recorded = trigger, measured = false, mode = PenMode.BASIC))
    }

    @Test fun `clips, markers and prompts per control`() {
        assertEquals("squeeze", clipFor("pinch"))
        assertEquals("double_tap", clipFor("double_tap"))
        assertEquals("idle", clipFor("upper"))
        assertEquals("marker_slide_top", markerFor("slide_up"))
        assertEquals("marker_slide_bottom", markerFor("slide_down"))
        assertNull(markerFor("lower"))
        assertEquals("Squeeze the pen now…", recordPrompt(PenControl.PINCH))
        assertEquals("Press the upper button now…", recordPrompt(PenControl.UPPER))
    }

    @Test fun `sent time shows the day only when it was not today`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(2026, 10, 12, 9, 30, 0, 0, zone).toInstant().toEpochMilli()
        val earlierToday = ZonedDateTime.of(2026, 10, 12, 0, 5, 0, 0, zone).toInstant().toEpochMilli()
        val yesterday = ZonedDateTime.of(2026, 10, 11, 23, 55, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("T", sentTimeLabel(earlierToday, now, zone, { "T" }, { "D" }))
        assertEquals("D T", sentTimeLabel(yesterday, now, zone, { "T" }, { "D" }))
    }
}
