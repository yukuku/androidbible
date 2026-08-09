package yuku.alkitab.base.compose

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import yuku.alkitab.base.util.BookColorUtil

/**
 * The app's brand colors, mirroring the XML theme resources so the Compose
 * surfaces read as part of the same app as the View-based screens:
 *
 *  - [Accent] = `@color/accent` (Light Blue A200), the interactive color of
 *    every View control via `colorAccent`: tab indicators, checkboxes,
 *    links, dialog buttons.
 *  - [ChromeBlueGray] / [ChromeBlueGrayDark] = `@color/primary` /
 *    `@color/primary_dark` (Blue Gray 700/900) — the toolbar and status bar,
 *    the most visible identity color of the View screens.
 *  - [Escape] = `@color/escape` (Teal 300), used sparingly on the View side
 *    for occasional "something interesting" texts.
 *  - [WindowGray] = `@color/window` (Gray 800), the window background.
 */
private object BrandColors {
    val Accent = Color(0xFF40C4FF)
    val ChromeBlueGray = Color(0xFF455A64)
    val ChromeBlueGrayDark = Color(0xFF263238)
    val Escape = Color(0xFF4DB6AC)
    val WindowGray = Color(0xFF303030)
}

/**
 * Static dark color scheme built from [BrandColors]. Deliberately NOT the
 * Material dynamic scheme: dynamic colors come from the device wallpaper, so
 * the same screen would be purple on one phone and green on another while the
 * XML screens around it stay blue-gray/light-blue — the palette must be the
 * app's own on every device.
 *
 * Role mapping, weighted the way the View screens actually use the colors:
 *  - `primary` carries the light-blue accent, taking the role `colorAccent`
 *    plays for View widgets (buttons, tab indicators, checkboxes, links).
 *  - The `secondary` family carries the blue-gray chrome:
 *    `secondaryContainer` is exactly the XML toolbar color, so the M3
 *    components that lean on it (tonal buttons, selected chips and segmented
 *    buttons) and the Compose top bars pick up the same blue-gray the View
 *    toolbars wear, and `onSecondary` is exactly the XML status bar color.
 *  - The `tertiary` family carries the teal escape color — deliberately the
 *    least-used slot, because escape is rare on the View side too.
 *  - The neutral surface ladder is anchored on the pure-gray XML window
 *    background: `surface` equals `@color/window`, containers step up from it
 *    only slightly so elevated Compose surfaces stay in the same gray family
 *    as the View screens they sit against.
 *  - `surfaceTint` is transparent so tonal elevation never washes surfaces
 *    with blue; container roles alone express elevation, keeping grays pure
 *    like the XML side.
 *
 * Error roles keep the Material defaults from [darkColorScheme].
 */
val BibleAppDarkColorScheme: ColorScheme = darkColorScheme(
    primary = BrandColors.Accent,
    onPrimary = Color(0xFF00344A),
    primaryContainer = Color(0xFF004C6A),
    onPrimaryContainer = Color(0xFFC3E7FF),
    inversePrimary = Color(0xFF006590),

    secondary = Color(0xFFB0BEC5),
    onSecondary = BrandColors.ChromeBlueGrayDark,
    secondaryContainer = BrandColors.ChromeBlueGray,
    onSecondaryContainer = Color(0xFFECEFF1),

    tertiary = BrandColors.Escape,
    onTertiary = Color(0xFF003732),
    tertiaryContainer = Color(0xFF00504A),
    onTertiaryContainer = Color(0xFFB2DFDB),

    background = BrandColors.WindowGray,
    onBackground = Color(0xFFEDEDED),
    surface = BrandColors.WindowGray,
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF454545),
    onSurfaceVariant = Color(0xFFC2C2C2),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFE6E6E6),
    inverseOnSurface = Color(0xFF2E2E2E),

    surfaceDim = Color(0xFF2A2A2A),
    surfaceBright = Color(0xFF4A4A4A),
    surfaceContainerLowest = Color(0xFF252525),
    surfaceContainerLow = Color(0xFF333333),
    surfaceContainer = Color(0xFF363636),
    surfaceContainerHigh = Color(0xFF3A3A3A),
    surfaceContainerHighest = Color(0xFF424242),

    outline = Color(0xFF8C8C8C),
    outlineVariant = Color(0xFF515151),
)

/**
 * Theme for the app's Compose surfaces, applying [BibleAppDarkColorScheme].
 *
 * The scheme is dark whatever the system theme is, because the XML screens
 * these surfaces sit among are dark unconditionally: the base theme descends
 * from a dark MaterialComponents theme and the app never sets a night mode.
 * A surface that followed the system would be the only light thing in an
 * otherwise dark app. Once the remaining XML screens are ported, the whole app
 * can follow the system together.
 */
@Composable
fun BibleAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BibleAppDarkColorScheme, content = content)
}

/** Book name color legible on the dark surfaces Compose draws. */
fun bookForegroundColor(bookId: Int): Color = Color(BookColorUtil.getForegroundOnDark(bookId))
