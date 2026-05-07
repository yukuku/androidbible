package yuku.alkitab.base.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import yuku.alkitab.base.util.BookColorUtil

@Composable
fun BibleAppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Theme-aware book name color. Picks [BookColorUtil.getForegroundOnDark] or
 *  [BookColorUtil.getForegroundOnLight] based on the current Compose theme. */
@Composable
fun bookForegroundColor(bookId: Int): Color = if (isSystemInDarkTheme()) {
    Color(BookColorUtil.getForegroundOnDark(bookId))
} else {
    Color(BookColorUtil.getForegroundOnLight(bookId))
}
