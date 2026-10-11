package com.charan.padstudio.core.pen

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

    @Test fun `shutter outside a camera context opens the camera`() {
        assertEquals(KmAction.System("open_camera"), basic.resolve(PenIntent.SHUTTER, null).variants.single().actions.single())
    }

    @Test fun `library opens PadStudio through an intent uri key mapper can parse`() {
        val intent = basic.resolve(PenIntent.OPEN_LIBRARY, null).variants.single().actions.single() as KmAction.Intent
        assertEquals("#Intent;component=com.charan.padstudio/com.charan.padstudio.home.LibraryActivity;end", intent.uri)
    }

    @Test fun `notes shortcut needs a chosen app`() {
        val none = ActionResolver(PenFixtures.FACTS.copy(notesApp = null), PenMode.BASIC).resolve(PenIntent.OPEN_NOTES, null)
        assertTrue(none.variants.isEmpty())
        assertNull(none.variants.firstOrNull())
    }
}
