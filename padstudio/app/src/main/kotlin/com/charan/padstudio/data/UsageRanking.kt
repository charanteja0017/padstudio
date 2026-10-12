package com.charan.padstudio.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.core.content.edit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How the Library and a folder order their apps; the choice is kept in the "panels" preferences. */
enum class PanelSort { MOST_USED, A_Z }

/**
 * "Most used" for the Library and the folders. Android's usage statistics say how long each app was open in
 * the last 30 days, but only once the user has given PadStudio Usage access (a page in Settings, not a
 * dialog). Until then the ranking counts how often an app was opened from PadStudio's own panels.
 *
 * Everything the panels remember lives in the "panels" SharedPreferences: the sort choice under `sort` and one
 * launch counter per package under `launch_<package>`.
 */
object UsageRanking {
    /**
     * [apps] with the most used first. Apps with the same usage, and apps with none, follow A to Z by label,
     * ignoring case; with an empty [usage] that is plain A to Z. [usage] is keyed by package name.
     */
    fun rank(apps: List<InstalledApp>, usage: Map<String, Long>): List<InstalledApp> = apps.sortedWith(
        compareByDescending<InstalledApp> { usage[it.facts.packageName] ?: 0L }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.facts.label },
    )

    /** Whether the user has given PadStudio Usage access (Settings > Usage access). */
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        // Deprecated in the API 37 SDK without a replacement that reads another uid's mode; still the documented check.
        @Suppress("DEPRECATION")
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            // Nobody has decided in Settings: Android then follows the permission itself.
            AppOpsManager.MODE_DEFAULT -> context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    /**
     * How long each package was in the foreground over the last 30 days, in milliseconds; null without
     * Usage access (also if Android takes it back while this runs). A package that was never opened is absent.
     */
    suspend fun usage(context: Context): Map<String, Long>? {
        if (!hasAccess(context)) return null
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            try {
                context.getSystemService(UsageStatsManager::class.java)
                    .queryAndAggregateUsageStats(now - TimeUnit.DAYS.toMillis(WINDOW_DAYS), now)
                    .mapValues { (_, stats) -> stats.totalTimeInForeground }
            } catch (_: SecurityException) {
                null
            }
        }
    }

    /** Counts one launch of [pkg] from a panel; [ownCounts] ranks by these while there is no Usage access. */
    fun recordLaunch(context: Context, pkg: String) {
        synchronized(this) {
            val prefs = prefs(context)
            val key = LAUNCH_PREFIX + pkg
            prefs.edit { putLong(key, prefs.getLong(key, 0L) + 1L) }
        }
    }

    /** The launches [recordLaunch] counted, by package name. */
    fun ownCounts(context: Context): Map<String, Long> = prefs(context).all
        .mapNotNull { (key, value) ->
            if (key.startsWith(LAUNCH_PREFIX) && value is Long) key.removePrefix(LAUNCH_PREFIX) to value else null
        }
        .toMap()

    /** The chosen order; A to Z until the user picks Most used. */
    fun sortMode(context: Context): PanelSort {
        val saved = prefs(context).getString(KEY_SORT, null)
        return PanelSort.entries.firstOrNull { it.name == saved } ?: PanelSort.A_Z
    }

    fun setSortMode(context: Context, mode: PanelSort) {
        prefs(context).edit { putString(KEY_SORT, mode.name) }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

private const val PREFS = "panels"
private const val KEY_SORT = "sort"
private const val LAUNCH_PREFIX = "launch_"
private const val WINDOW_DAYS = 30L
