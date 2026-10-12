package com.charan.padstudio.today

import com.charan.padstudio.HealthCheck
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.core.home.GridSpec
import com.charan.padstudio.core.home.GroupPlanner
import com.charan.padstudio.core.home.PlannedGroup
import com.charan.padstudio.core.home.WidgetLayout
import com.charan.padstudio.core.pen.Binding
import com.charan.padstudio.core.pen.DefaultProfiles
import com.charan.padstudio.core.pen.HomeScreenFacts
import com.charan.padstudio.core.pen.PenContext
import com.charan.padstudio.core.pen.PenControl
import com.charan.padstudio.core.pen.PenIntent
import com.charan.padstudio.core.pen.PenKind
import com.charan.padstudio.core.pen.PenMode
import com.charan.padstudio.core.pen.PenProfile
import com.charan.padstudio.core.pen.PenStatus
import com.charan.padstudio.core.pen.Press
import com.charan.padstudio.data.IconStyle
import com.charan.padstudio.data.StudioConfig
import com.charan.padstudio.setup.CheckFacts
import com.charan.padstudio.setup.plainWords
import com.charan.padstudio.ui.components.StatusLevel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// What the Today screen shows, worked out from the ViewModel's state as plain functions (unit-tested in
// TodayStateTest). Status words come from core's PenStatus (plan §7); the rest is display copy for this screen
// only: none of it feeds the Key Mapper profile, so no :core label changes.

// ---- Hero: the pen and its status (plan §7) ---------------------------------------------------------------

/** PenStatus from the inputs plan §7 names: the Key Mapper service, the sent and current profile hashes, when it was sent, and Pause/Resume. */
internal fun todayPenStatus(listening: Boolean?, config: StudioConfig, planHash: String?): PenStatus = PenStatus(
    listening = listening,
    sentHash = config.appliedProfileHash,
    planHash = planHash,
    appliedAt = config.appliedAt,
    penEnabled = config.penEnabled,
)

/** The pen as Today names it: the Focus Pen Pro by name; Smart Pen 2 and Focus Pen share the two-button setup. */
internal fun penName(kind: PenKind): String = when (kind) {
    PenKind.FOCUS_PEN_PRO -> "Focus Pen Pro"
    PenKind.TWO_BUTTON -> "Two-button pen"
}

/** The controls a pen kind has, in the order the Pen screen lists them. */
internal fun penControls(kind: PenKind): List<PenControl> = when (kind) {
    PenKind.TWO_BUTTON -> listOf(PenControl.UPPER, PenControl.LOWER)
    PenKind.FOCUS_PEN_PRO -> listOf(PenControl.PINCH, PenControl.SLIDE_UP, PenControl.SLIDE_DOWN, PenControl.DOUBLE_TAP)
}

/** How many of the pen's controls have a trigger, recorded here or measured by the probe ([recorded] = PenPlan.controls keys). */
internal fun controlsSet(kind: PenKind, recorded: Set<String>): Int = penControls(kind).count { it.id in recorded }

/** The hero's three text lines, cut from [PenStatus.configuredText]: "Your pen · Expert setup", "Focus Pen Pro", "4 controls recorded". */
internal data class HeroText(val overline: String, val title: String, val subtitle: String)

internal fun heroText(status: PenStatus, penName: String, mode: PenMode, controlsSet: Int): HeroText {
    val pieces = status.configuredText(penName, mode, controlsSet).split(SEPARATOR)
    return if (status.configured == PenStatus.Configured.SET_UP && pieces.size >= 3) {
        HeroText(overline = "$YOUR_PEN$SEPARATOR${pieces[1]}", title = pieces[0], subtitle = pieces.drop(2).joinToString(SEPARATOR))
    } else {
        // "Pen profile not ready": the pen's name still titles the card.
        HeroText(overline = YOUR_PEN, title = penName, subtitle = pieces.joinToString(SEPARATOR))
    }
}

internal fun PenStatus.Tone.level(): StatusLevel = when (this) {
    PenStatus.Tone.GOOD -> StatusLevel.Good
    PenStatus.Tone.WARN -> StatusLevel.Warn
    PenStatus.Tone.BAD -> StatusLevel.Bad
    PenStatus.Tone.UNKNOWN -> StatusLevel.Unknown
}

