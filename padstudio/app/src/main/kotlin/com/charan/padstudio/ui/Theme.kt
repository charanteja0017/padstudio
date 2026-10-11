package com.charan.padstudio.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Material You colours from the wallpaper (minSdk 31 always has them), like the HyperOS system UI. */
@Composable
fun PadStudioTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * Background and glyph colours for themed icons, taken from the same wallpaper palette Android
 * uses for its own themed icons: a light accent container with a dark glyph, inverted at night.
 */
data class ThemedIconColors(val background: Int, val glyph: Int)

fun themedIconColors(context: Context): ThemedIconColors {
    val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    // Dark plates sit on PadStudio's dark widget card, not the wallpaper, so they are a step lighter than
    // Android's own dark themed icons (accent1_800 measured as 48,48,48: invisible on the card).
    return if (night) {
        ThemedIconColors(context.getColor(android.R.color.system_accent1_600), context.getColor(android.R.color.system_accent1_50))
    } else {
        ThemedIconColors(context.getColor(android.R.color.system_accent1_100), context.getColor(android.R.color.system_accent1_700))
    }
}
