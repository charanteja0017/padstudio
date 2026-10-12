/**
 * PadStudio 2 component library (redesign plan Step 3, task C). This header is the contract: screens use
 * only the components listed here, plus Material 3 1.4.0's public, non-Expressive APIs directly (dialogs,
 * sheets, snackbars, text fields, HorizontalDivider). Visual spec: docs/redesign/reference-today.png,
 * docs/redesign/kit.css and docs/redesign/mock-*.html; previews of every component are in
 * ComponentPreviews.kt.
 *
 * Rules every component keeps
 * - Colours come from MaterialTheme.colorScheme. The one exception is status (Material 3 has no good or
 *   warn role): statusInk() in Status.kt gives the good/warn inks from a private fallback equal to W2
 *   task A's PadStudioTheme.semantic (switch statusInk() to that after the merge); pill tints use the
 *   bases Tokens.Good and Tokens.Warn at 16 %; bad is colorScheme.error.
 * - Text comes from MaterialTheme.typography: page title displaySmall (medium weight), card title
 *   titleLarge, body bodyLarge, supporting text bodyMedium, labels and buttons labelLarge, small meta
 *   bodySmall, numbers headlineMedium with tabular figures. Weights are 400 and 500 only (Bold text adds
 *   300 on the tablet). Text wraps instead of clipping at 200 %.
 * - Corners and spacing come from Tokens (ui/theme/Tokens.kt).
 * - Every touch target is at least 48 x 48 dp.
 * - Keyboard focus shows the kit's ring: 3 dp in colorScheme.secondary, 2 dp outside buttons, chips,
 *   binding chips and clickable cards (1 dp round icon buttons and swatches), just inside list rows and
 *   the Disclosure header (cards clip them); a focused segment's edge turns into it. Material 3 1.4.0
 *   alone shows only a faint state layer. Touch never moves focus, so the ring never shows under a
 *   finger or the pen.
 * - Shapes are set explicitly from Tokens, never left to Material's slots: cards 24 dp (Material's Card
 *   would take shapes.medium, 16 dp), buttons 12 dp, chips 8 dp (Material's chips take shapes.small,
 *   12 dp), binding chips 12 dp (the kit's `.binding`), dialogs 28 dp.
 * - Filled symbols mean selected, but 23 of the 66 symbols look the same filled and outlined (Home,
 *   Setup, chevrons, arrows, Close, Add, ...), so selection is always also shown by a container or
 *   colour change and by semantics. Check Sym.fillLooksDifferent(id).
 * - No @OptIn is needed: SingleChoiceSegmentedButtonRow and SegmentedButton are stable in Material 3
 *   1.4.0 (compiled without the opt-in; a known experimental API fails the same build).
 * - Every `id`/`icon` Int parameter is a @DrawableRes symbol id from Sym.
 *
 * Symbols: ui/Symbols.kt, package com.charan.padstudio.ui
 * ```
 * object Sym {
 *     val <slot>: Int          // outline, one per map.json slot in camelCase: today, pen, home, looks, setup,
 *                              // shutter, openCamera, ..., namesHidden, testPen, hyperos, fitWarn (66)
 *     val <slot>Fill: Int      // its filled twin: todayFill, ..., fitWarnFill (66)
 *     val slots: Map<String, Int>                       // "open_camera" -> openCamera, map.json order
 *     fun filled(id: Int): Int
 *     fun outline(id: Int): Int
 *     fun filledIf(id: Int, selected: Boolean): Int
 *     fun fillLooksDifferent(id: Int): Boolean
 *     fun forSlot(slot: String): Int?
 *     fun forIntent(intent: PenIntent): Int             // all 25 pen actions
 *     fun forControl(controlId: String): Int            // PenControl.id: pinch, slide_up, slide_down, ...
 * }
 * ```
 *
 * Page and layout
 * ```
 * @Composable fun ScreenScaffold(title: String, modifier: Modifier = Modifier, subtitle: String? = null,
 *     scrollState: ScrollState? = rememberScrollState(), actions: @Composable RowScope.() -> Unit = {},
 *     content: @Composable ColumnScope.() -> Unit)
 * object ScreenDefaults { val MaxContentWidth: Dp; val Gutter: Dp; val TopPadding: Dp; val BottomPadding: Dp;
 *     val HeaderGap: Dp; val MinTitleWidth: Dp }    // 1040, 24, 12, 24, 20 and 280 dp
 * @Composable fun CardGrid(modifier: Modifier = Modifier, minColumnWidth: Dp = 320.dp, maxColumns: Int = 3,
 *     spacing: Dp = Tokens.GapM, content: @Composable CardGridScope.() -> Unit)
 * interface CardGridScope { fun Modifier.span(columns: Int): Modifier }
 * @Composable fun CardColumns(modifier: Modifier = Modifier, startWeight: Float = 3f, endWeight: Float = 2f,
 *     endWidth: Dp = Dp.Unspecified, stackBelow: Dp = 720.dp, spacing: Dp = Tokens.GapM,
 *     start: @Composable ColumnScope.() -> Unit, end: @Composable ColumnScope.() -> Unit)
 * ```
 *
 * Cards and sections
 * ```
 * enum class CardTone { Low, High, Hero, Tonal, Outlined }
 * @Composable fun StudioCard(modifier: Modifier = Modifier, tone: CardTone = CardTone.Low,
 *     onClick: (() -> Unit)? = null, contentPadding: PaddingValues = PaddingValues(Tokens.CardPadding),
 *     spacing: Dp = Tokens.GapS, content: @Composable ColumnScope.() -> Unit)
 * @Composable fun SectionHeader(title: String, modifier: Modifier = Modifier, supporting: String? = null,
 *     overline: String? = null, accent: Boolean = false, actionLabel: String? = null,
 *     onAction: (() -> Unit)? = null)
 * fun Modifier.cardBleed(horizontal: Dp = 16.dp): Modifier
 * ```
 *
 * Lists
 * ```
 * sealed interface RowLeading {
 *     data class Symbol(val id: Int, val tonal: Boolean = false); data class App(val app: InstalledApp)
 *     class Custom(val content: @Composable () -> Unit) }
 * sealed interface RowTrailing {
 *     data object None; data object Chevron; data class Symbol(val id: Int); data class Value(val text: String)
 *     data class Toggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit)
 *     class Custom(val content: @Composable () -> Unit) }
 * @Composable fun ListRow(headline: String, modifier: Modifier = Modifier, supporting: String? = null,
 *     leading: RowLeading? = null, trailing: RowTrailing = RowTrailing.None, onClick: (() -> Unit)? = null,
 *     selected: Boolean? = null, enabled: Boolean = true)
 * @Composable fun Disclosure(modifier: Modifier = Modifier, title: String = "Developer details",
 *     icon: Int? = Sym.code, initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit)
 * @Composable fun Disclosure(expanded: Boolean, onExpandedChange: (Boolean) -> Unit,
 *     modifier: Modifier = Modifier, title: String = "Developer details", icon: Int? = Sym.code,
 *     content: @Composable ColumnScope.() -> Unit)
 * ```
 *
 * Actions and choices
 * ```
 * enum class PillStyle { Filled, Tonal, Outlined, Text }
 * object PillButtonDefaults { val Shape: Shape; val FullPill: Shape }    // 12 dp corners (the reference); a pill
 * @Composable fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
 *     style: PillStyle = PillStyle.Filled, icon: Int? = null, trailingIcon: Int? = null,
 *     enabled: Boolean = true, shape: Shape = PillButtonDefaults.Shape)
 * @Composable fun SymIconButton(icon: Int, contentDescription: String, onClick: () -> Unit,
 *     modifier: Modifier = Modifier, style: PillStyle = PillStyle.Text, enabled: Boolean = true)
 * @Composable fun <T> SegmentedChoice(options: List<T>, selected: T, onSelect: (T) -> Unit,
 *     label: (T) -> String, modifier: Modifier = Modifier, enabled: Boolean = true,
 *     icon: ((T) -> Int?)? = null)
 * @Composable fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit,
 *     modifier: Modifier = Modifier, icon: Int? = null, enabled: Boolean = true)
 * @Composable fun ActionChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
 *     icon: Int? = null, enabled: Boolean = true)
 * @Composable fun BindingChip(onClick: () -> Unit, modifier: Modifier = Modifier, action: String? = null,
 *     icon: Int? = null, control: String? = null, enabled: Boolean = true, addLabel: String = "Add action")
 * data class Swatch(val label: String, val color: Color, val contentColor: Color)
 * @Composable fun SwatchRow(swatches: List<Swatch>, selectedIndex: Int, onSelect: (Int) -> Unit,
 *     modifier: Modifier = Modifier, enabled: Boolean = true,
 *     horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp))
 * @Composable fun ConfirmDialog(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit,
 *     onDismiss: () -> Unit, modifier: Modifier = Modifier, dismissLabel: String = "Cancel",
 *     icon: Int? = null)                                   // body in bodyLarge, as the preview's dialog
 * ```
 *
 * Status
 * ```
 * enum class StatusLevel { Good, Warn, Bad, Unknown }
 * val StatusLevel.symbol: Int                               // ok, warning, error, help
 * @Composable fun statusInk(level: StatusLevel): Color       // the level's symbol colour, e.g. a status dot
 * @Composable fun StatusPill(text: String, level: StatusLevel, modifier: Modifier = Modifier,
 *     icon: Int = level.symbol)
 * @Composable fun ProgressRing(progress: Float, value: String, modifier: Modifier = Modifier,
 *     size: Dp = 112.dp, strokeWidth: Dp = 6.dp, contentDescription: String? = null)
 * @Composable fun EmptyState(title: String, modifier: Modifier = Modifier, icon: Int? = null,
 *     body: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null)
 * ```
 *
 * Apps and icons
 * ```
 * data class IconAppearance(val style: IconStyle, val pack: String?)
 * @Composable fun AppIcon(app: InstalledApp, modifier: Modifier = Modifier, size: Dp = 48.dp,
 *     contentDescription: String? = app.facts.label, appearance: IconAppearance? = null)
 * @Composable fun AppIconStack(apps: List<InstalledApp>, modifier: Modifier = Modifier, size: Dp = 40.dp,
 *     max: Int = 3, ringColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
 *     contentDescription: String? = null, appearance: IconAppearance? = null)
 * @Composable fun SymIcon(id: Int, contentDescription: String?, modifier: Modifier = Modifier,
 *     filled: Boolean = false, size: Dp = 24.dp, tint: Color = LocalContentColor.current,
 *     autoMirror: Boolean = false)
 * class SuperellipseShape(exponent: Float = 4.4f) : Shape
 * ```
 * The older com.charan.padstudio.home.AppIcon(app, sizeDp) still exists for AppGrid; where both are
 * visible, import this package's AppIcon explicitly.
 *
 * A page like the reference's Today:
 * ```
 * ScreenScaffold(
 *     title = "Today",
 *     subtitle = "Your tablet, set up your way.",
 *     actions = { PillButton("Open library", onClick = open, style = PillStyle.Outlined, icon = Sym.library) },
 * ) {
 *     CardColumns(
 *         start = { StudioCard { SectionHeader("Focus Pen Pro", overline = "Your pen") } },
 *         end = { StudioCard { SectionHeader("Home screen") } },
 *     )
 * }
 * ```
 */
