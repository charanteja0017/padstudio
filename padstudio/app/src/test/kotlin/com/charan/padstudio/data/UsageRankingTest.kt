package com.charan.padstudio.data

import android.content.pm.LauncherActivityInfo
import com.charan.padstudio.core.home.AppFacts
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Most used" order: the more an app was used the earlier it comes; ties and unused apps follow A to Z. */
class UsageRankingTest {
    @Test fun `the app used most comes first`() {
        val apps = listOf(app("Calendar", "pkg.calendar"), app("Books", "pkg.books"), app("Maps", "pkg.maps"))
        val usage = mapOf("pkg.calendar" to 5_000L, "pkg.books" to 900_000L, "pkg.maps" to 60_000L)

        // Neither A to Z (Books, Calendar, Maps) nor the order they came in (Calendar, Books, Maps).
        assertEquals(listOf("Books", "Maps", "Calendar"), labels(UsageRanking.rank(apps, usage)))
    }

    @Test fun `apps used equally follow A to Z whatever their case`() {
        val apps = listOf(app("zoom", "pkg.zoom"), app("Mail", "pkg.mail"), app("Camera", "pkg.camera"), app("beeper", "pkg.beeper"))
        val usage = mapOf("pkg.zoom" to 100L, "pkg.mail" to 100L, "pkg.camera" to 100L, "pkg.beeper" to 100L)

        assertEquals(listOf("beeper", "Camera", "Mail", "zoom"), labels(UsageRanking.rank(apps, usage)))
    }

    @Test fun `a tie is broken A to Z but never moves an app past one used more`() {
        val apps = listOf(app("Notes", "pkg.notes"), app("Clock", "pkg.clock"), app("Files", "pkg.files"), app("Browser", "pkg.browser"))
        val usage = mapOf("pkg.notes" to 40L, "pkg.clock" to 40L, "pkg.files" to 90L, "pkg.browser" to 10L)

        assertEquals(listOf("Files", "Clock", "Notes", "Browser"), labels(UsageRanking.rank(apps, usage)))
    }

    @Test fun `apps with no usage come last in A to Z`() {
        val apps = listOf(app("Weather", "pkg.weather"), app("Zen", "pkg.zen"), app("Alarm", "pkg.alarm"), app("Music", "pkg.music"))
        // Weather has a zero entry, Alarm and Zen have none: all three count as unused.
        val usage = mapOf("pkg.music" to 1L, "pkg.weather" to 0L)

        assertEquals(listOf("Music", "Alarm", "Weather", "Zen"), labels(UsageRanking.rank(apps, usage)))
    }

    @Test fun `with no usage at all the order is plain A to Z`() {
        val apps = listOf(app("Tasks", "pkg.tasks"), app("camera", "pkg.camera"), app("Browser", "pkg.browser"))

        assertEquals(listOf("Browser", "camera", "Tasks"), labels(UsageRanking.rank(apps, emptyMap())))
    }

    @Test fun `usage of a package that is not in the list changes nothing`() {
        val apps = listOf(app("B", "pkg.b"), app("A", "pkg.a"))

        assertEquals(listOf("A", "B"), labels(UsageRanking.rank(apps, mapOf("pkg.gone" to 1_000_000L))))
    }

    @Test fun `ranking leaves the list it was given as it was`() {
        val apps = listOf(app("B", "pkg.b"), app("A", "pkg.a"))

        UsageRanking.rank(apps, mapOf("pkg.b" to 1L))

        assertEquals(listOf("B", "A"), labels(apps))
    }

    private fun labels(apps: List<InstalledApp>) = apps.map { it.facts.label }

    private fun app(label: String, pkg: String) = InstalledApp(AppFacts(packageName = pkg, activityName = "$pkg.Main", label = label), launcherInfo())

    /**
     * rank() reads only an app's package and label, never its LauncherActivityInfo, and the unit-test android.jar
     * has no way to make one (its constructor is hidden). So make an instance without running a constructor,
     * the way Objenesis and Mockito do.
     */
    private fun launcherInfo(): LauncherActivityInfo {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, LauncherActivityInfo::class.java) as LauncherActivityInfo
    }
}
