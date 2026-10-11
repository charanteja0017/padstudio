package com.charan.padstudio.core.pen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Checks the generated backup against the vocabulary extracted from Key Mapper's source
 * (src/test/resources/keymapper-schema-v25.json, made by tools/lib/keymapper_schema.py).
 */
class KeyMapperJsonTest {
    private val schema = Json.parseToJsonElement(
        javaClass.getResource("/keymapper-schema-v25.json")!!.readText(),
    ).jsonObject

    private fun names(key: String) = schema.getValue(key).jsonArray.map { it.jsonPrimitive.content }.toSet()

    private fun backup(setup: PenSetup, kind: PenKind) =
        KeyMapperJson.backupJson(ProfileGenerator(PenFixtures.FACTS).generate(DefaultProfiles.profile(kind, setup.mode), setup))

    private fun assertKeys(obj: JsonObject, allowed: Set<String>, what: String) {
        val unknown = obj.keys - allowed
        assertTrue(unknown.isEmpty(), "$what uses keys Key Mapper does not know: $unknown")
    }

    private fun checkAgainstSchema(root: JsonObject) {
        assertKeys(root, names("backup"), "backup")
        assertEquals(schema.getValue("database_version").jsonPrimitive.int, root.getValue("keymap_db_version").jsonPrimitive.int)

        for (group in root.getValue("groups").jsonArray.map { it.jsonObject }) {
            assertKeys(group, names("group"), "group")
            group.getValue("constraints").jsonArray.forEach { checkConstraint(it.jsonObject) }
        }
        for (keyMap in root.getValue("keymap_list").jsonArray.map { it.jsonObject }) {
            assertKeys(keyMap, names("key_map"), "key map")
            val trigger = keyMap.getValue("trigger").jsonObject
            assertKeys(trigger, names("trigger"), "trigger")
            for (key in trigger.getValue("keys").jsonArray.map { it.jsonObject }) {
                // Key Mapper's TriggerKeyEntity.DESERIALIZER picks the evdev branch when deviceProduct exists.
                if ("deviceProduct" in key) {
                    assertKeys(key, names("evdev_trigger_key"), "evdev trigger key")
                } else {
                    assertKeys(key, names("key_event_trigger_key"), "key event trigger key")
                    assertTrue("deviceId" in key)
                }
            }
            for (action in keyMap.getValue("actionList").jsonArray.map { it.jsonObject }) {
                assertKeys(action, names("action"), "action")
                val type = action.getValue("type").jsonPrimitive.content
                assertTrue(type in names("action_types"), "unknown action type $type")
                if (type == "SYSTEM_ACTION") {
                    val id = action.getValue("data").jsonPrimitive.content
                    assertTrue(id in names("system_action_ids"), "unknown system action $id")
                }
                for (extra in action.getValue("extras").jsonArray.map { it.jsonObject }) {
                    assertKeys(extra, names("extra"), "extra")
                    val id = extra.getValue("id").jsonPrimitive.content
                    assertTrue(id in names("action_extras"), "unknown action extra $id")
                }
            }
            keyMap.getValue("constraintList").jsonArray.forEach { checkConstraint(it.jsonObject) }
        }
    }

    private fun checkConstraint(constraint: JsonObject) {
        assertKeys(constraint, names("constraint"), "constraint")
        val type = constraint.getValue("type").jsonPrimitive.content
        assertTrue(type in names("constraint_types"), "unknown constraint type $type")
        for (extra in constraint.getValue("extras").jsonArray.map { it.jsonObject }) {
            assertTrue(extra.getValue("id").jsonPrimitive.content in names("constraint_extras"))
        }
    }

    @Test fun `every profile only uses names from Key Mapper's schema`() {
        checkAgainstSchema(backup(PenFixtures.BASIC_TWO_BUTTON, PenKind.TWO_BUTTON))
        checkAgainstSchema(backup(PenFixtures.EXPERT_TWO_BUTTON, PenKind.TWO_BUTTON))
        checkAgainstSchema(backup(PenFixtures.EXPERT_FOCUS_PRO, PenKind.FOCUS_PEN_PRO))
    }

    @Test fun `the schema check rejects an unknown field`() {
        val root = backup(PenFixtures.BASIC_TWO_BUTTON, PenKind.TWO_BUTTON)
        val broken = JsonObject(root + ("groups" to JsonArray(listOf(JsonObject(mapOf("constraintMode" to Json.parseToJsonElement("1")))))))
        val failed = runCatching { checkAgainstSchema(broken) }.isFailure
        assertTrue(failed, "a group key Key Mapper does not know must fail the check")
    }

    @Test fun `groups come parent first`() {
        val groups = backup(PenFixtures.BASIC_TWO_BUTTON, PenKind.TWO_BUTTON).getValue("groups").jsonArray.map { it.jsonObject }
        val seen = mutableSetOf<String>()
        for (group in groups) {
            group["parent_uid"]?.jsonPrimitive?.content?.let { assertTrue(it in seen, "parent $it listed after its child") }
            seen += group.getValue("uid").jsonPrimitive.content
        }
        assertEquals(ProfileGenerator.ROOT_GROUP_NAME, groups.first().getValue("name").jsonPrimitive.content)
        assertFalse("parent_uid" in groups.first())
    }

    @Test fun `ui element clicks match by resource id only`() {
        // Key Mapper's matchAccessibilityNode accepts a node whose class name alone matches, so a
        // class name would let any clickable view of that class be clicked instead of the shutter.
        val actions = backup(PenFixtures.BASIC_TWO_BUTTON, PenKind.TWO_BUTTON).getValue("keymap_list").jsonArray
            .flatMap { it.jsonObject.getValue("actionList").jsonArray.map { action -> action.jsonObject } }
            .filter { it.getValue("type").jsonPrimitive.content == "INTERACT_UI_ELEMENT" }
        assertTrue(actions.isNotEmpty())
        for (action in actions) {
            val extras = action.getValue("extras").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
            assertTrue("extra_accessibility_view_resource_id" in extras)
            assertFalse("extra_accessibility_class_name" in extras, "class name would widen the match")
        }
    }

    @Test fun `squeeze with no android key code is detected by scan code`() {
        val keyMaps = backup(PenFixtures.EXPERT_FOCUS_PRO, PenKind.FOCUS_PEN_PRO).getValue("keymap_list").jsonArray
        val pinchKey = keyMaps.map { it.jsonObject.getValue("trigger").jsonObject.getValue("keys").jsonArray.single().jsonObject }
            .first { it.getValue("scanCode").jsonPrimitive.int == 189 }
        assertEquals(2, pinchKey.getValue("flags").jsonPrimitive.int, "EvdevTriggerKeyEntity.FLAG_DETECT_WITH_SCAN_CODE")
        assertEquals(0x5081, pinchKey.getValue("deviceProduct").jsonPrimitive.int)
    }
}
