package com.charan.padstudio.data

import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.core.pen.Binding
import com.charan.padstudio.core.pen.PenKind
import com.charan.padstudio.core.pen.PenMode
import com.charan.padstudio.core.pen.PenTrigger
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class IconStyle(val label: String) {
    THEMED("Themed"),
    ICON_PACK("Icon pack"),
    ORIGINAL("Original"),
}

/**
 * Everything PadStudio remembers, in files/config.json. tools/rollback.sh saves this file with
 * `run-as` before uninstalling and tools/install.sh writes it back, so a reinstall keeps it.
 *
 * Fields are only ever added, each with a default, and never renamed: an older config.json then loads
 * with the defaults, and an older PadStudio (also `ignoreUnknownKeys`) reads a newer file (plan §4).
 */
@Serializable
data class StudioConfig(
    val penMode: PenMode = PenMode.BASIC,
    val penKind: PenKind = PenKind.TWO_BUTTON,
    /** Recorded pen controls by PenControl id. */
    val controls: Map<String, PenTrigger> = emptyMap(),
    /** Null means "the default profile for this pen and mode". */
    val bindings: List<Binding>? = null,
    val notesApp: String? = null,
    /** Package → group chosen on the Organize screen (HIDDEN hides the app everywhere). */
    val overrides: Map<String, AppGroup> = emptyMap(),
    val density: Density = Density.COMPACT,
    val iconStyle: IconStyle = IconStyle.THEMED,
    val iconPack: String? = null,
    /** Hash of the last profile handed to Key Mapper; the setup screen compares it to the current one. */
    val appliedProfileHash: String? = null,
    val wallpaperApplied: Boolean = false,
    /** Pen kind and mode were taken from device-facts.json once; the user's later choices win. */
    val factsAdopted: Boolean = false,
    /** The user turned on Key Mapper's "key event actions use system bridge" (Expert) setting. */
    val keyInjection: Boolean = false,
    /**
     * Pen actions on or off, as last requested from PadStudio. Key Mapper can't be asked, so this is
     * the request, not an observed state (plan §7).
     */
    val penEnabled: Boolean = true,
    /**
     * When the profile behind [appliedProfileHash] was handed to Key Mapper, in epoch ms. Null when unknown:
     * nothing handed over yet, or handed over by a build older than this field.
     */
    val appliedAt: Long? = null,
    /** Looks → "Match wallpaper colours": Material You colours instead of the brand palette. */
    val matchWallpaperColours: Boolean = false,
    /** Grid for widgets set to Default, as columns x rows ("8x4"); null means Auto (plan §6b). */
    val widgetGrid: String? = null,
)

class ConfigStore(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val state = MutableStateFlow(load())
    val config: StateFlow<StudioConfig> = state.asStateFlow()

    fun update(transform: (StudioConfig) -> StudioConfig) {
        synchronized(this) {
            val next = transform(state.value)
            if (next == state.value) return
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(StudioConfig.serializer(), next))
            temp.renameTo(file)
            state.value = next
        }
    }

    private fun load(): StudioConfig = runCatching {
        json.decodeFromString(StudioConfig.serializer(), file.readText())
    }.getOrDefault(StudioConfig())
}
