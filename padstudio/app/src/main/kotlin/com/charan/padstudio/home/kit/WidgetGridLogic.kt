package com.charan.padstudio.home.kit

import com.charan.padstudio.core.home.Density
import com.charan.padstudio.core.home.GridSpec
import com.charan.padstudio.core.home.GroupPlanner
import com.charan.padstudio.core.home.WidgetLayout

// The Default widget grid picker's logic (plan §6b), kept free of Android and Compose so it is unit-tested
// (WidgetGridLogicTest). Fit decisions are GroupPlanner.layoutFor's; nothing here re-implements them.

/** A widget size in dp, as Glance reads it from the launcher's options. 0 × 0 means the launcher hasn't said. */
data class SizeDp(val width: Float, val height: Float) {
    val known: Boolean get() = width > 0f && height > 0f
    val area: Float get() = width * height

    /** "472 × 252 dp", in whole dp, as GroupPlanner's reasons count them. */
    val label: String get() = "${width.toInt()} × ${height.toInt()} dp"
}

/** The three sizes a group widget comes in (one receiver each), as the Home Kit names them. */
enum class WidgetKind(val label: String) {
    SMALL("Small"),
    WIDE("Wide"),
    LARGE("Large"),
}

/**
 * A group widget on the home screen as PadStudio can read it: its id, which receiver placed it, and its
 * Glance state ([com.charan.padstudio.home.GroupWidget.GROUP], `GRID` and `DENSITY`, null when missing), plus
 * the sizes the launcher reports for it (Glance draws one layout per size).
 */
data class PlacedWidget(
    val appWidgetId: Int,
    val kind: WidgetKind?,
    val group: String? = null,
    val grid: String? = null,
    val density: String? = null,
    val sizes: List<SizeDp> = emptyList(),
)

/**
 * True when a widget with this saved grid follows the Default (plan §6b precedence, as GridSpec.resolve
 * applies it): a missing key, "default", and anything that doesn't parse. Its own "8x4" or "auto" (any case)
 * keeps it off the Default.
 */
fun followsDefault(perWidget: String?): Boolean =
    GridSpec.parse(perWidget) == null && !perWidget.equals(GridSpec.AUTO, ignoreCase = true)

/** How many of [widgets] follow the Default; null while they can't be read. */
fun defaultCount(widgets: List<PlacedWidget>?): Int? = widgets?.count { followsDefault(it.grid) }

/** "Applies to all widgets set to Default (2)", or without the count while the widgets can't be read. */
fun defaultScopeLine(widgets: List<PlacedWidget>?): String {
    val count = defaultCount(widgets) ?: return "Applies to all widgets set to Default"
    return "Applies to all widgets set to Default ($count)"
}

/**
 * The size a widget is drawn at now. The launcher reports one size per orientation and Glance lays out
 * each, so in landscape the widest shape is the one on screen and in portrait the tallest. Null when the
 * launcher reported none.
 */
fun sizeNow(sizes: List<SizeDp>, landscape: Boolean): SizeDp? {
    val known = sizes.filter { it.known }
    return if (landscape) {
        known.maxWithOrNull(compareBy<SizeDp>({ it.width / it.height }, { it.area }))
    } else {
        known.maxWithOrNull(compareBy<SizeDp>({ it.height / it.width }, { it.area }))
    }
}

/** Why the preview is an example widget rather than one of yours. */
enum class ExampleReason {
    /** The placed widgets haven't been read (yet). */
    NOT_READ,

    /** No group widget is on the home screen. */
    NONE_PLACED,

    /** Every placed widget has its own grid. */
    NONE_ON_DEFAULT,

    /** The launcher hasn't reported a size for any widget set to Default. */
    NO_SIZES,
}

/** The size the Default picker previews on, and the widget it belongs to; [example] says why it is an example. */
data class PreviewTarget(val size: SizeDp, val widget: PlacedWidget?, val example: ExampleReason?)

/** The example widget of the design preview (mock-homekit.html), used only until a real one reports its size. */
val EXAMPLE_WIDGET: SizeDp = SizeDp(472f, 252f)

/** Previews on the biggest widget set to Default that reports a size in this orientation, else on [EXAMPLE_WIDGET]. */
fun previewTarget(widgets: List<PlacedWidget>?, landscape: Boolean): PreviewTarget {
    if (widgets == null) return PreviewTarget(EXAMPLE_WIDGET, null, ExampleReason.NOT_READ)
    if (widgets.isEmpty()) return PreviewTarget(EXAMPLE_WIDGET, null, ExampleReason.NONE_PLACED)
    val onDefault = widgets.filter { followsDefault(it.grid) }
    if (onDefault.isEmpty()) return PreviewTarget(EXAMPLE_WIDGET, null, ExampleReason.NONE_ON_DEFAULT)
    val best = onDefault
        .mapNotNull { widget -> sizeNow(widget.sizes, landscape)?.let { widget to it } }
        .maxByOrNull { it.second.area }
        ?: return PreviewTarget(EXAMPLE_WIDGET, null, ExampleReason.NO_SIZES)
    return PreviewTarget(best.second, best.first, null)
}

