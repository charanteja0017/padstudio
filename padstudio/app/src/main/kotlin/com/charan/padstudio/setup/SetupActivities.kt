package com.charan.padstudio.setup

import android.app.WallpaperManager
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.charan.padstudio.studio
import com.charan.padstudio.ui.PadStudioTheme
import java.io.File

/**
 * Logs every key and pen event delivered to this window (PLAN gate G1, layer "window"). Press the
 * pen buttons here, then "Save" writes files/pen-probe.txt for docs/device/pen-matrix.md.
 */
class PenProbeActivity : ComponentActivity() {
    private val lines = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PadStudioTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Pen probe", style = MaterialTheme.typography.headlineSmall)
                        Text("Hover, write and press every pen button and gesture. Each event is listed below.")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = ::save) { Text("Save to files/pen-probe.txt") }
                            OutlinedButton(onClick = { lines.clear() }) { Text("Clear") }
                        }
                        LazyColumn {
                            items(lines.asReversed()) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                        }
                    }
                }
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

    private fun save() {
        val file = File(filesDir, "pen-probe.txt")
        file.writeText(lines.joinToString("\n"))
        lines += "saved ${file.absolutePath} (adb exec-out run-as ${packageName} cat files/pen-probe.txt)"
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
