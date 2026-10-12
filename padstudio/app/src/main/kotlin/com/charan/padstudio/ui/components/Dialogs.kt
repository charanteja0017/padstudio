package com.charan.padstudio.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.charan.padstudio.ui.theme.Tokens

/**
 * A confirmation before a destructive or far-reaching action (plan §4: "Confirmation dialogs before
 * destructive actions"), as the kit's `.dialog`: 28 dp corners on surface container high, [title] in the
 * headline role, [body] in bodyLarge (Material's AlertDialog would set it in bodyMedium, 13 sp; the
 * preview's dialog text is 16 sp), and two text buttons, [dismissLabel] then [confirmLabel]. An optional
 * [icon] sits above the title in secondary.
 *
 * Show it while your own flag is true. [onDismiss] runs for the dismiss button, Back and a tap outside;
 * [onConfirm] runs for the confirm button, so clear the flag in both.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Cancel",
    @DrawableRes icon: Int? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { PillButton(text = confirmLabel, onClick = onConfirm, style = PillStyle.Text) },
        modifier = modifier,
        dismissButton = { PillButton(text = dismissLabel, onClick = onDismiss, style = PillStyle.Text) },
        icon = if (icon != null) {
            { SymIcon(icon, contentDescription = null) }
        } else {
            null
        },
        title = { Text(text = title) },
        text = { Text(text = body, style = MaterialTheme.typography.bodyLarge) },
        shape = RoundedCornerShape(Tokens.CornerXl),
    )
}
