package com.charan.padstudio.core.home

import com.charan.padstudio.core.icons.AppFilter
import com.charan.padstudio.core.icons.XmlTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowerpotTest {
    @Test fun `every vendored lawnchair rule file parses`() {
        val pots = FlowerpotResources.loadAll()
        assertEquals(Flowerpot.GROUP_OF.size, pots.size, "one file per mapped category")
        assertTrue(pots.sumOf { it.packages.size } > 5000, "real data, not an empty stub")
        val tools = pots.single { it.name == "TOOLS" }
        assertTrue("android.intent.category.APP_CALCULATOR" in tools.intentCategories)
        assertTrue(tools.codeRules.contains(listOf("category", "productivity")))
    }

    @Test fun `format errors are reported`() {
        assertFailsWith<FlowerpotFormatException> { Flowerpot.parse("X", "com.app\n") }
        assertFailsWith<FlowerpotFormatException> { Flowerpot.parse("X", "$1\n$1\n") }
        assertFailsWith<FlowerpotFormatException> { Flowerpot.parse("X", "$2\ncom.app\n") }
        assertFailsWith<FlowerpotFormatException> { Flowerpot.parse("X", "$1\n!weird\n") }
    }
}

class CategorizerTest {
    private val pots = FlowerpotResources.loadAll()

    private fun app(pkg: String, label: String = pkg, category: Int? = null, system: Boolean = false, intents: Set<String> = emptySet(), game: Boolean = false) =
        AppFacts(pkg, "$pkg.Main", label, category, game, system, intents)

    @Test fun `user override beats everything`() {
        val c = Categorizer(pots, overrides = mapOf("com.whatsapp" to AppGroup.WORK))
        assertEquals(AppGroup.WORK, c.place(app("com.whatsapp")).group)
    }

    @Test fun `curated list covers UPI and OTT apps flowerpot misses`() {
        val c = Categorizer(pots)
        assertEquals(AppGroup.SHOP_PAY, c.place(app("com.phonepe.app")).group)
        assertEquals(AppGroup.WATCH_LISTEN, c.place(app("in.startv.hotstar")).group)
        assertEquals(AppGroup.STUDY, c.place(app("anything.unknown", label = "Notewise")).group)
    }

    @Test fun `flowerpot package lists and intent rules are used next`() {
        val c = Categorizer(pots)
        val education = pots.single { it.name == "EDUCATION" }.packages.first { it !in com.charan.padstudio.core.home.CuratedApps.GROUP_OF }
        assertEquals(AppGroup.STUDY, c.place(app(education)).group)
        assertEquals("flowerpot TOOLS (intent)", c.place(app("org.example.calc", intents = setOf("TOOLS"))).reason)
    }

    @Test fun `android category, keywords, system and fallback`() {
        val c = Categorizer(pots)
        assertEquals(AppGroup.GAMES, c.place(app("org.example.puzzle", game = true)).group)
        assertEquals(AppGroup.WATCH_LISTEN, c.place(app("org.example.a", category = AndroidCategory.VIDEO)).group)
        assertEquals(AppGroup.READING, c.place(app("org.example.pdf", label = "PDF Reader Pro")).group)
        assertEquals(AppGroup.MORE, c.place(app("org.example.facebooklike", label = "Facebooklike")).group, "\\b keeps 'book' out of 'facebook'")
        assertEquals(AppGroup.SYSTEM, c.place(app("com.miui.something", system = true)).group)
        assertEquals(AppGroup.MORE, c.place(app("org.example.zzz")).group)
    }
}

class GroupPlannerTest {
    private fun placement(pkg: String, label: String, group: AppGroup) =
        Placement(AppFacts(pkg, "$pkg.Main", label), group, "test")

    @Test fun `hidden apps vanish, single apps fold into More, order and sorting are stable`() {
        val plan = GroupPlanner.plan(
            listOf(
                placement("a", "zeta", AppGroup.STUDY),
                placement("b", "Alpha", AppGroup.STUDY),
                placement("c", "Solo", AppGroup.WORK),
                placement("d", "Junk", AppGroup.HIDDEN),
                placement("e", "Other", AppGroup.MORE),
            ),
        )
        assertEquals(listOf(AppGroup.STUDY, AppGroup.MORE), plan.map { it.group })
        assertEquals(listOf("Alpha", "zeta"), plan[0].apps.map { it.label })
        assertEquals(setOf("Solo", "Other"), plan[1].apps.map { it.label }.toSet())
    }

    @Test fun `widget grid respects density and the glance row limit`() {
        assertEquals(5 to 3, GroupPlanner.gridFor(320, 230, Density.COMPACT))
        assertEquals(10 to 1, GroupPlanner.gridFor(2000, 100, Density.DENSE), "capped at 10 per row")
    }
}

class AppFilterTest {
    private val tags = sequenceOf(
        XmlTag("iconback", mapOf("img1" to "back_1", "img2" to "back_2")),
        XmlTag("iconmask", mapOf("img1" to "mask")),
        XmlTag("scale", mapOf("factor" to "0.85")),
        XmlTag("item", mapOf("component" to "ComponentInfo{com.whatsapp/com.whatsapp.Main}", "drawable" to "whatsapp")),
        XmlTag("item", mapOf("component" to "ComponentInfo{com.relative/.Launcher}", "drawable" to "relative")),
        XmlTag("item", mapOf("component" to "ComponentInfo{com.whatsapp/com.whatsapp.Main}", "drawable" to "duplicate_ignored")),
        XmlTag("item", mapOf("component" to ":BROKEN", "drawable" to "nope")),
        XmlTag("calendar", mapOf("component" to "ComponentInfo{com.google.android.calendar/com.android.calendar.AllInOneActivity}", "prefix" to "calendar_")),
    )

    @Test fun `parses items, fallback layers and calendars`() {
        val filter = AppFilter.fromTags(tags)
        assertEquals("whatsapp", filter.drawableFor("com.whatsapp", "com.whatsapp.Main"))
        assertEquals("relative", filter.drawableFor("com.relative", "com.relative.Launcher"))
        assertEquals("whatsapp", filter.drawableFor("com.whatsapp", "com.whatsapp.OtherEntry"), "package-level fallback")
        assertNull(filter.drawableFor("com.unknown", "x"))
        assertEquals(listOf("back_1", "back_2"), filter.iconBacks)
        assertEquals("mask", filter.iconMask)
        assertEquals(0.85f, filter.scale)
        assertEquals(2, filter.drawables.size)
        assertEquals("calendar_", filter.calendars.values.single())
    }
}
