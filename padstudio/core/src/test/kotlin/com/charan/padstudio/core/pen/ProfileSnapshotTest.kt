package com.charan.padstudio.core.pen

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pins the exact bytes of the Key Mapper backup PadStudio hands over (redesign plan §4 "Protected",
 * §13 "Pen behaviour"): for the same config and the same installed apps, the profile must not
 * change while the app around it is redesigned. Each golden in src/test/resources is the file the
 * app writes to cache/profiles/padstudio-pen-profile.json, byte for byte (UTF-8, no final newline),
 * so `shasum -a 256` of a golden is the SHA-256 of that file for the same setup on the tablet, and
 * its first 12 hex digits are the app's PenPlan.hash.
 *
 * [plan] restates the app's PenStudio.plan() (app/.../pen/PenStudio.kt) step for step, with the
 * tablet's inputs fixed in the [Tablet] values below. The goldens therefore pin everything in
 * :core that feeds the JSON: DefaultProfiles, FactsFile.applyTo, ActionResolver, ProfileGenerator
 * (every group and key map name) and KeyMapperJson. If PenStudio.plan() changes, change [plan].
 * On 11 Oct 2026 [plan] was fed the tablet's backed-up config.json, device-facts.json and package
 * list and rebuilt the profile the tablet had sent, byte for byte, apart from five key map names:
 * that profile predates commit 4749fd9, which renamed "Double tap press →" to "Double tap →".
 *
 * A failure means the profile Key Mapper would receive has changed. Only if that is intended,
 * regenerate, review the goldens' diff, and say why in the commit (from the repo root):
 *
 *     JAVA_TOOL_OPTIONS=-Dpadstudio.updateSnapshots=true \
 *         tools/lib/gradle-slot.sh :core:test --tests 'com.charan.padstudio.core.pen.ProfileSnapshotTest' --rerun
 *     tools/lib/gradle-slot.sh :core:test
 *
 * The first command rewrites the goldens and then fails on purpose, so the switch can't be left
 * on unnoticed; the second must pass. Gradle runs tests in a JVM of their own and
 * core/build.gradle.kts doesn't forward `-D` options to it, so the property travels in
 * JAVA_TOOL_OPTIONS, which every JVM reads at start-up. `--rerun` makes Gradle run the test task
 * even when none of its inputs changed (the variable is not one). In an IDE, put
 * -Dpadstudio.updateSnapshots=true in the run configuration's VM options instead.
 */
class ProfileSnapshotTest {

    /** What PenStudio.plan() reads on the tablet. */
    private data class Tablet(
        /** Launchable apps, package to label (LauncherApps, through HomeSnapshot.apps). */
        val apps: Map<String, String>,
        /** Installed without an app-drawer entry; PenStudio asks PackageManager about these. */
        val alsoInstalled: Set<String>,
        /** WindowManager.maximumWindowMetrics.bounds. */
        val window: ScreenSize,
        // files/config.json (StudioConfig): the fields plan() reads.
        val penKind: PenKind,
        val penMode: PenMode,
        val controls: Map<String, PenTrigger>,
        val bindings: List<Binding>?,
        val notesApp: String?,
        val keyInjection: Boolean,
        /** files/device-facts.json from tools/probe-device.sh; null when it was never pushed. */
        val probe: FactsFile?,
    )

    /** [actionTypes]: Key Mapper action types the golden must contain, so it can't silently pin less. */
    private class Snapshot(val file: String, val tablet: Tablet, val actionTypes: Set<String>)

    @Test fun `focus pen pro in expert mode sends the pinned profile`() = assertPinned(FOCUS_PEN_PRO_EXPERT)

    @Test fun `two-button pen in basic mode sends the pinned profile`() = assertPinned(TWO_BUTTON_BASIC)

    @Test fun `the byte comparison notices a one-byte change`() {
        val bytes = plan(TWO_BUTTON_BASIC.tablet).second.toByteArray(Charsets.UTF_8)
        assertNull(difference(bytes, bytes.copyOf()))
        val middle = bytes.size / 2
        val flipped = bytes.copyOf().also { it[middle] = (it[middle].toInt() xor 1).toByte() }
        assertNotNull(difference(bytes, flipped), "one changed byte")
        assertNotNull(difference(bytes, bytes + '\n'.code.toByte()), "a final newline added by an editor")
        assertNotNull(difference(bytes, bytes.copyOf(bytes.size - 1)), "one byte missing")
    }