/**
 * The headline pill's level: Key Mapper off is bad; with no profile yet nothing is known (not a warning, so
 * the pill doesn't flash amber while the profile is built at start); unsent changes warn. Pen actions on/off
 * is only what was asked for, so it never colours the headline.
 */
internal fun headlineLevel(status: PenStatus): StatusLevel = when {
    status.keyMapper == PenStatus.KeyMapper.OFF -> StatusLevel.Bad
    status.configured == PenStatus.Configured.NOT_READY -> StatusLevel.Unknown
    status.sent.tone == PenStatus.Tone.WARN -> StatusLevel.Warn
    status.keyMapper.tone == PenStatus.Tone.UNKNOWN || status.sent.tone == PenStatus.Tone.UNKNOWN -> StatusLevel.Unknown
    else -> StatusLevel.Good
}

/**
 * When the profile was sent, for [PenStatus.sentText]: the time only if it was today, else the day too
 * ("10 Oct, 14:05"). [time] and [day] do the locale's formatting (DateFormat.getTimeFormat follows the
 * tablet's 12/24-hour setting); [day] gets whether the year is this year, so another year can show it.
 */
internal fun sentTimeText(at: Long, now: Long, zone: ZoneId, time: (Long) -> String, day: (Long, Boolean) -> String): String {
    val sentOn = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return if (sentOn == today) time(at) else "${day(at, sentOn.year == today.year)}, ${time(at)}"
}

/** What the hero callouts and their list show for each gesture. [clip] and [marker] are the pen model's names; null for buttons the model doesn't have. */
internal data class HeroGesture(
    val controlId: String,
    val label: String,
    val action: String,
    val intent: PenIntent?,
    val clip: String?,
    val marker: String?,
)

/**
 * The gestures the hero calls out, as in the reference: double tap and squeeze on the Focus Pen Pro, the two
 * buttons on a two-button pen. Each is named with what it does "everywhere else" (the general meaning);
 * when that context has nothing for it, with Camera's action ("Shutter in Camera"); else "Not set".
 */
internal fun heroGestures(kind: PenKind, bindings: List<Binding>): List<HeroGesture> = when (kind) {
    PenKind.FOCUS_PEN_PRO -> listOf(
        gesture(PenControl.DOUBLE_TAP, bindings, clip = "double_tap", marker = "marker_double_tap"),
        gesture(PenControl.PINCH, bindings, clip = "squeeze", marker = "marker_squeeze"),
    )
    PenKind.TWO_BUTTON -> listOf(
        gesture(PenControl.UPPER, bindings, clip = null, marker = null),
        gesture(PenControl.LOWER, bindings, clip = null, marker = null),
    )
}

private fun gesture(control: PenControl, bindings: List<Binding>, clip: String?, marker: String?): HeroGesture {
    fun bound(contextId: String) = bindings.firstOrNull { it.contextId == contextId && it.control == control.id && it.press == Press.SHORT }
    val everywhere = bound(PenProfile.EVERYWHERE)
    val camera = bound(DefaultProfiles.CAMERA.id)
    val action = when {
        everywhere != null -> everywhere.intent.label
        camera != null -> "${camera.intent.label} in ${DefaultProfiles.CAMERA.title}"
        else -> NOT_SET
    }
    return HeroGesture(control.id, control.label, action, (everywhere ?: camera)?.intent, clip, marker)
}

/**
 * How much of a pen gesture has played, counted as PenModelView's own clock counts it: frame time while the pen
 * moves, at most [MAX_FRAME_STEP_NANOS] a frame, and nothing across a [hold]. So a gesture held by Pause, by the
 * app going to the background (no frames come) or by the pen being scrolled out of sight ends here when the pen
 * finishes it, rather than on a wall-clock timer that runs on while the pen stands still.
 */
internal class GestureClock(private val lengthNanos: Long) {
    private var lastFrame = 0L

    var playedNanos = 0L
        private set

    val done: Boolean get() = playedNanos >= lengthNanos

    fun frame(frameNanos: Long) {
        if (lastFrame != 0L) playedNanos += (frameNanos - lastFrame).coerceIn(0L, MAX_FRAME_STEP_NANOS)
        lastFrame = frameNanos
    }

    /** The pen stopped moving: the next frame starts counting afresh. */
    fun hold() {
        lastFrame = 0L
    }
}

