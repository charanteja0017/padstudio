package com.charan.padstudio.core.home

/** The folders PadStudio sorts apps into (PLAN D6), in display order. */
enum class AppGroup(val title: String) {
    STUDY("Study"),
    WORK("Work"),
    READING("Reading"),
    CREATE("Create"),
    SOCIAL("Social"),
    WATCH_LISTEN("Watch & Listen"),
    SHOP_PAY("Shop & Pay"),
    GAMES("Games"),
    LIFE("Life"),
    TOOLS("Tools"),
    SYSTEM("System"),
    MORE("More"),

    /** Preinstalled apps the user never wants to see; kept out of every widget instead of uninstalled. */
    HIDDEN("Hidden"),
}

/**
 * What the app layer knows about one launchable activity. [intentCategories] lists the flowerpot
 * category files whose intent rules (APP_BROWSER, APP_FILES, …) resolved to this package on the
 * tablet; resolving needs PackageManager, so it happens in :app and arrives here as plain data.
 */
data class AppFacts(
    val packageName: String,
    val activityName: String,
    val label: String,
    /** ApplicationInfo.category (API 26+), or null when undefined. */
    val androidCategory: Int? = null,
    val isGame: Boolean = false,
    val isSystem: Boolean = false,
    val intentCategories: Set<String> = emptySet(),
)

/** ApplicationInfo.CATEGORY_* values (stable since API 26). */
object AndroidCategory {
    const val GAME = 0
    const val AUDIO = 1
    const val VIDEO = 2
    const val IMAGE = 3
    const val SOCIAL = 4
    const val NEWS = 5
    const val MAPS = 6
    const val PRODUCTIVITY = 7
    const val ACCESSIBILITY = 8
}
