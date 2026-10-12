package com.charan.padstudio.pen.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.charan.padstudio.core.pen.PenKind
import com.charan.padstudio.core.pen.PenMode
import com.charan.padstudio.core.pen.PenProfile
import com.charan.padstudio.core.pen.Press

/** The press the action picker is editing: one press of one control in one context. */
data class PickerTarget(val contextId: String, val controlId: String, val press: Press)

/**
 * What the Pen screen remembers about itself across rotation and process restore (plan §13 "State": same tab,
 * scroll and open picker): the chosen context and control, the open picker with its search and group, which
 * sections are open, the pending confirmation, and whether the pen animation is paused. Settings themselves live in
 * StudioConfig and save at once.
 */
@Stable
class PenUiState internal constructor(
    contextId: MutableState<String>,
    controlId: MutableState<String?>,
    picker: MutableState<PickerTarget?>,
    query: MutableState<String>,
    group: MutableState<ActionGroup?>,
    advancedOpen: MutableState<Boolean>,
    needsOpen: MutableState<Boolean>,
    animationPaused: MutableState<Boolean>,
    confirm: MutableState<String?>,
) {
    var contextId: String by contextId
    var controlId: String? by controlId
    var picker: PickerTarget? by picker
    var query: String by query
    var group: ActionGroup? by group
    var advancedOpen: Boolean by advancedOpen
    var needsOpen: Boolean by needsOpen
    var animationPaused: Boolean by animationPaused

    /** A pending confirmation, as [PenConfirm.encode] writes it. */
    var confirm: String? by confirm
}

@Composable
fun rememberPenUiState(): PenUiState {
    val contextId = rememberSaveable { mutableStateOf(PenProfile.EVERYWHERE) }
    val controlId = rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberSaveable(stateSaver = PickerSaver) { mutableStateOf<PickerTarget?>(null) }
    val query = rememberSaveable { mutableStateOf("") }
    val group = rememberSaveable { mutableStateOf<ActionGroup?>(null) }
    val advancedOpen = rememberSaveable { mutableStateOf(false) }
    val needsOpen = rememberSaveable { mutableStateOf(false) }
    val animationPaused = rememberSaveable { mutableStateOf(false) }
    val confirm = rememberSaveable { mutableStateOf<String?>(null) }
    return remember {
        PenUiState(contextId, controlId, picker, query, group, advancedOpen, needsOpen, animationPaused, confirm)
    }
}

private val PickerSaver = listSaver<PickerTarget?, String>(
    save = { target -> if (target == null) emptyList() else listOf(target.contextId, target.controlId, target.press.name) },
    restore = { saved ->
        if (saved.size < 3) null else PickerTarget(saved[0], saved[1], Press.entries.firstOrNull { it.name == saved[2] } ?: Press.SHORT)
    },
)

/** A change the screen asks about first (plan §4 allows confirmations before destructive actions). */
sealed interface PenConfirm {
    /** Switching the pen clears recorded buttons and own action choices (MainViewModel.setPenKind). */
    data class Kind(val kind: PenKind) : PenConfirm

    /** Switching the setup clears them too (MainViewModel.setPenMode). */
    data class Mode(val mode: PenMode) : PenConfirm

    /** Reset pen actions (MainViewModel.resetBindings). */
    data object Reset : PenConfirm

    /** Forget one recorded control (MainViewModel.forgetControl). */
    data class Forget(val controlId: String) : PenConfirm

    companion object {
        fun encode(confirm: PenConfirm): String = when (confirm) {
            is Kind -> "kind:${confirm.kind.name}"
            is Mode -> "mode:${confirm.mode.name}"
            Reset -> "reset"
            is Forget -> "forget:${confirm.controlId}"
        }

        fun decode(text: String?): PenConfirm? {
            if (text == null) return null
            val value = text.substringAfter(':', "")
            return when (text.substringBefore(':')) {
                "kind" -> PenKind.entries.firstOrNull { it.name == value }?.let(::Kind)
                "mode" -> PenMode.entries.firstOrNull { it.name == value }?.let(::Mode)
                "reset" -> Reset
                "forget" -> value.takeIf { it.isNotEmpty() }?.let(::Forget)
                else -> null
            }
        }
    }
}

/** A confirmation's words. */
data class ConfirmWords(val title: String, val body: String, val confirm: String)

/** Whether switching the pen or setup would lose anything: recorded buttons, or action choices of your own. */
fun switchLoses(recordedControls: Int, ownActions: Boolean): Boolean = recordedControls > 0 || ownActions

/**
 * The words for [confirm]. [recordedControls] and [ownActions] say what a switch clears; [controlLabel] names the
 * control to forget.
 */
fun confirmWords(
    confirm: PenConfirm,
    recordedControls: Int,
    ownActions: Boolean,
    controlLabel: String = "",
): ConfirmWords {
    val lost = listOfNotNull(
        when (recordedControls) {
            0 -> null
            1 -> "the recorded button"
            else -> "the $recordedControls recorded buttons"
        },
        if (ownActions) "your own action choices" else null,
    ).joinToString(" and ")
    return when (confirm) {
        is PenConfirm.Kind -> ConfirmWords(
            title = "Switch to ${penName(confirm.kind)}?",
            body = "Switching clears $lost. Pen actions start again from this pen's defaults.",
            confirm = "Switch",
        )
        is PenConfirm.Mode -> ConfirmWords(
            title = "Switch to ${setupName(confirm.mode)}?",
            body = "Switching clears $lost. Pen actions start again from the ${setupName(confirm.mode)} defaults.",
            confirm = "Switch",
        )
        PenConfirm.Reset -> ConfirmWords(
            title = "Reset pen actions?",
            body = "Every app group goes back to the default actions for this pen and setup. Recorded buttons stay.",
            confirm = "Reset",
        )
        is PenConfirm.Forget -> ConfirmWords(
            title = "Forget $controlLabel?",
            body = "Its recorded key is removed. If the probe measured it, that measurement is used again; " +
                "otherwise its actions are skipped until you record it again.",
            confirm = "Forget",
        )
    }
}
