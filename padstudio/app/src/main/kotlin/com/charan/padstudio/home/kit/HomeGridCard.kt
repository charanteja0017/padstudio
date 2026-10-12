package com.charan.padstudio.home.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.ActionChip
import com.charan.padstudio.ui.components.CardTone
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.SectionHeader
import com.charan.padstudio.ui.components.StatusLevel
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.SymIcon
import com.charan.padstudio.ui.components.statusInk
import java.time.LocalDate

/**
 * "Home screen grid", outcome G5-B (plan §6a, docs/decisions/ADR-005-home-grid.md): HyperOS has no grid setting
 * here, so the card shows the grid recorded on the tablet as a dated baseline (never as live state), the
 * nearest HyperOS options as links, and PadStudio's widget grid as the way to a denser page.
 *
 * Each link was resolved first ([links]); one that can't open is disabled with its reason. [failure] is the
 * last open that failed, with Retry.
 */
@Composable
internal fun HomeGridCard(
    record: HomeRecord,
    links: Map<HyperOsScreen, LinkResolution>,
    failure: Pair<HyperOsScreen, OpenFailure>?,
    onOpen: (HyperOsScreen) -> Unit,
    onDenser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val facts = (record as? HomeRecord.Recorded)?.facts
    StudioCard(modifier, spacing = 10.dp) {
        Column {
            SectionHeader(title = "Home screen grid")
            Text(hyperosGridLine(facts?.hyperosGridSetting), style = type.bodyLarge, color = scheme.onSurface)
            hyperosGridNote(facts?.hyperosGridSetting)?.let { Text(it, style = type.bodyMedium, color = scheme.onSurfaceVariant) }
        }
        Baseline(record)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Nearest options in HyperOS", style = type.bodyMedium, color = scheme.onSurfaceVariant)
                SymIcon(Sym.open, contentDescription = null, size = 16.dp, tint = scheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HyperOsLink.entries.forEach { link ->
                    ActionChip(
                        label = link.label,
                        onClick = { onOpen(link.screen) },
                        icon = linkIcon(link),
                        enabled = links[link.screen]?.state == LinkState.READY,
                    )
                }
            }
            HyperOsScreen.entries.forEach { screen ->
                links[screen]?.let { unavailableLine(screen, it.state) }?.let { ProblemLine(it) }
            }
            failure?.let { (screen, why) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProblemLine(openFailedLine(screen, why), Modifier.weight(1f))
                    PillButton(text = "Retry", onClick = { onOpen(screen) }, style = PillStyle.Text)
                }
            }
        }
        PillButton(
            text = "For a denser page, use PadStudio's widget grid",
            onClick = onDenser,
            style = PillStyle.Text,
            icon = Sym.widgets,
            trailingIcon = Sym.chevron,
            // A text button's 12 dp inner edge, so its icon lines up with the card's text (as the mock).
            modifier = Modifier.offset(x = (-12).dp),
        )
    }
}

/** The recorded grid: a big readout, "Recorded 11 Oct: 6 × 4, names hidden", and "Recorded baseline, not live". */
@Composable
private fun Baseline(record: HomeRecord) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    // A card inside the card (CardTone.High), as the mock's inset.
    StudioCard(Modifier.fillMaxWidth(), tone = CardTone.High, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            when (record) {
                is HomeRecord.Recorded -> {
                    Text(
                        recordedReadout(record.facts),
                        style = type.headlineMedium.copy(fontFeatureSettings = "tnum"),
                        color = scheme.onSurface,
                        // The line beside it says the same in words.
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(recordedLine(record.facts, LocalDate.now()), style = type.labelLarge, color = scheme.onSurface)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SymIcon(Sym.time, contentDescription = null, size = 16.dp, tint = scheme.onSurfaceVariant)
                            Text(baselineTag(record.facts.orientation), style = type.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
                HomeRecord.Missing -> Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(NOT_RECORDED_LINE, style = type.labelLarge, color = scheme.onSurface)
                    Text(NOT_RECORDED_HINT, style = type.bodySmall, color = scheme.onSurfaceVariant)
                }
                HomeRecord.Loading -> Text(
                    "Reading the recorded grid…",
                    style = type.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** Why something can't be done: the warning symbol (not colour alone) and the reason. */
@Composable
internal fun ProblemLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SymIcon(Sym.warning, contentDescription = null, size = 18.dp, tint = statusInk(StatusLevel.Warn), modifier = Modifier.padding(top = 1.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun linkIcon(link: HyperOsLink): Int = when (link) {
    HyperOsLink.NAMES -> Sym.namesHidden
    HyperOsLink.LOCK_LAYOUT -> Sym.lock
    HyperOsLink.MODE -> Sym.home
    HyperOsLink.ICON_BACKGROUND -> Sym.looks
}
