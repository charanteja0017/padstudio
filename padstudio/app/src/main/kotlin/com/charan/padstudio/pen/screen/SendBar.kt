package com.charan.padstudio.pen.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.charan.padstudio.core.pen.PenStatus
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.StatusLevel
import com.charan.padstudio.ui.components.StatusPill
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.SymIcon
import com.charan.padstudio.ui.components.statusInk

/** Where the last Send or Pause/Resume stands (plan §13, "Every action's states"). */
@Immutable
sealed interface ActionNote {
    /** Pending: "Opening Key Mapper…". */
    data class Pending(val text: String) : ActionNote

    /** Failed: the plain reason, with Retry. */
    data class Failed(val text: String) : ActionNote

    /** Unknown outcome, as for pause and resume: "Requested". */
    data class Requested(val text: String) : ActionNote
}

/** A §7 status level for a core PenStatus tone. */
fun levelOf(tone: PenStatus.Tone): StatusLevel = when (tone) {
    PenStatus.Tone.GOOD -> StatusLevel.Good
    PenStatus.Tone.WARN -> StatusLevel.Warn
    PenStatus.Tone.BAD -> StatusLevel.Bad
    PenStatus.Tone.UNKNOWN -> StatusLevel.Unknown
}

/**
 * The bottom bar (mock-up): whether Key Mapper has the current profile ([sentLine], from PenStatus.sentText),
 * Send to Key Mapper (the one explicit apply, plan §13; filled while there are changes to send) and Test pen. Under
 * the status: the last action's [note], and pills for Key Mapper being off (with Fix), or not installed
 * ([keyMapperInstalled] false: there is nothing for Fix to open, so no Fix), or the pen being paused.
 * The buttons sit beside the status when there's room and under it otherwise.
 */
@Composable
fun SendBar(
    status: PenStatus,
    keyMapperInstalled: Boolean,
    sentLine: String,
    canSend: Boolean,
    note: ActionNote?,
    onRetry: () -> Unit,
    onSend: () -> Unit,
    onTestPen: () -> Unit,
    onFixKeyMapper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val changes = status.needsSend
    StudioCard(modifier, contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
        BesideOrUnder(
            status = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SymIcon(
                            id = if (changes) Sym.warning else Sym.send,
                            contentDescription = null,
                            size = 20.dp,
                            tint = statusInk(levelOf(status.sent.tone)),
                        )
                        Text(text = sentLine, style = type.labelLarge, color = scheme.onSurface)
                    }
                    if (changes) {
                        Text(text = "Key Mapper asks how to restore: choose Replace.", style = type.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    when (note) {
                        null -> Unit
                        is ActionNote.Pending -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(text = note.text, style = type.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                        is ActionNote.Failed -> Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SymIcon(Sym.error, contentDescription = null, size = 18.dp, tint = statusInk(StatusLevel.Bad))
                                Text(text = note.text, style = type.bodyMedium, color = scheme.onSurface)
                            }
                            PillButton(text = "Retry", onClick = onRetry, style = PillStyle.Text, icon = Sym.refresh)
                        }
                        is ActionNote.Requested -> Text(
                            text = note.text,
                            style = type.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (status.keyMapper != PenStatus.KeyMapper.LISTENING) {
                            StatusPill(
                                text = keyMapperBarText(status, keyMapperInstalled),
                                level = levelOf(status.keyMapper.tone),
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                            if (offersKeyMapperFix(status, keyMapperInstalled)) {
                                PillButton(text = "Fix", onClick = onFixKeyMapper, style = PillStyle.Text)
                            }
                        }
                        if (status.penActions == PenStatus.PenActions.OFF_AS_REQUESTED) {
                            StatusPill(
                                text = status.penActionsLine,
                                level = levelOf(status.penActions.tone),
                                icon = Sym.pause,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        }
                    }
                }
            },
            actions = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PillButton(
                        text = "Send to Key Mapper",
                        onClick = onSend,
                        style = if (changes) PillStyle.Filled else PillStyle.Outlined,
                        icon = Sym.send,
                        enabled = canSend && note !is ActionNote.Pending,
                    )
                    PillButton(text = "Test pen", onClick = onTestPen, style = if (changes) PillStyle.Outlined else PillStyle.Filled, icon = Sym.testPen)
                }
            },
        )
    }
}

/**
 * [status] on the left and [actions] on the right when the status keeps at least [MIN_STATUS] beside them;
 * otherwise the actions go under the status, at the end (large text, a narrow window).
 */
@Composable
private fun BesideOrUnder(status: @Composable () -> Unit, actions: @Composable () -> Unit) {
    Layout(contents = listOf(status, actions)) { (statusMeasurables, actionMeasurables), constraints ->
        val width = constraints.maxWidth
        val gap = 12.dp.roundToPx()
        val actionPlaceables = actionMeasurables.map { it.measure(Constraints(maxWidth = width)) }
        val actionsWidth = actionPlaceables.maxOfOrNull { it.width } ?: 0
        val actionsHeight = actionPlaceables.sumOf { it.height }
        val room = width - actionsWidth - gap
        if (room >= MIN_STATUS.roundToPx()) {
            val statusPlaceables = statusMeasurables.map { it.measure(Constraints(maxWidth = room)) }
            val statusHeight = statusPlaceables.sumOf { it.height }
            val height = maxOf(statusHeight, actionsHeight)
            layout(width, height) {
                var y = (height - statusHeight) / 2
                statusPlaceables.forEach { it.placeRelative(0, y); y += it.height }
                var ay = (height - actionsHeight) / 2
                actionPlaceables.forEach { it.placeRelative(width - it.width, ay); ay += it.height }
            }
        } else {
            val statusPlaceables = statusMeasurables.map { it.measure(Constraints(maxWidth = width)) }
            val statusHeight = statusPlaceables.sumOf { it.height }
            // Too narrow to sit beside the status: the buttons (measured at the full width) wrap onto lines of their own.
            layout(width, statusHeight + gap + actionsHeight) {
                var y = 0
                statusPlaceables.forEach { it.placeRelative(0, y); y += it.height }
                y += gap
                actionPlaceables.forEach { it.placeRelative(width - it.width, y); y += it.height }
            }
        }
    }
}

private val MIN_STATUS = 200.dp
