package com.charan.padstudio.pen

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charan.padstudio.MainViewModel
import com.charan.padstudio.core.pen.Binding
import com.charan.padstudio.core.pen.PenControl
import com.charan.padstudio.core.pen.PenIntent
import com.charan.padstudio.core.pen.PenKind
import com.charan.padstudio.core.pen.PenMode
import com.charan.padstudio.core.pen.PenStatus
import com.charan.padstudio.core.pen.Press
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.pen.screen.ActionNote
import com.charan.padstudio.pen.screen.ActionPicker
import com.charan.padstudio.pen.screen.AdvancedSection
import com.charan.padstudio.pen.screen.BindingsMatrix
import com.charan.padstudio.pen.screen.ChipFlow
import com.charan.padstudio.pen.screen.ContextItem
import com.charan.padstudio.pen.screen.ContextList
import com.charan.padstudio.pen.screen.ControlDetail
import com.charan.padstudio.pen.screen.DeveloperDetails
import com.charan.padstudio.pen.screen.DeveloperFacts
import com.charan.padstudio.pen.screen.HeroCard
import com.charan.padstudio.pen.screen.MatrixCell
import com.charan.padstudio.pen.screen.MatrixRow
import com.charan.padstudio.pen.screen.PenChoice
import com.charan.padstudio.pen.screen.PenConfirm
import com.charan.padstudio.pen.screen.PenNotice
import com.charan.padstudio.pen.screen.PenStage
import com.charan.padstudio.pen.screen.PenUiState
import com.charan.padstudio.pen.screen.PickerTarget
import com.charan.padstudio.pen.screen.RecordNotice
import com.charan.padstudio.pen.screen.RecordingCard
import com.charan.padstudio.pen.screen.SendBar
import com.charan.padstudio.pen.screen.StageChip
import com.charan.padstudio.pen.screen.bindingLabel
import com.charan.padstudio.pen.screen.boundIntent
import com.charan.padstudio.pen.screen.clipFor
import com.charan.padstudio.pen.screen.confirmWords
import com.charan.padstudio.pen.screen.contextLabels
import com.charan.padstudio.pen.screen.controlsFor
import com.charan.padstudio.pen.screen.controlsKnown
import com.charan.padstudio.pen.screen.controlsSetLine
import com.charan.padstudio.pen.screen.controlsWithActions
import com.charan.padstudio.pen.screen.describeDevices
import com.charan.padstudio.pen.screen.groupOf
import com.charan.padstudio.pen.screen.isDark
import com.charan.padstudio.pen.screen.markerFor
import com.charan.padstudio.pen.screen.penBackdrop
import com.charan.padstudio.pen.screen.penName
import com.charan.padstudio.pen.screen.penWarnings
import com.charan.padstudio.pen.screen.penWideWarnings
import com.charan.padstudio.pen.screen.pressLabel
import com.charan.padstudio.pen.screen.pressesFor
import com.charan.padstudio.pen.screen.rememberPenUiState
import com.charan.padstudio.pen.screen.rememberRecordingTracker
import com.charan.padstudio.pen.screen.screenContexts
import com.charan.padstudio.pen.screen.sentTimeLabel
import com.charan.padstudio.pen.screen.switchLoses
import com.charan.padstudio.pen.screen.warningsFor
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.BindingChip
import com.charan.padstudio.ui.components.ConfirmDialog
import com.charan.padstudio.ui.components.EmptyState
import com.charan.padstudio.ui.components.ListRow
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.RowLeading
import com.charan.padstudio.ui.components.ScreenScaffold
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.SymIconButton
import com.charan.padstudio.ui.components.cardBleed
import com.charan.padstudio.ui.theme.Tokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Date

/**
 * The Pen tab (redesign plan §10 "Pen"; mock-up docs/redesign/mock-pen.html): which pen and setup, where the pen
 * works (contexts with their apps), what each control does there (the pen with callouts, or a 2 × 3 grid for a
 * two-button pen), an action picker, recording, and Send to Key Mapper. At 840 dp and wider it shows list–detail
 * panes with the picker as a side sheet; below that one column with the picker as a bottom sheet.
 *
 * Every button calls the same [MainViewModel] function with the same arguments as PadStudio 1's Pen screen did
 * (plan §4). Pen key events still reach the view model through MainActivity.dispatchKeyEvent; this screen only
 * starts and cancels captures, and cancels one when Back is pressed or the tab is left.
 */
