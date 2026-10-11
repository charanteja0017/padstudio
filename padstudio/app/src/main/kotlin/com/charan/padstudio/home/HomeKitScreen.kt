package com.charan.padstudio.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charan.padstudio.MainViewModel
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.core.home.Placement
import com.charan.padstudio.pen.Section

/** Organize: every group with its apps; tap an app to move it. Widgets refresh after each change. */
@Composable
fun HomeKitScreen(model: MainViewModel, modifier: Modifier = Modifier) {
    val config by model.config.collectAsStateWithLifecycle()
    val snapshot by model.snapshot.collectAsStateWithLifecycle()
    var moving by remember { mutableStateOf<Placement?>(null) }

    LazyColumn(modifier, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Section("Home screen") {
                Text(
                    "Add a group widget for each group you use most. Icons inside sit at the density below, " +
                        "so the home screen stays tidy and HyperOS keeps its gestures, dock and split-screen.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Density", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Density.entries.forEach { density ->
                        FilterChip(config.density == density, onClick = { model.setDensity(density) }, label = { Text(density.name.lowercase().replaceFirstChar(Char::uppercase)) })
                    }
                }
                TextButton(onClick = model::resetOverrides) { Text("Undo all my moves") }
            }
        }
        val groups = snapshot?.groups.orEmpty()
        items(groups, key = { it.group.name }) { planned ->
            Section("${planned.group.title} · ${planned.apps.size}") {
                OutlinedButton(onClick = { model.pinWidget(planned.group) }) { Text("Add ${planned.group.title} widget") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    planned.apps.forEach { app ->
                        val placement = snapshot?.placements?.firstOrNull { it.app == app }
                        AssistChip(onClick = { moving = placement }, label = { Text(app.label) })
                    }
                }
            }
        }
        val hidden = snapshot?.placements.orEmpty().filter { it.group == AppGroup.HIDDEN }
        if (hidden.isNotEmpty()) {
            item {
                Section("Hidden · ${hidden.size}") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        hidden.forEach { placement -> AssistChip(onClick = { moving = placement }, label = { Text(placement.app.label) }) }
                    }
                }
            }
        }
    }

    moving?.let { placement ->
        MoveDialog("${placement.app.label} (now ${placement.group.title}: ${placement.reason})", onDismiss = { moving = null }) { group ->
            moving = null
            model.move(placement.app.packageName, group)
        }
    }
}
