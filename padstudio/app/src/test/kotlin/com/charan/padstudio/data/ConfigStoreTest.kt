package com.charan.padstudio.data

import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.core.pen.Binding
import com.charan.padstudio.core.pen.PenIntent
import com.charan.padstudio.core.pen.PenKind
import com.charan.padstudio.core.pen.PenMode
import com.charan.padstudio.core.pen.PenTrigger
import com.charan.padstudio.core.pen.Press
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** config.json across the redesign: old files load with the new defaults, and new files survive a rollback. */
class ConfigStoreTest {
    private val dir: File = Files.createTempDirectory("padstudio-config").toFile()
    private val file = File(dir, "config.json")

    @AfterTest fun deleteFiles() {
        dir.deleteRecursively()
    }

    @Test fun `a config saved before the redesign keeps every setting and gets the new defaults`() {
        file.writeText(SAVED_BEFORE_REDESIGN)

        val loaded = ConfigStore(file).config.value

        // Every old field differs from its default, so this can only pass through a real decode, never
        // through ConfigStore's fall-back to StudioConfig().
        assertEquals(BEFORE_REDESIGN, loaded)
        assertEquals(true, loaded.penEnabled)
        assertEquals(null, loaded.appliedAt)
        assertEquals(false, loaded.matchWallpaperColours)
        assertEquals(null, loaded.widgetGrid)
    }

    @Test fun `the new settings are saved and read back`() {
        file.writeText(SAVED_BEFORE_REDESIGN)
        val store = ConfigStore(file)

        store.update { WITH_NEW_SETTINGS }

        assertEquals(WITH_NEW_SETTINGS, store.config.value)
        assertEquals(WITH_NEW_SETTINGS, ConfigStore(file).config.value)
        // encodeDefaults writes every key, under these names (install.sh and debug tools read the file).
        assertEquals(ALL_KEYS, Json.parseToJsonElement(file.readText()).jsonObject.keys)
    }

    @Test fun `the build before the redesign still reads a config with the new settings`() {
        ConfigStore(file).update { WITH_NEW_SETTINGS }

        val readByOldBuild = OLD_BUILD_JSON.decodeFromString(StudioConfigBeforeRedesign.serializer(), file.readText())

        assertEquals(
            StudioConfigBeforeRedesign(
                penMode = PenMode.EXPERT,
                penKind = PenKind.FOCUS_PEN_PRO,
                controls = BEFORE_REDESIGN.controls,
                bindings = BEFORE_REDESIGN.bindings,
                notesApp = "com.example.notes",
                overrides = mapOf("com.example.game" to AppGroup.GAMES),
                density = Density.DENSE,
                iconStyle = IconStyle.ORIGINAL,
                iconPack = "com.example.iconpack",
                appliedProfileHash = "cdbc98bc0674",
                wallpaperApplied = true,
                factsAdopted = true,
                keyInjection = true,
            ),
            readByOldBuild,
        )
    }

    @Test fun `a file that can't be read gives the defaults`() {
        file.writeText("""{ "penMode": "NO_SUCH_MODE" }""")

        assertEquals(StudioConfig(), ConfigStore(file).config.value)
    }

    private companion object {
        /** files/config.json as the build before the redesign wrote it: same keys, order and layout. */
        val SAVED_BEFORE_REDESIGN = """
            {
                "penMode": "EXPERT",
                "penKind": "FOCUS_PEN_PRO",
                "controls": {
                    "pinch": {
                        "type": "key_event",
                        "keyCode": 0,
                        "scanCode": 189,
                        "deviceDescriptor": "a1b2c3d4",
                        "deviceName": "Xiaomi Focus Pen Pro"
                    }
                },
                "bindings": [
                    {
                        "contextId": "everywhere",
                        "control": "pinch",
                        "press": "SHORT",
                        "intent": "SCREENSHOT"
                    }
                ],
                "notesApp": "com.example.notes",
                "overrides": {
                    "com.example.game": "GAMES"
                },
                "density": "DENSE",
                "iconStyle": "ORIGINAL",
                "iconPack": "com.example.iconpack",
                "appliedProfileHash": "cdbc98bc0674",
                "wallpaperApplied": true,
                "factsAdopted": true,
                "keyInjection": true
            }
        """.trimIndent()

        val BEFORE_REDESIGN = StudioConfig(
            penMode = PenMode.EXPERT,
            penKind = PenKind.FOCUS_PEN_PRO,
            controls = mapOf("pinch" to PenTrigger.KeyEvent(0, 189, "a1b2c3d4", "Xiaomi Focus Pen Pro")),
            bindings = listOf(Binding("everywhere", "pinch", Press.SHORT, PenIntent.SCREENSHOT)),
            notesApp = "com.example.notes",
            overrides = mapOf("com.example.game" to AppGroup.GAMES),
            density = Density.DENSE,
            iconStyle = IconStyle.ORIGINAL,
            iconPack = "com.example.iconpack",
            appliedProfileHash = "cdbc98bc0674",
            wallpaperApplied = true,
            factsAdopted = true,
            keyInjection = true,
        )

        /** Every new field away from its default. */
        val WITH_NEW_SETTINGS = BEFORE_REDESIGN.copy(
            penEnabled = false,
            appliedAt = 1_760_191_500_000L,
            matchWallpaperColours = true,
            widgetGrid = "8x4",
        )

        val ALL_KEYS = setOf(
            "penMode", "penKind", "controls", "bindings", "notesApp", "overrides", "density", "iconStyle", "iconPack",
            "appliedProfileHash", "wallpaperApplied", "factsAdopted", "keyInjection",
            "penEnabled", "appliedAt", "matchWallpaperColours", "widgetGrid",
        )

        /** ConfigStore's Json in the build before the redesign. */
        val OLD_BUILD_JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
    }
}

/** StudioConfig exactly as the build before the redesign declared it (commit 07c10d8), to test a rollback. */
@Serializable
private data class StudioConfigBeforeRedesign(
    val penMode: PenMode = PenMode.BASIC,
    val penKind: PenKind = PenKind.TWO_BUTTON,
    val controls: Map<String, PenTrigger> = emptyMap(),
    val bindings: List<Binding>? = null,
    val notesApp: String? = null,
    val overrides: Map<String, AppGroup> = emptyMap(),
    val density: Density = Density.COMPACT,
    val iconStyle: IconStyle = IconStyle.THEMED,
    val iconPack: String? = null,
    val appliedProfileHash: String? = null,
    val wallpaperApplied: Boolean = false,
    val factsAdopted: Boolean = false,
    val keyInjection: Boolean = false,
)
