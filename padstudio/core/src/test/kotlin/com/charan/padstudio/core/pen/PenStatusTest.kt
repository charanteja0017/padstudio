package com.charan.padstudio.core.pen

import com.charan.padstudio.core.pen.PenStatus.Configured
import com.charan.padstudio.core.pen.PenStatus.ExpertMode
import com.charan.padstudio.core.pen.PenStatus.KeyMapper
import com.charan.padstudio.core.pen.PenStatus.PenActions
import com.charan.padstudio.core.pen.PenStatus.Sent
import com.charan.padstudio.core.pen.PenStatus.Tone
import com.charan.padstudio.core.pen.PenStatus.Working
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Redesign plan §7: each state in its own words, and never a claim the tablet can't back. */
class PenStatusTest {
    private val sentAt = 1_760_000_000_000L
    private val clock: (Long) -> String = { if (it == sentAt) "14:05" else "at $it" }

    /** The tablet's profile hashes (plan §2): what it last sent, and what the current generator builds. */
    private val before = "bfa1ea74a187"
    private val current = "cdbc98bc0674"

    @Test fun `set up, sent and listening`() {
        val status = PenStatus(listening = true, sentHash = current, planHash = current, appliedAt = sentAt, penEnabled = true)
        assertEquals(Configured.SET_UP, status.configured)
        assertEquals(Sent.SENT, status.sent)
        assertEquals(KeyMapper.LISTENING, status.keyMapper)
        assertEquals(PenActions.ON_AS_REQUESTED, status.penActions)
        assertEquals(ExpertMode.CANT_BE_CHECKED, status.expertMode)
        assertEquals(Working.NOT_OBSERVABLE, status.working)
        assertEquals("Focus Pen Pro · Expert setup · 4 controls set", status.configuredText("Focus Pen Pro", PenMode.EXPERT, 4))
        assertEquals("Sent to Key Mapper 14:05", status.sentText(clock))
        assertEquals("Key Mapper is listening", status.keyMapperText)
        assertEquals("On (as last requested)", status.penActionsText)
        assertEquals("Pen actions on (as last requested)", status.penActionsLine)
        assertEquals("Can't be checked from the tablet", status.expertModeText)
        assertEquals("Set up and sent · Key Mapper listening", status.headline)
        assertFalse(status.needsSend)
    }

    @Test fun `the tablet today - sent before a rename, so changes are not sent yet`() {
        val status = PenStatus(listening = true, sentHash = before, planHash = current, appliedAt = null, penEnabled = true)
        assertEquals(Sent.CHANGES_NOT_SENT, status.sent)
        assertEquals("Changes not sent yet", status.sentText(clock))
        assertEquals("Set up, changes not sent yet · Key Mapper listening", status.headline)
        assertTrue(status.needsSend)
    }

    @Test fun `never sent`() {
        for (sentHash in listOf(null, "", " ")) {
            val status = PenStatus(listening = true, sentHash = sentHash, planHash = current, appliedAt = null, penEnabled = true)
            assertEquals(Sent.NOT_SENT, status.sent, "\"$sentHash\"")
            assertEquals("Not sent to Key Mapper yet", status.sentText(clock))
            assertEquals("Set up, not sent yet · Key Mapper listening", status.headline)
            assertTrue(status.needsSend)
        }
    }

    @Test fun `sent with no time recorded leaves the time out`() {
        val status = PenStatus(listening = true, sentHash = current, planHash = current, appliedAt = null, penEnabled = true)
        assertEquals("Sent to Key Mapper", status.sentText { error("no time to format") })
    }

    @Test fun `no profile yet - nothing to compare and nothing to send`() {
        val sentBefore = PenStatus(listening = true, sentHash = current, planHash = null, appliedAt = sentAt, penEnabled = true)
        assertEquals(Configured.NOT_READY, sentBefore.configured)
        assertEquals(Sent.SENT_NOT_COMPARED, sentBefore.sent)
        assertEquals("Last sent to Key Mapper 14:05", sentBefore.sentText(clock))
        assertEquals("Pen profile not ready", sentBefore.configuredText("Focus Pen Pro", PenMode.EXPERT, 4))
        assertEquals("Pen profile not ready · Key Mapper listening", sentBefore.headline)
        assertFalse(sentBefore.needsSend)

        val neverSent = PenStatus(listening = true, sentHash = null, planHash = "", appliedAt = null, penEnabled = true)
        assertEquals(Configured.NOT_READY, neverSent.configured)
        assertEquals(Sent.NOT_SENT, neverSent.sent)
        assertEquals("Pen profile not ready · Key Mapper listening", neverSent.headline)
        assertFalse(neverSent.needsSend)
    }

