package com.charan.padstudio.core.pen

import com.charan.padstudio.core.pen.ActionResolver.Companion.KEYCODE_CAMERA
import com.charan.padstudio.core.pen.ActionResolver.Companion.KEYCODE_VOLUME_DOWN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActionResolverTest {
    private val basic = ActionResolver(PenFixtures.FACTS, PenMode.BASIC)
    private val expert = ActionResolver(PenFixtures.FACTS, PenMode.EXPERT)

    @Test fun `basic page turns are one swipe per orientation in that orientation's coordinates`() {
        val variants = basic.resolve(PenIntent.NEXT_PAGE, "com.adobe.reader").variants
        assertEquals(listOf("portrait", "landscape"), variants.map { it.key })
        val portrait = variants[0].actions.single() as KmAction.Swipe
        assertEquals(ScreenSize(2136, 3200), portrait.screen)
        assertEquals(Point(1068, 2560), portrait.from) // 80% down a 3200 px tall screen
        assertEquals(Point(1068, 800), portrait.to)
        assertEquals(listOf(KmConstraint.InOrientation(Orientation.PORTRAIT)), variants[0].constraints)
        val landscape = variants[1].actions.single() as KmAction.Swipe
        assertEquals(ScreenSize(3200, 2136), landscape.screen)
    }

    @Test fun `page-flip readers swipe sideways`() {
        val next = basic.resolve(PenIntent.NEXT_PAGE, "com.amazon.kindle").variants.first().actions.single() as KmAction.Swipe
        assertTrue(next.from.x > next.to.x && next.from.y == next.to.y, "next page flips right-to-left: $next")
    }

    @Test fun `expert page turns are page keys`() {
        assertEquals(KmAction.KeyEvent(93), expert.resolve(PenIntent.NEXT_PAGE, "com.adobe.reader").variants.single().actions.single())
        assertEquals(KmAction.KeyEvent(92), expert.resolve(PenIntent.PREVIOUS_PAGE, "com.adobe.reader").variants.single().actions.single())
    }

    @Test fun `undo is ctrl z in expert mode and a recorded button in basic mode`() {
        assertEquals(KmAction.KeyEvent(54, 0x3000), expert.resolve(PenIntent.UNDO, "com.microsoft.office.onenote").variants.single().actions.single())
        val keep = basic.resolve(PenIntent.UNDO, "com.google.android.keep").variants.single().actions.single() as KmAction.UiElement
        assertEquals("com.google.android.keep:id/undo", keep.node.viewResourceId)
        val onenote = basic.resolve(PenIntent.UNDO, "com.microsoft.office.onenote")
        assertTrue(onenote.variants.isEmpty() && onenote.warnings.single().contains("Expert"))
    }

    @Test fun `shutter outside a camera context opens the probed camera directly`() {
        assertEquals(KmAction.App("com.android.camera", null), basic.resolve(PenIntent.SHUTTER, null).variants.single().actions.single())
        assertEquals(KmAction.App("com.android.camera", null), basic.resolve(PenIntent.OPEN_CAMERA, null).variants.single().actions.single())
        // Nothing probed: Android's own camera action, chooser or not.
        val unprobed = ActionResolver(PenFixtures.FACTS.copy(cameras = DefaultProfiles.KNOWN_CAMERAS), PenMode.BASIC)
        assertEquals(KmAction.System("open_camera"), unprobed.resolve(PenIntent.OPEN_CAMERA, null).variants.single().actions.single())
    }

    @Test fun `library opens PadStudio through an intent uri key mapper can parse`() {
        val intent = basic.resolve(PenIntent.OPEN_LIBRARY, null).variants.single().actions.single() as KmAction.Intent
        assertEquals("#Intent;component=com.charan.padstudio/com.charan.padstudio.home.LibraryActivity;end", intent.uri)
    }

    @Test fun `expert mode without key injection uses swipes and recorded buttons`() {
        val noInjection = ActionResolver(PenFixtures.FACTS.copy(canInjectKeys = false), PenMode.EXPERT)
        val page = noInjection.resolve(PenIntent.NEXT_PAGE, "com.adobe.reader").variants
        assertTrue(page.size == 2 && page.all { it.actions.single() is KmAction.Swipe }, page.toString())
        val shutter = noInjection.resolve(PenIntent.SHUTTER, "com.android.camera").variants.single().actions.single()
        assertTrue(shutter is KmAction.UiElement, "recorded shutter node wins: $shutter")
        val openCamera = noInjection.resolve(PenIntent.SHUTTER, "net.sourceforge.opencamera")
        assertTrue(openCamera.variants.isEmpty() && openCamera.warnings.isNotEmpty(), "no camera key without injection")
    }

    @Test fun `with key injection a measured shutter key beats the recorded button`() {
        val measured = PenFixtures.FACTS.cameras.getValue("com.android.camera").copy(honoursCameraKey = true, volumeKeyIsShutter = true)
        val facts = PenFixtures.FACTS.copy(canInjectKeys = true, cameras = PenFixtures.FACTS.cameras + ("com.android.camera" to measured))
        val expert = ActionResolver(facts, PenMode.EXPERT)
        assertEquals(KmAction.KeyEvent(KEYCODE_CAMERA), expert.resolve(PenIntent.SHUTTER, "com.android.camera").variants.single().actions.single())

        val volumeOnly = facts.copy(cameras = facts.cameras + ("com.android.camera" to measured.copy(honoursCameraKey = false)))
        assertEquals(
            KmAction.KeyEvent(KEYCODE_VOLUME_DOWN),
            ActionResolver(volumeOnly, PenMode.EXPERT).resolve(PenIntent.SHUTTER, "com.android.camera").variants.single().actions.single(),
        )
        // Basic mode can't inject keys, so the recorded button stays.
        val basicShutter = ActionResolver(facts, PenMode.BASIC).resolve(PenIntent.SHUTTER, "com.android.camera").variants.single().actions.single()
        assertTrue(basicShutter is KmAction.UiElement, basicShutter.toString())
    }

    @Test fun `notes shortcut needs a chosen app`() {
        val none = ActionResolver(PenFixtures.FACTS.copy(notesApp = null), PenMode.BASIC).resolve(PenIntent.OPEN_NOTES, null)
        assertTrue(none.variants.isEmpty())
        assertNull(none.variants.firstOrNull())
    }
}
