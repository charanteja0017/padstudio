package com.charan.padstudio.setup

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charan.padstudio.HealthCheck
import com.charan.padstudio.MainViewModel
import com.charan.padstudio.core.pen.FactsFile
import com.charan.padstudio.core.pen.PenStatus
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.home.WidgetPinning
import com.charan.padstudio.icons.IconRepository
import com.charan.padstudio.pen.KeyMapperBridge
import com.charan.padstudio.studio
import com.charan.padstudio.ui.AboutCard
import com.charan.padstudio.ui.LicencesDialog
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.AppIcon
import com.charan.padstudio.ui.components.CardColumns
import com.charan.padstudio.ui.components.CardTone
import com.charan.padstudio.ui.components.Disclosure
import com.charan.padstudio.ui.components.ListRow
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.ProgressRing
import com.charan.padstudio.ui.components.RowLeading
import com.charan.padstudio.ui.components.RowTrailing
import com.charan.padstudio.ui.components.ScreenDefaults
import com.charan.padstudio.ui.components.ScreenScaffold
import com.charan.padstudio.ui.components.StatusLevel
import com.charan.padstudio.ui.components.StatusPill
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.SuperellipseShape
import com.charan.padstudio.ui.components.SymIcon
import com.charan.padstudio.ui.components.cardBleed
import com.charan.padstudio.ui.components.statusInk
import com.charan.padstudio.ui.components.symbol
import com.charan.padstudio.ui.theme.Tokens
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Setup (plan §10, docs/redesign/mock-setup.html): a ring "N of 8 ready" over MainViewModel's eight health checks;
 * the checks grouped Pen, Home and Looks (any other under Other), each in plain words with its status pill and its
 * own Fix; Developer (the pen probe, device facts via tools/facts.sh); and About at the end.
 *
 * At 840 dp and wider the checks fill the left column and the summary, Developer and About a 344 dp column on the
 * right, as the mock-up; narrower, one column with the summary first. With [scrollToAbout] (the shell passes it
 * for `--es tab about`) the page opens scrolled to About: once, on its first composition, so a rotation keeps the
 * reader's place.
 *
 * Every button does what it did before the redesign: Check again calls `refresh(force = true)`, a Fix starts the
 * check's own intent through [onStart] (shown, as in the mock-up, on checks that aren't ready), and the pen probe
 * starts [PenProbeActivity] through [onStart].
 */
