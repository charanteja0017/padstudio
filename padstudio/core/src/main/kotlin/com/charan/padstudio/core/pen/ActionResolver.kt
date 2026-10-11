package com.charan.padstudio.core.pen

/** One concrete way to perform an intent; taps and swipes need one variant per orientation. */
data class Variant(
    val key: String,
    val actions: List<KmAction>,
    val constraints: List<KmConstraint> = emptyList(),
)

data class Resolution(val variants: List<Variant>, val warnings: List<String> = emptyList())

/**
 * Turns a [PenIntent] into Key Mapper actions for one app, using what the probe learned about the
 * tablet. Key events are only used in Expert mode: in Basic mode they would go through Key
 * Mapper's keyboard, which must be the active input method to work.
 */
class ActionResolver(private val facts: DeviceFacts, private val mode: PenMode) {
    private val injectKeys = mode == PenMode.EXPERT && facts.canInjectKeys

    fun resolve(intent: PenIntent, packageName: String?): Resolution = when (intent) {
        PenIntent.SCREENSHOT -> system("screenshot")
        PenIntent.BACK -> system("go_back")
        PenIntent.HOME -> system("go_home")
        PenIntent.RECENTS -> system("open_recents")
        PenIntent.SPLIT_SCREEN -> system("toggle_split_screen")
        PenIntent.NOTIFICATIONS -> system("expand_notification_drawer")
        PenIntent.OPEN_CAMERA -> openCamera()
        PenIntent.PLAY_PAUSE -> system("play_pause_media")
        PenIntent.NEXT_TRACK -> system("next_track")
        PenIntent.PREVIOUS_TRACK -> system("previous_track")
        PenIntent.LAST_APP -> system("go_last_app")
        PenIntent.QUICK_SETTINGS -> system("expand_quick_settings")
        PenIntent.ASSISTANT -> system("open_assistant")
        PenIntent.VOLUME_UP -> single(KmAction.System("volume_up", flags = FLAG_SHOW_VOLUME_UI))
        PenIntent.VOLUME_DOWN -> single(KmAction.System("volume_down", flags = FLAG_SHOW_VOLUME_UI))
        PenIntent.DO_NOT_DISTURB -> single(KmAction.System("toggle_do_not_disturb_mode", mapOf("extra_do_not_disturb_mode" to "do_not_disturb_priority")))
        PenIntent.FLASHLIGHT -> single(KmAction.System("toggle_flashlight", mapOf("extra_flash" to "option_lens_back")))
        PenIntent.ROTATION_LOCK -> system("toggle_auto_rotate")
        PenIntent.OPEN_LIBRARY -> single(
            KmAction.Intent("#Intent;component=${facts.libraryComponent};end", "PadStudio app library"),
        )
        PenIntent.OPEN_NOTES -> facts.notesApp
            ?.let { single(KmAction.App(it, facts.appNames[it])) }
            ?: Resolution(emptyList(), listOf("No notes app chosen, so \"${intent.label}\" was skipped."))
        PenIntent.SHUTTER -> if (packageName == null) openCamera() else shutter(packageName)
        PenIntent.NEXT_PAGE -> page(packageName, forward = true)
        PenIntent.PREVIOUS_PAGE -> page(packageName, forward = false)
        PenIntent.UNDO -> edit(packageName, redo = false)
        PenIntent.REDO -> edit(packageName, redo = true)
    }

    /**
     * The camera the probe measured, opened as an app: Key Mapper's "open camera" uses Android's
     * generic camera action, which shows a chooser when several apps can take photos (it does on the Pad 8).
     */
    private fun openCamera(): Resolution {
        val probed = facts.cameras.values.firstOrNull { it.shutterNode != null || it.volumeKeyIsShutter || it.shutterTap.isNotEmpty() }
        val camera = probed?.packageName?.takeIf { facts.appNames.isEmpty() || it in facts.appNames } ?: return system("open_camera")
        return single(KmAction.App(camera, facts.appNames[camera]))
    }

