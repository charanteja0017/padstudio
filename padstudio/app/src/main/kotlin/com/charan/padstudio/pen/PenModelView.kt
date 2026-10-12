package com.charan.padstudio.pen

import android.app.ActivityManager
import android.content.Context
import android.database.ContentObserver
import android.opengl.EGLContext
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.google.android.filament.Engine
import com.google.android.filament.LightManager
import com.google.android.filament.Scene
import com.google.android.filament.View
import io.github.sceneview.FrameRatePolicy
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.createEglContext
import io.github.sceneview.createEngine
import io.github.sceneview.createEnvironment
import io.github.sceneview.environment.Environment
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.LightNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberRenderInvalidator
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberView
import io.github.sceneview.utils.destroy
import io.github.sceneview.utils.readBuffer
import io.github.sceneview.utils.worldToScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import io.github.sceneview.node.ModelNode as SceneModelNode

/** Names inside `assets/pen/focus_pen_pro.glb` (see `Branding/pen3d/README.md`). */
object PenModel {
    /** `idle` loops; every other clip plays once and hands back to `idle`. */
    val clips = listOf("idle", "squeeze", "slide_up", "slide_down", "double_tap", "write")

    /**
     * The marker empties whose screen positions [PenModelView] reports through `onMarkers`. `marker_top` (the end
     * cap, round 2) gives screens the pen's axis without assuming its length; a model without it (older builds,
     * an owner's model missing it) simply doesn't report it, and screens fall back to `marker_double_tap`.
     */
    val markers = listOf("marker_squeeze", "marker_slide_top", "marker_slide_bottom", "marker_double_tap", "marker_nib", "marker_top")
}

/**
 * The live Focus Pen Pro (redesign plan decision 15), drawn on a transparent surface so the card behind
 * it shows through. The pen lies on the reference design's diagonal (cap upper right, nib lower left,
 * about 36° from vertical, tipped 12° toward the viewer) and fills the view's height. A key light from
 * the upper left, a lavender rim light behind its lower right and SceneView's bundled neutral
 * image-based light (an asset in the APK; nothing is fetched) give the satin white body its shape on
 * a dark violet card.
 *
 * - [clip]: `idle` loops. `squeeze`, `slide_up`, `slide_down`, `double_tap` and `write` play once each
 *   time the value changes to them (or [replay] changes), then the pen returns to `idle`. Changing back
 *   to `idle` doesn't cut a gesture short. Unknown names count as `idle`.
 * - [playing]: `false` holds the current pose; a close-up already under way still plays out (see below).
 * - Gestures close in on the nib section: while `squeeze`, `slide_up`, `slide_down` or `double_tap` plays, the camera
 *   eases (450 ms) from the whole-pen framing to a 220 mm close-up of the grip band, holds it for the clip's length
 *   and eases back out. `write` swings the whole pen, so it doesn't zoom. The timeline is [ZoomTimeline]; it
 *   freezes while the view can't be seen.
 * - Animation frames also stop while the view is scrolled out of sight, while the screen is below
 *   STARTED, and when the system animator duration scale is 0 ("Remove animations"); then the pen holds
 *   a still pose (a gesture shows its key pose for the gesture's length, with the close-up cut in and out
 *   rather than eased). Leaving the composition releases the whole Filament engine.
 * - [onMarkers]: where `marker_squeeze`, `marker_slide_top`, `marker_slide_bottom`, `marker_double_tap`
 *   and `marker_nib` are, in this view's pixels, reported whenever they move (every animated frame, a
 *   resize). Draw callouts from these. Called on the main thread.
 * - If 3D can't run (no OpenGL ES 3, Filament's libraries or the model fail to load), a 2D pen drawn
 *   from the motion kit's Focus Pen Pro takes its place with the same framing, effects and markers. The
 *   2D pen is also what shows for the first frames while Filament starts.
 * - Touches pass through to whatever contains the view; it has no gestures of its own.
 *
 * Cost: SceneView brings Filament's native libraries, 6.7 MB for arm64-v8a (filament-jni 3.06 MB,
 * gltfio-jni 3.10 MB, filament-utils-jni 0.56 MB) and about as much again for each other ABI.
 *
 * @param replay bump to play [clip] again from the start (the four parameters before it are the
 *   cross-task contract; this one is an optional addition).
 * @param onBusyChange `true` once when a gesture or `write` starts, `false` once when the pen is back at rest framing
 *   (after the close-up has eased out; for `write`, when its clip ends). Screens hide their callouts, and stop
 *   re-placing them, while busy (round 2 contract). A gesture that starts while another is busy doesn't report
 *   again. Reports `false` if the view leaves the composition while busy.
 */
