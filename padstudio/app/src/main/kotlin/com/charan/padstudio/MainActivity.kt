package com.charan.padstudio

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.charan.padstudio.debug.ColorsDumpEffect
import com.charan.padstudio.debug.DebugDumps
import com.charan.padstudio.debug.DebugOverrides
import com.charan.padstudio.home.HomeKitScreen
import com.charan.padstudio.looks.LooksScreen
import com.charan.padstudio.pen.KeyMapperBridge
import com.charan.padstudio.pen.PenStudioScreen
import com.charan.padstudio.setup.SetupScreen
import com.charan.padstudio.today.TodayScreen
import com.charan.padstudio.ui.PadStudioShell
import com.charan.padstudio.ui.PadStudioTheme
import com.charan.padstudio.ui.ShellDestination
import com.charan.padstudio.ui.ShellStatusLine
import com.charan.padstudio.ui.ShellStatusTone
import kotlinx.coroutines.launch

/**
 * PadStudio's places, in rail order. The names are protected (plan §4): `--es tab <name>` matches them in any case.
 * ABOUT isn't in the rail: it opens Setup, which gets an About section in W3 (see openAboutSection).
 */
internal enum class Tab(val label: String, @param:DrawableRes val icon: Int, @param:DrawableRes val selectedIcon: Int) {
    TODAY("Today", R.drawable.sym_today, R.drawable.sym_today_fill),
    PEN("Pen", R.drawable.sym_pen, R.drawable.sym_pen_fill),
    HOME("Home", R.drawable.sym_home, R.drawable.sym_home_fill),
    LOOKS("Looks", R.drawable.sym_looks, R.drawable.sym_looks_fill),
    SETUP("Setup", R.drawable.sym_setup, R.drawable.sym_setup_fill),
    ABOUT("About", R.drawable.sym_info, R.drawable.sym_info_fill),
    ;

    companion object {
        /** The tab a `--es tab` extra names (today, pen, home, looks, setup or about, in any case); null if none. */
        fun fromExtra(value: String?): Tab? = value?.let { name -> entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
    }
}

/** What the rail and the bottom bar show: every tab but ABOUT, which opens inside Setup. */
private val railDestinations: List<ShellDestination<Tab>> =
    Tab.entries.filter { it != Tab.ABOUT }.map { ShellDestination(it, it.label, it.icon, it.selectedIcon) }

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()

    /** Debug builds: the stored `--es theme|fontscale|boldtext` overrides, as a Configuration override (DebugOverrides). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DebugOverrides.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PadStudioTheme { PadStudioApp(model) } }
        handleDebug(intent, fresh = savedInstanceState == null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A debug appearance change recreates the activity, and the new one reads getIntent(): make that this one.
        setIntent(intent)
        handleDebug(intent, fresh = true)
    }

    /**
     * The pen wizard records the next key event delivered to this window. Overriding is the only way to see
     * keys before Compose consumes them; lint flags the super call into androidx's ComponentActivity.
     */
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = model.onKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun onStop() {
        // Leaving mid-capture must not leave the pen paused in Key Mapper.
        if (!isChangingConfigurations) model.cancelCapture()
        super.onStop()
    }

