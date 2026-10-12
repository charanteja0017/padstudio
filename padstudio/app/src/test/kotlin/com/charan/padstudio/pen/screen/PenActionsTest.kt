package com.charan.padstudio.pen.screen

import com.charan.padstudio.core.pen.PenIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PenActionsTest {
    @Test fun `every action is in exactly one group and the groups hold all 25`() {
        val listed = ActionGroup.entries.flatMap { actionsIn(it) }
        assertEquals(25, PenIntent.entries.size)
        assertEquals(PenIntent.entries.toSet(), listed.toSet())
        assertEquals(listed.size, listed.toSet().size, "an action is listed twice")
    }

    @Test fun `groups match the mock-up and plan`() {
        assertEquals(listOf(PenIntent.SHUTTER, PenIntent.OPEN_CAMERA, PenIntent.FLASHLIGHT), actionsIn(ActionGroup.CAMERA))
        assertEquals(ActionGroup.READING, groupOf(PenIntent.NEXT_PAGE))
        assertEquals(ActionGroup.READING, groupOf(PenIntent.OPEN_NOTES))
        assertEquals(ActionGroup.MEDIA, groupOf(PenIntent.VOLUME_UP))
        assertEquals(ActionGroup.NAVIGATION, groupOf(PenIntent.LAST_APP))
        assertEquals(ActionGroup.NAVIGATION, groupOf(PenIntent.OPEN_LIBRARY))
        assertEquals(ActionGroup.SYSTEM, groupOf(PenIntent.SCREENSHOT))
        assertEquals(listOf("Camera", "Reading & notes", "Media", "Navigation", "System"), ActionGroup.entries.map { it.label })
    }

    @Test fun `unavailable text comes from core's needs`() {
        assertEquals("Unavailable: needs camera permission", unavailableText(PenIntent.FLASHLIGHT))
        assertEquals("Unavailable: needs Do Not Disturb access", unavailableText(PenIntent.VOLUME_DOWN))
        assertEquals("Unavailable: needs modify system settings", unavailableText(PenIntent.ROTATION_LOCK))
        assertNull(unavailableText(PenIntent.SHUTTER))
        assertEquals(5, PenIntent.entries.count { unavailableText(it) != null })
    }

    @Test fun `no search and no group lists every group`() {
        val sections = pickerSections("", null)
        assertEquals(ActionGroup.entries.toList(), sections.map { it.group })
        assertEquals(25, sections.sumOf { it.actions.size })
        assertTrue(showsNothingChoice(""))
    }

    @Test fun `a group narrows the list`() {
        val sections = pickerSections("  ", ActionGroup.MEDIA)
        assertEquals(listOf(ActionGroup.MEDIA), sections.map { it.group })
        assertEquals(5, sections.single().actions.size)
    }

    @Test fun `search ignores case and the chosen group`() {
        val sections = pickerSections("VOLUME", ActionGroup.CAMERA)
        assertEquals(listOf(PenIntent.VOLUME_UP, PenIntent.VOLUME_DOWN), sections.flatMap { it.actions })
        assertFalse(showsNothingChoice("volume"))
    }

    @Test fun `search matches group names and every word`() {
        assertEquals(actionsIn(ActionGroup.CAMERA), pickerSections("camera", null).flatMap { it.actions })
        assertEquals(listOf(PenIntent.NEXT_PAGE), pickerSections("next page", null).flatMap { it.actions })
        assertTrue(pickerSections("zzz", null).isEmpty())
        assertTrue(showsNothingChoice("nothing"))
        assertTrue(showsNothingChoice("hyperos"))
    }

    @Test fun `the placeholder counts the actions`() {
        assertEquals(25, actionCount)
    }
}
