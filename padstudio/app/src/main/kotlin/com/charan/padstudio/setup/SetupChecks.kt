package com.charan.padstudio.setup

import com.charan.padstudio.core.pen.FactsFile
import com.charan.padstudio.core.pen.HomeScreenFacts
import com.charan.padstudio.core.pen.PenControl
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Locale

// The Setup screen's logic (plan §10 "Setup"), kept free of Android types so SetupChecksTest runs it on the JVM:
// - which area each of MainViewModel's health checks belongs to (Pen, Home, Looks; anything else goes to Other);
// - the "N of 8 ready" tally and its legend;
// - each check's sentence in plain words, and the device-facts lines under Developer.
// The checks themselves (title, ok, detail, Fix and its intent) stay MainViewModel.healthChecks()'s.

/** The titles MainViewModel.healthChecks() gives its eight checks, matched here to group them and word them. */
object CheckTitles {
    const val KEY_MAPPER_INSTALLED = "Key Mapper installed"
    const val KEY_MAPPER_SEES_PEN = "Key Mapper can see the pen"
    const val PROFILE_CURRENT = "Pen profile is current"
    const val HYPEROS_KEEPS_KEY_MAPPER = "HyperOS keeps Key Mapper running"
    const val FACTS_IMPORTED = "Device facts imported"
    const val WIDGETS_PLACED = "Group widgets on the home screen"
    const val THEMED_ICONS = "Themed icon source"
    const val WALLPAPER = "PadStudio wallpaper"
}

/** Setup's groups, in page order. [OTHER] holds every check whose title isn't one of [CheckTitles], so none is dropped. */
enum class CheckArea(val title: String) {
    PEN("Pen"),
    HOME("Home"),
    LOOKS("Looks"),
    OTHER("Other"),
}

/** Titles are matched ignoring case and surrounding spaces. */
private fun titleKey(title: String) = title.trim().lowercase(Locale.ROOT)

private val areaByTitle: Map<String, CheckArea> = mapOf(
    CheckTitles.KEY_MAPPER_INSTALLED to CheckArea.PEN,
    CheckTitles.KEY_MAPPER_SEES_PEN to CheckArea.PEN,
    CheckTitles.PROFILE_CURRENT to CheckArea.PEN,
    CheckTitles.HYPEROS_KEEPS_KEY_MAPPER to CheckArea.PEN,
    CheckTitles.FACTS_IMPORTED to CheckArea.PEN,
    CheckTitles.WIDGETS_PLACED to CheckArea.HOME,
    CheckTitles.THEMED_ICONS to CheckArea.LOOKS,
    CheckTitles.WALLPAPER to CheckArea.LOOKS,
).mapKeys { (title, _) -> titleKey(title) }

/** The area a check with this [title] belongs to; [CheckArea.OTHER] for a title Setup doesn't know. */
fun checkArea(title: String): CheckArea = areaByTitle[titleKey(title)] ?: CheckArea.OTHER

/** Whether [title] is the check titled [known] (one of [CheckTitles]), matched as [checkArea] matches. */
fun isCheck(title: String, known: String): Boolean = titleKey(title) == titleKey(known)

/** The checks of one [area], in the order they came. */
data class AreaGroup<T>(val area: CheckArea, val items: List<T>)

/** [items] grouped by the area of their [title], areas in [CheckArea] order, each keeping its items' order; empty areas are left out. */
fun <T> groupByArea(items: List<T>, title: (T) -> String): List<AreaGroup<T>> {
    val byArea = items.groupBy { checkArea(title(it)) }
    return CheckArea.entries.mapNotNull { area -> byArea[area]?.let { AreaGroup(area, it) } }
}

/** What a check's `ok` says: true is ready, false needs attention, null can't be read by an app (check by hand). */
enum class CheckState(val label: String) {
    READY("Ready"),
    ATTENTION("Needs attention"),
    BY_HAND("Check by hand"),
}