/** Where the preview's size comes from, in plain words: "On your Wide widget (Study), 776 × 300 dp as HyperOS reports it." */
fun previewSourceLine(target: PreviewTarget, groupTitle: String?): String {
    val size = target.size.label
    return when (target.example) {
        null -> {
            val kind = target.widget?.kind?.label?.let { "$it " }.orEmpty()
            val group = groupTitle?.let { " ($it)" }.orEmpty()
            "On your ${kind}widget$group, $size as HyperOS reports it."
        }
        ExampleReason.NOT_READ -> "On an example widget, $size."
        ExampleReason.NONE_PLACED -> "On an example widget, $size. Add a group widget to preview your own."
        ExampleReason.NONE_ON_DEFAULT -> "On an example widget, $size. Your widgets each have their own grid."
        ExampleReason.NO_SIZES -> "On an example widget, $size. HyperOS hasn't reported your widgets' sizes."
    }
}

/** The icon density a widget saved for itself (its `DENSITY`); null when it has none or the saved name isn't a density. */
fun ownDensity(widget: PlacedWidget?): Density? =
    widget?.density?.let { name -> Density.entries.firstOrNull { it.name == name } }

/** A widget's own icon density (its saved `DENSITY`) when valid, else PadStudio's. */
fun densityFor(widget: PlacedWidget?, saved: Density): Density = ownDensity(widget) ?: saved

/** One chip of the Default widget grid picker. */
sealed interface GridOption {
    /** Today's behaviour: as many icons as fit at the density. */
    data object Auto : GridOption

    /** 6 × 3, 8 × 4 or 10 × 5. */
    data class Preset(val spec: GridSpec) : GridOption

    /** Any other exact grid; [spec] is the saved one while it is the Default. */
    data class Custom(val spec: GridSpec?) : GridOption
}

/** The chips in order: Auto, the presets, Custom (showing the saved custom grid, if that's the Default). */
fun gridOptions(saved: String?): List<GridOption> {
    val custom = (selectedOption(saved) as? GridOption.Custom) ?: GridOption.Custom(null)
    return listOf(GridOption.Auto) + GridSpec.PRESETS.map { GridOption.Preset(it) } + custom
}

/**
 * The chip a saved [com.charan.padstudio.data.StudioConfig.widgetGrid] selects. A value that doesn't parse
 * selects Auto, because that is what widgets set to Default show for it (GridSpec.resolve).
 */
fun selectedOption(saved: String?): GridOption {
    val spec = GridSpec.parse(saved) ?: return GridOption.Auto
    return if (spec in GridSpec.PRESETS) GridOption.Preset(spec) else GridOption.Custom(spec)
}

fun optionLabel(option: GridOption): String = when (option) {
    GridOption.Auto -> "Auto"
    is GridOption.Preset -> option.spec.label
    is GridOption.Custom -> option.spec?.let { "Custom · ${it.label}" } ?: "Custom"
}

/** What the picker saves for [option]: GridSpec's stored form ("8x4"), or null for Auto. */
fun storedValue(option: GridOption): String? = when (option) {
    GridOption.Auto -> null
    is GridOption.Preset -> option.spec.toString()
    is GridOption.Custom -> option.spec?.toString()
}

/** Where Custom starts: the saved exact grid, else what the preview shows now, kept inside 4–10 × 2–8. */
fun customStart(saved: String?, shown: WidgetLayout): GridSpec =
    GridSpec.parse(saved) ?: GridSpec(shown.columns.coerceIn(GridSpec.COLUMNS), shown.rows.coerceIn(GridSpec.ROWS))

/** The sizes now of the widgets set to Default that report one. */
fun defaultWidgetSizes(widgets: List<PlacedWidget>?, landscape: Boolean): List<SizeDp> =
    widgets.orEmpty().filter { followsDefault(it.grid) }.mapNotNull { sizeNow(it.sizes, landscape) }

/**
 * Whether some widget set to Default can show [spec] without trimming (layoutFor's fit rule); null when no
 * widget set to Default reports a size, so nothing can be refused.
 */
