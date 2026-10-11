package com.charan.padstudio.icons

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.InsetDrawable
import android.util.Xml
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import com.charan.padstudio.core.icons.AppFilter
import com.charan.padstudio.core.icons.XmlTag
import com.charan.padstudio.data.IconStyle
import com.charan.padstudio.data.InstalledApp
import java.util.concurrent.ConcurrentHashMap
import org.xmlpull.v1.XmlPullParser

/** An installed icon pack: an app answering the ADW or Nova theme intent. */
data class IconPack(val packageName: String, val label: String)

/**
 * App icons in the chosen style, as [Icon]s so widgets can show them (RemoteViews needs Icon or
 * Bitmap). Original and icon-pack icons are resource references, which keeps widget updates small;
 * only themed icons are drawn into small bitmaps.
 */
class IconRepository(private val context: Context) {
    private val filters = ConcurrentHashMap<String, AppFilter?>()
    private val themed = ConcurrentHashMap<String, Bitmap>()

    fun installedPacks(): List<IconPack> {
        val pm = context.packageManager
        return THEME_ACTIONS.flatMap { action -> pm.queryIntentActivities(Intent(action), 0) }
            .map { IconPack(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun icon(app: InstalledApp, style: IconStyle, pack: String?, accent: Int, onAccent: Int): Icon = when (style) {
        IconStyle.ORIGINAL -> original(app)
        IconStyle.ICON_PACK -> pack?.let { fromPack(it, app) } ?: original(app)
        IconStyle.THEMED -> themedIcon(app, accent, onAccent)
    }

    fun drawable(app: InstalledApp, style: IconStyle, pack: String?, accent: Int, onAccent: Int): Drawable =
        icon(app, style, pack, accent, onAccent).loadDrawable(context) ?: app.info.getIcon(0)

    /** Which branch [icon] takes for this app; for the debug icon dump. */
    fun source(app: InstalledApp, style: IconStyle, pack: String?): String {
        val original = if (app.info.getIcon(0) is AdaptiveIconDrawable) "original/adaptive" else "original/legacy-plate"
        return when (style) {
            IconStyle.ORIGINAL -> original
            IconStyle.ICON_PACK -> if (pack != null && fromPack(pack, app) != null) "pack" else original
            IconStyle.THEMED -> when {
                monochrome(app) != null -> "themed/monochrome"
                lawniconsGlyph(app) != null -> "themed/lawnicons"
                else -> "themed→$original"
            }
        }
    }

    private fun original(app: InstalledApp): Icon {
        val res = app.info.activityInfo.iconResource
        val drawable = app.info.getIcon(0)
        // Adaptive icons stay resource references (small widget updates); legacy ones need a plate.
        return if (res != 0 && drawable is AdaptiveIconDrawable) {
            Icon.createWithResource(app.component.packageName, res)
        } else {
            Icon.createWithBitmap(render(drawable))
        }
    }

    private fun fromPack(pack: String, app: InstalledApp): Icon? {
        val filter = filters.getOrPut(pack) { loadFilter(pack) } ?: return null
        val name = filter.drawableFor(app.component.packageName, app.component.className) ?: return null
        val res = packResources(pack) ?: return null
        @Suppress("DiscouragedApi") // icon packs are addressed by drawable name, by design of the format
        val id = res.getIdentifier(name, "drawable", pack)
        return if (id != 0) Icon.createWithResource(pack, id) else null
    }

    /** Monochrome layer (API 33) or Lawnicons glyph, tinted, on an accent-coloured rounded square. */
    private fun themedIcon(app: InstalledApp, accent: Int, onAccent: Int): Icon {
        val key = "${app.component.flattenToShortString()}|$accent|$onAccent"
        val bitmap = themed.getOrPut(key) {
            val glyph = monochrome(app) ?: lawniconsGlyph(app)
            if (glyph == null) render(app.info.getIcon(0)) else badge(glyph, accent, onAccent)
        }
        return Icon.createWithBitmap(bitmap)
    }

    private fun monochrome(app: InstalledApp): Drawable? =
        if (android.os.Build.VERSION.SDK_INT >= 33) (app.info.getIcon(0) as? AdaptiveIconDrawable)?.monochrome else null

    /** Lawnicons ships whole adaptive icons; the glyph is their monochrome (or foreground) layer. */
    private fun lawniconsGlyph(app: InstalledApp): Drawable? =
        when (val icon = runCatching { fromPack(LAWNICONS, app)?.loadDrawable(context) }.getOrNull()) {
            is AdaptiveIconDrawable -> (if (android.os.Build.VERSION.SDK_INT >= 33) icon.monochrome else null) ?: icon.foreground
            else -> icon
        }

    private fun badge(glyph: Drawable, accent: Int, onAccent: Int): Bitmap {
        val size = ICON_PX
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val radius = size * 0.3f
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, paint)
        val glyphCopy = glyph.mutate().constantState?.newDrawable()?.mutate() ?: glyph.mutate()
        glyphCopy.colorFilter = PorterDuffColorFilter(onAccent, PorterDuff.Mode.SRC_IN)
        // Glyphs here are adaptive-icon layers: 108dp, of which an icon shows the middle 72dp. Drawn at
        // 150% and clipped to the plate they match what the launcher shows (drawn 1:1 they come out tiny).
        val bleed = size / 4
        canvas.clipPath(Path().apply { addRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, Path.Direction.CW) })
        glyphCopy.setBounds(-bleed, -bleed, size + bleed, size + bleed)
        glyphCopy.draw(canvas)
        return bitmap
    }

    private fun render(drawable: Drawable): Bitmap {
        val bitmap = createBitmap(ICON_PX, ICON_PX)
        val canvas = Canvas(bitmap)
        val shaped = when {
            drawable is AdaptiveIconDrawable -> drawable
            // Square artwork (most legacy game icons): full size, cut to the system icon shape.
            sample(drawable).let { opaqueBorder(it) >= FULL_BLEED } -> {
                canvas.clipPath(systemShape(ICON_PX))
                drawable
            }
            // Artwork with its own transparent padding: on a plate, as launchers do; drawn bare it floats
            // as a small glyph.
            else -> onPlate(drawable)
        }
        shaped.setBounds(0, 0, ICON_PX, ICON_PX)
        shaped.draw(canvas)
        return bitmap
    }

    private fun sample(drawable: Drawable): IntArray {
        val bitmap = createBitmap(SAMPLE_PX, SAMPLE_PX)
        drawable.setBounds(0, 0, SAMPLE_PX, SAMPLE_PX)
        drawable.draw(Canvas(bitmap))
        return IntArray(SAMPLE_PX * SAMPLE_PX).also { bitmap.getPixels(it, 0, SAMPLE_PX, 0, 0, SAMPLE_PX, SAMPLE_PX) }
    }

    /** Share of the outermost pixel ring that is opaque: near 1 for square artwork, 0 for padded glyphs. */
    private fun opaqueBorder(pixels: IntArray): Float {
        val last = SAMPLE_PX - 1
        val ring = pixels.indices.filter { val x = it % SAMPLE_PX; val y = it / SAMPLE_PX; x == 0 || y == 0 || x == last || y == last }
        return ring.count { Color.alpha(pixels[it]) > 200 }.toFloat() / ring.size
    }

    private fun systemShape(size: Int): Path =
        AdaptiveIconDrawable(Color.TRANSPARENT.toDrawable(), Color.TRANSPARENT.toDrawable()).run {
            setBounds(0, 0, size, size)
            iconMask
        }

    /** White plate for dark artwork, near-black for light artwork (a white glyph on white vanishes). */
    private fun onPlate(legacy: Drawable): AdaptiveIconDrawable {
        var weight = 0f
        var luminance = 0f
        for (pixel in sample(legacy)) {
            val alpha = Color.alpha(pixel) / 255f
            weight += alpha
            luminance += alpha * Color.luminance(pixel)
        }
        val plate = if (weight > 0f && luminance / weight > LIGHT_ARTWORK) Color.rgb(32, 33, 36) else Color.WHITE
        // Foreground layers are 108dp with the visible part in the middle 72dp; the inset keeps the
        // artwork inside it with a little air.
        return AdaptiveIconDrawable(plate.toDrawable(), InsetDrawable(legacy, LEGACY_INSET))
    }

    private fun packResources(pack: String): Resources? =
        runCatching { context.packageManager.getResourcesForApplication(pack) }.getOrNull()

    /** appfilter.xml from res/xml (compiled) or assets/ (plain), whichever the pack ships. */
    private fun loadFilter(pack: String): AppFilter? {
        val res = packResources(pack) ?: return null
        @Suppress("DiscouragedApi")
        val xmlId = res.getIdentifier("appfilter", "xml", pack)
        val parser: XmlPullParser = if (xmlId != 0) {
            res.getXml(xmlId)
        } else {
            val stream = runCatching { res.assets.open("appfilter.xml") }.getOrNull() ?: return null
            Xml.newPullParser().apply { setInput(stream, null) }
        }
        return AppFilter.fromTags(tags(parser))
    }

    private fun tags(parser: XmlPullParser): Sequence<XmlTag> = sequence {
        while (true) {
            val event = runCatching { parser.next() }.getOrNull() ?: break
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.START_TAG) {
                val attributes = (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
                yield(XmlTag(parser.name, attributes))
            }
        }
    }

    fun clear() {
        filters.clear()
        themed.clear()
    }

    companion object {
        const val LAWNICONS = "app.lawnchair.lawnicons"
        const val ICON_PX = 96
        private const val SAMPLE_PX = 24
        private const val LIGHT_ARTWORK = 0.6f
        private const val LEGACY_INSET = 0.25f
        private const val FULL_BLEED = 0.6f
        private val THEME_ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME")
    }
}
