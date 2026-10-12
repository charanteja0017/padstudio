package com.charan.padstudio.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.charan.padstudio.setup.SetupTile
import com.charan.padstudio.setup.TileTone
import com.charan.padstudio.ui.components.ListRow
import com.charan.padstudio.ui.components.RowLeading
import com.charan.padstudio.ui.components.RowTrailing
import com.charan.padstudio.ui.components.ScreenScaffold
import com.charan.padstudio.ui.components.StudioCard
import com.charan.padstudio.ui.components.SymIconButton
import com.charan.padstudio.ui.components.cardBleed
import com.charan.padstudio.ui.theme.Tokens
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

/**
 * The open-source work PadStudio builds on (README "Licences"). Names between ** are set in the medium weight.
 * Every bundled library and its licence is listed separately, by AboutLibraries ([LicencesDialog]).
 */
private val credits = listOf(
    "**Key Mapper** (keymapperorg, GPL-3.0) runs the pen’s buttons as a separate app, used unmodified; PadStudio writes " +
        "its profiles in Key Mapper’s backup format.",
    "**Lawnchair’s flowerpot** (GPL-3.0-or-later): the app-category rules behind automatic grouping, used unchanged.",
    "**Lawnchair’s icon-pack handling** informed PadStudio’s appfilter.xml reader.",
    "**FocusPen Pro X** (HMQYHM, GPL-3.0-only) documented how HyperOS reports Focus Pen Pro gestures.",
    "**Lawnicons** (Apache-2.0) and **Arcticons** (GPL-3.0 / CC BY-SA 4.0) supply themed icons when installed.",
    "**Material Symbols** (Apache-2.0): PadStudio’s icons.",
    "**Plus Jakarta Sans** (SIL Open Font License 1.1): PadStudio’s typeface.",
)

/**
 * About, as a card: PadStudio's version and licence, the work it builds on, and a row that opens every library's
 * licence through [onOpenLicences] (show [LicencesDialog]). [showTitle] false leaves out the card's own "About"
 * heading, for a page that already has that title ([AboutScreen]).
 */
@Composable
fun AboutCard(onOpenLicences: () -> Unit, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val version = remember(context) { appVersion(context) }
    val name = SpanStyle(color = scheme.onSurface, fontWeight = Tokens.WeightMedium)
    StudioCard(modifier.fillMaxWidth()) {
        if (showTitle) {
            Text("About", style = type.titleLarge, color = scheme.onSurface, modifier = Modifier.semantics { heading() })
        }
        Text(
            if (version.isNullOrBlank()) "PadStudio is free software under GPL-3.0. It builds on:" else "PadStudio $version is free software under GPL-3.0. It builds on:",
            style = type.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        Column(Modifier.padding(top = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            credits.forEach { CreditLine(credit(it, name)) }
        }
        Column(Modifier.cardBleed()) {
            ListRow(
                headline = "Open-source licences",
                supporting = "Each library and its licence.",
                leading = RowLeading.Custom { SetupTile(Sym.code, TileTone.Neutral) },
                trailing = RowTrailing.Chevron,
                onClick = onOpenLicences,
            )
        }
    }
}

/** One credit with a small primary bullet on its first line (decorative). */
@Composable
private fun CreditLine(text: AnnotatedString) {
    val style = MaterialTheme.typography.bodyMedium
    val dot = 4.dp
    val top = with(LocalDensity.current) { if (style.lineHeight.isSp) (style.lineHeight.toDp() - dot) / 2 else 0.dp }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = top.coerceAtLeast(0.dp)).size(dot).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text(text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** [text] with each **name** in [name]'s style. */
private fun credit(text: String, name: SpanStyle): AnnotatedString = buildAnnotatedString {
    text.split("**").forEachIndexed { index, part ->
        if (index % 2 == 1) withStyle(name) { append(part) } else append(part)
    }
}

private fun appVersion(context: Context): String? =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()

/**
 * Every library bundled in PadStudio and its licence (AboutLibraries, collected at build time), in a dialog with
 * a Close button; Back and a tap outside close it too. Tapping a library shows its licence text.
 */
@Composable
fun LicencesDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .fillMaxHeight(),
            shape = RoundedCornerShape(Tokens.CornerXl),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Open-source licences",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    SymIconButton(Sym.close, contentDescription = "Close", onClick = onDismiss)
                }
                val libraries by produceLibraries()
                LibrariesContainer(libraries, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

/** About as a page of its own (round 2: its own rail icon; `--es tab about` opens it). */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    var licencesOpen by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(title = "About", modifier = modifier) {
        AboutCard(onOpenLicences = { licencesOpen = true }, showTitle = false)
    }
    if (licencesOpen) LicencesDialog(onDismiss = { licencesOpen = false })
}
