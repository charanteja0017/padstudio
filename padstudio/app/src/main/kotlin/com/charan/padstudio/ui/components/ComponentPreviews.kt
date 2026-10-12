package com.charan.padstudio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charan.padstudio.core.pen.PenIntent
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.theme.BrandPalette
import com.charan.padstudio.ui.theme.Tokens

// Previews of the component library on the tablet's 1164 × 777 dp landscape screen, in the brand colours
// (Tokens, not the wallpaper, so they render without a device). Example copy only.

@Preview(name = "Today-like page, Violet dark", widthDp = 1164, heightDp = 777)
@Composable
private fun PagePreview() = PreviewTheme(Tokens.darkScheme(BrandPalette.VIOLET)) {
    ScreenScaffold(
        title = "Today",
        subtitle = "Your tablet, set up your way.",
        actions = { PillButton(text = "Open library", onClick = {}, style = PillStyle.Outlined, icon = Sym.library) },
    ) {
        CardColumns(
            start = {
                StudioCard(tone = CardTone.High) {
                    SectionHeader(title = "Focus Pen Pro", overline = "Your pen", supporting = "4 controls configured")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(text = "Test pen", onClick = {}, icon = Sym.testPen)
                        PillButton(text = "Edit controls", onClick = {}, style = PillStyle.Outlined, icon = Sym.edit)
                    }
                    StatusPill(text = "Sent to Key Mapper 14:05", level = StatusLevel.Good, icon = Sym.send)
                }
                StudioCard(spacing = 4.dp) {
                    SectionHeader(title = "Pen by app", actionLabel = "Manage", onAction = {})
                    Column(Modifier.cardBleed()) {
                        ListRow(
                            headline = "Camera",
                            supporting = "Squeeze to take a photo",
                            leading = RowLeading.Symbol(Sym.forIntent(PenIntent.SHUTTER), tonal = true),
                            trailing = RowTrailing.Chevron,
                            onClick = {},
                        )
                        ListRow(
                            headline = "Reading & PDF",
                            supporting = "Slide to turn pages",
                            leading = RowLeading.Symbol(Sym.nextPage, tonal = true),
                            trailing = RowTrailing.Chevron,
                            onClick = {},
                        )
                    }
                }
            },
            end = {
                StudioCard {
                    var tab by remember { mutableStateOf("Widgets") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(title = "Home screen", modifier = Modifier.weight(1f))
                        SegmentedChoice(options = listOf("Groups", "Widgets"), selected = tab, onSelect = { tab = it }, label = { it })
                    }
                    Column(Modifier.cardBleed()) {
                        ListRow(headline = "Widget layout", trailing = RowTrailing.Value("4 × 3"), onClick = {})
                    }
                }
                StudioCard {
                    var palette by remember { mutableIntStateOf(0) }
                    SectionHeader(title = "Make it yours", supporting = "Violet dusk · Theme & wallpaper")
                    SwatchRow(swatches = previewSwatches(), selectedIndex = palette, onSelect = { palette = it })
                }
            },
        )
    }
}

@Preview(name = "Controls, Violet dark", widthDp = 1164, heightDp = 777)
@Composable
private fun ControlsDarkPreview() = PreviewTheme(Tokens.darkScheme(BrandPalette.VIOLET)) { ControlsSheet() }

@Preview(name = "Controls, Ocean light", widthDp = 1164, heightDp = 777)
@Composable
private fun ControlsLightPreview() = PreviewTheme(Tokens.lightScheme(BrandPalette.OCEAN)) { ControlsSheet() }