@Composable
fun PenStudioScreen(model: MainViewModel, onStart: (Intent) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val config by model.config.collectAsStateWithLifecycle()
    val plan by model.plan.collectAsStateWithLifecycle()
    val capturing by model.capturing.collectAsStateWithLifecycle()
    val snapshot by model.snapshot.collectAsStateWithLifecycle()
    val keyMapper = rememberKeyMapper()
    val timeLabel = rememberTimeLabel()
    val ui = rememberPenUiState()
    val tracker = rememberRecordingTracker(model)
    val scope = rememberCoroutineScope()
    val status = PenStatus(keyMapper.listening, config.appliedProfileHash, plan?.hash, config.appliedAt, config.penEnabled)

    // Leaving the tab mid-recording cancels it, so Key Mapper isn't left paused; a rotation keeps it going
    // (the same rule as MainActivity.onStop).
    val activity = LocalActivity.current
    val currentTracker by rememberUpdatedState(tracker)
    DisposableEffect(Unit) {
        onDispose { if (activity?.isChangingConfigurations != true) currentTracker.cancel() }
    }

    // The pen's state.
    val kind = config.penKind
    val mode = config.penMode
    val controls = controlsFor(kind)
    val bindings = plan?.profile?.bindings.orEmpty()
    val triggers = plan?.controls.orEmpty()
    val appsByPackage = remember(snapshot) { snapshot?.apps.orEmpty().associateBy { it.facts.packageName } }
    val items = remember(plan, appsByPackage) {
        val current = plan ?: return@remember emptyList()
        val warnings = current.generated.warnings
        screenContexts(current.contexts).map { penContext ->
            ContextItem(
                context = penContext,
                labels = contextLabels(penContext),
                apps = penContext.packages.mapNotNull { appsByPackage[it] },
                warnings = penWarnings(warningsFor(penContext, warnings, current.profile.bindings)) { appsByPackage[it]?.facts?.label },
            )
        }
    }
    val selected = items.firstOrNull { it.context.id == ui.contextId } ?: items.firstOrNull()
    // Warnings about the pen itself (a refused or Expert-only trigger), which no context claims.
    val penWide = remember(plan, items, appsByPackage) {
        val current = plan ?: return@remember emptyList()
        penWarnings(penWideWarnings(items.map { it.context }, current.generated.warnings, current.profile.bindings)) { appsByPackage[it]?.facts?.label }
    }
    val selectedControl = controls.firstOrNull { it.id == ui.controlId } ?: controls.first()
    val target = ui.picker?.takeIf { t ->
        items.any { it.context.id == t.contextId && !it.context.passThrough } && controls.any { it.id == t.controlId }
    }

    // The pen animation: a callout plays its gesture; tapping the same one again replays it (a frame of idle in
    // between, so PenModelView sees the clip change).
    var gesture by remember { mutableStateOf(IDLE) }
    var resting by remember { mutableStateOf(false) }
    var replays by remember { mutableIntStateOf(0) }
    LaunchedEffect(replays) {
        if (replays == 0) return@LaunchedEffect
        resting = true
        withFrameNanos { }
        withFrameNanos { }
        resting = false
    }
    val clip = if (resting) IDLE else gesture

    // Where the last Send or Pause/Resume stands (plan §13).
    var note by remember { mutableStateOf<ActionNote?>(null) }
    var retry by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(note) {
        if (note is ActionNote.Pending || note is ActionNote.Requested) {
            delay(NOTE_SHOWN_MS)
            note = null
        }
    }
    LifecycleResumeEffect(Unit) {
        if (note is ActionNote.Pending) note = null
        onPauseOrDispose { }
    }

    fun openPicker(contextId: String, controlId: String, press: Press) {
        ui.picker = PickerTarget(contextId, controlId, press)
        ui.query = ""
        ui.group = boundIntent(bindings, contextId, controlId, press)?.let(::groupOf)
    }

    fun chooseControl(controlId: String, press: Press = Press.SHORT) {
        val again = ui.controlId == controlId
        ui.controlId = controlId
        gesture = clipFor(controlId)
        if (again) replays++
        val contextId = selected?.context?.id ?: return
        openPicker(contextId, controlId, press)
    }

    fun selectContext(contextId: String) {
        ui.contextId = contextId
        ui.needsOpen = false
        val open = ui.picker ?: return
        val passThrough = items.firstOrNull { it.context.id == contextId }?.context?.passThrough == true
        if (passThrough) ui.picker = null else openPicker(contextId, open.controlId, open.press)
    }

    fun send() {
        retry = ::send
        note = ActionNote.Pending("Opening Key Mapper…")
        val intent = model.applyToKeyMapper()
        if (intent == null) {
            // applyToKeyMapper says why in its message (Key Mapper missing, no key maps); without a profile it can't run.
            note = ActionNote.Failed(model.message.value ?: PROFILE_NOT_READY)
        } else {
            onStart(intent)
        }
    }

    fun setPen(enabled: Boolean) {
        retry = { setPen(enabled) }
        // The view model's own conditions for sending nothing (MainViewModel.setPenEnabled).
        val refused = !KeyMapperBridge.isInstalled(context) || plan?.generated?.groups.isNullOrEmpty()
        model.setPenEnabled(enabled)
        note = if (refused) ActionNote.Failed(model.message.value ?: PROFILE_NOT_READY) else ActionNote.Requested("Requested")
    }

    val ownActions = config.bindings != null
    fun requestKind(next: PenKind) {
        if (next == kind) return
        if (switchLoses(config.controls.size, ownActions)) ui.confirm = PenConfirm.encode(PenConfirm.Kind(next)) else model.setPenKind(next)
    }

    fun requestMode(next: PenMode) {
        if (next == mode) return
        if (switchLoses(config.controls.size, ownActions)) ui.confirm = PenConfirm.encode(PenConfirm.Mode(next)) else model.setPenMode(next)
    }

    fun record(controlId: String, fromSheet: Boolean) {
        // A bottom sheet is its own window: the pen's key would go to it instead of MainActivity, so it closes first.
        if (fromSheet) ui.picker = null
        tracker.start(controlId)
    }

    val advancedRequester = remember { BringIntoViewRequester() }
    val heroRequester = remember { BringIntoViewRequester() }
    val subtitle = status.configuredText(penName(kind), mode, controlsKnown(controls, triggers))
    val sentLine = status.sentText(timeLabel)
    val notesApps = remember(snapshot, plan, config.notesApp) { notesCandidates(snapshot?.apps.orEmpty(), plan, config.notesApp) }

    val pauseAction: @Composable () -> Unit = {
        PillButton(
            text = if (config.penEnabled) "Pause pen" else "Resume pen",
            onClick = { setPen(!config.penEnabled) },
            style = PillStyle.Outlined,
            icon = if (config.penEnabled) Sym.pause else Sym.play,
        )
    }

    val sendBar: @Composable (Modifier) -> Unit = { barModifier ->
        SendBar(
            status = status,
            sentLine = sentLine,
            canSend = plan != null,
            note = note,
            onRetry = { retry?.invoke() },
            onSend = ::send,
            onTestPen = { onStart(Intent(context, PenTestActivity::class.java)) },
            onFixKeyMapper = { onStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            modifier = barModifier,
        )
    }

    val penChoice: @Composable () -> Unit = {
        if (penWide.isNotEmpty()) PenNotice(penWide)
        PenChoice(
            kind = kind,
            mode = mode,
            onKind = ::requestKind,
            onSetupChip = {
                ui.advancedOpen = true
                scope.launch {
                    delay(ADVANCED_OPEN_MS)
                    advancedRequester.bringIntoView()
                }
            },
        )
    }

    val contextList: @Composable (Dp) -> Unit = { bleed ->
        if (selected == null) {
            EmptyState(title = subtitle, icon = Sym.pen)
        } else {
            ContextList(
                items = items,
                selectedId = selected.context.id,
                bleed = bleed,
                onSelect = { id ->
                    selectContext(id)
                    scope.launch { heroRequester.bringIntoView() }
                },
            )
        }
    }

    val advanced: @Composable (Modifier) -> Unit = { advancedModifier ->
        AdvancedSection(
            expanded = ui.advancedOpen,
            onExpandedChange = { ui.advancedOpen = it },
            mode = mode,
            onMode = ::requestMode,
            keyInjection = config.keyInjection,
            onKeyInjection = model::setKeyInjection,
            notesApps = notesApps,
            notesApp = config.notesApp,
            onNotesApp = model::setNotesApp,
            onResetActions = { ui.confirm = PenConfirm.encode(PenConfirm.Reset) },
            developer = {
                DeveloperDetails(
                    controls = controls.map { control ->
                        val recorded = config.controls[control.id]
                        ControlDetail(control, recorded, recorded ?: triggers[control.id])
                    },
                    mode = mode,
                    listening = capturing,
                    facts = DeveloperFacts(
                        devices = describeDevices(triggers.values),
                        profileLine = plan?.let {
                            "${it.generated.keyMaps.size} key maps in ${it.generated.groups.size} groups · profile ${it.hash}. " +
                                "Key Mapper asks how to restore: choose Replace."
                        },
                        sentHash = config.appliedProfileHash,
                        keyMapperLine = keyMapper.version?.let { "Version $it · ${status.keyMapperText}" }
                            ?: "Not installed. PadStudio writes the pen profile; Key Mapper runs it (tools/install.sh keymapper).",
                        penActions = status.penActionsText,
                        expertMode = status.expertModeText,
                        warnings = plan?.generated?.warnings.orEmpty(),
                    ),
                    onRecord = { record(it, fromSheet = false) },
                    onForget = { ui.confirm = PenConfirm.encode(PenConfirm.Forget(it)) },
                    onResendPenActions = { setPen(config.penEnabled) },
                )
            },
            modifier = advancedModifier.bringIntoViewRequester(advancedRequester),
        )
    }

    // The right pane's body for the chosen context. [fill]: take the card's remaining height (panes) or a fixed one.
    val heroBody: @Composable ColumnScope.(Boolean, Dp) -> Unit = { fill, stageHeight ->
        val item = selected
        val sized = if (fill) Modifier.weight(1f).fillMaxWidth() else Modifier.height(stageHeight).fillMaxWidth()
        when {
            item == null -> Unit
            item.context.passThrough -> PassThroughNote(item)
            kind == PenKind.FOCUS_PEN_PRO -> {
                val contextId = item.context.id
                val chips = controls.mapNotNull { control ->
                    val marker = markerFor(control.id) ?: return@mapNotNull null
                    val intent = boundIntent(bindings, contextId, control.id, Press.SHORT)
                    StageChip(control.id, control.label, intent?.label, intent?.let(Sym::forIntent), marker)
                }
                val dark = isDark(MaterialTheme.colorScheme.surface)
                Box(sized.penBackdrop(MaterialTheme.colorScheme.primaryContainer, dark)) {
                    PenStage(
                        chips = chips,
                        selectedControl = selectedControl.id,
                        clip = clip,
                        playing = !ui.animationPaused,
                        onChip = { chooseControl(it) },
                        modifier = Modifier.fillMaxSize(),
                        reserveBottomEnd = DpSize(PAUSE_CORNER, PAUSE_CORNER),
                    )
                    SymIconButton(
                        icon = if (ui.animationPaused) Sym.play else Sym.pause,
                        contentDescription = if (ui.animationPaused) "Play the pen animation" else "Pause the pen animation",
                        onClick = { ui.animationPaused = !ui.animationPaused },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                        style = PillStyle.Tonal,
                    )
                }
                MorePresses(controls, mode, bindings, contextId) { control, press -> chooseControl(control.id, press) }
            }
            else -> {
                val rows = controls.map { control ->
                    val available = pressesFor(control, mode)
                    val presses = if (control.singleGesture) listOf(Press.SHORT) else Press.entries
                    MatrixRow(
                        controlId = control.id,
                        control = control.label,
                        icon = Sym.forControl(control.id),
                        cells = presses.map { press ->
                            val intent = boundIntent(bindings, item.context.id, control.id, press)
                            MatrixCell(
                                press = press,
                                label = pressLabel(press),
                                action = intent?.label,
                                icon = intent?.let(Sym::forIntent),
                                unavailable = if (press in available) null else EXPERT_ONLY,
                            )
                        },
                    )
                }
                BindingsMatrix(
                    rows = rows,
                    selected = target?.takeIf { it.contextId == item.context.id }?.let { it.controlId to it.press },
                    onCell = { controlId, press ->
                        ui.controlId = controlId
                        openPicker(item.context.id, controlId, press)
                    },
                    // In the panes the grid scrolls inside the card; in one column the page scrolls instead.
                    modifier = if (fill) Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()) else Modifier,
                )
            }
        }
    }

    val hero: @Composable (Modifier, Boolean, Dp) -> Unit = { heroModifier, fill, stageHeight ->
        val item = selected
        if (item == null) {
            StudioCard(heroModifier) { EmptyState(title = subtitle, icon = Sym.pen) }
        } else {
            HeroCard(
                item = item,
                meta = if (item.context.passThrough) item.labels.supporting else controlsSetLine(controlsWithActions(bindings, item.context.id, controls), controls.size),
                needsOpen = ui.needsOpen,
                onNeedsOpen = { ui.needsOpen = it },
                modifier = heroModifier,
            ) { heroBody(fill, stageHeight) }
        }
    }

    val picker: @Composable (Modifier, Boolean) -> Unit = { pickerModifier, sheet ->
        val open = target
        if (open != null) {
            val control = PenControl.byId(open.controlId)
            val presses = pressesFor(control, mode)
            val press = open.press.takeIf { it in presses } ?: Press.SHORT
            val contextTitle = items.firstOrNull { it.context.id == open.contextId }?.labels?.title.orEmpty()
            val noTrigger = triggers[control.id] == null
            ActionPicker(
                control = control.label,
                contextTitle = contextTitle,
                presses = presses,
                press = press,
                onPress = { openPicker(open.contextId, open.controlId, it) },
                current = boundIntent(bindings, open.contextId, open.controlId, press),
                onPick = { intent: PenIntent? ->
                    model.setBinding(open.contextId, open.controlId, press, intent)
                    if (sheet) ui.picker = null
                },
                query = ui.query,
                onQuery = { ui.query = it },
                group = ui.group,
                onGroup = { ui.group = it },
                onClose = { ui.picker = null },
                notice = if (noTrigger) {
                    RecordNotice(
                        text = "${control.label} isn't recorded yet, so its actions don't run.",
                        enabled = capturing == null,
                        onRecord = { record(control.id, fromSheet = sheet) },
                    )
                } else {
                    null
                },
                modifier = pickerModifier,
            )
        }
    }

    val recordingCard: @Composable (Modifier) -> Unit = { cardModifier ->
        RecordingCard(tracker = tracker, capturing = capturing, label = { PenControl.byId(it) }, timeLabel = timeLabel, modifier = cardModifier)
    }

    BoxWithConstraints(modifier) {
        val wide = maxWidth >= PANES_MIN_WIDTH
        BackHandler(enabled = capturing != null || (wide && target != null)) {
            if (capturing != null) tracker.cancel() else ui.picker = null
        }
        if (wide) {
            val inner = maxWidth - Tokens.Gutter * 2
            val main = if (target != null) inner - SIDE_SHEET_WIDTH - PANE_GAP else inner
            val leftWidth = (main * LEFT_SHARE).coerceIn(LEFT_MIN, LEFT_MAX)
            ScreenScaffold(title = "Pen", subtitle = subtitle, scrollState = null, actions = { pauseAction() }) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(PANE_GAP)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(PANE_GAP)) {
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(PANE_GAP)) {
                            StudioCard(Modifier.width(leftWidth).fillMaxHeight(), contentPadding = PaddingValues(12.dp)) {
                                Column(
                                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    penChoice()
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    WhereLabel()
                                    contextList(LEFT_PANE_BLEED)
                                    advanced(Modifier)
                                }
                            }
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                hero(Modifier.fillMaxSize(), true, 0.dp)
                                recordingCard(Modifier.align(Alignment.BottomCenter).padding(12.dp).widthIn(max = RECORDING_MAX_WIDTH))
                            }
                        }
                        sendBar(Modifier.fillMaxWidth())
                    }
                    if (target != null) {
                        StudioCard(
                            modifier = Modifier.width(SIDE_SHEET_WIDTH).fillMaxHeight().semantics { paneTitle = "Choose an action" },
                            contentPadding = PaddingValues(top = 4.dp),
                            spacing = 0.dp,
                        ) {
                            picker(Modifier.weight(1f), false)
                        }
                    }
                }
            }
        } else {
            val stageHeight = (maxWidth * NARROW_STAGE_SHARE).coerceIn(NARROW_STAGE_MIN, NARROW_STAGE_MAX)
            Box(Modifier.fillMaxSize()) {
                ScreenScaffold(title = "Pen", subtitle = subtitle, actions = { pauseAction() }) {
                    sendBar(Modifier.fillMaxWidth())
                    StudioCard(Modifier.fillMaxWidth()) { penChoice() }
                    StudioCard(Modifier.fillMaxWidth()) {
                        WhereLabel()
                        contextList(CARD_BLEED)
                    }
                    hero(Modifier.fillMaxWidth().bringIntoViewRequester(heroRequester), false, stageHeight)
                    advanced(Modifier.fillMaxWidth())
                }
                recordingCard(Modifier.align(Alignment.BottomCenter).padding(16.dp).widthIn(max = RECORDING_MAX_WIDTH))
            }
            if (target != null) PickerBottomSheet(onDismiss = { ui.picker = null }) { picker(Modifier, true) }
        }
    }

    ConfirmPrompt(ui, model, config.controls.size, ownActions)
}

