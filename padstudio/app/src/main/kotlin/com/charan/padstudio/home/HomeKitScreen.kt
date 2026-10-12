package com.charan.padstudio.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charan.padstudio.MainViewModel
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.home.kit.CustomDefaultGridDialog
import com.charan.padstudio.home.kit.GroupsCard
import com.charan.padstudio.home.kit.HomeGridCard
import com.charan.padstudio.home.kit.HyperOsScreen
import com.charan.padstudio.home.kit.KitDetails
import com.charan.padstudio.home.kit.OpenFailure
import com.charan.padstudio.home.kit.OrganizeScreen
import com.charan.padstudio.home.kit.WidgetGridCard
import com.charan.padstudio.home.kit.addedMessage
import com.charan.padstudio.home.kit.canRequestPin
import com.charan.padstudio.home.kit.movedMessage
import com.charan.padstudio.home.kit.openFolderPanel
import com.charan.padstudio.home.kit.openHyperOs
import com.charan.padstudio.home.kit.openLibrary
import com.charan.padstudio.home.kit.rememberHomeRecord
import com.charan.padstudio.home.kit.rememberHyperOsLinks
import com.charan.padstudio.home.kit.rememberPlacedWidgets
import com.charan.padstudio.home.kit.resetMessage
import com.charan.padstudio.home.kit.timeNow
import com.charan.padstudio.ui.LocalStudioSnackbar
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.CardColumns
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.ScreenScaffold
import kotlinx.coroutines.launch

/**
 * Home (plan §6 and §10 "Home Kit"; docs/redesign/mock-homekit.html):
 * - "Home screen grid": HyperOS's grid as recorded on the tablet (outcome G5-B, ADR-005), never as live state,
 *   with links to HyperOS's nearest options;
 * - "Widget grid": the grid for widgets set to Default (MainViewModel.setDefaultWidgetGrid) with a live preview
 *   of your icons, and the icon density Auto uses (setDensity);
 * - "Your groups": Add widget in three sizes (pinWidget) and Open (the folder panel), Hidden last;
 * - Organize, from the top-right action: move apps between groups (move, MoveDialog) and undo every move
 *   (resetOverrides, confirmed first). Two panes at [WIDE_LAYOUT] and wider.
 *
 * Settings save as soon as they are chosen (plan §13). Placed widgets, the recorded grid and the HyperOS links
 * are read again on every resume.
 */
