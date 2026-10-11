package com.charan.padstudio.core.pen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Writes a Key Mapper backup ("data.json" format, restorable through Key Mapper's
 * RestoreKeyMapsActivity). Every key and value below comes from keymapperorg/KeyMapper's entity
 * classes at database version 25; core/src/test/resources/keymapper-schema-v25.json is the
 * extracted vocabulary the tests check against. Null fields are left out, as Gson does.
 */
object KeyMapperJson {
    const val DATABASE_VERSION = 25

    /** versionCode of keymapper-4.6.1-foss.apk (aapt2 dump badging). */
    const val KEY_MAPPER_VERSION_CODE = 286

    private const val TRIGGER_MODE_UNDEFINED = 2
    private const val KEY_EVENT_FLAG_DETECT_WITH_SCAN_CODE = 4
    private const val EVDEV_FLAG_DETECT_WITH_SCAN_CODE = 2
    private const val KEYCODE_UNKNOWN = 0

    private val pretty = Json { prettyPrint = true }

    fun backup(profile: GeneratedProfile, appVersion: Int = KEY_MAPPER_VERSION_CODE): String =
        pretty.encodeToString(JsonElement.serializer(), backupJson(profile, appVersion))

    fun backupJson(profile: GeneratedProfile, appVersion: Int = KEY_MAPPER_VERSION_CODE): JsonObject =
        buildJsonObject {
            put("keymap_db_version", DATABASE_VERSION)
            put("app_version", appVersion)
            put("keymap_list", JsonArray(profile.keyMaps.map(::keyMap)))
            // Parents before children, so a restore never meets an unknown parent_uid.
            put("groups", JsonArray(parentsFirst(profile.groups).map(::group)))
        }

    private fun parentsFirst(groups: List<KmGroup>): List<KmGroup> {
        val ordered = mutableListOf<KmGroup>()
        val remaining = groups.toMutableList()
        while (remaining.isNotEmpty()) {
            val next = remaining.firstOrNull { g -> g.parentUid == null || ordered.any { it.uid == g.parentUid } }
                ?: remaining.first() // orphan: keep it, Key Mapper ignores key maps under missing groups
            ordered += next
            remaining -= next
        }
        return ordered
    }

    private fun group(group: KmGroup) = buildJsonObject {
        put("uid", group.uid)
        put("name", group.name)
        put("constraints", constraints(group.constraints, owner = group.uid))
        put("constraint_mode", group.mode.value)
        group.parentUid?.let { put("parent_uid", it) }
    }

    private fun keyMap(keyMap: KmKeyMap) = buildJsonObject {
        put("id", 0)
        put("trigger", trigger(keyMap))
        put("actionList", JsonArray(keyMap.actions.mapIndexed { i, action -> action(action, ProfileGenerator.uid("action:${keyMap.uid}:$i")) }))
        put("constraintList", constraints(keyMap.constraints, owner = keyMap.uid))
        put("constraintMode", keyMap.mode.value)
        putJsonArray("constraintGroups") {}
        put("flags", 0)
        put("isEnabled", true)
        put("uid", keyMap.uid)
        put("group_uid", keyMap.groupUid)
        put("name", keyMap.name)
    }

    private fun trigger(keyMap: KmKeyMap) = buildJsonObject {
        putJsonArray("keys") { add(triggerKey(keyMap)) }
        putJsonArray("extras") {}
        put("mode", TRIGGER_MODE_UNDEFINED)
        put("flags", 0)
    }

