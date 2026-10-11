package com.charan.padstudio.core.icons

/** One start tag from an icon pack's appfilter.xml, as any XML pull parser reports it. */
data class XmlTag(val name: String, val attributes: Map<String, String>)

/**
 * The parts of the ADW/Nova icon-pack format PadStudio uses (Lawnchair docs "icon packs";
 * Arcticons' appfilter.xml): `<item component="ComponentInfo{pkg/cls}" drawable="name"/>`, plus the
 * fallback layers `<iconback img1=…>`, `<iconmask img1=…>`, `<iconupon img1=…>`, `<scale factor=…>`
 * and calendar icons `<calendar component=… prefix=…>`.
 */
data class AppFilter(
    val drawables: Map<String, String>,
    val calendars: Map<String, String> = emptyMap(),
    val iconBacks: List<String> = emptyList(),
    val iconMask: String? = null,
    val iconUpon: String? = null,
    val scale: Float = 1f,
) {
    /** Drawable for an activity; packs sometimes list only the package's main activity. */
    fun drawableFor(packageName: String, activityName: String): String? =
        drawables["$packageName/$activityName"]
            ?: drawables.entries.firstOrNull { it.key.substringBefore('/') == packageName }?.value

    companion object {
        private val COMPONENT = Regex("""ComponentInfo\{([^/}]+)/([^}]+)\}""")

        fun fromTags(tags: Sequence<XmlTag>): AppFilter {
            val drawables = linkedMapOf<String, String>()
            val calendars = linkedMapOf<String, String>()
            val backs = mutableListOf<String>()
            var mask: String? = null
            var upon: String? = null
            var scale = 1f
            for (tag in tags) {
                when (tag.name) {
                    "item" -> {
                        val component = component(tag.attributes["component"]) ?: continue
                        val drawable = tag.attributes["drawable"]?.takeIf { it.isNotBlank() } ?: continue
                        drawables.putIfAbsent(component, drawable)
                    }
                    "calendar" -> {
                        val component = component(tag.attributes["component"]) ?: continue
                        tag.attributes["prefix"]?.let { calendars[component] = it }
                    }
                    "iconback" -> backs += tag.attributes.filterKeys { it.startsWith("img") }.toSortedMap().values
                    "iconmask" -> mask = tag.attributes["img1"] ?: mask
                    "iconupon" -> upon = tag.attributes["img1"] ?: upon
                    "scale" -> scale = tag.attributes["factor"]?.toFloatOrNull() ?: scale
                }
            }
            return AppFilter(drawables, calendars, backs, mask, upon, scale)
        }

        /** "ComponentInfo{com.app/com.app.Main}" → "com.app/com.app.Main"; relative ".Main" is expanded. */
        fun component(raw: String?): String? {
            val match = raw?.let { COMPONENT.find(it) } ?: return null
            val pkg = match.groupValues[1].trim()
            val cls = match.groupValues[2].trim()
            return "$pkg/${if (cls.startsWith(".")) pkg + cls else cls}"
        }
    }
}
