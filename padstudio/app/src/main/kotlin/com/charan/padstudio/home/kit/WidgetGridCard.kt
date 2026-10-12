package com.charan.padstudio.home.kit

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.core.home.GridSpec
import com.charan.padstudio.core.home.GroupPlanner
import com.charan.padstudio.core.home.PlannedGroup
import com.charan.padstudio.data.HomeSnapshot
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.ChoiceChip
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.SectionHeader
import com.charan.padstudio.ui.components.SegmentedChoice
import com.charan.padstudio.ui.components.StatusLevel
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.SymIcon
import com.charan.padstudio.ui.components.statusInk
import com.charan.padstudio.ui.theme.Tokens

/**
 * "Widget grid" (plan §6b): the grid for widgets set to Default, with a live preview on your biggest such
 * widget, and the icon density Auto uses. Choosing saves at once (plan §13), through [onDefaultGrid] with
 * GridSpec's stored form or null for Auto, and [onDensity].
 *
 * A preset that no widget set to Default can show is disabled, with the reason. Custom calls [onCustom]: the
 * screen shows [CustomDefaultGridDialog] outside its one- and two-column layouts, so the dialog and its draft
 * survive a rotation across the two-column breakpoint (this card moves between the layouts, and state kept
 * here would not).
 */
@Composable
internal fun WidgetGridCard(
    saved: String?,
    density: Density,
    placed: List<PlacedWidget>?,
    snapshot: HomeSnapshot?,
    onDefaultGrid: (String?) -> Unit,
    onCustom: () -> Unit,
    onDensity: (Density) -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = previewSource(density, placed, snapshot)
    val target = source.target
    val previewDensity = source.density
    val labelLine = source.labelLine
    val savedSpec = GridSpec.parse(saved)
    val layout = GroupPlanner.layoutFor(target.size.width, target.size.height, previewDensity, savedSpec, labelLine)
    val refused = refusedPresets(placed, source.landscape, previewDensity, labelLine)
    val selected = selectedOption(saved)
    val group = source.group
    val apps = source.apps

    StudioCard(modifier, spacing = Tokens.GapS) {
        SectionHeader(title = "Widget grid", supporting = defaultScopeLine(placed))
        FlowRow(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gridOptions(saved).forEach { option ->
                ChoiceChip(
                    label = optionLabel(option),
                    selected = option == selected,
                    onClick = {
                        when (option) {
                            is GridOption.Custom -> onCustom()
                            else -> onDefaultGrid(storedValue(option))
                        }
                    },
                    enabled = !(option is GridOption.Preset && option.spec in refused),
                )
            }
        }
        refusalLine(refused)?.let { line ->
            FitNote(line, detail = refused.lastOrNull()?.let { refusalDetail(it, target, previewDensity, labelLine) })
        }
        WidgetPreview(
            layout = layout,
            widgetSize = target.size,
            apps = apps,
            title = group?.group?.title ?: "Preview",
            description = "Preview of a widget set to Default",
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(previewCaption(layout, savedSpec, previewDensity), style = MaterialTheme.typography.bodyMedium)
            layout.note?.let { FitNote(it) }
            Text(
                previewSourceLine(target, group?.group?.title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DensityChoice(density, onDensity)
    }
}

/**
 * Custom for the Default grid (see [CustomGridDialog]), previewed on the same widget as [WidgetGridCard].
 * [onUse] gets the chosen grid; the caller saves it with GridSpec's stored form (`spec.toString()`).
 *
 * Call it where the screen's layout doesn't change with the window width, and keep its open flag there with
 * rememberSaveable: then a rotation keeps the dialog open with its draft.
 */
@Composable
internal fun CustomDefaultGridDialog(
    saved: String?,
    density: Density,
    placed: List<PlacedWidget>?,
    snapshot: HomeSnapshot?,
    onUse: (GridSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    val source = previewSource(density, placed, snapshot)
    val target = source.target
    val shown = GroupPlanner.layoutFor(target.size.width, target.size.height, source.density, GridSpec.parse(saved), source.labelLine)
    CustomGridDialog(
        start = customStart(saved, shown),
        target = target,
        density = source.density,
        labelLine = source.labelLine,
        fitsSomeWidget = { fitsSomeDefaultWidget(it, placed, source.landscape, source.density, source.labelLine) },
        apps = source.apps,
        title = source.group?.group?.title ?: "Preview",
        onUse = onUse,
        onDismiss = onDismiss,
    )
}

/** What the Default grid is previewed on: the widget ([target]), its icon [density], the label line, and the group shown. */
private class PreviewSource(
    val landscape: Boolean,
    val labelLine: Float,
    val target: PreviewTarget,
    val density: Density,
    val group: PlannedGroup?,
    val apps: List<InstalledApp>,
)

@Composable
private fun previewSource(density: Density, placed: List<PlacedWidget>?, snapshot: HomeSnapshot?): PreviewSource {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val labelLine = rememberLabelLineDp()
    val target = previewTarget(placed, landscape)
    val group = previewGroup(target, snapshot?.groups.orEmpty())
    return PreviewSource(
        landscape = landscape,
        labelLine = labelLine,
        target = target,
        density = densityFor(target.widget, density),
        group = group,
        apps = group?.apps.orEmpty().mapNotNull { snapshot?.app(it) },
    )
}

/** The group the preview shows: the previewed widget's own, as the widget picks it, else your biggest group. */
private fun previewGroup(target: PreviewTarget, groups: List<PlannedGroup>): PlannedGroup? {
    val widget = target.widget ?: return groups.maxByOrNull { it.apps.size }
    return groups.firstOrNull { it.group.name == widget.group } ?: groups.firstOrNull()
}

/** A fit warning: the kit's aspect-ratio symbol in the warn ink, the line, and an optional quieter detail. */
@Composable
internal fun FitNote(line: String, detail: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SymIcon(Sym.fitWarn, contentDescription = null, size = 18.dp, tint = statusInk(StatusLevel.Warn), modifier = Modifier.padding(top = 1.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(line, style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

/**
 * "Icon density · used by Auto": the existing setting. Segmented as in the mock while the three labels fit
 * the card on one line (measured at the current text size); chips that wrap otherwise.
 */
@Composable
private fun DensityChoice(density: Density, onDensity: (Density) -> Unit) {
    val type = MaterialTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Icon density", style = type.titleSmall, modifier = Modifier.semantics { heading() })
        Text(
            "Used by widgets on Auto, and by the folder and library panels.",
            style = type.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val measurer = rememberTextMeasurer()
            val style = type.labelLarge
            val pxDensity = LocalDensity.current
            val needed = remember(measurer, style, pxDensity) {
                val widest = Density.entries.maxOf { measurer.measure(it.label, style, maxLines = 1, softWrap = false).size.width }
                maxOf(SEGMENT_MIN, with(pxDensity) { widest.toDp() } + SEGMENT_CHROME) * Density.entries.size
            }
            if (needed <= maxWidth) {
                SegmentedChoice(
                    options = Density.entries,
                    selected = density,
                    onSelect = onDensity,
                    label = { it.label },
                )
            } else {
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Density.entries.forEach { option ->
                        ChoiceChip(label = option.label, selected = option == density, onClick = { onDensity(option) })
                    }
                }
            }
        }
    }
}

/**
 * Material 3 1.4.0's segmented row gives every segment the same width (weight 1 in an IntrinsicSize.Min row),
 * at least ButtonDefaults.MinWidth (58 dp), and each always keeps 12 + 12 dp of padding, the 18 dp check and
 * its 8 dp gap beside its label (read from the 1.4.0 classes).
 */
private val SEGMENT_MIN = 58.dp
private val SEGMENT_CHROME = 50.dp

/**
 * Custom: columns 4–10 and rows 2–8 as chips, "Up to C × R" for the previewed widget, the draft drawn on it,
 * and Use. The draft lives only in the dialog (kept across recreation); Cancel, Back or a tap outside
 * change nothing. Use is disabled when no widget set to Default could show the draft.
 */
@Composable
private fun CustomGridDialog(
    start: GridSpec,
    target: PreviewTarget,
    density: Density,
    labelLine: Float,
    fitsSomeWidget: (GridSpec) -> Boolean?,
    apps: List<InstalledApp>,
    title: String,
    onUse: (GridSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    var columns by rememberSaveable { mutableIntStateOf(start.columns) }
    var rows by rememberSaveable { mutableIntStateOf(start.rows) }
    val draft = GridSpec(columns.coerceIn(GridSpec.COLUMNS), rows.coerceIn(GridSpec.ROWS))
    val layout = GroupPlanner.layoutFor(target.size.width, target.size.height, density, draft, labelLine)
    val refused = fitsSomeWidget(draft) == false
    val type = MaterialTheme.typography
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { PillButton(text = "Use ${draft.label}", onClick = { onUse(draft) }, style = PillStyle.Text, enabled = !refused) },
        dismissButton = { PillButton(text = "Cancel", onClick = onDismiss, style = PillStyle.Text) },
        title = { Text("Custom grid") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.GapS)) {
                Text(upToLine(target.size), style = type.bodyLarge)
                NumberChips(label = "Columns", range = GridSpec.COLUMNS, value = draft.columns, onValue = { columns = it })
                NumberChips(label = "Rows", range = GridSpec.ROWS, value = draft.rows, onValue = { rows = it })
                WidgetPreview(
                    layout = layout,
                    widgetSize = target.size,
                    apps = apps,
                    title = title,
                    description = "Preview of ${draft.label}",
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(previewCaption(layout, draft, density), style = type.bodyMedium)
                    val note = layout.note
                    when {
                        refused -> refusalLine(listOf(draft))?.let { FitNote(it, detail = refusalDetail(draft, target, density, labelLine)) }
                        note != null -> FitNote(note, detail = layout.reason)
                    }
                    Text(previewSourceLine(target, null), style = type.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        shape = RoundedCornerShape(Tokens.CornerXl),
    )
}

/** One number out of [range] as a row of chips that wraps, under its [label]. */
@Composable
private fun NumberChips(label: String, range: IntRange, value: Int, onValue: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            range.forEach { number ->
                ChoiceChip(label = number.toString(), selected = number == value, onClick = { onValue(number) })
            }
        }
    }
}
