package com.charan.padstudio.home

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.charan.padstudio.core.home.AppFacts
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.core.home.GridSpec
import com.charan.padstudio.core.home.GroupPlanner
import com.charan.padstudio.core.home.Placement
import com.charan.padstudio.core.home.PlannedGroup
import com.charan.padstudio.core.home.WidgetLayout
import com.charan.padstudio.data.HomeSnapshot
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.data.StudioConfig
import com.charan.padstudio.studio
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.theme.Tokens
import com.charan.padstudio.ui.themedIconColors
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.glance.color.ColorProvider as DayNight
import androidx.glance.material3.ColorProviders as materialColors

/**
 * One home-screen widget per app group (plan §6b, §10 Widget, docs/redesign/mock-widget.html).
 *
 * - **Header:** a pill with the group's name and app count that opens the folder panel, and an edit
 *   button for Edit widget (HyperOS may not offer reconfigure). While the saved grid doesn't fit, the
 *   note "8 × 4 doesn't fit · showing 6 × 3" sits beside it; when both don't fit, the note wins.
 * - **Grid:** [GridSpec.resolve] picks this widget's grid ([GRID], else PadStudio's default, else Auto) and
 *   [GroupPlanner.layoutFor] lays it out for the real size ([SizeMode.Exact]). Auto is today's grid exactly.
 *   Rows share the height; when a group has more apps than cells, the last cell is "+N" and opens the folder.
 * - **Designed states, never blanks:** "Choose a group" (no group saved), "No apps in Games right now"
 *   (an empty group), and the app's initial on a tile when its icon can't be loaded.
 * - **Colours:** the brand surface from [Tokens] in light or dark as the system is, or the wallpaper's
 *   colours (Glance's dynamic colours) while Looks → "Match wallpaper colours" is on; the system's widget
 *   corner radius.
 *
 * Glance lays out at most 10 children per Row and Column, which [GridSpec] (10 columns, 8 rows) respects.
 */
class GroupWidget : GlanceAppWidget() {
    // Exact: one layout per size the launcher reports (portrait and landscape), each filling the real
    // box. Responsive snapped to the nearest preset and left the grid off-centre with empty bands.
    override val sizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val studio = context.studio
        val first = withContext(Dispatchers.Default) { studio.apps.snapshot() }
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val keptGroup = keepLegacyGroups(context, first)[appWidgetId]
        provideContent {
            // Read inside the composition, so a change made while this session is open (density, default
            // grid, colours, icon style, a moved app) redraws the widget instead of waiting for a new session.
            val config by studio.config.config.collectAsState()
            val snapshot by produceState(first, config.overrides) {
                value = withContext(Dispatchers.Default) { studio.apps.snapshot() }
            }
            val state = currentState<Preferences>()
            // Each widget may keep its own density; otherwise it follows PadStudio's setting.
            val density = state[DENSITY]?.let { name -> Density.entries.firstOrNull { it.name == name } } ?: config.density
            GlanceTheme(colors = widgetColors(config.matchWallpaperColours)) {
                WidgetContent(context, appWidgetId, snapshot, config, state[GROUP] ?: keptGroup, state[GRID], density)
            }
        }
    }

    companion object {
        val GROUP = stringPreferencesKey("group")
        val DENSITY = stringPreferencesKey("density")

        /** This widget's grid (plan §6b): "default" or missing → StudioConfig.widgetGrid, "auto", or "8x4". */
        val GRID = stringPreferencesKey("grid")
    }
}

/** The wide (4×2) widget; the original receiver, so widgets placed before sizes existed keep working. */
class GroupWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GroupWidget()
}

class GroupWidgetSmallReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GroupWidget()
}

class GroupWidgetLargeReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GroupWidget()
}

/** Sizes in the launcher's widget picker; one receiver each, all drawn by [GroupWidget]. */
enum class WidgetSize(val label: String, val receiver: Class<out GlanceAppWidgetReceiver>) {
    SMALL("Small 2×2", GroupWidgetSmallReceiver::class.java),
    WIDE("Wide 4×2", GroupWidgetReceiver::class.java),
    LARGE("Large 4×4", GroupWidgetLargeReceiver::class.java),
}

// --- Drawing -------------------------------------------------------------------------------------

