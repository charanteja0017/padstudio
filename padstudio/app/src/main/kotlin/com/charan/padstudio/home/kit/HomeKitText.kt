package com.charan.padstudio.home.kit

import com.charan.padstudio.core.home.AppFacts
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.home.Placement
import com.charan.padstudio.core.home.PlannedGroup
import com.charan.padstudio.core.pen.HomeScreenFacts
import java.time.LocalDate

// The Home Kit's words and its Organize lists, free of Android so they are unit-tested (HomeKitTextTest).
// The home-screen grid wording follows docs/decisions/ADR-005-home-grid.md: a recorded baseline, never live.

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "11 Oct", with the year when it isn't [today]'s ("11 Oct 2025"); the saved text itself when it isn't an ISO date. */
fun recordedDay(recordedAt: String, today: LocalDate): String {
    val text = recordedAt.trim()
    val date = runCatching { LocalDate.parse(text.take(ISO_DATE_LENGTH)) }.getOrNull() ?: return text
    val day = "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"
    return if (date.year == today.year) day else "$day ${date.year}"
}

private const val ISO_DATE_LENGTH = 10

/** "Recorded 11 Oct: 6 × 4, names hidden" (ADR-005). */
fun recordedLine(facts: HomeScreenFacts, today: LocalDate): String =
    "Recorded ${recordedDay(facts.recordedAt, today)}: ${facts.columns} × ${facts.rows}, names ${if (facts.namesHidden) "hidden" else "shown"}"

/** The big readout beside the recorded line: "6 × 4". */
fun recordedReadout(facts: HomeScreenFacts): String = "${facts.columns} × ${facts.rows}"

/** The tag under the recorded line. PadStudio can't read HyperOS's grid, so it is never called live (plan §7). */
fun baselineTag(orientation: String?): String = when (orientation?.trim()?.lowercase()) {
    "landscape" -> "Landscape · recorded baseline, not live"
    "portrait" -> "Portrait · recorded baseline, not live"
    else -> "Recorded baseline, not live"
}

/**
 * The card's first line. Gate G5 found no grid setting (outcome G5-B), and that holds unless the recorded
 * facts say HyperOS has one.
 */
fun hyperosGridLine(gridSetting: Boolean?): String =
    if (gridSetting == true) "HyperOS on this tablet has a grid setting." else "HyperOS on this tablet has no grid setting."

/** Outcome G5-A only: PadStudio can't follow a change made in HyperOS. */
fun hyperosGridNote(gridSetting: Boolean?): String? =
    if (gridSetting == true) "PadStudio can't read HyperOS's grid. Record it again after you change it." else null

/**
 * Shown when no home-screen record has been pushed to the tablet. The grid is measured by hand (gate G5,
 * docs/device/home-grid.json); tools/facts.sh only builds device-facts.json with it and pushes it.
 */
const val NOT_RECORDED_LINE = "No recorded baseline on this tablet yet"
const val NOT_RECORDED_HINT = "Push one from a Mac with tools/facts.sh, then come back to this screen."

/** HyperOS launcher screens PadStudio may open (ADR-005, plan Appendix A). */
enum class HyperOsScreen(val action: String, val title: String) {
    HOME_SETTINGS("com.miui.home.Setting", "HyperOS's Home screen settings"),
    ICON_BACKGROUND("com.miui.home.action.ICON_CUSTOMIZE", "HyperOS's icon background setting"),
}

/** The launcher package that owns [HyperOsScreen]'s activities. */
const val HYPEROS_LAUNCHER = "com.miui.home"

/** The nearest HyperOS options (ADR-005 decision 1) as chips, in the mock's order: HyperOS's wording, and the screen that holds each. */
enum class HyperOsLink(val label: String, val screen: HyperOsScreen) {
    NAMES("Don't show text", HyperOsScreen.HOME_SETTINGS),
    LOCK_LAYOUT("Lock Home screen layout", HyperOsScreen.HOME_SETTINGS),
    MODE("Classic or App drawer", HyperOsScreen.HOME_SETTINGS),
    ICON_BACKGROUND("Icon background colour", HyperOsScreen.ICON_BACKGROUND),
}

/** Whether a HyperOS screen can be opened, found by resolving its intent before showing the link. */
enum class LinkState {
    /** Resolves to an exported activity. */
    READY,

    /** Nothing on the tablet handles the intent. */
    MISSING,

    /** It resolves, but the launcher keeps the activity to itself. */
    BLOCKED,
}

/** Why a link is disabled, in plain words; null when it can be opened. */
fun unavailableLine(screen: HyperOsScreen, state: LinkState): String? = when (state) {
    LinkState.READY -> null
    LinkState.MISSING -> "Can't open ${screen.title}: this tablet's launcher doesn't offer that screen."
    LinkState.BLOCKED -> "Can't open ${screen.title}: HyperOS doesn't let other apps open it."
}

/** Why opening a resolved screen still failed: no activity matched any more (ActivityNotFoundException), or HyperOS refused (SecurityException). */
enum class OpenFailure { NOT_FOUND, REFUSED }

