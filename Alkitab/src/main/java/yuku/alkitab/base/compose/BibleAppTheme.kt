package yuku.alkitab.base.compose

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import yuku.alkitab.base.util.BookColorUtil

/**
 * Theme for the app's Compose surfaces.
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
    val ctx = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(ctx)
    } else {
        darkColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Book name color legible on the dark surfaces Compose draws. */
fun bookForegroundColor(bookId: Int): Color = Color(BookColorUtil.getForegroundOnDark(bookId))
