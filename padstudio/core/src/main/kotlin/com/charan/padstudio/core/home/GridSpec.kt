package com.charan.padstudio.core.home

/**
 * An exact widget grid the owner picked, columns × rows (redesign plan §6b). Columns are 4–10,
 * because Glance lays out at most 10 children per Row ([GroupPlanner.MAX_PER_ROW]); rows are 2–8.
 * A value outside those ranges can't be constructed, so [parse] is the way in for anything saved.
 *
 * Whether a grid fits a particular widget is [GroupPlanner.layoutFor]'s job, not this class's.
 */
data class GridSpec(val columns: Int, val rows: Int) {
    init {
        require(columns in COLUMNS) { "columns must be in $COLUMNS, was $columns" }
        require(rows in ROWS) { "rows must be in $ROWS, was $rows" }
    }

    /** The stored form, and the only form [parse] reads back: "8x4". */
    override fun toString(): String = "${columns}x$rows"

    /** How screens show it: "8 × 4". */
    val label: String get() = "$columns × $rows"

    companion object {
        const val MIN_COLUMNS = 4
        const val MAX_COLUMNS = GroupPlanner.MAX_PER_ROW
        const val MIN_ROWS = 2
        const val MAX_ROWS = 8

        val COLUMNS: IntRange get() = MIN_COLUMNS..MAX_COLUMNS
        val ROWS: IntRange get() = MIN_ROWS..MAX_ROWS

        /** The plan's presets; anything else in range is "Custom". */
        val PRESETS: List<GridSpec> get() = listOf(GridSpec(6, 3), GridSpec(8, 4), GridSpec(10, 5))

        /** Per-widget values that aren't a grid (plan §6b). Anything else that doesn't parse counts as Default. */
        const val AUTO = "auto"
        const val DEFAULT = "default"

        private val STORED = Regex("([1-9][0-9]?)x([1-9][0-9]?)")

        /**
         * Reads the stored form exactly: "8x4". Anything else gives null and never throws: other
         * spellings ("8X4", "8 × 4", " 8x4", "08x4"), out of range ("11x4", "3x4", "4x9"), or junk.
         */
        fun parse(text: String?): GridSpec? {
            val match = text?.let(STORED::matchEntire) ?: return null
            val columns = match.groupValues[1].toInt()
            val rows = match.groupValues[2].toInt()
            return if (columns in COLUMNS && rows in ROWS) GridSpec(columns, rows) else null
        }

        /** Plan §6b precedence: per-widget CxR, else per-widget "auto" (any case) → Auto, else [defaultSpec]; null means Auto. */
        fun resolve(perWidget: String?, defaultSpec: String?): GridSpec? {
            parse(perWidget)?.let { return it }
            if (perWidget.equals(AUTO, ignoreCase = true)) return null
            return parse(defaultSpec)
        }
    }
}
