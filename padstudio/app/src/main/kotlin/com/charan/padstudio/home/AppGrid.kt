package com.charan.padstudio.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.charan.padstudio.core.home.AppGroup
import com.charan.padstudio.core.home.Density
import com.charan.padstudio.data.InstalledApp
import com.charan.padstudio.data.UsageRanking
import com.charan.padstudio.studio
import com.charan.padstudio.ui.components.ListRow
import com.charan.padstudio.ui.components.PillButton
import com.charan.padstudio.ui.components.PillStyle
import com.charan.padstudio.ui.theme.Tokens
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.charan.padstudio.ui.components.AppIcon as StudioAppIcon

/**
 * The dense app grid shared by the folder panel and the library. Tap opens the app; long-press
 * offers App info, Move to group, Hide and Uninstall. Results and failures show as a short toast; the
 * panels use [PanelAppGrid], which reports them in the panel's own snackbar instead.
 */
@Composable
fun AppGrid(apps: List<InstalledApp>, density: Density, onLaunched: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    PanelAppGrid(
        apps = apps,
        density = density,
        onLaunched = onLaunched,
        onMessage = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
        modifier = modifier,
    )
}

/**
 * [AppGrid] as the panels draw it (plan §10 Panels; docs/redesign/mock-panel.html): cells at least the
 * [density]'s cell wide, each a 48 dp+ target with the icon at the density's size and, when the density
 * shows them, the name under it. [whereOf] names the app's place in the long-press menu ("In More");
 * [isHidden] greys out Hide for an app that is already hidden. [onMessage] gets the plain result of an
 * action ("WiFiBar moved to Tools · 14:05") or why it failed.
 */
@Composable
internal fun PanelAppGrid(
    apps: List<InstalledApp>,
    density: Density,
    onLaunched: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    whereOf: (InstalledApp) -> String? = { null },
    isHidden: (InstalledApp) -> Boolean = { false },
    state: LazyGridState = rememberLazyGridState(),
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(density.cellDp.dp),
        modifier = modifier,
        state = state,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(apps, key = { it.component.flattenToShortString() }) { app ->
            AppTile(app, density, whereOf(app), isHidden(app), onLaunched, onMessage)
        }
    }
}

@Composable
private fun AppTile(
    app: InstalledApp,
    density: Density,
    where: String?,
    hidden: Boolean,
    onLaunched: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val label = app.facts.label
    // Saved, so an open menu or move dialog survives rotation (plan §13 "Editing and saving").
    var menu by rememberSaveable { mutableStateOf(false) }
    var moving by rememberSaveable { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Tokens.CornerM)
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Tokens.TouchTarget)
                .clip(shape)
                // The held app stays marked while its menu is open (the mock's .is-held).
                .then(if (menu) Modifier.background(scheme.secondaryContainer) else Modifier)
                // Keyboard focus: the kit's 3 dp secondary ring, inside the tile. Touch never moves focus.
                .then(if (focused) Modifier.border(FOCUS_RING, scheme.secondary, shape) else Modifier)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    onClickLabel = "Open",
                    role = Role.Button,
                    onLongClickLabel = "Show options",
                    onLongClick = { menu = true },
                    onClick = {
                        val failure = context.startOrReason(app.launchIntent())
                        if (failure == null) {
                            // Most used counts this launch while Usage access is off.
                            UsageRanking.recordLaunch(context, app.component.packageName)
                            onLaunched()
                        } else {
                            onMessage("Couldn't open $label. $failure")
                        }
                    },
                )
                .padding(horizontal = 4.dp, vertical = if (density.showLabels) 8.dp else 4.dp),
        ) {
            // With its name under it the icon needs no description of its own; without, it carries the name.
            StudioAppIcon(
                app = app,
                size = density.iconDp.dp,
                contentDescription = if (density.showLabels) null else label,
            )
            if (density.showLabels) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (menu) scheme.onSecondaryContainer else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        AppMenu(
            app = app,
            where = where,
            hidden = hidden,
            expanded = menu,
            onDismiss = { menu = false },
            onAppInfo = {
                menu = false
                context.startOrReason(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.component.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )?.let { onMessage("Couldn't open app info for $label. $it") }
            },
            onMove = {
                menu = false
                moving = true
            },
            onHide = {
                menu = false
                moveApp(context, app, AppGroup.HIDDEN)
                onMessage("$label hidden from every widget · ${timeNow(context)}")
            },
            onUninstall = {
                menu = false
                context.startOrReason(
                    Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.component.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )?.let { onMessage("Couldn't start uninstalling $label. $it") }
            },
        )
    }
    if (moving) {
        MoveDialog(label, onDismiss = { moving = false }) { group ->
            moving = false
            moveApp(context, app, group)
            onMessage("$label moved to ${group.title} · ${timeNow(context)}")
        }
    }
}