@Composable
fun SetupScreen(
    model: MainViewModel,
    onStart: (Intent) -> Unit,
    modifier: Modifier = Modifier,
    scrollToAbout: Boolean = false,
) {
    val context = LocalContext.current
    val checks by model.health.collectAsStateWithLifecycle()
    val config by model.config.collectAsStateWithLifecycle()
    val plan by model.plan.collectAsStateWithLifecycle()
    val snapshot by model.snapshot.collectAsStateWithLifecycle()
    val readings by rememberReadings(checks)

    // The "Pen profile is current" sentence uses the status words of plan §7.
    val status = PenStatus(readings?.listening, config.appliedProfileHash, plan?.hash, config.appliedAt, config.penEnabled)
    val facts = readings?.factsFile?.summary()
    val known = CheckFacts(
        keyMapperVersion = readings?.keyMapperVersion,
        sentText = status.sentText { formatWhen(context, it) },
        needsSend = status.needsSend,
        widgetsPlaced = readings?.widgetsPlaced,
        lawnicons = readings?.lawnicons,
        facts = facts,
    )
    val rows = remember(checks, known) { checks.map { CheckRowUi(it, plainWords(it.title, it.ok, it.detail, known)) } }
    val groups = groupByArea(rows) { it.check.title }
    val summary = if (checks.isEmpty()) null else tally(checks.map { it.ok })
    val issue = topIssue(rows) { it.check.ok }
    val keyMapperApp = remember(snapshot) { snapshot?.apps?.firstOrNull { it.facts.packageName == KeyMapperBridge.PACKAGE } }
    val recorded = facts?.recordedOn?.let { formatDay(context, it) }
    val details = remember(readings, plan?.hash, config.appliedProfileHash) {
        developerDetails(readings, plan?.hash, config.appliedProfileHash)
    }

    // Check again: pending until the view model publishes a new list, then "Checked 14:05".
    val recheck = remember { RecheckState() }
    LaunchedEffect(checks) { recheck.onChecks(checks, System.currentTimeMillis()) }
    LaunchedEffect(recheck.checking) {
        if (recheck.checking) {
            delay(RECHECK_TIMEOUT_MS)
            recheck.giveUp()
        }
    }

    // Kept here, above the layout switch, so they survive a rotation or a resize across 840 dp.
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var licencesOpen by rememberSaveable { mutableStateOf(false) }

    val scroll = rememberScrollState()
    val anchors = remember { ScrollAnchors() }
    ScrollToAboutEffect(scrollToAbout, settled = checks.isNotEmpty() && readings != null, scroll, anchors)

    val fix: (HealthCheck) -> Unit = { check -> check.fixIntent?.let(onStart) }
    ScreenScaffold(
        title = "Setup",
        subtitle = setupSubtitle(checks.size),
        modifier = modifier.onPlaced { anchors.viewport = it },
        scrollState = scroll,
        actions = {
            PillButton(
                text = if (recheck.checking) "Checking…" else "Check again",
                onClick = {
                    recheck.start(checks)
                    model.refresh(force = true)
                },
                style = PillStyle.Outlined,
                icon = Sym.refresh,
                enabled = !recheck.checking,
            )
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val summaryCard: @Composable () -> Unit = {
                SummaryCard(summary, issue, recheck.checkedAt?.let { formatWhen(context, it) }, fix)
            }
            val areaCards: @Composable () -> Unit = {
                groups.forEach { AreaCard(it, keyMapperApp, fix) }
            }
            val developerCard: @Composable () -> Unit = {
                DeveloperCard(
                    factsText = factsRowText(present = readings?.let { it.factsFile != null }, recorded = recorded),
                    details = details,
                    detailsOpen = detailsOpen,
                    onDetailsOpenChange = { detailsOpen = it },
                    onOpenProbe = { onStart(Intent(context, PenProbeActivity::class.java)) },
                )
            }
            val aboutCard: @Composable () -> Unit = {
                AboutCard(onOpenLicences = { licencesOpen = true }, modifier = Modifier.onPlaced { anchors.about = it })
            }
            if (maxWidth >= TwoPaneMinWidth) {
                CardColumns(
                    endWidth = SideColumnWidth,
                    stackBelow = TwoPaneMinWidth,
                    start = { areaCards() },
                    end = {
                        summaryCard()
                        developerCard()
                        aboutCard()
                    },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.GapM)) {
                    summaryCard()
                    areaCards()
                    developerCard()
                    aboutCard()
                }
            }
        }
    }
    if (licencesOpen) LicencesDialog(onDismiss = { licencesOpen = false })
}

/** From this content width the page has two columns, as the mock-up (plan §10: list–detail at 840 dp and wider). */
private val TwoPaneMinWidth = 840.dp

/** The mock-up's right-hand column: the summary, Developer and About. */
private val SideColumnWidth = 344.dp

/** Check again gives up waiting after this long, so its button can't stay on "Checking…". */
private const val RECHECK_TIMEOUT_MS = 15_000L

/** With [scrollToAbout], wait at most this long for the checks and facts to load before scrolling, so About doesn't move after. */
private const val SETTLE_TIMEOUT_MS = 2_000L

/** One check as the page shows it: the view model's [check], its [state] and its sentence in plain [words]. */
private class CheckRowUi(val check: HealthCheck, val words: String) {
    val state: CheckState = checkState(check.ok)
}

private val CheckState.level: StatusLevel
    get() = when (this) {
        CheckState.READY -> StatusLevel.Good
        CheckState.ATTENTION -> StatusLevel.Warn
        CheckState.BY_HAND -> StatusLevel.Unknown
    }

/**
 * What the page reads beyond the view model, off the main thread: device-facts.json, Key Mapper's version and
 * accessibility service, how many group widgets are placed, and whether Lawnicons is installed.
 */