@Composable
private fun WidgetContent(
    context: Context,
    appWidgetId: Int,
    snapshot: HomeSnapshot,
    config: StudioConfig,
    savedGroup: String?,
    savedGrid: String?,
    density: Density,
) {
    val size = LocalSize.current
    val widthDp = size.width.value
    val heightDp = size.height.value
    val meter = TextMeter(context)
    val edit = actionStartActivity(
        Intent(context, EditGroupWidgetActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setIdentifier(widgetIntentId(appWidgetId, "edit")),
    )
    val shows = widgetShows(savedGroup, snapshot.groups, snapshot.placements)

    // The pill (and with it the header) is 24 dp tall at 100 % text and grows with the text.
    val pillHeightDp = maxOf(PILL_MIN_DP, meter.lineDp(LABEL_SP, WEIGHT_MEDIUM) + 2 * PILL_PAD_V_DP)
    val headerDp = PAD_DP + pillHeightDp + HEADER_GAP_DP
    val bodyHeightDp = heightDp - headerDp - PAD_DP

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .background(widgetSurface(config.matchWallpaperColours))
            .padding(bottom = PAD_DP.dp),
    ) {
        when (shows) {
            is WidgetShows.Apps -> {
                val group = shows.group
                val layout = widgetLayout(widthDp, heightDp, savedGrid, config.widgetGrid, density, meter.labelLineDp)
                logInvalidGrids(appWidgetId, savedGrid, config.widgetGrid)
                // The widget id lets the folder panel offer Edit widget for this widget (Panels, 840 dp and wider).
                val openPanel = actionStartActivity(
                    Intent(context, FolderPanelActivity::class.java)
                        .putExtra(FolderPanelActivity.EXTRA_GROUP, group.group.name)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .setIdentifier(widgetIntentId(appWidgetId, "panel")),
                )
                val count = group.apps.size.toString()
                val note = layout.note
                val pillDp = meter.pillWidthDp(group.group.title, count)
                val noteTextDp = note?.let { meter.widthDp(it, LABEL_SP, WEIGHT_REGULAR) }
                val headerRoomDp = widthDp - 2 * PAD_DP - EDIT_START_DP - EDIT_BOX_DP
                val showPill = headerShowsPill(headerRoomDp, pillDp, noteTextDp?.let { NOTE_FRAME_DP + it })
                Header(
                    title = group.group.title,
                    count = count,
                    showPill = showPill,
                    primary = openPanel,
                    primaryDescription = "${group.group.title}, ${appsPhrase(group.apps.size)}. Opens the folder",
                    note = note,
                    noteWidthDp = noteTextDp?.let { noteWidth(headerRoomDp - (if (showPill) pillDp + PILL_NOTE_GAP_DP else 0f) - NOTE_FRAME_DP, it) },
                    edit = edit,
                )
                AppGrid(context, snapshot, config, group, layout, cellGeometry(layout, bodyHeightDp, meter), meter, openPanel)
            }
            is WidgetShows.NoApps -> {
                val title = shows.group.title
                Header(
                    title = title,
                    count = shows.inMore.toString(),
                    showPill = true,
                    primary = edit,
                    primaryDescription = "$title, ${appsPhrase(shows.inMore)}. Edit widget",
                    note = null,
                    noteWidthDp = null,
                    edit = null,
                )
                StateBody(
                    meter = meter,
                    widthDp = widthDp,
                    heightDp = bodyHeightDp,
                    symbol = Sym.home,
                    title = if (shows.inMore == 0) "No apps in $title right now" else "Only ${appsPhrase(shows.inMore)} in $title",
                    supporting = when (shows.inMore) {
                        0 -> null
                        1 -> "It's shown in More for now."
                        else -> "They're shown in More for now."
                    },
                    button = "Edit",
                    buttonIcon = Sym.edit,
                    filled = false,
                    action = edit,
                )
            }
            WidgetShows.ChooseGroup -> {
                Header(
                    title = "PadStudio",
                    count = null,
                    showPill = true,
                    primary = edit,
                    primaryDescription = "Choose a group for this widget",
                    note = null,
                    noteWidthDp = null,
                    edit = null,
                )
                StateBody(
                    meter = meter,
                    widthDp = widthDp,
                    heightDp = bodyHeightDp,
                    symbol = Sym.widgets,
                    title = "Choose a group",
                    supporting = "Pick which apps this widget shows.",
                    button = "Choose",
                    buttonIcon = null,
                    filled = true,
                    action = edit,
                )
            }
        }
    }
}

/**
 * The header row. Its two touch areas reach up through the widget's top padding, so each is 38 dp tall
 * and as wide as it can be: the pill side ([primary]) and the edit side (the fit note plus the button).
 * [noteWidthDp] is [noteWidth]'s answer: null lets the note take its own width.
 */