@Composable
fun HomeKitScreen(model: MainViewModel, modifier: Modifier = Modifier) {
    val config by model.config.collectAsStateWithLifecycle()
    val snapshot by model.snapshot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val placed by rememberPlacedWidgets(resumes)
    val record by rememberHomeRecord(resumes)
    val links = rememberHyperOsLinks(resumes)

    var organizing by rememberSaveable { mutableStateOf(false) }
    var organizeGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var linkFailure by rememberSaveable { mutableStateOf<Pair<HyperOsScreen, OpenFailure>?>(null) }
    // Kept here, not in the card: the card changes place at WIDE_LAYOUT, which every rotation on the tablet crosses.
    var customGridOpen by rememberSaveable { mutableStateOf(false) }
    val overviewScroll = rememberScrollState()
    val gridCard = remember { BringIntoViewRequester() }
    BackHandler(enabled = organizing) { organizing = false }

    // Success states with the time (plan §13) go to the shell's one snackbar host, where they queue with
    // MainViewModel's messages (pinWidget's failure, for one) instead of overlapping them in a second host.
    val snackbar = LocalStudioSnackbar.current
    val notify: (String) -> Unit = { text -> scope.launch { snackbar?.showSnackbar(text) } }

    // Add widget: the launcher answers on the home screen, so success is a widget that wasn't there before.
    var pinRequest by remember { mutableStateOf<PinRequest?>(null) }
    LaunchedEffect(placed) {
        val request = pinRequest ?: return@LaunchedEffect
        val now = placed ?: return@LaunchedEffect
        if (System.currentTimeMillis() - request.at > PIN_WINDOW_MS) {
            pinRequest = null
            return@LaunchedEffect
        }
        val added = now.firstOrNull { it.appWidgetId !in request.before } ?: return@LaunchedEffect
        pinRequest = null
        val group = AppGroup.entries.firstOrNull { it.name == added.group }?.title
        notify(addedMessage(added.kind, group, timeNow(context)))
    }

    Box(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= WIDE_LAYOUT
            if (organizing) {
                OrganizeScreen(
                    snapshot = snapshot,
                    overrides = config.overrides,
                    wide = wide,
                    selected = organizeGroup,
                    onSelect = { organizeGroup = it },
                    onMove = { packageName, label, group ->
                        model.move(packageName, group)
                        notify(movedMessage(label, group, timeNow(context)))
                    },
                    onReset = {
                        val count = config.overrides.size
                        model.resetOverrides()
                        notify(resetMessage(count, timeNow(context)))
                    },
                    onDone = { organizing = false },
                )
            } else {
                ScreenScaffold(
                    title = "Home",
                    subtitle = "Group widgets keep your home screen tidy.",
                    scrollState = overviewScroll,
                    actions = { PillButton(text = "Organize", onClick = { organizing = true }, style = PillStyle.Outlined, icon = Sym.edit) },
                ) {
                    val homeGrid: @Composable () -> Unit = {
                        HomeGridCard(
                            record = record,
                            links = links,
                            failure = linkFailure,
                            onOpen = { screen -> linkFailure = openHyperOs(context, screen)?.let { screen to it } },
                            onDenser = { scope.launch { gridCard.bringIntoView() } },
                        )
                    }
                    val widgetGrid: @Composable () -> Unit = {
                        WidgetGridCard(
                            saved = config.widgetGrid,
                            density = config.density,
                            placed = placed,
                            snapshot = snapshot,
                            onDefaultGrid = model::setDefaultWidgetGrid,
                            onCustom = { customGridOpen = true },
                            onDensity = model::setDensity,
                            modifier = Modifier.bringIntoViewRequester(gridCard),
                        )
                    }
                    val groups: @Composable () -> Unit = {
                        GroupsCard(
                            snapshot = snapshot,
                            hiddenCount = snapshot?.placements.orEmpty().count { it.group == AppGroup.HIDDEN },
                            onPin = { group, size ->
                                val before = placed
                                if (before != null && canRequestPin(context)) {
                                    pinRequest = PinRequest(before.map { it.appWidgetId }.toSet(), System.currentTimeMillis())
                                }
                                model.pinWidget(group, size)
                            },
                            onOpen = { group -> openFolderPanel(context, group) },
                            onOpenHidden = {
                                organizeGroup = AppGroup.HIDDEN.name
                                organizing = true
                            },
                            onOpenLibrary = { openLibrary(context) },
                        )
                    }
                    if (wide) {
                        CardColumns(endWidth = SIDE_COLUMN, stackBelow = 0.dp, start = { homeGrid(); groups() }, end = { widgetGrid() })
                    } else {
                        homeGrid()
                        widgetGrid()
                        groups()
                    }
                    KitDetails(record = record, links = links, placed = placed, savedGrid = config.widgetGrid, density = config.density)
                }
            }
        }
    }

    // Outside the one- and two-column layouts, so the dialog and its draft stay through a rotation.
    if (customGridOpen) {
        CustomDefaultGridDialog(
            saved = config.widgetGrid,
            density = config.density,
            placed = placed,
            snapshot = snapshot,
            onUse = { spec ->
                customGridOpen = false
                model.setDefaultWidgetGrid(spec.toString())
            },
            onDismiss = { customGridOpen = false },
        )
    }
}

/** From this width the page has two columns, and Organize two panes (plan §10). */
private val WIDE_LAYOUT = 840.dp

/** The mock's widget-grid column. */
private val SIDE_COLUMN = 360.dp

/** An Add widget request: the widgets placed before it, and when it was made. */
private data class PinRequest(val before: Set<Int>, val at: Long)

/** A widget that shows up later than this after a request isn't claimed as its result. */
private const val PIN_WINDOW_MS = 2 * 60 * 1000L