package com.charan.padstudio.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.charan.padstudio.ui.Sym
import com.charan.padstudio.ui.theme.Tokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Page measurements shared by [ScreenScaffold] and anything laid out like it (plan §10). */
object ScreenDefaults {
    /** Content is at most this wide (plan §10). */
    val MaxContentWidth: Dp = 1040.dp

    /** Space at the left and right of a page. */
    val Gutter: Dp = Tokens.Gutter

    /** Above the title, under the shell's status-bar inset (the reference sets the title close to the top). */
    val TopPadding: Dp = 12.dp

    /** Below the last card. */
    val BottomPadding: Dp = 24.dp

    /** Between the title block and the first card. */
    val HeaderGap: Dp = 20.dp

    /** The title keeps at least this width beside the actions; narrower, the actions wrap under it. */
    val MinTitleWidth: Dp = 280.dp
}

/**
 * A Material Symbol from [Sym], [size] dp square, tinted [tint] (the content colour by default). [filled]
 * draws the filled twin, which means selected or active; see [Sym.fillLooksDifferent] before relying on it.
 * [autoMirror] flips it in right-to-left layouts (chevrons and arrows that point "forward").
 */
@Composable
fun SymIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    autoMirror: Boolean = false,
) {
    val mirrored = autoMirror && LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        painter = painterResource(if (filled) Sym.filled(id) else id),
        contentDescription = contentDescription,
        modifier = modifier.size(size).then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
        tint = tint,
    )
}