fun checkState(ok: Boolean?): CheckState = when (ok) {
    true -> CheckState.READY
    false -> CheckState.ATTENTION
    null -> CheckState.BY_HAND
}

/** How many checks are in each state. Only ready ones count towards "N of 8 ready": a check by hand is never counted as ready. */
data class SetupTally(val ready: Int, val attention: Int, val byHand: Int) {
    val total: Int get() = ready + attention + byHand

    /** The ring's fill, 0..1. */
    val progress: Float get() = if (total == 0) 0f else ready.toFloat() / total

    /** "6 of 8 ready". */
    val headline: String get() = "$ready of $total ready"

    /** "6 ready", then "1 needs attention" and "1 to check by hand" when there are any, each with its state for the symbol. */
    val legend: List<Pair<CheckState, String>>
        get() = buildList {
            add(CheckState.READY to "$ready ready")
            if (attention > 0) add(CheckState.ATTENTION to if (attention == 1) "1 needs attention" else "$attention need attention")
            if (byHand > 0) add(CheckState.BY_HAND to "$byHand to check by hand")
        }

    /** What TalkBack says for the ring: "6 of 8 ready, 1 needs attention, 1 to check by hand". */
    val spoken: String get() = (listOf(headline) + legend.drop(1).map { it.second }).joinToString(", ")
}

fun tally(oks: Iterable<Boolean?>): SetupTally {
    var ready = 0
    var attention = 0
    var byHand = 0
    oks.forEach { ok ->
        when (checkState(ok)) {
            CheckState.READY -> ready++
            CheckState.ATTENTION -> attention++
            CheckState.BY_HAND -> byHand++
        }
    }
    return SetupTally(ready, attention, byHand)
}

/** The check to show first in the summary: the first that needs attention (the view model lists the pen's first). */
fun <T> topIssue(items: List<T>, ok: (T) -> Boolean?): T? = items.firstOrNull { ok(it) == false }

/** The page subtitle: "Eight checks for your pen, home screen and looks." */
fun setupSubtitle(count: Int): String {
    val what = "for your pen, home screen and looks."
    return when {
        count <= 0 -> "Checks $what"
        count == 1 -> "One check $what"
        else -> "${COUNT_WORDS.getOrElse(count) { count.toString() }} checks $what"
    }
}

private val COUNT_WORDS = mapOf(
    2 to "Two", 3 to "Three", 4 to "Four", 5 to "Five", 6 to "Six",
    7 to "Seven", 8 to "Eight", 9 to "Nine", 10 to "Ten", 11 to "Eleven", 12 to "Twelve",
)

/**
 * What Setup read beyond the checks themselves, for their sentences. Null (or false for [needsSend]) means not
 * known yet; a check whose fact is missing falls back to the view model's own detail text.
 *
 * @property sentText PenStatus.sentText: "Sent to Key Mapper 14:05", "Changes not sent yet", ...
 * @property needsSend PenStatus.needsSend: there is a profile Key Mapper doesn't have yet.
 */
data class CheckFacts(
    val keyMapperVersion: String? = null,
    val sentText: String? = null,
    val needsSend: Boolean = false,
    val widgetsPlaced: Int? = null,
    val lawnicons: Boolean? = null,
    val facts: FactsSummary? = null,
)

/**
 * A check's one sentence in plain words (the mock-up's wording where it has one). An unknown title, or a check
 * whose facts aren't known, keeps the view model's [detail]. Status words come from PenStatus (plan §7).
 */
fun plainWords(title: String, ok: Boolean?, detail: String, known: CheckFacts): String = typographic(words(title, ok, detail, known))

