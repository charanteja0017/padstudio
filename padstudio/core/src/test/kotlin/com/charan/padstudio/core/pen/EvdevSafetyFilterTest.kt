package com.charan.padstudio.core.pen

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EvdevSafetyFilterTest {
    private fun trigger(name: String, bus: Int, scanCode: Int = 104, caps: List<String> = emptyList()) =
        PenTrigger.Evdev(92, scanCode, EvdevDevice(name, bus, 0x2717, 0x5014, caps))

    @Test fun `bluetooth pen key device is allowed`() {
        assertNull(EvdevSafetyFilter.check(trigger("Xiaomi Smart Pen", 0x0005, caps = listOf("KEY_PAGEUP", "KEY_PAGEDOWN"))))
    }

    @Test fun `pen-named device on another bus is allowed when it has no touch events`() {
        assertNull(EvdevSafetyFilter.check(trigger("Xiaomi Focus Pen Pro", 0x0006, scanCode = 189, caps = listOf("KEY_F19"))))
    }

    @Test fun `touchscreen and digitizer are refused`() {
        assertNotNull(EvdevSafetyFilter.check(trigger("xiaomi-touch", 0x0018, caps = listOf("BTN_TOUCH", "ABS_MT_POSITION_X"))))
        assertNotNull(EvdevSafetyFilter.check(trigger("Xiaomi Pen digitizer", 0x0005, caps = listOf("BTN_TOOL_PEN"))))
        assertNotNull(EvdevSafetyFilter.check(trigger("Xiaomi Smart Pen", 0x0005, scanCode = 331)), "BTN_STYLUS is a digitizer code")
    }

    @Test fun `unknown wired keyboard is refused`() {
        assertNotNull(EvdevSafetyFilter.check(trigger("USB Keyboard", 0x0003)))
    }
}
