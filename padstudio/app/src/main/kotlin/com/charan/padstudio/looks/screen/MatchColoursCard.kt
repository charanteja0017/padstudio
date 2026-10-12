package com.charan.padstudio.looks.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charan.padstudio.ui.components.ListRow
import com.charan.padstudio.ui.components.RowTrailing
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.cardBleed
import com.charan.padstudio.ui.theme.Tokens

/**
 * Looks → "Match wallpaper colours" (plan §3.1, mock-looks.html): one switch row. On, PadStudio and its widgets
 * take Material You colours from the wallpaper; off (the default), the brand [palette]. The supporting text says
 * which, and why the brand colours are the default. When the system can't give wallpaper colours ([supported]
 * false), the switch is disabled and the text says why. [onCheckedChange] goes to
 * MainViewModel.setMatchWallpaperColours.
 */
@Composable
internal fun MatchColoursCard(
    checked: Boolean,
    supported: Boolean,
    palette: String,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    StudioCard(modifier, contentPadding = PaddingValues(horizontal = Tokens.CardPadding, vertical = 8.dp)) {
        ListRow(
            headline = "Match wallpaper colours",
            supporting = matchColoursText(on = checked && supported, supported = supported, palette = palette),
            trailing = RowTrailing.Toggle(checked = checked && supported, onCheckedChange = onCheckedChange),
            enabled = supported,
            modifier = Modifier.cardBleed(),
        )
    }
}
