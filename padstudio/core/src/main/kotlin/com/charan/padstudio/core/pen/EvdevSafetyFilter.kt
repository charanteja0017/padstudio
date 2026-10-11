package com.charan.padstudio.core.pen

/**
 * Expert Mode grabs the whole input device a trigger belongs to (Key Mapper's
 * evdev_grab_controller.rs). Grabbing the touchscreen would send every pen stroke through Key
 * Mapper, so only devices that look like a pen's *key* device are allowed: Bluetooth (bus 0x0005)
 * or a pen/stylus name, and no touch or pen-position events.
 */
object EvdevSafetyFilter {
    const val BUS_BLUETOOTH = 0x0005

    private val PEN_NAME = Regex("pen|stylus", RegexOption.IGNORE_CASE)
    private val DIGITIZER_CAPABILITY = Regex("^(BTN_TOOL_[A-Z]+|BTN_TOUCH|BTN_STYLUS[23]?|ABS_[A-Z_]+)$")

    /** Linux scan codes that only digitizers send (input-event-codes.h). */
    private val DIGITIZER_SCAN_CODES = mapOf(
        320 to "BTN_TOOL_PEN",
        321 to "BTN_TOOL_RUBBER",
        325 to "BTN_TOOL_FINGER",
        329 to "BTN_STYLUS3",
        330 to "BTN_TOUCH",
        331 to "BTN_STYLUS",
        332 to "BTN_STYLUS2",
    )

    /** Null when the trigger is safe to grab, otherwise the reason it is refused. */
    fun check(trigger: PenTrigger.Evdev): String? {
        val device = trigger.device
        DIGITIZER_SCAN_CODES[trigger.scanCode]?.let {
            return "${device.name} code $it comes from the touchscreen/digitizer"
        }
        device.capabilities.firstOrNull { DIGITIZER_CAPABILITY.matches(it) }?.let {
            return "${device.name} reports $it, so it is a touch or pen-position device"
        }
        if (device.bus != BUS_BLUETOOTH && !PEN_NAME.containsMatchIn(device.name)) {
            return "${device.name} is neither Bluetooth nor named like a pen"
        }
        return null
    }
}