private class Readings(
    val factsFile: FactsFile?,
    val keyMapperVersion: String?,
    val listening: Boolean,
    val widgetsPlaced: Int,
    val lawnicons: Boolean,
)

/** [Readings], read again each time the view model publishes its checks (on resume and on Check again). Null until the first read. */
@Composable
private fun rememberReadings(checks: List<HealthCheck>): State<Readings?> {
    val context = LocalContext.current
    return produceState<Readings?>(initialValue = null, checks) {
        value = withContext(Dispatchers.IO) {
            val app = context.studio
            Readings(
                factsFile = app.pen.factsFile(),
                keyMapperVersion = KeyMapperBridge.versionName(app),
                listening = KeyMapperBridge.isAccessibilityEnabled(app),
                widgetsPlaced = WidgetPinning.placedCount(app),
                lawnicons = runCatching { app.packageManager.getPackageInfo(IconRepository.LAWNICONS, 0) }.isSuccess,
            )
        }
    }
}

/** The Developer details lines: how to refresh the facts (tools/facts.sh), what the file holds, Key Mapper and the profile. */
private fun developerDetails(readings: Readings?, planHash: String?, sentHash: String?): List<String> = buildList {
    addAll(FACTS_HOW_TO)
    val facts = readings?.factsFile
    when {
        readings == null -> add("Reading device-facts.json…")
        facts == null -> add("No device-facts.json on this tablet yet.")
        else -> addAll(factsDetails(facts, KeyEvent::keyCodeToString))
    }
    if (readings != null) add("Key Mapper: ${KeyMapperBridge.PACKAGE} ${readings.keyMapperVersion ?: "is not installed"}.")
    add("Profile ${planHash ?: "not built yet"}; last sent ${sentHash ?: "never"}.")
}

/** "14:05" for today, otherwise with the day ("11 Oct, 14:05"), in the tablet's 12- or 24-hour style. */
private fun formatWhen(context: Context, millis: Long): String {
    val time = DateFormat.getTimeFormat(context).format(Date(millis))
    if (DateUtils.isToday(millis)) return time
    return DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH) + ", " + time
}

/** A recorded day as the tablet writes dates: "11 Oct" this year, "11 Oct 2025" otherwise (order by locale). */
private fun formatDay(context: Context, day: LocalDate): String {
    // Noon, so no time zone or daylight-saving shift can move it to another day.
    val millis = day.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
}

/** Check again's states (plan §13): pending while the view model works, then the time the new checks arrived. */
@Stable
private class RecheckState {
    /** The list on screen when Check again was pressed; a different list means the new checks are in. */
    private var waitingFor by mutableStateOf<List<HealthCheck>?>(null)

    var checkedAt by mutableStateOf<Long?>(null)
        private set

    val checking: Boolean get() = waitingFor != null

    fun start(current: List<HealthCheck>) {
        waitingFor = current
    }

    fun onChecks(latest: List<HealthCheck>, now: Long) {
        val waited = waitingFor ?: return
        if (latest !== waited) {
            waitingFor = null
            checkedAt = now
        }
    }

    fun giveUp() {
        waitingFor = null
    }
}

/** Where the page's viewport and its About card are. Plain fields: they're only read when scrolling, never drawn from. */
private class ScrollAnchors {
    var viewport: LayoutCoordinates? = null
    var about: LayoutCoordinates? = null

    /** The scroll offset that puts About's top [gap] px below the top of the viewport; null until both are placed. */
    fun aboutOffset(current: Int, gap: Int): Int? {
        val page = viewport?.takeIf { it.isAttached } ?: return null
        val card = about?.takeIf { it.isAttached } ?: return null
        return current + page.localPositionOf(card, Offset.Zero).y.roundToInt() - gap
    }
}

/**
 * Scrolls to About once [scrollToAbout] is on: after the checks and facts have loaded ([settled]), or after
 * [SETTLE_TIMEOUT_MS] if they take longer. Done once per request: remembered across recreation, and asked again
 * only when [scrollToAbout] turns off and on.
 */