/** The narrow layout's picker: Material 3's modal bottom sheet, fully open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerBottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.semantics { paneTitle = "Choose an action" }) { content() }
    }
}

/** The confirmation the screen is waiting on, if any, and what its confirm button does. */
@Composable
private fun ConfirmPrompt(ui: PenUiState, model: MainViewModel, recorded: Int, ownActions: Boolean) {
    val confirm = PenConfirm.decode(ui.confirm) ?: return
    val forget = confirm as? PenConfirm.Forget
    val words = confirmWords(
        confirm = confirm,
        recordedControls = recorded,
        ownActions = ownActions,
        controlLabel = forget?.let { PenControl.byId(it.controlId).label }.orEmpty(),
    )
    ConfirmDialog(
        title = words.title,
        body = words.body,
        confirmLabel = words.confirm,
        onConfirm = {
            ui.confirm = null
            when (confirm) {
                is PenConfirm.Kind -> model.setPenKind(confirm.kind)
                is PenConfirm.Mode -> model.setPenMode(confirm.mode)
                PenConfirm.Reset -> model.resetBindings()
                is PenConfirm.Forget -> model.forgetControl(confirm.controlId)
            }
        },
        onDismiss = { ui.confirm = null },
        icon = if (confirm is PenConfirm.Reset) Sym.refresh else Sym.warning,
    )
}