/** PenModelView's clip clock counts at most 0.1 s a frame. */
internal const val MAX_FRAME_STEP_NANOS = 100_000_000L

/** The bindings in force: the saved ones, else the defaults for this pen and mode (as PenStudio.bindings()). */
internal fun currentBindings(config: StudioConfig): List<Binding> = config.bindings ?: DefaultProfiles.bindings(config.penKind, config.penMode)

// ---- Quick actions: pending, requested and failed (plan §13) ----------------------------------------------

/** The two hero actions whose outcome Today reports in place. */
internal enum class HeroAction { SEND, PEN_ON_OFF }

/** What a failed action offers: run it again, or go where it can be fixed. */
internal enum class Remedy(val label: String) {
    RETRY("Retry"),
    OPEN_SETUP("Fix"),
    OPEN_PEN("Fix"),
}

/**
 * The line under the hero's buttons after an action: pending, unknown ("Requested") or failed with a way
 * out. Success goes to the shell's snackbar with the time; [Done] shows it here only where there is none.
 */
internal sealed interface HeroNote {
    data class Pending(val text: String) : HeroNote

    data class Done(val text: String) : HeroNote

    data class Requested(val text: String) : HeroNote

    data class Failed(val reason: String, val remedy: Remedy, val action: HeroAction) : HeroNote
}

internal const val OPENING_KEY_MAPPER = "Opening Key Mapper…"

/**
 * Why Send to Key Mapper gave nothing to open, checked in the order MainViewModel.applyToKeyMapper checks it
 * (the ViewModel's own message says it too, as a snackbar). With all three fine, the intent was made but
 * nothing on the tablet opened it: "Key Mapper didn't open." with Retry.
 */
internal fun sendFailure(keyMapperInstalled: Boolean, profileReady: Boolean, hasKeyMaps: Boolean): HeroNote.Failed = when {
    !profileReady -> HeroNote.Failed("The pen profile isn't ready yet.", Remedy.RETRY, HeroAction.SEND)
    !keyMapperInstalled -> HeroNote.Failed("Key Mapper isn't installed.", Remedy.OPEN_SETUP, HeroAction.SEND)
    !hasKeyMaps -> HeroNote.Failed("No pen buttons are recorded or measured yet.", Remedy.OPEN_PEN, HeroAction.SEND)
    else -> HeroNote.Failed("Key Mapper didn't open.", Remedy.RETRY, HeroAction.SEND)
}

/**
 * After Pause or Resume pen: when the saved request changed, the broadcasts went out and the result is
 * "Requested" (Key Mapper can't be asked, plan §7); otherwise it failed, for the reasons
 * MainViewModel.setPenEnabled checks. [time] is the request's time in the tablet's format.
 */
internal fun penRequestNote(requestedOn: Boolean, savedOn: Boolean, keyMapperInstalled: Boolean, time: String): HeroNote = when {
    savedOn == requestedOn -> HeroNote.Requested(
        "${if (requestedOn) "Resume" else "Pause"} requested at $time. Key Mapper can't confirm it.",
    )
    !keyMapperInstalled -> HeroNote.Failed("Key Mapper isn't installed.", Remedy.OPEN_SETUP, HeroAction.PEN_ON_OFF)
    else -> HeroNote.Failed("The pen profile isn't ready yet.", Remedy.RETRY, HeroAction.PEN_ON_OFF)
}

/** The Pause/Resume button's label, from what was last asked for. */
internal fun penToggleLabel(penEnabled: Boolean): String = if (penEnabled) "Pause pen" else "Resume pen"

// ---- Pen by app --------------------------------------------------------------------------------------------

/** One row of "Pen by app": a context, its installed apps and what the pen does there, in words. */
internal data class ContextLine(val id: String, val title: String, val words: String, val packages: List<String>)

/**
 * The contexts with an installed app, in the profile's order, pass-through notes apps last. [contexts] are
 * PenPlan.contexts (already limited to installed apps); [bindings] are the bindings in force.
 */
