package com.charan.padstudio.debug

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.util.Log
import androidx.core.content.edit

/**
 * Debug builds only: the appearance PadStudio is tested at (plan §12, §13) without changing a system setting.
 *
 *     adb shell am start -n com.charan.padstudio/.MainActivity --es theme dark|light|system
 *     adb shell am start -n com.charan.padstudio/.MainActivity --es fontscale 1.25|2|reset
 *     adb shell am start -n com.charan.padstudio/.MainActivity --es boldtext on|off|reset
 *
 * Each value is kept in a debug SharedPreferences file until reset, so recreate(), rotation and later launches
 * keep it. MainActivity applies it in attachBaseContext as a Configuration override: uiMode's night bits,
 * fontScale (Android's nonlinear text scaling follows from it) and fontWeightAdjustment (Bold text adds 300).
 * Only activities that wrap their base context with [wrap] follow it; release builds ignore all of it.
 */
internal object DebugOverrides {
    private const val TAG = "PadStudioDebug"
    private const val PREFS = "debug_appearance"
    private const val KEY_NIGHT = "night"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_FONT_WEIGHT = "font_weight_adjustment"

    fun isDebuggable(context: Context): Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** [base] with the stored appearance applied; [base] itself when nothing is overridden or the app isn't debuggable. */
    fun wrap(base: Context): Context {
        if (!isDebuggable(base)) return base
        val appearance = read(base)
        if (appearance == Appearance()) return base
        return base.createConfigurationContext(appearance.toConfiguration())
    }

    /**
     * Stores the `theme`, `fontscale` and `boldtext` values that [extra] returns; invalid ones are logged and
     * ignored. True when the stored appearance changed, so the caller recreates the activity to apply it.
     */
    fun applyExtras(context: Context, extra: (String) -> String?): Boolean {
        val old = read(context)
        val new = old.withExtras(extra) { Log.w(TAG, it) }
        if (new == old) return false
        write(context, new)
        Log.i(TAG, "appearance override: $new")
        return true
    }

    private fun read(context: Context): Appearance {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Appearance(
            night = if (prefs.contains(KEY_NIGHT)) prefs.getBoolean(KEY_NIGHT, false) else null,
            fontScale = if (prefs.contains(KEY_FONT_SCALE)) prefs.getFloat(KEY_FONT_SCALE, 1f) else null,
            fontWeightAdjustment = if (prefs.contains(KEY_FONT_WEIGHT)) prefs.getInt(KEY_FONT_WEIGHT, 0) else null,
        )
    }

    private fun write(context: Context, appearance: Appearance) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            val night = appearance.night
            val fontScale = appearance.fontScale
            val weight = appearance.fontWeightAdjustment
            if (night == null) remove(KEY_NIGHT) else putBoolean(KEY_NIGHT, night)
            if (fontScale == null) remove(KEY_FONT_SCALE) else putFloat(KEY_FONT_SCALE, fontScale)
            if (weight == null) remove(KEY_FONT_WEIGHT) else putInt(KEY_FONT_WEIGHT, weight)
        }
    }

    /** A delta: only overridden fields are set, and Configuration.updateFrom leaves every unset field as it is. */
    private fun Appearance.toConfiguration() = Configuration().also { config ->
        night?.let { config.uiMode = if (it) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
        fontScale?.let { config.fontScale = it }
        fontWeightAdjustment?.let { config.fontWeightAdjustment = it }
    }
}

/** The debug extras [Appearance.withExtras] reads. */
internal const val EXTRA_THEME = "theme"
internal const val EXTRA_FONT_SCALE = "fontscale"
internal const val EXTRA_BOLD_TEXT = "boldtext"

/** Android's Bold text: FONT_WEIGHT_BOLD − FONT_WEIGHT_NORMAL (700 − 400), the tablet's secure font_weight_adjustment. */
internal const val BOLD_TEXT_WEIGHT_ADJUSTMENT = 300

/** The text scales `fontscale` accepts: Android's own settings stop at 2; 3 leaves room to stress layouts. */
internal val FONT_SCALE_RANGE = 0.5f..3f

/** Appearance forced by debug extras; a null field follows the system. Pure, so it's unit-tested. */
internal data class Appearance(
    val night: Boolean? = null,
    val fontScale: Float? = null,
    val fontWeightAdjustment: Int? = null,
) {
    /** This appearance after the `theme`, `fontscale` and `boldtext` extras; an absent extra keeps its field. */
    fun withExtras(extra: (String) -> String?, onInvalid: (String) -> Unit = {}): Appearance {
        val theme = extra(EXTRA_THEME)
        val scale = extra(EXTRA_FONT_SCALE)
        val bold = extra(EXTRA_BOLD_TEXT)
        return Appearance(
            night = if (theme == null) night else parseTheme(theme).applyTo(night, onInvalid),
            fontScale = if (scale == null) fontScale else parseFontScale(scale).applyTo(fontScale, onInvalid),
            fontWeightAdjustment = if (bold == null) fontWeightAdjustment else parseBoldText(bold).applyTo(fontWeightAdjustment, onInvalid),
        )
    }
}

/** What one debug extra asks for: a value, back to the system's, or nothing (it couldn't be read). */
internal sealed interface OverrideChange<out T> {
    data class SetTo<T>(val value: T) : OverrideChange<T>

    data object Reset : OverrideChange<Nothing>

    data class Invalid(val message: String) : OverrideChange<Nothing>
}

/** `theme`: dark, light, or system (reset also works). */
internal fun parseTheme(value: String): OverrideChange<Boolean> = when (value.trim().lowercase()) {
    "dark" -> OverrideChange.SetTo(true)
    "light" -> OverrideChange.SetTo(false)
    "system", "reset" -> OverrideChange.Reset
    else -> OverrideChange.Invalid("theme \"$value\" ignored: use dark, light or system")
}

/** `fontscale`: a number in [FONT_SCALE_RANGE] (1.25 is the tablet's setting), or reset. */
internal fun parseFontScale(value: String): OverrideChange<Float> {
    val text = value.trim()
    if (text.equals("reset", ignoreCase = true)) return OverrideChange.Reset
    val scale = text.toFloatOrNull()
    return if (scale != null && scale in FONT_SCALE_RANGE) {
        OverrideChange.SetTo(scale)
    } else {
        OverrideChange.Invalid("fontscale \"$value\" ignored: use a number from 0.5 to 3, or reset")
    }
}

/** `boldtext`: on (+300, like Settings → Bold text), off (0, even if the system's Bold text is on), or reset. */
internal fun parseBoldText(value: String): OverrideChange<Int> = when (value.trim().lowercase()) {
    "on" -> OverrideChange.SetTo(BOLD_TEXT_WEIGHT_ADJUSTMENT)
    "off" -> OverrideChange.SetTo(0)
    "reset" -> OverrideChange.Reset
    else -> OverrideChange.Invalid("boldtext \"$value\" ignored: use on, off or reset")
}

private fun <T> OverrideChange<T>.applyTo(current: T?, onInvalid: (String) -> Unit): T? = when (this) {
    is OverrideChange.SetTo -> value
    OverrideChange.Reset -> null
    is OverrideChange.Invalid -> {
        onInvalid(message)
        current
    }
}
