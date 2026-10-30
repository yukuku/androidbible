package yuku.alkitab.songs

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import coil.compose.AsyncImage
import java.text.NumberFormat
import java.util.Locale
import yuku.alkitab.base.compose.ComposeBottomSheetHost
import yuku.alkitab.debug.R

object SongMediaSheet {
    fun show(
        activity: ComponentActivity,
        title: String,
        subtitle: String?,
        viewModel: SongMediaViewModel,
        controller: SongAudioController,
        onPlay: (SongAudioClip) -> Unit,
        onVideo: (SongYoutubeVideo) -> Unit,
    ) {
        ComposeBottomSheetHost.show(
            activity = activity,
            sheetMaxWidth = 360.dp,
            sheetGesturesEnabled = false,
            dimBackground = true,
            contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal) },
        ) { dismiss ->
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window?.isNavigationBarContrastEnforced = false
                }
            }
            val state by viewModel.state.collectAsState()
            SongMediaSheetContent(
                title = title,
                subtitle = subtitle,
                state = state,
                controller = controller,
                onPlay = onPlay,
                onVideo = onVideo,
                onRetry = {
                    val book = state.bookName
                    val code = state.code
                    if (book != null && code != null) viewModel.load(book, code, true)
                },
                onDismiss = dismiss,
            )
        }
    }
}

@Composable
internal fun SongMediaSheetContent(
    title: String,
    subtitle: String?,
    state: SongMediaViewModel.UiState,
    controller: SongAudioController,
    onPlay: (SongAudioClip) -> Unit,
    onVideo: (SongYoutubeVideo) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = (maxHeight - 64.dp).coerceAtLeast(1.dp)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, stringResource(R.string.desc_close))
                }
            }
            HorizontalDivider()
            val media = state.media
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues(),
            ) {
                if (state.isLoading) {
                    item {
                        Box(Modifier
                            .fillMaxWidth()
                            .padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                        }
                    }
                }
                if (state.isError || media?.isIncomplete == true) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(stringResource(R.string.sn_media_load_error), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onRetry, enabled = !state.isLoading) {
                                Text(stringResource(R.string.devotion_retry))
                            }
                        }
                    }
                }
                if (media != null) {
                    items(media.audioClips) { clip ->
                        SongAudioBar(clip, controller, { onPlay(clip) }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    items(media.youtubeVideos, key = { it.videoId }) { video ->
                        SongVideoRow(video, { onVideo(video) })
                    }
                    if (!state.isLoading && !state.isError && !media.isIncomplete && media.audioClips.isEmpty() && media.youtubeVideos.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.sn_media_empty),
                                Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
@OptIn(UnstableApi::class)
internal fun SongAudioBar(
    clip: SongAudioClip,
    controller: SongAudioController,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackState = controller.state
    val isLooping = controller.isLooping
    val isActive = controller.isCurrentUrl(clip.url)
    val player = controller.playbackPlayer.takeIf {
        isActive && playbackState != MediaController.State.error && playbackState != MediaController.State.reset_media_known_to_exist
    }
    val progress = rememberProgressStateWithTickInterval(player, tickIntervalMs = 250)
    var seekPosition by remember(controller, clip.url) { mutableStateOf<Float?>(null) }
    val isPlaying = isActive && playbackState == MediaController.State.playing
    val isPreparing = isActive && playbackState == MediaController.State.preparing
    val position = progress.currentPositionMs.coerceAtLeast(0L)
    val duration = progress.durationMs.coerceAtLeast(0L)
    val seekDescription = stringResource(R.string.sn_media_seek)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.sn_media_audio), Modifier
                .weight(1f)
                .padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
            IconToggleButton(checked = isLooping, onCheckedChange = controller::setLoop) {
                Icon(Icons.Default.Repeat, stringResource(R.string.sn_media_repeat))
            }
            FilledIconButton(onClick = onPlay, enabled = !isPreparing) {
                if (isPreparing) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (isPlaying) R.string.audio_bar_pause else R.string.audio_bar_play),
                    )
                }
            }
        }
        Slider(
            value = (seekPosition ?: position.toFloat()).coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
            onValueChange = { seekPosition = it },
            onValueChangeFinished = {
                seekPosition?.let { controller.seekTo(it.toLong()) }
                seekPosition = null
            },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            enabled = duration > 0,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = seekDescription },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatSongTime(seekPosition?.toLong() ?: position), style = MaterialTheme.typography.labelMedium)
            Text(if (duration > 0) formatSongTime(duration) else "–:––", style = MaterialTheme.typography.labelMedium)
        }
        if (isActive && playbackState == MediaController.State.error) {
            Text(stringResource(R.string.audio_bar_error_retry), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

internal fun formatSongTime(positionMs: Long): String =
    String.format(Locale.US, "%d:%02d", positionMs / 60000, positionMs % 60000 / 1000)

@Composable
private fun SongVideoRow(
    video: SongYoutubeVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(104.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.SmartDisplay, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(video.channelName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)

            val metadata = formatSongVideoMetadata(video.viewCount, video.isViewCountApproximate, video.publishedAt, locale)
            if (metadata.isNotEmpty()) {
                val count = video.viewCount
                val spokenViews = if (count != null) {
                    stringResource(
                        if (video.isViewCountApproximate) R.string.sn_media_views_approximate else R.string.sn_media_views_exact,
                        NumberFormat.getIntegerInstance(locale).format(count),
                    )
                } else {
                    null
                }
                val spokenMetadata = listOfNotNull(spokenViews, video.publishedAt?.let { formatSongVideoDate(it, locale) }).joinToString(" • ")
                Text(
                    metadata,
                    modifier = Modifier.semantics { contentDescription = spokenMetadata },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