fun openFailedLine(screen: HyperOsScreen, failure: OpenFailure): String = when (failure) {
    OpenFailure.NOT_FOUND -> "Couldn't open ${screen.title}: the launcher no longer offers that screen."
    OpenFailure.REFUSED -> "Couldn't open ${screen.title}: HyperOS refused the request."
}

/** "12 apps", "1 app". */
fun appCount(count: Int): String = if (count == 1) "1 app" else "$count apps"

/** Under the Hidden group: what it is for, and how many apps it holds. */
fun hiddenLine(count: Int): String =
    if (count == 0) "Kept out of every widget. None yet." else "Kept out of every widget · ${appCount(count)}"

/** The categoriser's reason for a placement (:core's Categorizer), in plain words. Unknown reasons are shown as they are. */
fun placementReasonText(reason: String): String = when {
    reason == "your choice" -> "Moved here by you"
    reason.startsWith("PadStudio list") -> "On PadStudio's app list"
    reason.startsWith("flowerpot") -> "From a shared list of app types"
    reason == "Android: game" -> "Android lists it as a game"
    reason == "Android category" -> "From its Android category"
    reason == "name" -> "From its name"
    reason == "system app" -> "A system app"
    reason == "no rule matched" -> "No rule matched it"
    else -> reason
}

/** One app in Organize: its facts and the line under it. */
data class OrganizeApp(val facts: AppFacts, val reason: String)

/** One group in Organize, with its apps in the order the widgets show them. */
data class OrganizeGroup(val group: AppGroup, val apps: List<OrganizeApp>)

/**
 * Organize's groups: the planned groups as the widgets show them (GroupPlanner: display order, Hidden left
 * out, one-app groups folded into More), then Hidden, always last and always present.
 */
fun organizeGroups(groups: List<PlannedGroup>, placements: List<Placement>): List<OrganizeGroup> {
    val byApp = placements.associateBy { it.app.packageName to it.app.activityName }
    val shown = groups.map { planned ->
        OrganizeGroup(
            group = planned.group,
            apps = planned.apps.map { facts -> OrganizeApp(facts, placementLine(byApp[facts.packageName to facts.activityName], planned.group)) },
        )
    }
    val hidden = placements
        .filter { it.group == AppGroup.HIDDEN }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.app.label })
        .map { OrganizeApp(it.app, placementLine(it, AppGroup.HIDDEN)) }
    return shown + OrganizeGroup(AppGroup.HIDDEN, hidden)
}

/**
 * Why an app is where it shows: the reason, plus, when its group is too small to show and it sits in
 * [shownIn] instead, where it goes ("In More until Reading has 2 apps").
 */
fun placementLine(placement: Placement?, shownIn: AppGroup): String {
    if (placement == null) return ""
    val why = placementReasonText(placement.reason)
    val sorted = placement.group
    return if (sorted != shownIn && sorted != AppGroup.HIDDEN) "$why. In ${shownIn.title} until ${sorted.title} has 2 apps." else why
}

/** The group Organize should show: [requested] if it still exists, else the first. */
fun organizeSelection(groups: List<OrganizeGroup>, requested: String?): OrganizeGroup? =
    groups.firstOrNull { it.group.name == requested } ?: groups.firstOrNull()

/** Snackbar after a move (plan §13: success with the time). */
fun movedMessage(appLabel: String, group: AppGroup, time: String): String = "Moved $appLabel to ${group.title} · $time"

/** Snackbar after undoing every move. */
fun resetMessage(count: Int, time: String): String =
    "Undid ${if (count == 1) "your move" else "your $count moves"} · $time"

/** The reset confirmation's body, from the saved moves (StudioConfig.overrides). */
fun resetBody(overrides: Map<String, AppGroup>): String {
    val count = overrides.size
    val hidden = overrides.values.count { it == AppGroup.HIDDEN }
    val back = if (count == 1) "1 app goes back to the group PadStudio picks for it." else "$count apps go back to the groups PadStudio picks for them."
    val unhide = when (hidden) {
        0 -> ""
        1 -> " That includes 1 hidden app, which comes back."
        else -> " That includes $hidden hidden apps, which come back."
    }
    return "$back$unhide Widgets update."
}

/** Organize's line about saved moves. */
fun movesLine(count: Int): String = when (count) {
    0 -> "No moves yet"
    1 -> "1 app moved by you"
    else -> "$count apps moved by you"
}

/** Snackbar when a widget appeared after an Add widget request: from what the launcher placed, not what was asked. */
fun addedMessage(kind: WidgetKind?, groupTitle: String?, time: String): String {
    val size = kind?.label?.lowercase()?.let { "$it " }.orEmpty()
    val group = groupTitle?.let { "$it " }.orEmpty()
    return "Added a $size${group}widget · $time"
}

/** What each Add widget button says to TalkBack: "Add a small Study widget". */
fun addWidgetDescription(kind: WidgetKind, groupTitle: String): String = "Add a ${kind.label.lowercase()} $groupTitle widget"
