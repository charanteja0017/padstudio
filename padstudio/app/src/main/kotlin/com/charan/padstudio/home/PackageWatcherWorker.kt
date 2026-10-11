package com.charan.padstudio.home

import android.content.Context
import androidx.core.content.edit
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.charan.padstudio.studio

/**
 * Refreshes the group widgets when apps were installed or removed. PackageManager.getChangedPackages
 * is a cheap poll; manifest receivers can't get ACTION_PACKAGE_ADDED since Android 8.
 */
class PackageWatcherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("watcher", Context.MODE_PRIVATE)
        val current = applicationContext.studio.apps.changeSequence()
        if (prefs.getInt(KEY, -1) != current) {
            applicationContext.studio.apps.snapshot(force = true)
            GroupWidget().updateAll(applicationContext)
            prefs.edit { putInt(KEY, current) }
        }
        return Result.success()
    }

    private companion object {
        const val KEY = "sequence"
    }
}
