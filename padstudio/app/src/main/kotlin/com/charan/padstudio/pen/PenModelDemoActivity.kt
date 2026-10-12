package com.charan.padstudio.pen

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Debug builds only (it closes itself otherwise): [PenModelView] on a dark violet card like the Today
 * hero, with a button for each clip and Pause, and the reported markers drawn as dots, for on-device
 * screenshots:
 *
 *     adb shell am start -n com.charan.padstudio/.pen.PenModelDemoActivity
 *
 * Optional extras: `--es clip squeeze` plays a clip at start, `--ez paused true` starts paused,
 * `--ez fallback true` draws the 2D pen instead of the 3D one. The status line names the renderer in
 * use (also logged under the tag `PenModelView`).
 */
class PenModelDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            finish()
            return
        }
        enableEdgeToEdge()
        val startClip = intent.getStringExtra("clip")?.takeIf { it in PenModel.clips } ?: "idle"
        val startPaused = intent.getBooleanExtra("paused", false)
        val force2d = intent.getBooleanExtra("fallback", false)
        setContent {
            MaterialTheme(colorScheme = DemoColors) {
                PenModelDemo(startClip, startPaused, force2d)
            }
        }
    }
}

private val DemoColors = darkColorScheme(
    primary = Color(0xFFBBAAFF),
    onPrimary = Color(0xFF211B36),
    background = Color(0xFF141419),
    surface = Color(0xFF141419),
    onSurface = Color(0xFFF5F4FD),
    onSurfaceVariant = Color(0xFFADB1C5),
    outline = Color(0xFF515566),
)

private val CardTop = Color(0xFF26253A)
private val CardBottom = Color(0xFF1C1C26)
private val CardGlow = Color(0xFF6A5BA8)
private val Accent = Color(0xFFBBAAFF)

private val MarkerLabels = mapOf(
    "marker_squeeze" to "Squeeze",
    "marker_slide_top" to "Slide top",
    "marker_slide_bottom" to "Slide bottom",
    "marker_double_tap" to "Double tap",
    "marker_nib" to "Nib",
)

private val ClipLabels = mapOf(
    "idle" to "Idle",
    "squeeze" to "Squeeze",
    "slide_up" to "Slide up",
    "slide_down" to "Slide down",
    "double_tap" to "Double tap",
    "write" to "Write",
)

@Composable
private fun PenModelDemo(startClip: String, startPaused: Boolean, force2d: Boolean) {
    var clip by rememberSaveable { mutableStateOf(startClip) }
    var replay by rememberSaveable { mutableIntStateOf(0) }
    var playing by rememberSaveable { mutableStateOf(!startPaused) }
    var renderer by remember { mutableStateOf("starting") }
    var markers by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    val nibTrail = remember { mutableStateListOf<Offset>() }
    var writeStarted by remember { mutableLongStateOf(if (startClip == "write") System.nanoTime() else 0L) }
    val debug = remember(force2d) { PenModelDebug(force2d) { renderer = it } }
    val resolver = LocalContext.current.contentResolver
    val animatorScale = remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }

    val play: (String) -> Unit = { name ->
        clip = name
        replay++
        nibTrail.clear()
        writeStarted = if (name == "write") System.nanoTime() else 0L
    }
    val onMarkers: (Map<String, Offset>) -> Unit = { reported ->
        markers = reported
        // The ink a screen would draw: the nib's path while `write` plays.
        val nib = reported["marker_nib"]
        if (nib != null && writeStarted != 0L && System.nanoTime() - writeStarted < WRITE_TRAIL_NANOS) nibTrail += nib
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            val card: @Composable (Modifier) -> Unit = { modifier ->
                PenCard(modifier, clip, playing, replay, debug, markers, nibTrail, onMarkers)
            }
            val controls: @Composable (Modifier) -> Unit = { modifier ->
                Controls(modifier, renderer, clip, playing, animatorScale, play) { playing = !playing }
            }
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    controls(Modifier.weight(0.36f).fillMaxHeight())
                    card(Modifier.weight(0.64f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    card(Modifier.weight(0.62f).fillMaxWidth())
                    controls(Modifier.weight(0.38f).fillMaxWidth())
                }
            }
        }
    }
}

private const val WRITE_TRAIL_NANOS = 1_800_000_000L

@Composable
private fun PenCard(
    modifier: Modifier,
    clip: String,
    playing: Boolean,
    replay: Int,
    debug: PenModelDebug,
    markers: Map<String, Offset>,
    nibTrail: List<Offset>,
    onMarkers: (Map<String, Offset>) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color(0xFFF5F4FD), fontSize = 13.sp)
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(CardTop, CardBottom)))
            .background(Brush.radialGradient(listOf(CardGlow.copy(alpha = 0.32f), Color.Transparent))),
    ) {
        CompositionLocalProvider(LocalPenModelDebug provides debug) {
            PenModelView(Modifier.fillMaxSize(), clip = clip, playing = playing, onMarkers = onMarkers, replay = replay)
        }
        // What a screen does with onMarkers: dots and labels at the reported points, ink from the nib.
        Canvas(Modifier.fillMaxSize()) {
            if (nibTrail.size > 1) {
                val ink = Path().apply {
                    moveTo(nibTrail[0].x, nibTrail[0].y)
                    for (i in 1 until nibTrail.size) lineTo(nibTrail[i].x, nibTrail[i].y)
                }
                drawPath(ink, Accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            for ((name, at) in markers) {
                drawCircle(Accent, radius = 5.dp.toPx(), center = at)
                drawCircle(Accent, radius = 9.dp.toPx(), center = at, alpha = 0.5f, style = Stroke(1.5.dp.toPx()))
                val label = measurer.measure(MarkerLabels[name] ?: name, labelStyle)
                drawText(label, topLeft = Offset(at.x + 14.dp.toPx(), at.y - label.size.height / 2f))
            }
        }
    }
}

@Composable
private fun Controls(
    modifier: Modifier,
    renderer: String,
    clip: String,
    playing: Boolean,
    animatorScale: Float,
    onClip: (String) -> Unit,
    onPlayPause: () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("YOUR PEN", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text("Focus Pen Pro", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "PenModelView demo · renderer: $renderer",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Clip: ${ClipLabels[clip] ?: clip} · ${if (playing) "playing" else "paused"} · animator scale $animatorScale" +
                if (animatorScale == 0f) " (reduced motion: still poses)" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        for (row in PenModel.clips.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (name in row) {
                    OutlinedButton(onClick = { onClip(name) }) { Text(ClipLabels[name] ?: name) }
                }
            }
        }
        Button(onClick = onPlayPause, modifier = Modifier.width(160.dp)) { Text(if (playing) "Pause" else "Play") }
    }
}