internal fun penByApp(contexts: List<PenContext>, bindings: List<Binding>): List<ContextLine> {
    val shown = contexts.filter { it.packages.isNotEmpty() && it.id != PenProfile.EVERYWHERE }
    return (shown.filterNot { it.passThrough } + shown.filter { it.passThrough }).map { context ->
        ContextLine(
            id = context.id,
            title = contextTitle(context),
            words = if (context.passThrough) PASS_THROUGH else bindingsInWords(bindings.filter { it.contextId == context.id }),
            packages = context.packages,
        )
    }
}

/**
 * Display titles. "Notes with their own pen settings" wraps to four lines at 125 % Bold, so it reads "Own pen
 * settings" (SCREENS-BRIEF); with that beside it, "Other notes" is plain "Notes", as in the Today mock.
 */
internal fun contextTitle(context: PenContext): String = when (context.id) {
    DefaultProfiles.NATIVE_NOTES.id -> "Own pen settings"
    DefaultProfiles.OTHER_NOTES.id -> "Notes"
    else -> context.title
}

/**
 * One context's bindings as a sentence: gestures that do the same thing share it ("Squeeze or double tap for
 * a photo"), in control order, then press order; "No pen actions set yet" when there are none.
 */
internal fun bindingsInWords(bindings: List<Binding>): String {
    if (bindings.isEmpty()) return NO_PEN_ACTIONS
    val ordered = bindings.sortedWith(compareBy<Binding>({ controlRank(it.control) }, { it.press.ordinal }))
    val byAction = LinkedHashMap<PenIntent, MutableList<Binding>>()
    ordered.forEach { byAction.getOrPut(it.intent) { mutableListOf() } += it }
    val sentence = byAction.entries.joinToString(", ") { (intent, sameAction) ->
        sameAction.joinToString(" or ") { gestureWords(it) } + " for " + actionWords(intent)
    }
    return sentence.replaceFirstChar { it.uppercaseChar() }
}

private val CONTROL_ORDER = listOf(
    PenControl.PINCH.id, PenControl.SLIDE_UP.id, PenControl.SLIDE_DOWN.id, PenControl.DOUBLE_TAP.id,
    PenControl.UPPER.id, PenControl.LOWER.id, PenControl.FOCUS.id,
)

private fun controlRank(id: String): Int = CONTROL_ORDER.indexOf(id).let { if (it < 0) CONTROL_ORDER.size else it }

private fun gestureWords(binding: Binding): String {
    val name = PenControl.byId(binding.control).label.lowercase()
    return when (binding.press) {
        Press.SHORT -> name
        Press.DOUBLE -> "$name twice"
        Press.LONG -> "hold $name"
    }
}

/** A pen action as the end of "… for ___", in plain words. */
internal fun actionWords(intent: PenIntent): String = when (intent) {
    PenIntent.SHUTTER -> "a photo"
    PenIntent.NEXT_PAGE -> "next page"
    PenIntent.PREVIOUS_PAGE -> "previous page"
    PenIntent.UNDO -> "undo"
    PenIntent.REDO -> "redo"
    PenIntent.SCREENSHOT -> "a screenshot"
    PenIntent.BACK -> "back"
    PenIntent.HOME -> "home"
    PenIntent.RECENTS -> "recent apps"
    PenIntent.SPLIT_SCREEN -> "split screen"
    PenIntent.NOTIFICATIONS -> "notifications"
    PenIntent.OPEN_CAMERA -> "the camera"
    PenIntent.OPEN_LIBRARY -> "the app library"
    PenIntent.OPEN_NOTES -> "your notes app"
    PenIntent.PLAY_PAUSE -> "play or pause"
    PenIntent.NEXT_TRACK -> "next track"
    PenIntent.PREVIOUS_TRACK -> "previous track"
    PenIntent.LAST_APP -> "the last app"
    PenIntent.QUICK_SETTINGS -> "quick settings"
    PenIntent.ASSISTANT -> "the assistant"
    PenIntent.VOLUME_UP -> "volume up"
    PenIntent.VOLUME_DOWN -> "volume down"
    PenIntent.DO_NOT_DISTURB -> "Do Not Disturb"
    PenIntent.FLASHLIGHT -> "the flashlight"
    PenIntent.ROTATION_LOCK -> "rotation lock"
}

// ---- Home screen -------------------------------------------------------------------------------------------