    private fun assertPinned(snapshot: Snapshot) {
        val (generated, json) = plan(snapshot.tablet)

        // A golden regenerated from a broken fixture would pin nothing, so check the fixture still reaches these.
        val types = Json.parseToJsonElement(json).jsonObject.getValue("keymap_list").jsonArray
            .flatMap { keyMap -> keyMap.jsonObject.getValue("actionList").jsonArray.map { it.jsonObject.getValue("type").jsonPrimitive.content } }
            .toSet()
        assertTrue(types.containsAll(snapshot.actionTypes), "${snapshot.file} no longer reaches ${snapshot.actionTypes - types}; fix the fixture")
        val recorded = snapshot.tablet.probe?.controls.orEmpty().keys + snapshot.tablet.controls.keys
        assertEquals(recorded, generated.keyMaps.map { it.controlId }.toSet(), "${snapshot.file}: every recorded control is mapped")

        // KeyMapperBridge.importIntent writes the JSON with File.writeText, which is UTF-8.
        val now = json.toByteArray(Charsets.UTF_8)
        if (java.lang.Boolean.getBoolean(UPDATE_PROPERTY)) {
            val file = sourceFile(snapshot.file)
            val before = file.takeIf { it.isFile }?.readBytes()
            file.writeBytes(now)
            val what = when {
                before == null -> "created"
                before.contentEquals(now) -> "unchanged"
                else -> "rewritten"
            }
            fail(
                "${snapshot.file} $what (${now.size} bytes, sha256 ${sha256(now)}) because $UPDATE_PROPERTY=true. " +
                    "This run fails on purpose so the switch can't stay on: review the golden's diff, then run :core:test without it.",
            )
        }
        val golden = javaClass.getResourceAsStream("/${snapshot.file}")?.use { it.readBytes() }
            ?: fail("${snapshot.file} is missing from src/test/resources; create it as ProfileSnapshotTest's KDoc says.")
        difference(golden, now)?.let {
            fail(
                "The Key Mapper profile pinned in ${snapshot.file} changed (redesign plan §4 says it must not).\n$it\n" +
                    "If the change is intended, regenerate as ProfileSnapshotTest's KDoc says and explain it in the commit.",
            )
        }
    }

    /** The app's PenStudio.plan(), with [tablet] in place of Android: the same steps in the same order. */
    private fun plan(tablet: Tablet): Pair<GeneratedProfile, String> {
        // contexts(): the default contexts limited to installed apps, plus apps whose label matches a hint.
        val byLabel = tablet.apps.entries.associate { (packageName, label) -> label to packageName }
        val contexts = DefaultProfiles.CONTEXTS.map { context ->
            val installed = context.packages.filter { it in tablet.apps || it in tablet.alsoInstalled }
            context.copy(packages = (installed + context.labelHints.mapNotNull { byLabel[it] }).distinct())
        }
        // facts(): what PadStudio knows itself, with the probe's device-facts.json applied on top.
        val base = DeviceFacts(
            screen = tablet.window,
            cameras = DefaultProfiles.KNOWN_CAMERAS,
            paging = DefaultProfiles.PAGING,
            notesApp = tablet.notesApp,
            canInjectKeys = tablet.keyInjection,
            libraryComponent = LIBRARY_COMPONENT,
            appNames = tablet.apps,
        )
        val facts = tablet.probe?.applyTo(base) ?: base
        // bindings() and plan().
        val bindings = tablet.bindings ?: DefaultProfiles.bindings(tablet.penKind, tablet.penMode)
        val controls = tablet.probe?.controls.orEmpty() + tablet.controls
        val generated = ProfileGenerator(facts).generate(PenProfile(contexts, bindings), PenSetup(tablet.penMode, controls))
        return generated to KeyMapperJson.backup(generated)
    }

    /** Null when the bytes are identical; otherwise sizes, hashes and the first differing line of each. */
    private fun difference(golden: ByteArray, now: ByteArray): String? {
        if (golden.contentEquals(now)) return null
        val at = golden.indices.firstOrNull { it >= now.size || golden[it] != now[it] } ?: golden.size
        val line = (0 until at).count { golden[it] == NEWLINE } + 1
        return buildString {
            appendLine("golden: ${golden.size} bytes, sha256 ${sha256(golden)}")
            appendLine("now:    ${now.size} bytes, sha256 ${sha256(now)}")
            appendLine("first difference at byte $at (line $line): golden ${byteAt(golden, at)}, now ${byteAt(now, at)}")
            appendLine("  golden: ${lineAround(golden, at)}")
            append("  now:    ${lineAround(now, at)}")
        }
    }

    private fun byteAt(bytes: ByteArray, at: Int) = if (at < bytes.size) "0x%02x".format(bytes[at]) else "end of file"

