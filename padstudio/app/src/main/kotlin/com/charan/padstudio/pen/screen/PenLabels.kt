package com.charan.padstudio.pen.screen

import com.charan.padstudio.core.pen.Binding
import com.charan.padstudio.core.pen.DefaultProfiles
import com.charan.padstudio.core.pen.PenContext
import com.charan.padstudio.core.pen.PenControl
import com.charan.padstudio.core.pen.PenIntent
import com.charan.padstudio.core.pen.PenKind
import com.charan.padstudio.core.pen.PenMode
import com.charan.padstudio.core.pen.PenProfile
import com.charan.padstudio.core.pen.PenTrigger
import com.charan.padstudio.core.pen.Press
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

// Plain logic behind the Pen screen (redesign plan §10 "Pen"): which controls and presses exist, what the
// screen calls things, and how bindings are looked up. Display strings only: the :core labels that feed the
// Key Mapper JSON are never changed here (plan §4).

/** The controls each pen has, in the order the screen lists them (the same as the PadStudio 1 screen). */
fun controlsFor(kind: PenKind): List<PenControl> = when (kind) {
    PenKind.TWO_BUTTON -> listOf(PenControl.UPPER, PenControl.LOWER)
    PenKind.FOCUS_PEN_PRO -> listOf(PenControl.PINCH, PenControl.SLIDE_UP, PenControl.SLIDE_DOWN, PenControl.DOUBLE_TAP)
}

/**
 * The presses [control] can be given an action for in [mode], as PadStudio 1 offered them: a gesture the pen
 * recognises itself (double tap) is one press; Expert setup adds holds, which HyperOS keeps for itself in Basic.
 */
fun pressesFor(control: PenControl, mode: PenMode): List<Press> = when {
    control.singleGesture -> listOf(Press.SHORT)
    mode == PenMode.EXPERT -> Press.entries
    else -> listOf(Press.SHORT, Press.DOUBLE)
}

/** "Press", "Hold" or "Double press". */
fun pressLabel(press: Press): String = press.label.replaceFirstChar { it.uppercase() }

/** What a binding is called on a chip: "Squeeze" for the main press (or a single gesture), else "Squeeze double press". */
fun bindingLabel(control: PenControl, press: Press): String =
    if (control.singleGesture || press == Press.SHORT) control.label else "${control.label} ${press.label}"

/** The action [bindings] give [controlId]'s [press] in [contextId], or null for "Nothing": the first match, as before. */
fun boundIntent(bindings: List<Binding>, contextId: String, controlId: String, press: Press): PenIntent? =
    bindings.firstOrNull { it.contextId == contextId && it.control == controlId && it.press == press }?.intent

/** The pen's name in status lines ("Focus Pen Pro · Expert setup · 4 controls recorded"). */
fun penName(kind: PenKind): String = when (kind) {
    PenKind.TWO_BUTTON -> "Two-button pen"
    PenKind.FOCUS_PEN_PRO -> "Focus Pen Pro"
}

/** The pen-kind choice's segment labels. */
fun penKindChoice(kind: PenKind): String = when (kind) {
    PenKind.TWO_BUTTON -> "Two buttons"
    PenKind.FOCUS_PEN_PRO -> "Focus Pen Pro"
}

/** One line under the pen-kind choice. */
fun penKindDetail(kind: PenKind): String = when (kind) {
    PenKind.TWO_BUTTON -> "Smart Pen 2 or Focus Pen: an upper and a lower button"
    PenKind.FOCUS_PEN_PRO -> "Squeeze, slide up, slide down and double tap"
}

/** "Basic setup" or "Expert setup", as the setup-mode chip and PenStatus name it. */
fun setupName(mode: PenMode): String = when (mode) {
    PenMode.BASIC -> "Basic setup"
    PenMode.EXPERT -> "Expert setup"
}

/** What each setup means, in the words of PadStudio 1's Pen screen. */
fun setupDetail(mode: PenMode): String = when (mode) {
    PenMode.BASIC ->
        "Nothing to redo after a reboot. Presses HyperOS keeps for itself (holds, Focus Pen Pro gestures) can't be changed."
    PenMode.EXPERT ->
        "Key Mapper's Expert Mode takes the pen's input device, so every press can be mapped. Needs wireless debugging; restarts itself on Wi-Fi."
}

/**
 * The contexts the screen lists: "Everywhere else" first, then every context with installed apps that gets
 * key maps, then the pass-through ones (the apps that keep the pen). Contexts with no installed apps are left
 * out, as before: the generator skips them.
 */
fun screenContexts(planContexts: List<PenContext>): List<PenContext> =
    listOf(PenContext(PenProfile.EVERYWHERE, EVERYWHERE_TITLE, emptyList())) +
        planContexts.filter { it.packages.isNotEmpty() && !it.passThrough } +
        planContexts.filter { it.packages.isNotEmpty() && it.passThrough }

/** A context's name and supporting line on screen. */
data class ContextLabels(val title: String, val supporting: String)

/**
 * The screen's words for [context]. The pass-through context's core title ("Notes with their own pen
 * settings") wraps to four lines at 125 % bold text, so it shows as "Own pen settings" (W1 note); the core
 * label itself is unchanged.
 */
