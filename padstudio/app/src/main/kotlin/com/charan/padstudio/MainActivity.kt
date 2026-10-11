package com.charan.padstudio

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charan.padstudio.home.HomeKitScreen
import com.charan.padstudio.looks.LooksScreen
import com.charan.padstudio.pen.PenStudioScreen
import com.charan.padstudio.setup.SetupScreen
import com.charan.padstudio.ui.AboutScreen
import com.charan.padstudio.ui.PadStudioTheme

private enum class Tab(val label: String, val icon: ImageVector) {
    PEN("Pen", Icons.Default.Edit),
    HOME("Home", Icons.Default.Home),
    LOOKS("Looks", Icons.Default.Star),
    SETUP("Setup", Icons.Default.Build),
    ABOUT("About", Icons.Default.Info),
}

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PadStudioTheme { PadStudioApp(model) } }
        handleDebug(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDebug(intent)
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
     * Debug builds only, e.g. `adb shell am start -n com.charan.padstudio/.MainActivity --es tab home`
     * opens a tab (pen, home, looks, setup, about) for screenshots; `--es open probe` opens the pen probe,
     * `--es open pentest` the pen test;
     * `--es apply keymapper` hands the current pen profile to Key Mapper.
     */
    private fun handleDebug(intent: Intent?) {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable) return
        intent?.getStringExtra("tab")?.let { requestedTab = it }
        // `--es open probe` opens the pen probe (it isn't exported, so adb can't start it directly).
        if (intent?.getStringExtra("open") == "probe") startActivity(Intent(this, com.charan.padstudio.setup.PenProbeActivity::class.java))
        if (intent?.getStringExtra("open") == "pentest") startActivity(Intent(this, com.charan.padstudio.pen.PenTestActivity::class.java))
        // `--es apply keymapper` opens Key Mapper's restore dialog with the current profile.
        if (intent?.getStringExtra("apply") == "keymapper") model.applyWhenReady { startActivity(it) }
    }

    private var requestedTab by mutableStateOf<String?>(null)

    @Composable
    private fun PadStudioApp(model: MainViewModel) {
        var tab by rememberSaveable { mutableStateOf(Tab.PEN) }
        requestedTab?.let { name -> Tab.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { tab = it }; requestedTab = null }
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
        BoxWithConstraints {
            val wide = maxWidth >= 600.dp
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!wide) {
                        NavigationBar {
                            Tab.entries.forEach { item ->
                                NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(item.icon, null) }, label = { Text(item.label) })
                            }
                        }
                    }
                },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (wide) {
                        NavigationRail {
                            Tab.entries.forEach { item ->
                                NavigationRailItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(item.icon, null) }, label = { Text(item.label) })
                            }
                        }
                        VerticalDivider()
                    }
                    val content = Modifier.fillMaxSize()
                    when (tab) {
                        Tab.PEN -> PenStudioScreen(model, start, content)
                        Tab.HOME -> HomeKitScreen(model, content)
                        Tab.LOOKS -> LooksScreen(model, content)
                        Tab.SETUP -> SetupScreen(model, start, content)
                        Tab.ABOUT -> AboutScreen(content)
                    }
                }
            }
        }
    }
}
