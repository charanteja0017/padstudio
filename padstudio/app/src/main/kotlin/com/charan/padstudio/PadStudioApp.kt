package com.charan.padstudio

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.charan.padstudio.data.AppRepository
import com.charan.padstudio.data.ConfigStore
import com.charan.padstudio.home.PackageWatcherWorker
import com.charan.padstudio.icons.IconRepository
import com.charan.padstudio.pen.PenStudio
import java.io.File
import java.util.concurrent.TimeUnit

class PadStudioApp : Application() {
    val config by lazy { ConfigStore(File(filesDir, "config.json")) }
    val apps by lazy { AppRepository(this, config) }
    val icons by lazy { IconRepository(this) }
    val pen by lazy { PenStudio(this, config, apps) }

    override fun onCreate() {
        super.onCreate()
        // App installs can't be heard in the background (no manifest PACKAGE_ADDED); poll instead.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "package-watcher",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PackageWatcherWorker>(15, TimeUnit.MINUTES).build(),
        )
    }
}

val Context.studio: PadStudioApp get() = applicationContext as PadStudioApp