private fun words(title: String, ok: Boolean?, detail: String, known: CheckFacts): String {
    val state = checkState(ok)
    return when (titleKey(title)) {
        titleKey(CheckTitles.KEY_MAPPER_INSTALLED) -> when (state) {
            CheckState.READY -> known.keyMapperVersion?.let { "Version $it runs your pen’s buttons." } ?: "Runs your pen’s buttons."
            CheckState.ATTENTION -> "Key Mapper runs your pen’s buttons. Install it from your computer with tools/install.sh keymapper."
            CheckState.BY_HAND -> detail
        }
        titleKey(CheckTitles.KEY_MAPPER_SEES_PEN) -> when (state) {
            CheckState.READY -> "Its accessibility service is turned on."
            CheckState.ATTENTION ->
                "Turn on its accessibility service. If Android says “Restricted setting”, open Key Mapper’s App info → ⋮ → " +
                    "Allow restricted settings first."
            CheckState.BY_HAND -> detail
        }
        titleKey(CheckTitles.PROFILE_CURRENT) -> when {
            known.sentText == null -> detail
            known.needsSend -> "${known.sentText}. Use Send to Key Mapper on Today or Pen."
            else -> "${known.sentText}."
        }
        titleKey(CheckTitles.HYPEROS_KEEPS_KEY_MAPPER) -> when (state) {
            CheckState.BY_HAND ->
                "PadStudio can’t check this, so turn on Autostart, set Battery saver to No restrictions and lock Key Mapper in Recents."
            else -> detail
        }
        titleKey(CheckTitles.FACTS_IMPORTED) -> when (state) {
            CheckState.READY -> known.facts?.let { "PadStudio knows ${it.contents()}." } ?: "Imported."
            CheckState.ATTENTION -> "Optional. Adds your camera’s shutter button and the pen’s measured controls: see Device facts under Developer."
            CheckState.BY_HAND -> detail
        }
        titleKey(CheckTitles.WIDGETS_PLACED) -> when (state) {
            CheckState.READY -> known.widgetsPlaced?.takeIf { it > 0 }?.let { "$it placed on your home screen." } ?: detail
            CheckState.ATTENTION -> "None placed yet. Add one from Home, or long-press the home screen → Widgets → PadStudio."
            CheckState.BY_HAND -> detail
        }
        titleKey(CheckTitles.THEMED_ICONS) -> when (state) {
            CheckState.READY -> when (known.lawnicons) {
                true -> "Lawnicons is installed and supplies themed icons."
                false -> "Android’s own monochrome icons are in use. Install Lawnicons for more coverage."
                null -> detail
            }
            CheckState.ATTENTION -> "Install Lawnicons for themed icons."
            CheckState.BY_HAND -> detail
        }
        titleKey(CheckTitles.WALLPAPER) -> when (state) {
            CheckState.READY -> "A PadStudio wallpaper is applied."
            CheckState.ATTENTION -> "Not applied yet, and it’s optional: pick one in Looks for Home, Lock or both."
            CheckState.BY_HAND -> detail
        }
        else -> detail
    }
}

/** What device-facts.json holds, for the "Device facts imported" sentence and the Developer card. */
data class FactsSummary(
    /** Pen control ids with a recorded trigger, in file order: pinch, slide_up, ... */
    val controls: List<String>,
    val cameras: Int,
    val penDevices: List<String>,
    val homeScreen: HomeScreenFacts?,
    /** The file's one recorded date (homeScreen.recordedAt, gate G5); null when absent or not a date. */
    val recordedOn: LocalDate?,
) {
    /** "the pen’s 4 controls, 1 camera app and the home-screen grid", or "no pen, camera or home-screen facts". */
    fun contents(): String {
        val parts = buildList {
            if (controls.isNotEmpty()) add(if (controls.size == 1) "the pen’s 1 control" else "the pen’s ${controls.size} controls")
            if (cameras > 0) add(if (cameras == 1) "1 camera app" else "$cameras camera apps")
            if (homeScreen != null) add("the home-screen grid")
        }
        return if (parts.isEmpty()) "no pen, camera or home-screen facts yet" else joinAnd(parts)
    }
}

