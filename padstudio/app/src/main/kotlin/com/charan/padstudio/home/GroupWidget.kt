package com.charan.padstudio.home

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
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
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(140.dp, 110.dp), DpSize(280.dp, 110.dp), DpSize(280.dp, 200.dp), DpSize(420.dp, 200.dp), DpSize(560.dp, 300.dp)),
    )
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val studio = context.studio
        val snapshot = withContext(Dispatchers.Default) { studio.apps.snapshot() }
        val config = studio.config.config.value
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val name = currentState<Preferences>()[GROUP]
            val group = snapshot.groups.firstOrNull { it.group.name == name } ?: snapshot.groups.firstOrNull()
            GlanceTheme { WidgetContent(context, group, snapshot, config, appWidgetId) }
        }
    }

    companion object {
        val GROUP = stringPreferencesKey("group")
    }
}

class GroupWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GroupWidget()
}

@Composable
private fun WidgetContent(context: Context, group: PlannedGroup?, snapshot: HomeSnapshot, config: StudioConfig, appWidgetId: Int) {
    val size = LocalSize.current
    val density = config.density
    val (columns, rows) = GroupPlanner.gridFor(size.width.value.toInt(), size.height.value.toInt(), density)
    val openPanel = Intent(context, FolderPanelActivity::class.java)
        .putExtra(FolderPanelActivity.EXTRA_GROUP, group?.group?.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val edit = Intent(context, EditGroupWidgetActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val colors = themedIconColors(context)

    Column(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(GlanceTheme.colors.widgetBackground).padding(10.dp),
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
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                row.forEach { cell ->
                    when (cell) {
                        is Cell.App -> {
                            val app = snapshot.app(cell.facts) ?: return@forEach
                            val icon = context.studio.icons.icon(app, config.iconStyle, config.iconPack, colors.background, colors.glyph)
                            Column(
                                modifier = GlanceModifier.width(density.cellDp.dp).padding(vertical = 3.dp).clickable(actionStartActivity(app.launchIntent())),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Image(ImageProvider(icon), contentDescription = app.facts.label, modifier = GlanceModifier.size(density.iconDp.dp))
                                if (density.showLabels) {
                                    Text(app.facts.label, maxLines = 1, style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurface))
                                }
                            }
                        }
                        is Cell.More -> Box(
                            modifier = GlanceModifier.width(density.cellDp.dp).height(density.iconDp.dp).clickable(actionStartActivity(openPanel)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+${cell.count}", style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, color = GlanceTheme.colors.primary))
                        }
                    }
                }
            }
        }
    }
}

private sealed interface Cell {
    data class App(val facts: com.charan.padstudio.core.home.AppFacts) : Cell

    data class More(val count: Int) : Cell
}