@Composable
private fun ScrollToAboutEffect(scrollToAbout: Boolean, settled: Boolean, scroll: ScrollState, anchors: ScrollAnchors) {
    var done by rememberSaveable { mutableStateOf(false) }
    val gap = with(LocalDensity.current) { ScreenDefaults.TopPadding.roundToPx() }
    LaunchedEffect(scrollToAbout) {
        if (!scrollToAbout) done = false
    }
    LaunchedEffect(scrollToAbout, done, settled) {
        if (!scrollToAbout || done) return@LaunchedEffect
        if (!settled) delay(SETTLE_TIMEOUT_MS)
        // Let the page lay out with what has loaded, then measure where About is.
        repeat(ANCHOR_FRAMES) {
            withFrameNanos { }
            val target = anchors.aboutOffset(scroll.value, gap)
            if (target != null) {
                scroll.scrollTo(target)
                done = true
                return@LaunchedEffect
            }
        }
        done = true
    }
}

/** Frames to wait for the About card to be placed before giving up on scrolling to it. */
private const val ANCHOR_FRAMES = 10

/** A card's title, with [meta] ("4 of 5 ready") at the end, or under it when they don't fit side by side. */
@Composable
private fun CardTitle(title: String, meta: String? = null) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    BesideOrBelow(
        start = { Text(title, style = type.titleLarge, color = scheme.onSurface, modifier = Modifier.semantics { heading() }) },
        end = {
            if (meta != null) Text(meta, style = type.bodyMedium.copy(fontFeatureSettings = "tnum"), color = scheme.onSurfaceVariant)
        },
        modifier = Modifier.fillMaxWidth(),
        wrapStart = false,
    )
}

/**
 * [start] and [end] in a row, centred on each other, with [end] at the far end; or [end] under [start] when the
 * row is too narrow: when [start] wouldn't fit beside [end] whole (or, with [wrapStart], in at least half the
 * row). Decided from the measured widths, never from a font-scale threshold, so at 200 % the pill moves under
 * the check's name as in the mock-up.
 */
@Composable
private fun BesideOrBelow(
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = Tokens.GapS,
    wrapStart: Boolean = true,
) {
    Layout(contents = listOf(start, end), modifier = modifier) { (startMeasurables, endMeasurables), constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val first = startMeasurables.firstOrNull()
        val second = endMeasurables.firstOrNull()?.measure(loose)
        val gapPx = gap.roundToPx()
        if (first == null) {
            val height = max(second?.height ?: 0, constraints.minHeight)
            return@Layout layout(max(second?.width ?: 0, constraints.minWidth), height) { second?.placeRelative(0, 0) }
        }
        if (second == null || second.width == 0 || !constraints.hasBoundedWidth) {
            val one = first.measure(loose)
            val width = max(one.width + (second?.let { gapPx + it.width } ?: 0), constraints.minWidth)
            val height = max(max(one.height, second?.height ?: 0), constraints.minHeight)
            return@Layout layout(width, height) {
                one.placeRelative(0, (height - one.height) / 2)
                second?.placeRelative(width - second.width, (height - second.height) / 2)
            }
        }
        val width = constraints.maxWidth
        val room = width - second.width - gapPx
        val fitsWhole = room > 0 && first.maxIntrinsicWidth(constraints.maxHeight) <= room
        if (fitsWhole || (wrapStart && room >= width / 2)) {
            val one = first.measure(loose.copy(maxWidth = room))
            val height = max(max(one.height, second.height), constraints.minHeight)
            layout(width, height) {
                one.placeRelative(0, (height - one.height) / 2)
                second.placeRelative(width - second.width, (height - second.height) / 2)
            }
        } else {
            val one = first.measure(loose)
            val between = 4.dp.roundToPx()
            layout(width, max(one.height + between + second.height, constraints.minHeight)) {
                one.placeRelative(0, 0)
                second.placeRelative(0, one.height + between)
            }
        }
    }
}

