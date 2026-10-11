package com.charan.padstudio.core.pen

import com.charan.padstudio.core.pen.PenIntent.BACK
import com.charan.padstudio.core.pen.PenIntent.NEXT_PAGE
import com.charan.padstudio.core.pen.PenIntent.OPEN_CAMERA
import com.charan.padstudio.core.pen.PenIntent.OPEN_LIBRARY
import com.charan.padstudio.core.pen.PenIntent.OPEN_NOTES
import com.charan.padstudio.core.pen.PenIntent.PLAY_PAUSE
import com.charan.padstudio.core.pen.PenIntent.PREVIOUS_PAGE
import com.charan.padstudio.core.pen.PenIntent.RECENTS
import com.charan.padstudio.core.pen.PenIntent.REDO
import com.charan.padstudio.core.pen.PenIntent.SCREENSHOT
import com.charan.padstudio.core.pen.PenIntent.SHUTTER
import com.charan.padstudio.core.pen.PenIntent.UNDO
import com.charan.padstudio.core.pen.PenProfile.Companion.EVERYWHERE

/** Which pen the wizard found; decides which controls the default profile uses. */
enum class PenKind {
    /** Smart Pen 2 / Focus Pen: an upper and a lower barrel button. */
    TWO_BUTTON,

    /** Focus Pen Pro: squeeze plus barrel slides, no buttons. */
    FOCUS_PEN_PRO,
}

/**
 * The starting profile (PLAN §6). Package ids are the well-known Play/F-Droid ids; apps whose id
 * could not be verified in advance are matched on the tablet by label ([PenContext.labelHints]).
 * The app layer drops packages that are not installed before showing or generating anything.
 */
object DefaultProfiles {
    val CAMERA = PenContext(
        id = "camera",
        title = "Camera",
        packages = listOf("com.android.camera", "net.sourceforge.opencamera", "com.google.android.GoogleCamera"),
    )
    val READING = PenContext(
        id = "reading",
        title = "Reading & PDF",
        packages = listOf(
            "com.adobe.reader",
            "com.xodo.pdf.reader",
            "cn.wps.moffice_eng",
            "com.flyersoft.moonreader",
            "org.readera",
            "com.amazon.kindle",
            "com.google.android.apps.books",
            "com.google.android.apps.docs",
        ),
    )
    val NATIVE_NOTES = PenContext(
        id = "notes_native",
        title = "Notes with their own pen settings",
        // com.miui.creation (Mi Canvas) verified on the Pad 8 probe (ADR-001).
        packages = listOf("com.miui.creation", "com.orion.notein.global", "com.flexcil.flexcilnote"),
        passThrough = true,
        labelHints = listOf("Notein", "Notewise", "Flexcil", "Mi Canvas"),
    )
    val OTHER_NOTES = PenContext(
        id = "notes_other",
        title = "Other notes",
        packages = listOf(
            "com.microsoft.office.onenote",
            "com.google.android.keep",
            "com.steadfastinnovation.android.projectpapyrus",
            // On the Pad 8 (ADR-001): Mi Notes and freenote.
            "com.miui.notes",
            "ai.note.notepad.memo.journal.freenote",
        ),
    )
    val MEDIA = PenContext(
        id = "media",
        title = "Video & music",
        packages = listOf(
            "com.google.android.youtube",
            "in.startv.hotstar",
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "com.spotify.music",
            "com.jio.media.jiobeats",
        ),
    )
    val HOME = PenContext(id = "home", title = "Home screen", packages = listOf("com.miui.home", "app.lawnchair"))

    val CONTEXTS = listOf(CAMERA, READING, NATIVE_NOTES, OTHER_NOTES, MEDIA, HOME)

    /** Page-flip readers; everything else scrolls vertically. */
    val PAGING = mapOf(
        "com.amazon.kindle" to Paging.HORIZONTAL,
        "com.google.android.apps.books" to Paging.HORIZONTAL,
        "com.flyersoft.moonreader" to Paging.HORIZONTAL,
        "org.readera" to Paging.HORIZONTAL,
    )

    /** Open Camera takes a photo on KEYCODE_CAMERA (MainUI.onKeyDown in its source). */
    val KNOWN_CAMERAS = mapOf(
        "net.sourceforge.opencamera" to CameraFacts("net.sourceforge.opencamera", honoursCameraKey = true),
    )

    fun profile(kind: PenKind, mode: PenMode): PenProfile = PenProfile(CONTEXTS, bindings(kind, mode))

    fun bindings(kind: PenKind, mode: PenMode): List<Binding> = when (kind) {
        PenKind.TWO_BUTTON -> twoButton(mode)
        PenKind.FOCUS_PEN_PRO -> focusPenPro()
    }

    private fun twoButton(mode: PenMode): List<Binding> {
        val upper = PenControl.UPPER.id
        val lower = PenControl.LOWER.id
        val base = listOf(
            Binding(CAMERA.id, upper, Press.SHORT, SHUTTER),
            Binding(CAMERA.id, lower, Press.SHORT, SHUTTER),
            Binding(READING.id, upper, Press.SHORT, NEXT_PAGE),
            Binding(READING.id, lower, Press.SHORT, PREVIOUS_PAGE),
            Binding(OTHER_NOTES.id, upper, Press.SHORT, UNDO),
            Binding(OTHER_NOTES.id, lower, Press.SHORT, REDO),
            Binding(MEDIA.id, upper, Press.SHORT, PLAY_PAUSE),
            Binding(MEDIA.id, lower, Press.SHORT, BACK),
            Binding(HOME.id, upper, Press.SHORT, OPEN_LIBRARY),
            Binding(HOME.id, lower, Press.SHORT, OPEN_NOTES),
            Binding(HOME.id, upper, Press.DOUBLE, OPEN_CAMERA),
            Binding(EVERYWHERE, upper, Press.SHORT, SCREENSHOT),
            Binding(EVERYWHERE, lower, Press.SHORT, BACK),
            Binding(EVERYWHERE, upper, Press.DOUBLE, OPEN_CAMERA),
            Binding(EVERYWHERE, lower, Press.DOUBLE, RECENTS),
        )
        // Long presses are Xiaomi's (screenshot / quick note) unless Expert mode takes the device.
        val expertOnly = listOf(
            Binding(EVERYWHERE, upper, Press.LONG, SCREENSHOT),
            Binding(EVERYWHERE, lower, Press.LONG, OPEN_NOTES),
        )
        return if (mode == PenMode.EXPERT) base + expertOnly else base
    }

    /** Focus Pen Pro gestures are intercepted by HyperOS, so this profile assumes Expert mode. */
    private fun focusPenPro(): List<Binding> {
        val pinch = PenControl.PINCH.id
        return listOf(
            Binding(CAMERA.id, pinch, Press.SHORT, SHUTTER),
            Binding(READING.id, PenControl.SLIDE_DOWN.id, Press.SHORT, NEXT_PAGE),
            Binding(READING.id, PenControl.SLIDE_UP.id, Press.SHORT, PREVIOUS_PAGE),
            Binding(MEDIA.id, pinch, Press.SHORT, PLAY_PAUSE),
            Binding(HOME.id, pinch, Press.SHORT, OPEN_LIBRARY),
            Binding(HOME.id, pinch, Press.DOUBLE, OPEN_CAMERA),
            Binding(EVERYWHERE, pinch, Press.DOUBLE, SCREENSHOT),
            Binding(EVERYWHERE, pinch, Press.SHORT, BACK),
        )
    }
}