    private fun lineAround(bytes: ByteArray, at: Int): String {
        if (at >= bytes.size) return "<end of file>"
        var start = at
        while (start > 0 && bytes[start - 1] != NEWLINE) start--
        var end = at
        while (end < bytes.size && bytes[end] != NEWLINE) end++
        return String(bytes, start, end - start, Charsets.UTF_8).trim().take(200)
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** This module's src/test/resources, wherever the JVM was started (Gradle starts it in padstudio/core). */
    private fun sourceFile(name: String): File {
        val here = File(System.getProperty("user.dir")).absoluteFile
        val resources = listOf(here, File(here, "core"), File(here, "padstudio/core"))
            .map { File(it, "src/test/resources") }
            .firstOrNull { File(it, "keymapper-schema-v25.json").isFile }
            ?: fail("Can't find core's src/test/resources from $here, so the goldens can't be written.")
        return File(resources, name)
    }

    private companion object {
        const val UPDATE_PROPERTY = "padstudio.updateSnapshots"
        const val NEWLINE = '\n'.code.toByte()

        /** The applicationId, a slash and LibraryActivity's class name, as PenStudio.facts() builds it (a §4 protected name). */
        const val LIBRARY_COMPONENT = "com.charan.padstudio/com.charan.padstudio.home.LibraryActivity"

        /** A launcher's apps: some from every default context, a pen-notes app found by its label, and one unlisted app. */
        val APPS = linkedMapOf(
            "com.android.camera" to "Camera",
            "net.sourceforge.opencamera" to "Open Camera",
            "com.google.android.GoogleCamera" to "Pixel Camera",
            "org.readera" to "ReadEra",
            "com.google.android.apps.docs" to "Drive",
            "com.miui.creation" to "Mi Canvas",
            // Made-up id: the app is found only because its label is one of NATIVE_NOTES.labelHints.
            "com.example.notewise" to "Notewise",
            "com.google.android.keep" to "Keep Notes",
            "com.miui.notes" to "Notes",
            "com.google.android.youtube" to "YouTube",
            "com.spotify.music" to "Spotify",
            "com.whatsapp" to "WhatsApp",
        )

        /** The HyperOS launcher (the Home screen context) has no app-drawer entry of its own. */
        val ALSO_INSTALLED = setOf("com.miui.home")

        /** device-facts.json as tools/lib/make_facts.py writes it; the camera and node values are PenFixtures'. */
        val PROBE = FactsFile(
            screen = ScreenSize(2136, 3200),
            cameras = listOf(
                PenFixtures.FACTS.cameras.getValue("com.android.camera").copy(volumeKeyIsShutter = true),
                PenFixtures.FACTS.cameras.getValue("com.google.android.GoogleCamera"),
            ),
            undoNodes = PenFixtures.FACTS.undoNodes,
            redoNodes = PenFixtures.FACTS.redoNodes,
        )

        /** Like the owner's tablet: HyperOS keeps the pen's keys from apps, so the probe recorded them, not config.json. */
        val FOCUS_PEN_PRO_EXPERT = Snapshot(
            file = "profile-snapshot-focus-pen-pro-expert.json",
            tablet = Tablet(
                apps = APPS,
                alsoInstalled = ALSO_INSTALLED,
                window = PenFixtures.SCREEN,
                penKind = PenKind.FOCUS_PEN_PRO,
                penMode = PenMode.EXPERT,
                controls = emptyMap(),
                bindings = null,
                notesApp = "com.miui.creation",
                keyInjection = true,
                probe = PROBE.copy(controls = PenFixtures.EXPERT_FOCUS_PRO.controls, penKind = PenKind.FOCUS_PEN_PRO, penMode = PenMode.EXPERT),
            ),
            actionTypes = setOf("KEY_EVENT", "TAP_COORDINATE", "SYSTEM_ACTION", "INTENT", "APP"),
        )

        /** Buttons recorded in PadStudio's own window (PenStudio.triggerFor), so they live in config.json. */
        val TWO_BUTTON_BASIC = Snapshot(
            file = "profile-snapshot-two-button-basic.json",
            tablet = Tablet(
                apps = APPS,
                alsoInstalled = ALSO_INSTALLED,
                window = PenFixtures.SCREEN,
                penKind = PenKind.TWO_BUTTON,
                penMode = PenMode.BASIC,
                controls = PenFixtures.BASIC_TWO_BUTTON.controls,
                bindings = null,
                notesApp = "com.miui.creation",
                keyInjection = false,
                probe = PROBE,
            ),
            actionTypes = setOf("INTERACT_UI_ELEMENT", "TAP_COORDINATE", "SWIPE_COORDINATE", "SYSTEM_ACTION", "INTENT", "APP"),
        )
    }
}
