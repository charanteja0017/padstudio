package com.charan.padstudio.debug

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class DebugDumpsTest {
    @Test fun `opaque colours are #RRGGBB, translucent ones #AARRGGBB`() {
        assertEquals("#5A3FD6", argbHex(0xFF5A3FD6.toInt()))
        assertEquals("#000000", argbHex(0xFF000000.toInt()))
        assertEquals("#80000000", argbHex(0x80000000.toInt()))
        assertEquals("#00FFFFFF", argbHex(0x00FFFFFF))
    }

    @Test fun `the colour dump has every ColorScheme role`() {
        // Every role is a public no-argument getter returning a Color (a long on the JVM): getPrimary-0d7_KjU and so on.
        val roles = ColorScheme::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE && it.name.startsWith("get") }
            .map { method -> method.name.removePrefix("get").substringBefore('-').replaceFirstChar { it.lowercaseChar() } }
            .toSet()
        val dumped = lightColorScheme().roleHexes()
        assertEquals(roles, dumped.keys)
        assertEquals(48, dumped.size)
    }

    @Test fun `the colour dump writes each role's own colour`() {
        val dumped = lightColorScheme(primary = Color(0xFF421EBE), onSurfaceVariant = Color(0xFF48464C)).roleHexes()
        assertEquals("#421EBE", dumped["primary"])
        assertEquals("#48464C", dumped["onSurfaceVariant"])
    }

    @Test fun `colors json lists the settings, then the roles in order`() {
        val json = colorsJson(true, 1.25f, 300, linkedMapOf("primary" to "#C8BFFF", "onPrimary" to "#2A0D8E"))
        val expected = """
            |{
            |  "night": true,
            |  "fontScale": 1.25,
            |  "fontWeightAdjustment": 300,
            |  "roles": {
            |    "primary": "#C8BFFF",
            |    "onPrimary": "#2A0D8E"
            |  }
            |}
            |
        """.trimMargin()
        assertEquals(expected, json)
    }

    @Test fun `sha256 matches the standard test vector`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc".toByteArray()))
    }
}
