package com.charan.padstudio.core.pen

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What tools/probe-device.sh learned about the tablet, pushed into PadStudio's files dir as
 * device-facts.json (see tools/lib/make_facts.py). Everything is optional: PadStudio works without
 * it and simply has fewer shutter/undo mechanisms to choose from.
 */
@Serializable
data class FactsFile(
    val screen: ScreenSize? = null,
    val cameras: List<CameraFacts> = emptyList(),
    /** Pen key devices from `getevent -lp`, used to build Expert (evdev) triggers. */
    val penDevices: List<EvdevDevice> = emptyList(),
    val undoNodes: Map<String, UiNode> = emptyMap(),
    val redoNodes: Map<String, UiNode> = emptyMap(),
    /**
     * Pen controls recorded from the kernel by tools/capture-pen.sh. Needed when HyperOS keeps
     * the pen's keys from apps (Focus Pen Pro), so PadStudio's in-window recorder never sees them.
     */
    val controls: Map<String, PenTrigger> = emptyMap(),
    val penKind: PenKind? = null,
    val penMode: PenMode? = null,
) {
    fun applyTo(base: DeviceFacts): DeviceFacts = base.copy(
        screen = screen ?: base.screen,
        cameras = base.cameras + cameras.associateBy { it.packageName },
        undoNodes = base.undoNodes + undoNodes,
        redoNodes = base.redoNodes + redoNodes,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): FactsFile = json.decodeFromString(serializer(), text)
    }
}