/** The long-press menu, anchored under the held app: who it is and where, then its actions (the mock's .m-panel-menu). */
@Composable
private fun AppMenu(
    app: InstalledApp,
    where: String?,
    hidden: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAppInfo: () -> Unit,
    onMove: () -> Unit,
    onHide: () -> Unit,
    onUninstall: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = MENU_MIN_WIDTH),
        shape = RoundedCornerShape(Tokens.CornerM),
        containerColor = scheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)
                .semantics(mergeDescendants = true) { heading() },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StudioAppIcon(app = app, size = 36.dp, contentDescription = null)
            Column {
                Text(text = app.facts.label, style = type.titleMedium, color = scheme.onSurface)
                if (where != null) Text(text = where, style = type.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = scheme.outlineVariant)
        MenuAction("App info", onClick = onAppInfo)
        MenuAction("Move to group…", onClick = onMove)
        MenuAction("Hide", hint = if (hidden) "Already hidden" else "Out of every widget", enabled = !hidden, onClick = onHide)
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = scheme.outlineVariant)
        MenuAction("Uninstall", hint = "Asks first", danger = true, onClick = onUninstall)
    }
}

@Composable
private fun MenuAction(title: String, onClick: () -> Unit, hint: String? = null, danger: Boolean = false, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val ink = when {
        !enabled -> scheme.onSurface.copy(alpha = DISABLED_ALPHA)
        danger -> scheme.error
        else -> scheme.onSurface
    }
    DropdownMenuItem(
        text = {
            Column {
                Text(text = title, style = type.bodyLarge, color = ink)
                if (hint != null) Text(text = hint, style = type.bodyMedium, color = scheme.onSurfaceVariant)
            }
        },
        onClick = onClick,
        enabled = enabled,
    )
}

/** Every icon in [sizeDp] dp, in the user's icon style; a missing icon shows the app's initial instead. */
@Composable
fun AppIcon(app: InstalledApp, sizeDp: Int) {
    StudioAppIcon(app = app, size = sizeDp.dp)
}

/**
 * Asks which group [label] should move to: every group, Hidden last ("Out of every widget"). Tapping one
 * calls [onMove]; Cancel, Back or a tap outside calls [onDismiss].
 */
@Composable
fun MoveDialog(label: String, onDismiss: () -> Unit, onMove: (AppGroup) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { PillButton(text = "Cancel", onClick = onDismiss, style = PillStyle.Text) },
        title = { Text("Move $label to…") },
        text = {
            LazyColumn {
                items(AppGroup.entries) { group ->
                    ListRow(
                        headline = group.title,
                        supporting = if (group == AppGroup.HIDDEN) "Out of every widget" else null,
                        onClick = { onMove(group) },
                    )
                }
            }
        },
        shape = RoundedCornerShape(Tokens.CornerXl),
    )
}

/** Saves the user's choice, re-sorts, and redraws every group widget. */
fun moveApp(context: Context, app: InstalledApp, group: AppGroup) {
    val studio = context.studio
    studio.config.update { it.copy(overrides = it.overrides + (app.component.packageName to group)) }
    CoroutineScope(Dispatchers.Default).launch {
        studio.apps.snapshot(force = true)
        GroupWidget().updateAll(context.applicationContext)
    }
}

/** Starts [intent]; null when it started, otherwise why not, in plain words. */
internal fun Context.startOrReason(intent: Intent): String? = try {
    startActivity(intent)
    null
} catch (_: ActivityNotFoundException) {
    "It may have been uninstalled."
} catch (_: SecurityException) {
    "Android didn't allow it."
} catch (e: RuntimeException) {
    e.message ?: "Android gave no reason."
}

/** The time now as the tablet shows it ("14:05", or "2:05 PM" on a 12-hour clock). */
internal fun timeNow(context: Context): String = DateFormat.getTimeFormat(context).format(Date())

private val FOCUS_RING = 3.dp
private val MENU_MIN_WIDTH = 240.dp
private const val DISABLED_ALPHA = 0.38f