/** The summary: the ring, "6 of 8 ready" with its legend, then the first check that needs attention with its Fix. */
@Composable
private fun SummaryCard(tally: SetupTally?, issue: CheckRowUi?, checkedAt: String?, onFix: (HealthCheck) -> Unit) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    StudioCard(Modifier.fillMaxWidth(), spacing = Tokens.GapM) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.GapM)) {
            ProgressRing(
                progress = tally?.progress ?: 0f,
                value = tally?.ready?.toString() ?: "–",
                size = 96.dp,
                strokeWidth = 8.dp,
                contentDescription = tally?.spoken ?: "Checking your setup",
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    tally?.headline ?: "Checking…",
                    style = type.titleLarge,
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                tally?.legend?.forEach { (state, words) -> LegendLine(state.level, words) }
                if (checkedAt != null) {
                    Row(
                        Modifier
                            .padding(top = 4.dp)
                            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SymIcon(
                            Sym.time,
                            contentDescription = null,
                            size = LegendIconSize,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = firstLineInset(type.bodyMedium)),
                        )
                        Text("Checked $checkedAt", style = type.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (issue != null) TopIssue(issue, onFix)
    }
}

private val LegendIconSize = 16.dp

/** How far down a [LegendIconSize] icon sits to centre on the first line of [style] (lines grow with the text size). */
@Composable
private fun firstLineInset(style: TextStyle): Dp {
    val line = with(LocalDensity.current) { if (style.lineHeight.isSp) style.lineHeight.toDp() else LegendIconSize }
    return ((line - LegendIconSize) / 2).coerceAtLeast(0.dp)
}

/** A legend line: the state's own symbol in its colour, then the words, so the state never rests on colour alone. */
@Composable
private fun LegendLine(level: StatusLevel, words: String) {
    val style = MaterialTheme.typography.bodyMedium
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SymIcon(level.symbol, contentDescription = null, size = LegendIconSize, tint = statusInk(level), modifier = Modifier.padding(top = firstLineInset(style)))
        Text(words, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The first check that needs attention, as the mock-up's callout: name, sentence and its Fix when it has one. */
@Composable
private fun TopIssue(row: CheckRowUi, onFix: (HealthCheck) -> Unit) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    StudioCard(Modifier.fillMaxWidth(), tone = CardTone.High, contentPadding = PaddingValues(16.dp), spacing = 6.dp) {
        Row(
            Modifier.clearAndSetSemantics { contentDescription = "${row.state.label}: ${row.check.title}. ${row.words}" },
            horizontalArrangement = Arrangement.spacedBy(Tokens.GapS),
        ) {
            SymIcon(Sym.warning, contentDescription = null, tint = statusInk(StatusLevel.Warn), modifier = Modifier.padding(top = 2.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.check.title, style = type.titleMedium, color = scheme.onSurface)
                Text(row.words, style = type.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        FixButton(row.check, onFix, PillStyle.Filled, Modifier.padding(start = 24.dp + Tokens.GapS))
    }
}

/** The check's own Fix (MainViewModel's label and intent), when it has one. */
@Composable
private fun FixButton(check: HealthCheck, onFix: (HealthCheck) -> Unit, style: PillStyle, modifier: Modifier = Modifier) {
    val label = check.fix ?: return
    if (check.fixIntent == null) return
    PillButton(text = typographic(label), onClick = { onFix(check) }, modifier = modifier, style = style, icon = Sym.open)
}

/** One area's card: its title with "4 of 5 ready", then its checks. */
@Composable
private fun AreaCard(group: AreaGroup<CheckRowUi>, keyMapperApp: InstalledApp?, onFix: (HealthCheck) -> Unit) {
    val count = tally(group.items.map { it.check.ok })
    StudioCard(Modifier.fillMaxWidth(), spacing = 4.dp) {
        CardTitle(group.area.title, count.headline)
        Column {
            group.items.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = hairline())
                CheckRow(row, group.area, keyMapperApp, onFix)
            }
        }
    }
}

/** A check: its icon, name, status pill and sentence (one phrase for TalkBack), then its Fix. */
@Composable
private fun CheckRow(row: CheckRowUi, area: CheckArea, keyMapperApp: InstalledApp?, onFix: (HealthCheck) -> Unit) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = Tokens.GapS)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = "${row.check.title}, ${row.state.label}. ${row.words}" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Tokens.GapM),
        ) {
            if (keyMapperApp != null && isCheck(row.check.title, CheckTitles.KEY_MAPPER_INSTALLED)) {
                AppIcon(keyMapperApp, size = TileSize, contentDescription = null)
            } else {
                SetupTile(checkSymbol(row.check.title), area.tone)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BesideOrBelow(
                    start = { Text(row.check.title, style = type.titleMedium, color = scheme.onSurface) },
                    end = { StatusPill(row.state.label, row.state.level) },
                )
                Text(row.words, style = type.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        // As the mock-up: a ready check has nothing to fix, so only the others show their Fix.
        if (row.state != CheckState.READY) {
            FixButton(row.check, onFix, PillStyle.Tonal, Modifier.padding(start = TileSize + Tokens.GapM, top = 6.dp))
        }
    }
}

/** The symbol on a check's tile. */
@DrawableRes
private fun checkSymbol(title: String): Int = when {
    isCheck(title, CheckTitles.KEY_MAPPER_INSTALLED) -> Sym.keyboard
    isCheck(title, CheckTitles.KEY_MAPPER_SEES_PEN) -> Sym.pen
    isCheck(title, CheckTitles.PROFILE_CURRENT) -> Sym.send
    isCheck(title, CheckTitles.HYPEROS_KEEPS_KEY_MAPPER) -> Sym.hyperos
    isCheck(title, CheckTitles.FACTS_IMPORTED) -> Sym.info
    isCheck(title, CheckTitles.WIDGETS_PLACED) -> Sym.widgets
    isCheck(title, CheckTitles.THEMED_ICONS) -> Sym.looks
    isCheck(title, CheckTitles.WALLPAPER) -> Sym.wallpaper
    else -> Sym.setup
}

private val CheckArea.tone: TileTone
    get() = when (this) {
        CheckArea.PEN -> TileTone.Pen
        CheckArea.HOME -> TileTone.Home
        CheckArea.LOOKS -> TileTone.Looks
        CheckArea.OTHER -> TileTone.Neutral
    }

/** Developer: the pen probe, where device facts come from, and the raw details. */
@Composable
private fun DeveloperCard(
    factsText: String,
    details: List<String>,
    detailsOpen: Boolean,
    onDetailsOpenChange: (Boolean) -> Unit,
    onOpenProbe: () -> Unit,
) {
    StudioCard(Modifier.fillMaxWidth()) {
        CardTitle("Developer")
        Column(Modifier.cardBleed()) {
            ListRow(
                headline = "Pen probe",
                supporting = "Shows every key and pen event as it arrives.",
                leading = RowLeading.Custom { SetupTile(Sym.testPen, TileTone.Neutral) },
                trailing = RowTrailing.Chevron,
                onClick = onOpenProbe,
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = hairline())
            ListRow(
                headline = "Device facts",
                supporting = factsText,
                leading = RowLeading.Custom { SetupTile(Sym.info, TileTone.Neutral) },
            )
        }
        Disclosure(expanded = detailsOpen, onExpandedChange = onDetailsOpenChange) {
            details.forEach { Text(it) }
        }
    }
}

/** The mock-up's hairline between rows. */
@Composable
private fun hairline() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)

/** The colours of a [SetupTile]: one per area, neutral for Developer and About. */
internal enum class TileTone { Pen, Home, Looks, Neutral }

private val TileSize = 40.dp
private val TileShape = SuperellipseShape()

/**
 * A category tile (the mock-up's `.m-setup-cat`): [icon] on a 40 dp tonal superellipse, the shape of the app icons
 * beside it. Decorative: the row's text says what it is.
 */
@Composable
internal fun SetupTile(@DrawableRes icon: Int, tone: TileTone, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        TileTone.Pen -> scheme.primaryContainer to scheme.onPrimaryContainer
        TileTone.Home -> scheme.secondaryContainer to scheme.onSecondaryContainer
        TileTone.Looks -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        TileTone.Neutral -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
    Box(modifier.size(TileSize).clip(TileShape).background(container), contentAlignment = Alignment.Center) {
        SymIcon(icon, contentDescription = null, tint = content)
    }
}
