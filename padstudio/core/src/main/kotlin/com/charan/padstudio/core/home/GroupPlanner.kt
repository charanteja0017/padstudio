package com.charan.padstudio.core.home

/** Icon density for widgets and panels (PLAN D7); tuned from screenshots on the tablet. */
enum class Density(val cellDp: Int, val iconDp: Int, val showLabels: Boolean, val label: String) {
    CALM(cellDp = 76, iconDp = 52, showLabels = true, label = "Roomy"),
    COMPACT(cellDp = 64, iconDp = 44, showLabels = true, label = "Standard"),
    DENSE(cellDp = 52, iconDp = 40, showLabels = false, label = "Dense"),
}

data class PlannedGroup(val group: AppGroup, val apps: List<AppFacts>)

/**
 * How one widget lays out its apps, from [GroupPlanner.layoutFor] (redesign plan §6b). Sizes are
 * in dp. Cells share the usable area evenly: inside the 10 dp padding and below the 28 dp header,
 * [cellWidthDp] is its width / [columns] and [cellHeightDp] its height / [rows].
 */
data class WidgetLayout(
    val columns: Int,
    val rows: Int,
    val cellWidthDp: Float,
    val cellHeightDp: Float,
    val iconDp: Float,
    val showLabels: Boolean,
    /** False only when an exact grid was asked for and this widget is too small for it. */
    val fits: Boolean,
    /** Why the grid asked for doesn't fit, in plain words, e.g. "10 columns need a 500 dp wide widget; this one is 412 dp."; null when it fits. */
    val reason: String?,
    /** The grid asked for, when a smaller one is shown instead; null otherwise. */
    val trimmedFrom: GridSpec?,
) {
    /** The header note while trimmed: "8 × 4 doesn't fit · showing 6 × 3". Null when nothing is trimmed. */
    val note: String? get() = trimmedFrom?.let { "${it.label} doesn't fit · showing $columns × $rows" }

    /** Which of a group's [appCount] apps get a cell here, and the "+N" tile. */
    fun overflow(appCount: Int): Overflow = GroupPlanner.overflow(appCount, columns, rows)
}

/**
 * A group's apps in a grid: the first [shown] apps get a cell each and, when [more] > 0, the last
 * cell is a "+[more]" tile that opens the folder panel.
 */
