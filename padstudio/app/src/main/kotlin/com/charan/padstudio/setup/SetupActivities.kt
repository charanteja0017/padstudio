package com.charan.padstudio.setup

import android.app.WallpaperManager
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.charan.padstudio.studio
import com.charan.padstudio.ui.PadStudioTheme
import com.charan.padstudio.ui.ShellDefaults
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.EmptyState
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.ScreenScaffold
import com.charan.padstudio.ui.components.StudioCard
import java.io.File

/**
 * Logs every key and pen event delivered to this window (PLAN gate G1, layer "window"). Press the
 * pen buttons here, then "Save" writes files/pen-probe.txt for docs/device/pen-matrix.md.
 */
class PenProbeActivity : ComponentActivity() {
    private val lines = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Tests take a while; the screen going off would stop HyperOS delivering pen events.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            PadStudioTheme {
                PenProbePage(lines, onSave = ::save, onClear = { lines.clear() })
            }
        }
    }

    @android.annotation.SuppressLint("RestrictedApi") // see MainActivity.dispatchKeyEvent
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val device = event.device
        val action = when (event.action) { KeyEvent.ACTION_DOWN -> "DOWN"; KeyEvent.ACTION_UP -> "UP"; else -> "ACTION_${event.action}" }
        lines += "KEY $action ${KeyEvent.keyCodeToString(event.keyCode)}(${event.keyCode}) " +
            "scan=${event.scanCode} repeat=${event.repeatCount} device=\"${device?.name}\" " +
            "vendor=0x%04x product=0x%04x descriptor=${device?.descriptor} source=0x%x".format(device?.vendorId ?: 0, device?.productId ?: 0, event.source)
        return true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        logMotion("TOUCH", event)
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        logMotion("HOVER", event)
        return super.dispatchGenericMotionEvent(event)
    }

    private fun logMotion(kind: String, event: MotionEvent) {
        val stylus = event.isFromSource(InputDevice.SOURCE_STYLUS)
        // Only log changes that matter for buttons, not every movement sample.
        if (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            if (event.buttonState == 0) return
        }
        lines += "$kind ${MotionEvent.actionToString(event.actionMasked)} tool=${event.getToolType(0)} " +
            "buttons=0x%x stylus=$stylus device=\"${event.device?.name}\"".format(event.buttonState)
    }

    override fun onStop() {
        super.onStop()
        save()
    }

    private fun save() {
        val file = File(filesDir, "pen-probe.txt")
        file.writeText(lines.joinToString("\n"))
        lines += "saved ${file.absolutePath} (adb exec-out run-as ${packageName} cat files/pen-probe.txt)"
    }
}

/**
 * The pen probe's page, in the house style: Save and Clear at the top right, the events newest first in a card.
 * [lines] is the activity's log; the page only shows it. Keys never reach the buttons here (the activity takes
 * every key event for the log), so they're for touch and the pen.
 */
@Composable
private fun PenProbePage(lines: List<String>, onSave: () -> Unit, onClear: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Surface(Modifier.fillMaxSize(), color = ShellDefaults.canvasColor(), contentColor = scheme.onSurface) {
        ScreenScaffold(
            title = "Pen probe",
            subtitle = "Hover, write and press every pen button and gesture. Each event is listed below, newest first.",
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            scrollState = null,
            actions = {
                PillButton("Save to files/pen-probe.txt", onClick = onSave)
                PillButton("Clear", onClick = onClear, style = PillStyle.Outlined, icon = Sym.close)
            },
        ) {
            StudioCard(Modifier.fillMaxWidth().weight(1f)) {
                Text(
                    if (lines.isEmpty()) "Events" else "Events (${lines.size})",
                    style = type.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                if (lines.isEmpty()) {
                    EmptyState(
                        title = "Nothing yet",
                        icon = Sym.testPen,
                        body = "Each key and pen event this window receives appears here.",
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(lines.asReversed()) {
                            Text(it, style = type.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Opened by tools/rollback.sh: confirms, then returns home and lock screen to the HyperOS default. */
class ResetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PadStudioTheme {
                AlertDialog(
                    onDismissRequest = ::finish,
                    title = { Text("Reset wallpaper?") },
                    text = { Text("PadStudio is being removed. Return the home and lock screen to the HyperOS default wallpaper?") },
                    confirmButton = {
                        TextButton(onClick = {
                            WallpaperManager.getInstance(this).clear(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                            studio.config.update { it.copy(wallpaperApplied = false) }
                            finish()
                        }) { Text("Reset") }
                    },
                    dismissButton = { TextButton(onClick = ::finish) { Text("Keep") } },
                )
            }
        }
    }
}
