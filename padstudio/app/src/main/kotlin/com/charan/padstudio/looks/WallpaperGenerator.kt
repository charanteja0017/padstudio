package com.charan.padstudio.looks

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import kotlin.random.Random

/**
 * Calm wallpapers that match the accent colour: a diagonal two-tone gradient, a few large soft
 * light pools, and fine grain so it doesn't band on the tablet's display. Square at the screen's
 * long side, so portrait and landscape crop the same picture.
 */
object WallpaperGenerator {
    fun render(size: Int, seed: Long, accent: Int, dark: Boolean): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val random = Random(seed)
        val hsv = FloatArray(3).also { Color.colorToHSV(accent, it) }
        fun tone(hueShift: Float, saturation: Float, value: Float) =
            Color.HSVToColor(floatArrayOf((hsv[0] + hueShift + 360f) % 360f, saturation, value))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val from = if (dark) tone(-14f, 0.45f, 0.14f) else tone(-14f, 0.16f, 0.97f)
        val to = if (dark) tone(20f, 0.55f, 0.26f) else tone(20f, 0.30f, 0.86f)
        paint.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), from, to, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        repeat(4) {
            val cx = random.nextFloat() * size
            val cy = random.nextFloat() * size
            val radius = size * (0.25f + random.nextFloat() * 0.35f)
            val pool = if (dark) tone(random.nextFloat() * 60f - 30f, 0.6f, 0.5f) else tone(random.nextFloat() * 60f - 30f, 0.35f, 1f)
            val alpha = if (dark) 90 else 120
            paint.shader = RadialGradient(
                cx, cy, radius,
                Color.argb(alpha, Color.red(pool), Color.green(pool), Color.blue(pool)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, radius, paint)
        }

        paint.shader = null
        paint.strokeWidth = (size / 2000f).coerceAtLeast(1f)
        paint.color = if (dark) Color.argb(18, 255, 255, 255) else Color.argb(14, 0, 0, 0)
        val points = FloatArray(((size.toLong() * size) / 60L).toInt() * 2) { random.nextFloat() * size }
        canvas.drawPoints(points, paint)
        return bitmap
    }

    /** Sets home and lock screen; needs only the normal SET_WALLPAPER permission. */
    fun apply(context: Context, bitmap: Bitmap) {
        WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
    }

    fun longSide(context: Context): Int {
        val bounds = context.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds
        return maxOf(bounds.width(), bounds.height())
    }
}
