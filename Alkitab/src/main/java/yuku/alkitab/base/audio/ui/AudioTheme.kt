package yuku.alkitab.base.audio.ui

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Single Compose theme used by every audio-bar surface ([AudioBar],
 * future speed bottom sheet). The audio bar is the project's first Compose
 * surface, so we own the bridge between the existing AppCompat theme and a
 * Material 3 [MaterialTheme] here, rather than threading one through every
 * call site.
 *
 * Strategy: derive the color scheme from [isSystemInDarkTheme] (which already
 * follows AppCompat's `MODE_NIGHT_*` setting via the activity's resources
 * configuration) so the bar picks up the same light/dark state as the rest of
 * the app — without us having to re-implement custom-theme selection in
 * Compose. When users override the reading theme to e.g. sepia, that's a
 * concern of [AudioHighlightColor], not the bar chrome.
 */
@Composable
fun AudioTheme(content: @Composable () -> Unit) {
    val isDark = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES || isSystemInDarkTheme()

    val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