    private fun triggerKey(keyMap: KmKeyMap): JsonObject = buildJsonObject {
        val uid = ProfileGenerator.uid("triggerkey:${keyMap.uid}")
        when (val t = keyMap.trigger) {
            is PenTrigger.KeyEvent -> {
                put("keyCode", t.keyCode)
                put("deviceId", t.deviceDescriptor)
                put("deviceName", t.deviceName)
                put("clickType", keyMap.press.clickType)
                put("flags", if (t.keyCode == KEYCODE_UNKNOWN && t.scanCode != null) KEY_EVENT_FLAG_DETECT_WITH_SCAN_CODE else 0)
                put("uid", uid)
                t.scanCode?.let { put("scanCode", it) }
            }
            is PenTrigger.Evdev -> {
                put("keyCode", t.keyCode)
                put("scanCode", t.scanCode)
                put("deviceName", t.device.name)
                put("deviceBus", t.device.bus)
                put("deviceVendor", t.device.vendor)
                put("deviceProduct", t.device.product)
                put("clickType", keyMap.press.clickType)
                // Evdev codes are what the probe measured; matching on them avoids depending on
                // how a key layout maps them to Android key codes.
                put("flags", EVDEV_FLAG_DETECT_WITH_SCAN_CODE)
                put("uid", uid)
            }
        }
    }

    private fun action(action: KmAction, uid: String) = buildJsonObject {
        when (action) {
            is KmAction.System -> entity("SYSTEM_ACTION", action.id) {
                action.extras.forEach { (id, data) -> extra(id, data) }
            }
            is KmAction.App -> entity("APP", action.packageName) {
                action.appName?.let { extra("extra_app_name", it) }
            }
            is KmAction.Intent -> entity("INTENT", action.uri) {
                extra("extra_intent_description", action.description)
                extra("extra_intent_target", "ACTIVITY")
                extra("extra_intent_extras", "[]")
            }
            is KmAction.KeyEvent -> entity("KEY_EVENT", action.keyCode.toString()) {
                if (action.metaState != 0) extra("extra_meta_state", action.metaState.toString())
            }
            is KmAction.Tap -> entity("TAP_COORDINATE", "${action.at.x},${action.at.y}") {
                extra("extra_coordinate_description", action.description)
                extra("extra_screen_resolution", "${action.screen.width},${action.screen.height}")
            }
            is KmAction.Swipe -> entity(
                "SWIPE_COORDINATE",
                "${action.from.x},${action.from.y},${action.to.x},${action.to.y},1,${action.durationMs}",
            ) {
                extra("extra_coordinate_description", action.description)
                extra("extra_screen_resolution", "${action.screen.width},${action.screen.height}")
            }
            is KmAction.UiElement -> entity("INTERACT_UI_ELEMENT", action.node.description) {
                extra("extra_accessibility_node_action", action.nodeAction.name)
                extra("extra_accessibility_package_name", action.packageName)
                // No class name: Key Mapper also accepts a node whose class name alone matches, so it
                // could click any view of that class instead of this one (matchAccessibilityNode).
                extra("extra_accessibility_view_resource_id", action.node.viewResourceId)
                extra("extra_accessibility_actions", action.nodeAction.accessibilityActionId.toString())
            }
        }
        put("flags", (action as? KmAction.System)?.flags ?: 0)
        put("uid", uid)
    }

    private fun constraints(constraints: List<KmConstraint>, owner: String) = buildJsonArray {
        constraints.forEachIndexed { i, constraint ->
            addJsonObject {
                when (constraint) {
                    is KmConstraint.AppForeground -> {
                        put("type", "constraint_app_foreground")
                        putJsonArray("extras") {
                            addJsonObject {
                                put("id", "extra_package_name")
                                put("data", constraint.packageName)
                            }
                        }
                        put("uid", ProfileGenerator.uid("constraint:$owner:$i"))
                        put("isNot", constraint.isNot)
                    }
                    is KmConstraint.InOrientation -> {
                        put("type", "constraint_orientation_${constraint.orientation.name.lowercase()}")
                        putJsonArray("extras") {}
                        put("uid", ProfileGenerator.uid("constraint:$owner:$i"))
                        put("isNot", false)
                    }
                }
            }
        }
    }

    private class Extras {
        val list = mutableListOf<Pair<String, String>>()

        fun extra(id: String, data: String) {
            list += id to data
        }
    }

    private fun JsonObjectBuilder.entity(type: String, data: String, extras: Extras.() -> Unit = {}) {
        put("type", type)
        put("data", data)
        val collected = Extras().apply(extras).list
        putJsonArray("extras") {
            collected.forEach { (id, value) ->
                addJsonObject {
                    put("id", id)
                    put("data", value)
                }
            }
        }
    }
}
