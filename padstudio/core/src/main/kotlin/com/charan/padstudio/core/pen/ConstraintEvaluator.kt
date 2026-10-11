package com.charan.padstudio.core.pen

/** The state Key Mapper checks constraints against. */
data class Situation(val foregroundPackage: String, val orientation: Orientation)

/**
 * Mirrors Key Mapper's constraint semantics for previews and tests: a key map can fire when its
 * own constraints pass and every ancestor group's constraints pass (each list combined with its
 * own AND/OR mode; an empty list passes). A key map whose group is missing never fires, as in
 * DetectKeyMapsUseCase.processKeyMapsAndGroups.
 */
object ConstraintEvaluator {

    fun eligible(profile: GeneratedProfile, controlId: String, press: Press, situation: Situation): List<KmKeyMap> {
        val groups = profile.groups.associateBy { it.uid }
        return profile.keyMaps.filter { keyMap ->
            keyMap.controlId == controlId &&
                keyMap.press == press &&
                passes(keyMap.constraints, keyMap.mode, situation) &&
                ancestorsPass(keyMap.groupUid, groups, situation)
        }
    }

    fun passes(constraints: List<KmConstraint>, mode: ConstraintMode, situation: Situation): Boolean {
        if (constraints.isEmpty()) return true
        val results = constraints.map { holds(it, situation) }
        return if (mode == ConstraintMode.AND) results.all { it } else results.any { it }
    }

    private fun holds(constraint: KmConstraint, situation: Situation): Boolean = when (constraint) {
        is KmConstraint.AppForeground -> (situation.foregroundPackage == constraint.packageName) != constraint.isNot
        is KmConstraint.InOrientation -> situation.orientation == constraint.orientation
    }

    private fun ancestorsPass(groupUid: String?, groups: Map<String, KmGroup>, situation: Situation): Boolean {
        var uid = groupUid
        var depth = 0
        while (uid != null && depth < 100) {
            val group = groups[uid] ?: return false
            if (!passes(group.constraints, group.mode, situation)) return false
            uid = group.parentUid
            depth++
        }
        return true
    }
}
