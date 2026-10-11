package com.charan.padstudio.home

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.core.home.GroupPlanner
import com.charan.padstudio.core.home.PlannedGroup
import com.charan.padstudio.data.HomeSnapshot
import com.charan.padstudio.data.StudioConfig
import com.charan.padstudio.studio
import com.charan.padstudio.ui.themedIconColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One home-screen widget per app group: the group's apps in a tight grid at the chosen density,
 * a header that opens the folder panel, and a "+N" tile when the group has more apps than fit.
 * Glance lays out at most 10 children per row, which GroupPlanner.gridFor already respects.
 */
class GroupWidget : GlanceAppWidget() {
    // Exact: one layout per size the launcher reports (portrait and landscape), each filling the real
    // box. Responsive snapped to the nearest preset and left the grid off-centre with empty bands.
    override val sizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val studio = context.studio
        val snapshot = withContext(Dispatchers.Default) { studio.apps.snapshot() }
        val config = studio.config.config.value
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val state = currentState<Preferences>()
            val group = snapshot.groups.firstOrNull { it.group.name == state[GROUP] } ?: snapshot.groups.firstOrNull()
            // Each widget may keep its own grid; otherwise it follows PadStudio's setting.
            val density = state[DENSITY]?.let { name -> Density.entries.firstOrNull { it.name == name } } ?: config.density
            GlanceTheme { WidgetContent(context, group, snapshot, config, appWidgetId, density) }
        }
    }

    companion object {
        val GROUP = stringPreferencesKey("group")
        val DENSITY = stringPreferencesKey("density")
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

@Composable
private fun WidgetContent(context: Context, group: PlannedGroup?, snapshot: HomeSnapshot, config: StudioConfig, appWidgetId: Int, density: Density) {
    val size = LocalSize.current
    val (columns, rows) = GroupPlanner.gridFor(size.width.value.toInt() - 2 * PADDING_DP, size.height.value.toInt() - 2 * PADDING_DP, density)
    // Cells share the width equally, so icons grow a little with the cell instead of leaving margins.
    val cellDp = (size.width.value - 2 * PADDING_DP) / columns
    val iconDp = (cellDp * ICON_SHARE).coerceIn(density.iconDp.toFloat(), density.iconDp * MAX_ICON_GROWTH)
    val openPanel = Intent(context, FolderPanelActivity::class.java)
        .putExtra(FolderPanelActivity.EXTRA_GROUP, group?.group?.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val edit = Intent(context, EditGroupWidgetActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val colors = themedIconColors(context)

    Column(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(GlanceTheme.colors.widgetBackground).padding(PADDING_DP.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = group?.let { "${it.group.title}  ${it.apps.size}" } ?: "PadStudio",
                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(openPanel)),
            )
            Text("Edit", style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.primary), modifier = GlanceModifier.padding(horizontal = 6.dp).clickable(actionStartActivity(edit)))
            Text("›", style = TextStyle(fontSize = 18.sp, color = GlanceTheme.colors.primary), modifier = GlanceModifier.padding(start = 4.dp).clickable(actionStartActivity(openPanel)))
        }
        if (group == null) {
            Text("Open PadStudio to sort your apps.", style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
            return@Column
        }
        val slots = columns * rows
        val shown = if (group.apps.size > slots) group.apps.take(slots - 1) else group.apps
        val overflow = group.apps.size - shown.size
        val cells = shown.map<_, Cell> { Cell.App(it) } + if (overflow > 0) listOf(Cell.More(overflow)) else emptyList()
        cells.chunked(columns).forEach { row ->
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                row.forEach { cell ->
                    when (cell) {
                        is Cell.App -> {
                            val app = snapshot.app(cell.facts) ?: return@forEach
                            val icon = context.studio.icons.icon(app, config.iconStyle, config.iconPack, colors.background, colors.glyph)
                            Column(
                                modifier = GlanceModifier.defaultWeight().padding(vertical = 3.dp).clickable(actionStartActivity(app.launchIntent())),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Image(ImageProvider(icon), contentDescription = app.facts.label, modifier = GlanceModifier.size(iconDp.dp))
                                if (density.showLabels) {
                                    Text(app.facts.label, maxLines = 1, style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurface))
                                }
                            }
                        }
                        is Cell.More -> Box(
                            modifier = GlanceModifier.defaultWeight().height(iconDp.dp).clickable(actionStartActivity(openPanel)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+${cell.count}", style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, color = GlanceTheme.colors.primary))
                        }
                    }
                }
                // Empty cells keep a short last row on the same columns as the rows above.
                repeat(columns - row.size) { Spacer(GlanceModifier.defaultWeight()) }
            }
        }
    }
}

private const val PADDING_DP = 10

/** Icon size as a share of its cell, and how far past the density's size it may grow. */
private const val ICON_SHARE = 0.78f
private const val MAX_ICON_GROWTH = 1.3f

private sealed interface Cell {
    data class App(val facts: com.charan.padstudio.core.home.AppFacts) : Cell

    data class More(val count: Int) : Cell
}