    @Test fun `key mapper off or unknown`() {
        val off = PenStatus(listening = false, sentHash = current, planHash = current, appliedAt = sentAt, penEnabled = true)
        assertEquals(KeyMapper.OFF, off.keyMapper)
        assertEquals("Key Mapper is off", off.keyMapperText)
        assertEquals("Set up and sent · Key Mapper off", off.headline)

        val unknown = off.copy(listening = null)
        assertEquals(KeyMapper.UNKNOWN, unknown.keyMapper)
        assertEquals("Can't tell if Key Mapper is listening", unknown.keyMapperText)
        assertEquals("Set up and sent · Key Mapper status unknown", unknown.headline)
    }

    @Test fun `pen actions are only what was last requested`() {
        val paused = PenStatus(listening = true, sentHash = current, planHash = current, appliedAt = sentAt, penEnabled = false)
        assertEquals(PenActions.OFF_AS_REQUESTED, paused.penActions)
        assertEquals("Off (as last requested)", paused.penActionsText)
        assertEquals("Pen actions off (as last requested)", paused.penActionsLine)
        assertEquals("Set up and sent · Key Mapper listening · Pen actions off (as last requested)", paused.headline)
    }

    @Test fun `the configured line counts controls in plain words`() {
        val status = PenStatus(listening = true, sentHash = null, planHash = current, appliedAt = null, penEnabled = true)
        assertEquals("Smart Pen 2 · Basic setup · 2 controls set", status.configuredText("Smart Pen 2", PenMode.BASIC, 2))
        assertEquals("Focus Pen Pro · Expert setup · 1 control set", status.configuredText("Focus Pen Pro", PenMode.EXPERT, 1))
        assertEquals("Focus Pen Pro · Expert setup · no controls set", status.configuredText("Focus Pen Pro", PenMode.EXPERT, 0))
    }

    @Test fun `tones match the design kit's pills`() {
        assertEquals(listOf(Tone.GOOD, Tone.UNKNOWN), Configured.entries.map { it.tone })
        assertEquals(listOf(Tone.GOOD, Tone.WARN, Tone.WARN, Tone.UNKNOWN), Sent.entries.map { it.tone })
        assertEquals(listOf(Tone.GOOD, Tone.BAD, Tone.UNKNOWN), KeyMapper.entries.map { it.tone })
        assertEquals(listOf(Tone.UNKNOWN, Tone.UNKNOWN), PenActions.entries.map { it.tone }, "kit.html: the pen-actions pill is 'unknown'")
        assertEquals(listOf(Tone.UNKNOWN), ExpertMode.entries.map { it.tone })
        assertEquals(listOf(Tone.UNKNOWN), Working.entries.map { it.tone })
    }

    /** Claims plan §7 rules out: nothing is verified, Expert mode is never shown running, and working is never claimed. */
    private val forbidden = listOf("verified", "running", "expert mode on", "working", "grabbed")

    private fun claims(text: String) = forbidden.filter { text.contains(it, ignoreCase = true) }

    @Test fun `no input makes it claim more than the tablet can see`() {
        // The detector must be able to fail, or a clean scan proves nothing.
        assertEquals(listOf("verified", "running", "expert mode on"), claims("Verified · Expert running · Expert Mode On"))
        assertEquals(emptyList(), claims("Set up and sent · Key Mapper listening"))

        val texts = mutableSetOf<String>()
        val states = mutableSetOf<Enum<*>>()
        var statuses = 0
        for (listening in listOf(true, false, null)) {
            for (sentHash in listOf(null, "", before, current)) {
                for (planHash in listOf(null, "", before, current)) {
                    for (appliedAt in listOf(null, 0L, sentAt)) {
                        for (penEnabled in listOf(true, false)) {
                            val status = PenStatus(listening, sentHash, planHash, appliedAt, penEnabled)
                            statuses++
                            states += listOf(status.configured, status.sent, status.keyMapper, status.penActions, status.expertMode, status.working)
                            val words = listOf(
                                status.headline, status.sentText(clock), status.keyMapperText, status.penActionsText,
                                status.penActionsLine, status.expertModeText,
                            ) + PenMode.entries.flatMap { mode ->
                                (0..4).flatMap { n -> listOf("Focus Pen Pro", "Smart Pen 2").map { status.configuredText(it, mode, n) } }
                            }
                            for (text in words) assertEquals(emptyList(), claims(text), "\"$text\" from $status")
                            texts += words
                        }
                    }
                }
            }
        }
        assertEquals(3 * 4 * 4 * 3 * 2, statuses)
        // Every state of every row was reached, so the scan read all of the wording.
        val everyState = Configured.entries + Sent.entries + KeyMapper.entries + PenActions.entries + ExpertMode.entries + Working.entries
        assertEquals(everyState.toSet<Enum<*>>(), states)
        val section7 = listOf(
            "Sent to Key Mapper 14:05", "Changes not sent yet", "Key Mapper is listening", "Key Mapper is off",
            "On (as last requested)", "Off (as last requested)", "Can't be checked from the tablet",
            "Set up and sent · Key Mapper listening", "Focus Pen Pro · Expert setup · 4 controls set",
        )
        for (text in section7) assertTrue(text in texts, "the scan covered \"$text\"")
    }
}