/** The left pane's section label (the mock-up's "Where the pen works"). */
@Composable
private fun WhereLabel() {
    Text(text = "Where the pen works", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The presses beside each control's main one that have an action here (Squeeze double press · Open camera), so
 * nothing set is hidden behind a callout. Choosing one opens the picker on that press.
 */
@Composable
private fun MorePresses(controls: List<PenControl>, mode: PenMode, bindings: List<Binding>, contextId: String, onChoose: (PenControl, Press) -> Unit) {
    val extras = controls.filterNot { it.singleGesture }.flatMap { control ->
        pressesFor(control, mode).filter { it != Press.SHORT }.mapNotNull { press ->
            boundIntent(bindings, contextId, control.id, press)?.let { Triple(control, press, it) }
        }
    }
    if (extras.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "More presses", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipFlow {
            extras.forEach { (control, press, intent) ->
                BindingChip(
                    onClick = { onChoose(control, press) },
                    action = intent.label,
                    icon = Sym.forIntent(intent),
                    control = bindingLabel(control, press),
                )
            }
        }
    }
}

/** A pass-through context: its apps keep the pen's buttons, so it has no actions to choose. */
@Composable
private fun PassThroughNote(item: ContextItem) {
    Text(
        text = "These apps get the pen's own buttons, so PadStudio leaves the pen to them. Set them up in each app, " +
            "for example button 1 = Eraser and button 2 = Undo.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(Modifier.cardBleed()) {
        item.context.packages.forEach { packageName ->
            val app = item.apps.firstOrNull { it.facts.packageName == packageName }
            ListRow(
                headline = app?.facts?.label ?: packageName,
                leading = if (app != null) RowLeading.App(app) else RowLeading.Symbol(Sym.openNotes),
            )
        }
    }
}

/** Notes apps to offer, as PadStudio 1 chose them, plus the chosen one if it's installed but not among them. */
private fun notesCandidates(apps: List<InstalledApp>, plan: PenPlan?, chosen: String?): List<InstalledApp> {
    val candidates = apps.filter { app ->
        plan?.contexts.orEmpty().any { it.passThrough && app.facts.packageName in it.packages } ||
            app.facts.label.contains("note", ignoreCase = true)
    }.distinctBy { it.facts.packageName }
    val extra = apps.firstOrNull { it.facts.packageName == chosen }?.takeIf { app -> candidates.none { it.facts.packageName == app.facts.packageName } }
    return candidates + listOfNotNull(extra)
}

/** Key Mapper as this screen reads it, again on every resume: its accessibility service and version. */
@Immutable
private data class KeyMapperState(val listening: Boolean, val version: String?)

@Composable
private fun rememberKeyMapper(): KeyMapperState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readKeyMapper(context)) }
    LifecycleResumeEffect(context) {
        state = readKeyMapper(context)
        onPauseOrDispose { }
    }
    return state
}