@Composable
private fun Header(
    title: String,
    count: String?,
    showPill: Boolean,
    primary: Action,
    primaryDescription: String,
    note: String?,
    noteWidthDp: Float?,
    edit: Action?,
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(primary)
                .semantics { contentDescription = primaryDescription }
                .padding(start = PAD_DP.dp, top = PAD_DP.dp, bottom = HEADER_GAP_DP.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (showPill) Pill(title, count) else Spacer(GlanceModifier.width(1.dp).height(PILL_MIN_DP.dp))
        }
        if (edit != null) {
            Row(
                modifier = GlanceModifier
                    .clickable(edit)
                    .semantics { contentDescription = if (note != null) "$note. Edit widget" else "Edit widget" }
                    .padding(start = EDIT_START_DP.dp, top = PAD_DP.dp, end = PAD_DP.dp, bottom = HEADER_GAP_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Keeps this side as tall as the pill, so the note and the button line up with it.
                Spacer(GlanceModifier.width(1.dp).height(PILL_MIN_DP.dp))
                if (note != null) {
                    Image(
                        provider = ImageProvider(Sym.fitWarn),
                        contentDescription = null,
                        modifier = GlanceModifier.size(FIT_ICON_DP.dp),
                        colorFilter = ColorFilter.tint(WarnInk),
                    )
                    // A fixed width stops long words from pushing the edit button out of the row.
                    if (noteWidthDp != 0f) {
                        Spacer(GlanceModifier.width(FIT_ICON_GAP_DP.dp))
                        Text(
                            note,
                            maxLines = 1,
                            modifier = if (noteWidthDp != null) GlanceModifier.width(noteWidthDp.dp) else GlanceModifier,
                            style = TextStyle(fontSize = LABEL_SP.sp, color = GlanceTheme.colors.onSurfaceVariant),
                        )
                    }
                    Spacer(GlanceModifier.width(NOTE_END_GAP_DP.dp))
                }
                Box(modifier = GlanceModifier.width(EDIT_BOX_DP.dp), contentAlignment = Alignment.CenterEnd) {
                    Image(
                        provider = ImageProvider(Sym.edit),
                        contentDescription = null,
                        modifier = GlanceModifier.size(EDIT_ICON_DP.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                    )
                }
            }
        }
    }
}

/** The group's name and app count on a pill, at least 24 dp tall (kit .widget__pill). */
@Composable
private fun Pill(title: String, count: String?) {
    Box(
        modifier = GlanceModifier.cornerRadius(PILL_RADIUS_DP.dp).background(GlanceTheme.colors.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Spacer(GlanceModifier.width(1.dp).height(PILL_MIN_DP.dp))
        Row(
            modifier = GlanceModifier.padding(horizontal = PILL_PAD_H_DP.dp, vertical = PILL_PAD_V_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = GlanceTheme.colors.onSecondaryContainer
            Text(title, maxLines = 1, style = TextStyle(fontSize = LABEL_SP.sp, fontWeight = FontWeight.Medium, color = color))
            if (count != null) {
                Spacer(GlanceModifier.width(PILL_COUNT_GAP_DP.dp))
                Text(count, maxLines = 1, style = TextStyle(fontSize = LABEL_SP.sp, color = color))
            }
        }
    }
}

/** How each cell draws: the icon size, and whether icon and label fit so they can be centred. */
private class CellGeometry(val iconDp: Float, val showLabel: Boolean, val centred: Boolean)

private fun cellGeometry(layout: WidgetLayout, gridHeightDp: Float, meter: TextMeter): CellGeometry {
    // Rows share what the header leaves, which is a little less than layoutFor's 28 dp header allows once
    // text is larger than 100 %.
    val cellHeightDp = (gridHeightDp / layout.rows).coerceAtLeast(0f)
    // Never bigger than its cell: only very small widgets (under one 48 dp cell) need this.
    val iconDp = minOf(layout.iconDp, layout.cellWidthDp, cellHeightDp).coerceAtLeast(1f)
    val labelDp = if (layout.showLabels) meter.lineDp(LABEL_SP, WEIGHT_REGULAR) else null
    return CellGeometry(iconDp, layout.showLabels, cellHolds(cellHeightDp, iconDp, labelDp))
}

@Composable
private fun ColumnScope.AppGrid(
    context: Context,
    snapshot: HomeSnapshot,
    config: StudioConfig,
    group: PlannedGroup,
    layout: WidgetLayout,
    geometry: CellGeometry,
    meter: TextMeter,
    openPanel: Action,
) {
    val colors = themedIconColors(context)
    Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(horizontal = PAD_DP.dp)) {
        gridCells(group.apps, layout.columns, layout.rows).forEach { row ->
            // Empty rows keep their share, so the cells keep the height layoutFor gave them.
            if (row.all { it == Cell.Empty }) {
                Spacer(GlanceModifier.fillMaxWidth().defaultWeight())
                return@forEach
            }
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                row.forEach { cell ->
                    when (cell) {
                        is Cell.App -> {
                            val app = snapshot.app(cell.app)
                            AppCell(cell.app, app, app?.let { appIcon(context, it, config, colors.background, colors.glyph) }, geometry, meter)
                        }
                        is Cell.More -> MoreCell(cell.count, geometry, meter, openPanel)
                        Cell.Empty -> Spacer(GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.AppCell(facts: AppFacts, app: InstalledApp?, icon: Icon?, geometry: CellGeometry, meter: TextMeter) {
    val label = app?.facts?.label ?: facts.label
    Column(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .clickable(actionStartActivity(app?.launchIntent() ?: launchIntent(facts)))
            .semantics { contentDescription = label },
        verticalAlignment = if (geometry.centred) Alignment.CenterVertically else Alignment.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(geometry.iconDp.dp))
        } else {
            InitialTile(appInitial(label, facts.packageName), geometry.iconDp, meter)
        }
        if (geometry.showLabel) {
            Spacer(GlanceModifier.height(LABEL_SPACER_DP.dp))
            Text(
                label,
                maxLines = 1,
                modifier = GlanceModifier.padding(horizontal = LABEL_PAD_H_DP.dp),
                style = TextStyle(fontSize = LABEL_SP.sp, color = GlanceTheme.colors.onSurface, textAlign = TextAlign.Center),
            )
        }
    }
}

/** The "+N" tile: the whole last cell is its touch target (at least 48 dp in any grid that fits). */
@Composable
private fun RowScope.MoreCell(count: Int, geometry: CellGeometry, meter: TextMeter, openPanel: Action) {
    val text = "+$count"
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .clickable(openPanel)
            .semantics { contentDescription = moreDescription(count) },
        contentAlignment = if (geometry.centred) Alignment.Center else Alignment.TopCenter,
    ) {
        Box(
            modifier = GlanceModifier
                .size(geometry.iconDp.dp)
                .cornerRadius((geometry.iconDp * TILE_CORNER).dp)
                .background(GlanceTheme.colors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                maxLines = 1,
                style = TextStyle(
                    fontSize = meter.fitSp(text, MORE_SP, geometry.iconDp * TILE_TEXT_SHARE, WEIGHT_MEDIUM).sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSecondaryContainer,
                ),
            )
        }
    }
}

/** A missing icon: the app's initial on a tonal tile (kit .app-icon--initial), never a blank cell. */
@Composable
private fun InitialTile(initial: String, sizeDp: Float, meter: TextMeter) {
    Box(
        modifier = GlanceModifier
            .size(sizeDp.dp)
            .cornerRadius((sizeDp * TILE_CORNER).dp)
            .background(GlanceTheme.colors.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        // Part of the icon, so it keeps the icon's proportions at any font scale.
        Text(
            initial,
            maxLines = 1,
            style = TextStyle(
                fontSize = meter.spForDp(sizeDp * INITIAL_SHARE).sp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onTertiaryContainer,
            ),
        )
    }
}

/**
 * A designed state in place of the grid: a symbol, a title, a line of help and a button. When the widget
 * is too short for all of them, the symbol goes first, then the help, then the button (the whole area
 * then opens [action]); the title always stays.
 */
@Composable
private fun ColumnScope.StateBody(
    meter: TextMeter,
    widthDp: Float,
    heightDp: Float,
    @DrawableRes symbol: Int,
    title: String,
    supporting: String?,
    button: String,
    @DrawableRes buttonIcon: Int?,
    filled: Boolean,
    action: Action,
) {
    val textWidthDp = widthDp - 2 * (PAD_DP + STATE_SIDE_DP)
    val titleLines = lineCount(title, textWidthDp) { meter.widthDp(it, TITLE_SP, WEIGHT_MEDIUM) }
    val supportingLines = supporting?.let { text -> lineCount(text, textWidthDp) { meter.widthDp(it, BODY_SP, WEIGHT_REGULAR) } } ?: 0
    val buttonHeightDp = maxOf(BUTTON_MIN_DP, meter.lineDp(BODY_SP, WEIGHT_MEDIUM) + 2 * BUTTON_PAD_V_DP)
    val fit = stateFit(
        heightDp = heightDp - STATE_BOTTOM_DP,
        titleLines = titleLines,
        titleLineDp = meter.lineDp(TITLE_SP, WEIGHT_MEDIUM),
        supportingLines = supportingLines,
        supportingLineDp = meter.lineDp(BODY_SP, WEIGHT_REGULAR),
        buttonDp = buttonHeightDp + 2 * BUTTON_TARGET_PAD_DP,
    )
    var modifier = GlanceModifier
        .fillMaxWidth()
        .defaultWeight()
        .padding(start = (PAD_DP + STATE_SIDE_DP).dp, end = (PAD_DP + STATE_SIDE_DP).dp, bottom = STATE_BOTTOM_DP.dp)
    if (!fit.button) modifier = modifier.clickable(action).semantics { contentDescription = "$title. $button" }
    Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        if (fit.icon) {
            Box(
                modifier = GlanceModifier
                    .size(STATE_ICON_DP.dp)
                    .cornerRadius((STATE_ICON_DP / 2).dp)
                    .background(GlanceTheme.colors.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(symbol),
                    contentDescription = null,
                    modifier = GlanceModifier.size(STATE_SYMBOL_DP.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                )
            }
            Spacer(GlanceModifier.height(STATE_ICON_GAP_DP.dp))
        }
        Text(
            title,
            maxLines = fit.titleLines,
            style = TextStyle(fontSize = TITLE_SP.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface, textAlign = TextAlign.Center),
        )
        if (fit.supportingLines > 0 && supporting != null) {
            Spacer(GlanceModifier.height(STATE_GAP_DP.dp))
            Text(
                supporting,
                maxLines = fit.supportingLines,
                style = TextStyle(fontSize = BODY_SP.sp, color = GlanceTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center),
            )
        }
        if (fit.button) {
            Spacer(GlanceModifier.height(STATE_GAP_DP.dp))
            PillButton(button, buttonIcon, filled, action)
        }
    }
}

/** A 40 dp pill button inside a 48 dp touch target: filled for the main action, tonal otherwise. */
@Composable
private fun PillButton(label: String, @DrawableRes icon: Int?, filled: Boolean, action: Action) {
    val container = if (filled) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer
    val content = if (filled) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
    Box(
        modifier = GlanceModifier
            .cornerRadius(PILL_RADIUS_DP.dp)
            .clickable(action)
            .semantics { contentDescription = label }
            .padding(vertical = BUTTON_TARGET_PAD_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = GlanceModifier.cornerRadius(PILL_RADIUS_DP.dp).background(container), contentAlignment = Alignment.Center) {
            Spacer(GlanceModifier.width(1.dp).height(BUTTON_MIN_DP.dp))
            Row(
                modifier = GlanceModifier.padding(
                    start = (if (icon != null) BUTTON_PAD_ICON_DP else BUTTON_PAD_H_DP).dp,
                    end = BUTTON_PAD_H_DP.dp,
                    top = BUTTON_PAD_V_DP.dp,
                    bottom = BUTTON_PAD_V_DP.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Image(
                        provider = ImageProvider(icon),
                        contentDescription = null,
                        modifier = GlanceModifier.size(BUTTON_ICON_DP.dp),
                        colorFilter = ColorFilter.tint(content),
                    )
                    Spacer(GlanceModifier.width(BUTTON_ICON_GAP_DP.dp))
                }
                Text(label, maxLines = 1, style = TextStyle(fontSize = BODY_SP.sp, fontWeight = FontWeight.Medium, color = content))
            }
        }
    }
}

// --- Android helpers -----------------------------------------------------------------------------

/**
 * The brand palette's colours; the launcher picks light or dark as the system is. Lazy: Glance builds
 * them with android.graphics.Color, which must not run when this file's plain rules are unit-tested.
 */
private val BrandColors: ColorProviders by lazy {
    materialColors(light = Tokens.lightScheme(Tokens.DefaultPalette), dark = Tokens.darkScheme(Tokens.DefaultPalette))
}

private val BrandSurface: ColorProvider = DayNight(
    day = brandSurface(Tokens.lightScheme(Tokens.DefaultPalette), dark = false),
    night = brandSurface(Tokens.darkScheme(Tokens.DefaultPalette), dark = true),
)

/** The fit note's icon: the warning ink, which reaches 3:1 on the brand surfaces (GroupWidgetTest). */
private val WarnInk: ColorProvider = DayNight(day = Tokens.WarnInkLight, night = Tokens.WarnInkDark)

/** Brand colours, or Glance's wallpaper (dynamic) colours while Looks → "Match wallpaper colours" is on. */
private fun widgetColors(matchWallpaper: Boolean): ColorProviders = if (matchWallpaper) DynamicThemeColorProviders else BrandColors

@Composable
private fun widgetSurface(matchWallpaper: Boolean): ColorProvider =
    if (matchWallpaper) GlanceTheme.colors.widgetBackground else BrandSurface

/** An app's icon in the chosen style, or null when it can't be made (the cell then shows the initial). */
private fun appIcon(context: Context, app: InstalledApp, config: StudioConfig, accent: Int, onAccent: Int): Icon? =
    runCatching { context.studio.icons.icon(app, config.iconStyle, config.iconPack, accent, onAccent) }
        .onFailure { warnOnce("icon:${app.facts.packageName}", "No icon for ${app.facts.packageName}; showing its initial", it) }
        .getOrNull()

/** Opens an app the snapshot no longer lists, the way InstalledApp.launchIntent does. */
private fun launchIntent(facts: AppFacts): Intent = Intent(Intent.ACTION_MAIN)
    .addCategory(Intent.CATEGORY_LAUNCHER)
    .setComponent(ComponentName(facts.packageName, facts.activityName))
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

/**
 * Text as the launcher will draw it: the system font at the system font scale (nonlinear on Android 14
 * and later) and with Bold text's extra weight. Glance can't measure its views, so the header, the cell
 * alignment and the designed states are decided from these numbers.
 */
private class TextMeter(context: Context) {
    private val metrics = context.resources.displayMetrics
    private val boldAdjustment = context.resources.configuration.fontWeightAdjustment
        .takeIf { it != Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED } ?: 0

    /** One 11 sp label line in dp for layoutFor, with Android's own (nonlinear) font scale (plan §6b). */
    val labelLineDp: Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LABEL_SP, metrics) / metrics.density

    private fun paint(sp: Float, weight: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
        typeface = Typeface.create(Typeface.DEFAULT, (weight + boldAdjustment).coerceIn(1, 1000), false)
    }

    fun widthDp(text: String, sp: Float, weight: Int): Float = paint(sp, weight).measureText(text) / metrics.density

    /** A one-line TextView's height: the font's top to bottom, as TextView pads text by default. */
    fun lineDp(sp: Float, weight: Int): Float = paint(sp, weight).fontMetrics.let { it.bottom - it.top } / metrics.density

    fun pillWidthDp(title: String, count: String?): Float =
        2 * PILL_PAD_H_DP + widthDp(title, LABEL_SP, WEIGHT_MEDIUM) +
            (count?.let { PILL_COUNT_GAP_DP + widthDp(it, LABEL_SP, WEIGHT_REGULAR) } ?: 0f)

    /** The sp that draws text [dp] tall at any font scale, for glyphs that belong to an icon. */
    fun spForDp(dp: Float): Float =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP, dp * metrics.density, metrics)
        } else {
            dp * metrics.density / TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, metrics)
        }

    /** [sp], or smaller if [text] would be wider than [maxWidthDp]. */
    fun fitSp(text: String, sp: Float, maxWidthDp: Float, weight: Int): Float {
        val width = widthDp(text, sp, weight)
        if (width <= maxWidthDp || width <= 0f) return sp
        val textDp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics) / metrics.density
        return spForDp(textDp * maxWidthDp / width)
    }
}

private val warned = ConcurrentHashMap.newKeySet<String>()

/** Logs [message] the first time [key] comes up in this process; a widget redraws often. */
private fun warnOnce(key: String, message: String, error: Throwable? = null) {
    if (warned.add(key)) Log.w(TAG, message, error)
}

/** Plan §6b: an invalid saved grid is treated as Default and logged, never a crash. */
private fun logInvalidGrids(appWidgetId: Int, savedGrid: String?, defaultGrid: String?) {
    for (value in invalidGrids(savedGrid, defaultGrid)) {
        warnOnce("grid:$appWidgetId:$value", "Widget $appWidgetId: $value isn't a grid (CxR, auto or default); using Default")
    }
}

private val legacyLock = Mutex()

/** Groups [keepLegacyGroups] gave out in this process, by widget id. */
private val keptGroups = ConcurrentHashMap<Int, String>()

/**
 * Widgets placed before this version may have no saved group: they showed the first group. Once, the
 * first time an updated PadStudio draws its widgets, each one without a group is given the group it was
 * showing, so it keeps drawing the same apps (plan §5.5: widgets 42 and 49). From then on, a widget
 * without a group is new or restored and asks for one ("Choose a group", plan §6b).
 *
 * Returns the groups given out in this process. Glance reads a widget's state before it calls
 * provideGlance, so a widget drawn in this same process falls back to its entry here; later sessions
 * read the saved group.
 */
private suspend fun keepLegacyGroups(context: Context, snapshot: HomeSnapshot): Map<Int, String> = legacyLock.withLock {
    val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(LEGACY_DONE, false)) return@withLock keptGroups
    val firstGroup = snapshot.groups.firstOrNull()?.group?.name
    val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    if (firstGroup != null && info != null && isUpdate(info.firstInstallTime, info.lastUpdateTime)) {
        val manager = GlanceAppWidgetManager(context)
        for (appWidgetId in WidgetPinning.placedIds(context)) {
            runCatching {
                updateAppWidgetState(context, manager.getGlanceIdBy(appWidgetId)) { state ->
                    if (state[GroupWidget.GROUP] == null) {
                        state[GroupWidget.GROUP] = firstGroup
                        keptGroups[appWidgetId] = firstGroup
                    }
                }
            }.onFailure { Log.w(TAG, "Widget $appWidgetId: couldn't keep it on $firstGroup", it) }
        }
        if (keptGroups.isNotEmpty()) Log.i(TAG, "Kept pre-update widgets on the group they showed: $keptGroups")
    }
    prefs.edit { putBoolean(LEGACY_DONE, true) }
    keptGroups
}

