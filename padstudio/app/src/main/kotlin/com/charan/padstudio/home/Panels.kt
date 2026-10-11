package com.charan.padstudio.home

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.data.HomeSnapshot
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.studio
import com.charan.padstudio.ui.PadStudioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Blurs whatever is behind a panel when HyperOS allows cross-window blur, otherwise dims it. */
private fun ComponentActivity.blurOrDimBehind() {
    val wm = getSystemService(WindowManager::class.java)
    if (wm.isCrossWindowBlurEnabled) {
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.also { it.blurBehindRadius = 64 }
        window.setDimAmount(0.25f)
    } else {
        window.setDimAmount(0.55f)
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
}

@Composable
private fun rememberSnapshot(): HomeSnapshot? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val snapshot by produceState<HomeSnapshot?>(null) {
        value = withContext(Dispatchers.Default) { context.studio.apps.snapshot() }
    }
    return snapshot
}

/** A rounded card in the middle of the screen; tapping outside closes it. */
@Composable
private fun PanelFrame(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 880.dp)
                .heightIn(max = 760.dp)
                // Swallow taps on the card itself so they don't dismiss it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                content()
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        placeholder = { Text("Search apps") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

private fun List<InstalledApp>.matching(query: String) =
    if (query.isBlank()) this else filter { it.facts.label.contains(query.trim(), ignoreCase = true) }

/** The folder that opens from a group widget's header or "+N" tile. */
class FolderPanelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blurOrDimBehind()
        val groupName = intent.getStringExtra(EXTRA_GROUP)
        setContent {
            PadStudioTheme {
                val snapshot = rememberSnapshot()
                val config by studio.config.config.collectAsState()
                val group = snapshot?.groups?.firstOrNull { it.group.name == groupName }
                var query by remember { mutableStateOf("") }
                PanelFrame(group?.group?.title ?: "Apps", onDismiss = ::finish) {
                    if (snapshot != null && group != null) {
                        SearchField(query) { query = it }
                        val apps = group.apps.mapNotNull(snapshot::app).matching(query)
                        AppGrid(apps, config.density, onLaunched = ::finish)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_GROUP = "com.charan.padstudio.GROUP"
    }
}

/** Every group as a tab, plus search across all apps. A pen press can open this (Key Mapper INTENT action). */
class LibraryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blurOrDimBehind()
        setContent {
            PadStudioTheme {
                val snapshot = rememberSnapshot()
                val config by studio.config.config.collectAsState()
                var tab by remember { mutableIntStateOf(0) }
                var query by remember { mutableStateOf("") }
                PanelFrame("Library", onDismiss = ::finish) {
                    if (snapshot == null) return@PanelFrame
                    SearchField(query) { query = it }
                    if (query.isNotBlank()) {
                        AppGrid(snapshot.apps.matching(query).sortedBy { it.facts.label.lowercase() }, config.density, onLaunched = ::finish)
                        return@PanelFrame
                    }
                    PrimaryScrollableTabRow(selectedTabIndex = tab.coerceIn(0, (snapshot.groups.size - 1).coerceAtLeast(0)), edgePadding = 0.dp) {
                        snapshot.groups.forEachIndexed { index, group ->
                            Tab(selected = index == tab, onClick = { tab = index }, text = { Text("${group.group.title} ${group.apps.size}") })
                        }
                    }
                    snapshot.groups.getOrNull(tab)?.let { group ->
                        AppGrid(group.apps.mapNotNull(snapshot::app), config.density, onLaunched = ::finish, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
}

/**
 * Chooses which group a widget shows. It is the widget's configuration activity (Android passes
 * EXTRA_APPWIDGET_ID) and also opens from the widget's "Edit", since HyperOS may not offer
 * "reconfigure" itself.
 */
class EditGroupWidgetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        blurOrDimBehind()
        setContent {
            PadStudioTheme {
                val snapshot = rememberSnapshot()
                var density by remember { mutableStateOf<Density?>(null) }
                LaunchedEffect(appWidgetId) { density = currentDensity(appWidgetId) }
                PanelFrame("Edit widget", onDismiss = ::finish) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Grid", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Density.entries.forEach { option ->
                                FilterChip(
                                    selected = density == option,
                                    onClick = {
                                        density = option
                                        setDensity(appWidgetId, option)
                                    },
                                    label = { Text(option.label) },
                                )
                            }
                        }
                        Text("Show which group?", style = MaterialTheme.typography.labelLarge)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(snapshot?.groups.orEmpty()) { group ->
                                ListItem(
                                    headlineContent = { Text(group.group.title) },
                                    supportingContent = { Text(group.apps.take(6).joinToString { it.label } + if (group.apps.size > 6) "…" else "") },
                                    modifier = Modifier.clickable { choose(appWidgetId, group.group) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** This widget's own grid if it has one, else PadStudio's. */
    private suspend fun currentDensity(appWidgetId: Int): Density {
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        val state = getAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId)
        return state[GroupWidget.DENSITY]?.let { name -> Density.entries.firstOrNull { it.name == name } } ?: studio.config.config.value.density
    }

    private fun setDensity(appWidgetId: Int, density: Density) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@EditGroupWidgetActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@EditGroupWidgetActivity, glanceId) { it[GroupWidget.DENSITY] = density.name }
            GroupWidget().update(this@EditGroupWidgetActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        }
    }

    private fun choose(appWidgetId: Int, group: AppGroup) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@EditGroupWidgetActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@EditGroupWidgetActivity, glanceId) { it[GroupWidget.GROUP] = group.name }
            GroupWidget().update(this@EditGroupWidgetActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}