@Composable
fun PenModelView(
    modifier: Modifier,
    clip: String = "idle",
    playing: Boolean = true,
    onMarkers: (Map<String, Offset>) -> Unit = {},
    replay: Int = 0,
    onBusyChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val busy by rememberUpdatedState(onBusyChange)
    val debug = LocalPenModelDebug.current
    val report by rememberUpdatedState(onMarkers)
    val motionAllowed = rememberAnimationsEnabled()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val started = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    var visible by remember { mutableStateOf(true) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var stage by remember {
        mutableStateOf(
            when {
                debug.force2d -> Stage.Fallback("forced by the debug switch")
                !supportsGles3(context) -> Stage.Fallback("no OpenGL ES 3.0")
                else -> Stage.Waiting
            },
        )
    }
    val request = ClipRequest(clip.takeIf { it in PenModel.clips } ?: IDLE, replay)
    val live = playing && visible && started

    // One zoom timeline for both pens, and the busy signal: its edges are reported (true once at a start, false once
    // z is back to 0 after the clip, false at once if the view leaves while busy). Time freezes while the view can't
    // be seen, so a gesture isn't over before anyone watched it.
    val zoom = remember { ZoomState { busy(it) } }
    DisposableEffect(zoom) { onDispose { zoom.release() } }
    LaunchedEffect(request) {
        if (request.clip != IDLE) {
            zoom.start(frameClockMs(), GESTURE_MS, zooms = request.clip != CLIP_WRITE, reducedMotion = !motionAllowed)
        }
    }
    val frozen = !(visible && started)
    LaunchedEffect(frozen) {
        if (frozen) zoom.pause(frameClockMs()) else zoom.resume(frameClockMs())
    }

    // Filament starts after the 2D pen has reached the screen, and only once the view is visible: the
    // engine, shaders and model take a moment on the main thread.
    LaunchedEffect(stage, visible, started) {
        if (stage == Stage.Waiting && visible && started) {
            withFrameNanos { }
            stage = Stage.Loading
        }
    }
    LaunchedEffect(stage) {
        debug.onRenderer(stage.label)
        Log.i(TAG, "renderer: ${stage.label}")
    }

    Box(modifier.onSizeChanged { size = it }.onVisibilityChanged(minFractionVisible = 0.01f) { visible = it }) {
        if (stage == Stage.Loading || stage == Stage.Ready) {
            Pen3d(
                modifier = Modifier.matchParentSize(),
                size = size,
                request = request,
                live = live,
                motionAllowed = motionAllowed,
                zoom = zoom,
                report = report,
                onReady = { if (stage == Stage.Loading) stage = Stage.Ready },
                onFailure = { error ->
                    Log.w(TAG, "3D pen failed; drawing the 2D pen instead", error)
                    stage = Stage.Fallback(error.message ?: error.javaClass.simpleName)
                },
            )
        }
        if (stage != Stage.Ready) {
            Pen2d(
                modifier = Modifier.matchParentSize(),
                size = size,
                request = request,
                live = live,
                motionAllowed = motionAllowed,
                zoom = zoom,
                report = { markers -> if (stage != Stage.Ready) report(markers) },
            )
        }
        // The pen is an illustration: drags over it scroll the screen instead of reaching the 3D surface.
        Box(Modifier.matchParentSize().pointerInput(Unit) {})
    }
}

/** Debug hooks for [PenModelDemoActivity]: force the 2D pen, and hear which renderer is drawing. */
internal class PenModelDebug(val force2d: Boolean = false, val onRenderer: (String) -> Unit = {})

internal val LocalPenModelDebug = staticCompositionLocalOf { PenModelDebug() }

private const val TAG = "PenModelView"
private const val IDLE = "idle"
private const val CLIP_WRITE = "write" // the one gesture that doesn't zoom: it swings the whole pen
private const val PEN_ASSET = "pen/focus_pen_pro.glb"

// Pose and camera. Pen space is the model's: metres, +Y toward the end cap, +Z the face with the slide strip.
private const val PEN_ROLL_DEG = 36f // pen top toward the upper right, measured from vertical
private const val PEN_TILT_DEG = 12f // pen top toward the viewer
private const val FOCAL_MM = 85f // a portrait lens keeps the long pen free of wide-angle distortion
private const val BAND_FOCAL_MM = 220f // the close-up on the nib section (no dolly: only the lens and the pan change)
private const val BAND_SPAN_M = 0.060f // the close-up centres on the middle of the pen from the nib up this far
private const val SENSOR_HALF_MM = 12f // Filament's lens projection assumes a 24 mm high sensor
private const val FILL_HEIGHT = 0.86f // share of the view's height the pen at rest covers
private const val FILL_WIDTH = 0.94f // share of the width the widest pose (the `write` swing) may cover
private const val WRITE_SWING_DEG = 25f // `write` pivots the pen on its nib by up to 22° plus a 3° wobble
private const val MAX_FPS = 60

// Lights, with exposure pinned at f/12, 1/200 s, ISO 200 (exposure ≈ 5.8e-5). Tuned by rendering this scene
// with Filament 1.72.1's web build against the reference's pen. Filament uses only the dominant
// directional light, so the rim is a point light: a second directional light would be dropped.
private const val KEY_LUX = 40_000f
private const val IBL_LUX = 3_000f
private const val RIM_LUMENS = 80_000f
private const val RIM_FALLOFF_M = 2f
private val KEY_TRAVEL = floatArrayOf(0.80f, -0.50f, -0.35f) // from the upper left, slightly in front
private val RIM_POSITION = floatArrayOf(0.30f, -0.24f, -0.30f) // behind the pen, lower right (world, metres)
private val LAVENDER = floatArrayOf(0.497f, 0.402f, 1.0f) // #BBAAFF in linear RGB, the kit's accent

/**
 * The gesture effects (`fx_*`) glow by emission only, at half the model's strength: lit and at full
 * strength they tone-map to near white, at this level they stay the kit's lavender.
 */
private const val FX_EMISSION = 0.5f

/** Where a gesture's still preview stops, as a share of the clip (the reduced-motion pose). */
private val PREVIEW_AT = mapOf("squeeze" to 0.38f, "slide_up" to 0.47f, "slide_down" to 0.47f, "double_tap" to 0.2f, "write" to 0.38f)

/** Clip lengths of the bundled model, used by the 2D pen and when a clip is missing from a model. */
private const val GESTURE_SECONDS = 1.7f
private const val IDLE_SECONDS = 6f
private val GESTURE_MS = (GESTURE_SECONDS * 1000).toLong() // how long a gesture's close-up holds

private sealed class Stage(val label: String) {
    data object Waiting : Stage("2D (waiting for the view to be visible)")
    data object Loading : Stage("2D while 3D loads")
    data object Ready : Stage("3D (SceneView/Filament)")
    class Fallback(reason: String) : Stage("2D fallback: $reason")
}

private data class ClipRequest(val clip: String, val replay: Int)

private fun supportsGles3(context: Context): Boolean {
    val activityManager = context.getSystemService(ActivityManager::class.java) ?: return false
    return activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
}

/** `false` when the system animator duration scale is 0 (Settings ▸ Accessibility ▸ Remove animations). */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    var enabled by remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = read()
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        enabled = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

// ------------------------------------------------------------------------------------------------
// Clip timing, shared by the 3D and 2D pens
// ------------------------------------------------------------------------------------------------

/** Which clip shows and how far into it; advanced by frame time only while animation runs. Main thread. */
private class ClipClock(private val length: (String) -> Float) {
    var clip = IDLE
        private set
    var time = 0f
        private set
    private var lastNanos = 0L

    /** A gesture starts from its first frame; `idle` never cuts a gesture short. */
    fun request(name: String) {
        if (name != IDLE) {
            clip = name
            time = 0f
        }
    }

    fun hold(name: String, at: Float) {
        clip = name
        time = at
    }

    /** Forget the last frame time, so a pause or a parked scene isn't counted as elapsed time. */
    fun pause() {
        lastNanos = 0L
    }

    fun advance(frameNanos: Long) {
        val step = if (lastNanos == 0L) 0f else ((frameNanos - lastNanos) / 1e9f).coerceIn(0f, MAX_STEP_SECONDS)
        lastNanos = frameNanos
        time += step
        val length = length(clip)
        if (clip == IDLE) {
            if (length > 0f) time %= length
        } else if (time >= length) {
            // Every clip starts and ends at rest, so idle picks up seamlessly.
            time = (time - length).coerceAtMost(length(IDLE))
            clip = IDLE
        }
    }

    fun preview(name: String): Float = length(name) * (PREVIEW_AT[name] ?: 0f)

    private companion object {
        const val MAX_STEP_SECONDS = 0.1f
    }
}

// ------------------------------------------------------------------------------------------------
// The nib close-up: one timeline for the zoom a gesture plays, shared by the 3D and 2D pens
// ------------------------------------------------------------------------------------------------

/**
 * How far the camera has closed in on the nib section while a gesture plays: `z` is 0 at rest and 1 fully in.
 * [start] eases z up over [EASE_MS] (ease-in-out), holds it until the clip's length is up, then eases it back down
 * over [EASE_MS]; [busy] is true from [start] until z is back to 0. A clip that doesn't zoom (`write`) keeps z at 0
 * but is busy for the clip's length. With reduced motion z jumps to 1 at the start and to 0 at the end. A clip
 * shorter than the ease still gets the whole ease in before it eases out. A [start] while busy carries on from the
 * current z, so the camera never snaps.
 *
 * Pure Kotlin on a millisecond clock the caller supplies (the pens pass frame times), so it runs without Android.
 * Main thread only.
 */
internal class ZoomTimeline {
    enum class State { REST, IN, HOLD, OUT }

    var state = State.REST
        private set

    /** The z of the latest [start] or [update], in 0..1. */
    var z = 0f
        private set

    /** True from [start] until z is back to 0 after the clip. */
    val busy: Boolean get() = state != State.REST

    var paused = false
        private set

    private var skewMs = 0L // time spent paused, which the timeline doesn't count
    private var pausedAtMs = 0L
    private var lastMs = 0L // timeline time of the latest call; it never runs backwards
    private var startMs = 0L
    private var inEndMs = 0L // z has reached its peak
    private var holdEndMs = 0L // the clip's length is up
    private var endMs = 0L // z is back to 0
    private var from = 0f // z when the run started
    private var peak = 0f // 1 for a zoom, 0 for a clip that stays at rest

    /**
     * Starts a run at [nowMs] for a clip [clipMs] long. [zooms] `false` is for a clip that stays at the rest
     * framing: z stays 0, and the run is still busy for [clipMs].
     */
    fun start(nowMs: Long, clipMs: Long, zooms: Boolean, reducedMotion: Boolean) {
        val t = max(timeAt(nowMs), lastMs)
        if (busy) evaluate(t)
        from = z
        peak = if (zooms) 1f else 0f
        startMs = t
        inEndMs = if (reducedMotion || from == peak) t else t + EASE_MS
        holdEndMs = max(t + max(clipMs, 0L), inEndMs)
        endMs = if (reducedMotion || !zooms) holdEndMs else holdEndMs + EASE_MS
        lastMs = t
        state = if (t < inEndMs) State.IN else State.HOLD
        z = if (state == State.IN) from else peak
    }

    /** Moves the timeline to [nowMs] and returns z (0 once it is back at rest, and before any [start]). */
    fun update(nowMs: Long): Float {
        if (!busy) return 0f
        val t = max(timeAt(nowMs), lastMs)
        lastMs = t
        evaluate(t)
        return z
    }

    /** Freezes the timeline at [nowMs]: until [resume], [update] returns the same z and nothing finishes. */
    fun pause(nowMs: Long) {
        if (paused) return
        paused = true
        pausedAtMs = nowMs
    }

    /** Carries on from where [pause] stopped, as if no time had passed. */
    fun resume(nowMs: Long) {
        if (!paused) return
        paused = false
        skewMs += max(nowMs - pausedAtMs, 0L)
    }

    private fun timeAt(nowMs: Long): Long = (if (paused) pausedAtMs else nowMs) - skewMs

    private fun evaluate(t: Long) {
        when {
            t >= endMs -> {
                state = State.REST
                z = 0f
            }
            t >= holdEndMs -> {
                state = State.OUT
                z = peak * (1f - ease((t - holdEndMs) / EASE_MS.toFloat()))
            }
            t >= inEndMs -> {
                state = State.HOLD
                z = peak
            }
            else -> {
                state = State.IN
                z = from + (peak - from) * ease((t - startMs) / EASE_MS.toFloat())
            }
        }
    }

    private companion object {
        const val EASE_MS = 450L

        /** Ease-in-out: smoothstep, flat at both ends, 0.5 at the middle. */
        fun ease(u: Float): Float {
            val t = u.coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}

/**
 * The [ZoomTimeline] both pens follow, plus the busy signal. Whichever pen is drawing calls [update] with its frame
 * time (the timeline never runs backwards, so two callers in one frame agree); [onBusy] hears each change of busy
 * once. Main thread.
 */
private class ZoomState(private val onBusy: (Boolean) -> Unit) {
    private val timeline = ZoomTimeline()
    private var reported = false
    private var released = false

    /** True while frames are needed: the timeline is busy and not frozen. Snapshot state, so the pens react to it. */
    var ticking by mutableStateOf(false)
        private set

    /** The z of the latest update. */
    val z: Float get() = timeline.z

    fun start(nowMs: Long, clipMs: Long, zooms: Boolean, reducedMotion: Boolean) {
        timeline.start(nowMs, clipMs, zooms, reducedMotion)
        sync()
    }

    fun update(nowMs: Long): Float = timeline.update(nowMs).also { sync() }

    fun pause(nowMs: Long) {
        timeline.pause(nowMs)
        sync()
    }

    fun resume(nowMs: Long) {
        timeline.resume(nowMs)
        sync()
    }

    /** The view is leaving the composition: nobody waits for the end any more, so report not busy now. */
    fun release() {
        released = true
        ticking = false
        if (reported) {
            reported = false
            onBusy(false)
        }
    }

    private fun sync() {
        if (released) return
        ticking = timeline.busy && !timeline.paused
        if (timeline.busy != reported) {
            reported = timeline.busy
            onBusy(reported)
        }
    }
}

/** Milliseconds on the frame clock's timebase (Choreographer frame times are `System.nanoTime()`), for [ZoomTimeline]. */
private fun frameClockMs(): Long = System.nanoTime() / 1_000_000L

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

// ------------------------------------------------------------------------------------------------
// Framing: one camera model for the 3D scene and the 2D pen
// ------------------------------------------------------------------------------------------------

/** The pen's size in pen space: half its length, its barrel radius, and the Y of its middle. */
private data class PenDims(val halfLength: Float, val radius: Float, val centerY: Float) {
    companion object {
        /** The bundled model: 165 mm end-cap top to nib apex, 10 mm barrel (nominal, see the README). */
        val Bundled = PenDims(halfLength = 0.0825f, radius = 0.005f, centerY = 0f)
    }
}

/** Pen space → world: roll about Z after a tilt about X (the same order as the scene's nested nodes). */
private object PenPose {
    private val roll = Math.toRadians(-PEN_ROLL_DEG.toDouble())
    private val tilt = Math.toRadians(PEN_TILT_DEG.toDouble())
    private val ca = cos(roll).toFloat()
    private val sa = sin(roll).toFloat()
    private val ct = cos(tilt).toFloat()
    private val st = sin(tilt).toFloat()

    fun toWorld(x: Float, y: Float, z: Float, out: FloatArray) {
        out[0] = ca * x - sa * ct * y + sa * st * z
        out[1] = sa * x + ca * ct * y - ca * st * z
        out[2] = st * y + ct * z
    }
}

/**
 * A camera on +Z looking down −Z with a [focalMm] lens. [fit] places one with the rest lens ([FOCAL_MM]) so the pen at
 * rest fills [FILL_HEIGHT] of the view's height, centred, and the `write` swing still fits [FILL_WIDTH] of its width.
 */
private class PenFraming(
    val width: Int,
    val height: Int,
    val camX: Float,
    val camY: Float,
    val camZ: Float,
    val focalMm: Float = FOCAL_MM,
) {
    private val tanHalf = SENSOR_HALF_MM / focalMm
    private val aspect = width.toFloat() / height
    private val world = FloatArray(3)

    fun worldToView(x: Float, y: Float, z: Float): Offset? {
        val depth = camZ - z
        if (depth <= MIN_DEPTH) return null
        val nx = (x - camX) / (depth * tanHalf * aspect)
        val ny = (y - camY) / (depth * tanHalf)
        return Offset((nx + 1f) * 0.5f * width, (1f - ny) * 0.5f * height)
    }

    fun penToView(x: Float, y: Float, z: Float): Offset? {
        PenPose.toWorld(x, y, z, world)
        return worldToView(world[0], world[1], world[2])
    }

    companion object {
        private const val MIN_DEPTH = 1e-4f
        private const val RING = 16

        fun fit(width: Int, height: Int, dims: PenDims): PenFraming? {
            if (width <= 0 || height <= 0) return null
            val tanHalf = SENSOR_HALF_MM / FOCAL_MM
            val aspect = width.toFloat() / height
            val bottom = dims.centerY - dims.halfLength
            val top = dims.centerY + dims.halfLength
            val rest = ArrayList<FloatArray>()
            val swing = ArrayList<FloatArray>()
            val swingAngle = Math.toRadians(-WRITE_SWING_DEG.toDouble())
            val cs = cos(swingAngle).toFloat()
            val ss = sin(swingAngle).toFloat()
            for (k in 0 until RING) {
                val a = 2.0 * PI * k / RING
                val x = dims.radius * cos(a).toFloat()
                val z = dims.radius * sin(a).toFloat()
                for (y in floatArrayOf(bottom, top)) rest += FloatArray(3).also { PenPose.toWorld(x, y, z, it) }
                // The top ring swung about the nib, the top toward +X: `write`'s widest pose.
                val dy = top - bottom
                swing += FloatArray(3).also { PenPose.toWorld(cs * x - ss * dy, bottom + ss * x + cs * dy, z, it) }
            }
            var cx = 0f
            var cy = 0f
            var cz = 0.6f
            repeat(10) {
                var minX = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                for (p in rest) {
                    val d = cz - p[2]
                    val nx = (p[0] - cx) / (d * tanHalf * aspect)
                    val ny = (p[1] - cy) / (d * tanHalf)
                    minX = min(minX, nx); maxX = max(maxX, nx); minY = min(minY, ny); maxY = max(maxY, ny)
                }
                val midX = (minX + maxX) / 2
                val midY = (minY + maxY) / 2
                var reach = max(abs(minX - midX), abs(maxX - midX))
                for (p in swing) {
                    val d = cz - p[2]
                    reach = max(reach, abs((p[0] - cx) / (d * tanHalf * aspect) - midX))
                }
                val scale = max((maxY - minY) / 2 / FILL_HEIGHT, reach / FILL_WIDTH)
                cx += midX * cz * tanHalf * aspect
                cy += midY * cz * tanHalf
                cz *= scale
            }
            return PenFraming(width, height, cx, cy, cz)
        }
    }
}

/** The rest framing and the nib close-up, and the camera in between for a zoom `z` (0 = rest, 1 = close-up). */
private class ZoomFraming(val rest: PenFraming, val band: PenFraming) {
    fun focalAt(z: Float): Float = mix(rest.focalMm, band.focalMm, z)
    fun camXAt(z: Float): Float = mix(rest.camX, band.camX, z)
    fun camYAt(z: Float): Float = mix(rest.camY, band.camY, z)

    /** The whole framing at [z], for the 2D pen. */
    fun at(z: Float): PenFraming =
        if (z <= 0f) rest else PenFraming(rest.width, rest.height, camXAt(z), camYAt(z), rest.camZ, focalAt(z))

    companion object {
        /**
         * The close-up: the same camera distance (no dolly), a [BAND_FOCAL_MM] lens, panned so the middle of the pen
         * from the nib up [BAND_SPAN_M] is at the view's centre. A camera looking straight down −Z puts a point at
         * the centre exactly when it is right above it in x and y, whatever the lens.
         */
        fun of(rest: PenFraming, dims: PenDims): ZoomFraming {
            val middle = FloatArray(3)
            PenPose.toWorld(0f, dims.centerY - dims.halfLength + BAND_SPAN_M / 2, 0f, middle)
            return ZoomFraming(rest, PenFraming(rest.width, rest.height, middle[0], middle[1], rest.camZ, BAND_FOCAL_MM))
        }
    }
}

// ------------------------------------------------------------------------------------------------
// 3D: SceneView (Filament + gltfio)
// ------------------------------------------------------------------------------------------------

private sealed interface ModelLoad {
    data object Loading : ModelLoad
    class Ready(val instance: ModelInstance) : ModelLoad
    class Failed(val error: Throwable) : ModelLoad
}

@Composable
private fun Pen3d(
    modifier: Modifier,
    size: IntSize,
    request: ClipRequest,
    live: Boolean,
    motionAllowed: Boolean,
    zoom: ZoomState,
    report: (Map<String, Offset>) -> Unit,
    onReady: () -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    val context = LocalContext.current
    // Created here, guarded, rather than by rememberEngine's own factories: a device without a usable
    // OpenGL ES 3 context or without Filament's libraries must fall back to 2D, not crash.
    val core = remember { runCatching { createCore() } }
    val created = core.getOrNull()
    if (created == null) {
        LaunchedEffect(Unit) { onFailure(core.exceptionOrNull() ?: IllegalStateException("Filament failed to start")) }
        return
    }
    val engine = rememberEngine(eglContextCreator = { created.first }, engineCreator = { created.second })
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val view = rememberView(engine)
    val renderer = rememberRenderer(engine)
    val scene = rememberScene(engine)
    // rememberEnvironment keys on the factory, so the factory itself is remembered.
    val environmentFactory = remember(engine, environmentLoader) { { penEnvironment(engine, environmentLoader) } }
    val environment = rememberEnvironment(environmentLoader, isOpaque = false, environment = environmentFactory)
    val cameraNode = rememberCameraNode(engine) {
        focalLength = FOCAL_MM.toDouble()
        near = 0.01f
        far = 10f
        setExposure(12f, 1f / 200f, 200f)
        rotation = Rotation(0f, 0f, 0f)
        position = Position(0f, 0f, 0.6f) // until the view's size is known; Pen3dDriver.setFraming then places it
    }
    // Not SceneView's rememberNode: it is an inline function compiled for JVM 21, and this app targets 17.
    val keyLight = remember(engine) { keyLight(engine) }
    val rimLight = remember(engine) { rimLight(engine) }
    DisposableEffect(keyLight, rimLight) {
        onDispose {
            keyLight.destroy()
            rimLight.destroy()
        }
    }
    val invalidator = rememberRenderInvalidator()

    val load by produceState<ModelLoad>(ModelLoad.Loading, modelLoader) {
        value = try {
            val buffer = withContext(Dispatchers.IO) { context.assets.readBuffer(PEN_ASSET) }
            ModelLoad.Ready(modelLoader.createModelInstance(buffer))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ModelLoad.Failed(e)
        }
    }
    val instance = (load as? ModelLoad.Ready)?.instance
    DisposableEffect(instance) {
        onDispose { instance?.let { modelLoader.destroyModel(it.asset) } }
    }
    val rigResult = remember(instance) { instance?.let { runCatching { PenRig(engine, it) } } }
    val rig = rigResult?.getOrNull()
    LaunchedEffect(load, rigResult) {
        val error = (load as? ModelLoad.Failed)?.error ?: rigResult?.exceptionOrNull()
        if (error != null) onFailure(error)
    }

    val scope = rememberCoroutineScope()
    val driver = remember { Pen3dDriver(scope, zoom) }
    val nodeHolder = remember { arrayOfNulls<SceneModelNode>(1) }
    // Read here, so a close-up starting or ending recomposes this and the SideEffect below re-arms the frame loop.
    val zoomTicking = zoom.ticking

    SceneView(
        modifier = modifier,
        surfaceType = SurfaceType.TextureSurface, // inline, so the card shows through and callouts draw on top
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        environmentLoader = environmentLoader,
        view = view,
        isOpaque = false,
        frameRatePolicy = FrameRatePolicy.OnDemand(maxFps = MAX_FPS),
        renderInvalidator = invalidator,
        autoCenterContent = false,
        renderer = renderer,
        scene = scene,
        environment = environment,
        mainLightNode = keyLight,
        fillLightNode = rimLight,
        cameraNode = cameraNode,
        cameraManipulator = null,
        onGestureListener = null,
        onFrame = { driver.onPresented() },
    ) {
        if (instance != null) {
            Node(rotation = Rotation(z = -PEN_ROLL_DEG)) {
                Node(rotation = Rotation(x = PEN_TILT_DEG)) {
                    ModelNode(modelInstance = instance, autoAnimate = false, apply = { nodeHolder[0] = this })
                }
            }
        }
    }

    // After SceneView's own effects, which apply its default render-quality preset to the view.
    LaunchedEffect(view) {
        tuneView(view)
        invalidator.requestRender()
    }
    // The rest framing goes through the same camera function the frames use (Pen3dDriver.applyCamera).
    LaunchedEffect(size, rig) {
        val dims = rig?.dims ?: PenDims.Bundled
        val rest = PenFraming.fit(size.width, size.height, dims) ?: return@LaunchedEffect
        driver.setFraming(ZoomFraming.of(rest, dims))
        invalidator.requestRender()
    }
    SideEffect {
        driver.bind(rig, nodeHolder[0], scene, view, cameraNode, report, onReady, onFailure)
        driver.setMotion(allowed = motionAllowed, running = live && motionAllowed, zoomTicking = zoomTicking)
    }
    LaunchedEffect(request, rig) {
        if (rig != null) driver.request(request.clip)
    }
}

private fun createCore(): Pair<EGLContext, Engine> {
    val egl = createEglContext()
    return try {
        egl to createEngine(egl)
    } catch (e: Throwable) {
        runCatching { egl.destroy() }
        throw e
    }
}

private fun penEnvironment(engine: Engine, loader: EnvironmentLoader): Environment =
    try {
        createEnvironment(loader, isOpaque = false).also { it.indirectLight?.intensity = IBL_LUX }
    } catch (e: Exception) {
        Log.w(TAG, "image-based light failed to load; using the direct lights only", e)
        createEnvironment(engine, isOpaque = false, indirectLight = null)
    }

private fun keyLight(engine: Engine): LightNode {
    val length = sqrt(KEY_TRAVEL[0] * KEY_TRAVEL[0] + KEY_TRAVEL[1] * KEY_TRAVEL[1] + KEY_TRAVEL[2] * KEY_TRAVEL[2])
    return LightNode(engine, LightManager.Type.DIRECTIONAL) {
        color(1f, 1f, 1f)
        intensity(KEY_LUX)
        direction(KEY_TRAVEL[0] / length, KEY_TRAVEL[1] / length, KEY_TRAVEL[2] / length)
        castShadows(false)
    }
}

private fun rimLight(engine: Engine): LightNode = LightNode(engine, LightManager.Type.POINT) {
    color(LAVENDER[0], LAVENDER[1], LAVENDER[2])
    intensity(RIM_LUMENS)
    position(RIM_POSITION[0], RIM_POSITION[1], RIM_POSITION[2])
    falloff(RIM_FALLOFF_M)
    castShadows(false)
}

/**
 * No shadows (nothing to cast on); ambient occlusion for the grip band's depth in the close-up, and a faint bloom for the
 * satin highlights; 4× MSAA for the long thin edges. The HDR colour buffer is HIGH quality, which keeps its alpha (the
 * target is transparent). SceneView's own view setup, including its Filmic tone mapping, is left as it is.
 */
private fun tuneView(view: View) {
    view.setShadowingEnabled(false)
    view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply {
        enabled = true
        quality = View.QualityLevel.MEDIUM
    }
    view.bloomOptions = view.bloomOptions.apply {
        enabled = true
        strength = 0.08f
    }
    view.renderQuality = view.renderQuality.apply { hdrColorBuffer = View.QualityLevel.HIGH }
    view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply {
        enabled = true
        sampleCount = 4
    }
    view.antiAliasing = View.AntiAliasing.NONE
}

/** The loaded model's clips and markers. */
private class PenRig(private val engine: Engine, instance: ModelInstance) {
    private val animator = instance.animator
    private val clipIndex: Map<String, Int> = (0 until animator.animationCount).associateBy { animator.getAnimationName(it) }
    private val lengths: Map<String, Float> = clipIndex.mapValues { animator.getAnimationDuration(it.value) }
    val markerEntities: Map<String, Int> = buildMap {
        for (entity in instance.entities) {
            val name = instance.asset.getName(entity)
            if (name != null && name in PenModel.markers) put(name, entity)
        }
    }
    val bodyEntity: Int = instance.entities.firstOrNull { engine.renderableManager.hasComponent(it) } ?: 0
    val dims: PenDims = instance.asset.boundingBox.let { box ->
        val half = box.halfExtent[1]
        if (half > 0f) PenDims(half, half * PenDims.Bundled.radius / PenDims.Bundled.halfLength, box.center[1]) else PenDims.Bundled
    }
    private val matrix = FloatArray(16)

    init {
        // See FX_EMISSION. gltfio shares one material instance per glTF material; setting it per primitive is harmless.
        val renderables = engine.renderableManager
        for (entity in instance.entities) {
            if (instance.asset.getName(entity)?.startsWith("fx_") != true) continue
            val renderable = renderables.getInstance(entity)
            if (renderable == 0) continue
            for (primitive in 0 until renderables.getPrimitiveCount(renderable)) {
                val material = renderables.getMaterialInstanceAt(renderable, primitive)
                if (material.material.hasParameter("baseColorFactor")) material.setParameter("baseColorFactor", 0f, 0f, 0f, 1f)
                if (material.material.hasParameter("emissiveFactor")) {
                    material.setParameter("emissiveFactor", LAVENDER[0] * FX_EMISSION, LAVENDER[1] * FX_EMISSION, LAVENDER[2] * FX_EMISSION)
                }
            }
        }
    }

    fun length(clip: String): Float = lengths[clip] ?: if (clip == IDLE) IDLE_SECONDS else GESTURE_SECONDS

    fun pose(clip: String, time: Float) {
        val index = clipIndex[clip] ?: clipIndex[IDLE] ?: return
        animator.applyAnimation(index, time)
        animator.updateBoneMatrices()
    }

    /** World position of [entity] as of the last transform update, into [out]. */
    fun worldPosition(entity: Int, out: FloatArray): Boolean {
        val transforms = engine.transformManager
        val instance = transforms.getInstance(entity)
        if (instance == 0) return false
        transforms.getWorldTransform(instance, matrix)
        out[0] = matrix[12]
        out[1] = matrix[13]
        out[2] = matrix[14]
        return true
    }
}

/**
 * Advances the clip clock from the frame loop, poses the model, steers the camera for the close-up, and reports markers
 * after each frame.
 */
private class Pen3dDriver(private val scope: CoroutineScope, private val zoom: ZoomState) {
    private var rig: PenRig? = null
    private var node: SceneModelNode? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: CameraNode? = null
    private var framing: ZoomFraming? = null
    private var appliedZ = Float.NaN // the z the camera was last aimed for; NaN forces the next aim
    private var report: (Map<String, Offset>) -> Unit = {}
    private var onReady: () -> Unit = {}
    private var onFailure: (Throwable) -> Unit = {}
    private val clock = ClipClock { rig?.length(it) ?: GESTURE_SECONDS }
    private var motionAllowed = true
    private var running = false
    private var failed = false
    private var stillJob: Job? = null
    private var framesWithModel = 0
    private var lastMarkers: Map<String, Offset> = emptyMap()
    private val world = FloatArray(3)

    private val tick: (Long) -> Unit = { frameNanos ->
        guard {
            // The clip moves only while the pen plays; the close-up goes on until it has eased back out.
            if (running) {
                clock.advance(frameNanos)
                rig?.pose(clock.clip, clock.time)
            }
            applyCamera(zoom.update(frameNanos / 1_000_000L))
        }
    }

    fun bind(
        rig: PenRig?,
        node: SceneModelNode?,
        scene: Scene,
        view: View,
        camera: CameraNode,
        report: (Map<String, Offset>) -> Unit,
        onReady: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        this.node = node
        this.scene = scene
        this.view = view
        this.report = report
        this.onReady = onReady
        this.onFailure = onFailure
        if (camera !== this.camera) {
            this.camera = camera
            appliedZ = Float.NaN
            guard { applyCamera(zoom.z) }
        }
        if (rig !== this.rig) {
            this.rig = rig
            framesWithModel = 0
            guard { rig?.pose(clock.clip, clock.time) }
        }
    }

    fun setMotion(allowed: Boolean, running: Boolean, zoomTicking: Boolean) {
        if (allowed != motionAllowed) {
            motionAllowed = allowed
            stillJob?.cancel()
            if (!allowed) hold(IDLE, 0f)
        }
        val ready = rig != null && node != null && !failed
        val run = running && ready
        if (run != this.running) {
            this.running = run
            clock.pause()
        }
        // A non-null Node.onFrame keeps SceneView's on-demand loop drawing; null lets it park. The loop also runs while a
        // close-up is under way, even if the clip is held (`playing` false) or motion is reduced.
        node?.onFrame = if (run || (zoomTicking && ready)) tick else null
    }

    /** The camera's rest framing and close-up for the current view size and model; aims the camera for the zoom as it stands. */
    fun setFraming(framing: ZoomFraming) {
        this.framing = framing
        appliedZ = Float.NaN
        guard { applyCamera(zoom.z) }
    }

    /**
     * The one place the camera is aimed: the lens and the pan blend from the rest framing to the close-up as [z] goes
     * 0 to 1 (SceneView re-projects when the focal length is set). The distance never changes.
     */
    private fun applyCamera(z: Float) {
        val camera = camera ?: return
        val framing = framing ?: return
        if (z == appliedZ) return
        appliedZ = z
        camera.focalLength = framing.focalAt(z).toDouble()
        camera.position = Position(framing.camXAt(z), framing.camYAt(z), framing.rest.camZ)
    }

    fun request(clip: String) {
        stillJob?.cancel()
        if (motionAllowed) {
            clock.request(clip)
            guard { rig?.pose(clock.clip, clock.time) }
            node?.requestRender()
        } else if (clip == IDLE) {
            hold(IDLE, 0f)
        } else {
            // Reduced motion: the gesture's key pose, held for the gesture's length, then rest.
            hold(clip, clock.preview(clip))
            val length = rig?.length(clip) ?: GESTURE_SECONDS
            stillJob = scope.launch {
                delay((length * 1000).toLong())
                hold(IDLE, 0f)
            }
        }
    }

    private fun hold(clip: String, at: Float) {
        clock.hold(clip, at)
        guard { rig?.pose(clip, at) }
        node?.requestRender()
    }

    /** SceneView's post-present callback: readiness, then markers. */
    fun onPresented() = guard {
        val rig = rig ?: return@guard
        val scene = scene ?: return@guard
        if (framesWithModel < READY_FRAMES) {
            if (rig.bodyEntity != 0 && scene.hasEntity(rig.bodyEntity)) framesWithModel++
            if (framesWithModel == READY_FRAMES) onReady() else return@guard
        }
        val view = view ?: return@guard
        val markers = HashMap<String, Offset>(rig.markerEntities.size)
        for ((name, entity) in rig.markerEntities) {
            if (!rig.worldPosition(entity, world)) continue
            val screen = view.worldToScreen(Position(world[0], world[1], world[2])) ?: continue
            markers[name] = Offset(screen.x, screen.y)
        }
        if (moved(lastMarkers, markers)) {
            lastMarkers = markers
            report(markers)
        }
    }

    private inline fun guard(block: () -> Unit) {
        if (failed) return
        try {
            block()
        } catch (e: Exception) {
            failed = true
            node?.onFrame = null
            onFailure(e)
        }
    }

    private companion object {
        /** Presented frames with the model in the scene before the 2D pen steps aside (shaders warm up). */
        const val READY_FRAMES = 3
    }
}

private fun moved(old: Map<String, Offset>, new: Map<String, Offset>): Boolean {
    if (old.keys != new.keys) return true
    for ((name, offset) in new) {
        val before = old[name] ?: return true
        if (abs(before.x - offset.x) > 0.25f || abs(before.y - offset.y) > 0.25f) return true
    }
    return false
}

// ------------------------------------------------------------------------------------------------
// 2D: the motion kit's Focus Pen Pro (docs/redesign/pen-motion-kit.html), with the same framing
// ------------------------------------------------------------------------------------------------

// Kit drawing units: viewBox 300 × 640, pen axis at x 150, end-cap top at y 48, nib apex at y 575.5.
private const val KIT_AXIS = 150f
private const val KIT_TOP = 48f
private const val KIT_APEX = 575.5f
private const val KIT_MID = (KIT_TOP + KIT_APEX) / 2
private const val KIT_PER_METRE = (KIT_APEX - KIT_TOP) / (2 * 0.0825f)

private val KitAccent = Color(0xFFBBAAFF)
private val KitBody = Brush.horizontalGradient(
    0f to Color(0xFF9CA6B7), 0.3f to Color(0xFFFFFFFF), 0.65f to Color(0xFFE7EBF1), 1f to Color(0xFF9DA8B8),
    startX = 134f, endX = 166f,
)
private val KitNib = Color(0xFFB2BAC6)
private val KitHairline = Color(0xFFBCC5D1)

/** Marker positions in pen space, metres: the model's, with the gesture markers in the grip band near the nib. */
private val MARKERS_PEN = mapOf(
    "marker_squeeze" to floatArrayOf(0f, -0.0505f, 0.005f),
    "marker_slide_top" to floatArrayOf(0f, -0.0345f, 0.005f),
    "marker_slide_bottom" to floatArrayOf(0f, -0.0565f, 0.005f),
    "marker_double_tap" to floatArrayOf(0f, -0.0385f, 0.005f),
    "marker_nib" to floatArrayOf(0f, -0.0825f, 0f),
    "marker_top" to floatArrayOf(0f, 0.0825f, 0f),
)

private class Kit2dPaths {
    val body = Path().apply {
        // rect x 134..166, y 48..534 with rx 12, its lower corners squared off by the collar rect.
        moveTo(134f, 534f)
        lineTo(134f, 60f)
        quadraticTo(134f, 48f, 146f, 48f)
        lineTo(154f, 48f)
        quadraticTo(166f, 48f, 166f, 60f)
        lineTo(166f, 534f)
        close()
    }
    val cone = Path().apply {
        moveTo(134f, 534f)
        lineTo(147f, 568f)
        quadraticTo(150f, 579f, 153f, 568f)
        lineTo(166f, 534f)
        close()
    }
    val nib = Path().apply {
        moveTo(147f, 568f)
        quadraticTo(150f, 583f, 153f, 568f)
        close()
    }
}

/** The 2D pen's pose at one moment, in pen space: a swing about the nib and a rise along the pen. */
private class Pose2d(var swingDeg: Float = 0f, var rise: Float = 0f, var squeeze: Float = 0f, var tap: Float = 0f, var slide: Float = -1f, var slideAlpha: Float = 0f)

private fun pulse(u: Float, a: Float, b: Float): Float = if (u < a || u > b) 0f else sin(PI.toFloat() * (u - a) / (b - a))

private fun smooth(u: Float, a: Float, b: Float): Float {
    val t = ((u - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/** The kit's gesture timing (pulse windows) applied to one clip at time [time] seconds. */
private fun pose2d(clip: String, time: Float, length: Float, out: Pose2d) {
    out.swingDeg = 0f; out.rise = 0f; out.squeeze = 0f; out.tap = 0f; out.slide = -1f; out.slideAlpha = 0f
    val u = if (length > 0f) (time / length).coerceIn(0f, 1f) else 0f
    when (clip) {
        IDLE -> out.rise = 0.001f * (1 - cos(2 * PI.toFloat() * u))
        "squeeze" -> out.squeeze = pulse(u, 0.12f, 0.64f)
        "double_tap" -> out.tap = max(pulse(u, 0.10f, 0.31f), pulse(u, 0.42f, 0.63f))
        "slide_up", "slide_down" -> {
            val travel = smooth(u, 0.14f, 0.80f)
            out.slide = if (clip == "slide_up") travel else 1f - travel
            out.slideAlpha = min(1f, min(u / 0.14f, (1f - u) / 0.2f)).coerceIn(0f, 1f)
        }
        "write" -> {
            val envelope = sin(PI.toFloat() * u)
            out.swingDeg = 22f * envelope + 3f * sin(4 * PI.toFloat() * u) * envelope
        }
    }
}

/** Pen space (after the 2D pose) → view pixels. */
private class Pen2dProjector(private val framing: PenFraming) {
    private var swingCos = 1f
    private var swingSin = 0f
    private var rise = 0f
    private var nibY = -0.0825f

    fun set(pose: Pose2d, dims: PenDims) {
        val a = Math.toRadians(-pose.swingDeg.toDouble())
        swingCos = cos(a).toFloat()
        swingSin = sin(a).toFloat()
        rise = pose.rise
        nibY = dims.centerY - dims.halfLength
    }

    fun project(x: Float, y: Float, z: Float): Offset? {
        // Swing about the nib in the pen's XY plane (top toward +X), then rise along +Y.
        val dy = y - nibY
        return framing.penToView(swingCos * x - swingSin * dy, nibY + swingSin * x + swingCos * dy + rise, z)
    }

    /** The affine map from kit units to view pixels for the pen's middle plane. */
    fun kitMatrix(): Matrix? {
        val metre = 1f / KIT_PER_METRE
        val origin = project(0f, 0f, 0f) ?: return null
        val alongX = project(100 * metre, 0f, 0f) ?: return null
        val alongDown = project(0f, -100 * metre, 0f) ?: return null
        val ux = (alongX.x - origin.x) / 100
        val uy = (alongX.y - origin.y) / 100
        val vx = (alongDown.x - origin.x) / 100
        val vy = (alongDown.y - origin.y) / 100
        return Matrix().apply {
            values[Matrix.ScaleX] = ux
            values[Matrix.SkewY] = uy
            values[Matrix.SkewX] = vx
            values[Matrix.ScaleY] = vy
            values[Matrix.TranslateX] = origin.x - KIT_AXIS * ux - KIT_MID * vx
            values[Matrix.TranslateY] = origin.y - KIT_AXIS * uy - KIT_MID * vy
        }
    }
}

@Composable
private fun Pen2d(
    modifier: Modifier,
    size: IntSize,
    request: ClipRequest,
    live: Boolean,
    motionAllowed: Boolean,
    zoom: ZoomState,
    report: (Map<String, Offset>) -> Unit,
) {
    val paths = remember { Kit2dPaths() }
    val clock = remember { ClipClock(::length2d) }
    var shownClip by remember { mutableStateOf(IDLE) }
    var shownTime by remember { mutableFloatStateOf(0f) }
    var shownZoom by remember { mutableFloatStateOf(0f) }
    val framing = remember(size) {
        PenFraming.fit(size.width, size.height, PenDims.Bundled)?.let { ZoomFraming.of(it, PenDims.Bundled) }
    }
    val currentFraming by rememberUpdatedState(framing)
    val lastMarkers = remember { arrayOf<Map<String, Offset>>(emptyMap()) }

    // Shows the clock's pose and the timeline's zoom, and reports where the markers went (from effects, never the draw pass).
    fun show() {
        shownClip = clock.clip
        shownTime = clock.time
        shownZoom = zoom.z
        val projector = Pen2dProjector(currentFraming?.at(shownZoom) ?: return)
        projector.set(Pose2d().also { pose2d(clock.clip, clock.time, length2d(clock.clip), it) }, PenDims.Bundled)
        val markers = HashMap<String, Offset>(MARKERS_PEN.size)
        for ((name, p) in MARKERS_PEN) projector.project(p[0], p[1], p[2])?.let { markers[name] = it }
        if (moved(lastMarkers[0], markers)) {
            lastMarkers[0] = markers
            report(markers)
        }
    }

    LaunchedEffect(framing) { show() }
    LaunchedEffect(request, motionAllowed) {
        if (motionAllowed) {
            clock.request(request.clip)
            show()
        } else if (request.clip == IDLE) {
            clock.hold(IDLE, 0f)
            show()
        } else {
            clock.hold(request.clip, clock.preview(request.clip))
            show()
            delay((GESTURE_SECONDS * 1000).toLong())
            clock.hold(IDLE, 0f)
            show()
        }
    }
    // Frames run while the clip plays, and while a close-up is under way even if it doesn't (`playing` false, reduced motion).
    val animate = live && motionAllowed
    val frames = animate || zoom.ticking
    LaunchedEffect(animate, frames) {
        clock.pause()
        if (!frames) return@LaunchedEffect
        while (true) {
            withFrameNanos { frameNanos ->
                if (animate) clock.advance(frameNanos)
                zoom.update(frameNanos / 1_000_000L)
                show()
            }
        }
    }
    Canvas(modifier) {
        val pose = Pose2d().also { pose2d(shownClip, shownTime, length2d(shownClip), it) }
        val projector = Pen2dProjector((framing ?: return@Canvas).at(shownZoom)).also { it.set(pose, PenDims.Bundled) }
        val matrix = projector.kitMatrix() ?: return@Canvas
        withTransform({ transform(matrix) }) {
            drawKitPen(paths, pose)
        }
    }
}

private fun length2d(clip: String) = if (clip == IDLE) IDLE_SECONDS else GESTURE_SECONDS

private fun kitY(yMetres: Float) = KIT_MID - yMetres * KIT_PER_METRE

/** The kit's Focus Pen Pro in kit units, with its gesture effects placed at the model's markers. */
private fun DrawScope.drawKitPen(paths: Kit2dPaths, pose: Pose2d) {
    drawPath(paths.body, KitBody)
    drawPath(paths.cone, KitBody)
    drawPath(paths.nib, KitNib)
    drawLine(KitHairline, Offset(136f, 65f), Offset(164f, 65f), strokeWidth = 1f)
    drawLine(KitHairline, Offset(136f, 532f), Offset(164f, 532f), strokeWidth = 1f)
    drawLine(Color.White.copy(alpha = 0.2f), Offset(139f, 73f), Offset(139f, 522f), strokeWidth = 1f)
    // The model's logo band (the kit's wordmark position).
    drawLine(Color(0xFFAEB5C1), Offset(134f, 109f), Offset(166f, 109f), strokeWidth = 2.8f)

    val stroke = Stroke(width = 2.5f, cap = StrokeCap.Round)
    if (pose.squeeze > 0f) {
        val y = kitY(MARKERS_PEN.getValue("marker_squeeze")[1])
        val inward = 13f * pose.squeeze
        val left = Path().apply { moveTo(114f + inward, y - 20f); quadraticTo(99f + inward, y, 114f + inward, y + 20f) }
        val right = Path().apply { moveTo(186f - inward, y - 20f); quadraticTo(201f - inward, y, 186f - inward, y + 20f) }
        drawPath(left, KitAccent, alpha = pose.squeeze, style = stroke)
        drawPath(right, KitAccent, alpha = pose.squeeze, style = stroke)
    }
    if (pose.tap > 0f) {
        val center = Offset(KIT_AXIS, kitY(MARKERS_PEN.getValue("marker_double_tap")[1]))
        drawCircle(KitAccent, radius = 18f + 12f * pose.tap, center = center, alpha = pose.tap, style = stroke)
        drawCircle(KitAccent, radius = 5f, center = center, alpha = pose.tap)
    }
    if (pose.slide >= 0f && pose.slideAlpha > 0f) {
        val top = kitY(MARKERS_PEN.getValue("marker_slide_top")[1])
        val bottom = kitY(MARKERS_PEN.getValue("marker_slide_bottom")[1])
        drawLine(
            KitAccent, Offset(KIT_AXIS, top), Offset(KIT_AXIS, bottom), strokeWidth = 2.5f, alpha = 0.25f * pose.slideAlpha,
            cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 7f)),
        )
        drawCircle(KitAccent, radius = 6f, center = Offset(KIT_AXIS, bottom + (top - bottom) * pose.slide), alpha = pose.slideAlpha)
    }
}
