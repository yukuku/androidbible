package yuku.alkitab.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import yuku.alkitab.base.App
import yuku.alkitab.base.fr.base.BaseFragment
import yuku.alkitab.songs.newdoc.SongDocument
import yuku.alkitab.songs.newdoc.SongDocumentJson

/**
 * Native Jetpack Compose song viewer — the experimental replacement for the
 * WebView-based [SongFragment], gated behind
 * [yuku.alkitab.base.settings.ExperimentalFlags.useComposeSong]. Renders the
 * same [SongDocument] via [SongDocumentComposable] with the exact feature set
 * of the HTML/CSS path (see that composable's doc), routing scripture,
 * YouTube, and patch-text clicks back to the host activity.
 */
class SongComposeFragment : BaseFragment(), SongTextZoomable {
    private val args by lazy { requireArguments() }
    private val doc: SongDocument by lazy { SongDocumentJson.decode(args.getString(ARG_songJson)!!) }
    private val copyright: String? by lazy { args.getString(ARG_copyright) }
    private val patchTextLinkLabel: String? by lazy { args.getString(ARG_patchTextLinkLabel) }

    private var zoomPercentState = mutableIntStateOf(100)

    override var songTextZoomPercent: Int
        get() = zoomPercentState.intValue
        set(value) { zoomPercentState.intValue = value }

    interface Host {
        fun onSongScriptureClick(osis: String)
        fun onSongYoutubeClick(videoId: String)
        fun onSongPatchTextClick()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val host = activity as? Host
                val applied = App.services.uiDimensions.applied()
                val style = SongComposeStyle(
                    fontColor = applied.fontColor,
                    verseNumberColor = applied.verseNumberColor,
                    backgroundColor = applied.backgroundColor,
                    baseFontSizeDp = applied.fontSize2dp,
                    lineSpacingMult = applied.lineSpacingMult,
                    typeface = applied.fontFace,
                    zoomPercent = zoomPercentState.intValue,
                )

                SongDocumentComposable(
                    doc = doc,
                    style = style,
                    copyright = copyright,
                    patchTextLinkLabel = patchTextLinkLabel,
                    onScriptureClick = { osis -> host?.onSongScriptureClick(osis) },
                    onYoutubeClick = { videoId -> host?.onSongYoutubeClick(videoId) },
                    onPatchTextClick = { host?.onSongPatchTextClick() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    companion object {
        private const val ARG_songJson = "songJson"
        private const val ARG_copyright = "copyright"
        private const val ARG_patchTextLinkLabel = "patchTextLinkLabel"

        fun create(doc: SongDocument, copyright: String?, patchTextLinkLabel: String?) = SongComposeFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_songJson, SongDocumentJson.encode(doc))
                putString(ARG_copyright, copyright)
                putString(ARG_patchTextLinkLabel, patchTextLinkLabel)
            }
        }
    }
}
