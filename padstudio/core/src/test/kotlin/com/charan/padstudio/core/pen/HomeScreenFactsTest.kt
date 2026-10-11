package com.charan.padstudio.core.pen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The optional homeScreen block of device-facts.json (redesign plan §6a). pad8-device-facts-2026-10-11.json
 * is docs/device/device-facts.json as committed at 18e4a71 (gate G5); pad8-device-facts.json is an
 * older file from before the block existed.
 */
class HomeScreenFactsTest {
    private fun resource(name: String) = javaClass.getResource(name)!!.readText()

    private val committed = resource("/pad8-device-facts-2026-10-11.json")

    @Test fun `the committed facts carry the recorded 6 x 4 home screen`() {
        val facts = FactsFile.parse(committed)
        val home = assertNotNull(facts.homeScreen)
        assertEquals(6, home.columns)
        assertEquals(4, home.rows)
        assertTrue(home.namesHidden)
        assertEquals("landscape", home.orientation)
        assertEquals("2026-10-11", home.recordedAt)
        assertTrue(home.source.startsWith("Gate G5"), home.source)
        assertEquals(false, home.hyperosGridSetting)
        assertEquals(5, home.hyperosOptions.size)
        assertTrue("Don't show text" in home.hyperosOptions)
        // The pen facts beside it are untouched.
        assertEquals(PenKind.FOCUS_PEN_PRO, facts.penKind)
        assertEquals(PenMode.EXPERT, facts.penMode)
        assertEquals(setOf("pinch", "slide_up", "slide_down", "double_tap"), facts.controls.keys)
    }

    @Test fun `facts from before the block parse with no home screen`() {
        val old = FactsFile.parse(resource("/pad8-device-facts.json"))
        assertNull(old.homeScreen)
        assertEquals(setOf("pinch", "slide_up", "slide_down"), old.controls.keys)
        assertNull(FactsFile.parse("{}").homeScreen)
        assertNull(FactsFile.parse("""{"homeScreen": null}""").homeScreen)
    }

    @Test fun `only what the writer requires is required, and new keys are ignored`() {
        val minimal = buildJsonObject {
            put("columns", 6)
            put("rows", 5)
            put("namesHidden", false)
            put("recordedAt", "2026-10-12")
            put("source", "test")
            put("iconSize", "large") // a key a later writer might add
        }
        val home = assertNotNull(FactsFile.parse(withHomeScreen(minimal)).homeScreen)
        assertEquals(HomeScreenFacts(columns = 6, rows = 5, namesHidden = false, recordedAt = "2026-10-12", source = "test"), home)
        assertNull(home.orientation)
        assertNull(home.hyperosGridSetting, "not recorded is not the same as no setting")
        assertEquals(emptyList(), home.hyperosOptions)
    }

    @Test fun `a malformed block reads as none and never costs the pen facts`() {
        val good = FactsFile.parse(committed)
        val recorded = Json.parseToJsonElement(committed).jsonObject.getValue("homeScreen").jsonObject
        val broken: Map<String, JsonElement> = mapOf(
            "columns as text" to JsonObject(recorded + ("columns" to JsonPrimitive("six"))),
            "no columns" to JsonObject(recorded - "columns"),
            "no rows" to JsonObject(recorded - "rows"),
            "no date" to JsonObject(recorded - "recordedAt"),
            "no source" to JsonObject(recorded - "source"),
            "names hidden as text" to JsonObject(recorded + ("namesHidden" to JsonPrimitive("yes"))),
            "zero columns" to JsonObject(recorded + ("columns" to JsonPrimitive(0))),
            "negative rows" to JsonObject(recorded + ("rows" to JsonPrimitive(-4))),
            "options as text" to JsonObject(recorded + ("hyperosOptions" to JsonPrimitive("Don't show text"))),
            "a string" to JsonPrimitive("6x4"),
            "a number" to JsonPrimitive(6),
            "an array" to Json.parseToJsonElement("[6, 4]"),
        )
        for ((what, block) in broken) {
            val facts = FactsFile.parse(withHomeScreen(block))
            assertNull(facts.homeScreen, what)
            assertEquals(good.copy(homeScreen = null), facts, "$what: everything else parses as before")
        }
        // The same edits, undone, parse again: the test above isn't failing for some other reason.
        assertEquals(good, FactsFile.parse(withHomeScreen(recorded)))
        assertEquals(JsonNull, Json.parseToJsonElement(withHomeScreen(JsonNull)).jsonObject["homeScreen"])
    }

    private fun withHomeScreen(block: JsonElement): String =
        JsonObject(Json.parseToJsonElement(committed).jsonObject + ("homeScreen" to block)).toString()
}
