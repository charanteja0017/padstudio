package com.charan.padstudio.setup

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charan.padstudio.MainViewModel
import com.charan.padstudio.pen.Section

/** Health checks with a fix for each, plus the pen probe and the device-facts location. */
@Composable
fun SetupScreen(model: MainViewModel, onStart: (Intent) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val checks by model.health.collectAsStateWithLifecycle()
    LazyColumn(modifier, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Setup checks", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.refresh(force = true) }) { Text("Check again") }
            }
        }
        items(checks) { check ->
            Section(check.title) {
                val status = when (check.ok) {
                    true -> "✓ OK"
                    false -> "✗ Needs attention"
                    null -> "• Check by hand"
                }
                Text(status, color = if (check.ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text(check.detail, style = MaterialTheme.typography.bodySmall)
                if (check.fix != null && check.fixIntent != null) {
                    OutlinedButton(onClick = { onStart(check.fixIntent) }) { Text(check.fix) }
                }
            }
        }
        item {
            Section("Pen probe") {
                Text("Shows every key and pen event this window receives, to see what your pen reports.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { onStart(Intent(context, PenProbeActivity::class.java)) }) { Text("Open pen probe") }
            }
        }
        item {
            Section("Device facts") {
                Column(Modifier.padding(top = 2.dp)) {
                    Text("tools/probe-device.sh writes docs/device/device-facts.json. Push it with:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "adb shell run-as ${context.packageName} sh -c 'cat > files/device-facts.json' < docs/device/device-facts.json",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(720.dp),
                    )
                }
            }
        }
    }
}