// --- Rules: plain functions, unit-tested in GroupWidgetTest ---------------------------------------

/** The grid a widget draws (plan §6b): its own [savedGrid], else PadStudio's [defaultGrid], else Auto. */
internal fun widgetLayout(widthDp: Float, heightDp: Float, savedGrid: String?, defaultGrid: String?, density: Density, labelLineDp: Float): WidgetLayout =
    GroupPlanner.layoutFor(widthDp, heightDp, density, GridSpec.resolve(savedGrid, defaultGrid), labelLineDp)

/** Saved values [widgetLayout] had to treat as Default: neither a grid ("8x4") nor "auto" or "default". */
internal fun invalidGrids(savedGrid: String?, defaultGrid: String?): List<String> = buildList {
    val auto = savedGrid.equals(GridSpec.AUTO, ignoreCase = true)
    val own = GridSpec.parse(savedGrid)
    if (savedGrid != null && own == null && !auto && !savedGrid.equals(GridSpec.DEFAULT, ignoreCase = true)) {
        add("its grid \"$savedGrid\"")
    }
    // The default only counts when this widget follows it.
    if (own == null && !auto && defaultGrid != null && GridSpec.parse(defaultGrid) == null) add("the default grid \"$defaultGrid\"")
}

/** What a widget shows instead of, or as, its grid. */
internal sealed interface WidgetShows {
    /** No group saved (a new or restored widget), or one that isn't a group any more. */
    data object ChooseGroup : WidgetShows

