package com.charan.padstudio.home.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.data.HomeSnapshot
import com.charan.padstudio.home.MoveDialog
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.AppIconStack
import com.charan.padstudio.ui.components.ChoiceChip
import com.charan.padstudio.ui.components.ConfirmDialog
import com.charan.padstudio.ui.components.EmptyState
import com.charan.padstudio.ui.components.ListRow
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.RowLeading
import com.charan.padstudio.ui.components.RowTrailing
import com.charan.padstudio.ui.components.ScreenScaffold
import com.charan.padstudio.ui.components.SectionHeader
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.cardBleed
import com.charan.padstudio.ui.theme.Tokens

/**
 * Organize (plan §10): every group as the widgets show it, Hidden last, and the apps of the chosen group. Tap
 * an app to move it with [MoveDialog] ([onMove] calls MainViewModel.move). "Undo all my moves" asks first,
 * then [onReset] (MainViewModel.resetOverrides).
 *
 * At [wide] (840 dp and up) the groups and the apps are two panes that scroll on their own; narrower, the
 * groups are chips above the apps. The chosen group, an open move and an open confirmation survive
 * recreation.
 */
@Composable
internal fun OrganizeScreen(
    snapshot: HomeSnapshot?,
    overrides: Map<String, AppGroup>,
    wide: Boolean,
    selected: String?,
    onSelect: (String) -> Unit,
    onMove: (packageName: String, label: String, group: AppGroup) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(snapshot) { snapshot?.let { organizeGroups(it.groups, it.placements) }.orEmpty() }
    val current = organizeSelection(groups, selected)
    var moving by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(
        title = "Organize",
        modifier = modifier,
        subtitle = "Tap an app to move it. Widgets update after each move.",
        scrollState = if (wide) null else rememberScrollState(),
        actions = { PillButton(text = "Done", onClick = onDone, style = PillStyle.Outlined, icon = Sym.ok) },
    ) {
        if (snapshot == null) {
            Text("Sorting your apps…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@ScreenScaffold
        }
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Tokens.GapM)) {
                StudioCard(Modifier.width(GROUP_PANE).fillMaxHeight(), contentPadding = PaddingValues(0.dp)) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(Tokens.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(Tokens.GapS),
                    ) {
                        SectionHeader(title = "Groups")
                        GroupList(groups, current, snapshot, onSelect)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ResetBlock(overrides.size, onAsk = { confirmReset = true })
                    }
                }
                StudioCard(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(0.dp)) {
                    // A new group starts at its top.
                    key(current?.group) {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()).padding(Tokens.CardPadding),
                            verticalArrangement = Arrangement.spacedBy(Tokens.GapS),
                        ) {
                            current?.let { AppsPane(it, snapshot, onPick = { app -> moving = app }) }
                        }
                    }
                }
            }
        } else {
            StudioCard {
                SectionHeader(title = "Groups")
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { entry ->
                        ChoiceChip(
                            label = "${entry.group.title} ${entry.apps.size}",
                            selected = entry.group == current?.group,
                            onClick = { onSelect(entry.group.name) },
                        )
                    }
                }
            }
            current?.let { StudioCard { AppsPane(it, snapshot, onPick = { app -> moving = app }) } }
            StudioCard { ResetBlock(overrides.size, onAsk = { confirmReset = true }) }
        }
    }

    moving?.let { packageName ->
        val placement = snapshot?.placements?.firstOrNull { it.app.packageName == packageName }
        // An app uninstalled while its dialog was open: nothing left to move.
        LaunchedEffect(snapshot, placement) { if (snapshot != null && placement == null) moving = null }
        if (placement != null) {
            MoveDialog(placement.app.label, onDismiss = { moving = null }) { group ->
                moving = null
                onMove(packageName, placement.app.label, group)
            }
        }
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Undo all your moves?",
            body = resetBody(overrides),
            confirmLabel = "Undo moves",
            onConfirm = {
                confirmReset = false
                onReset()
            },
            onDismiss = { confirmReset = false },
            icon = Sym.undo,
        )
    }
}

private val GROUP_PANE = 320.dp

/** The groups as a picker: each with its icons and count, the chosen one highlighted; Hidden last. */
@Composable
private fun GroupList(groups: List<OrganizeGroup>, current: OrganizeGroup?, snapshot: HomeSnapshot, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.cardBleed().selectableGroup()) {
        groups.forEach { entry ->
            val chosen = entry.group == current?.group
            val apps = entry.apps.mapNotNull { snapshot.app(it.facts) }
            ListRow(
                headline = entry.group.title,
                supporting = if (entry.group == AppGroup.HIDDEN) hiddenLine(entry.apps.size) else appCount(entry.apps.size),
                leading = if (entry.group == AppGroup.HIDDEN || apps.isEmpty()) {
                    RowLeading.Symbol(Sym.namesHidden, tonal = true)
                } else {
                    RowLeading.Custom {
                        AppIconStack(apps = apps, size = 32.dp, max = 2, ringColor = if (chosen) scheme.secondaryContainer else scheme.surfaceContainerLow)
                    }
                },
                selected = chosen,
                onClick = { onSelect(entry.group.name) },
            )
        }
    }
}

/** The chosen group's apps, each with why it is there; tapping one asks where to move it. */
@Composable
private fun AppsPane(entry: OrganizeGroup, snapshot: HomeSnapshot, onPick: (String) -> Unit) {
    val hidden = entry.group == AppGroup.HIDDEN
    SectionHeader(
        title = entry.group.title,
        supporting = if (hidden) hiddenLine(entry.apps.size) else "${appCount(entry.apps.size)} · tap one to move it",
    )
    if (entry.apps.isEmpty()) {
        EmptyState(
            title = if (hidden) "No hidden apps" else "No apps in ${entry.group.title} right now",
            icon = if (hidden) Sym.namesHidden else Sym.home,
            body = if (hidden) "Move an app here to keep it out of every widget." else null,
        )
        return
    }
    Column(Modifier.cardBleed()) {
        entry.apps.forEach { app ->
            val installed = snapshot.app(app.facts)
            ListRow(
                headline = app.facts.label,
                supporting = app.reason.ifEmpty { null },
                leading = installed?.let { RowLeading.App(it) } ?: RowLeading.Symbol(Sym.home),
                trailing = RowTrailing.Value("Move"),
                onClick = { onPick(app.facts.packageName) },
            )
        }
    }
}

/** How many apps you moved, and "Undo all my moves" (confirmed first). */
@Composable
private fun ResetBlock(count: Int, onAsk: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(movesLine(count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PillButton(text = "Undo all my moves", onClick = onAsk, style = PillStyle.Outlined, icon = Sym.undo, enabled = count > 0)
    }
}
