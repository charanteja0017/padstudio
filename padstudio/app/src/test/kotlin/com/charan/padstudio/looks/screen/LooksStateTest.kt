package com.charan.padstudio.looks.screen

import com.charan.padstudio.core.home.AppFacts
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.home.PlannedGroup
import com.charan.padstudio.data.IconStyle
import com.charan.padstudio.icons.IconPack
import com.charan.padstudio.ui.theme.BrandPalette
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class LooksStateTest {
    private fun app(pkg: String, label: String = pkg) = AppFacts(packageName = pkg, activityName = "$pkg.Main", label = label)

    private fun group(group: AppGroup, vararg packages: String) = PlannedGroup(group, packages.map { app(it) })

    // --- The 12 preview apps -------------------------------------------------------------------------

    @Test fun `previews take one app per group in turn`() {
        val groups = listOf(
            group(AppGroup.STUDY, "a1", "a2", "a3"),
            group(AppGroup.WORK, "b1"),
            group(AppGroup.READING, "c1", "c2"),
        )
        assertEquals(listOf("a1", "b1", "c1", "a2", "c2", "a3"), previewApps(groups).map { it.packageName })
        assertEquals(listOf("a1", "b1", "c1", "a2"), previewApps(groups, count = 4).map { it.packageName })
    }

    @Test fun `previews leave out PadStudio and show each package once`() {
        val groups = listOf(
            group(AppGroup.TOOLS, "com.charan.padstudio", "same.pkg", "t2"),
            PlannedGroup(AppGroup.MORE, listOf(AppFacts("same.pkg", "same.pkg.Other", "Same, second activity"), app("m2"))),
        )
        val picked = previewApps(groups, exclude = setOf("com.charan.padstudio")).map { it.packageName }
        assertEquals(listOf("same.pkg", "m2", "t2"), picked)
    }

    @Test fun `previews stop at twelve and cope with fewer or none`() {
        val many = AppGroup.entries.filter { it != AppGroup.HIDDEN }.map { g -> group(g, "${g.name}.1", "${g.name}.2") }
        assertEquals(PREVIEW_APP_COUNT, 12)
        assertEquals(12, previewApps(many).size)
        assertEquals(12, previewApps(many).map { it.packageName }.toSet().size)
        assertEquals(2, previewApps(listOf(group(AppGroup.GAMES, "g1", "g2"))).size)
        assertTrue(previewApps(emptyList()).isEmpty())
    }

    @Test fun `every icon style is previewed once, apps' own icons first`() {
        assertEquals(IconStyle.entries.toSet(), STYLE_ORDER.toSet())
        assertEquals(IconStyle.entries.size, STYLE_ORDER.size)
        assertEquals(IconStyle.ORIGINAL, STYLE_ORDER.first())
    }

    // --- Icon sources and packs ----------------------------------------------------------------------

    @Test fun `icon sources read IconRepository's answers`() {
        assertEquals(IconSource.PACK, iconSourceOf("pack"))
        assertEquals(IconSource.ONE_COLOUR, iconSourceOf("themed/monochrome"))
        assertEquals(IconSource.ONE_COLOUR, iconSourceOf("themed/lawnicons"))
        assertEquals(IconSource.ORIGINAL, iconSourceOf("themed→original/adaptive"))
        assertEquals(IconSource.ORIGINAL, iconSourceOf("original/legacy-plate"))
    }

    @Test fun `IconRepository still answers with the strings the screen reads`() {
        val source = sourceFile("icons/IconRepository.kt")
        for (literal in listOf("\"pack\"", "\"themed/monochrome\"", "\"themed/lawnicons\"", "\"themed→\$original\"")) {
            assertTrue(literal in source, "IconRepository.source() no longer returns $literal")
        }
    }

    private val lawnicons = IconPack("app.lawnchair.lawnicons", "Lawnicons")
    private val arcticons = IconPack("com.donnnno.arcticons", "Arcticons")

    @Test fun `the pack setting is told apart`() {
        assertNull(packChoice(null, "app.lawnchair.lawnicons", 11, 12))
        assertEquals(PackChoice.Chosen("Lawnicons", "app.lawnchair.lawnicons", 11, 12), packChoice(listOf(arcticons, lawnicons), "app.lawnchair.lawnicons", 11, 12))
        assertEquals(PackChoice.Missing("gone.pack"), packChoice(listOf(lawnicons), "gone.pack", null, 12))
        assertEquals(PackChoice.Missing("gone.pack"), packChoice(emptyList(), "gone.pack", null, 12))
        assertEquals(PackChoice.NoneInstalled, packChoice(emptyList(), null, null, 12))
        assertEquals(PackChoice.NotChosen, packChoice(listOf(lawnicons), null, null, 12))
    }

    @Test fun `the icon pack caption counts the previews the pack draws`() {
        fun caption(covered: Int?) = styleCaption(IconStyle.ICON_PACK, PackChoice.Chosen("Lawnicons", "p", covered, 12))
        assertEquals("Lawnicons has 11 of these 12", caption(11))
        assertEquals("Lawnicons has all 12", caption(12))
        assertEquals("Lawnicons has none of these 12", caption(0))
        assertEquals("Lawnicons", caption(null))
        assertEquals("No icon pack installed", styleCaption(IconStyle.ICON_PACK, PackChoice.NoneInstalled))
        assertEquals("As each app draws them", styleCaption(IconStyle.ORIGINAL, null))
        // Themed icons take the system's wallpaper palette (themedIconColors), never the brand palette.
        assertEquals("One colour, from your wallpaper", styleCaption(IconStyle.THEMED, null))
    }

    @Test fun `the pack row opens the picker only when there is something to pick`() {
        assertFalse(packRowText(null).opensPicker)
        assertFalse(packRowText(PackChoice.NoneInstalled).opensPicker)
        assertTrue(packRowText(PackChoice.NotChosen).opensPicker)
        assertTrue(packRowText(PackChoice.Missing("gone.pack")).opensPicker)
        val chosen = packRowText(PackChoice.Chosen("Lawnicons", "p", 11, 12))
        assertEquals("Icon pack: Lawnicons", chosen.headline)
        assertEquals("New packs show up when you come back", chosen.supporting)
        assertEquals("Has 11 of these 12 apps", pickerCoverageText(11, 12))
        assertNull(pickerCoverageText(null, 12))
    }

    // --- Match wallpaper colours ---------------------------------------------------------------------

    @Test fun `wallpaper colours need Android 12`() {
        assertFalse(dynamicColourSupported(30))
        assertTrue(dynamicColourSupported(31))
        assertTrue(dynamicColourSupported(37))
    }

    @Test fun `the switch's text says the state in words and why brand colours are the default`() {
        val palette = paletteLabel(BrandPalette.VIOLET)
        assertEquals("Violet", palette)
        assertEquals("Graphite", paletteLabel(BrandPalette.GRAPHITE))
        val off = matchColoursText(on = false, supported = true, palette = palette)
        assertTrue(off.startsWith("Off") && "Violet" in off && "made the app all grey" in off, off)
        val on = matchColoursText(on = true, supported = true, palette = palette)
        assertTrue(on.startsWith("On") && "made the app all grey" in on, on)
        val unsupported = matchColoursText(on = false, supported = false, palette = palette)
        assertTrue("Android 12" in unsupported, unsupported)
    }

    // --- Wallpaper -----------------------------------------------------------------------------------

    @Test fun `wallpaper results are read from MainViewModel's message`() {
        assertEquals(WallpaperResult.Set, wallpaperResultOf("Wallpaper set on home and lock screen."))
        assertEquals(WallpaperResult.Failed("Permission denial: x"), wallpaperResultOf("Couldn't set the wallpaper: Permission denial: x"))
        assertEquals(WallpaperResult.Failed(null), wallpaperResultOf("Couldn't set the wallpaper: null"))
        assertEquals(WallpaperResult.Failed(null), wallpaperResultOf("Couldn't set the wallpaper: "))
        assertNull(wallpaperResultOf("Pen actions on."))
        assertNull(wallpaperResultOf(null))
    }

    @Test fun `MainViewModel still reports the wallpaper with the texts the screen reads`() {
        val source = sourceFile("MainViewModel.kt")
        assertTrue("\"$WALLPAPER_SET_MESSAGE\"" in source, "MainViewModel.applyWallpaper's success message changed")
        assertTrue("\"$WALLPAPER_FAILED_PREFIX \${it.message}\"" in source, "MainViewModel.applyWallpaper's failure message changed")
    }

    @Test fun `failures get a plain reason, never the raw exception text`() {
        val raw = listOf(
            "java.lang.SecurityException: Permission denial: writing com.android.server.wallpaper",
            "ENOSPC (No space left on device)",
            "java.lang.OutOfMemoryError: Failed to allocate",
            "Some vendor error 0x7f",
            null,
        )
        val reasons = raw.map { plainWallpaperFailure(it) }
        assertEquals("PadStudio isn't allowed to change the wallpaper on this tablet.", reasons[0])
        assertEquals("The tablet is out of storage space.", reasons[1])
        assertEquals("The tablet ran out of memory making the picture.", reasons[2])
        assertEquals("The tablet didn't accept the new wallpaper.", reasons[3])
        assertEquals(reasons[3], reasons[4])
        raw.filterNotNull().zip(reasons).forEach { (detail, reason) -> assertFalse(detail in reason) }
    }

    @Test fun `the wallpaper is named like the reference, and success says when`() {
        assertEquals("Violet dusk", wallpaperName("Violet", dark = true))
        assertEquals("Violet dawn", wallpaperName("Violet", dark = false))
        assertEquals("Dusk, in your wallpaper's colours", wallpaperName(null, dark = true))
        assertEquals("Wallpaper set on home and lock screen · 14:05", wallpaperSetText("14:05"))
    }

    @Test fun `the preview is drawn at about its own width`() {
        assertEquals(0, previewRenderSize(0))
        assertEquals(256, previewRenderSize(1))
        assertEquals(768, previewRenderSize(700))
        assertEquals(768, previewRenderSize(768))
        assertEquals(1280, previewRenderSize(5000))
    }

    @Test fun `label lists read as a sentence`() {
        assertEquals("none", labelList(emptyList()))
        assertEquals("WiFiBar", labelList(listOf("WiFiBar")))
        assertEquals("Flipkart, Meesho and WiFiBar", labelList(listOf("Flipkart", "Meesho", "WiFiBar")))
    }

    // --- Copy rules (SCREENS-BRIEF rule 7) -----------------------------------------------------------

    @Test fun `the Looks screen never claims what it can't know`() {
        val looks = File(mainKotlin(), "looks")
        val files = looks.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.size >= 6, "expected the Looks screen's files under $looks, found ${files.map { it.name }}")
        val literal = Regex("\"([^\"\\\\]|\\\\.)*\"")
        for (file in files) {
            for (match in literal.findAll(file.readText())) {
                val text = match.value.lowercase()
                for (word in listOf("verified", "running", "expert mode on")) {
                    assertFalse(word in text, "${file.name}: $word in ${match.value}")
                }
            }
        }
    }

    /** The app's main Kotlin sources: unit tests run in the module directory (padstudio/app). */
    private fun mainKotlin(): File {
        val dir = File(System.getProperty("user.dir"), "src/main/kotlin/com/charan/padstudio")
        if (!dir.isDirectory) fail("main sources not found at ${dir.absolutePath}")
        return dir
    }

    private fun sourceFile(path: String): String {
        val file = File(mainKotlin(), path)
        if (!file.isFile) fail("${file.absolutePath} not found")
        return file.readText()
    }
}
