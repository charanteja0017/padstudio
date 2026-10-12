package com.charan.padstudio.debug

import android.content.Context
import android.util.Log
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.charan.padstudio.pen.PenPlan
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Debug builds only: `--es dump profile` and `--es dump colors` (plan §13, rows "Pen behaviour" and "Contrast").
 * - files/profile-dump.json: the pen profile JSON that Apply hands to Key Mapper, byte for byte;
 * - files/colors.json: every Material colour role on screen.
 *
 * Read them with `adb shell run-as com.charan.padstudio cat files/<name>`; each write is logged under [TAG].
 */
internal object DebugDumps {
    const val TAG = "PadStudioDebug"
    const val PROFILE_FILE = "profile-dump.json"
    const val COLORS_FILE = "colors.json"

    /** How long `dump profile` waits for the first pen plan (the app list has to load first). */
    private const val PLAN_WAIT_MS = 30_000L

    /** `dump colors` waits for the theme to settle (it may still be reading its settings) before writing. */
    const val COLORS_SETTLE_MS = 500L

    /**
     * Writes the first pen plan's JSON to files/profile-dump.json. It is the same string Apply hands over:
     * KeyMapperBridge.importIntent writes plan.json with writeText (UTF-8), and so does this. Logs size and SHA-256.
     */
    suspend fun dumpProfile(context: Context, plans: Flow<PenPlan?>) {
        val plan = withTimeoutOrNull(PLAN_WAIT_MS) { plans.filterNotNull().first() }
        if (plan == null) {
            Log.w(TAG, "dump profile: no pen plan after ${PLAN_WAIT_MS / 1000} s")
            return
        }
        val summary = withContext(Dispatchers.IO) {
            val file = File(context.filesDir, PROFILE_FILE).apply { writeText(plan.json) }
            "${file.path}, ${file.length()} bytes, sha256 ${sha256Hex(file.readBytes())}"
        }
        Log.i(TAG, "dump profile: $summary (plan ${plan.hash})")
    }

    suspend fun writeColors(context: Context, json: String): String = withContext(Dispatchers.IO) {
        File(context.filesDir, COLORS_FILE).apply { writeText(json) }.path
    }
}

/**
 * Writes files/colors.json for each `--es dump colors` request ([requests] counts them; 0 writes nothing) from the
 * MaterialTheme where it is composed, once the colours have held for [DebugDumps.COLORS_SETTLE_MS].
 */
@Composable
internal fun ColorsDumpEffect(requests: Int) {
    if (requests == 0) return
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val roles = MaterialTheme.colorScheme.roleHexes()
    val night = configuration.isNightModeActive
    val fontScale = configuration.fontScale
    val weight = configuration.fontWeightAdjustment
    LaunchedEffect(requests, roles, night, fontScale, weight) {
        delay(DebugDumps.COLORS_SETTLE_MS)
        val path = DebugDumps.writeColors(context, colorsJson(night, fontScale, weight, roles))
        Log.i(DebugDumps.TAG, "dump colors: $path, ${roles.size} roles, night $night")
    }
}

/** colors.json: the night mode, text scale and weight adjustment in force, then every role as a hex colour. */
internal fun colorsJson(night: Boolean, fontScale: Float, fontWeightAdjustment: Int, roles: Map<String, String>): String =
    buildString {
        append("{\n")
        append("  \"night\": ").append(night).append(",\n")
        append("  \"fontScale\": ").append(fontScale).append(",\n")
        append("  \"fontWeightAdjustment\": ").append(fontWeightAdjustment).append(",\n")
        append("  \"roles\": {\n")
        roles.entries.forEachIndexed { index, (name, hex) ->
            append("    \"").append(name).append("\": \"").append(hex).append('"')
            append(if (index < roles.size - 1) ",\n" else "\n")
        }
        append("  }\n}\n")
    }

/** Every ColorScheme role (48 in Material 3 1.4.0), in the order ColorScheme declares them. */
internal fun ColorScheme.roleHexes(): Map<String, String> = linkedMapOf(
    "primary" to primary,
    "onPrimary" to onPrimary,
    "primaryContainer" to primaryContainer,
    "onPrimaryContainer" to onPrimaryContainer,
    "inversePrimary" to inversePrimary,
    "secondary" to secondary,
    "onSecondary" to onSecondary,
    "secondaryContainer" to secondaryContainer,
    "onSecondaryContainer" to onSecondaryContainer,
    "tertiary" to tertiary,
    "onTertiary" to onTertiary,
    "tertiaryContainer" to tertiaryContainer,
    "onTertiaryContainer" to onTertiaryContainer,
    "background" to background,
    "onBackground" to onBackground,
    "surface" to surface,
    "onSurface" to onSurface,
    "surfaceVariant" to surfaceVariant,
    "onSurfaceVariant" to onSurfaceVariant,
    "surfaceTint" to surfaceTint,
    "inverseSurface" to inverseSurface,
    "inverseOnSurface" to inverseOnSurface,
    "error" to error,
    "onError" to onError,
    "errorContainer" to errorContainer,
    "onErrorContainer" to onErrorContainer,
    "outline" to outline,
    "outlineVariant" to outlineVariant,
    "scrim" to scrim,
    "surfaceBright" to surfaceBright,
    "surfaceDim" to surfaceDim,
    "surfaceContainer" to surfaceContainer,
    "surfaceContainerHigh" to surfaceContainerHigh,
    "surfaceContainerHighest" to surfaceContainerHighest,
    "surfaceContainerLow" to surfaceContainerLow,
    "surfaceContainerLowest" to surfaceContainerLowest,
    "primaryFixed" to primaryFixed,
    "primaryFixedDim" to primaryFixedDim,
    "onPrimaryFixed" to onPrimaryFixed,
    "onPrimaryFixedVariant" to onPrimaryFixedVariant,
    "secondaryFixed" to secondaryFixed,
    "secondaryFixedDim" to secondaryFixedDim,
    "onSecondaryFixed" to onSecondaryFixed,
    "onSecondaryFixedVariant" to onSecondaryFixedVariant,
    "tertiaryFixed" to tertiaryFixed,
    "tertiaryFixedDim" to tertiaryFixedDim,
    "onTertiaryFixed" to onTertiaryFixed,
    "onTertiaryFixedVariant" to onTertiaryFixedVariant,
).mapValues { (_, color) -> argbHex(color.toArgb()) }

/** #RRGGBB for an opaque colour, #AARRGGBB otherwise; upper case, like tokens.json. */
internal fun argbHex(argb: Int): String {
    val rgb = (argb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()
    val alpha = argb ushr 24
    return if (alpha == 0xFF) "#$rgb" else "#" + alpha.toString(16).padStart(2, '0').uppercase() + rgb
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
