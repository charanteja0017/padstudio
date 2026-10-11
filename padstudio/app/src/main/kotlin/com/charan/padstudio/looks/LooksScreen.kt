package com.charan.padstudio.looks

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charan.padstudio.MainViewModel
import com.charan.padstudio.data.IconStyle
import com.charan.padstudio.pen.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Icon style for widgets and panels, and a wallpaper that matches the wallpaper-derived accent. */
@Composable
fun LooksScreen(model: MainViewModel, modifier: Modifier = Modifier) {
    val config by model.config.collectAsStateWithLifecycle()
    val packs = remember { model.iconPacks() }
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    var seed by remember { mutableLongStateOf(7L) }
    var dark by remember { mutableStateOf(false) }
    val preview by produceState<ImageBitmap?>(null, seed, dark, accent) {
        value = withContext(Dispatchers.Default) { WallpaperGenerator.render(480, seed, accent, dark).asImageBitmap() }
    }

    LazyColumn(modifier, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Section("Icons") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconStyle.entries.forEach { style ->
                        FilterChip(config.iconStyle == style, onClick = { model.setIconStyle(style) }, label = { Text(style.label) })
                    }
                }
                if (config.iconStyle == IconStyle.ICON_PACK) {
                    if (packs.isEmpty()) {
                        Text("No icon pack installed. Arcticons (F-Droid) or Lawnicons work.", style = MaterialTheme.typography.bodySmall)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        packs.forEach { pack ->
                            FilterChip(config.iconPack == pack.packageName, onClick = { model.setIconStyle(IconStyle.ICON_PACK, pack.packageName) }, label = { Text(pack.label) })
                        }
                    }
                }
                Text(
                    "Themed: each app's monochrome icon on your wallpaper's accent, like Android's themed icons; Lawnicons fills gaps when installed.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Section("Wallpaper") {
                preview?.let { Image(it, contentDescription = "Wallpaper preview", modifier = Modifier.size(240.dp).clip(RoundedCornerShape(20.dp))) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!dark, onClick = { dark = false }, label = { Text("Light") })
                    FilterChip(dark, onClick = { dark = true }, label = { Text("Dark") })
                    OutlinedButton(onClick = { seed += 1 }) { Text("Another") }
                    Button(onClick = { model.applyWallpaper(seed, dark, accent) }) { Text("Apply wallpaper") }
                }
                Text("Note your current wallpaper first: apps can't read it back, so undo resets to the HyperOS default.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