    /** The saved group has no apps of its own right now; [inMore] of them sit in More (a group needs 2). */
    data class NoApps(val group: AppGroup, val inMore: Int) : WidgetShows

    data class Apps(val group: PlannedGroup) : WidgetShows
}

internal fun widgetShows(savedGroup: String?, groups: List<PlannedGroup>, placements: List<Placement>): WidgetShows {
    val group = AppGroup.entries.firstOrNull { it.name == savedGroup }
    if (group == null || group == AppGroup.HIDDEN) return WidgetShows.ChooseGroup
    groups.firstOrNull { it.group == group }?.takeIf { it.apps.isNotEmpty() }?.let { return WidgetShows.Apps(it) }
    return WidgetShows.NoApps(group, placements.count { it.group == group })
}

/** A grid cell: an app, the "+N" tile, or nothing. */
internal sealed interface Cell<out T> {
    data class App<T>(val app: T) : Cell<T>

    data class More(val count: Int) : Cell<Nothing>

    data object Empty : Cell<Nothing>
}

/**
 * [apps] in a [columns] × [rows] grid, row by row: as many apps as fit and, when they don't all fit, the
 * "+N" tile in the last cell (GroupPlanner.overflow); short groups end in empty cells.
 */
internal fun <T> gridCells(apps: List<T>, columns: Int, rows: Int): List<List<Cell<T>>> {
    val overflow = GroupPlanner.overflow(apps.size, columns, rows)
    val filled: List<Cell<T>> = apps.take(overflow.shown).map { Cell.App(it) } +
        (if (overflow.more > 0) listOf(Cell.More(overflow.more)) else emptyList())
    return List(rows) { row -> List(columns) { column -> filled.getOrNull(row * columns + column) ?: Cell.Empty } }
}

