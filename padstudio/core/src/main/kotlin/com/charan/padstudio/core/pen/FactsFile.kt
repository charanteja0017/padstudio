package com.charan.padstudio.core.pen

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder

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
    /**
     * HyperOS's home-screen grid as recorded on the tablet (redesign plan §6a): a dated baseline,
     * never live state, because apps can't read HyperOS's grid. Absent in older files. A block that
     * doesn't decode reads as null instead of failing the file, so it can't cost the pen facts above.
     */
    @Serializable(with = RecordedHomeScreen::class)
    val homeScreen: HomeScreenFacts? = null,
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

/**
 * HyperOS's home screen as gate G5 recorded it (docs/decisions/ADR-005-home-grid.md), written by
 * tools/lib/make_facts.py from docs/device/home-grid.json. That writer requires [columns], [rows],
 * [namesHidden], [recordedAt] and [source]; the rest is optional.
 */
@Serializable
data class HomeScreenFacts(
    val columns: Int,
    val rows: Int,
    /** HyperOS's "Don't show text": app names hidden under the icons. */
    val namesHidden: Boolean,
    /** How the tablet was held when the grid was counted: "landscape" or "portrait". */
    val orientation: String? = null,
    /** The day it was recorded, ISO 8601, e.g. "2026-10-11". */
    val recordedAt: String,
    /** How it was recorded, for the "recorded baseline" wording. */
    val source: String,
    /** Whether HyperOS offers a grid-size setting on this tablet; false is outcome G5-B. Null when not recorded. */
    val hyperosGridSetting: Boolean? = null,
    /** HyperOS's own home-screen options, in its wording. */
    val hyperosOptions: List<String> = emptyList(),
) {
    init {
        require(columns > 0 && rows > 0) { "a recorded grid has at least one column and one row, was ${columns}x$rows" }
    }
}

/** Reads [FactsFile.homeScreen] leniently: a block that doesn't decode becomes null. */
internal object RecordedHomeScreen : KSerializer<HomeScreenFacts?> {
    private val strict = HomeScreenFacts.serializer().nullable

    override val descriptor: SerialDescriptor = strict.descriptor

    override fun serialize(encoder: Encoder, value: HomeScreenFacts?) = strict.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): HomeScreenFacts? {
        val input = decoder as? JsonDecoder ?: return strict.deserialize(decoder)
        val element = input.decodeJsonElement()
        return try {
            input.json.decodeFromJsonElement(strict, element)
        } catch (malformed: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException, and so is a failed require().
            null
        }
    }
}
