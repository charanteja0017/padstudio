package com.charan.padstudio.today

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.TypedValue
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charan.padstudio.MainViewModel
import com.charan.padstudio.Tab
import com.charan.padstudio.core.home.GridSpec
import com.charan.padstudio.core.pen.HomeScreenFacts
import com.charan.padstudio.core.pen.PenKind
import com.charan.padstudio.data.IconStyle
import com.charan.padstudio.home.LibraryActivity
import com.charan.padstudio.pen.KeyMapperBridge
import com.charan.padstudio.pen.PenTestActivity
import com.charan.padstudio.studio
import com.charan.padstudio.ui.LocalStudioSnackbar
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.CardColumns
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.ScreenScaffold
import com.charan.padstudio.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import android.text.format.DateFormat as AndroidDateFormat

/**
 * Today, the start screen (plan §10 "Today", the owner's reference design): the pen hero with its status in
 * PenStatus's words (§7) and the quick actions, then Pen by app, Home screen, Make it yours and Health. Two
 * columns on a wide window, one below 840 dp or with very large text.
 *
 * Every action calls existing code: Test pen and Open library start their activities, Pause/Resume pen is
 * `setPenEnabled`, Send is `applyToKeyMapper` then [onStart]. Cards open their tab with [onOpenTab]. [onStart]
 * refreshes the checks when nothing can handle an intent. Internal because [Tab] is.
 */
@Composable
internal fun TodayScreen(
    model: MainViewModel,
    onStart: (Intent) -> Unit,
    onOpenTab: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val config by model.config.collectAsStateWithLifecycle()
    val plan by model.plan.collectAsStateWithLifecycle()
    val snapshot by model.snapshot.collectAsStateWithLifecycle()
    val health by model.health.collectAsStateWithLifecycle()

    // The checks are the view model's last refresh (on resume). Another tab can change what they read without a
    // resume (a binding on Pen, the wallpaper on Looks), which would leave Health contradicting the hero, so they
    // are refreshed when Today opens, as Setup does. Not while there are none yet: that first refresh is running.
    LaunchedEffect(model) {
        if (model.health.value.isNotEmpty()) model.refresh()
    }

    // Re-read on every resume, as the shell's status line does: Settings or Key Mapper may have changed them.
    val resumes = rememberResumeCount()
    val listening = remember(resumes) { KeyMapperBridge.isAccessibilityEnabled(context) }
    val animationsOn = remember(resumes) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val formatTime = rememberTimeFormatter(resumes)

    val status = todayPenStatus(listening, config, plan?.hash)
    val bindings = currentBindings(config)
    val penKind = config.penKind
    val gestures = remember(penKind, bindings) { heroGestures(penKind, bindings) }
    val heroCopy = heroText(status, penName(penKind), config.penMode, controlsSet(penKind, plan?.controls?.keys.orEmpty()))
    val contexts = plan?.contexts
    val lines = remember(contexts, bindings) { contexts?.let { penByApp(it, bindings) } }
    val appsByPackage = remember(snapshot) { snapshot?.apps.orEmpty().groupBy { it.facts.packageName } }

    val facts by produceState<HomeScreenFacts?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { context.studio.pen.factsFile()?.homeScreen }.getOrNull() }
    }
    val wallpaperColors by produceState<List<Color>?>(null, resumes) {
        value = withContext(Dispatchers.IO) { readWallpaperColors(context) }
    }
    val iconStyle = config.iconStyle
    val savedPack = config.iconPack
    val pack by produceState(PackLookup(checked = false, label = null), iconStyle, savedPack, resumes) {
        value = if (iconStyle == IconStyle.ICON_PACK && savedPack != null) {
            PackLookup(true, withContext(Dispatchers.IO) { runCatching { model.iconPacks().firstOrNull { it.packageName == savedPack }?.label }.getOrNull() })
        } else {
            PackLookup(true, null)
        }
    }
    // One 11 sp label line in dp, with Android's own non-linear font scale (plan §6b), from the resources the
    // composition observes.
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val labelLineDp = remember(resources, configuration) {
        val metrics = resources.displayMetrics
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, metrics) / metrics.density
    }
    val preview = remember(config.widgetGrid, config.density, labelLineDp) {
        previewWidget(GridSpec.parse(config.widgetGrid), config.density, labelLineDp)
    }
    val locale = LocalLocale.current.platformLocale
    val launcherGrid = facts?.let { recorded ->
        launcherGridText(recorded, recordedDayText(recorded.recordedAt, LocalDate.now()) { date, sameYear -> formatDay(date, sameYear, locale) })
    }

    // Quick actions: the outcome line under the hero's buttons; a send's success goes to the shell's snackbar.
    var note by remember { mutableStateOf<HeroNote?>(null) }
    var sends by remember { mutableIntStateOf(0) }
    val snackbar = LocalStudioSnackbar.current

    fun send() {
        val intent = model.applyToKeyMapper()
        if (intent == null) {
            val current = model.plan.value
            note = sendFailure(
                keyMapperInstalled = KeyMapperBridge.isInstalled(context),
                profileReady = current != null,
                hasKeyMaps = current?.generated?.keyMaps?.isNotEmpty() == true,
            )
        } else {
            note = HeroNote.Pending(OPENING_KEY_MAPPER)
            sends++
            onStart(intent)
        }
    }

    fun togglePen() {
        val requested = !model.config.value.penEnabled
        model.setPenEnabled(requested)
        note = penRequestNote(
            requestedOn = requested,
            savedOn = model.config.value.penEnabled,
            keyMapperInstalled = KeyMapperBridge.isInstalled(context),
            time = formatTime(System.currentTimeMillis()),
        )
    }

    val actions = HeroActions(
        onTestPen = { onStart(Intent(context, PenTestActivity::class.java)) },
        onTogglePen = ::togglePen,
        onEditControls = { onOpenTab(Tab.PEN) },
        onSend = ::send,
        onFixKeyMapper = { onStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        onRemedy = { failed ->
            when (failed.remedy) {
                Remedy.RETRY -> if (failed.action == HeroAction.SEND) send() else togglePen()
                Remedy.OPEN_SETUP -> onOpenTab(Tab.SETUP)
                Remedy.OPEN_PEN -> onOpenTab(Tab.PEN)
            }
        },
    )

    // "Opening Key Mapper…" stays until you come back from it; then the shell's snackbar says when it was sent
    // (one host for every screen, so messages queue instead of overlapping). Without that host, it says so here.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestStatus by rememberUpdatedState(status)
    val latestFormat by rememberUpdatedState(formatTime)
    LaunchedEffect(sends) {
        if (sends == 0) return@LaunchedEffect
        delay(PENDING_MIN_MS)
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        val sent = latestStatus.sentText(latestFormat)
        if (snackbar != null) {
            if (note is HeroNote.Pending) note = null
            snackbar.showSnackbar(sent)
        } else if (note is HeroNote.Pending) {
            note = HeroNote.Done(sent)
        }
    }

    ScreenScaffold(
        title = "Today",
        modifier = modifier,
        subtitle = "Your tablet, set up your way.",
        actions = {
            PillButton(
                text = "Open library",
                onClick = { onStart(Intent(context, LibraryActivity::class.java)) },
                style = PillStyle.Outlined,
                icon = Sym.library,
            )
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val bodyEm = with(LocalDensity.current) { MaterialTheme.typography.bodyLarge.fontSize.toDp().value }
            val twoColumns = useTwoColumns(maxWidth.value, bodyEm)
            val heroWidth = if (twoColumns) maxWidth - Tokens.GapM - END_COLUMN_DP.dp else maxWidth
            CardColumns(
                endWidth = END_COLUMN_DP.dp,
                stackBelow = if (twoColumns) 0.dp else STACK_ALWAYS,
                start = {
                    TodayHero(
                        status = status,
                        text = heroCopy,
                        gestures = gestures,
                        calloutsPossible = penKind == PenKind.FOCUS_PEN_PRO && gestures.all { it.marker != null },
                        sentLine = status.sentText(formatTime),
                        penEnabled = config.penEnabled,
                        note = note,
                        animationsOn = animationsOn,
                        heroWidth = heroWidth,
                        actions = actions,
                    )
                    PenByAppCard(
                        lines = lines,
                        apps = { packages -> packages.mapNotNull { appsByPackage[it]?.firstOrNull() } },
                        onOpen = { onOpenTab(Tab.PEN) },
                    )
                },
                end = {
                    HomeScreenCard(
                        snapshot = snapshot,
                        preview = preview,
                        defaultGrid = defaultGridText(config.widgetGrid),
                        launcherGrid = launcherGrid,
                        wallpaperColors = wallpaperColors,
                        onOpen = { onOpenTab(Tab.HOME) },
                    )
                    MakeItYoursCard(
                        iconLine = iconStyleText(iconStyle, savedPack, pack.label, pack.checked),
                        wallpaperLine = wallpaperText(config.wallpaperApplied),
                        coloursLine = coloursText(config.matchWallpaperColours),
                        wallpaperColors = wallpaperColors,
                        onOpen = { onOpenTab(Tab.LOOKS) },
                    )
                    HealthCard(
                        summary = healthSummary(health),
                        issueText = { check -> healthIssueText(check, status, formatTime) },
                        onFix = onStart,
                        onOpenSetup = { onOpenTab(Tab.SETUP) },
                    )
                },
            )
        }
    }
}

