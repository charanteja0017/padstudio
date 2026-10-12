package com.charan.padstudio.pen.screen

import com.charan.padstudio.core.pen.Binding
import com.charan.padstudio.core.pen.DefaultProfiles
import com.charan.padstudio.core.pen.PenContext
import com.charan.padstudio.core.pen.PenIntent
import com.charan.padstudio.core.pen.PenProfile
import com.charan.padstudio.core.pen.Press
import kotlin.test.Test
import kotlin.test.assertEquals

class PenWarningsTest {
    private val camera = DefaultProfiles.CAMERA.copy(packages = listOf("com.android.camera", "net.sourceforge.opencamera"))
    private val reading = DefaultProfiles.READING.copy(packages = listOf("org.readera"))
    private val everywhere = PenContext(PenProfile.EVERYWHERE, EVERYWHERE_TITLE, emptyList())
    private val home = DefaultProfiles.HOME
    private val bindings = listOf(
        Binding("home", "lower", Press.SHORT, PenIntent.OPEN_NOTES),
        Binding("camera", "upper", Press.SHORT, PenIntent.SHUTTER),
    )

    // The generator's wording, copied from core/pen/ProfileGenerator.kt and ActionResolver.kt.
    private val notRecorded = "Lower button was not recorded, so \"Camera: Shutter\" was skipped."
    private val everywhereSkipped = "Upper button was not recorded, so \"Everywhere else: Screenshot\" was skipped."
    private val noShutter = "No shutter known for com.android.camera: open it and run `tools/probe-device.sh --camera`, or calibrate a tap."
    private val notCalibrated = "Shutter for net.sourceforge.opencamera is not calibrated in landscape, so presses there do nothing."
    private val undoHere = "Undo in this context needs key injection (Expert mode + Key Mapper's system-bridge key setting) or a recorded Undo button."
    private val noNotes = "No notes app chosen, so \"Open notes app\" was skipped."
    private val duplicate = "camera: Upper button press was bound 2 times; using \"Shutter\"."
    private val refused = "Refused Squeeze: not a pen device."
    private val all = listOf(notRecorded, everywhereSkipped, noShutter, notCalibrated, undoHere, noNotes, duplicate, refused)

    @Test fun `warnings go to the context they name`() {
        assertEquals(listOf(notRecorded, noShutter, notCalibrated, duplicate), warningsFor(camera, all, bindings))
        assertEquals(listOf(everywhereSkipped, undoHere), warningsFor(everywhere, all, bindings))
        assertEquals(listOf(noNotes), warningsFor(home, all, bindings))
        assertEquals(emptyList(), warningsFor(reading, all, bindings))
    }

    @Test fun `a package is matched as a whole word`() {
        val camera2 = DefaultProfiles.CAMERA.copy(packages = listOf("com.android.camera2"))
        assertEquals(emptyList(), warningsFor(camera2, listOf(noShutter), bindings))
    }

    @Test fun `unclaimed warnings are pen-wide`() {
        assertEquals(listOf(refused), penWideWarnings(listOf(everywhere, camera, reading, home), all, bindings))
    }

    @Test fun `plain words name apps and keep unknown wording`() {
        val names = mapOf("com.android.camera" to "Camera", "net.sourceforge.opencamera" to "Open Camera")
        val plain = { raw: String -> plainWarning(raw, names::get) }
        assertEquals("Lower button isn't recorded yet, so “Shutter” does nothing here.", plain(notRecorded))
        assertEquals("Shutter isn't set up for Camera yet, so it does nothing there.", plain(noShutter))
        assertEquals("Shutter in Open Camera isn't set up for landscape, so it does nothing there.", plain(notCalibrated))
        assertEquals(
            "Undo here needs Expert setup with “Key Mapper can press keys” on, or a recorded Undo button.",
            plain(undoHere),
        )
        assertEquals("“Open notes app” does nothing until you choose a notes app under Advanced.", plain(noNotes))
        assertEquals("Upper button press has 2 actions here, so only “Shutter” is used.", plain(duplicate))
        assertEquals(refused, plain(refused))
        assertEquals(
            "Shutter in org.example isn't set up for portrait, so it does nothing there.",
            plain("Shutter for org.example is not calibrated in portrait, so presses there do nothing."),
        )
    }

    @Test fun `pen warnings keep the original`() {
        val listed = penWarnings(listOf(refused), { null })
        assertEquals(PenWarning(refused, refused), listed.single())
    }
}
