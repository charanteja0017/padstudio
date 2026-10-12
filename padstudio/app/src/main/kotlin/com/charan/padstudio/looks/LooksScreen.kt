package com.charan.padstudio.looks

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charan.padstudio.MainViewModel
import com.charan.padstudio.data.IconStyle
import com.charan.padstudio.icons.IconPack
import com.charan.padstudio.looks.screen.ApplyOutcome
import com.charan.padstudio.looks.screen.IconFacts
import com.charan.padstudio.looks.screen.IconStyleCard
import com.charan.padstudio.looks.screen.LooksPanes
import com.charan.padstudio.looks.screen.MatchColoursCard
import com.charan.padstudio.looks.screen.WallpaperCard
import com.charan.padstudio.looks.screen.WallpaperResult
import com.charan.padstudio.looks.screen.dynamicColourSupported
import com.charan.padstudio.looks.screen.loadIconFacts
import com.charan.padstudio.looks.screen.paletteLabel
import com.charan.padstudio.looks.screen.previewApps
import com.charan.padstudio.looks.screen.wallpaperResultOf
import com.charan.padstudio.looks.screen.wallpaperSetText
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.components.ScreenScaffold
import com.charan.padstudio.ui.theme.Tokens
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Looks (plan §10, docs/redesign/mock-looks.html): the icon style of PadStudio's widgets and panels, previewed on
 * 12 of the user's apps; the icon-pack picker; "Match wallpaper colours"; and the generated wallpaper. At 840 dp
 * and wider the icon cards and the wallpaper card sit side by side; narrower, in one column.
 *
 * Every action calls the same MainViewModel function with the same arguments as before the redesign:
 * - a style: `setIconStyle(style)`, and a pack: `setIconStyle(IconStyle.ICON_PACK, pack)`;
 * - the installed packs: `iconPacks()`, read again on every resume, so a pack installed meanwhile shows up;
 * - the switch: `setMatchWallpaperColours(on)`;
 * - Apply: `applyWallpaper(seed, dark, accent)`, with seed 7 and +1 per Shuffle, Light or Dark, and the theme's
 *   primary colour as the accent. It sets the home and lock screen together, as it always has.
 *
 * applyWallpaper reports only through `MainViewModel.message`. While an Apply from this screen waits for that
 * message, the screen takes it from the shell's snackbar (`clearMessage`) and says it itself: success as a
 * snackbar with the time, failure in the card with a plain reason and Retry. If the screen is left before the
 * answer comes, the shell shows the message as before.
 */