private fun readKeyMapper(context: Context) = KeyMapperState(KeyMapperBridge.isAccessibilityEnabled(context), KeyMapperBridge.versionName(context))

/** "14:05" (the device's 12/24-hour setting), with the day ("11 Oct 14:05") when it wasn't today. */
@Composable
private fun rememberTimeLabel(): (Long) -> String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) {
        val time = DateFormat.getTimeFormat(context)
        val dayPattern = DateFormat.getBestDateTimePattern(configuration.locales[0], "d MMM")
        val label: (Long) -> String = { epochMs ->
            sentTimeLabel(epochMs, System.currentTimeMillis(), ZoneId.systemDefault(), { time.format(Date(it)) }, { DateFormat.format(dayPattern, it).toString() })
        }
        label
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private const val IDLE = "idle"
private const val EXPERT_ONLY = "Expert setup only"
private const val PROFILE_NOT_READY = "The pen profile isn't ready yet."

/** How long "Opening Key Mapper…" and "Requested" stay before the status line speaks for itself. */
private const val NOTE_SHOWN_MS = 4_000L

/** Lets the Advanced section start opening before it's scrolled into view. */
private const val ADVANCED_OPEN_MS = 150L

private val PANES_MIN_WIDTH = 840.dp

/** Context rows reach into the left pane's 12 dp padding (selection inset from the edge) and a card's 20 dp. */
private val LEFT_PANE_BLEED = 8.dp
private val CARD_BLEED = 16.dp
private val SIDE_SHEET_WIDTH = 300.dp
private val PANE_GAP = 12.dp
private const val LEFT_SHARE = 0.3f
private val LEFT_MIN = 240.dp
private val LEFT_MAX = 300.dp
private val PAUSE_CORNER = 56.dp
private val RECORDING_MAX_WIDTH = 420.dp
private const val NARROW_STAGE_SHARE = 0.8f
private val NARROW_STAGE_MIN = 320.dp
private val NARROW_STAGE_MAX = 480.dp
