package com.charan.padstudio.pen

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charan.padstudio.MainViewModel
import com.charan.padstudio.core.pen.PenContext
import com.charan.padstudio.core.pen.PenControl
import com.charan.padstudio.core.pen.PenIntent
import com.charan.padstudio.core.pen.PenKind
import com.charan.padstudio.core.pen.PenMode
import com.charan.padstudio.core.pen.PenProfile
import com.charan.padstudio.core.pen.PenTrigger
import com.charan.padstudio.core.pen.Press

private fun controlsFor(kind: PenKind) = when (kind) {
    PenKind.TWO_BUTTON -> listOf(PenControl.UPPER, PenControl.LOWER)
    PenKind.FOCUS_PEN_PRO -> listOf(PenControl.PINCH, PenControl.SLIDE_UP, PenControl.SLIDE_DOWN)
}

private fun describe(trigger: PenTrigger?): String = when (trigger) {
    null -> "not recorded"
    is PenTrigger.KeyEvent -> "key ${trigger.keyCode} · scan ${trigger.scanCode} · ${trigger.deviceName}"
    is PenTrigger.Evdev -> "evdev ${trigger.keyCode}/${trigger.scanCode} · ${trigger.device.name}"
}

/**
 * The pen tab: which pen, Basic or Expert, record each control, choose what every press does per
 * app group, then hand the profile to Key Mapper.
 */
@Composable
fun PenStudioScreen(model: MainViewModel, onStart: (android.content.Intent) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val config by model.config.collectAsStateWithLifecycle()
    val plan by model.plan.collectAsStateWithLifecycle()
    val capturing by model.capturing.collectAsStateWithLifecycle()
    val snapshot by model.snapshot.collectAsStateWithLifecycle()
    val controls = controlsFor(config.penKind)

    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { KeyMapperCard(context) }
        item {
            Section("Your pen") {
                Text("Pen", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(config.penKind == PenKind.TWO_BUTTON, onClick = { model.setPenKind(PenKind.TWO_BUTTON) }, label = { Text("Two buttons (Smart Pen 2 / Focus Pen)") })
                    FilterChip(config.penKind == PenKind.FOCUS_PEN_PRO, onClick = { model.setPenKind(PenKind.FOCUS_PEN_PRO) }, label = { Text("Focus Pen Pro (squeeze, slides)") })
                }
                Text("How Key Mapper listens", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(config.penMode == PenMode.BASIC, onClick = { model.setPenMode(PenMode.BASIC) }, label = { Text("Basic") })
                    FilterChip(config.penMode == PenMode.EXPERT, onClick = { model.setPenMode(PenMode.EXPERT) }, label = { Text("Expert") })
                }
                Text(
                    if (config.penMode == PenMode.BASIC) {
                        "Basic: nothing to redo after a reboot. Presses HyperOS keeps for itself (holds, Focus Pen Pro gestures) can't be changed."
                    } else {
                        "Expert: Key Mapper's Expert Mode takes the pen's input device, so every press can be mapped. Needs wireless debugging; restarts itself on Wi-Fi."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                controls.forEach { control ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(control.label, style = MaterialTheme.typography.bodyLarge)
                            Text(describe(config.controls[control.id]), style = MaterialTheme.typography.bodySmall)
                        }
                        if (capturing == control.id) {
                            Text("Press it now…", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                            TextButton(onClick = model::cancelCapture) { Text("Cancel") }
                        } else {
                            OutlinedButton(onClick = { model.startCapture(control.id) }) { Text(if (config.controls[control.id] == null) "Record" else "Record again") }
                        }
                    }
                }
            }
        }
        item {
            Section("Notes app") {
                Text("Opened by \"Open notes app\" presses.", style = MaterialTheme.typography.bodySmall)
                val candidates = snapshot?.apps.orEmpty().filter { app ->
                    plan?.contexts.orEmpty().any { it.passThrough && app.facts.packageName in it.packages } ||
                        app.facts.label.contains("note", ignoreCase = true)
                }.distinctBy { it.facts.packageName }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    candidates.forEach { app ->
                        FilterChip(config.notesApp == app.facts.packageName, onClick = { model.setNotesApp(app.facts.packageName) }, label = { Text(app.facts.label) })
                    }
                }
            }
        }
        plan?.let { current ->
            val contexts = current.contexts.filter { it.packages.isNotEmpty() && !it.passThrough } +
                PenContext(PenProfile.EVERYWHERE, "Everywhere else", emptyList())
            items(contexts, key = { it.id }) { penContext ->
                BindingsCard(penContext, controls, config.penMode, current.profile.bindings, model)
            }
            current.contexts.filter { it.passThrough && it.packages.isNotEmpty() }.forEach { passThrough ->
                item {
                    Section(passThrough.title) {
                        Text("Pass-through: these apps get the raw pen buttons. Set them up in each app (e.g. button 1 = Eraser, button 2 = Undo).", style = MaterialTheme.typography.bodySmall)
                        Text(passThrough.packages.joinToString(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (current.generated.warnings.isNotEmpty()) {
                item {
                    Section("Check these") {
                        current.generated.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { model.applyToKeyMapper()?.let(onStart) }) { Text("Apply to Key Mapper") }
                    OutlinedButton(onClick = { model.setPenEnabled(true) }) { Text("Pen on") }
                    OutlinedButton(onClick = { model.setPenEnabled(false) }) { Text("Pen off") }
                    TextButton(onClick = model::resetBindings) { Text("Reset to defaults") }
                }
                Text(
                    "${current.generated.keyMaps.size} key maps in ${current.generated.groups.size} groups · profile ${current.hash}. " +
                        "Key Mapper asks how to restore: choose Replace.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun KeyMapperCard(context: Context) {
    val version = KeyMapperBridge.versionName(context)
    val listening = KeyMapperBridge.isAccessibilityEnabled(context)
    Section("Key Mapper") {
        Text(
            when {
                version == null -> "Not installed. PadStudio writes the pen profile; Key Mapper runs it (tools/install.sh keymapper)."
                !listening -> "Version $version — its accessibility service is off, so it can't see the pen yet."
                else -> "Version $version — listening."
            },
        )
    }
}

@Composable
private fun BindingsCard(penContext: PenContext, controls: List<PenControl>, mode: PenMode, bindings: List<com.charan.padstudio.core.pen.Binding>, model: MainViewModel) {
    val presses = if (mode == PenMode.EXPERT) Press.entries else listOf(Press.SHORT, Press.DOUBLE)
    Section(penContext.title) {
        if (penContext.packages.isNotEmpty()) Text(penContext.packages.joinToString(), style = MaterialTheme.typography.bodySmall)
        controls.forEach { control ->
            presses.forEach { press ->
                val current = bindings.firstOrNull { it.contextId == penContext.id && it.control == control.id && it.press == press }?.intent
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("${control.label} ${press.label}", modifier = Modifier.width(200.dp))
                    IntentPicker(current) { model.setBinding(penContext.id, control.id, press, it) }
                }
            }
        }
    }
}

@Composable
private fun IntentPicker(current: PenIntent?, onPick: (PenIntent?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(current?.label ?: "—") })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Nothing (leave to the app / HyperOS)") }, onClick = { open = false; onPick(null) })
            PenIntent.entries.forEach { intent ->
                DropdownMenuItem(text = { Text(intent.label) }, onClick = { open = false; onPick(intent) })
            }
        }
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
