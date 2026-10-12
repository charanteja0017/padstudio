package com.charan.padstudio.pen.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.charan.padstudio.core.pen.PenControl
import com.charan.padstudio.core.pen.PenMode
import com.charan.padstudio.core.pen.PenTrigger
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.Disclosure
import com.charan.padstudio.ui.components.ListRow
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.RowLeading
import com.charan.padstudio.ui.components.RowTrailing
import com.charan.padstudio.ui.components.SegmentedChoice
import com.charan.padstudio.ui.components.cardBleed

/** One control as Developer details lists it. */
@Immutable
data class ControlDetail(
    val control: PenControl,
    /** Recorded in PadStudio's window (StudioConfig.controls). */
    val recorded: PenTrigger?,
    /** What Key Mapper will use: [recorded], or the probe's measurement. */
    val effective: PenTrigger?,
) {
    val measured: Boolean get() = recorded == null && effective != null
}

/** What Developer details shows besides the controls (plan §10: devices, the profile hash, raw warnings). */
@Immutable
data class DeveloperFacts(
    val devices: List<String>,
    val profileLine: String?,
    val sentHash: String?,
    val keyMapperLine: String,
    val penActions: String,
    val expertMode: String,
    val warnings: List<String>,
)

/**
 * Advanced (plan §10, collapsed by default): the setup (Basic or Expert), whether Key Mapper can press keys
 * (Expert only, as before), the notes app with a "None" choice, Reset pen actions, and Developer details.
 */
@Composable
fun AdvancedSection(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    mode: PenMode,
    onMode: (PenMode) -> Unit,
    keyInjection: Boolean,
    onKeyInjection: (Boolean) -> Unit,
    notesApps: List<InstalledApp>,
    notesApp: String?,
    onNotesApp: (String?) -> Unit,
    onResetActions: () -> Unit,
    developer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    Disclosure(expanded = expanded, onExpandedChange = onExpandedChange, modifier = modifier, title = "Advanced", icon = Sym.settings) {
        // Disclosure sets its body in monospace for codes; these are settings, in the body face.
        CompositionLocalProvider(LocalTextStyle provides type.bodyMedium, LocalContentColor provides scheme.onSurface) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SubHeading("Setup")
                SegmentedChoice(
                    options = PenMode.entries,
                    selected = mode,
                    onSelect = onMode,
                    label = { if (it == PenMode.BASIC) "Basic" else "Expert" },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(text = setupDetail(mode), style = type.bodyMedium, color = scheme.onSurfaceVariant)
                if (mode == PenMode.EXPERT) {
                    ListRow(
                        headline = "Key Mapper can press keys",
                        modifier = Modifier.cardBleed(),
                        supporting = "Turn on only after enabling Key Mapper's Expert-mode setting for key event actions. " +
                            "Then page turns use the Page Up and Page Down keys instead of swipes.",
                        trailing = RowTrailing.Toggle(keyInjection, onKeyInjection),
                    )
                }
                HorizontalDivider(color = scheme.outlineVariant)
                SubHeading("Notes app")
                Text(text = "Opened by “Open notes app” presses.", style = type.bodyMedium, color = scheme.onSurfaceVariant)
                Column(Modifier.cardBleed().selectableGroup()) {
                    ListRow(
                        headline = "None",
                        leading = RowLeading.Symbol(Sym.close),
                        selected = notesApp == null,
                        onClick = { onNotesApp(null) },
                    )
                    notesApps.forEach { app ->
                        ListRow(
                            headline = app.facts.label,
                            leading = RowLeading.App(app),
                            selected = notesApp == app.facts.packageName,
                            onClick = { onNotesApp(app.facts.packageName) },
                        )
                    }
                }
                HorizontalDivider(color = scheme.outlineVariant)
                SubHeading("Pen actions")
                Text(
                    text = "Every app group goes back to the default actions for this pen and setup. Recorded buttons stay.",
                    style = type.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                PillButton(text = "Reset pen actions", onClick = onResetActions, style = PillStyle.Outlined, icon = Sym.refresh)
                developer()
            }
        }
    }
}

/**
 * Developer details (plan §10): each control's codes with Record, Record again and Forget; the input devices; the
 * profile and its hash; Key Mapper's version; the §7 rows that can't be observed; and core's raw warnings.
 * [listening] is the control being recorded; Record waits while any control is.
 */
@Composable
fun DeveloperDetails(
    controls: List<ControlDetail>,
    mode: PenMode,
    listening: String?,
    facts: DeveloperFacts,
    onRecord: (String) -> Unit,
    onForget: (String) -> Unit,
    onResendPenActions: () -> Unit,
) {
    Disclosure(title = "Developer details", icon = Sym.code) {
        controls.forEach { detail ->
            Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(text = detail.control.label, color = MaterialTheme.colorScheme.onSurface)
                Text(text = describeTrigger(detail.effective) + if (detail.measured) " · measured by the probe" else "")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        listening == detail.control.id -> Text(text = "Listening…", modifier = Modifier.padding(vertical = 12.dp))
                        else -> when (recordChoice(detail.recorded, detail.measured, mode)) {
                            RecordChoice.MEASURED -> Unit
                            RecordChoice.RECORD -> PillButton(
                                text = "Record",
                                onClick = { onRecord(detail.control.id) },
                                style = PillStyle.Text,
                                enabled = listening == null,
                            )
                            RecordChoice.RECORD_AGAIN -> PillButton(
                                text = "Record again",
                                onClick = { onRecord(detail.control.id) },
                                style = PillStyle.Text,
                                enabled = listening == null,
                            )
                        }
                    }
                    if (detail.recorded != null && listening != detail.control.id) {
                        PillButton(text = "Forget", onClick = { onForget(detail.control.id) }, style = PillStyle.Text, enabled = listening == null)
                    }
                }
            }
        }
        if (mode == PenMode.EXPERT && controls.any { it.measured }) {
            Text(text = "HyperOS keeps these presses away from apps, so they are measured over adb (tools/capture-pen.sh) instead of recorded here.")
        }
        DetailLine("Devices", facts.devices.ifEmpty { listOf("None recorded") }.joinToString("\n"))
        facts.profileLine?.let { DetailLine("Profile", it) }
        DetailLine("Sent to Key Mapper", facts.sentHash ?: "Nothing yet")
        DetailLine("Key Mapper", facts.keyMapperLine)
        Text(text = "Pen actions", color = MaterialTheme.colorScheme.onSurface)
        Text(text = facts.penActions)
        PillButton(text = "Send again", onClick = onResendPenActions, style = PillStyle.Text)
        DetailLine("Expert mode", "${facts.expertMode} (tools/pen-health.sh checks it over adb)")
        DetailLine("Warnings", facts.warnings.ifEmpty { listOf("None") }.joinToString("\n"))
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(text = label, color = MaterialTheme.colorScheme.onSurface)
        Text(text = value)
    }
}

@Composable
private fun SubHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}
