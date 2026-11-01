package yuku.alkitab.qa

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import yuku.alkitab.base.audio.AudioLogEntry
import yuku.alkitab.base.audio.ui.AudioBar
import yuku.alkitab.base.audio.ui.AudioBarUiState
import yuku.alkitab.base.audio.ui.AudioSourceOption
import yuku.alkitab.base.compose.BibleAppTheme

object TypographyComponentFixtures {
    @JvmStatic
    fun showAudio(activity: Activity, isSourcePicker: Boolean) {
        val state = if (isSourcePicker) {
            AudioBarUiState.HIDDEN.copy(pickerOptions = listOf(
                AudioSourceOption("qa/left", "BIMK", "qa/left", "QA left pane"),
                AudioSourceOption("qa/right", "DDD", "qa/right", "QA right pane"),
            ))
        } else {
            AudioBarUiState.HIDDEN.copy(showLogSheet = true, logs = listOf(
                AudioLogEntry(0L, "Memuat Kejadian 1"),
                AudioLogEntry(1000L, "Merekam contoh status pemuatan untuk audit tipografi"),
            ))
        }
        val view = ComposeView(activity)
        activity.addContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view.setContent {
            BibleAppTheme {
                AudioBar(state = state, onCommand = {}, modifier = Modifier)
            }
        }
    }
}
