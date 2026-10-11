package com.charan.padstudio.pen

import android.content.Context
import android.view.KeyEvent
import android.view.WindowManager
import com.charan.padstudio.core.pen.Binding
import com.charan.padstudio.core.pen.DefaultProfiles
import com.charan.padstudio.core.pen.DeviceFacts
import com.charan.padstudio.core.pen.FactsFile
import com.charan.padstudio.core.pen.GeneratedProfile
import com.charan.padstudio.core.pen.KeyMapperJson
import com.charan.padstudio.core.pen.PenContext
import com.charan.padstudio.core.pen.PenMode
import com.charan.padstudio.core.pen.PenProfile
import com.charan.padstudio.core.pen.PenTrigger
import com.charan.padstudio.core.pen.ProfileGenerator
import com.charan.padstudio.core.pen.ScreenSize
import com.charan.padstudio.data.AppRepository
import com.charan.padstudio.data.ConfigStore
import com.charan.padstudio.data.HomeSnapshot
import com.charan.padstudio.home.LibraryActivity
import java.io.File
import java.security.MessageDigest

/** A generated profile plus the JSON Key Mapper will restore and a short hash to recognise it later. */
data class PenPlan(val contexts: List<PenContext>, val profile: PenProfile, val generated: GeneratedProfile, val json: String, val hash: String)

/** Turns PadStudio's settings and this tablet's facts into a Key Mapper pen profile. */
class PenStudio(private val context: Context, private val config: ConfigStore, private val apps: AppRepository) {

    /** device-facts.json from tools/probe-device.sh: pushed into files/ (run-as) or the app's external files dir. */
    fun factsFile(): FactsFile? = listOfNotNull(File(context.filesDir, FACTS), context.getExternalFilesDir(null)?.let { File(it, FACTS) })
        .firstOrNull { it.exists() }
        ?.let { runCatching { FactsFile.parse(it.readText()) }.getOrNull() }

    fun facts(snapshot: HomeSnapshot): DeviceFacts {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val base = DeviceFacts(
            screen = ScreenSize(bounds.width(), bounds.height()),
            cameras = DefaultProfiles.KNOWN_CAMERAS,
            paging = DefaultProfiles.PAGING,
            notesApp = config.config.value.notesApp,
            libraryComponent = "${context.packageName}/${LibraryActivity::class.java.name}",
            appNames = snapshot.apps.associate { it.facts.packageName to it.facts.label },
        )
        return factsFile()?.applyTo(base) ?: base
    }

    /** Default contexts limited to installed apps; apps without a verified id are matched by label. */
    fun contexts(snapshot: HomeSnapshot): List<PenContext> {
        val installed = snapshot.apps.map { it.facts.packageName }.toSet()
        val byLabel = snapshot.apps.associate { it.facts.label to it.facts.packageName }
        return DefaultProfiles.CONTEXTS.map { context ->
            context.copy(packages = (context.packages.filter { it in installed } + context.labelHints.mapNotNull { byLabel[it] }).distinct())
        }
    }

    fun bindings(): List<Binding> {
        val cfg = config.config.value
        return cfg.bindings ?: DefaultProfiles.bindings(cfg.penKind, cfg.penMode)
    }

    fun plan(snapshot: HomeSnapshot = apps.snapshot()): PenPlan {
        val cfg = config.config.value
        val contexts = contexts(snapshot)
        val profile = PenProfile(contexts, bindings())
        val generated = ProfileGenerator(facts(snapshot)).generate(profile, com.charan.padstudio.core.pen.PenSetup(cfg.penMode, cfg.controls))
        val json = KeyMapperJson.backup(generated)
        return PenPlan(contexts, profile, generated, json, hash(json))
    }

    /**
     * The trigger a pen key event should become. Basic mode records the device descriptor Key Mapper
     * matches on; Expert mode needs the evdev identity (bus/vendor/product) that only the probe sees,
     * so it is taken from device-facts.json.
     */
    fun triggerFor(event: KeyEvent): Result<PenTrigger> {
        val device = event.device ?: return Result.failure(IllegalStateException("The key event has no input device."))
        if (config.config.value.penMode == PenMode.BASIC) {
            return Result.success(PenTrigger.KeyEvent(event.keyCode, event.scanCode, device.descriptor, device.name))
        }
        val candidates = factsFile()?.penDevices.orEmpty()
        val match = candidates.firstOrNull { it.vendor == device.vendorId && it.product == device.productId }
            ?: candidates.singleOrNull()
            ?: return Result.failure(
                IllegalStateException("Expert mode needs the pen's input device from the probe: run tools/probe-device.sh, then import device facts."),
            )
        return Result.success(PenTrigger.Evdev(event.keyCode, event.scanCode, match))
    }

    private fun hash(json: String): String =
        MessageDigest.getInstance("SHA-256").digest(json.toByteArray()).take(6).joinToString("") { "%02x".format(it) }

    companion object {
        const val FACTS = "device-facts.json"
    }
}
