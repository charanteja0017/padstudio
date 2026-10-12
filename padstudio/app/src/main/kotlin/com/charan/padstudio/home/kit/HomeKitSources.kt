package com.charan.padstudio.home.kit

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.text.format.DateFormat
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.pen.HomeScreenFacts
import com.charan.padstudio.home.FolderPanelActivity
import com.charan.padstudio.home.GroupWidget
import com.charan.padstudio.home.LibraryActivity
import com.charan.padstudio.home.WidgetSize
import com.charan.padstudio.studio
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Everything the Home Kit reads from the tablet or starts on it. Reads run off the main thread and are
// redone on every resume ([refresh]), so a widget placed or edited elsewhere shows when you come back.

/**
 * The group widgets on the home screen, from the three receivers ([WidgetSize]), with each one's Glance state
 * and the sizes the launcher reports (Glance's own reading, the one its Exact layout uses). Null while the
 * first read runs, or if the launcher can't be asked; a failed later read keeps the last good list.
 */
@Composable
internal fun rememberPlacedWidgets(refresh: Int): State<List<PlacedWidget>?> {
    val context = LocalContext.current.applicationContext
    return produceState<List<PlacedWidget>?>(null, refresh) {
        runCatching { readPlacedWidgets(context) }.onSuccess { value = it }
    }
}

private suspend fun readPlacedWidgets(context: Context): List<PlacedWidget> = withContext(Dispatchers.IO) {
    val widgets = AppWidgetManager.getInstance(context)
    val glance = GlanceAppWidgetManager(context)
    WidgetSize.entries
        .flatMap { size -> widgets.getAppWidgetIds(ComponentName(context, size.receiver)).map { id -> size to id } }
        .map { (size, id) ->
            val glanceId = runCatching { glance.getGlanceIdBy(id) }.getOrNull()
            val state = glanceId?.let { runCatching { getAppWidgetState(context, PreferencesGlanceStateDefinition, it) }.getOrNull() }
            val sizes = glanceId?.let { runCatching { glance.getAppWidgetSizes(it) }.getOrNull() }.orEmpty()
            PlacedWidget(
                appWidgetId = id,
                kind = WidgetKind.entries.firstOrNull { it.name == size.name },
                group = state?.get(GroupWidget.GROUP),
                grid = state?.get(GroupWidget.GRID),
                density = state?.get(GroupWidget.DENSITY),
                sizes = sizes.map { SizeDp(it.width.value, it.height.value) },
            )
        }
        .sortedBy { it.appWidgetId }
}

/** What the tablet has recorded about HyperOS's home screen. */
internal sealed interface HomeRecord {
    data object Loading : HomeRecord

    /** No device-facts.json, or one without a readable homeScreen block. */
    data object Missing : HomeRecord

    data class Recorded(val facts: HomeScreenFacts) : HomeRecord
}

/** The recorded home screen, `studio.pen.factsFile()?.homeScreen`, read off the main thread. */
@Composable
internal fun rememberHomeRecord(refresh: Int): State<HomeRecord> {
    val context = LocalContext.current.applicationContext
    return produceState<HomeRecord>(HomeRecord.Loading, refresh) {
        val facts = withContext(Dispatchers.IO) { runCatching { context.studio.pen.factsFile()?.homeScreen }.getOrNull() }
        value = facts?.let { HomeRecord.Recorded(it) } ?: HomeRecord.Missing
    }
}

/** A HyperOS screen's intent: its action, pinned to the launcher's package so no other app can answer it. */
internal fun hyperOsIntent(screen: HyperOsScreen): Intent = Intent(screen.action).setPackage(HYPEROS_LAUNCHER)

/** What resolving a HyperOS screen found, for the link and for Developer details. */
internal data class LinkResolution(val state: LinkState, val component: String?)

/** Resolves every [HyperOsScreen] the way startActivity will (default category only); redone on [refresh]. */
@Composable
internal fun rememberHyperOsLinks(refresh: Int): Map<HyperOsScreen, LinkResolution> {
    val context = LocalContext.current
    return remember(refresh) { HyperOsScreen.entries.associateWith { resolve(context.packageManager, it) } }
}

private fun resolve(packageManager: PackageManager, screen: HyperOsScreen): LinkResolution {
    val info = runCatching { resolveDefault(packageManager, hyperOsIntent(screen)) }.getOrNull()
    val activity = info?.activityInfo ?: return LinkResolution(LinkState.MISSING, null)
    val component = ComponentName(activity.packageName, activity.name).flattenToShortString()
    return LinkResolution(if (activity.exported) LinkState.READY else LinkState.BLOCKED, component)
}

private fun resolveDefault(packageManager: PackageManager, intent: Intent): ResolveInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
    } else {
        @Suppress("DEPRECATION")
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }

/** Opens [screen] in PadStudio's task, so Back returns here; null when it opened, else why it didn't. */
internal fun openHyperOs(context: Context, screen: HyperOsScreen): OpenFailure? = try {
    context.startActivity(hyperOsIntent(screen).forContext(context))
    null
} catch (missing: ActivityNotFoundException) {
    OpenFailure.NOT_FOUND
} catch (refused: SecurityException) {
    OpenFailure.REFUSED
}

/** The folder panel a group widget's header opens, here for [group]. */
internal fun openFolderPanel(context: Context, group: AppGroup) {
    context.startActivity(
        Intent(context, FolderPanelActivity::class.java).putExtra(FolderPanelActivity.EXTRA_GROUP, group.name).forContext(context),
    )
}

/** The library panel: every group, and search. */
internal fun openLibrary(context: Context) {
    context.startActivity(Intent(context, LibraryActivity::class.java).forContext(context))
}

/** True when the launcher accepts pin requests; otherwise MainViewModel.pinWidget explains the manual way. */
internal fun canRequestPin(context: Context): Boolean =
    runCatching { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }.getOrDefault(false)

/** A new task only when there is no activity to start from. */
private fun Intent.forContext(context: Context): Intent = if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else this

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The time for a success snackbar ("14:05"), in the device's 12- or 24-hour style. */
internal fun timeNow(context: Context): String = DateFormat.getTimeFormat(context).format(Date())

/**
 * One 11 sp label line in dp at the current text size (plan §6b), for GroupPlanner.layoutFor. Android's own
 * nonlinear font scale converts it (TypedValue.applyDimension); fontScale is never multiplied.
 */
@Composable
internal fun rememberLabelLineDp(): Float {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) {
        val metrics = resources.displayMetrics
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LABEL_SP, metrics) / metrics.density
    }
}

private const val LABEL_SP = 11f
