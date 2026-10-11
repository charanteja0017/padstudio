package com.charan.padstudio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

/** Open-source work PadStudio builds on, then every bundled library and its licence. */
private val credits = listOf(
    "Key Mapper (keymapperorg, GPL-3.0) runs the pen buttons; PadStudio writes profiles in its backup format.",
    "Lawnchair's flowerpot (GPL-3.0-or-later): the app-category rules behind automatic grouping, used unchanged.",
    "Lawnchair's icon-pack handling informed PadStudio's appfilter.xml reader.",
    "FocusPen Pro X (HMQYHM, GPL-3.0-only) documented how HyperOS reports Focus Pen Pro gestures.",
    "Lawnicons (Apache-2.0) and Arcticons (GPL-3.0 / CC BY-SA 4.0) supply themed icons when installed.",
)

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val libraries by produceLibraries()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text("PadStudio is GPL-3.0 and built on", style = MaterialTheme.typography.titleMedium)
            credits.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
        }
        HorizontalDivider()
        LibrariesContainer(libraries, Modifier.fillMaxSize())
    }
}
