package com.charan.padstudio.pen.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.charan.padstudio.core.pen.PenIntent
import com.charan.padstudio.core.pen.Press
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.CardTone
import com.charan.padstudio.ui.components.ChoiceChip
import com.charan.padstudio.ui.components.EmptyState
import com.charan.padstudio.ui.components.ListRow
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.RowLeading
import com.charan.padstudio.ui.components.RowTrailing
import com.charan.padstudio.ui.components.SegmentedChoice
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.SymIcon
import com.charan.padstudio.ui.components.SymIconButton
import com.charan.padstudio.ui.theme.Tokens

/** A control that has no trigger yet, so its actions can't run: the picker offers to record it. */
@Immutable
data class RecordNotice(val text: String, val enabled: Boolean, val onRecord: () -> Unit)

/**
 * The action picker (plan §10 "Action picker"; the kit's sheet): what one press of one control does in one
 * context. The screen puts it in a side sheet at 840 dp and wider and in a bottom sheet below that; the content is
 * the same.
 *
 * - Header: the control ([control]), "In Camera" ([contextTitle]) and Close.
 * - [presses]: Press, Hold, Double press when the control has more than one; [press] is the one being edited.
 * - [notice]: "Squeeze isn't recorded yet…" with Record, when the control has no trigger.
 * - Search through all 25 actions ([query]); the group chips ([group]) narrow the list when there's no search.
 * - The list: "Nothing (leave to the app / HyperOS)" first, then each group; actions Key Mapper can't run without a
 *   permission say "Unavailable: needs …" (core's PenIntent.needs) and can still be chosen, as before.
 *
 * Choosing calls [onPick] with the action, or null for Nothing; settings save at once (plan §13).
 */
@Composable
fun ActionPicker(
    control: String,
    contextTitle: String,
    presses: List<Press>,
    press: Press,
    onPress: (Press) -> Unit,
    current: PenIntent?,
    onPick: (PenIntent?) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    group: ActionGroup?,
    onGroup: (ActionGroup?) -> Unit,
    onClose: () -> Unit,
    notice: RecordNotice?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val searching = query.isNotBlank()
    val sections = pickerSections(query, group)
    val nothing = showsNothingChoice(query)
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(text = control, style = type.titleLarge, color = scheme.onSurface, modifier = Modifier.semantics { heading() })
                Text(text = "In $contextTitle", style = type.bodyMedium, color = scheme.onSurfaceVariant)
            }
            SymIconButton(icon = Sym.close, contentDescription = "Close", onClick = onClose)
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth().selectableGroup(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
        ) {
            // The choosing controls scroll with the list, so large text never squeezes the list out of the sheet.
            item(key = "controls") {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (presses.size > 1) {
                        SegmentedChoice(
                            options = presses,
                            selected = press,
                            onSelect = onPress,
                            label = ::pressLabel,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (notice != null) {
                        StudioCard(tone = CardTone.Tonal, contentPadding = PaddingValues(16.dp), spacing = 8.dp) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SymIcon(Sym.warning, contentDescription = null, size = 20.dp)
                                Text(text = notice.text, style = type.bodyMedium)
                            }
                            PillButton(text = "Record", onClick = notice.onRecord, style = PillStyle.Filled, enabled = notice.enabled)
                        }
                    }
                    SearchField(query, onQuery)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionGroup.entries.forEach { g ->
                            ChoiceChip(
                                label = g.label,
                                selected = !searching && group == g,
                                onClick = {
                                    onQuery("")
                                    onGroup(if (!searching && group == g) null else g)
                                },
                            )
                        }
                    }
                }
            }
            if (nothing) {
                item(key = "nothing") {
                    ListRow(
                        headline = NOTHING_LABEL,
                        leading = RowLeading.Symbol(Sym.close),
                        trailing = if (current == null) RowTrailing.Symbol(Sym.ok) else RowTrailing.None,
                        selected = current == null,
                        onClick = { onPick(null) },
                    )
                }
            }
            sections.forEach { section ->
                item(key = "group-${section.group.name}") {
                    Text(
                        text = section.group.label,
                        style = type.labelLarge,
                        color = scheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                    )
                }
                items(section.actions, key = { it.name }) { intent ->
                    ListRow(
                        headline = intent.label,
                        supporting = unavailableText(intent),
                        leading = RowLeading.Symbol(Sym.forIntent(intent)),
                        trailing = if (intent == current) RowTrailing.Symbol(Sym.ok) else RowTrailing.None,
                        selected = intent == current,
                        onClick = { onPick(intent) },
                    )
                }
            }
            if (sections.isEmpty() && !nothing) {
                item(key = "none") { EmptyState(title = "No actions match “${query.trim()}”", icon = Sym.search) }
            }
        }
    }
}

/** The kit's search field: a full pill on surface container highest, no underline. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val label = "Search $actionCount actions"
    TextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        placeholder = { Text(text = label) },
        leadingIcon = { SymIcon(Sym.search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            { SymIconButton(icon = Sym.close, contentDescription = "Clear search", onClick = { onQuery("") }) }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(Tokens.CornerFull),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHighest,
            unfocusedContainerColor = scheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    )
}