@Composable
private fun ControlsSheet() {
    ScreenScaffold(title = "Kit", subtitle = "Every shared component") {
        CardGrid(minColumnWidth = 280.dp) {
            StudioCard {
                SectionHeader(title = "Buttons", accent = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(text = "Send", onClick = {}, icon = Sym.send)
                    PillButton(text = "Pause pen", onClick = {}, style = PillStyle.Tonal, icon = Sym.pause)
                    PillButton(text = "Test pen", onClick = {}, style = PillStyle.Outlined, icon = Sym.testPen)
                    PillButton(text = "Manage", onClick = {}, style = PillStyle.Text, trailingIcon = Sym.chevron)
                    PillButton(text = "Send", onClick = {}, enabled = false)
                    PillButton(text = "Fix", onClick = {}, style = PillStyle.Tonal, shape = PillButtonDefaults.FullPill)
                    SymIconButton(icon = Sym.more, contentDescription = "More", onClick = {})
                    SymIconButton(icon = Sym.edit, contentDescription = "Edit", onClick = {}, style = PillStyle.Tonal)
                    SymIconButton(icon = Sym.add, contentDescription = "Add", onClick = {}, style = PillStyle.Filled)
                }
                var reading by remember { mutableStateOf(true) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(label = "Study", selected = false, onClick = {})
                    ChoiceChip(label = "Reading", selected = reading, onClick = { reading = !reading })
                    ActionChip(label = "Don't show text", onClick = {}, icon = Sym.open)
                }
                var kind by remember { mutableStateOf("Focus Pen Pro") }
                SegmentedChoice(
                    options = listOf("Two buttons", "Focus Pen Pro"),
                    selected = kind,
                    onSelect = { kind = it },
                    label = { it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            StudioCard {
                SectionHeader(title = "Status", accent = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(text = "Key Mapper is listening", level = StatusLevel.Good)
                    StatusPill(text = "Changes not sent yet", level = StatusLevel.Warn)
                    StatusPill(text = "Key Mapper is off", level = StatusLevel.Bad)
                    StatusPill(text = "Can't be checked from the tablet", level = StatusLevel.Unknown)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProgressRing(progress = 7f / 8f, value = "7", contentDescription = "7 of 8 ready")
                    Text(text = "of 8 ready", style = MaterialTheme.typography.titleLarge)
                }
            }
            StudioCard {
                SectionHeader(title = "Pen", accent = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BindingChip(onClick = {}, action = "Shutter", icon = Sym.forIntent(PenIntent.SHUTTER), control = "Squeeze")
                    BindingChip(onClick = {}, action = "Last app", icon = Sym.forIntent(PenIntent.LAST_APP), control = "Double tap")
                    BindingChip(onClick = {})
                }
                var on by remember { mutableStateOf(true) }
                Column(Modifier.cardBleed()) {
                    ListRow(headline = "Pen actions", trailing = RowTrailing.Toggle(on) { on = it })
                    ListRow(
                        headline = "Flashlight",
                        supporting = "Unavailable: needs camera permission",
                        leading = RowLeading.Symbol(Sym.flashlight),
                        enabled = false,
                        onClick = {},
                    )
                }
            }
            StudioCard(modifier = Modifier.span(2)) {
                SectionHeader(title = "Picker and details", accent = true)
                var chosen by remember { mutableIntStateOf(0) }
                Column(Modifier.cardBleed().selectableGroup()) {
                    listOf(PenIntent.SHUTTER, PenIntent.OPEN_CAMERA).forEachIndexed { index, intent ->
                        ListRow(
                            headline = intent.label,
                            leading = RowLeading.Symbol(Sym.forIntent(intent)),
                            trailing = if (index == chosen) RowTrailing.Symbol(Sym.ok) else RowTrailing.None,
                            selected = index == chosen,
                            onClick = { chosen = index },
                        )
                    }
                }
                Disclosure(initiallyExpanded = true) {
                    Text("Profile cdbc98bc0674 · 32,748 bytes")
                    Text("Squeeze: evdev 332/189")
                }
            }
            StudioCard(tone = CardTone.Outlined) {
                EmptyState(title = "No apps in Games right now", icon = Sym.widgets, actionLabel = "Edit", onAction = {})
            }
        }
    }
}

private fun previewSwatches(): List<Swatch> = BrandPalette.entries.map { palette ->
    val scheme = Tokens.darkScheme(palette)
    Swatch(label = palette.name.lowercase().replaceFirstChar { it.uppercase() }, color = scheme.primary, contentColor = scheme.onPrimary)
}

@Composable
private fun PreviewTheme(scheme: ColorScheme, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme) {
        Surface(color = scheme.background, contentColor = scheme.onBackground) {
            Row {
                // Stands in for the shell's expanded rail, so the content column is as wide as on the tablet.
                Column(Modifier.width(216.dp).padding(16.dp)) { Text("PadStudio", style = MaterialTheme.typography.titleLarge) }
                content()
            }
        }
    }
}
