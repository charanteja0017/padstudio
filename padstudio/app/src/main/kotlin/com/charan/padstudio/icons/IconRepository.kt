package com.charan.padstudio.icons

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.util.Xml
import androidx.core.graphics.createBitmap
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

    private fun original(app: InstalledApp): Icon {
        val res = app.info.activityInfo.iconResource
        return if (res != 0) {
            Icon.createWithResource(app.component.packageName, res)
        } else {
            Icon.createWithBitmap(render(app.info.getIcon(0)))
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

    private fun lawniconsGlyph(app: InstalledApp): Drawable? =
        runCatching { fromPack(LAWNICONS, app)?.loadDrawable(context) }.getOrNull()

    private fun badge(glyph: Drawable, accent: Int, onAccent: Int): Bitmap {
        val size = ICON_PX
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val radius = size * 0.3f
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, paint)
        val glyphCopy = glyph.mutate().constantState?.newDrawable()?.mutate() ?: glyph.mutate()
        glyphCopy.colorFilter = PorterDuffColorFilter(onAccent, PorterDuff.Mode.SRC_IN)
        // Adaptive-icon monochrome layers carry an 18dp safe-zone inset on each side of 108dp.
        val inset = if (glyph is AdaptiveIconDrawable || glyph.intrinsicWidth <= 0) 0 else (size * 0.18f).toInt()
        glyphCopy.setBounds(inset, inset, size - inset, size - inset)
        glyphCopy.draw(canvas)
        return bitmap
    }

    private fun render(drawable: Drawable): Bitmap {
        val bitmap = createBitmap(ICON_PX, ICON_PX)
        drawable.setBounds(0, 0, ICON_PX, ICON_PX)
        drawable.draw(Canvas(bitmap))
        return bitmap
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
        private val THEME_ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME")
    }
}
