package com.charan.padstudio.home.kit

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.core.home.GroupPlanner
import com.charan.padstudio.ui.components.Disclosure
import java.util.Locale

/**
 * "Developer details" (closed by default): where the recorded grid came from, what each HyperOS link resolved
 * to, every placed widget with its grid and reported sizes, the saved Default, and the fit rule.
 */
@Composable
internal fun KitDetails(
    record: HomeRecord,
    links: Map<HyperOsScreen, LinkResolution>,
    placed: List<PlacedWidget>?,
    savedGrid: String?,
    density: Density,
    modifier: Modifier = Modifier,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val labelLine = rememberLabelLineDp()
    Disclosure(modifier) {
        when (record) {
            is HomeRecord.Recorded -> with(record.facts) {
                Text(
                    "Home-screen grid: recorded $recordedAt${orientation?.let { ", $it" }.orEmpty()}: $columns × $rows, " +
                        "names ${if (namesHidden) "hidden" else "shown"}. Source: $source",
                )
                Text("HyperOS grid setting recorded: ${hyperosGridSetting?.toString() ?: "not recorded"}. PadStudio can't read the live grid.")
                if (hyperosOptions.isNotEmpty()) Text("HyperOS options recorded: ${hyperosOptions.joinToString("; ")}.")
            }
            HomeRecord.Missing -> Text("Home-screen grid: no homeScreen block in device-facts.json (push one with tools/facts.sh).")
            HomeRecord.Loading -> Text("Home-screen grid: reading device-facts.json…")
        }
        HyperOsScreen.entries.forEach { screen ->
            val found = links[screen]
            val result = if (found == null) {
                "not checked"
            } else {
                when (found.state) {
                    LinkState.READY -> "${found.component} (opens)"
                    LinkState.BLOCKED -> "${found.component} (not exported, so it can't be opened)"
                    LinkState.MISSING -> "nothing resolves"
                }
            }
            Text("${screen.action} in $HYPEROS_LAUNCHER → $result")
        }
        when {
            placed == null -> Text("Widgets: not read yet.")
            placed.isEmpty() -> Text("Widgets: none placed.")
            else -> placed.forEach { Text("Widget ${widgetDetailLine(it)}") }
        }
        Text(savedDefaultLine(savedGrid))
        val target = previewTarget(placed, landscape)
        Text(
            "Preview: ${target.widget?.let { "widget ${it.appWidgetId}" } ?: "example"} at ${target.size.label}, " +
                if (landscape) "landscape." else "portrait.",
        )
        Text(
            "Fit rule: every space at least ${GroupPlanner.MIN_CELL_DP} × ${GroupPlanner.MIN_CELL_DP} dp; usable width = widget − " +
                "${2 * GroupPlanner.PADDING_DP} dp; usable height = widget − ${2 * GroupPlanner.PADDING_DP} − ${GroupPlanner.HEADER_DP} dp header.",
        )
        Text("Label line: 11 sp = ${String.format(Locale.ROOT, "%.1f", labelLine)} dp at this text size.")
        Text("Density saved: ${density.name} (${density.label}).")
    }
}