fun FactsFile.summary(): FactsSummary = FactsSummary(
    controls = controls.keys.toList(),
    cameras = cameras.size,
    penDevices = penDevices.map { it.name },
    homeScreen = homeScreen,
    recordedOn = recordedDate(homeScreen?.recordedAt),
)

/** An ISO 8601 date ("2026-10-11") or date-time as a date; null when [iso] is missing or isn't one. */
fun recordedDate(iso: String?): LocalDate? {
    val text = iso?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return parseOrNull { LocalDate.parse(text) }
        ?: parseOrNull { LocalDateTime.parse(text).toLocalDate() }
        ?: parseOrNull { OffsetDateTime.parse(text).toLocalDate() }
}

private inline fun parseOrNull(parse: () -> LocalDate): LocalDate? = try {
    parse()
} catch (_: DateTimeParseException) {
    null
}

/**
 * The Device facts row's line. [present] is null while the file is being read; [recorded] is the recorded date
 * already formatted for the reader ("11 Oct 2026"), or null when the file has none.
 */
fun factsRowText(present: Boolean?, recorded: String?): String = when (present) {
    null -> "Reading device-facts.json…"
    false -> "Not imported. Optional: run tools/facts.sh on your computer to add them."
    true -> if (recorded != null) {
        "Recorded $recorded. Run tools/facts.sh on your computer to refresh."
    } else {
        "Imported. Run tools/facts.sh on your computer to refresh."
    }
}

/**
 * How device-facts.json is made and pushed today (tools/facts.sh's header). tools/probe-device.sh only collects
 * the raw dumps facts.sh reads; the old Setup copy that said probe-device.sh wrote the file is gone.
 */
val FACTS_HOW_TO: List<String> = listOf(
    "On your computer, with the tablet connected over adb: tools/facts.sh rebuilds docs/device/device-facts.json " +
        "from the newest tools/probe-device.sh run and pushes it to PadStudio’s files/device-facts.json.",
    "tools/facts.sh --no-push rebuilds the file without pushing it.",
    "PadStudio reads the new file on its next refresh: press Check again.",
)

/**
 * The facts file's contents as developer lines: pen kind and mode, each control's key ([keyName] turns an
 * Android key code into its name, KeyEvent.keyCodeToString on the tablet), pen devices, cameras, the home screen.
 */
fun factsDetails(facts: FactsFile, keyName: (Int) -> String): List<String> = buildList {
    val recorded = facts.homeScreen?.recordedAt
    add(if (recorded != null) "device-facts.json, recorded $recorded (homeScreen.recordedAt)." else "device-facts.json, with no recorded date.")
    if (facts.penKind != null || facts.penMode != null) {
        add("Pen: ${facts.penKind?.name ?: "kind not recorded"}, ${facts.penMode?.name ?: "mode not recorded"}.")
    }
    if (facts.controls.isNotEmpty()) {
        add(
            "Controls: " + facts.controls.entries.joinToString(" · ") { (id, trigger) ->
                val scan = trigger.scanCode?.let { " (scan $it)" }.orEmpty()
                "${PenControl.byId(id).label} ${keyName(trigger.keyCode)}$scan"
            } + ".",
        )
    }
    if (facts.penDevices.isNotEmpty()) add("Pen devices: ${facts.penDevices.joinToString { it.name }}.")
    if (facts.cameras.isNotEmpty()) add("Cameras: ${facts.cameras.joinToString { it.packageName }}.")
    facts.homeScreen?.let { home ->
        val names = if (home.namesHidden) "names hidden" else "names shown"
        add("Home screen: ${home.columns} × ${home.rows}, $names${home.orientation?.let { ", $it" }.orEmpty()}.")
    }
}

/** [text] from the view model with typographic apostrophes, like the rest of the page: "Open Key Mapper’s app info". */
fun typographic(text: String): String = text.replace('\'', '’')

/** "a", "a and b", "a, b and c". */
internal fun joinAnd(parts: List<String>): String = when (parts.size) {
    0 -> ""
    1 -> parts[0]
    else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
}
