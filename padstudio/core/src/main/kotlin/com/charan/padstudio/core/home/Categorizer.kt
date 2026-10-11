package com.charan.padstudio.core.home

/** Where an app ended up and why; the Organize screen shows [reason] under each app. */
data class Placement(val app: AppFacts, val group: AppGroup, val reason: String)

/**
 * Sorts apps into [AppGroup]s. First rule that matches wins:
 * 1. the user's own choice, 2. PadStudio's curated list (package, then label), 3. flowerpot
 * package lists, 4. flowerpot intent rules (resolved on the tablet), 5. flowerpot code rules /
 * ApplicationInfo.category, 6. keyword heuristics, 7. Xiaomi/Android system packages → System,
 * otherwise More.
 */
class Categorizer(
    private val pots: List<FlowerpotPot>,
    private val overrides: Map<String, AppGroup> = emptyMap(),
) {
    private val potGroups = pots.mapNotNull { pot -> Flowerpot.GROUP_OF[pot.name]?.let { pot to it } }

    fun place(app: AppFacts): Placement {
        overrides[app.packageName]?.let { return Placement(app, it, "your choice") }
        CuratedApps.GROUP_OF[app.packageName]?.let { return Placement(app, it, "PadStudio list") }
        CuratedApps.GROUP_OF_LABEL[app.label]?.let { return Placement(app, it, "PadStudio list (by name)") }

        potGroups.firstOrNull { (pot, _) -> app.packageName in pot.packages }
            ?.let { (pot, group) -> return Placement(app, group, "flowerpot ${pot.name}") }
        potGroups.firstOrNull { (pot, _) -> pot.name in app.intentCategories }
            ?.let { (pot, group) -> return Placement(app, group, "flowerpot ${pot.name} (intent)") }

        if (app.isGame || app.androidCategory == AndroidCategory.GAME) return Placement(app, AppGroup.GAMES, "Android: game")
        androidGroup(app.androidCategory)?.let { return Placement(app, it, "Android category") }
        keywordGroup(app)?.let { return Placement(app, it, "name") }
        if (app.isSystem && SYSTEM_PREFIXES.any { app.packageName.startsWith(it) }) {
            return Placement(app, AppGroup.SYSTEM, "system app")
        }
        return Placement(app, AppGroup.MORE, "no rule matched")
    }

    fun placeAll(apps: List<AppFacts>): List<Placement> = apps.map(::place)

    private fun androidGroup(category: Int?): AppGroup? = when (category) {
        AndroidCategory.AUDIO, AndroidCategory.VIDEO -> AppGroup.WATCH_LISTEN
        AndroidCategory.IMAGE -> AppGroup.CREATE
        AndroidCategory.SOCIAL -> AppGroup.SOCIAL
        AndroidCategory.NEWS -> AppGroup.READING
        AndroidCategory.MAPS -> AppGroup.LIFE
        AndroidCategory.PRODUCTIVITY -> AppGroup.WORK
        AndroidCategory.ACCESSIBILITY -> AppGroup.TOOLS
        else -> null
    }

    private fun keywordGroup(app: AppFacts): AppGroup? {
        val text = "${app.packageName} ${app.label}".lowercase()
        return KEYWORDS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second
    }

    companion object {
        private val SYSTEM_PREFIXES = listOf("com.miui.", "com.xiaomi.", "com.android.", "com.google.android.")

        // Last resort only; `\b` keeps "facebook", "profit" or "bitmap" from matching "book", "fit", "map".
        private val KEYWORDS = listOf(
            Regex("""\b(upi|bank|pay|wallet|shop|deliver)|\bmart\b""") to AppGroup.SHOP_PAY,
            Regex("""\b(notes?|canvas|sketch|draw|paint|photo ?edit)""") to AppGroup.CREATE,
            Regex("""\b(pdf|reader|e?books?|news|comics?)\b""") to AppGroup.READING,
            Regex("""\b(classroom|learn|tutor|school|course|study)|\bexams?\b""") to AppGroup.STUDY,
            Regex("""\b(g?mail|meet|office|docs|sheets?|slides|calendar)\b""") to AppGroup.WORK,
            Regex("""(\btv\b|music|video|player|movie|stream|radio|podcast)""") to AppGroup.WATCH_LISTEN,
            Regex("""\b(chat|messeng|social)""") to AppGroup.SOCIAL,
            Regex("""\b(weather|fitness|health|maps?|travel|rides?|cabs?|rail|flights?)\b""") to AppGroup.LIFE,
        )
    }
}
