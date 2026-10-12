package com.charan.padstudio.pen.screen

import com.charan.padstudio.core.pen.PenIntent

// The action picker's plain logic (plan §10 "Action picker"): the 25 pen actions in five groups, search,
// and the "Unavailable: needs …" line from core's PenIntent.needs.

/** The picker's groups, in order. */
enum class ActionGroup(val label: String) {
    CAMERA("Camera"),
    READING("Reading & notes"),
    MEDIA("Media"),
    NAVIGATION("Navigation"),
    SYSTEM("System"),
}

/** The group each pen action is listed under. Every PenIntent has exactly one (a `when` without else, so a new action can't be missed). */
fun groupOf(intent: PenIntent): ActionGroup = when (intent) {
    PenIntent.SHUTTER, PenIntent.OPEN_CAMERA, PenIntent.FLASHLIGHT -> ActionGroup.CAMERA
    PenIntent.NEXT_PAGE, PenIntent.PREVIOUS_PAGE, PenIntent.UNDO, PenIntent.REDO, PenIntent.OPEN_NOTES -> ActionGroup.READING
    PenIntent.PLAY_PAUSE, PenIntent.NEXT_TRACK, PenIntent.PREVIOUS_TRACK, PenIntent.VOLUME_UP, PenIntent.VOLUME_DOWN -> ActionGroup.MEDIA
    PenIntent.BACK, PenIntent.HOME, PenIntent.RECENTS, PenIntent.LAST_APP, PenIntent.SPLIT_SCREEN, PenIntent.OPEN_LIBRARY ->
        ActionGroup.NAVIGATION
    PenIntent.SCREENSHOT, PenIntent.NOTIFICATIONS, PenIntent.QUICK_SETTINGS, PenIntent.ASSISTANT, PenIntent.DO_NOT_DISTURB,
    PenIntent.ROTATION_LOCK,
    -> ActionGroup.SYSTEM
}

/** The actions in [group], in PenIntent order. */
fun actionsIn(group: ActionGroup): List<PenIntent> = PenIntent.entries.filter { groupOf(it) == group }

/**
 * The supporting line for an action Key Mapper can't run without a permission it may not have: "Unavailable:
 * needs camera permission". Null for the others.
 */
fun unavailableText(intent: PenIntent): String? = intent.needs?.let { "Unavailable: needs $it" }

/** The picker's first choice: no key map for this press, so the press reaches the app or HyperOS (PadStudio 1's wording). */
const val NOTHING_LABEL = "Nothing (leave to the app / HyperOS)"

/** One group of the picker's list. */
data class ActionSection(val group: ActionGroup, val actions: List<PenIntent>)

/**
 * What the picker lists. A search ([query], ignoring case and extra spaces) looks through all 25 actions by
 * name and group name, whatever [group] is chosen; without one, [group] narrows the list to that group, or
 * null shows every group. Empty groups are left out.
 */
fun pickerSections(query: String, group: ActionGroup?): List<ActionSection> {
    val words = searchWords(query)
    val sections = ActionGroup.entries.map { ActionSection(it, actionsIn(it)) }
    return when {
        words.isNotEmpty() -> sections.map { section ->
            section.copy(actions = section.actions.filter { matches(words, it.label, section.group.label) })
        }
        group != null -> sections.filter { it.group == group }
        else -> sections
    }.filter { it.actions.isNotEmpty() }
}

/** Whether the "Nothing" choice shows: always without a search, and for a search that matches its words. */
fun showsNothingChoice(query: String): Boolean {
    val words = searchWords(query)
    return words.isEmpty() || matches(words, NOTHING_LABEL)
}

/** How many actions the picker can search, for its placeholder ("Search 25 actions"). */
val actionCount: Int get() = PenIntent.entries.size

private fun searchWords(query: String): List<String> = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

/** Every word of the query appears somewhere in [texts]. */
private fun matches(words: List<String>, vararg texts: String): Boolean {
    val haystack = texts.joinToString(" ").lowercase()
    return words.all { it in haystack }
}