/** "Launcher grid: 6 × 4, names hidden · recorded 11 Oct": the recorded baseline (ADR-005), never a live reading. */
internal fun launcherGridText(facts: HomeScreenFacts, recordedDay: String): String =
    "Launcher grid: ${facts.columns} × ${facts.rows}" + (if (facts.namesHidden) ", names hidden" else "") + SEPARATOR + "recorded $recordedDay"

/** The recorded day ("2026-10-11") through [format] (given whether it is this year); the text as recorded if it isn't a date. */
internal fun recordedDayText(recordedAt: String, today: LocalDate, format: (LocalDate, Boolean) -> String): String {
    val date = runCatching { LocalDate.parse(recordedAt.trim().take(10)) }.getOrNull() ?: return recordedAt
    return format(date, date.year == today.year)
}

/** The default widget grid, as Home Kit's picker names it: "Auto" or "8 × 4". Anything unreadable counts as Auto, as widgets treat it. */
internal fun defaultGridText(widgetGrid: String?): String = GridSpec.parse(widgetGrid)?.label ?: "Auto"

/** Today's widget preview: the size it is laid out at, in dp, and the layout core gives it there. */
internal data class PreviewWidget(val widthDp: Float, val heightDp: Float, val layout: WidgetLayout)

/**
 * A widget laid out with the saved default grid, so the preview shows what widgets set to Default get.
 * HyperOS hasn't reported real widget sizes (plan §6b), so it uses the mock's 412 × 222 dp, grown to the
 * smallest size where an exact grid fits rather than showing it trimmed.
 */
internal fun previewWidget(spec: GridSpec?, density: Density, labelLineDp: Float): PreviewWidget {
    val width = maxOf(PREVIEW_WIDTH_DP, spec?.let { GroupPlanner.widthNeededDp(it.columns).toFloat() } ?: 0f)
    val height = maxOf(PREVIEW_HEIGHT_DP, spec?.let { GroupPlanner.heightNeededDp(it.rows).toFloat() } ?: 0f)
    return PreviewWidget(width, height, GroupPlanner.layoutFor(width, height, density, spec, labelLineDp))
}

/** The group the preview shows: the fullest one (the first of equals, in display order), so overflow shows when there is any. */
internal fun previewGroup(groups: List<PlannedGroup>): PlannedGroup? = groups.maxByOrNull { it.apps.size }

// ---- Make it yours -----------------------------------------------------------------------------------------

/**
 * The icon style line. [packLabel] is the saved pack's name when it is installed; until the installed packs
 * have been looked through ([packChecked] false) the line says only "Icon pack".
 */
internal fun iconStyleText(style: IconStyle, savedPack: String?, packLabel: String?, packChecked: Boolean = true): String = when (style) {
    IconStyle.THEMED -> "Themed icons"
    IconStyle.ORIGINAL -> "Original icons"
    IconStyle.ICON_PACK -> when {
        !packChecked -> "Icon pack"
        packLabel != null -> "Icon pack: $packLabel"
        savedPack != null -> "Icon pack not installed"
        else -> "No icon pack chosen"
    }
}

/** The wallpaper line: PadStudio can say it set one, not what is on the home screen now. */
internal fun wallpaperText(applied: Boolean): String = if (applied) "PadStudio wallpaper applied" else "Your own wallpaper"

internal fun coloursText(matchWallpaper: Boolean): String = if (matchWallpaper) "Colours from your wallpaper" else "PadStudio colours"

// ---- Health ------------------------------------------------------------------------------------------------

/** The eight Setup checks in brief: how many are ready, need attention or can only be checked by hand, and the first that needs attention. */
internal data class HealthSummary(val ready: Int, val total: Int, val attention: Int, val byHand: Int, val top: HealthCheck?) {
    val title: String get() = "$ready of $total ready"

    /** "1 needs attention · 1 to check by hand", or null when both are none. */
    val breakdown: String?
        get() = listOfNotNull(
            attention.takeIf { it > 0 }?.let { if (it == 1) "1 needs attention" else "$it need attention" },
            byHand.takeIf { it > 0 }?.let { "$it to check by hand" },
        ).joinToString(SEPARATOR).ifEmpty { null }
}

internal fun healthSummary(checks: List<HealthCheck>): HealthSummary = HealthSummary(
    ready = checks.count { it.ok == true },
    total = checks.size,
    attention = checks.count { it.ok == false },
    byHand = checks.count { it.ok == null },
    top = checks.firstOrNull { it.ok == false },
)

