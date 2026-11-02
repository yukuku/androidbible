package yuku.alkitab.qa

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import yuku.alkitab.base.audio.AudioLogEntry
import yuku.alkitab.base.audio.ui.AudioBar
import yuku.alkitab.base.audio.ui.AudioBarUiState
import yuku.alkitab.base.audio.ui.AudioSourceOption
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.songs.SongComposeStyle
import yuku.alkitab.songs.SongDocumentComposable
import yuku.alkitab.songs.newdoc.Line
import yuku.alkitab.songs.newdoc.PBlock
import yuku.alkitab.songs.newdoc.SongDocument

object TypographyComponentFixtures {
    @JvmStatic
    fun showSongCasing(activity: Activity) {
        val view = ComposeView(activity)
        activity.setContentView(view)
        view.setContent {
            SongDocumentComposable(
                doc = SongDocument("1", blocks = listOf(
                    PBlock(role = "title", content = Line.Plain("Song typography sample")),
                    PBlock(role = "tune", content = Line.Plain("Evening hymn")),
                    PBlock(role = "copyright", content = Line.Plain("Written permission")),
                    PBlock(content = Line.Plain("A source sentence keeps its casing.")),
                )),
                style = SongComposeStyle(Color.BLACK, Color.GRAY, Color.WHITE, 20f, 1.2f, Typeface.SERIF, 100, false),
                copyright = "Copyright Sample Publisher",
                patchTextLinkLabel = null,
                onScriptureClick = {},
                onYoutubeClick = {},
                onPatchTextClick = {},
                modifier = Modifier.background(androidx.compose.ui.graphics.Color.White),
            )
        }
    }

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