    private fun shutter(packageName: String): Resolution {
        val camera = facts.cameras[packageName]
        // A key the probe saw take a photo comes first: on the Pad 8 (HyperOS 3, Key Mapper 4.6.1) clicking
        // the recorded shutter button did nothing, while an injected camera key did (tests 2026-10-11).
        if (injectKeys && camera?.honoursCameraKey == true) {
            return single(KmAction.KeyEvent(KEYCODE_CAMERA))
        }
        if (injectKeys && camera?.volumeKeyIsShutter == true) {
            return single(KmAction.KeyEvent(KEYCODE_VOLUME_DOWN))
        }
        // Without key injection: the recorded shutter button, which needs no orientation variants.
        camera?.shutterNode?.let { return single(KmAction.UiElement(packageName, it, NodeAction.CLICK)) }

        val taps = camera?.shutterTap.orEmpty()
        if (taps.isNotEmpty()) {
            val variants = Orientation.entries.mapNotNull { orientation ->
                val point = taps[orientation] ?: return@mapNotNull null
                Variant(
                    key = orientation.name.lowercase(),
                    actions = listOf(
                        KmAction.Tap(point, facts.screen.inOrientation(orientation), "Shutter (${orientation.name.lowercase()})"),
                    ),
                    constraints = listOf(KmConstraint.InOrientation(orientation)),
                )
            }
            val missing = Orientation.entries.filter { it !in taps }
            return Resolution(
                variants,
                missing.map { "Shutter for $packageName is not calibrated in ${it.name.lowercase()}, so presses there do nothing." },
            )
        }
        if (injectKeys) {
            return Resolution(
                listOf(Variant("volume", listOf(KmAction.KeyEvent(KEYCODE_VOLUME_DOWN)))),
                listOf("Shutter for $packageName sends Volume Down, which is not verified for this app yet."),
            )
        }
        return Resolution(
            emptyList(),
            listOf("No shutter known for $packageName: open it and run `tools/probe-device.sh --camera`, or calibrate a tap."),
        )
    }

    private fun page(packageName: String?, forward: Boolean): Resolution {
        if (injectKeys) {
            return single(KmAction.KeyEvent(if (forward) KEYCODE_PAGE_DOWN else KEYCODE_PAGE_UP))
        }
        val paging = packageName?.let { facts.paging[it] } ?: Paging.VERTICAL
        val variants = Orientation.entries.map { orientation ->
            val screen = facts.screen.inOrientation(orientation)
            Variant(
                key = orientation.name.lowercase(),
                actions = listOf(pageSwipe(screen, paging, forward)),
                constraints = listOf(KmConstraint.InOrientation(orientation)),
            )
        }
        return Resolution(variants)
    }

    private fun edit(packageName: String?, redo: Boolean): Resolution {
        val label = if (redo) "Redo" else "Undo"
        if (injectKeys) {
            return single(KmAction.KeyEvent(if (redo) KEYCODE_Y else KEYCODE_Z, META_CTRL))
        }
        val node = packageName?.let { (if (redo) facts.redoNodes else facts.undoNodes)[it] }
        if (node != null) return single(KmAction.UiElement(packageName, node, NodeAction.CLICK))
        return Resolution(
            emptyList(),
            listOf("$label in ${packageName ?: "this context"} needs key injection (Expert mode + Key Mapper's system-bridge key setting) or a recorded $label button."),
        )
    }

    private fun system(id: String) = single(KmAction.System(id))

    private fun single(action: KmAction) = Resolution(listOf(Variant("default", listOf(action))))

    companion object {
        /** ActionEntity.ACTION_FLAG_SHOW_VOLUME_UI: volume actions show the volume bar. */
        const val FLAG_SHOW_VOLUME_UI = 1

        // android.view.KeyEvent constants (stable since API 1).
        const val KEYCODE_VOLUME_DOWN = 25
        const val KEYCODE_CAMERA = 27
        const val KEYCODE_Y = 53
        const val KEYCODE_Z = 54
        const val KEYCODE_PAGE_UP = 92
        const val KEYCODE_PAGE_DOWN = 93

        /** META_CTRL_ON | META_CTRL_LEFT_ON. */
        const val META_CTRL = 0x1000 or 0x2000

        /**
         * A vertical page turn is a slow drag (no fling) over 55% of the screen; a horizontal one is
         * a quick flick across 70%, which page-flip readers treat as "next page".
         */
        fun pageSwipe(screen: ScreenSize, paging: Paging, forward: Boolean): KmAction.Swipe {
            val w = screen.width
            val h = screen.height
            return when (paging) {
                Paging.VERTICAL -> {
                    val low = Point(w / 2, (h * 0.80).toInt())
                    val high = Point(w / 2, (h * 0.25).toInt())
                    if (forward) {
                        KmAction.Swipe(low, high, 450, screen, "Next page (scroll down)")
                    } else {
                        KmAction.Swipe(high, low, 450, screen, "Previous page (scroll up)")
                    }
                }
                Paging.HORIZONTAL -> {
                    val right = Point((w * 0.85).toInt(), h / 2)
                    val left = Point((w * 0.15).toInt(), h / 2)
                    if (forward) {
                        KmAction.Swipe(right, left, 200, screen, "Next page (flip)")
                    } else {
                        KmAction.Swipe(left, right, 200, screen, "Previous page (flip)")
                    }
                }
            }
        }
    }
}
