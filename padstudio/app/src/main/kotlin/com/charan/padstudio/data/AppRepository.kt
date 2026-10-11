package com.charan.padstudio.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import com.charan.padstudio.core.home.AppFacts
import com.charan.padstudio.core.home.Categorizer
import com.charan.padstudio.core.home.FlowerpotResources
import com.charan.padstudio.core.home.GroupPlanner
import com.charan.padstudio.core.home.Placement
import com.charan.padstudio.core.home.PlannedGroup

/** A launchable activity on this tablet, with the facts the categoriser needs. */
data class InstalledApp(val facts: AppFacts, val info: LauncherActivityInfo) {
    val component: ComponentName get() = info.componentName

    fun launchIntent(): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

/** A consistent view of the installed apps, their groups and why each app is where it is. */
data class HomeSnapshot(
    val apps: List<InstalledApp>,
    val placements: List<Placement>,
    val groups: List<PlannedGroup>,
) {
    private val byKey = apps.associateBy { it.facts.packageName to it.facts.activityName }

    fun app(facts: AppFacts): InstalledApp? = byKey[facts.packageName to facts.activityName]
}

class AppRepository(private val context: Context, private val config: ConfigStore) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val pots by lazy { FlowerpotResources.loadAll() }

    @Volatile private var cached: Pair<Int, HomeSnapshot>? = null

    /** Rebuilt when packages or the user's overrides change; otherwise the cached copy is returned. */
    fun snapshot(force: Boolean = false): HomeSnapshot {
        val key = changeSequence() * 31 + config.config.value.overrides.hashCode()
        cached?.takeIf { !force && it.first == key }?.let { return it.second }
        val apps = installedApps()
        val placements = Categorizer(pots, config.config.value.overrides).placeAll(apps.map { it.facts })
        val snapshot = HomeSnapshot(apps, placements, GroupPlanner.plan(placements))
        cached = key to snapshot
        return snapshot
    }

    /** PackageManager.getChangedPackages sequence; resets at boot, so it is paired with the boot count. */
    fun changeSequence(): Int {
        val boot = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, 0)
        val sequence = context.packageManager.getChangedPackages(0)?.sequenceNumber ?: 0
        return boot * 100_003 + sequence
    }

    fun installedApps(): List<InstalledApp> {
        val intentMatches = intentCategoryMatches()
        return launcherApps.getActivityList(null, Process.myUserHandle()).map { info ->
            val ai = info.applicationInfo
            InstalledApp(
                AppFacts(
                    packageName = info.componentName.packageName,
                    activityName = info.componentName.className,
                    label = info.label.toString(),
                    androidCategory = ai.category.takeIf { it != ApplicationInfo.CATEGORY_UNDEFINED },
                    isGame = ai.category == ApplicationInfo.CATEGORY_GAME,
                    isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    intentCategories = intentMatches[info.componentName.packageName].orEmpty(),
                ),
                info,
            )
        }
    }

    /** Resolves flowerpot's intent rules (APP_BROWSER, APP_FILES, …) against this tablet's apps. */
    private fun intentCategoryMatches(): Map<String, Set<String>> {
        val pm = context.packageManager
        val matches = mutableMapOf<String, MutableSet<String>>()
        for (pot in pots) {
            val intents = pot.intentCategories.map { Intent(Intent.ACTION_MAIN).addCategory(it) } +
                pot.intentActions.map { Intent(it) }
            for (intent in intents) {
                for (resolved in pm.queryIntentActivities(intent, 0)) {
                    matches.getOrPut(resolved.activityInfo.packageName) { mutableSetOf() } += pot.name
                }
            }
        }
        return matches
    }
}