/** The saved icon pack's name, once the installed packs have been looked through. */
private data class PackLookup(val checked: Boolean, val label: String?)

/** Counts ON_RESUME, so values read from the system are read again when you come back. */
@Composable
private fun rememberResumeCount(): Int {
    var count by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        count++
        onPauseOrDispose { }
    }
    return count
}

/** "14:05" in the tablet's 12/24-hour setting, with the day before it when it wasn't today. */
@Composable
private fun rememberTimeFormatter(resumes: Int): (Long) -> String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = LocalLocale.current.platformLocale
    return remember(context, configuration, locale, resumes) {
        val time = AndroidDateFormat.getTimeFormat(context)
        val format: (Long) -> String = { at ->
            sentTimeText(
                at = at,
                now = System.currentTimeMillis(),
                zone = ZoneId.systemDefault(),
                time = { time.format(Date(it)) },
                day = { ms, sameYear -> SimpleDateFormat(dayPattern(locale, sameYear), locale).format(Date(ms)) },
            )
        }
        format
    }
}

private fun dayPattern(locale: Locale, sameYear: Boolean): String =
    AndroidDateFormat.getBestDateTimePattern(locale, if (sameYear) "dMMM" else "dMMMy")

private fun formatDay(date: LocalDate, sameYear: Boolean, locale: Locale): String =
    DateTimeFormatter.ofPattern(dayPattern(locale, sameYear), locale).format(date)

/** The home wallpaper's main colours (no permission needed); the picture itself can't be read by apps. */
private fun readWallpaperColors(context: Context): List<Color>? = runCatching {
    val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return@runCatching null
    listOfNotNull(colors.primaryColor, colors.secondaryColor, colors.tertiaryColor).map { Color(it.toArgb()) }
}.getOrNull()

/** At least this long on "Opening Key Mapper…", so it doesn't flash. */
private const val PENDING_MIN_MS = 600L

/** A stackBelow no window reaches: CardColumns then always stacks. */
private val STACK_ALWAYS = 100_000.dp