/**
 * The identifier on an intent that carries this widget's id (Edit widget, the folder panel). Intent
 * identifiers count when Android matches PendingIntents, so two widgets showing the same group never
 * share one and swap ids; Glance also makes them unique with a data URI, which this doesn't replace.
 */
internal fun widgetIntentId(appWidgetId: Int, target: String): String = "group-widget-$appWidgetId-$target"

internal fun appsPhrase(count: Int): String = if (count == 1) "1 app" else "$count apps"

/** What TalkBack says for the "+N" tile (mock-widget.html: "4 more apps, opens the folder"). */
internal fun moreDescription(count: Int): String = "$count more ${if (count == 1) "app" else "apps"}, opens the folder"

/** The letter on a missing icon's tile: the label's first letter or digit, else the package's, else "?". */
internal fun appInitial(label: String, packageName: String): String {
    fun firstLetter(text: String): String? {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (Character.isLetterOrDigit(codePoint)) return String(Character.toChars(codePoint)).uppercase()
            index += Character.charCount(codePoint)
        }
        return null
    }
    return firstLetter(label) ?: firstLetter(packageName.substringAfterLast('.')) ?: "?"
}

/**
 * PadStudio's widget surface (mock-widget.html): the scheme's low container mixed in Oklab with 14 %
 * (dark) or 6 % (light) of primary container, the reference's navy-violet card.
 */
