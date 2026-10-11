package com.charan.padstudio.core.home

/** Icon density for widgets and panels (PLAN D7); tuned from screenshots on the tablet. */
enum class Density(val cellDp: Int, val iconDp: Int, val showLabels: Boolean, val label: String) {
    CALM(cellDp = 76, iconDp = 52, showLabels = true, label = "Roomy"),
    COMPACT(cellDp = 64, iconDp = 44, showLabels = true, label = "Standard"),
    DENSE(cellDp = 52, iconDp = 40, showLabels = false, label = "Dense"),
}

data class PlannedGroup(val group: AppGroup, val apps: List<AppFacts>)

/**
 * Turns placements into the groups the widgets and the library show: display order, apps sorted
 * by label, Hidden removed, and groups with a single app folded into More (a one-app folder is
 * just a spread-out icon again).
 */
object GroupPlanner {
    fun plan(placements: List<Placement>, minimumGroupSize: Int = 2): List<PlannedGroup> {
        val byGroup = placements.filter { it.group != AppGroup.HIDDEN }
            .groupBy({ it.group }, { it.app })
            .toMutableMap()
        val tooSmall = byGroup.filter { (group, apps) -> group != AppGroup.MORE && apps.size < minimumGroupSize }
        for ((group, apps) in tooSmall) {
            byGroup.remove(group)
            byGroup[AppGroup.MORE] = byGroup[AppGroup.MORE].orEmpty() + apps
        }
        return AppGroup.entries.mapNotNull { group ->
            byGroup[group]?.takeIf { it.isNotEmpty() }?.let { apps ->
                PlannedGroup(group, apps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }))
            }
        }
    }

    /** How many icons fit in a widget [widthDp] × [heightDp] at [density], header excluded. */
    fun gridFor(widthDp: Int, heightDp: Int, density: Density, headerDp: Int = 36): Pair<Int, Int> {
        val columns = (widthDp / density.cellDp).coerceIn(1, MAX_PER_ROW)
        val rows = ((heightDp - headerDp) / density.cellDp).coerceAtLeast(1)
        return columns to rows
    }

    /** Glance lays out at most 10 children per Row/Column (androidx LayoutGenerator.MaxChildCount). */
    const val MAX_PER_ROW = 10
}
