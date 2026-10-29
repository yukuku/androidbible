package yuku.alkitab.songs

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.debug.R

internal enum class SongMediaToolbarState {
    Hidden, Loading, Audio, Videos, AudioAndVideos, Retry;

    val isVisible: Boolean get() = this != Hidden
    val isEnabled: Boolean get() = isVisible && this != Loading
}

internal fun SongMediaViewModel.UiState.toolbarState(bookName: String?, code: String?): SongMediaToolbarState {
    if (bookName == null || code == null) return SongMediaToolbarState.Hidden
    if (this.bookName != bookName || this.code != code || isLoading) return SongMediaToolbarState.Loading
    if (isError) return SongMediaToolbarState.Retry

    val media = media ?: return SongMediaToolbarState.Retry
    val isAudioAvailable = media.audioClips.isNotEmpty()
    val isVideoAvailable = media.youtubeVideos.isNotEmpty()
    return when {
        isAudioAvailable && isVideoAvailable -> SongMediaToolbarState.AudioAndVideos
        isAudioAvailable -> SongMediaToolbarState.Audio
        isVideoAvailable -> SongMediaToolbarState.Videos
        media.isIncomplete -> SongMediaToolbarState.Retry
        else -> SongMediaToolbarState.Hidden
    }
}

internal fun songMediaToolbarActionView(
    activity: ComponentActivity,
    toolbar: View,
    state: () -> SongMediaToolbarState,
    onClick: () -> Unit,
): ComposeView = ComposeView(activity).apply {
    // Toolbar measures action views before attaching them to the window.
    setViewTreeLifecycleOwner(activity)
    setViewTreeSavedStateRegistryOwner(activity)
    val composition = toolbar.findViewTreeCompositionContext()
        ?: toolbar.createLifecycleAwareWindowRecomposer(lifecycle = activity.lifecycle).also { toolbar.compositionContext = it }
    setParentCompositionContext(composition)
    setContent { BibleAppTheme { SongMediaToolbarAction(state(), onClick) } }
}

@Composable
internal fun SongMediaToolbarAction(
    state: SongMediaToolbarState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return

    val description = stringResource(when (state) {
        SongMediaToolbarState.Audio -> R.string.sn_media_audio
        SongMediaToolbarState.Videos -> R.string.sn_media_videos
        SongMediaToolbarState.Loading -> R.string.sn_media_checking
        SongMediaToolbarState.Retry -> R.string.sn_media_retry_options
        else -> R.string.sn_media_title
    })
    IconButton(
        onClick = onClick,
        enabled = state.isEnabled,
        modifier = modifier.size(48.dp),
    ) {
        Box(Modifier.size(24.dp).clearAndSetSemantics { contentDescription = description }) {
            when (state) {
                SongMediaToolbarState.Audio -> Icon(painterResource(R.drawable.ic_song_media_audio), null, tint = Color.White)
                SongMediaToolbarState.Videos -> Icon(painterResource(R.drawable.ic_song_media), null, tint = Color.White)
                else -> SongMediaToolbarCompositeIcon(state)
            }
        }
    }
}

@Composable
private fun SongMediaToolbarCompositeIcon(state: SongMediaToolbarState, modifier: Modifier = Modifier) {
    val isMuted = state == SongMediaToolbarState.Loading || state == SongMediaToolbarState.Retry
    Box(modifier.size(24.dp).alpha(if (isMuted) 0.38f else 1f)) {
        Canvas(Modifier.size(24.dp)) {
            scale(size.width / 24f, size.height / 24f, Offset.Zero) {
                if (state == SongMediaToolbarState.AudioAndVideos) {
                    drawPath(SongMediaToolbarPaths.frameWithNote, Color.White)
                    drawPath(SongMediaToolbarPaths.note, Color.White)
                } else {
                    drawPath(SongMediaToolbarPaths.frameWithCornerGap, Color.White)
                    if (state == SongMediaToolbarState.Retry) {
                        drawPath(SongMediaToolbarPaths.retry, Color.White, style = Stroke(1.56f, join = StrokeJoin.Round))
                    }
                }
            }
        }
        if (state == SongMediaToolbarState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.absoluteOffset(12.dp, 12.dp).size(12.dp),
                color = Color.White,
                strokeWidth = 1.5.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
            )
        }
    }
}

private object SongMediaToolbarPaths {
    private const val FRAME = "M21 3H3a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h18a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2Zm0 16H3V5h18Z"
    private const val NOTE = "M12 3v10.55A4 4 0 1 0 16 17V7h4V3Z"

    private val noteTransform = Matrix().apply { setScale(0.55f, 0.55f); postTranslate(4.8f, 10.8f) }
    val note: Path = path(NOTE).apply { transform(noteTransform) }.asComposePath()
    val frameWithNote: Path = path(FRAME).apply {
        val gap = AndroidPath()
        Paint().apply {
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = 5.5f
            strokeJoin = Paint.Join.ROUND
        }.getFillPath(path(NOTE), gap)
        gap.transform(noteTransform)
        op(gap, AndroidPath.Op.DIFFERENCE)
    }.asComposePath()
    val frameWithCornerGap: Path = path(FRAME).apply {
        op(AndroidPath().apply { addCircle(18f, 18f, 7.5f, AndroidPath.Direction.CW) }, AndroidPath.Op.DIFFERENCE)
    }.asComposePath()
    val retry: Path = path("M21 3v6h-6M20.5 15a9 9 0 1 1-2.1-9.4L21 9").apply {
        transform(Matrix().apply { setScale(0.52f, 0.52f); postTranslate(11.76f, 11.76f) })
    }.asComposePath()

    private fun path(data: String): AndroidPath = requireNotNull(PathParser.createPathFromPathData(data))
}
