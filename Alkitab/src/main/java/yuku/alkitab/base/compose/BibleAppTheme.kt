package yuku.alkitab.base.compose

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import yuku.alkitab.base.util.BookColorUtil

/**
 * Mirrors `@color/accent`, `@color/primary`, `@color/primary_dark`,
 * `@color/escape` and `@color/window`, so the Compose surfaces read as part of
 * the same app as the View screens. Update both together.
 */
private object BrandColors {
    val Accent = Color(0xFF40C4FF)
    val ChromeBlueGray = Color(0xFF455A64)
    val ChromeBlueGrayDark = Color(0xFF263238)
    val Escape = Color(0xFF4DB6AC)
    val WindowGray = Color(0xFF303030)
}

/**
 * This is a static scheme, not the Material dynamic one. Dynamic colors come
 * from the device wallpaper, so the same screen would be purple on one phone
 * and green on another while the XML screens around it stay blue-gray.
 *
 * `primary` carries the accent because that is the role `colorAccent` plays for
 * View widgets, and the `secondary` family carries the toolbar blue-gray so the
 * M3 components leaning on `secondaryContainer` wear it too. `surfaceTint` is
 * transparent so tonal elevation never washes the grays with blue.
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
 * The scheme is always dark, whatever the system theme is, because the XML
 * screens around these surfaces are always dark. The base theme descends from a
 * dark MaterialComponents theme and the app never sets a night mode.
 */
@Composable
fun BibleAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BibleAppDarkColorScheme, content = content)
}

/** Book name color legible on the dark surfaces Compose draws. */
fun bookForegroundColor(bookId: Int): Color = Color(BookColorUtil.getForegroundOnDark(bookId))
