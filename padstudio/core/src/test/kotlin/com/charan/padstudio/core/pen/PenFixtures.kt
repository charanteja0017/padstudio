package com.charan.padstudio.core.pen

/**
 * Synthetic devices used by the tests. Numbers follow the formats Key Mapper and Android use; the
 * descriptor strings are made up (the real ones come from the wizard on the tablet).
 */
object PenFixtures {
    val SCREEN = ScreenSize(3200, 2136)

    val SMART_PEN_UPPER = PenTrigger.KeyEvent(92, 104, "0123456789abcdef0123456789abcdef01234567", "Xiaomi Smart Pen")
    val SMART_PEN_LOWER = PenTrigger.KeyEvent(93, 109, "0123456789abcdef0123456789abcdef01234567", "Xiaomi Smart Pen")

    val FOCUS_PEN_PRO = EvdevDevice("Xiaomi Focus Pen Pro", bus = 0x0005, vendor = 0x0022, product = 0x5081, capabilities = listOf("KEY_F19"))
    val FOCUS_PEN_PRO_PINCH = PenTrigger.Evdev(keyCode = 0, scanCode = 189, device = FOCUS_PEN_PRO)
    val FOCUS_PEN_PRO_SLIDE_UP = PenTrigger.Evdev(keyCode = 196, scanCode = 196, device = FOCUS_PEN_PRO)
    val FOCUS_PEN_PRO_SLIDE_DOWN = PenTrigger.Evdev(keyCode = 197, scanCode = 197, device = FOCUS_PEN_PRO)

    val BASIC_TWO_BUTTON = PenSetup(PenMode.BASIC, mapOf("upper" to SMART_PEN_UPPER, "lower" to SMART_PEN_LOWER))
    val EXPERT_TWO_BUTTON = PenSetup(PenMode.EXPERT, mapOf("upper" to SMART_PEN_UPPER, "lower" to SMART_PEN_LOWER))
    val EXPERT_FOCUS_PRO = PenSetup(
        PenMode.EXPERT,
        mapOf("pinch" to FOCUS_PEN_PRO_PINCH, "slide_up" to FOCUS_PEN_PRO_SLIDE_UP, "slide_down" to FOCUS_PEN_PRO_SLIDE_DOWN),
    )

    val FACTS = DeviceFacts(
        screen = SCREEN,
        cameras = mapOf(
            "com.android.camera" to CameraFacts(
                "com.android.camera",
                shutterNode = UiNode("com.android.camera:id/shutter_button", "android.widget.ImageView", "Shutter"),
            ),
            "com.google.android.GoogleCamera" to CameraFacts(
                "com.google.android.GoogleCamera",
                shutterTap = mapOf(Orientation.PORTRAIT to Point(1068, 2900), Orientation.LANDSCAPE to Point(3000, 1068)),
            ),
        ) + DefaultProfiles.KNOWN_CAMERAS,
        paging = DefaultProfiles.PAGING,
        undoNodes = mapOf("com.google.android.keep" to UiNode("com.google.android.keep:id/undo", null, "Undo")),
        redoNodes = mapOf("com.google.android.keep" to UiNode("com.google.android.keep:id/redo", null, "Redo")),
        notesApp = "com.orion.notein.global",
        // Most tests describe a fully set-up Expert mode; ActionResolverTest covers injection being off.
        canInjectKeys = true,
    )

    /** Every package any default context mentions, plus apps no context lists. */
    val APPS = DefaultProfiles.CONTEXTS.flatMap { it.packages } + listOf("com.whatsapp", "com.android.settings")
}