    /**
     * Debug builds only, e.g. `adb shell am start -n com.charan.padstudio/.MainActivity --es tab home`:
     * - `--es tab today|pen|home|looks|setup|about` opens a tab for screenshots (about opens Setup);
     * - `--es open probe` opens the pen probe, `--es open pentest` the pen test;
     * - `--es apply keymapper` hands the current pen profile to Key Mapper;
     * - `--es widgets update` redraws every placed group widget; `--es dumpicons <group>` writes its icons;
     * - `--es theme dark|light|system`, `--es fontscale <0.5–3>|reset` and `--es boldtext on|off|reset` force
     *   the look until reset (DebugOverrides);
     * - `--es dump profile` writes the JSON Apply would send to files/profile-dump.json, `--es dump colors`
     *   the colour roles on screen to files/colors.json (DebugDumps).
     *
     * [fresh] is false when the activity is re-created (rotation, recreate(), process restore): the appearance
     * extras are only applied from a new request, so an old launch intent can't undo a later change.
     */
    private fun handleDebug(intent: Intent?, fresh: Boolean) {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable) return
        // A changed appearance recreates the activity; the new one handles the rest of this intent.
        if (fresh && intent != null && DebugOverrides.applyExtras(this, intent::getStringExtra)) {
            recreate()
            return
        }
        intent?.getStringExtra("tab")?.let { name ->
            requestedTab = Tab.fromExtra(name)
            if (requestedTab == null) Log.w(DebugDumps.TAG, "tab \"$name\" ignored: use ${Tab.entries.joinToString { it.name.lowercase() }}")
        }
        // `--es open probe` opens the pen probe (it isn't exported, so adb can't start it directly).
        if (intent?.getStringExtra("open") == "probe") startActivity(Intent(this, com.charan.padstudio.setup.PenProbeActivity::class.java))
        if (intent?.getStringExtra("open") == "pentest") startActivity(Intent(this, com.charan.padstudio.pen.PenTestActivity::class.java))
        // `--es apply keymapper` opens Key Mapper's restore dialog with the current profile.
        if (intent?.getStringExtra("apply") == "keymapper") model.applyWhenReady { startActivity(it) }
        // `--es widgets update` redraws every placed group widget (after installing a new build).
        if (intent?.getStringExtra("widgets") == "update") model.updateWidgets()
        // `--es dumpicons games` writes that group's icons as widgets draw them to files/icon-debug/.
        intent?.getStringExtra("dumpicons")?.let { model.dumpIcons(it) }
        when (val dump = intent?.getStringExtra("dump")) {
            null -> Unit
            "profile" -> lifecycleScope.launch { DebugDumps.dumpProfile(applicationContext, model.plan) }
            "colors" -> colorsDumpRequests++
            else -> Log.w(DebugDumps.TAG, "dump \"$dump\" ignored: use profile or colors")
        }
    }

    private var requestedTab by mutableStateOf<Tab?>(null)

    /** Counts `--es dump colors` requests; the composition writes the colours it is drawing with (ColorsDumpEffect). */
    private var colorsDumpRequests by mutableIntStateOf(0)

    @Composable
    private fun PadStudioApp(model: MainViewModel) {
        var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
        requestedTab?.let { tab = it; requestedTab = null }
        val snackbar = remember { SnackbarHostState() }
        val message by model.message.collectAsStateWithLifecycle()
        LaunchedEffect(message) {
            message?.let {
                snackbar.showSnackbar(it)
                model.clearMessage()
            }
        }
        // Come back from Settings or Key Mapper with fresh checks.
        LifecycleResumeEffect(Unit) {
            model.refresh()
            onPauseOrDispose { }
        }
        val start: (Intent) -> Unit = { intent ->
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                model.refresh()
            }
        }
        ColorsDumpEffect(colorsDumpRequests)
        // TODO(W3 Setup): pass `scrollToAbout = openAboutSection` to SetupScreen once it has its About section.
        val openAboutSection: Boolean = tab == Tab.ABOUT
        // Each tab keeps its own saved state (scroll position, open pickers) while another is showing.
        val tabStates = rememberSaveableStateHolder()
        PadStudioShell(
            destinations = railDestinations,
            selected = if (openAboutSection) Tab.SETUP else tab,
            onSelect = { tab = it },
            onSettings = { tab = Tab.SETUP },
            statusLine = { KeyMapperStatusLine() },
            snackbarHost = { SnackbarHost(snackbar) },
        ) {
            tabStates.SaveableStateProvider(tab.name) {
                val content = Modifier.fillMaxSize()
                when (tab) {
                    Tab.TODAY -> TodayScreen(model, start, onOpenTab = { tab = it }, modifier = content)
                    Tab.PEN -> PenStudioScreen(model, start, content)
                    Tab.HOME -> HomeKitScreen(model, content)
                    Tab.LOOKS -> LooksScreen(model, content)
                    Tab.SETUP, Tab.ABOUT -> SetupScreen(model, start, content)
                }
            }
        }
    }
}

/**
 * The rail's status line until the plan §7 status model drives it: whether Key Mapper's accessibility service is
 * on (read like the Pen screen's Key Mapper card, again on every resume). It never claims Expert mode is running.
 */
@Composable
private fun KeyMapperStatusLine() {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(KeyMapperBridge.isAccessibilityEnabled(context)) }
    LifecycleResumeEffect(context) {
        listening = KeyMapperBridge.isAccessibilityEnabled(context)
        onPauseOrDispose { }
    }
    if (listening) {
        ShellStatusLine("Key Mapper is listening", ShellStatusTone.Good)
    } else {
        ShellStatusLine("Key Mapper is off", ShellStatusTone.Attention)
    }
}
