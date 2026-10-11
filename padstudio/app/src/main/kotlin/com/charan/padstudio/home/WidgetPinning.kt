package com.charan.padstudio.home

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.charan.padstudio.core.home.AppGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** "Add Study widget": asks the launcher to pin a group widget that already shows that group. */
object WidgetPinning {
    fun isSupported(context: Context) = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun request(context: Context, group: AppGroup): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        // Mutable: the launcher fills in EXTRA_APPWIDGET_ID when the widget has been placed.
        val callback = PendingIntent.getBroadcast(
            context,
            group.ordinal,
            Intent(context, PinnedWidgetReceiver::class.java).putExtra(FolderPanelActivity.EXTRA_GROUP, group.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return manager.requestPinAppWidget(ComponentName(context, GroupWidgetReceiver::class.java), null, callback)
    }

    fun placedCount(context: Context) =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, GroupWidgetReceiver::class.java)).size
}

class PinnedWidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val group = intent.getStringExtra(FolderPanelActivity.EXTRA_GROUP) ?: return
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(context, glanceId) { it[GroupWidget.GROUP] = group }
                GroupWidget().update(context, glanceId)
            } finally {
                pending.finish()
            }
        }
    }
}