fun contextLabels(context: PenContext): ContextLabels = when {
    context.id == PenProfile.EVERYWHERE -> ContextLabels(EVERYWHERE_TITLE, "All other apps")
    context.passThrough -> ContextLabels("Own pen settings", "Leaves the pen to the app")
    context.id == DefaultProfiles.HOME.id -> ContextLabels(context.title, "Your home screen")
    else -> ContextLabels(context.title, appCount(context.packages.size))
}

/** "1 app", "3 apps". */
fun appCount(count: Int): String = if (count == 1) "1 app" else "$count apps"

/** How many of [controls] have at least one action in [contextId]. */
fun controlsWithActions(bindings: List<Binding>, contextId: String, controls: List<PenControl>): Int =
    controls.count { control -> bindings.any { it.contextId == contextId && it.control == control.id } }

/** The hero's line under a context: "2 of 4 have actions", or "No pen actions set yet" (actions, not recorded controls). */
fun controlsSetLine(set: Int, total: Int): String = if (set == 0) "No pen actions set yet" else "$set of $total have actions"

/** How many of the pen's controls Key Mapper can recognise: recorded here or measured by the probe. */
fun controlsKnown(controls: List<PenControl>, triggers: Map<String, PenTrigger>): Int = controls.count { triggers[it.id] != null }

/** A trigger for Developer details: "key 332 · scan 189 · Xiaomi Focus Pen Pro Keyboard". */
fun describeTrigger(trigger: PenTrigger?): String = when (trigger) {
    null -> "Not recorded"
    is PenTrigger.KeyEvent -> listOfNotNull("key ${trigger.keyCode}", trigger.scanCode?.let { "scan $it" }, trigger.deviceName).joinToString(SEPARATOR)
    is PenTrigger.Evdev -> "evdev ${trigger.keyCode}/${trigger.scanCode}$SEPARATOR${trigger.device.name}"
}

/** The input devices behind [triggers], once each: "Xiaomi Focus Pen Pro Keyboard · bus 5 · 2717:5081". */
fun describeDevices(triggers: Collection<PenTrigger>): List<String> = triggers.map { trigger ->
    when (trigger) {
        is PenTrigger.KeyEvent -> trigger.deviceName
        is PenTrigger.Evdev -> "${trigger.device.name}${SEPARATOR}bus ${trigger.device.bus}$SEPARATOR" +
            String.format(Locale.ROOT, "%04x:%04x", trigger.device.vendor, trigger.device.product)
    }
}.distinct()

/** What Developer details offers for one control: record it, record it again, or nothing (measured over adb). */
enum class RecordChoice { RECORD, RECORD_AGAIN, MEASURED }

/**
 * As PadStudio 1 decided it: in Expert setup a control measured by the probe (tools/capture-pen.sh) can't be
 * recorded in the app, because HyperOS keeps those presses from app windows.
 */
fun recordChoice(recorded: PenTrigger?, measured: Boolean, mode: PenMode): RecordChoice = when {
    mode == PenMode.EXPERT && measured -> RecordChoice.MEASURED
    recorded == null -> RecordChoice.RECORD
    else -> RecordChoice.RECORD_AGAIN
}

/** The recording card's instruction for [control]. */
fun recordPrompt(control: PenControl): String = when (control.id) {
    PenControl.PINCH.id -> "Squeeze the pen now…"
    PenControl.SLIDE_UP.id -> "Slide up on the pen now…"
    PenControl.SLIDE_DOWN.id -> "Slide down on the pen now…"
    PenControl.DOUBLE_TAP.id -> "Double-tap the pen now…"
    else -> "Press the ${control.label.lowercase()} now…"
}

/** The PenModelView clip that shows [controlId]'s gesture, or "idle" for a control the model has no clip for. */
fun clipFor(controlId: String): String = when (controlId) {
    PenControl.PINCH.id -> "squeeze"
    PenControl.SLIDE_UP.id -> "slide_up"
    PenControl.SLIDE_DOWN.id -> "slide_down"
    PenControl.DOUBLE_TAP.id -> "double_tap"
    else -> "idle"
}

/** The model's marker node for [controlId] (docs/redesign/APP-BRIEF.md), or null. */
fun markerFor(controlId: String): String? = when (controlId) {
    PenControl.PINCH.id -> "marker_squeeze"
    PenControl.SLIDE_UP.id -> "marker_slide_top"
    PenControl.SLIDE_DOWN.id -> "marker_slide_bottom"
    PenControl.DOUBLE_TAP.id -> "marker_double_tap"
    else -> null
}

/**
 * When something was sent, for PenStatus.sentText: [time] alone ("14:05") on the day it happened, with [day]
 * before it ("11 Oct 14:05") on any other day. [time] follows the device's 12/24-hour setting.
 */
fun sentTimeLabel(epochMs: Long, nowMs: Long, zone: ZoneId, time: (Long) -> String, day: (Long) -> String): String {
    val then = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return if (then == today) time(epochMs) else "${day(epochMs)} ${time(epochMs)}"
}

/** What "any app not in another context" is called, as the generator and PadStudio 1 call it. */
const val EVERYWHERE_TITLE = "Everywhere else"

private const val SEPARATOR = " · "
