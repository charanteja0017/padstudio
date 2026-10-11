package com.charan.padstudio

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.view.KeyEvent
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.core.pen.Binding
import com.charan.padstudio.core.pen.PenIntent
import com.charan.padstudio.core.pen.PenKind
import com.charan.padstudio.core.pen.PenMode
import com.charan.padstudio.core.pen.Press
import com.charan.padstudio.core.pen.ProfileGenerator
import com.charan.padstudio.data.HomeSnapshot
import com.charan.padstudio.data.IconStyle
import com.charan.padstudio.home.GroupWidget
import com.charan.padstudio.home.WidgetPinning
import com.charan.padstudio.icons.IconRepository
import com.charan.padstudio.looks.WallpaperGenerator
import com.charan.padstudio.pen.KeyMapperBridge
import com.charan.padstudio.pen.PenPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One line on the Setup screen: what to check, whether it's fine, and how to fix it. */
data class HealthCheck(val title: String, val ok: Boolean?, val detail: String, val fix: String? = null, val fixIntent: Intent? = null)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val studio = application as PadStudioApp
    val config = studio.config.config

    private val _snapshot = MutableStateFlow<HomeSnapshot?>(null)
    val snapshot: StateFlow<HomeSnapshot?> = _snapshot.asStateFlow()
    private val _plan = MutableStateFlow<PenPlan?>(null)
    val plan: StateFlow<PenPlan?> = _plan.asStateFlow()
    private val _capturing = MutableStateFlow<String?>(null)
    val capturing: StateFlow<String?> = _capturing.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _health = MutableStateFlow<List<HealthCheck>>(emptyList())
    val health: StateFlow<List<HealthCheck>> = _health.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { config.collect { replan() } }
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.Default) {
            _snapshot.value = studio.apps.snapshot(force)
            replan()
            _health.value = healthChecks()
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun replan() {
        val snapshot = _snapshot.value ?: return
        _plan.value = runCatching { studio.pen.plan(snapshot) }.onFailure { _message.value = it.message }.getOrNull()
    }

    // --- Pen -----------------------------------------------------------------------------------

    fun setPenMode(mode: PenMode) = studio.config.update { it.copy(penMode = mode, controls = emptyMap(), bindings = null) }

    fun setPenKind(kind: PenKind) = studio.config.update { it.copy(penKind = kind, controls = emptyMap(), bindings = null) }

    fun setNotesApp(packageName: String?) = studio.config.update { it.copy(notesApp = packageName) }

    /** Next pen key pressed in PadStudio's window becomes [controlId]; Key Mapper is paused so it can't eat it. */
    fun startCapture(controlId: String) {
        _capturing.value = controlId
        KeyMapperBridge.pauseMappings(studio)
    }

    fun cancelCapture() {
        _capturing.value = null
        KeyMapperBridge.resumeMappings(studio)
    }

    /** Called from MainActivity.dispatchKeyEvent; true means the event was taken by the wizard. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val controlId = _capturing.value ?: return false
        if (event.action != KeyEvent.ACTION_UP) return true
        studio.pen.triggerFor(event)
            .onSuccess { trigger -> studio.config.update { it.copy(controls = it.controls + (controlId to trigger)) } }
            .onFailure { _message.value = it.message }
        cancelCapture()
        return true
    }

    fun forgetControl(controlId: String) = studio.config.update { it.copy(controls = it.controls - controlId) }

    fun setBinding(contextId: String, control: String, press: Press, intent: PenIntent?) {
        val current = studio.pen.bindings().filterNot { it.contextId == contextId && it.control == control && it.press == press }
        val next = if (intent == null) current else current + Binding(contextId, control, press, intent)
        studio.config.update { it.copy(bindings = next) }
    }

    fun resetBindings() = studio.config.update { it.copy(bindings = null) }

    /** Remembers what was handed over and returns the intent that opens Key Mapper's restore dialog. */
    fun applyToKeyMapper(): Intent? {
        val plan = _plan.value ?: return null
        if (!KeyMapperBridge.isInstalled(studio)) {
            _message.value = "Install Key Mapper first (tools/install.sh keymapper)."
            return null
        }
        studio.config.update { it.copy(appliedProfileHash = plan.hash) }
        return KeyMapperBridge.importIntent(studio, plan.json)
    }

    fun setPenEnabled(enabled: Boolean) {
        val sent = KeyMapperBridge.setGroupEnabled(studio, ProfileGenerator.uid("group:root"), enabled)
        _message.value = if (sent) "Pen actions ${if (enabled) "on" else "off"}." else "Key Mapper is not installed."
    }

    // --- Home ----------------------------------------------------------------------------------

    fun setDensity(density: Density) {
        studio.config.update { it.copy(density = density) }
        updateWidgets()
    }

    fun move(packageName: String, group: AppGroup) {
        studio.config.update { it.copy(overrides = it.overrides + (packageName to group)) }
        refresh(force = true)
        updateWidgets()
    }

    fun resetOverrides() {
        studio.config.update { it.copy(overrides = emptyMap()) }
        refresh(force = true)
        updateWidgets()
    }

    fun pinWidget(group: AppGroup) {
        if (!WidgetPinning.request(studio, group)) {
            _message.value = "This launcher doesn't accept requests: long-press the home screen → Widgets → PadStudio → App group."
        }
    }

    // --- Looks ---------------------------------------------------------------------------------

    fun setIconStyle(style: IconStyle, pack: String? = config.value.iconPack) {
        studio.config.update { it.copy(iconStyle = style, iconPack = pack) }
        studio.icons.clear()
        updateWidgets()
    }

    fun iconPacks() = studio.icons.installedPacks()

    fun applyWallpaper(seed: Long, dark: Boolean, accent: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val bitmap = WallpaperGenerator.render(WallpaperGenerator.longSide(studio), seed, accent, dark)
            runCatching { WallpaperGenerator.apply(studio, bitmap) }
                .onSuccess {
                    studio.config.update { it.copy(wallpaperApplied = true) }
                    _message.value = "Wallpaper set on home and lock screen."
                }
                .onFailure { _message.value = "Couldn't set the wallpaper: ${it.message}" }
        }
    }

    private fun updateWidgets() {
        viewModelScope.launch { withContext(Dispatchers.Default) { GroupWidget().updateAll(studio) } }
    }

    // --- Setup ---------------------------------------------------------------------------------

    private fun installed(packageName: String) = runCatching { studio.packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA) }.isSuccess

    private fun healthChecks(): List<HealthCheck> {
        val cfg = config.value
        val keyMapper = KeyMapperBridge.versionName(studio)
        val plan = _plan.value
        return listOf(
            HealthCheck(
                "Key Mapper installed",
                keyMapper != null,
                keyMapper?.let { "Version $it" } ?: "Runs the pen buttons. Install with tools/install.sh keymapper.",
            ),
            HealthCheck(
                "Key Mapper can see the pen",
                KeyMapperBridge.isAccessibilityEnabled(studio),
                "Its accessibility service must be on. If Android says \"Restricted setting\": App info → ⋮ → Allow restricted settings.",
                "Open accessibility settings",
                Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ),
            HealthCheck(
                "Pen profile is current",
                plan != null && cfg.appliedProfileHash == plan.hash,
                if (cfg.appliedProfileHash == null) "Not applied yet: Pen → Apply to Key Mapper." else "Applied ${cfg.appliedProfileHash}; current ${plan?.hash}.",
            ),
            HealthCheck(
                "HyperOS keeps Key Mapper running",
                null,
                "Can't be read by apps: set Autostart on, Battery saver → No restrictions, and lock Key Mapper in Recents.",
                "Open Key Mapper's app info",
                KeyMapperBridge.appDetailsIntent(),
            ),
            HealthCheck(
                "Device facts imported",
                studio.pen.factsFile() != null,
                "Optional. Adds camera shutter buttons and Expert-mode pen devices found by tools/probe-device.sh.",
            ),
            HealthCheck("Group widgets on the home screen", WidgetPinning.placedCount(studio) > 0, "${WidgetPinning.placedCount(studio)} placed."),
            HealthCheck(
                "Themed icon source",
                installed(IconRepository.LAWNICONS) || Android13Monochrome.available,
                if (installed(IconRepository.LAWNICONS)) "Lawnicons installed." else "Using Android's own monochrome icons; install Lawnicons for more coverage.",
            ),
            HealthCheck("PadStudio wallpaper", cfg.wallpaperApplied, if (cfg.wallpaperApplied) "Applied." else "Optional: Looks → Apply wallpaper."),
        )
    }

    private object Android13Monochrome {
        val available = android.os.Build.VERSION.SDK_INT >= 33
    }
}