internal fun brandSurface(scheme: ColorScheme, dark: Boolean): Color =
    lerp(scheme.surfaceContainerLow, scheme.primaryContainer, if (dark) DARK_TINT else LIGHT_TINT)

/** Whether an icon (and its label line, if any) fits a cell's height whole, so it can be centred. */
internal fun cellHolds(cellHeightDp: Float, iconDp: Float, labelLineDp: Float?): Boolean =
    iconDp + (labelLineDp?.let { LABEL_SPACER_DP + it } ?: 0f) <= cellHeightDp + ROUNDING_DP

/** Whether the group pill fits beside the fit note in [roomDp]; when it doesn't, the note wins (mock-widget, 200 %). */
internal fun headerShowsPill(roomDp: Float, pillDp: Float, noteDp: Float?): Boolean =
    noteDp == null || pillDp + PILL_NOTE_GAP_DP + noteDp <= roomDp

/**
 * How wide the fit note's words may be when [roomDp] is left for them: null when they fit as they are
 * ([textDp]), else cut to the room, or 0 when under [NOTE_MIN_DP] is left (the icon alone; the edit
 * button still says the note to TalkBack).
 */
internal fun noteWidth(roomDp: Float, textDp: Float): Float? = when {
    textDp <= roomDp -> null
    roomDp >= NOTE_MIN_DP -> roomDp
    else -> 0f
}

/** How many lines [text] takes in [widthDp], wrapping at spaces as TextView does; [measure] gives a width in dp. */
internal fun lineCount(text: String, widthDp: Float, measure: (String) -> Float): Int {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return 0
    if (widthDp <= 0f) return words.size
    val space = measure(" ")
    var lines = 0
    var used = 0f
    for (word in words) {
        val width = measure(word)
        if (lines > 0 && used + space + width <= widthDp) {
            used += space + width
        } else {
            // A new line; a word wider than a line breaks across as many lines as it needs.
            val spans = ceil(width / widthDp).toInt().coerceAtLeast(1)
            lines += spans
            used = width - (spans - 1) * widthDp
        }
    }
    return lines
}

