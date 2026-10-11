package com.charan.padstudio.core.pen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parses the facts tools/lib/make_facts.py wrote for the real Xiaomi Pad 8 (copied from
 * docs/device/device-facts.json), proving the Python writer and this parser agree.
 */
class FactsFileTest {
    private val facts = FactsFile.parse(javaClass.getResource("/pad8-device-facts.json")!!.readText())

    @Test fun `pad 8 facts parse with the focus pen pro controls`() {
        assertEquals(PenKind.FOCUS_PEN_PRO, facts.penKind)
        assertEquals(PenMode.EXPERT, facts.penMode)
        val pinch = facts.controls.getValue("pinch") as PenTrigger.Evdev
        assertEquals(189, pinch.scanCode)
        assertEquals("Xiaomi Focus Pen Pro Keyboard", pinch.device.name)
        assertEquals(0x0022, pinch.device.vendor)
        assertEquals(setOf("pinch", "slide_up", "slide_down"), facts.controls.keys)
        assertEquals("com.android.camera:id/shutter_button", facts.cameras.single().shutterNode?.viewResourceId)
    }

    @Test fun `every recorded control passes the evdev safety filter`() {
        facts.controls.values.forEach { assertEquals(null, EvdevSafetyFilter.check(it as PenTrigger.Evdev)) }
    }

    @Test fun `the default focus pen pro profile generates for the pad 8`() {
        val deviceFacts = facts.applyTo(DeviceFacts(screen = ScreenSize(2136, 3200)))
        val profile = ProfileGenerator(deviceFacts).generate(
            DefaultProfiles.profile(PenKind.FOCUS_PEN_PRO, PenMode.EXPERT),
            PenSetup(PenMode.EXPERT, facts.controls),
        )
        val camera = ConstraintEvaluator.eligible(profile, "pinch", Press.SHORT, Situation("com.android.camera", Orientation.LANDSCAPE)).single()
        assertTrue(camera.actions.single() is KmAction.UiElement, "squeeze in Xiaomi Camera clicks the shutter")
        val readera = ConstraintEvaluator.eligible(profile, "slide_down", Press.SHORT, Situation("org.readera", Orientation.PORTRAIT)).single()
        assertTrue(readera.actions.single() is KmAction.Swipe, "without key injection a page turn is a swipe")
    }
}