data class Overflow(val shown: Int, val more: Int) {
    /** The "+N" tile's cell, counted row by row from 0: always the last cell. Null when every app has a cell. */
    val moreIndex: Int? get() = if (more > 0) shown else null
}

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

    /**
     * The layout of a widget [widthDp] × [heightDp] (its whole size, as Glance's LocalSize reports
     * it) showing [spec], or Auto at [density] when [spec] is null (plan §6b).
     *
     * Auto is today's widget unchanged: columns and rows are
     * `gridFor(widthDp.toInt() - 20, heightDp.toInt() - 20, density)` (its 36 dp header allowance),
     * the icon is 0.78 × the cell width kept between the density's icon size and 1.3 × it, and
     * labels follow [Density.showLabels]. [labelLineDp] isn't used.
     *
     * An exact [spec] fits when every cell is at least [MIN_CELL_DP] × [MIN_CELL_DP] inside the
     * usable area: width − 20, height − 20 − the [HEADER_DP] header. When it doesn't, the result
     * is the largest grid that does fit and is no bigger than [spec] either way, with
     * [WidgetLayout.fits] false, [WidgetLayout.reason] and [WidgetLayout.trimmedFrom] = [spec].
     * Nothing is trimmed silently. The icon is 0.78 × the cell's shorter side, at least
     * [MIN_ICON_DP]. Labels show only if the cell is at least icon + [LABEL_GAP_DP] + [labelLineDp]
     * tall, where [labelLineDp] is one 11 sp label line in dp, converted with Android's nonlinear
     * font scale (TypedValue.applyDimension), never by multiplying fontScale.
     */
    fun layoutFor(widthDp: Float, heightDp: Float, density: Density, spec: GridSpec?, labelLineDp: Float): WidgetLayout {
        if (spec == null) return autoLayout(widthDp, heightDp, density)
        // Whole dp decide the fit: every size a grid needs is whole, so flooring the widget's size
        // changes no verdict, and the reason never says "this one is 500 dp" about 499.9 dp.
        val wholeWidth = widthDp.toInt()
        val wholeHeight = heightDp.toInt()
        val fitColumns = countThatFits(wholeWidth - 2 * PADDING_DP)
        val fitRows = countThatFits(wholeHeight - 2 * PADDING_DP - HEADER_DP)
        val columns = if (spec.columns <= fitColumns) spec.columns else fitColumns.coerceAtLeast(1)
        val rows = if (spec.rows <= fitRows) spec.rows else fitRows.coerceAtLeast(1)
        val cellWidth = ((widthDp - 2 * PADDING_DP) / columns).coerceAtLeast(0f)
        val cellHeight = ((heightDp - 2 * PADDING_DP - HEADER_DP) / rows).coerceAtLeast(0f)
        val icon = maxOf(MIN_ICON_DP.toFloat(), ICON_SHARE * minOf(cellWidth, cellHeight))
        val reasons = buildList {
            if (spec.columns > fitColumns) add("${spec.columns} columns need a ${widthNeededDp(spec.columns)} dp wide widget; this one is $wholeWidth dp.")
            if (spec.rows > fitRows) add("${spec.rows} rows need a ${heightNeededDp(spec.rows)} dp tall widget; this one is $wholeHeight dp.")
        }
        val fits = reasons.isEmpty()
        return WidgetLayout(
            columns = columns,
            rows = rows,
            cellWidthDp = cellWidth,
            cellHeightDp = cellHeight,
            iconDp = icon,
            showLabels = cellHeight >= icon + LABEL_GAP_DP + labelLineDp,
            fits = fits,
            reason = if (fits) null else reasons.joinToString(" "),
            trimmedFrom = if (fits) null else spec,
        )
    }

    /** Auto, exactly as GroupWidget drew it before plan §6b. */
    private fun autoLayout(widthDp: Float, heightDp: Float, density: Density): WidgetLayout {
        val (columns, rows) = gridFor(widthDp.toInt() - 2 * PADDING_DP, heightDp.toInt() - 2 * PADDING_DP, density)
        val cellWidth = (widthDp - 2 * PADDING_DP) / columns
        return WidgetLayout(
            columns = columns,
            rows = rows,
            cellWidthDp = cellWidth.coerceAtLeast(0f),
            cellHeightDp = ((heightDp - 2 * PADDING_DP - HEADER_DP) / rows).coerceAtLeast(0f),
            iconDp = (cellWidth * ICON_SHARE).coerceIn(density.iconDp.toFloat(), density.iconDp * AUTO_MAX_ICON_GROWTH),
            showLabels = density.showLabels,
            fits = true,
            reason = null,
            trimmedFrom = null,
        )
    }

    /**
     * Which of [appCount] apps get a cell in a [columns] × [rows] grid. When they don't all fit,
     * the last cell becomes the "+N" tile, so one app fewer is shown (as the widget always did).
     */
    fun overflow(appCount: Int, columns: Int, rows: Int): Overflow {
        require(appCount >= 0) { "appCount must be at least 0, was $appCount" }
        require(columns >= 1 && rows >= 1) { "a grid needs at least one cell, was ${columns}x$rows" }
        val slots = columns * rows
        return if (appCount <= slots) Overflow(shown = appCount, more = 0) else Overflow(shown = slots - 1, more = appCount - (slots - 1))
    }

    /** The largest exact grid a widget [widthDp] × [heightDp] can show (Edit widget's "Up to 8 × 5"); null if not even 4 × 2 fits. */
    fun largestFit(widthDp: Float, heightDp: Float): GridSpec? {
        val columns = countThatFits(widthDp.toInt() - 2 * PADDING_DP).coerceAtMost(GridSpec.MAX_COLUMNS)
        val rows = countThatFits(heightDp.toInt() - 2 * PADDING_DP - HEADER_DP).coerceAtMost(GridSpec.MAX_ROWS)
        return if (columns >= GridSpec.MIN_COLUMNS && rows >= GridSpec.MIN_ROWS) GridSpec(columns, rows) else null
    }

    /** The narrowest widget with room for [columns] cells: 10 columns need 500 dp. */
    fun widthNeededDp(columns: Int): Int = columns * MIN_CELL_DP + 2 * PADDING_DP

    /** The shortest widget with room for [rows] cells under the header: 4 rows need 240 dp. */
    fun heightNeededDp(rows: Int): Int = rows * MIN_CELL_DP + 2 * PADDING_DP + HEADER_DP

    /** How many cells of at least [MIN_CELL_DP] fit in [usableDp]: the one place the 48 dp floor is applied. */
    private fun countThatFits(usableDp: Int): Int = (usableDp / MIN_CELL_DP).coerceAtLeast(0)

    /** Glance lays out at most 10 children per Row/Column (androidx LayoutGenerator.MaxChildCount). */
    const val MAX_PER_ROW = 10

    /** The widget's padding on each side, so 20 dp of its width and height hold no cells. */
    const val PADDING_DP = 10

    /** The header row above an exact grid. Auto keeps [gridFor]'s 36 dp allowance, as before. */
    const val HEADER_DP = 28

    /** The smallest side of a cell: the 48 dp touch target. */
    const val MIN_CELL_DP = 48

    /** An exact grid's icon is [ICON_SHARE] × the cell's shorter side, but never under this. */
    const val MIN_ICON_DP = 32

    /** Icon size as a share of its cell. */
    const val ICON_SHARE = 0.78f

    /** Space between an icon and its label. */
    const val LABEL_GAP_DP = 4

    /** In Auto an icon grows with its cell up to this many times the density's icon size. */
    const val AUTO_MAX_ICON_GROWTH = 1.3f
}