fun fitsSomeDefaultWidget(spec: GridSpec, widgets: List<PlacedWidget>?, landscape: Boolean, density: Density, labelLineDp: Float): Boolean? {
    val sizes = defaultWidgetSizes(widgets, landscape)
    if (sizes.isEmpty()) return null
    return sizes.any { GroupPlanner.layoutFor(it.width, it.height, density, spec, labelLineDp).fits }
}

/** The presets no widget set to Default can show: the picker disables them with [refusalLine]. */
fun refusedPresets(widgets: List<PlacedWidget>?, landscape: Boolean, density: Density, labelLineDp: Float): List<GridSpec> =
    GridSpec.PRESETS.filter { fitsSomeDefaultWidget(it, widgets, landscape, density, labelLineDp) == false }

/** "10 × 5 needs bigger widgets: its spaces would be too small to tap. Resize one first." Null when nothing is refused. */
fun refusalLine(refused: List<GridSpec>): String? = when (refused.size) {
    0 -> null
    1 -> "${refused.single().label} needs bigger widgets: its spaces would be too small to tap. Resize one first."
    else -> {
        val names = refused.dropLast(1).joinToString(", ") { it.label } + " and " + refused.last().label
        "$names need bigger widgets: their spaces would be too small to tap. Resize one first."
    }
}

/** layoutFor's reason for the biggest widget set to Default, e.g. "On your biggest widget set to Default: 10 columns need …". */
fun refusalDetail(spec: GridSpec, target: PreviewTarget, density: Density, labelLineDp: Float): String? {
    if (target.example != null) return null
    val reason = GroupPlanner.layoutFor(target.size.width, target.size.height, density, spec, labelLineDp).reason ?: return null
    return "On your biggest widget set to Default: $reason"
}

/**
 * "8 × 4 · 32 spaces · names hidden", or for Auto "Auto at Standard: 7 × 3 · 21 spaces · names shown". When the
 * previewed [widget] has an icon density of its own, the Auto line names the widget instead, because the density
 * control shows PadStudio's: "Your Study widget uses Dense: 10 × 4 · 40 spaces · names hidden" ([densityLead];
 * [groupTitle] is the widget's group). [density] is the one the layout was drawn at ([densityFor]). An exact grid
 * doesn't depend on the density, so its line says nothing about it.
 */
fun previewCaption(
    layout: WidgetLayout,
    spec: GridSpec?,
    density: Density,
    widget: PlacedWidget? = null,
    groupTitle: String? = null,
): String {
    val spaces = layout.columns * layout.rows
    val grid = "${layout.columns} × ${layout.rows} · $spaces ${if (spaces == 1) "space" else "spaces"}"
    val names = if (layout.showLabels) "names shown" else "names hidden"
    if (spec != null) return "$grid · $names"
    return "${densityLead(density, widgetOwns = ownDensity(widget) != null, groupTitle = groupTitle)}: $grid · $names"
}

/** Custom's "Up to 9 × 4 on this widget" (GroupPlanner.largestFit), or why not even 4 × 2 fits. */
fun upToLine(size: SizeDp): String {
    val largest = GroupPlanner.largestFit(size.width, size.height)
        ?: return "This widget is too small for an exact grid: 4 × 2 needs " +
            "${GroupPlanner.widthNeededDp(GridSpec.MIN_COLUMNS)} × ${GroupPlanner.heightNeededDp(GridSpec.MIN_ROWS)} dp."
    return "Up to ${largest.label} on this widget."
}

/** The saved Default as Developer details shows it, including a value that isn't a grid. */
fun savedDefaultLine(saved: String?): String = when {
    saved.isNullOrBlank() -> "Saved default grid: none, so Auto."
    GridSpec.parse(saved) != null -> "Saved default grid: $saved."
    else -> "Saved default grid: \"$saved\" isn't a grid, so widgets set to Default use Auto."
}

/** One placed widget for Developer details: "42 Wide · Default · group STUDY · 776 × 300 dp, 412 × 560 dp". */
fun widgetDetailLine(widget: PlacedWidget): String {
    val grid = when {
        widget.grid == null -> "Default (no grid key)"
        followsDefault(widget.grid) -> if (widget.grid.equals(GridSpec.DEFAULT, ignoreCase = true)) "Default" else "Default (\"${widget.grid}\" isn't a grid)"
        widget.grid.equals(GridSpec.AUTO, ignoreCase = true) -> "own grid: Auto"
        else -> "own grid: ${widget.grid}"
    }
    val sizes = widget.sizes.filter { it.known }.joinToString(", ") { it.label }.ifEmpty { "size not reported" }
    val group = widget.group?.let { "group $it" } ?: "no group saved"
    return "${widget.appWidgetId} ${widget.kind?.label ?: "Unknown size"} · $grid · $group · $sizes"
}