/**
 * The HyperOS app-icon outline: the superellipse |x|^n + |y|^n = 1, n = 4.4 as fitted to the launcher's
 * mask (plan §9; the kit's `--kit-squircle`). Used for app tiles that PadStudio draws itself (a missing
 * icon's initial, the "+N" tile), so they match the real icons beside them.
 */
@Immutable
class SuperellipseShape(private val exponent: Float = 4.4f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val power = 2.0 / exponent
        val rx = size.width / 2.0
        val ry = size.height / 2.0
        val path = Path()
        for (i in 0 until POINTS) {
            val t = 2.0 * PI * i / POINTS
            val c = cos(t)
            val s = sin(t)
            val x = (rx + rx * sign(c) * abs(c).pow(power)).toFloat()
            val y = (ry + ry * sign(s) * abs(s).pow(power)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is SuperellipseShape && other.exponent == exponent

    override fun hashCode(): Int = exponent.hashCode()

    private companion object {
        const val POINTS = 128
    }
}

/**
 * Makes a child of a [StudioCard] full width plus [horizontal] on each side, reaching into the card's
 * 20 dp padding like the kit's `.card > .list`. A [ListRow] (or a Column of them) with this modifier keeps
 * its text in line with the card's other content, while its pressed and selected background runs nearly
 * edge to edge.
 */
fun Modifier.cardBleed(horizontal: Dp = 16.dp): Modifier = this.layout { measurable, constraints ->
    val bleed = horizontal.roundToPx()
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
    val wide = constraints.maxWidth + 2 * bleed
    val placeable = measurable.measure(
        Constraints(minWidth = wide, maxWidth = wide, minHeight = constraints.minHeight, maxHeight = constraints.maxHeight),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(-bleed, 0) }
}
