package com.charan.padstudio.today

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.charan.padstudio.MainViewModel
import com.charan.padstudio.Tab
import com.charan.padstudio.ui.ShellDefaults
import com.charan.padstudio.ui.theme.Tokens

/**
 * Today, the start screen (plan §10 "Today"). A placeholder until the W3 Today screen: the title and subtitle only.
 *
 * The finished screen builds its hero from [model]'s state (plan §7), launches intents with [onStart] (it refreshes
 * the checks when nothing can handle one), and opens Pen, Home, Looks and Setup from its cards with [onOpenTab].
 * Internal because [Tab] is.
 */
@Composable
internal fun TodayScreen(
    model: MainViewModel,
    onStart: (Intent) -> Unit,
    onOpenTab: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(ShellDefaults.ContentPadding)) {
        Text(
            "Today",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = Tokens.WeightMedium),
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Your tablet, set up your way.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
