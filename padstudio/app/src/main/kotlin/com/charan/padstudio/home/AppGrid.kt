package com.charan.padstudio.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.appwidget.updateAll
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.icons.IconRepository
import com.charan.padstudio.studio
import com.charan.padstudio.ui.themedIconColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The dense app grid shared by the folder panel and the library. Tap opens the app; long-press
 * offers App info, Move to group, Hide and Uninstall.
 */
@Composable
fun AppGrid(apps: List<InstalledApp>, density: Density, onLaunched: () -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(density.cellDp.dp),
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(apps, key = { it.component.flattenToShortString() }) { app ->
            AppTile(app, density, onLaunched)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(app: InstalledApp, density: Density, onLaunched: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = {
                        context.startActivity(app.launchIntent())
                        onLaunched()
                    },
                    onLongClick = { menu = true },
                )
                .padding(vertical = 6.dp),
        ) {
            AppIcon(app, density.iconDp)
            if (density.showLabels) {
                Spacer(Modifier.height(4.dp))
                Text(
                    app.facts.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("App info") }, onClick = {
                menu = false
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.component.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            })
            DropdownMenuItem(text = { Text("Move to group…") }, onClick = {
                menu = false
                moving = true
            })
            DropdownMenuItem(text = { Text("Hide") }, onClick = {
                menu = false
                moveApp(context, app, AppGroup.HIDDEN)
            })
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = {
                menu = false
                context.startActivity(
                    Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.component.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            })
        }
    }
    if (moving) {
        MoveDialog(app.facts.label, onDismiss = { moving = false }) { group ->
            moving = false
            moveApp(context, app, group)
        }
    }
}

@Composable
fun AppIcon(app: InstalledApp, sizeDp: Int) {
    val context = LocalContext.current
    val config by context.studio.config.config.collectAsState()
    val bitmap by produceState<ImageBitmap?>(null, app.component, config.iconStyle, config.iconPack) {
        value = withContext(Dispatchers.Default) {
            val colors = themedIconColors(context)
            context.studio.icons.drawable(app, config.iconStyle, config.iconPack, colors.background, colors.glyph)
                .toBitmap(IconRepository.ICON_PX, IconRepository.ICON_PX)
                .asImageBitmap()
        }
    }
    Box(Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, contentDescription = app.facts.label, modifier = Modifier.size(sizeDp.dp)) }
    }
}

@Composable
fun MoveDialog(label: String, onDismiss: () -> Unit, onMove: (AppGroup) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move $label to…") },
        text = {
            LazyColumn {
                items(AppGroup.entries) { group ->
                    TextButton(onClick = { onMove(group) }, modifier = Modifier.fillMaxWidth()) {
                        Text(group.title, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Saves the user's choice, re-sorts, and redraws every group widget. */
fun moveApp(context: Context, app: InstalledApp, group: AppGroup) {
    val studio = context.studio
    studio.config.update { it.copy(overrides = it.overrides + (app.component.packageName to group)) }
    CoroutineScope(Dispatchers.Default).launch {
        studio.apps.snapshot(force = true)
        GroupWidget().updateAll(context.applicationContext)
    }
}
