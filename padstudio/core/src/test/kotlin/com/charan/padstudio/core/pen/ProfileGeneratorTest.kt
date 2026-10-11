package com.charan.padstudio.core.pen

import com.charan.padstudio.core.pen.PenFixtures.APPS
import com.charan.padstudio.core.pen.PenFixtures.BASIC_TWO_BUTTON
import com.charan.padstudio.core.pen.PenFixtures.EXPERT_FOCUS_PRO
import com.charan.padstudio.core.pen.PenFixtures.EXPERT_TWO_BUTTON
import com.charan.padstudio.core.pen.PenFixtures.FACTS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileGeneratorTest {
    private val generator = ProfileGenerator(FACTS)

    private fun only(profile: GeneratedProfile, app: String, control: String, press: Press, orientation: Orientation) =
        ConstraintEvaluator.eligible(profile, control, press, Situation(app, orientation))

    @Test fun `at most one key map can fire for any app, control, press and orientation`() {
        for ((setup, kind) in listOf(BASIC_TWO_BUTTON to PenKind.TWO_BUTTON, EXPERT_TWO_BUTTON to PenKind.TWO_BUTTON, EXPERT_FOCUS_PRO to PenKind.FOCUS_PEN_PRO)) {
            val profile = generator.generate(DefaultProfiles.profile(kind, setup.mode), setup)
            for (app in APPS) for (control in setup.controls.keys) for (press in Press.entries) for (orientation in Orientation.entries) {
                val fired = only(profile, app, control, press, orientation)
                assertTrue(fired.size <= 1, "$kind/${setup.mode}: $app $control $press $orientation fired ${fired.map { it.name }}")
            }
        }
    }

    @Test fun `pass-through notes apps get the raw button`() {
        val profile = generator.generate(DefaultProfiles.profile(PenKind.TWO_BUTTON, PenMode.BASIC), BASIC_TWO_BUTTON)
        for (app in DefaultProfiles.NATIVE_NOTES.packages) for (control in listOf("upper", "lower")) for (press in Press.entries) {
            assertEquals(emptyList(), only(profile, app, control, press, Orientation.PORTRAIT), "$app $control $press")
        }
    }

    @Test fun `everywhere covers unlisted apps but never a listed one`() {
        val profile = generator.generate(DefaultProfiles.profile(PenKind.TWO_BUTTON, PenMode.BASIC), BASIC_TWO_BUTTON)
        val whatsapp = only(profile, "com.whatsapp", "upper", Press.SHORT, Orientation.LANDSCAPE).single()
        assertEquals(KmAction.System("screenshot"), whatsapp.actions.single())
        val camera = only(profile, "com.android.camera", "upper", Press.SHORT, Orientation.LANDSCAPE).single()
        assertTrue(camera.name.startsWith("Camera"), camera.name)
    }

    @Test fun `each camera app gets its own shutter mechanism`() {
        val profile = generator.generate(DefaultProfiles.profile(PenKind.TWO_BUTTON, PenMode.BASIC), BASIC_TWO_BUTTON)
        val xiaomi = only(profile, "com.android.camera", "lower", Press.SHORT, Orientation.PORTRAIT).single().actions.single()
        assertTrue(xiaomi is KmAction.UiElement && xiaomi.node.viewResourceId == "com.android.camera:id/shutter_button")

        val portrait = only(profile, "com.google.android.GoogleCamera", "upper", Press.SHORT, Orientation.PORTRAIT).single().actions.single()
        val landscape = only(profile, "com.google.android.GoogleCamera", "upper", Press.SHORT, Orientation.LANDSCAPE).single().actions.single()
        assertEquals(KmAction.Tap(Point(1068, 2900), ScreenSize(2136, 3200), "Shutter (portrait)"), portrait)
        assertEquals(KmAction.Tap(Point(3000, 1068), ScreenSize(3200, 2136), "Shutter (landscape)"), landscape)
    }

    @Test fun `open camera uses its camera key in expert mode and warns in basic mode`() {
        val expert = generator.generate(DefaultProfiles.profile(PenKind.TWO_BUTTON, PenMode.EXPERT), EXPERT_TWO_BUTTON)
        val action = only(expert, "net.sourceforge.opencamera", "upper", Press.SHORT, Orientation.PORTRAIT).single().actions.single()
        assertEquals(KmAction.KeyEvent(ActionResolver.KEYCODE_CAMERA), action)

        val basic = generator.generate(DefaultProfiles.profile(PenKind.TWO_BUTTON, PenMode.BASIC), BASIC_TWO_BUTTON)
        assertEquals(emptyList(), only(basic, "net.sourceforge.opencamera", "upper", Press.SHORT, Orientation.PORTRAIT))
        assertTrue(basic.warnings.any { "net.sourceforge.opencamera" in it }, basic.warnings.toString())
    }

    @Test fun `long presses are only mapped in expert mode`() {
        val basic = generator.generate(DefaultProfiles.profile(PenKind.TWO_BUTTON, PenMode.BASIC), BASIC_TWO_BUTTON)
        assertTrue(basic.keyMaps.none { it.press == Press.LONG })
        val expert = generator.generate(DefaultProfiles.profile(PenKind.TWO_BUTTON, PenMode.EXPERT), EXPERT_TWO_BUTTON)
        assertEquals(KmAction.System("screenshot"), only(expert, "com.whatsapp", "upper", Press.LONG, Orientation.PORTRAIT).single().actions.single())
    }

    @Test fun `regenerating gives byte-identical json`() {
        val a = KeyMapperJson.backup(generator.generate(DefaultProfiles.profile(PenKind.TWO_BUTTON, PenMode.BASIC), BASIC_TWO_BUTTON))
        val b = KeyMapperJson.backup(generator.generate(DefaultProfiles.profile(PenKind.TWO_BUTTON, PenMode.BASIC), BASIC_TWO_BUTTON))
        assertEquals(a, b)
    }

    @Test fun `a control that was not recorded is skipped with a warning`() {
        val setup = PenSetup(PenMode.BASIC, mapOf("upper" to PenFixtures.SMART_PEN_UPPER))
        val profile = generator.generate(DefaultProfiles.profile(PenKind.TWO_BUTTON, PenMode.BASIC), setup)
        assertTrue(profile.keyMaps.none { it.controlId == "lower" })
        assertTrue(profile.warnings.any { it.startsWith("Lower button was not recorded") }, profile.warnings.toString())
    }

    @Test fun `basic mode warns when a plain press would be swallowed`() {
        val profile = PenProfile(
            DefaultProfiles.CONTEXTS,
            listOf(Binding(PenProfile.EVERYWHERE, "upper", Press.DOUBLE, PenIntent.SCREENSHOT)),
        )
        val generated = generator.generate(profile, BASIC_TWO_BUTTON)
        assertTrue(generated.warnings.any { "plain press there will do nothing" in it }, generated.warnings.toString())
    }

    @Test fun `digitizer triggers are refused`() {
        val digitizer = PenTrigger.Evdev(0, 331, EvdevDevice("xiaomi-touch", bus = 0x0018, vendor = 0, product = 0, capabilities = listOf("BTN_TOOL_PEN", "BTN_STYLUS")))
        val setup = PenSetup(PenMode.EXPERT, mapOf("upper" to digitizer))
        val profile = generator.generate(DefaultProfiles.profile(PenKind.TWO_BUTTON, PenMode.EXPERT), setup)
        assertTrue(profile.keyMaps.none { it.controlId == "upper" })
        assertTrue(profile.warnings.any { it.startsWith("Refused Upper button") }, profile.warnings.toString())
    }

    @Test fun `focus pen pro slides turn pages in readers`() {
        val profile = generator.generate(DefaultProfiles.profile(PenKind.FOCUS_PEN_PRO, PenMode.EXPERT), EXPERT_FOCUS_PRO)
        val next = only(profile, "com.adobe.reader", "slide_down", Press.SHORT, Orientation.PORTRAIT).single().actions.single()
        assertEquals(KmAction.KeyEvent(ActionResolver.KEYCODE_PAGE_DOWN), next)
        assertEquals(emptyList(), only(profile, "com.whatsapp", "slide_down", Press.SHORT, Orientation.PORTRAIT), "slides stay Xiaomi's outside readers")
    }
}
