package com.charan.padstudio.home

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.util.TypedValue
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charan.padstudio.core.home.AppFacts
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.core.home.GridSpec
import com.charan.padstudio.core.home.GroupPlanner
import com.charan.padstudio.core.home.PlannedGroup
import com.charan.padstudio.core.home.WidgetLayout
import com.charan.padstudio.data.HomeSnapshot
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.data.StudioConfig
import com.charan.padstudio.studio
import com.charan.padstudio.ui.PadStudioTheme
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.AppIconStack
import com.charan.padstudio.ui.components.CardTone
import com.charan.padstudio.ui.components.ChoiceChip
import com.charan.padstudio.ui.components.EmptyState
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillButtonDefaults
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.ScreenDefaults
import com.charan.padstudio.ui.components.SegmentedChoice
import com.charan.padstudio.ui.components.StatusLevel
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.SymIcon
import com.charan.padstudio.ui.components.SymIconButton
import com.charan.padstudio.ui.components.statusInk
import com.charan.padstudio.ui.theme.Tokens
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// The launcher-side panels (plan §10 Panels, docs/redesign/mock-panel.html): the folder a group widget
// opens, the library a pen press opens, and Edit widget. Each is a translucent activity over the home
// screen: the window blurs (or dims) what is behind it, and the content is one or two frosted sheets
// with 28 dp corners. A tap outside every sheet, Back, or Close dismisses it.

/** Blurs whatever is behind a panel when HyperOS allows cross-window blur, otherwise dims it. */
private fun ComponentActivity.blurOrDimBehind() {
    val wm = getSystemService(WindowManager::class.java)
    if (wm.isCrossWindowBlurEnabled) {
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.also { it.blurBehindRadius = 64 }
        window.setDimAmount(0.25f)
    } else {
        window.setDimAmount(0.55f)
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
}

/**
 * Draws a panel edge to edge, so its sheets keep clear of the system bars and the keyboard on every
 * Android version, with light bar icons over the dimmed home screen (as before: the panel theme never
 * asked for dark icons), then blurs or dims what is behind it.
 */
private fun ComponentActivity.openPanelWindow() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    blurOrDimBehind()
}

/** True while what is behind the panel is blurred, not just dimmed: the sheet can then let a little of it through. */
private fun ComponentActivity.blursBehind(): Boolean = getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled

/** A widget id that is a placed widget of this app (not a stale or foreign id from an intent). */
private fun Context.isOwnWidget(appWidgetId: Int): Boolean =
    appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
        runCatching { AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.packageName == packageName }
            .getOrDefault(false)

/**
 * The folder that opens from a group widget's header or "+N" tile: the group's apps, search, and
 * "Edit widget" for the widget it came from. That widget is the intent's
 * [AppWidgetManager.EXTRA_APPWIDGET_ID] when the widget passes it, otherwise the one widget showing this
 * group, if exactly one does.
 */