@Composable
fun LooksScreen(model: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val config by model.config.collectAsStateWithLifecycle()
    val snapshot by model.snapshot.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Packs, the wallpaper policy and HyperOS Themes can change while PadStudio is in the background.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val packs by produceState<List<IconPack>?>(null, resumes) {
        if (resumes > 0) value = withContext(Dispatchers.Default) { model.iconPacks() }
    }
    val wallpaperAllowed by produceState(true, resumes) {
        if (resumes > 0) value = withContext(Dispatchers.Default) { canSetWallpaper(context) }
    }
    val themesIntent by produceState<Intent?>(null, resumes) {
        if (resumes > 0) value = withContext(Dispatchers.Default) { context.packageManager.getLaunchIntentForPackage(HYPEROS_THEMES) }
    }

    val apps = remember(snapshot) {
        snapshot?.let { home -> previewApps(home.groups, exclude = setOf(context.packageName)).mapNotNull { home.app(it) } }
    }
    val iconFacts by produceState<IconFacts?>(null, apps, packs, config.iconPack) {
        val shown = apps
        val installed = packs
        if (shown != null && installed != null) {
            value = withContext(Dispatchers.Default) { loadIconFacts(context, shown, installed, config.iconPack) }
        }
    }

    var seed by rememberSaveable { mutableLongStateOf(7L) }
    var dark by rememberSaveable { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    var applying by remember { mutableStateOf(false) }
    var outcome by rememberSaveable { mutableStateOf(ApplyOutcome.None) }
    var failureDetail by rememberSaveable { mutableStateOf<String?>(null) }
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }

    val apply: () -> Unit = {
        if (!applying) {
            // A wallpaper message still showing from an earlier Apply would be read as this one's answer.
            if (wallpaperResultOf(model.message.value) != null) model.clearMessage()
            applying = true
            outcome = ApplyOutcome.None
            failureDetail = null
            model.applyWallpaper(seed, dark, accent)
            scope.launch {
                val result = withTimeoutOrNull(APPLY_WAIT_MS) { model.message.mapNotNull(::wallpaperResultOf).first() }
                applying = false
                when (result) {
                    null -> outcome = ApplyOutcome.Requested
                    WallpaperResult.Set -> {
                        model.clearMessage()
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(wallpaperSetText(timeFormat.format(Date())))
                    }
                    is WallpaperResult.Failed -> {
                        model.clearMessage()
                        failureDetail = result.detail
                        outcome = ApplyOutcome.Failed
                    }
                }
            }
        }
    }
    val openThemes: (() -> Unit)? = themesIntent?.let { intent ->
        {
            if (runCatching { context.startActivity(intent) }.isFailure) {
                scope.launch { snackbar.showSnackbar("Couldn't open HyperOS Themes.") }
            }
        }
    }

    val palette = paletteLabel(Tokens.DefaultPalette)
    BoxWithConstraints(modifier) {
        val sideBySide = maxWidth >= TWO_PANE_MIN_WIDTH
        ScreenScaffold(
            title = "Looks",
            subtitle = "Colours, wallpaper and icons for PadStudio's widgets.",
            actions = {
                if (openThemes != null) {
                    PillButton(text = "HyperOS Themes", onClick = openThemes, style = PillStyle.Outlined, icon = Sym.open)
                }
            },
        ) {
            LooksPanes(
                sideBySide = sideBySide,
                start = {
                    Column(verticalArrangement = Arrangement.spacedBy(Tokens.GapM)) {
                        IconStyleCard(
                            style = config.iconStyle,
                            savedPack = config.iconPack,
                            apps = apps,
                            packs = packs,
                            facts = iconFacts,
                            onSelectStyle = { style -> model.setIconStyle(style) },
                            onSelectPack = { pack -> model.setIconStyle(IconStyle.ICON_PACK, pack) },
                        )
                        MatchColoursCard(
                            checked = config.matchWallpaperColours,
                            supported = dynamicColourSupported(Build.VERSION.SDK_INT),
                            palette = palette,
                            onCheckedChange = { on -> model.setMatchWallpaperColours(on) },
                        )
                    }
                },
                end = {
                    WallpaperCard(
                        seed = seed,
                        dark = dark,
                        accent = accent,
                        palette = if (config.matchWallpaperColours) null else palette,
                        allowed = wallpaperAllowed,
                        applying = applying,
                        outcome = outcome,
                        failureDetail = failureDetail,
                        wallpaperApplied = config.wallpaperApplied,
                        onShuffle = {
                            seed += 1
                            outcome = ApplyOutcome.None
                        },
                        onDarkChange = {
                            dark = it
                            outcome = ApplyOutcome.None
                        },
                        onApply = apply,
                    )
                },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** Whether apps may set the wallpaper here: false when the system has none or a device policy forbids it. */
private fun canSetWallpaper(context: Context): Boolean = runCatching {
    val manager = WallpaperManager.getInstance(context)
    manager.isWallpaperSupported && manager.isSetWallpaperAllowed
}.getOrDefault(true)

/** The screen's own width (beside the rail) from which the icon cards and the wallpaper card sit side by side (brief rule 6). */
private val TWO_PANE_MIN_WIDTH = 840.dp

/** How long Apply waits for MainViewModel's answer before it shows "Requested". Drawing and setting take seconds. */
private const val APPLY_WAIT_MS = 30_000L

/** The HyperOS Themes app, for home-screen icons; the button shows only when it resolves on this tablet. */
private const val HYPEROS_THEMES = "com.android.thememanager"
