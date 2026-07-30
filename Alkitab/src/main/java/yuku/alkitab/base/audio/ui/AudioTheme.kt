package yuku.alkitab.base.audio.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Single Compose theme used by every audio-bar surface ([AudioBar],
 * future speed bottom sheet). The audio bar is the project's first Compose
 * surface, so we own the bridge between the existing AppCompat theme and a
 * Material 3 [MaterialTheme] here, rather than threading one through every
 * call site.
 *
 * The scheme is dark whatever the system theme is, for the reason
 * [yuku.alkitab.base.compose.BibleAppTheme] gives. When users override the
 * reading theme to e.g. sepia, that's a concern of [AudioHighlightColor], not
 * the bar chrome.
 */
@Composable
fun AudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content,
    )
}
