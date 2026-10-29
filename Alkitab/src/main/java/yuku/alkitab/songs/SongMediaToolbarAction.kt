package yuku.alkitab.songs

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.debug.R

internal enum class SongMediaToolbarState {
    Hidden, Loading, Audio, Videos, AudioAndVideos, Unavailable, Retry;

    val isVisible: Boolean get() = this != Hidden
    val isEnabled: Boolean get() = isVisible
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
        else -> SongMediaToolbarState.Unavailable
    }
}

internal fun songMediaToolbarActionView(
    activity: ComponentActivity,
    toolbar: View,
    state: () -> SongMediaToolbarState,
    onClick: () -> Unit,
): ComposeView = ComposeView(activity).apply {
    // Toolbar measures before attachment and temporarily detaches actions during refreshes.
    setViewTreeLifecycleOwner(activity)
    setViewTreeSavedStateRegistryOwner(activity)
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
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
        SongMediaToolbarState.Unavailable -> R.string.sn_media_empty
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
    val isMuted = state == SongMediaToolbarState.Loading || state == SongMediaToolbarState.Retry || state == SongMediaToolbarState.Unavailable
    val drawable = when (state) {
        SongMediaToolbarState.AudioAndVideos -> R.drawable.ic_song_media_audio_video
        SongMediaToolbarState.Retry -> R.drawable.ic_song_media_retry
        SongMediaToolbarState.Unavailable -> R.drawable.ic_song_media_unavailable
        else -> R.drawable.ic_song_media_loading
    }
    Box(modifier.size(24.dp).alpha(if (isMuted) 0.38f else 1f)) {
        Icon(painterResource(drawable), null, Modifier.size(24.dp), tint = Color.White)
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