class FolderPanelActivity : ComponentActivity() {
    /** What to show. singleTask: a second tap on a widget arrives through [onNewIntent], so this follows it. */
    private var request by mutableStateOf(FolderRequest(group = null, widgetId = null))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openPanelWindow()
        request = readRequest(intent)
        val frosted = blursBehind()
        setContent {
            PadStudioTheme {
                FolderPanel(
                    request = request,
                    frosted = frosted,
                    onClose = ::finish,
                    onOpenLibrary = {
                        startActivity(Intent(this, LibraryActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        request = readRequest(intent)
    }

    private fun readRequest(intent: Intent): FolderRequest {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return FolderRequest(group = intent.getStringExtra(EXTRA_GROUP), widgetId = widgetId.takeIf { isOwnWidget(it) })
    }

    companion object {
        const val EXTRA_GROUP = "com.charan.padstudio.GROUP"
    }
}

/** Every group as a chip, plus search across all apps. A pen press can open this (Key Mapper INTENT action). */
class LibraryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openPanelWindow()
        val frosted = blursBehind()
        setContent {
            PadStudioTheme {
                LibraryPanel(frosted = frosted, onClose = ::finish)
            }
        }
    }
}

/**
 * One widget's own settings: which group it shows, its grid and its density (plan §6b). It is the
 * widget's configuration activity (Android passes EXTRA_APPWIDGET_ID) and also opens from the widget's
 * "Edit", since HyperOS may not offer "reconfigure" itself. Every change is written at once and the widget
 * redrawn; Done closes it.
 */
class EditGroupWidgetActivity : ComponentActivity() {
    /** Something was saved: the launcher then keeps a newly placed widget, even after a rotation. */
    private var changed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        changed = savedInstanceState?.getBoolean(STATE_CHANGED) == true
        setResult(if (changed) RESULT_OK else RESULT_CANCELED, resultFor(appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        openPanelWindow()
        val frosted = blursBehind()
        setContent {
            PadStudioTheme {
                EditWidgetPanel(
                    appWidgetId = appWidgetId,
                    frosted = frosted,
                    onChanged = {
                        changed = true
                        setResult(RESULT_OK, resultFor(appWidgetId))
                    },
                    onDone = {
                        setResult(RESULT_OK, resultFor(appWidgetId))
                        finish()
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_CHANGED, changed)
    }

    private fun resultFor(appWidgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private companion object {
        const val STATE_CHANGED = "changed"
    }
}

/** The folder to show: a group name from [FolderPanelActivity.EXTRA_GROUP] and, when known, the widget it came from. */
private data class FolderRequest(val group: String?, val widgetId: Int?)

// --- Folder -----------------------------------------------------------------------------------------

@Composable
private fun FolderPanel(request: FolderRequest, frosted: Boolean, onClose: () -> Unit, onOpenLibrary: () -> Unit) {
    val context = LocalContext.current
    val config by context.studio.config.config.collectAsStateWithLifecycle()
    val (load, retry) = rememberSnapshotLoad(config.overrides)
    // The folder follows its widget: choosing another group in Edit widget shows that group here too.
    var groupName by rememberSaveable(request) { mutableStateOf(request.group) }
    var widgetId by rememberSaveable(request) { mutableStateOf(request.widgetId) }
    var query by rememberSaveable(request, groupName) { mutableStateOf("") }
    var editing by rememberSaveable(request) { mutableStateOf(false) }
    LaunchedEffect(request) {
        if (widgetId == null && request.group != null) widgetId = onlyWidgetShowing(context.applicationContext, request.group)
    }
    val messages = remember { SnackbarHostState() }
    val say = rememberSay(messages)
    val editId = widgetId
    BackHandler(enabled = editing && editId != null) { editing = false }

    PanelScrim(onDismiss = onClose, messages = messages) {
        val sheetHeight = maxHeight
        val folder: @Composable (Modifier) -> Unit = { modifier ->
            FolderSheet(
                modifier = modifier.heightIn(max = sheetHeight),
                frosted = frosted,
                load = load,
                onRetry = retry,
                groupName = groupName,
                density = config.density,
                query = query,
                onQuery = { query = it },
                canEdit = editId != null,
                editing = editing,
                onEdit = { editing = !editing },
                onOpenLibrary = onOpenLibrary,
                onClose = onClose,
                onMessage = say,
            )
        }
        val edit: @Composable (Modifier, Int) -> Unit = { modifier, id ->
            EditWidgetSheet(
                modifier = modifier.heightIn(max = sheetHeight),
                frosted = frosted,
                appWidgetId = id,
                config = config,
                load = load,
                onChanged = {},
                onDone = { editing = false },
                onGroupChanged = { groupName = it.name },
            )
        }
        when {
            // List-detail at 840 dp and wider, as the mock: the folder, and Edit widget beside it, sharing the
            // width as the mock's 576 : 516 within the page's 1040 dp content width.
            editing && editId != null && maxWidth >= TWO_PANES -> Row(
                modifier = Modifier.widthIn(max = ScreenDefaults.MaxContentWidth).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PANE_GAP),
                verticalAlignment = Alignment.Top,
            ) {
                folder(Modifier.weight(FOLDER_WIDTH.value))
                edit(Modifier.weight(EDIT_WIDTH.value), editId)
            }
            editing && editId != null -> edit(Modifier.widthIn(max = EDIT_WIDTH_ALONE).fillMaxWidth(), editId)
            else -> folder(Modifier.widthIn(max = FOLDER_WIDTH_ALONE).fillMaxWidth())
        }
    }
}

@Composable
private fun FolderSheet(
    modifier: Modifier,
    frosted: Boolean,
    load: SnapshotLoad,
    onRetry: () -> Unit,
    groupName: String?,
    density: Density,
    query: String,
    onQuery: (String) -> Unit,
    canEdit: Boolean,
    editing: Boolean,
    onEdit: () -> Unit,
    onOpenLibrary: () -> Unit,
    onClose: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val group = AppGroup.entries.firstOrNull { it.name == groupName }
    val snapshot = (load as? SnapshotLoad.Ready)?.snapshot
    val planned = snapshot?.groups?.firstOrNull { it.group == group }
    val apps = remember(snapshot, planned) { if (snapshot == null || planned == null) emptyList() else planned.apps.mapNotNull(snapshot::app) }
    FrostedSheet(modifier, frosted) {
        PanelHeader(
            title = group?.title ?: "Apps",
            overline = "Group",
            subtitle = when {
                load is SnapshotLoad.Loading -> "Loading…"
                load is SnapshotLoad.Failed -> null
                planned == null -> "No apps right now"
                else -> PanelText.appCount(apps.size)
            },
            actions = {
                PillButton(
                    text = "Open library",
                    onClick = onOpenLibrary,
                    style = PillStyle.Outlined,
                    icon = Sym.library,
                    shape = PillButtonDefaults.FullPill,
                )
                if (canEdit) {
                    // Pressed while the editor is open: a tonal container and a filled pencil, besides the pane itself.
                    PillButton(
                        text = "Edit widget",
                        onClick = onEdit,
                        style = if (editing) PillStyle.Tonal else PillStyle.Outlined,
                        icon = Sym.filledIf(Sym.edit, editing),
                        shape = PillButtonDefaults.FullPill,
                    )
                }
            },
            close = { SymIconButton(icon = Sym.close, contentDescription = "Close", onClick = onClose) },
        )
        when {
            load is SnapshotLoad.Loading -> LoadingLine("Loading your apps…")
            load is SnapshotLoad.Failed -> LoadFailed(load.reason, onRetry)
            planned == null -> EmptyState(
                title = if (group != null) "No apps in ${group.title} right now" else "This group isn't available",
                icon = Sym.grid,
                body = "Find any app in the library. Long-press an app there to move it to a group.",
                actionLabel = "Open library",
                onAction = onOpenLibrary,
            )
            else -> {
                SearchField(query = query, onQuery = onQuery, modifier = Modifier.padding(top = 16.dp))
                val shown = apps.matching(query)
                if (shown.isEmpty()) {
                    EmptyState(title = "No apps match “${query.trim()}”", icon = Sym.search, body = "Try part of the name, or search the library.")
                } else {
                    PanelAppGrid(
                        apps = shown,
                        density = density,
                        onLaunched = onClose,
                        onMessage = onMessage,
                        modifier = Modifier.padding(top = 12.dp).weight(1f, fill = false),
                        whereOf = { "In ${planned.group.title}" },
                    )
                }
            }
        }
    }
}

// --- Library ----------------------------------------------------------------------------------------

@Composable
private fun LibraryPanel(frosted: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val config by context.studio.config.config.collectAsStateWithLifecycle()
    val (load, retry) = rememberSnapshotLoad(config.overrides)
    var query by rememberSaveable { mutableStateOf("") }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val messages = remember { SnackbarHostState() }
    val say = rememberSay(messages)
    PanelScrim(onDismiss = onClose, messages = messages) {
        FrostedSheet(Modifier.widthIn(max = LIBRARY_WIDTH).fillMaxWidth().heightIn(max = maxHeight), frosted) {
            val snapshot = (load as? SnapshotLoad.Ready)?.snapshot
            val groups = snapshot?.groups.orEmpty()
            PanelHeader(
                title = "Library",
                subtitle = when (load) {
                    SnapshotLoad.Loading -> "Loading…"
                    is SnapshotLoad.Failed -> null
                    is SnapshotLoad.Ready -> PanelText.librarySubtitle(groups)
                },
                close = { SymIconButton(icon = Sym.close, contentDescription = "Close", onClick = onClose) },
            )
            when {
                load is SnapshotLoad.Loading -> LoadingLine("Loading your apps…")
                load is SnapshotLoad.Failed -> LoadFailed(load.reason, retry)
                snapshot != null -> {
                    SearchField(query = query, onQuery = { query = it }, modifier = Modifier.padding(top = 16.dp))
                    val places = remember(snapshot) { PlaceIndex(snapshot) }
                    if (query.isNotBlank()) {
                        val results = remember(snapshot, query) { snapshot.apps.matching(query).sortedBy { it.facts.label.lowercase() } }
                        if (results.isEmpty()) {
                            EmptyState(title = "No apps match “${query.trim()}”", icon = Sym.search, body = "Try part of the name.")
                        } else {
                            PanelAppGrid(
                                apps = results,
                                density = config.density,
                                onLaunched = onClose,
                                onMessage = say,
                                modifier = Modifier.padding(top = 12.dp).weight(1f, fill = false),
                                whereOf = places::where,
                                isHidden = places::isHidden,
                            )
                        }
                    } else if (groups.isEmpty()) {
                        EmptyState(title = "No apps to show yet", icon = Sym.library, body = "Apps you install appear here, sorted into groups.")
                    } else {
                        val selected = groups.firstOrNull { it.group.name == chosen } ?: groups.first()
                        GroupChips(groups, selected, onSelect = { chosen = it.group.name }, modifier = Modifier.padding(top = 12.dp))
                        // A fresh grid per group, so one group's scroll position never carries over to the next.
                        key(selected.group) {
                            PanelAppGrid(
                                apps = remember(snapshot, selected) { selected.apps.mapNotNull(snapshot::app) },
                                density = config.density,
                                onLaunched = onClose,
                                onMessage = say,
                                modifier = Modifier.padding(top = 8.dp).weight(1f, fill = false),
                                whereOf = { "In ${selected.group.title}" },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The Library's group chips, one per group with its app count, scrolling sideways; the chosen one starts in view. */
@Composable
private fun GroupChips(groups: List<PlannedGroup>, selected: PlannedGroup, onSelect: (PlannedGroup) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = groups.indexOf(selected).coerceAtLeast(0))
    LazyRow(modifier = modifier.fillMaxWidth(), state = state, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(groups, key = { it.group.name }) { group ->
            ChoiceChip(
                label = "${group.group.title} ${group.apps.size}",
                selected = group.group == selected.group,
                onClick = { onSelect(group) },
            )
        }
    }
}

/** Where each app sits, for the long-press menu: its group as the widgets show it, or Hidden. */
private class PlaceIndex(snapshot: HomeSnapshot) {
    private val groupOf: Map<AppFacts, AppGroup> = snapshot.groups.flatMap { planned -> planned.apps.map { it to planned.group } }.toMap()
    private val hidden: Set<AppFacts> = snapshot.placements.filter { it.group == AppGroup.HIDDEN }.map { it.app }.toSet()

    fun where(app: InstalledApp): String? = groupOf[app.facts]?.let { "In ${it.title}" } ?: if (app.facts in hidden) "Hidden" else null

    fun isHidden(app: InstalledApp): Boolean = app.facts in hidden
}

private fun List<InstalledApp>.matching(query: String) =
    if (query.isBlank()) this else filter { it.facts.label.contains(query.trim(), ignoreCase = true) }

// --- Edit widget ------------------------------------------------------------------------------------

@Composable
private fun EditWidgetPanel(appWidgetId: Int, frosted: Boolean, onChanged: () -> Unit, onDone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val config by context.studio.config.config.collectAsStateWithLifecycle()
    val (load, _) = rememberSnapshotLoad(config.overrides)
    val messages = remember { SnackbarHostState() }
    PanelScrim(onDismiss = onDismiss, messages = messages) {
        EditWidgetSheet(
            modifier = Modifier.widthIn(max = EDIT_WIDTH_ALONE).fillMaxWidth().heightIn(max = maxHeight),
            frosted = frosted,
            appWidgetId = appWidgetId,
            config = config,
            load = load,
            onChanged = onChanged,
            onDone = onDone,
        )
    }
}

/**
 * Edit widget (plan §6b, mock-panel.html): the group this widget shows, its grid, and its density. The
 * grid's fit is judged at the size the launcher reports for the current orientation
 * ([AppWidgetManager.getAppWidgetOptions]: portrait is the minimum width × maximum height, landscape the
 * maximum width × minimum height), and the sheet says which it used.
 */
@Composable
private fun EditWidgetSheet(
    modifier: Modifier,
    frosted: Boolean,
    appWidgetId: Int,
    config: StudioConfig,
    load: SnapshotLoad,
    onChanged: () -> Unit,
    onDone: () -> Unit,
    onGroupChanged: (AppGroup) -> Unit = {},
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val editor = rememberWidgetEditor(appWidgetId, onChanged)
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val size = remember(appWidgetId, landscape) { widgetSize(context, appWidgetId, landscape) }
    val labelLineDp = remember(configuration) { widgetLabelLineDp(context) }
    FrostedSheet(modifier, frosted) {
        PanelHeader(
            title = "Edit widget",
            subtitle = editor.statusLine(context),
            actions = { PillButton(text = "Done", onClick = onDone, style = PillStyle.Filled, shape = PillButtonDefaults.FullPill) },
        )
        val state = editor.state
        when {
            editor.loadFailed -> EmptyState(
                title = "Can't edit this widget",
                icon = Sym.widgets,
                body = "It may have been removed from the home screen.",
                actionLabel = "Close",
                onAction = onDone,
            )
            state == null -> LoadingLine("Reading this widget…")
            else -> Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                editor.failure?.let { FailureCard(it, onRetry = editor::retry) }
                GroupField(
                    load = load,
                    saved = state.group,
                    onChoose = { group ->
                        editor.setGroup(group)
                        onGroupChanged(group)
                    },
                )
                val density = state.density?.let { name -> Density.entries.firstOrNull { it.name == name } } ?: config.density
                GridField(
                    saved = GridPicker.read(state.grid),
                    size = size,
                    density = density,
                    defaultGrid = config.widgetGrid,
                    labelLineDp = labelLineDp,
                    onChoose = editor::setGrid,
                )
                DensityField(density = density, onChoose = editor::setDensity)
            }
        }
    }
}

@Composable
private fun GroupField(load: SnapshotLoad, saved: String?, onChoose: (AppGroup) -> Unit) {
    val snapshot = (load as? SnapshotLoad.Ready)?.snapshot
    FieldHead(title = "Group", meta = PanelText.groupMeta(saved, snapshot?.groups))
    when {
        load is SnapshotLoad.Loading -> LoadingLine("Loading your groups…")
        load is SnapshotLoad.Failed -> Text(
            text = "Couldn't read your groups. ${load.reason}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        snapshot == null || snapshot.groups.isEmpty() -> Text(
            text = "No groups yet: there are no apps to sort.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> TileGrid(
            items = snapshot.groups,
            labels = snapshot.groups.map { "${it.group.title} ${it.apps.size}" },
            reserved = TILE_PADDING * 2,
        ) { planned, tileModifier ->
            GroupTile(planned, snapshot, selected = planned.group.name == saved, onClick = { onChoose(planned.group) }, modifier = tileModifier)
        }
    }
}

/** A group as a radio tile: a few of its icons, its name and how many apps it has (the mock's .m-panel-tile). */
@Composable
private fun GroupTile(planned: PlannedGroup, snapshot: HomeSnapshot, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val count = planned.apps.size
    val apps = remember(planned, snapshot) { planned.apps.mapNotNull(snapshot::app) }
    StudioCard(
        modifier = modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
            contentDescription = "${planned.group.title}, ${PanelText.appCount(count)}"
        },
        tone = if (selected) CardTone.Tonal else CardTone.High,
        onClick = onClick,
        contentPadding = PaddingValues(TILE_PADDING),
        spacing = 6.dp,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // The tile's description already says what's in it; the icons are decoration here.
            Box(Modifier.weight(1f).clearAndSetSemantics {}) {
                AppIconStack(
                    apps = apps,
                    size = 28.dp,
                    ringColor = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh,
                )
            }
            if (selected) SymIcon(Sym.ok, contentDescription = null, filled = true, size = 18.dp)
        }
        val ink = LocalContentColor.current
        Text(
            text = buildAnnotatedString {
                append(planned.group.title)
                withStyle(SpanStyle(fontWeight = Tokens.WeightRegular, color = ink.copy(alpha = COUNT_ALPHA))) { append(" $count") }
            },
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun GridField(
    saved: SavedGrid,
    size: WidgetSizeDp?,
    density: Density,
    defaultGrid: String?,
    labelLineDp: Float,
    onChoose: (GridChoice) -> Unit,
) {
    // Custom stays chosen while its grid is edited, even when it lands on a preset's numbers.
    var customOpen by rememberSaveable { mutableStateOf(false) }
    val choice = saved.choice
    val options = remember(size, density, defaultGrid, labelLineDp, choice) { GridPicker.options(size, density, defaultGrid, labelLineDp, choice) }
    FieldHead(title = "Grid", meta = GridPicker.sizeText(size), note = GridPicker.sizeSource(size))
    TileGrid(
        items = options,
        labels = options.map { it.title },
        reserved = TILE_PADDING * 2 + GRID_TILE_SYMBOLS,
    ) { option, tileModifier ->
        GridTile(
            option = option,
            selected = GridPicker.isSelected(option, choice, customOpen),
            modifier = tileModifier,
            onClick = {
                val next = GridPicker.choiceFor(option)
                if (next != null) {
                    customOpen = false
                    onChoose(next)
                } else {
                    customOpen = true
                    if (!GridPicker.isCustom(choice)) onChoose(GridChoice.Exact(GridPicker.initialCustom(choice, size, density, defaultGrid, labelLineDp)))
                }
            },
        )
    }
    GridPicker.reasonText(options)?.let { ReasonCard(it, Modifier.padding(top = 10.dp)) }
    if (saved.invalid) {
        Text(
            text = "This widget's saved grid couldn't be read, so it follows Default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    val spec = (choice as? GridChoice.Exact)?.spec
    if (spec != null && (customOpen || GridPicker.isCustom(choice))) {
        CustomGridEditor(spec, size, density, labelLineDp, onChoose = { onChoose(GridChoice.Exact(it)) })
    }
}

/**
 * One grid choice as a radio tile: a hairline drawing of the grid (or a symbol), its name, and whether it
 * fits this widget. A grid that doesn't fit is disabled and says so in words and with the fit symbol; the
 * reason is read out with it and shown under the tiles.
 */
@Composable
private fun GridTile(option: GridOption, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val enabled = option.enabled
    val fade = if (enabled) 1f else DISABLED_ALPHA
    val description = if (!enabled && option.reason != null) "${option.description}. ${option.reason}" else option.description
    val body: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.alpha(fade)) {
                val mini = option.mini
                if (mini != null) {
                    MiniGrid(mini.columns, mini.rows)
                } else {
                    SymIcon(if (option.kind == GridOptionKind.CUSTOM) Sym.edit else Sym.grid, contentDescription = null, size = 20.dp)
                }
            }
            Text(text = option.title, style = type.labelLarge, modifier = Modifier.weight(1f).alpha(fade))
            if (selected) SymIcon(Sym.ok, contentDescription = null, filled = true, size = 18.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (option.warning) SymIcon(Sym.fitWarn, contentDescription = null, size = 16.dp, tint = statusInk(StatusLevel.Warn))
            Text(
                text = option.detail,
                style = type.bodySmall,
                color = if (selected) LocalContentColor.current else scheme.onSurfaceVariant,
            )
        }
    }
    if (enabled) {
        StudioCard(
            modifier = modifier.semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = description
            },
            tone = if (selected) CardTone.Tonal else CardTone.High,
            onClick = onClick,
            contentPadding = PaddingValues(TILE_PADDING),
            spacing = 6.dp,
            content = body,
        )
    } else {
        // Not a button, but still read out as an option that is unavailable, with its reason.
        Box(
            modifier.semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = description
                disabled()
            },
        ) {
            StudioCard(
                modifier = Modifier.fillMaxSize(),
                tone = if (selected) CardTone.Tonal else CardTone.High,
                contentPadding = PaddingValues(TILE_PADDING),
                spacing = 6.dp,
                content = body,
            )
        }
    }
}

/** A grid drawn in hairlines, 30 × 20 dp (the house style for small data graphics). Decoration only. */
@Composable
private fun MiniGrid(columns: Int, rows: Int) {
    val ink = LocalContentColor.current.copy(alpha = MINI_ALPHA)
    val drawnColumns = columns.coerceIn(1, GridSpec.MAX_COLUMNS)
    val drawnRows = rows.coerceIn(1, GridSpec.MAX_ROWS)
    Canvas(Modifier.size(width = 30.dp, height = 20.dp)) {
        val stroke = 1.dp.toPx()
        val half = stroke / 2f
        drawRoundRect(
            color = ink,
            topLeft = Offset(half, half),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(stroke),
        )
        for (c in 1 until drawnColumns) {
            val x = size.width * c / drawnColumns
            drawLine(ink, Offset(x, 0f), Offset(x, size.height), stroke)
        }
        for (r in 1 until drawnRows) {
            val y = size.height * r / drawnRows
            drawLine(ink, Offset(0f, y), Offset(size.width, y), stroke)
        }
    }
}

/**
 * Custom: columns 4–10 and rows 2–8 as chips. A count this widget has no room for is disabled, with the
 * reason for the first such count of each under them; picking one keeps the other within what fits.
 */
@Composable
private fun CustomGridEditor(spec: GridSpec, size: WidgetSizeDp?, density: Density, labelLineDp: Float, onChoose: (GridSpec) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val model = remember(spec, size, density, labelLineDp) { GridPicker.custom(spec, size, density, labelLineDp) }
    StudioCard(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        tone = CardTone.High,
        contentPadding = PaddingValues(16.dp),
        spacing = 8.dp,
    ) {
        Text(text = "Custom grid", style = type.titleMedium, modifier = Modifier.semantics { heading() })
        PickRow(label = "Columns", picks = model.columns, chosen = spec.columns, onPick = { onChoose(model.withColumns(it)) })
        PickRow(label = "Rows", picks = model.rows, chosen = spec.rows, onPick = { onChoose(model.withRows(it)) })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (model.warning) SymIcon(Sym.fitWarn, contentDescription = null, size = 16.dp, tint = statusInk(StatusLevel.Warn))
            Text(text = model.verdict, style = type.bodyMedium, color = scheme.onSurface)
        }
        model.reasons?.let { ReasonCard(it) }
    }
}

@Composable
private fun PickRow(label: String, picks: List<CustomPick>, chosen: Int, onPick: (Int) -> Unit) {
    Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        picks.forEach { pick ->
            ChoiceChip(label = "${pick.value}", selected = pick.value == chosen, onClick = { onPick(pick.value) }, enabled = pick.enabled)
        }
    }
}

@Composable
private fun DensityField(density: Density, onChoose: (Density) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.padding(end = 16.dp)) {
            FieldTitle("Density")
            Text(text = "Used by Auto", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SegmentedChoice(options = Density.entries, selected = density, onSelect = onChoose, label = { it.label })
    }
}

/** A field's title (primary, as the mock's section labels) with its meta at the right, or under it when there's no room. */
@Composable
private fun FieldHead(title: String, meta: String?, note: String? = null) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.Bottom,
    ) {
        FieldTitle(title, Modifier.padding(end = 12.dp))
        if (meta != null || note != null) {
            Column {
                if (meta != null) Text(text = meta, style = type.bodyMedium, color = scheme.onSurfaceVariant)
                if (note != null) Text(text = note, style = type.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FieldTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

/** Why grids are unavailable: the fit symbol in the warn ink, then layoutFor's words. */
@Composable
private fun ReasonCard(text: String, modifier: Modifier = Modifier) {
    StudioCard(
        modifier = modifier.fillMaxWidth(),
        tone = CardTone.Outlined,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            SymIcon(Sym.fitWarn, contentDescription = null, size = 20.dp, tint = statusInk(StatusLevel.Warn))
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** A change that didn't reach the widget: the plain reason and Retry. */
@Composable
private fun FailureCard(failure: EditFailure, onRetry: () -> Unit) {
    StudioCard(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        tone = CardTone.Outlined,
        contentPadding = PaddingValues(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SymIcon(Sym.error, contentDescription = null, size = 20.dp, tint = statusInk(StatusLevel.Bad))
            Text(
                text = "Couldn't update this widget. ${failure.reason}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            PillButton(text = "Retry", onClick = onRetry, style = PillStyle.Text)
        }
    }
}

/**
 * Tiles in equal columns, at most three across, each row as tall as its tallest tile. How many fit is
 * measured from the widest of [labels] in the tiles' text style (plus [reserved] for padding and symbols),
 * so large or bold text drops to fewer columns instead of squeezing the names.
 */
@Composable
private fun <T> TileGrid(
    items: List<T>,
    labels: List<String>,
    reserved: Dp,
    modifier: Modifier = Modifier,
    tile: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    val widest = remember(labels, style, density) {
        labels.maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val need = with(density) { widest.toDp() } + reserved
        val columns = ((maxWidth + TILE_GAP) / (need + TILE_GAP)).toInt().coerceIn(1, MAX_TILE_COLUMNS)
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
            items.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                    row.forEach { item -> tile(item, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** This widget's size for the current orientation, as the launcher reports it; null if it reports none. */
private fun widgetSize(context: Context, appWidgetId: Int, landscape: Boolean): WidgetSizeDp? = runCatching {
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
    GridPicker.sizeFor(
        minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
        maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
        minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
        maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
        landscape = landscape,
    )
}.getOrNull()

/** One 11 sp widget label line in dp, with Android's own (nonlinear) font scaling, as GroupPlanner.layoutFor wants it. */
private fun widgetLabelLineDp(context: Context): Float {
    val metrics = context.resources.displayMetrics
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, WIDGET_LABEL_SP, metrics) / metrics.density
}

/** The one placed group widget showing [group], or null when none or several do. */
private suspend fun onlyWidgetShowing(context: Context, group: String): Int? = withContext(Dispatchers.Default) {
    runCatching {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(GroupWidget::class.java)
            .filter { id -> getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[GroupWidget.GROUP] == group }
            .singleOrNull()
            ?.let(manager::getAppWidgetId)
    }.getOrNull()
}

/** One widget's saved settings: GroupWidget.GROUP, GRID and DENSITY as stored (null when missing). */
private data class WidgetPrefs(val group: String?, val grid: String?, val density: String?)

private data class EditFailure(val reason: String)

/**
 * Reads one widget's settings from Glance's state, then writes each change at once and redraws the
 * widget, as Edit widget always has (GroupWidget.GROUP and DENSITY; GRID is new). The choice shows on
 * screen straight away; if storing it fails, the sheet re-reads what is really saved and offers Retry.
 * Writes run in order, outside the activity's lifetime, so Done or Back never cuts one short.
 */
@Stable
private class WidgetEditor(private val context: Context, private val appWidgetId: Int, private val onChanged: () -> Unit) {
    var state by mutableStateOf<WidgetPrefs?>(null)
        private set
    var loadFailed by mutableStateOf(false)
        private set
    var failure by mutableStateOf<EditFailure?>(null)
        private set
    private var pending by mutableIntStateOf(0)
    private var savedAt by mutableStateOf<Long?>(null)
    private var retryLast: (() -> Unit)? = null
    private val writes = Mutex()

    suspend fun load() {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
                WidgetPrefs(prefs[GroupWidget.GROUP], prefs[GroupWidget.GRID], prefs[GroupWidget.DENSITY])
            }
        }
        result
            .onSuccess { prefs ->
                if (GridPicker.read(prefs.grid).invalid) Log.w(TAG, "Widget $appWidgetId has an invalid saved grid \"${prefs.grid}\"; shown as Default")
                state = prefs
                loadFailed = false
            }
            .onFailure { error ->
                Log.w(TAG, "Couldn't read widget $appWidgetId", error)
                loadFailed = true
            }
    }

    fun setGroup(group: AppGroup) = write({ it.copy(group = group.name) }) { it[GroupWidget.GROUP] = group.name }

    fun setGrid(choice: GridChoice) = write({ it.copy(grid = choice.stored) }) { it[GroupWidget.GRID] = choice.stored }

    fun setDensity(density: Density) = write({ it.copy(density = density.name) }) { it[GroupWidget.DENSITY] = density.name }

    fun retry() {
        retryLast?.invoke()
    }

    /** The sheet's subtitle: what happened to the last change (plan §13: pending, success with the time, failure). */
    fun statusLine(context: Context): String {
        val at = savedAt
        return when {
            pending > 0 -> "Updating the widget…"
            failure != null -> "Not updated"
            at != null -> "Updated ${DateFormat.getTimeFormat(context).format(Date(at))}"
            else -> "Changes apply right away"
        }
    }

    private fun write(show: (WidgetPrefs) -> WidgetPrefs, store: (MutablePreferences) -> Unit) {
        state = state?.let(show)
        failure = null
        pending++
        WriteScope.launch {
            val outcome = writes.withLock {
                runCatching {
                    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                    updateAppWidgetState(context, glanceId) { store(it) }
                    GroupWidget().update(context, glanceId)
                }
            }
            withContext(Dispatchers.Main) {
                pending--
                outcome
                    .onSuccess {
                        retryLast = null
                        savedAt = System.currentTimeMillis()
                        onChanged()
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Couldn't update widget $appWidgetId", error)
                        retryLast = { write(show, store) }
                        failure = EditFailure(
                            if (error is IllegalArgumentException) "It may have been removed from the home screen." else error.message ?: "Android gave no reason.",
                        )
                        load()
                    }
            }
        }
    }
}

@Composable
private fun rememberWidgetEditor(appWidgetId: Int, onChanged: () -> Unit): WidgetEditor {
    val context = LocalContext.current.applicationContext
    val latest by rememberUpdatedState(onChanged)
    val editor = remember(appWidgetId) { WidgetEditor(context, appWidgetId) { latest() } }
    LaunchedEffect(editor) { editor.load() }
    return editor
}

// --- Shared panel pieces ----------------------------------------------------------------------------

/** The installed apps and their groups, read off the main thread; read again when the user's moves change. */
private sealed interface SnapshotLoad {
    data object Loading : SnapshotLoad

    data class Ready(val snapshot: HomeSnapshot) : SnapshotLoad

    data class Failed(val reason: String) : SnapshotLoad
}

@Composable
private fun rememberSnapshotLoad(overrides: Map<String, AppGroup>): Pair<SnapshotLoad, () -> Unit> {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    val load by produceState<SnapshotLoad>(SnapshotLoad.Loading, overrides, attempt) {
        value = withContext(Dispatchers.Default) {
            runCatching { context.studio.apps.snapshot() }.fold(
                onSuccess = { SnapshotLoad.Ready(it) },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't read the installed apps", error)
                    SnapshotLoad.Failed(error.message ?: "Android gave no reason.")
                },
            )
        }
    }
    return load to { attempt += 1 }
}

/** Shows [String] messages in the panel's snackbar, the newest replacing any still showing. */
@Composable
private fun rememberSay(host: SnackbarHostState): (String) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(host, scope) {
        { text: String ->
            scope.launch {
                host.currentSnackbarData?.dismiss()
                host.showSnackbar(text)
            }
        }
    }
}

/**
 * The window behind the sheets: a tap outside every sheet dismisses the panel. It is not a button (Close
 * and Back do the same for TalkBack and keyboards). [content] is laid out inside the system bars and the
 * keyboard with 24 dp gutters, top-centred as in the mock, and knows the room it has.
 */
@Composable
private fun PanelScrim(onDismiss: () -> Unit, messages: SnackbarHostState, content: @Composable BoxWithConstraintsScope.() -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { dismiss() } }) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = SCRIM_GUTTER, vertical = 12.dp),
            contentAlignment = Alignment.TopCenter,
            content = content,
        )
        SnackbarHost(
            hostState = messages,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                // A tap on a message is not a tap outside the panel.
                .pointerInput(Unit) { detectTapGestures { } },
        )
    }
}

/**
 * A frosted sheet (plan §10 Panels): 28 dp corners, the low surface tinted a little toward the primary
 * container, and, while the window blurs what's behind, slightly see-through. Taps on it never reach the
 * scrim, so only a tap outside dismisses the panel.
 */
@Composable
private fun FrostedSheet(modifier: Modifier, frosted: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tint = lerp(scheme.surfaceContainerLow, scheme.primaryContainer, SHEET_TINT)
    Surface(
        modifier = modifier.pointerInput(Unit) { detectTapGestures { } },
        shape = RoundedCornerShape(Tokens.CornerXl),
        color = tint.copy(alpha = if (frosted) FROSTED_ALPHA else DIMMED_ALPHA),
        contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, scheme.onSurface.copy(alpha = HAIRLINE_ALPHA)),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp), content = content)
    }
}

/**
 * A sheet's header, as the mock: an optional small-capitals [overline], the [title] in the headline role,
 * a one-line [subtitle]; [actions] at the top right and [close] in the corner. When the title needs the
 * room (a narrow sheet, large text) the actions move under it and wrap; Close stays in the corner.
 */
@Composable
private fun PanelHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
    close: @Composable () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Column {
                if (overline != null) {
                    Text(text = overline.uppercase(), style = type.labelMedium.copy(letterSpacing = 0.08.em), color = scheme.onSurfaceVariant)
                }
                Text(
                    text = title,
                    style = type.headlineSmall.copy(fontWeight = Tokens.WeightMedium),
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) Text(text = subtitle, style = type.bodyLarge, color = scheme.onSurfaceVariant)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) { actions() }
            Box { close() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = 8.dp.roundToPx()
        val closer = measurables[2].measure(Constraints())
        val closeRoom = if (closer.width > 0) closer.width + gap else 0
        val actionsWidth = measurables[1].maxIntrinsicWidth(Constraints.Infinity)
        val actionsRoom = if (actionsWidth > 0) actionsWidth + gap else 0
        if (width - closeRoom - actionsRoom >= MIN_TITLE_WIDTH.roundToPx()) {
            val titleBlock = measurables[0].measure(Constraints(maxWidth = width - closeRoom - actionsRoom))
            val actions = measurables[1].measure(Constraints(maxWidth = actionsWidth))
            layout(width, maxOf(titleBlock.height, actions.height, closer.height)) {
                titleBlock.placeRelative(0, 0)
                actions.placeRelative(width - closeRoom - actions.width, 0)
                closer.placeRelative(width - closer.width, 0)
            }
        } else {
            val titleBlock = measurables[0].measure(Constraints(maxWidth = (width - closeRoom).coerceAtLeast(0)))
            val actions = measurables[1].measure(Constraints(maxWidth = width))
            val top = maxOf(titleBlock.height, closer.height)
            val between = if (actions.height > 0) Tokens.GapS.roundToPx() else 0
            layout(width, top + between + actions.height) {
                titleBlock.placeRelative(0, 0)
                closer.placeRelative(width - closer.width, 0)
                actions.placeRelative(0, top + between)
            }
        }
    }
}

/** "Search apps": a filled pill as the mock, with Clear once something is typed. Search on the keyboard hides it. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Tokens.CornerFull)
    val container = lerp(scheme.surfaceContainerHigh, scheme.primaryContainer, FIELD_TINT)
    TextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier
            .fillMaxWidth()
            .then(if (focused) Modifier.border(2.dp, scheme.primary, shape) else Modifier),
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text("Search apps") },
        leadingIcon = { SymIcon(Sym.search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            { SymIconButton(icon = Sym.close, contentDescription = "Clear search", onClick = { onQuery("") }) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        singleLine = true,
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        interactionSource = interaction,
    )
}

@Composable
private fun LoadingLine(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadFailed(reason: String, onRetry: () -> Unit) {
    EmptyState(title = "Couldn't read your apps", icon = Sym.error, body = reason, actionLabel = "Try again", onAction = onRetry)
}

/** Writes to widget state that must finish even when the panel closes first. */
private val WriteScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private const val TAG = "PadStudioPanels"

/** Two sheets side by side from here up (SCREENS-BRIEF rule 6); the mock's widths (as weights) and gap below that. */
private val TWO_PANES = 840.dp
private val FOLDER_WIDTH = 576.dp
private val EDIT_WIDTH = 516.dp
private val PANE_GAP = 24.dp
private val FOLDER_WIDTH_ALONE = 600.dp
private val EDIT_WIDTH_ALONE = 560.dp
private val LIBRARY_WIDTH = 880.dp
private val SCRIM_GUTTER = 24.dp
private val MIN_TITLE_WIDTH = 160.dp

/** Tiles: padding, the gap between them, and the room the grid symbol, its gap and the check take beside a name. */
private val TILE_PADDING = 12.dp
private val TILE_GAP = 8.dp
private val GRID_TILE_SYMBOLS = 20.dp + 8.dp + 8.dp + 18.dp
private const val MAX_TILE_COLUMNS = 3

/** The sheet's tint toward the primary container, and how see-through it is with and without blur behind. */
private const val SHEET_TINT = 0.14f
private const val FIELD_TINT = 0.12f
private const val FROSTED_ALPHA = 0.88f
private const val DIMMED_ALPHA = 0.97f
private const val HAIRLINE_ALPHA = 0.09f
private const val COUNT_ALPHA = 0.75f
private const val MINI_ALPHA = 0.8f
private const val DISABLED_ALPHA = 0.38f

/** The widget's label size (GroupWidget draws its labels at 11 sp). */
private const val WIDGET_LABEL_SP = 11f

// --- Grid picker logic: plain functions, no Android (tested in GridPickerTest) -------------------------

/** A widget's size in dp for the current orientation; [partial] when the launcher left one of the two numbers out. */
internal data class WidgetSizeDp(val widthDp: Int, val heightDp: Int, val landscape: Boolean, val partial: Boolean = false)

/** A per-widget grid as GroupWidget.GRID stores it (plan §6b): "default", "auto" or columns x rows. */
internal sealed interface GridChoice {
    /** The value written under GroupWidget.GRID. */
    val stored: String

    /** Follows StudioConfig.widgetGrid (Home Kit's default), which is Auto when unset. */
    data object Default : GridChoice {
        override val stored: String get() = GridSpec.DEFAULT
    }

    /** Today's widget: as many cells as the density allows. */
    data object Auto : GridChoice {
        override val stored: String get() = GridSpec.AUTO
    }

    data class Exact(val spec: GridSpec) : GridChoice {
        override val stored: String get() = spec.toString()
    }
}

/** A saved GroupWidget.GRID value read back: [invalid] when it couldn't be read, so it shows as Default. */
internal data class SavedGrid(val choice: GridChoice, val invalid: Boolean)

/** A grid in cells, for drawing; unlike [GridSpec] any size is allowed (Auto can be 3 × 1). */
internal data class Cells(val columns: Int, val rows: Int)

internal enum class GridOptionKind { DEFAULT, AUTO, PRESET, CUSTOM }

/**
 * One tile of the grid picker. [detail] is the line under the [title]; [description] is what TalkBack
 * reads. A grid that doesn't fit this widget has a [reason] from GroupPlanner.layoutFor (and [warning]);
 * it is [enabled] only while it is the saved choice, so the saved grid never looks unselected.
 */
internal data class GridOption(
    val kind: GridOptionKind,
    val title: String,
    val detail: String,
    val description: String,
    val enabled: Boolean = true,
    val warning: Boolean = false,
    val reason: String? = null,
    /** How the reason is introduced when several grids don't fit: "10 × 5", "Default (10 × 5)". */
    val reasonLabel: String = title,
    val preset: GridSpec? = null,
    val mini: Cells? = null,
)

/** One count in the custom grid's columns or rows. */
internal data class CustomPick(val value: Int, val enabled: Boolean)

/** The custom grid editor for [spec]: which counts fit, the verdict for [spec], and why the rest don't fit. */
internal data class CustomGrid(
    val spec: GridSpec,
    val columns: List<CustomPick>,
    val rows: List<CustomPick>,
    val verdict: String,
    val warning: Boolean,
    val reasons: String?,
) {
    /** [spec] with [columns], its rows brought within what fits. */
    fun withColumns(columns: Int): GridSpec = GridSpec(columns, spec.rows.coerceAtMost(rows.lastFitting() ?: spec.rows))

    /** [spec] with [rows], its columns brought within what fits. */
    fun withRows(rows: Int): GridSpec = GridSpec(spec.columns.coerceAtMost(columns.lastFitting() ?: spec.columns), rows)

    private fun List<CustomPick>.lastFitting(): Int? = filter { it.enabled }.maxOfOrNull { it.value }
}

/**
 * Edit widget's grid picker (plan §6b): Default (showing what it resolves to), Auto, the presets 6 × 3,
 * 8 × 4 and 10 × 5, and Custom (4–10 × 2–8). Whether a grid fits, and why not, always comes from
 * [GroupPlanner.layoutFor] for the widget's real size, never from the mock's example numbers.
 */
internal object GridPicker {
    /** Closes the reasons under the tiles (the mock's wording). */
    const val RESIZE = "Resize it first."

    /** Closes the custom grid's reasons: smaller counts still fit. */
    const val MORE_ROOM = "Resize the widget for more."

    /**
     * A saved GroupWidget.GRID value as a choice, the way GroupWidget resolves it ([GridSpec.resolve]):
     * missing or "default" is Default, "auto" (any case) is Auto, "8x4" is that grid. Anything else is
     * [SavedGrid.invalid] and shows as Default.
     */
    fun read(saved: String?): SavedGrid {
        if (saved == null || saved.equals(GridSpec.DEFAULT, ignoreCase = true)) return SavedGrid(GridChoice.Default, invalid = false)
        if (saved.equals(GridSpec.AUTO, ignoreCase = true)) return SavedGrid(GridChoice.Auto, invalid = false)
        return GridSpec.parse(saved)?.let { SavedGrid(GridChoice.Exact(it), invalid = false) } ?: SavedGrid(GridChoice.Default, invalid = true)
    }

    /** The grid [choice] gives with StudioConfig.widgetGrid [defaultGrid]; null is Auto. */
    fun effective(choice: GridChoice, defaultGrid: String?): GridSpec? = when (choice) {
        GridChoice.Default -> GridSpec.resolve(null, defaultGrid)
        GridChoice.Auto -> null
        is GridChoice.Exact -> choice.spec
    }

    /** True for a saved exact grid that isn't one of the presets. */
    fun isCustom(choice: GridChoice): Boolean = choice is GridChoice.Exact && choice.spec !in GridSpec.PRESETS

    /**
     * The widget's size for the current orientation from AppWidgetManager's options: portrait is the
     * minimum width × the maximum height, landscape the maximum width × the minimum height. If the
     * launcher left one of them out (0), the other bound stands in and the size is [WidgetSizeDp.partial];
     * null when it reported no width or no height at all.
     */
    fun sizeFor(minWidth: Int, maxWidth: Int, minHeight: Int, maxHeight: Int, landscape: Boolean): WidgetSizeDp? {
        val width = if (landscape) maxWidth else minWidth
        val height = if (landscape) minHeight else maxHeight
        val otherWidth = if (landscape) minWidth else maxWidth
        val otherHeight = if (landscape) maxHeight else minHeight
        val w = width.takeIf { it > 0 } ?: otherWidth.takeIf { it > 0 } ?: return null
        val h = height.takeIf { it > 0 } ?: otherHeight.takeIf { it > 0 } ?: return null
        return WidgetSizeDp(w, h, landscape, partial = width <= 0 || height <= 0)
    }

    /** "This widget: 412 × 300 dp". */
    fun sizeText(size: WidgetSizeDp?): String =
        if (size == null) "This widget's size isn't known yet" else "This widget: ${size.widthDp} × ${size.heightDp} dp"

    /** Which of the launcher's numbers the size is (plan §6b: say so). */
    fun sizeSource(size: WidgetSizeDp?): String? {
        if (size == null) return null
        val which = if (size.landscape) "Landscape size" else "Portrait size"
        return if (size.partial) "$which, partly reported by the launcher" else "$which, as the launcher reports it"
    }

    /** The picker's six tiles for a widget of [size] (null: not reported) at [density], with [saved] as the current choice. */
    fun options(size: WidgetSizeDp?, density: Density, defaultGrid: String?, labelLineDp: Float, saved: GridChoice): List<GridOption> {
        val savedSpec = (saved as? GridChoice.Exact)?.spec
        return buildList {
            add(defaultOption(size, density, defaultGrid, labelLineDp))
            add(autoOption(size, density, labelLineDp))
            GridSpec.PRESETS.forEach { add(presetOption(it, size, density, labelLineDp, saved = it == savedSpec)) }
            add(customOption(savedSpec?.takeIf { it !in GridSpec.PRESETS }, size, density, labelLineDp))
        }
    }

    /** Whether [option] shows as chosen: [customOpen] keeps Custom chosen while its grid is being edited. */
    fun isSelected(option: GridOption, saved: GridChoice, customOpen: Boolean): Boolean = when (option.kind) {
        GridOptionKind.CUSTOM -> customOpen || isCustom(saved)
        GridOptionKind.DEFAULT -> !customOpen && saved == GridChoice.Default
        GridOptionKind.AUTO -> !customOpen && saved == GridChoice.Auto
        GridOptionKind.PRESET -> !customOpen && saved is GridChoice.Exact && saved.spec == option.preset
    }

    /** What tapping [option] saves; null for Custom, which opens the custom grid instead. */
    fun choiceFor(option: GridOption): GridChoice? = when (option.kind) {
        GridOptionKind.DEFAULT -> GridChoice.Default
        GridOptionKind.AUTO -> GridChoice.Auto
        GridOptionKind.PRESET -> option.preset?.let { GridChoice.Exact(it) }
        GridOptionKind.CUSTOM -> null
    }

    /** The reasons under the tiles: one grid's reason as the mock words it, or each grid's, named, then "Resize it first." */
    fun reasonText(options: List<GridOption>): String? {
        val refused = options.filter { it.reason != null }
        return when (refused.size) {
            0 -> null
            1 -> "${refused.single().reason} $RESIZE"
            else -> refused.joinToString(separator = "\n", postfix = "\n$RESIZE") { "${it.reasonLabel}: ${it.reason}" }
        }
    }

    /**
     * Where Custom starts: the grid the widget shows now ([saved] as the widget resolves it, or Auto's
     * layout), brought within 4–10 × 2–8 and within what fits this widget.
     */
    fun initialCustom(saved: GridChoice, size: WidgetSizeDp?, density: Density, defaultGrid: String?, labelLineDp: Float): GridSpec {
        val first = GridSpec.PRESETS.first()
        val shown = effective(saved, defaultGrid)?.let { Cells(it.columns, it.rows) }
            ?: size?.let { layout(it, density, null, labelLineDp).cells() }
            ?: Cells(first.columns, first.rows)
        val largest = size?.let { GroupPlanner.largestFit(it.widthDp.toFloat(), it.heightDp.toFloat()) }
        val maxColumns = if (size == null) GridSpec.MAX_COLUMNS else largest?.columns ?: GridSpec.MIN_COLUMNS
        val maxRows = if (size == null) GridSpec.MAX_ROWS else largest?.rows ?: GridSpec.MIN_ROWS
        return GridSpec(shown.columns.coerceIn(GridSpec.MIN_COLUMNS, maxColumns), shown.rows.coerceIn(GridSpec.MIN_ROWS, maxRows))
    }

    /** The custom grid editor for [spec] on a widget of [size]. */
    fun custom(spec: GridSpec, size: WidgetSizeDp?, density: Density, labelLineDp: Float): CustomGrid {
        if (size == null) {
            return CustomGrid(
                spec = spec,
                columns = GridSpec.COLUMNS.map { CustomPick(it, enabled = true) },
                rows = GridSpec.ROWS.map { CustomPick(it, enabled = true) },
                verdict = "${spec.label} · fit not known yet",
                warning = false,
                reasons = null,
            )
        }
        // layoutFor keeps a count that fits and trims one that doesn't, so each dimension is judged on its own.
        val columnsFit = GridSpec.COLUMNS.filter { c -> layout(size, density, GridSpec(c, GridSpec.MIN_ROWS), labelLineDp).columns == c }
        val rowsFit = GridSpec.ROWS.filter { r -> layout(size, density, GridSpec(GridSpec.MIN_COLUMNS, r), labelLineDp).rows == r }
        val firstWideMiss = GridSpec.COLUMNS.firstOrNull { it !in columnsFit }
        val firstTallMiss = GridSpec.ROWS.firstOrNull { it !in rowsFit }
        val reasons = listOfNotNull(
            firstWideMiss?.let { layout(size, density, GridSpec(it, rowsFit.firstOrNull() ?: GridSpec.MIN_ROWS), labelLineDp).reason },
            firstTallMiss?.let { layout(size, density, GridSpec(columnsFit.firstOrNull() ?: GridSpec.MIN_COLUMNS, it), labelLineDp).reason },
        ).distinct()
        val current = layout(size, density, spec, labelLineDp)
        return CustomGrid(
            spec = spec,
            columns = GridSpec.COLUMNS.map { CustomPick(it, enabled = it in columnsFit) },
            rows = GridSpec.ROWS.map { CustomPick(it, enabled = it in rowsFit) },
            verdict = if (current.fits) "${spec.label} · ${fitWords(current)}" else current.note ?: spec.label,
            warning = !current.fits,
            reasons = if (reasons.isEmpty()) null else reasons.joinToString(" ") + " $MORE_ROOM",
        )
    }

    private fun defaultOption(size: WidgetSizeDp?, density: Density, defaultGrid: String?, labelLineDp: Float): GridOption {
        val resolved = GridSpec.resolve(null, defaultGrid)
        val name = resolved?.label ?: "Auto"
        val reasonLabel = "Default ($name)"
        if (size == null) return GridOption(GridOptionKind.DEFAULT, "Default", name, "Default · $name", reasonLabel = reasonLabel)
        val layout = layout(size, density, resolved, labelLineDp)
        return when {
            resolved == null -> {
                val here = "${layout.columns} × ${layout.rows} here"
                GridOption(GridOptionKind.DEFAULT, "Default", "Auto · $here", "Default · Auto, $here", reasonLabel = reasonLabel)
            }
            layout.fits -> GridOption(
                GridOptionKind.DEFAULT,
                "Default",
                "$name · ${fitWords(layout)}",
                "Default · $name, ${fitWords(layout)}",
                reasonLabel = reasonLabel,
            )
            else -> {
                val note = layout.note ?: "$name doesn't fit"
                GridOption(
                    GridOptionKind.DEFAULT,
                    "Default",
                    note,
                    "Default · $name, $note",
                    warning = true,
                    reason = layout.reason,
                    reasonLabel = reasonLabel,
                )
            }
        }
    }

    private fun autoOption(size: WidgetSizeDp?, density: Density, labelLineDp: Float): GridOption {
        if (size == null) return GridOption(GridOptionKind.AUTO, "Auto", "Set by density", "Auto, set by density")
        val layout = layout(size, density, null, labelLineDp)
        val here = "${layout.columns} × ${layout.rows} here"
        return GridOption(GridOptionKind.AUTO, "Auto", here, "Auto, $here", mini = layout.cells())
    }

    private fun presetOption(spec: GridSpec, size: WidgetSizeDp?, density: Density, labelLineDp: Float, saved: Boolean): GridOption {
        val mini = Cells(spec.columns, spec.rows)
        if (size == null) return GridOption(GridOptionKind.PRESET, spec.label, "Fit not known yet", "${spec.label}, fit not known yet", preset = spec, mini = mini)
        val layout = layout(size, density, spec, labelLineDp)
        if (layout.fits) {
            val words = fitWords(layout)
            return GridOption(GridOptionKind.PRESET, spec.label, words.replaceFirstChar { it.uppercase() }, "${spec.label}, $words", preset = spec, mini = mini)
        }
        // The saved grid stays choosable and says what the widget shows instead; any other grid is refused.
        val detail = if (saved) layout.note ?: "Doesn't fit" else "Doesn't fit"
        return GridOption(
            GridOptionKind.PRESET,
            spec.label,
            detail,
            "${spec.label}, ${detail.replaceFirstChar { it.lowercase() }}",
            enabled = saved,
            warning = true,
            reason = layout.reason,
            preset = spec,
            mini = mini,
        )
    }

    private fun customOption(savedCustom: GridSpec?, size: WidgetSizeDp?, density: Density, labelLineDp: Float): GridOption {
        if (savedCustom != null) {
            val label = savedCustom.label
            if (size == null) return GridOption(GridOptionKind.CUSTOM, "Custom", label, "Custom, $label")
            val layout = layout(size, density, savedCustom, labelLineDp)
            if (layout.fits) {
                val detail = "$label · ${fitWords(layout)}"
                return GridOption(GridOptionKind.CUSTOM, "Custom", detail, "Custom, $detail")
            }
            val note = layout.note ?: "$label doesn't fit"
            return GridOption(
                GridOptionKind.CUSTOM,
                "Custom",
                note,
                "Custom, $note",
                warning = true,
                reason = layout.reason,
                reasonLabel = "Custom ($label)",
            )
        }
        if (size == null) {
            val range = "Columns ${GridSpec.MIN_COLUMNS}–${GridSpec.MAX_COLUMNS}, rows ${GridSpec.MIN_ROWS}–${GridSpec.MAX_ROWS}"
            return GridOption(GridOptionKind.CUSTOM, "Custom", range, "Custom, $range")
        }
        val largest = GroupPlanner.largestFit(size.widthDp.toFloat(), size.heightDp.toFloat())
        if (largest != null) return GridOption(GridOptionKind.CUSTOM, "Custom", "Up to ${largest.label}", "Custom, up to ${largest.label}")
        val smallest = layout(size, density, GridSpec(GridSpec.MIN_COLUMNS, GridSpec.MIN_ROWS), labelLineDp)
        return GridOption(
            GridOptionKind.CUSTOM,
            "Custom",
            "Doesn't fit",
            "Custom, doesn't fit",
            enabled = false,
            warning = true,
            reason = smallest.reason,
        )
    }

    private fun fitWords(layout: WidgetLayout): String = if (layout.showLabels) "fits, with names" else "fits, no names"

    private fun layout(size: WidgetSizeDp, density: Density, spec: GridSpec?, labelLineDp: Float): WidgetLayout =
        GroupPlanner.layoutFor(size.widthDp.toFloat(), size.heightDp.toFloat(), density, spec, labelLineDp)

    private fun WidgetLayout.cells() = Cells(columns, rows)
}

/** The panels' plain words that depend on data (tested in GridPickerTest). */
internal object PanelText {
    fun appCount(count: Int): String = if (count == 1) "1 app" else "$count apps"

    /** Edit widget's "Showing More · 5 apps" for the saved GroupWidget.GROUP. */
    fun groupMeta(saved: String?, groups: List<PlannedGroup>?): String {
        val group = AppGroup.entries.firstOrNull { it.name == saved } ?: return "No group chosen yet"
        if (groups == null) return "Showing ${group.title}"
        val planned = groups.firstOrNull { it.group == group } ?: return "Showing ${group.title} · no apps right now"
        return "Showing ${group.title} · ${appCount(planned.apps.size)}"
    }

    /** The Library's subtitle: "142 apps in 12 groups". */
    fun librarySubtitle(groups: List<PlannedGroup>): String {
        val groupCount = if (groups.size == 1) "1 group" else "${groups.size} groups"
        return "${appCount(groups.sumOf { it.apps.size })} in $groupCount"
    }
}