/**
 * The top issue's sentence on the Health card, in the Setup screen's plain words for the same check
 * (setup/SetupChecks.kt), so Today never shows the view model's developer detail ("Applied bfa1ea74a187;
 * current 9d3c0e1f2a3b.") or copy Setup has replaced. The profile check is worded from PenStatus (plan §7),
 * as on Setup; a check Setup doesn't know keeps the view model's own detail.
 */
internal fun healthIssueText(check: HealthCheck, status: PenStatus, formatTime: (Long) -> String): String = plainWords(
    title = check.title,
    ok = check.ok,
    detail = check.detail,
    known = CheckFacts(
        sentText = status.sentText(formatTime),
        needsSend = status.needsSend,
        sentIsCurrent = status.sent == PenStatus.Sent.SENT,
    ),
)

// ---- Layout ------------------------------------------------------------------------------------------------

/** The side column's width (the mock's 352 dp). */
internal const val END_COLUMN_DP = 352f

/**
 * Two columns from 840 dp of content width (SCREENS-BRIEF rule 6), while the side column still holds
 * [END_COLUMN_MIN_EMS] lines' worth of body text: at 200 % text it doesn't, so the page goes to one column,
 * as the mock does. [bodyEmDp] is the body text size in dp after the tablet's (non-linear) font scale.
 */
internal fun useTwoColumns(contentWidthDp: Float, bodyEmDp: Float): Boolean =
    contentWidthDp >= TWO_COLUMNS_MIN_DP && END_COLUMN_DP / bodyEmDp.coerceAtLeast(1f) >= END_COLUMN_MIN_EMS

/** How the hero lays out its text and the pen. */
internal enum class HeroArrangement {
    /** Text on the left, the pen with its callouts on the right (the reference). */
    SIDE,

    /** Text above, the pen with its callouts under it (the mock at 200 %). */
    STACKED,

    /** Text above, a smaller pen with the gestures as a list beside it (callouts don't fit). */
    LIST,
}

/** Where the pen stands in each arrangement, in dp; [initial] is the one tried first. */
internal data class HeroGeometry(
    val initial: HeroArrangement,
    val sideStageWidthDp: Float,
    val sideStageHeightDp: Float,
    val stackedStageWidthDp: Float,
    val stackedStageHeightDp: Float,
)

/**
 * Side by side when the pen's name and status keep at least 13 body-text ems (and 240 dp) beside a stage of
 * half the hero, 240–360 dp wide and 0.9 times as tall (the pen view frames the pen by its width there, so a
 * taller stage only adds empty space); otherwise the text goes on top. The stacked stage is up to 520 dp
 * wide and 300 dp tall.
 */
internal fun heroGeometry(heroWidthDp: Float, bodyEmDp: Float): HeroGeometry {
    val sideWidth = (heroWidthDp * 0.5f).coerceIn(SIDE_STAGE_MIN_DP, SIDE_STAGE_MAX_DP)
    val textRoom = heroWidthDp - sideWidth
    val side = textRoom >= maxOf(MIN_TEXT_DP, HERO_TEXT_MIN_EMS * bodyEmDp)
    return HeroGeometry(
        initial = if (side) HeroArrangement.SIDE else HeroArrangement.STACKED,
        sideStageWidthDp = sideWidth,
        sideStageHeightDp = (sideWidth * 0.9f).coerceIn(220f, 320f),
        stackedStageWidthDp = heroWidthDp.coerceAtMost(520f),
        stackedStageHeightDp = 300f,
    )
}

private const val SEPARATOR = " · "
private const val YOUR_PEN = "Your pen"
private const val NOT_SET = "Not set"
internal const val NO_PEN_ACTIONS = "No pen actions set yet"
internal const val PASS_THROUGH = "Leaves the pen to the app"
private const val PREVIEW_WIDTH_DP = 412f
private const val PREVIEW_HEIGHT_DP = 222f
private const val TWO_COLUMNS_MIN_DP = 840f
private const val END_COLUMN_MIN_EMS = 14f
private const val SIDE_STAGE_MIN_DP = 240f
private const val SIDE_STAGE_MAX_DP = 360f
private const val MIN_TEXT_DP = 240f
private const val HERO_TEXT_MIN_EMS = 13f
