package com.charan.padstudio.core.pen

/**
 * PadStudio's mirror of the Key Mapper entities it writes (data/entities in keymapperorg/KeyMapper,
 * database version 25). Kept separate from JSON so the same objects drive the writer, the
 * constraint evaluator and the "what will this press do here?" preview.
 */
sealed interface KmAction {
    /** ActionEntity.Type.SYSTEM_ACTION; [id] is a value of ActionDataEntityMapper.SYSTEM_ACTION_ID_MAP. */
    data class System(val id: String, val extras: Map<String, String> = emptyMap(), val flags: Int = 0) : KmAction

    data class App(val packageName: String, val appName: String?) : KmAction

    /** Started with Intent.parseUri(uri, 0) as an activity (IntentAdapter.send). */
    data class Intent(val uri: String, val description: String) : KmAction

    /** Needs Expert Mode (system bridge) or Key Mapper's keyboard to be the active input method. */
    data class KeyEvent(val keyCode: Int, val metaState: Int = 0) : KmAction

    data class Tap(val at: Point, val screen: ScreenSize, val description: String) : KmAction

    data class Swipe(
        val from: Point,
        val to: Point,
        val durationMs: Int,
        val screen: ScreenSize,
        val description: String,
    ) : KmAction

    data class UiElement(val packageName: String, val node: UiNode, val nodeAction: NodeAction) : KmAction
}

/** common/utils/NodeInteractionType: the value is AccessibilityNodeInfo.ACTION_*. */
enum class NodeAction(val accessibilityActionId: Int) {
    CLICK(16),
    SCROLL_FORWARD(4096),
    SCROLL_BACKWARD(8192),
}

sealed interface KmConstraint {
    data class AppForeground(val packageName: String, val isNot: Boolean = false) : KmConstraint

    data class InOrientation(val orientation: Orientation) : KmConstraint
}

/** ConstraintEntity.MODE_OR / MODE_AND. */
enum class ConstraintMode(val value: Int) {
    OR(0),
    AND(1),
}

data class KmGroup(
    val uid: String,
    val name: String,
    val parentUid: String?,
    val constraints: List<KmConstraint> = emptyList(),
    val mode: ConstraintMode = ConstraintMode.AND,
)

data class KmKeyMap(
    val uid: String,
    val name: String,
    val groupUid: String,
    val controlId: String,
    val trigger: PenTrigger,
    val press: Press,
    val actions: List<KmAction>,
    val constraints: List<KmConstraint> = emptyList(),
    val mode: ConstraintMode = ConstraintMode.AND,
)

/** Everything one "Apply to Key Mapper" writes, plus what the user should know about it. */
data class GeneratedProfile(
    val groups: List<KmGroup>,
    val keyMaps: List<KmKeyMap>,
    val warnings: List<String>,
)
