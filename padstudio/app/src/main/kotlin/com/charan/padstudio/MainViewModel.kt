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
import com.charan.padstudio.data.HomeSnapshot
import com.charan.padstudio.data.IconStyle
import com.charan.padstudio.home.GroupWidget
import com.charan.padstudio.home.WidgetPinning
import com.charan.padstudio.icons.IconRepository
import com.charan.padstudio.looks.WallpaperGenerator
import com.charan.padstudio.pen.KeyMapperBridge
import com.charan.padstudio.pen.PenPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
    private var captureTimeout: Job? = null
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
            adoptDeviceFacts()
            _snapshot.value = studio.apps.snapshot(force)
            replan()
            _health.value = healthChecks()
        }
    }

    /** First time device-facts.json is seen, take its pen kind and mode (measured by the probe). */
    private fun adoptDeviceFacts() {
        if (config.value.factsAdopted) return
        val facts = studio.pen.factsFile() ?: return
        studio.config.update {
            it.copy(
                penKind = facts.penKind ?: it.penKind,
                penMode = facts.penMode ?: it.penMode,
                bindings = null,
                factsAdopted = true,
            )
        }
    }

    fun setKeyInjection(enabled: Boolean) = studio.config.update { it.copy(keyInjection = enabled) }

    /** Debug: hand the profile to Key Mapper as soon as one has been generated. */
    fun applyWhenReady(start: (Intent) -> Unit) {
        viewModelScope.launch {
            plan.filterNotNull().first()
            applyToKeyMapper()?.let(start)
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
        KeyMapperBridge.scheduleResume(studio, CAPTURE_TIMEOUT_MS + RESUME_SAFETY_MARGIN_MS)
        captureTimeout?.cancel()
        captureTimeout = viewModelScope.launch {
            delay(CAPTURE_TIMEOUT_MS)
            cancelCapture()
            _message.value = "No key reached PadStudio, so HyperOS keeps that button for itself. Key Mapper is listening again."
        }
    }

    /** Ends a capture and lets Key Mapper listen again; does nothing when no capture is running. */
    fun cancelCapture() {
        if (_capturing.value == null) return
        captureTimeout?.cancel()
        _capturing.value = null
        KeyMapperBridge.resumeMappings(studio)
        KeyMapperBridge.cancelScheduledResume(studio)
    }

    override fun onCleared() {
        cancelCapture()
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
        // Key Mapper's Replace would wipe every key map and add none.
        if (plan.generated.keyMaps.isEmpty()) {
            _message.value = "Nothing to hand over: this pen has no recorded or measured buttons. Check the pen type."
            return null
        }
        studio.config.update { it.copy(appliedProfileHash = plan.hash, appliedAt = System.currentTimeMillis()) }
        return KeyMapperBridge.importIntent(studio, plan.json)
    }

    /**
     * Key Mapper switches only the key maps directly inside a group, so every generated group is switched.
     * Once the broadcasts are sent, the request is remembered as
     * [com.charan.padstudio.data.StudioConfig.penEnabled]; Key Mapper can't confirm it.
     */
    fun setPenEnabled(enabled: Boolean) {
        val groups = _plan.value?.generated?.groups.orEmpty()
        if (!KeyMapperBridge.isInstalled(studio)) {
            _message.value = "Key Mapper is not installed."
            return
        }
        groups.forEach { KeyMapperBridge.setGroupEnabled(studio, it.uid, enabled) }
        if (groups.isNotEmpty()) studio.config.update { it.copy(penEnabled = enabled) }
        _message.value = if (groups.isEmpty()) "The pen profile isn't ready yet." else "Pen actions ${if (enabled) "on" else "off"}."
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

    /**
     * The grid for widgets set to Default (plan §6b): columns x rows such as "8x4" (GridSpec's form), or
     * null for Auto. A blank spec counts as null. Widgets with their own grid don't change.
     */
    fun setDefaultWidgetGrid(spec: String?) {
        studio.config.update { it.copy(widgetGrid = spec?.trim()?.ifEmpty { null }) }
        updateWidgets()
    }

    fun pinWidget(group: AppGroup, size: com.charan.padstudio.home.WidgetSize = com.charan.padstudio.home.WidgetSize.WIDE) {
        if (!WidgetPinning.request(studio, group, size)) {
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

    /** Looks → "Match wallpaper colours": Material You colours instead of the brand palette. */
    fun setMatchWallpaperColours(enabled: Boolean) {
        studio.config.update { it.copy(matchWallpaperColours = enabled) }
        updateWidgets()
    }

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

    /**
     * Debug: each icon of a group as widgets draw it, into files/icon-debug/ with an index of sources.
     * [spec] is "group" or "group:STYLE" to render another icon style without changing the setting.
     */
    fun dumpIcons(spec: String) {
        val groupName = spec.substringBefore(':')
        val styleOverride = spec.substringAfter(':', "").takeIf { it.isNotEmpty() }?.let { IconStyle.valueOf(it.uppercase()) }
        viewModelScope.launch(Dispatchers.Default) {
            val snapshot = studio.apps.snapshot()
            val group = snapshot.groups.firstOrNull { it.group.name.equals(groupName, ignoreCase = true) } ?: return@launch
            val cfg = config.value.let { if (styleOverride != null) it.copy(iconStyle = styleOverride) else it }
            val colors = com.charan.padstudio.ui.themedIconColors(studio)
            val dir = java.io.File(studio.filesDir, "icon-debug").apply {
                deleteRecursively()
                mkdirs()
            }
            val index = group.apps.mapNotNull { snapshot.app(it) }.map { app ->
                val drawable = studio.icons.drawable(app, cfg.iconStyle, cfg.iconPack, colors.background, colors.glyph)
                val bitmap = androidx.core.graphics.createBitmap(IconRepository.ICON_PX, IconRepository.ICON_PX)
                drawable.setBounds(0, 0, IconRepository.ICON_PX, IconRepository.ICON_PX)
                drawable.draw(android.graphics.Canvas(bitmap))
                java.io.File(dir, "${app.component.packageName}.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                "${app.component.packageName}\t${app.facts.label}\t${studio.icons.source(app, cfg.iconStyle, cfg.iconPack)}"
            }
            java.io.File(dir, "index.tsv").writeText(index.joinToString("\n", postfix = "\n"))
        }
    }

    fun updateWidgets() {
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

    private companion object {
        /** A capture that sees nothing gives up, so Key Mapper is never left paused. */
        const val CAPTURE_TIMEOUT_MS = 15_000L

        /** The alarm only matters if PadStudio died mid-capture, so it fires after the in-app timeout. */
        const val RESUME_SAFETY_MARGIN_MS = 5_000L
    }
}
