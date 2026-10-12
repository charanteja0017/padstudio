package com.charan.padstudio.core.pen

/**
 * What PadStudio can truthfully say about the pen (redesign plan §7). Configured, sent, observed
 * and unknown are different things, so each row of §7 has its own enum and its own words, and
 * nothing here claims more than the app can see: never "verified", never "running", never
 * "Expert mode on".
 *
 * @property listening Key Mapper's accessibility service is on (KeyMapperBridge); null when that can't be read.
 * @property sentHash the profile hash last handed to Key Mapper (StudioConfig.appliedProfileHash).
 * @property planHash the hash of the profile the current setup builds (PenPlan.hash); null while there is none.
 * @property appliedAt when [sentHash] was handed over, in epoch milliseconds (StudioConfig.appliedAt).
 * @property penEnabled what Pause/Resume pen last asked for (StudioConfig.penEnabled); Key Mapper can't be asked.
 */
data class PenStatus(
    val listening: Boolean?,
    val sentHash: String?,
    val planHash: String?,
    val appliedAt: Long?,
    val penEnabled: Boolean,
) {
    /** How a status pill is coloured: the design kit's good, warn, bad and unknown states. */
    enum class Tone { GOOD, WARN, BAD, UNKNOWN }

    /** §7 "Configured", known from the config and the profile built from it. */
    enum class Configured(val tone: Tone) {
        /** A profile was built from the saved setup. */
        SET_UP(Tone.GOOD),

        /** No profile yet: still loading, or it couldn't be built. */
        NOT_READY(Tone.UNKNOWN),
    }

    /** §7 "Sent": whether what was handed to Key Mapper is what the setup builds now. */
    enum class Sent(val tone: Tone) {
        /** Handed over, and the setup hasn't changed since. */
        SENT(Tone.GOOD),

        /** Handed over before, but the setup has changed since. */
        CHANGES_NOT_SENT(Tone.WARN),

        /** Never handed over. */
        NOT_SENT(Tone.WARN),

        /** Handed over before, with no current profile to compare it with. */
        SENT_NOT_COMPARED(Tone.UNKNOWN),
    }

    /** §7 "Key Mapper listening": its accessibility service, which apps can read. */
    enum class KeyMapper(val tone: Tone) {
        LISTENING(Tone.GOOD),
        OFF(Tone.BAD),
        UNKNOWN(Tone.UNKNOWN),
    }

    /** §7 "Pen actions on/off": only what was last requested, because Key Mapper can't be asked. */
    enum class PenActions(val tone: Tone) {
        ON_AS_REQUESTED(Tone.UNKNOWN),
        OFF_AS_REQUESTED(Tone.UNKNOWN),
    }

    /** §7 "Expert mode running, pen grabbed": visible only over adb (tools/pen-health.sh), so never shown as running. */
    enum class ExpertMode(val tone: Tone) {
        CANT_BE_CHECKED(Tone.UNKNOWN),
    }

    /** §7 "Working": not observable in the app, so never claimed. The Pen test shows live stylus data only. */
    enum class Working(val tone: Tone) {
        NOT_OBSERVABLE(Tone.UNKNOWN),
    }

    private val sentKey: String? get() = sentHash?.takeIf { it.isNotBlank() }
    private val planKey: String? get() = planHash?.takeIf { it.isNotBlank() }

    val configured: Configured get() = if (planKey != null) Configured.SET_UP else Configured.NOT_READY

    val sent: Sent
        get() = when {
            sentKey == null -> Sent.NOT_SENT
            planKey == null -> Sent.SENT_NOT_COMPARED
            sentKey == planKey -> Sent.SENT
            else -> Sent.CHANGES_NOT_SENT
        }

    val keyMapper: KeyMapper
        get() = when (listening) {
            true -> KeyMapper.LISTENING
            false -> KeyMapper.OFF
            null -> KeyMapper.UNKNOWN
        }

    val penActions: PenActions get() = if (penEnabled) PenActions.ON_AS_REQUESTED else PenActions.OFF_AS_REQUESTED

    val expertMode: ExpertMode get() = ExpertMode.CANT_BE_CHECKED

    val working: Working get() = Working.NOT_OBSERVABLE

    /** Whether to offer Send: there is a profile and Key Mapper doesn't have it yet. */
    val needsSend: Boolean get() = configured == Configured.SET_UP && sent != Sent.SENT

    /**
     * The Configured row: "Focus Pen Pro · Expert setup · 4 controls recorded", or "Pen profile not
     * ready" while there is no profile. [penName] is the pen as the Pen screen names it.
     */
    fun configuredText(penName: String, mode: PenMode, controlsSet: Int): String = when (configured) {
        Configured.SET_UP -> listOf(penName, "${modeName(mode)} setup", controlsText(controlsSet)).joinToString(SEPARATOR)
        Configured.NOT_READY -> NOT_READY_TEXT
    }

    /**
     * The Sent row: "Sent to Key Mapper 14:05", "Changes not sent yet", "Not sent to Key Mapper
     * yet", or "Last sent to Key Mapper 14:05" while there's no current profile to compare.
     * [formatTime] turns [appliedAt] into words, e.g. "14:05" (with the day too if it wasn't today);
     * without [appliedAt] the time is left out.
     */
    fun sentText(formatTime: (Long) -> String): String {
        val time = appliedAt?.let { " " + formatTime(it) }.orEmpty()
        return when (sent) {
            Sent.SENT -> "Sent to Key Mapper$time"
            Sent.CHANGES_NOT_SENT -> "Changes not sent yet"
            Sent.NOT_SENT -> "Not sent to Key Mapper yet"
            Sent.SENT_NOT_COMPARED -> "Last sent to Key Mapper$time"
        }
    }

    /** The Key Mapper row: "Key Mapper is listening" or "Key Mapper is off" (the screen adds Fix). */
    val keyMapperText: String
        get() = when (keyMapper) {
            KeyMapper.LISTENING -> "Key Mapper is listening"
            KeyMapper.OFF -> "Key Mapper is off"
            KeyMapper.UNKNOWN -> "Can't tell if Key Mapper is listening"
        }

    /** The Pen actions row's value: "On (as last requested)" or "Off (as last requested)". */
    val penActionsText: String
        get() = when (penActions) {
            PenActions.ON_AS_REQUESTED -> "On (as last requested)"
            PenActions.OFF_AS_REQUESTED -> "Off (as last requested)"
        }

    /** The same on its own, as a pill: "Pen actions on (as last requested)". */
    val penActionsLine: String
        get() = when (penActions) {
            PenActions.ON_AS_REQUESTED -> "Pen actions on (as last requested)"
            PenActions.OFF_AS_REQUESTED -> "Pen actions off (as last requested)"
        }

    /** The Expert mode row, shown with a How-to link: "Can't be checked from the tablet". */
    val expertModeText: String
        get() = when (expertMode) {
            ExpertMode.CANT_BE_CHECKED -> "Can't be checked from the tablet"
        }

    /**
     * The Today hero's headline, built from the rows above: "Set up and sent · Key Mapper
     * listening", plus "Pen actions off (as last requested)" while the pen is paused.
     */
    val headline: String
        get() = buildList {
            add(
                when (sent) {
                    Sent.SENT -> "Set up and sent"
                    Sent.CHANGES_NOT_SENT -> "Set up, changes not sent yet"
                    Sent.NOT_SENT -> if (configured == Configured.SET_UP) "Set up, not sent yet" else NOT_READY_TEXT
                    Sent.SENT_NOT_COMPARED -> NOT_READY_TEXT
                },
            )
            add(
                when (keyMapper) {
                    KeyMapper.LISTENING -> "Key Mapper listening"
                    KeyMapper.OFF -> "Key Mapper off"
                    KeyMapper.UNKNOWN -> "Key Mapper status unknown"
                },
            )
            if (penActions == PenActions.OFF_AS_REQUESTED) add(penActionsLine)
        }.joinToString(SEPARATOR)

    private companion object {
        const val SEPARATOR = " · "
        const val NOT_READY_TEXT = "Pen profile not ready"

        fun modeName(mode: PenMode) = when (mode) {
            PenMode.BASIC -> "Basic"
            PenMode.EXPERT -> "Expert"
        }

        // "recorded": the pen's controls PadStudio knows (recorded or measured), distinct from the actions chosen
        // for them, which the Pen screen counts as "2 of 4 have actions" (round 2: "no controls set" beside
        // "2 of 2 controls set" read as a contradiction).
        fun controlsText(count: Int) = when (val n = count.coerceAtLeast(0)) {
            0 -> "no controls recorded yet"
            1 -> "1 control recorded"
            else -> "$n controls recorded"
        }
    }
}
