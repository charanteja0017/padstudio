package com.charan.padstudio.core.home

/**
 * Port of Lawnchair's flowerpot rule format (lawnchair/src/app/lawnchair/flowerpot, GPL-3.0-or-later;
 * format spec in Lawnchair's flowerpot/README.md). One file per Play Store category:
 *
 *   # comment          $1  version (azalea, must come first)
 *   :intent.action     ;intent.category      &codeRule|arg      com.package.name
 *
 * The rule files themselves are vendored in :app assets/flowerpot (credited in About).
 */
data class FlowerpotPot(
    val name: String,
    val packages: Set<String>,
    val intentActions: List<String>,
    val intentCategories: List<String>,
    val codeRules: List<List<String>>,
)

class FlowerpotFormatException(message: String) : IllegalArgumentException(message)

object Flowerpot {
    const val VERSION_AZALEA = 1

    fun parse(name: String, text: String): FlowerpotPot {
        var version: Int? = null
        val packages = linkedSetOf<String>()
        val actions = mutableListOf<String>()
        val categories = mutableListOf<String>()
        val codeRules = mutableListOf<List<String>>()
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            if (line.startsWith("$")) {
                if (version != null) throw FlowerpotFormatException("$name:${index + 1}: version declared twice")
                version = line.substring(1).toIntOrNull()
                    ?: throw FlowerpotFormatException("$name:${index + 1}: bad version '$line'")
                if (version != VERSION_AZALEA) throw FlowerpotFormatException("$name: unsupported flowerpot version $version")
                return@forEachIndexed
            }
            if (version == null) throw FlowerpotFormatException("$name:${index + 1}: version must come before rules")
            when (line[0]) {
                ':' -> actions += line.substring(1)
                ';' -> categories += line.substring(1)
                '&' -> codeRules += line.substring(1).split("|")
                else -> if (line[0].isLetter()) {
                    packages += line
                } else {
                    throw FlowerpotFormatException("$name:${index + 1}: unknown rule '${line[0]}'")
                }
            }
        }
        return FlowerpotPot(name, packages, actions, categories, codeRules)
    }

    /** Flowerpot category file → PadStudio group. Files not listed here are ignored. */
    val GROUP_OF = mapOf(
        "EDUCATION" to AppGroup.STUDY,
        "BOOKS_AND_REFERENCE" to AppGroup.READING,
        "NEWS" to AppGroup.READING,
        "COMICS" to AppGroup.READING,
        "BUSINESS" to AppGroup.WORK,
        "PRODUCTIVITY" to AppGroup.WORK,
        "ART_AND_DESIGN" to AppGroup.CREATE,
        "PHOTOGRAPHY" to AppGroup.CREATE,
        "COMMUNICATION" to AppGroup.SOCIAL,
        "SOCIAL" to AppGroup.SOCIAL,
        "DATING" to AppGroup.SOCIAL,
        "ENTERTAINMENT" to AppGroup.WATCH_LISTEN,
        "MUSIC" to AppGroup.WATCH_LISTEN,
        "VIDEO" to AppGroup.WATCH_LISTEN,
        "SHOPPING" to AppGroup.SHOP_PAY,
        "FINANCE" to AppGroup.SHOP_PAY,
        "FOOD_AND_DRINK" to AppGroup.SHOP_PAY,
        "GAME" to AppGroup.GAMES,
        "AUTO_AND_VEHICLES" to AppGroup.LIFE,
        "BEAUTY" to AppGroup.LIFE,
        "EVENTS" to AppGroup.LIFE,
        "HEALTH_AND_FITNESS" to AppGroup.LIFE,
        "HOUSE_AND_HOME" to AppGroup.LIFE,
        "LIFESTYLE" to AppGroup.LIFE,
        "MAPS_AND_NAVIGATION" to AppGroup.LIFE,
        "MEDICAL" to AppGroup.LIFE,
        "PARENTING" to AppGroup.LIFE,
        "SPORTS" to AppGroup.LIFE,
        "WEATHER" to AppGroup.LIFE,
        "TOOLS" to AppGroup.TOOLS,
        "PERSONALIZATION" to AppGroup.TOOLS,
        "LIBRARIES_AND_DEMO" to AppGroup.TOOLS,
    )
}

/** Loads the vendored rule files (core/src/main/resources/flowerpot) from the classpath. */
object FlowerpotResources {
    fun loadAll(): List<FlowerpotPot> = Flowerpot.GROUP_OF.keys.mapNotNull { name ->
        FlowerpotResources::class.java.getResourceAsStream("/flowerpot/$name")
            ?.bufferedReader()
            ?.use { Flowerpot.parse(name, it.readText()) }
    }
}
