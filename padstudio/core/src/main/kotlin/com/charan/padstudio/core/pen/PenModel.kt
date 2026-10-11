package com.charan.padstudio.core.pen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One physical control on the pen, as the setup wizard recorded it.
 *
 * Smart Pen 2 and Focus Pen have an upper and a lower barrel button (Bluetooth Page Up/Down on
 * the Pad 6). The Focus Pen Pro has no buttons: HyperOS reports a squeeze as scan code 189 and
 * barrel slides as key codes 196/197 from the input device "Xiaomi Focus Pen Pro" (0022:5081),
 * per reference/focuspen-pro-x CompatProfile.kt.
 */
data class PenControl(val id: String, val label: String) {
    companion object {
        val UPPER = PenControl("upper", "Upper button")
        val LOWER = PenControl("lower", "Lower button")
        val FOCUS = PenControl("focus", "Focus key")
        val PINCH = PenControl("pinch", "Squeeze")
        val SLIDE_UP = PenControl("slide_up", "Slide up")
        val SLIDE_DOWN = PenControl("slide_down", "Slide down")
        val KNOWN = listOf(UPPER, LOWER, FOCUS, PINCH, SLIDE_UP, SLIDE_DOWN)

        fun byId(id: String) = KNOWN.firstOrNull { it.id == id } ?: PenControl(id, id)
    }
}

/** Same numbers as Key Mapper's TriggerKeyEntity SHORT_PRESS / LONG_PRESS / DOUBLE_PRESS. */
enum class Press(val clickType: Int, val label: String) {
    SHORT(0, "press"),
    LONG(1, "hold"),
    DOUBLE(2, "double press"),
}

/**
 * BASIC: Key Mapper sees the pen through its accessibility service (no setup after reboot).
 * EXPERT: Key Mapper's ADB-level helper grabs the pen's input device, so it also sees presses
 * HyperOS keeps for itself and can inject keys system-wide.
 */
enum class PenMode { BASIC, EXPERT }

/** How Key Mapper will recognise one pen control. */
@Serializable
sealed interface PenTrigger {
    val keyCode: Int
    val scanCode: Int?

    /** A key event seen by the accessibility service; [deviceDescriptor] is InputDevice.descriptor. */
    @Serializable
    @SerialName("key_event")
    data class KeyEvent(
        override val keyCode: Int,
        override val scanCode: Int?,
        val deviceDescriptor: String,
        val deviceName: String,
    ) : PenTrigger

    /** A raw key from /dev/input, grabbed by Key Mapper's system bridge. */
    @Serializable
    @SerialName("evdev")
    data class Evdev(
        override val keyCode: Int,
        override val scanCode: Int,
        val device: EvdevDevice,
    ) : PenTrigger
}

/**
 * [capabilities] are the KEY/BTN/ABS names `getevent -lp` lists for the device. They are only used
 * by [EvdevSafetyFilter] and never written to Key Mapper.
 */
@Serializable
data class EvdevDevice(
    val name: String,
    val bus: Int,
    val vendor: Int,
    val product: Int,
    val capabilities: List<String> = emptyList(),
)

/** What a press should do; resolved per app into concrete Key Mapper actions by [ActionResolver]. */
enum class PenIntent(val label: String, val perApp: Boolean = false) {
    SHUTTER("Shutter", perApp = true),
    NEXT_PAGE("Next page", perApp = true),
    PREVIOUS_PAGE("Previous page", perApp = true),
    UNDO("Undo", perApp = true),
    REDO("Redo", perApp = true),
    SCREENSHOT("Screenshot"),
    BACK("Back"),
    HOME("Home"),
    RECENTS("Recents"),
    SPLIT_SCREEN("Split screen"),
    NOTIFICATIONS("Notifications"),
    OPEN_CAMERA("Open camera"),
    OPEN_LIBRARY("Open app library"),
    OPEN_NOTES("Open notes app"),
    PLAY_PAUSE("Play/pause"),
    NEXT_TRACK("Next track"),
    PREVIOUS_TRACK("Previous track"),
}

/**
 * A set of apps that share pen behaviour. [passThrough] apps (notes apps with their own pen
 * settings) get no key maps, so the raw button reaches the app.
 */
data class PenContext(
    val id: String,
    val title: String,
    val packages: List<String>,
    val passThrough: Boolean = false,
    /** App labels matched on the tablet when a package id could not be verified in advance. */
    val labelHints: List<String> = emptyList(),
)

@Serializable
data class Binding(val contextId: String, val control: String, val press: Press, val intent: PenIntent)

data class PenProfile(val contexts: List<PenContext>, val bindings: List<Binding>) {
    companion object {
        /** Context id for "any app not listed in another context". */
        const val EVERYWHERE = "everywhere"
    }
}

/** The wizard's result: which mode Key Mapper runs in and how each control is recognised. */
data class PenSetup(val mode: PenMode, val controls: Map<String, PenTrigger>)

enum class Orientation { PORTRAIT, LANDSCAPE }

/** Logical display size in pixels; [inOrientation] swaps sides for portrait/landscape. */
@Serializable
data class ScreenSize(val width: Int, val height: Int) {
    fun inOrientation(orientation: Orientation): ScreenSize {
        val short = minOf(width, height)
        val long = maxOf(width, height)
        return if (orientation == Orientation.PORTRAIT) ScreenSize(short, long) else ScreenSize(long, short)
    }
}

@Serializable
data class Point(val x: Int, val y: Int)

/** A view found with `uiautomator dump` or Key Mapper's node recorder. */
@Serializable
data class UiNode(val viewResourceId: String, val className: String? = null, val description: String)

/**
 * What the probe learned about one camera app. Key Mapper does not move tap coordinates when the
 * tablet rotates (PerformActionsUseCase.normaliseSourceDisplaySize), so taps are per orientation.
 */
@Serializable
data class CameraFacts(
    val packageName: String,
    val shutterNode: UiNode? = null,
    val shutterTap: Map<Orientation, Point> = emptyMap(),
    /** Xiaomi Camera's "Volume buttons function = Shutter". */
    val volumeKeyIsShutter: Boolean = false,
    /** Takes a photo on KEYCODE_CAMERA (true for Open Camera, per its MainUI.onKeyDown). */
    val honoursCameraKey: Boolean = false,
)

/** How a reader turns pages: scrolling documents (vertical) or flipping pages (horizontal). */
enum class Paging { VERTICAL, HORIZONTAL }

data class DeviceFacts(
    val screen: ScreenSize,
    val cameras: Map<String, CameraFacts> = emptyMap(),
    val paging: Map<String, Paging> = emptyMap(),
    val undoNodes: Map<String, UiNode> = emptyMap(),
    val redoNodes: Map<String, UiNode> = emptyMap(),
    val notesApp: String? = null,
    val libraryComponent: String = "com.charan.padstudio/com.charan.padstudio.home.LibraryActivity",
    val appNames: Map<String, String> = emptyMap(),
)
