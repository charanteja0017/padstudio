package com.charan.padstudio.pen

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Talks to upstream Key Mapper through what it exports (keymapperorg/KeyMapper 4.6.1):
 * - RestoreKeyMapsActivity: VIEW of a content:// application/json backup → restore dialog
 * - api.PauseMappingsBroadcastReceiver: ACTION_PAUSE/RESUME_MAPPINGS
 * - api.EnableGroupBroadcastReceiver: ACTION_ENABLE/DISABLE_GROUP + EXTRA_GROUP_UID
 * Names are copied from api/src/main/java/.../api/Api.kt and the api module's manifest.
 */
object KeyMapperBridge {
    const val PACKAGE = "io.github.sds100.keymapper"
    private const val API = "io.github.sds100.keymapper.api"
    const val SERVICE = "$PACKAGE/$PACKAGE.system.accessibility.MyAccessibilityService"
    private const val EXTRA_GROUP_UID = "$PACKAGE.EXTRA_GROUP_UID"

    fun versionName(context: Context): String? = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0).versionName
    }.getOrNull()

    fun isInstalled(context: Context) = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
    }.isSuccess

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { it.startsWith("$PACKAGE/") }
    }

    fun pauseMappings(context: Context) = broadcast(context, "$PACKAGE.ACTION_PAUSE_MAPPINGS", "$API.PauseMappingsBroadcastReceiver")

    fun resumeMappings(context: Context) = broadcast(context, "$PACKAGE.ACTION_RESUME_MAPPINGS", "$API.PauseMappingsBroadcastReceiver")

    /**
     * Resumes Key Mapper after [delayMs] even if PadStudio's process has died by then (a crash
     * mid-capture). A force-stop of PadStudio cancels it too; its next capture cleans up instead.
     */
    fun scheduleResume(context: Context, delayMs: Long) {
        context.getSystemService(AlarmManager::class.java)
            .set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delayMs, resumeAlarm(context))
    }

    fun cancelScheduledResume(context: Context) = context.getSystemService(AlarmManager::class.java).cancel(resumeAlarm(context))

    private fun resumeAlarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent("$PACKAGE.ACTION_RESUME_MAPPINGS").setComponent(ComponentName(PACKAGE, "$API.PauseMappingsBroadcastReceiver")),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun setGroupEnabled(context: Context, groupUid: String, enabled: Boolean) = broadcast(
        context,
        if (enabled) "$PACKAGE.ACTION_ENABLE_GROUP" else "$PACKAGE.ACTION_DISABLE_GROUP",
        "$API.EnableGroupBroadcastReceiver",
    ) { putExtra(EXTRA_GROUP_UID, groupUid) }

    /** Writes the backup to cache/profiles and returns the intent that opens Key Mapper's restore dialog. */
    fun importIntent(context: Context, json: String): Intent {
        val dir = File(context.cacheDir, "profiles").apply { mkdirs() }
        val file = File(dir, "padstudio-pen-profile.json").apply { writeText(json) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/json")
            .setPackage(PACKAGE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun appDetailsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(android.net.Uri.fromParts("package", PACKAGE, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun launchIntent(context: Context): Intent? = context.packageManager.getLaunchIntentForPackage(PACKAGE)

    private fun broadcast(context: Context, action: String, receiver: String, extras: Intent.() -> Unit = {}): Boolean {
        if (!isInstalled(context)) return false
        val intent = Intent(action).setComponent(ComponentName(PACKAGE, receiver)).apply(extras)
        context.sendBroadcast(intent)
        return true
    }
}
