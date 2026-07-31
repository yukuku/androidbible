package yuku.alkitab.base.audio.ui

import androidx.compose.runtime.Composable
import yuku.alkitab.base.compose.BibleAppTheme

/**
 * Single Compose theme used by every audio-bar surface ([AudioBar],
 * [SpeedBottomSheet]). It delegates to [yuku.alkitab.base.compose.BibleAppTheme]
 * so the bar chrome draws from the very same palette as the rest of the app's
 * Compose surfaces — the bar sits directly against them, so any divergence
 * (e.g. one scheme dynamic and the other not) shows up as a mismatched grey.
 *
 * When users override the reading theme to e.g. sepia, that's a concern of
 * [AudioHighlightColor], not the bar chrome.
 */
@Composable
fun AudioTheme(content: @Composable () -> Unit) {
    BibleAppTheme(content)
}