/** Which parts of a designed state are drawn, and how many lines each text gets. */
internal data class StateFit(val icon: Boolean, val supportingLines: Int, val button: Boolean, val titleLines: Int)

/**
 * The parts of a designed state that fit [heightDp]: the symbol goes first, then the help line, then
 * the button; the title always stays, cut to the lines there is room for.
 */
internal fun stateFit(
    heightDp: Float,
    titleLines: Int,
    titleLineDp: Float,
    supportingLines: Int,
    supportingLineDp: Float,
    buttonDp: Float,
): StateFit {
    fun need(icon: Boolean, supporting: Boolean, button: Boolean): Float =
        titleLines * titleLineDp +
            (if (icon) STATE_ICON_DP + STATE_ICON_GAP_DP else 0f) +
            (if (supporting) STATE_GAP_DP + supportingLines * supportingLineDp else 0f) +
            (if (button) STATE_GAP_DP + buttonDp else 0f)
    val help = supportingLines > 0
    for ((icon, supporting, button) in listOf(Triple(true, help, true), Triple(false, help, true), Triple(false, false, true), Triple(false, false, false))) {
        if (need(icon, supporting, button) <= heightDp + ROUNDING_DP) {
            return StateFit(icon, if (supporting) supportingLines else 0, button, titleLines)
        }
    }
    val lines = if (titleLineDp > 0f) (heightDp / titleLineDp).toInt() else titleLines
    return StateFit(icon = false, supportingLines = 0, button = false, titleLines = lines.coerceIn(1, titleLines.coerceAtLeast(1)))
}

/** Widgets are given their old group only when PadStudio was updated; a first install has none to keep. */
internal fun isUpdate(firstInstallTime: Long, lastUpdateTime: Long): Boolean = lastUpdateTime > firstInstallTime

private const val TAG = "GroupWidget"
private const val LEGACY_PREFS = "group_widget"
private const val LEGACY_DONE = "legacy_groups_kept"

/** Widget padding on each side (layoutFor's PADDING_DP) and the gap under the header pill. */
private const val PAD_DP = GroupPlanner.PADDING_DP.toFloat()
private const val HEADER_GAP_DP = 4f

/** Text sizes (sp) and weights, from the token type scale: labelSmall 11, label 13, body 16. */
private const val LABEL_SP = 11f
private const val MORE_SP = 13f
private const val BODY_SP = 13f
private const val TITLE_SP = 16f
private const val WEIGHT_REGULAR = 400
private const val WEIGHT_MEDIUM = 500

/** The header pill: 24 dp tall at 100 % text (with the 4 dp gap, layoutFor's 28 dp header). */
private const val PILL_MIN_DP = 24f
private const val PILL_PAD_H_DP = 10f
private const val PILL_PAD_V_DP = 2f
private const val PILL_COUNT_GAP_DP = 6f
private const val PILL_RADIUS_DP = 999f
private const val PILL_NOTE_GAP_DP = 8f

/** The edit side of the header: the gap before it, the button's box, its icon, and the fit note's icon. */
private const val EDIT_START_DP = 8f
private const val EDIT_BOX_DP = 40f
private const val EDIT_ICON_DP = 18f
private const val FIT_ICON_DP = 14f
private const val FIT_ICON_GAP_DP = 4f
private const val NOTE_END_GAP_DP = 8f

/** The fit note around its words (icon, gap, gap to the edit button), and the least room worth words. */
private const val NOTE_FRAME_DP = FIT_ICON_DP + FIT_ICON_GAP_DP + NOTE_END_GAP_DP
private const val NOTE_MIN_DP = 24f

/** Cells: the label's spacing, tile corners as a share of the tile, and what the tile text may fill. */
private const val LABEL_SPACER_DP = 2f
private const val LABEL_PAD_H_DP = 2f
private const val TILE_CORNER = 0.3f
private const val TILE_TEXT_SHARE = 0.8f
private const val INITIAL_SHARE = 0.42f

/** Designed states (mock-widget .m-widget-state): symbol disc, gaps, side and bottom padding. */
private const val STATE_ICON_DP = 48f
private const val STATE_SYMBOL_DP = 24f
private const val STATE_ICON_GAP_DP = 10f
private const val STATE_GAP_DP = 4f
private const val STATE_SIDE_DP = 8f
private const val STATE_BOTTOM_DP = 4f

/** Buttons: a 40 dp pill, padded to a 48 dp touch target. */
private const val BUTTON_MIN_DP = 40f
private const val BUTTON_TARGET_PAD_DP = 4f
private const val BUTTON_PAD_H_DP = 16f
private const val BUTTON_PAD_ICON_DP = 12f
private const val BUTTON_PAD_V_DP = 8f
private const val BUTTON_ICON_DP = 18f
private const val BUTTON_ICON_GAP_DP = 8f

/** Sub-pixel slack when comparing heights made of rounded parts. */
private const val ROUNDING_DP = 0.5f

/** mock-widget.html's color-mix: 86 % low container + 14 % primary container (dark), 94 % + 6 % (light). */
private const val DARK_TINT = 0.14f
private const val LIGHT_TINT = 0.06f
