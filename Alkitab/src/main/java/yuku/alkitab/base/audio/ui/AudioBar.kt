package yuku.alkitab.base.audio.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import yuku.alkitab.base.audio.AudioLogEntry
import yuku.alkitab.base.audio.PlaybackState
import yuku.alkitab.base.audio.download.DownloadState
import yuku.alkitab.debug.R

data class AudioBarUiState(
    val visible: Boolean,
    val isPlaying: Boolean,
    val preparing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val verse_1: Int,
    val speed: Float,
    val error: String?,
    val timingAvailable: Boolean,
    val logs: List<AudioLogEntry>,
    val showLogSheet: Boolean,
    /** Non-null scopes the verse highlight to the pane playing this version. */
    val playingVersionId: String?,
    /** False when the visible versions offer at most one recording, which hides the chooser. */
    val canChooseSet: Boolean,
    /** When non-null, the source-picker dialog is shown over the bar. */
    val pickerOptions: List<AudioSourceOption>?,
    /** When true, the playback-speed bottom sheet is shown over the bar. */
    val showSpeedSheet: Boolean,
    val setGroups: List<AudioSetGroup>?,
    /** Non-null only for the bundled recording, whose chapters can be kept offline. */
    val downloadState: DownloadState?,
) {
    companion object {
        val HIDDEN = AudioBarUiState(
            visible = false,
            isPlaying = false,
            preparing = false,
            positionMs = 0L,
            durationMs = 0L,
            verse_1 = 0,
            speed = 1.0f,
            error = null,
            timingAvailable = false,
            logs = emptyList(),
            showLogSheet = false,
            playingVersionId = null,
            canChooseSet = false,
            pickerOptions = null,
            showSpeedSheet = false,
            setGroups = null,
            downloadState = null,
        )
    }
}

/** Which version drives audio in split view, vs. which recording of it ([AudioSetGroup]). */
data class AudioSourceOption(
    val versionId: String,
    val shortName: String,
    val audioId: String,
    val title: String,
)

data class AudioSetGroup(
    val versionId: String,
    val versionName: String,
    val options: List<AudioSetOption>,
)

/** Sets not covering the current book stay listed but disabled, so the list doesn't reshape as the user moves through the Bible. */
data class AudioSetOption(
    val audioId: String,
    val title: String,
    val selected: Boolean,
    val coversCurrentBook: Boolean,
)

/** Commands raised by the bar's UI; the controller maps these onto [yuku.alkitab.base.audio.BibleAudioService] calls. */
sealed interface AudioBarCommand {
    data object PlayPause : AudioBarCommand
    data object Retry : AudioBarCommand
    data object PrevVerse : AudioBarCommand
    data object NextVerse : AudioBarCommand
    data object Close : AudioBarCommand
    data object Speed : AudioBarCommand
    data class SetSpeed(val speed: Float) : AudioBarCommand
    data object DismissSpeedSheet : AudioBarCommand
    data object OpenSetSheet : AudioBarCommand
    data object DismissSetSheet : AudioBarCommand
    data class PickSet(val versionId: String, val audioId: String) : AudioBarCommand

    /** Fired continuously while dragging the slider thumb, for a live position preview. */
    data class SeekDrag(val positionMs: Long) : AudioBarCommand

    /** Fired on release; the controller decides whether to snap to a verse boundary. */
    data class SeekCommit(val positionMs: Long) : AudioBarCommand
    data class PickSource(val versionId: String) : AudioBarCommand
    data object CancelPicker : AudioBarCommand
    data object OpenLogSheet : AudioBarCommand
    data object DismissLogSheet : AudioBarCommand
    data object DownloadChapter : AudioBarCommand
    data object RemoveDownloadedChapter : AudioBarCommand
}

/**
 * Top-level audio bar surface. Shown and hidden synchronously with the
 * controller adding/removing this Compose content on session start/end; an
 * enter/exit transition would only delay the layout reflow the activity
 * already commits when the host view appears.
 */
@Composable
fun AudioBar(
    state: AudioBarUiState,
    onCommand: (AudioBarCommand) -> Unit,
    modifier: Modifier,
) {
    AudioTheme {
        state.pickerOptions?.let { options ->
            SourcePickerDialog(options = options, onCommand = onCommand)
        }
        if (state.showSpeedSheet) {
            SpeedBottomSheet(
                currentSpeed = state.speed,
                onSelect = { onCommand(AudioBarCommand.SetSpeed(it)) },
                onDismiss = { onCommand(AudioBarCommand.DismissSpeedSheet) },
            )
        }
        state.setGroups?.let { groups ->
            AudioSetBottomSheet(
                groups = groups,
                onSelect = { versionId, audioId -> onCommand(AudioBarCommand.PickSet(versionId, audioId)) },
                onDismiss = { onCommand(AudioBarCommand.DismissSetSheet) },
            )
        }
        if (state.showLogSheet) {
            AudioLogBottomSheet(
                logs = state.logs,
                onDismiss = { onCommand(AudioBarCommand.DismissLogSheet) },
            )
        }
        if (!state.visible) return@AudioTheme

        Surface(
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = modifier.fillMaxWidth(),
        ) {
            BoxWithConstraints(
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                ),
            ) {
                var dragValue by rememberSaveable { mutableStateOf<Float?>(null) }
                val isWide = maxWidth >= 600.dp

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    AudioLoadStatusLine(state = state, onCommand = onCommand)
                    if (isWide) {
                        AudioBarWideRow(
                            state = state,
                            onCommand = onCommand,
                            dragValue = dragValue,
                            onDragValueChange = { dragValue = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        AudioBarTopRow(state = state, onCommand = onCommand)
                        AudioBarSliderRow(
                            state = state,
                            onCommand = onCommand,
                            dragValue = dragValue,
                            onDragValueChange = { dragValue = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioBarTopRow(
    state: AudioBarUiState,
    onCommand: (AudioBarCommand) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Row {
                SpeedButton(state = state, onCommand = onCommand)
                if (state.canChooseSet) {
                    SetChooserButton(onCommand = onCommand)
                }
            }
        }

        PrevVerseButton(state = state, onCommand = onCommand)
        PlayPauseButton(state = state, onCommand = onCommand)
        NextVerseButton(state = state, onCommand = onCommand)

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Row {
                DownloadButton(state = state, onCommand = onCommand)
                CloseButton(onCommand = onCommand)
            }
        }
    }
}

@Composable
private fun PrevVerseButton(
    state: AudioBarUiState,
    onCommand: (AudioBarCommand) -> Unit,
) {
    IconButton(
        onClick = { onCommand(AudioBarCommand.PrevVerse) },
        enabled = state.timingAvailable,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_audio_keyboard_arrow_left),
            contentDescription = stringResource(R.string.audio_bar_prev_verse),
        )
    }
}

@Composable
private fun NextVerseButton(
    state: AudioBarUiState,
    onCommand: (AudioBarCommand) -> Unit,
) {
    IconButton(
        onClick = { onCommand(AudioBarCommand.NextVerse) },
        enabled = state.timingAvailable,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_audio_keyboard_arrow_right),
            contentDescription = stringResource(R.string.audio_bar_next_verse),
        )
    }
}

@Composable
private fun SpeedButton(
    state: AudioBarUiState,
    onCommand: (AudioBarCommand) -> Unit,
) {
    val locale = appLocale()
    TextButton(
        onClick = { onCommand(AudioBarCommand.Speed) },
        colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
    ) {
        Text(
            text = stringResource(R.string.audio_bar_speed_format, formatSpeedNumber(state.speed, locale)),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun SetChooserButton(
    onCommand: (AudioBarCommand) -> Unit,
) {
    IconButton(onClick = { onCommand(AudioBarCommand.OpenSetSheet) }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.List,
            contentDescription = stringResource(R.string.audio_bar_select_audio),
        )
    }
}

@Composable
private fun CloseButton(
    onCommand: (AudioBarCommand) -> Unit,
) {
    IconButton(onClick = { onCommand(AudioBarCommand.Close) }) {
        Icon(
            painter = painterResource(R.drawable.ic_audio_close),
            contentDescription = stringResource(R.string.audio_bar_close),
        )
    }
}

@Composable
private fun DownloadButton(
    state: AudioBarUiState,
    onCommand: (AudioBarCommand) -> Unit,
) {
    val downloadState = state.downloadState ?: return
    val label = when (downloadState) {
        DownloadState.NotDownloaded -> stringResource(R.string.audio_bar_download_chapter)
        is DownloadState.Downloading -> {
            if (downloadState.total > 0L) {
                val percent = ((downloadState.bytes * 100L) / downloadState.total).coerceIn(0L, 100L)
                stringResource(R.string.audio_bar_downloading_percent, percent)
            } else {
                stringResource(R.string.audio_bar_downloading)
            }
        }
        DownloadState.Downloaded -> stringResource(R.string.audio_bar_remove_download)
        DownloadState.Failed -> stringResource(R.string.audio_bar_retry_download)
    }
    val downloading = downloadState is DownloadState.Downloading
    IconButton(
        onClick = {
            onCommand(
                if (downloadState == DownloadState.Downloaded) {
                    AudioBarCommand.RemoveDownloadedChapter
                } else {
                    AudioBarCommand.DownloadChapter
                },
            )
        },
        enabled = !downloading,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        when (downloadState) {
            is DownloadState.Downloading -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            DownloadState.Downloaded -> Icon(Icons.Filled.DownloadDone, contentDescription = null)
            DownloadState.NotDownloaded, DownloadState.Failed -> Icon(Icons.Filled.Download, contentDescription = null)
        }
    }
}

@Composable
private fun AudioLoadStatusLine(
    state: AudioBarUiState,
    onCommand: (AudioBarCommand) -> Unit,
) {
    val slowLoad = slowLoadStatusVisible(state.preparing)
    if (state.error == null && !slowLoad) return
    // The error is the message; a trailing log entry would bury it.
    val text = state.error
        ?: state.logs.lastOrNull()?.message
        ?: stringResource(R.string.audio_bar_loading_status_fallback)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = if (state.error != null) MaterialTheme.colorScheme.error else LocalContentColor.current.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCommand(AudioBarCommand.OpenLogSheet) },
    )
}

@Composable
private fun slowLoadStatusVisible(preparing: Boolean): Boolean {
    if (LocalInspectionMode.current) return preparing
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(preparing) {
        if (preparing) {
            delay(SLOW_LOAD_STATUS_DELAY)
            show = true
        } else {
            show = false
        }
    }
    return show
}

private val SLOW_LOAD_STATUS_DELAY = 5_000.milliseconds

// Box+combinedClickable, not FilledIconButton: that component only exposes a
// single onClick, and layering a separate long-press detector around it
// would race its internal gesture detector for the down event.
@Composable
private fun PlayPauseButton(
    state: AudioBarUiState,
    onCommand: (AudioBarCommand) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val showPreparing = debouncedPreparing(state.preparing)
    val enabled = !showPreparing
    val colors = IconButtonDefaults.filledIconButtonColors()
    val containerColor = if (enabled) colors.containerColor else colors.disabledContainerColor
    val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(IconButtonDefaults.filledShape)
                .background(containerColor)
                .combinedClickable(
                    interactionSource = null,
                    indication = ripple(),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCommand(if (state.error != null) AudioBarCommand.Retry else AudioBarCommand.PlayPause)
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCommand(AudioBarCommand.OpenLogSheet)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (state.error != null) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = stringResource(R.string.audio_bar_error_retry),
                    tint = contentColor,
                )
            } else {
                AnimatedContent(
                    targetState = state.isPlaying,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(150)) togetherWith
                            fadeOut(animationSpec = tween(150)))
                    },
                    label = "playPauseIcon",
                ) { playing ->
                    if (playing) {
                        Icon(
                            painter = painterResource(R.drawable.ic_audio_pause),
                            contentDescription = stringResource(R.string.audio_bar_pause),
                            tint = contentColor,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_audio_play),
                            contentDescription = stringResource(R.string.audio_bar_play),
                            tint = contentColor,
                        )
                    }
                }
            }
        }
        if (showPreparing) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun debouncedPreparing(preparing: Boolean): Boolean {
    if (LocalInspectionMode.current) return preparing
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(preparing) {
        if (preparing) {
            delay(PREPARING_INDICATION_DELAY)
            show = true
        } else {
            show = false
        }
    }
    return show
}

private val PREPARING_INDICATION_DELAY = 100.milliseconds

@Composable
private fun AudioBarSliderRow(
    state: AudioBarUiState,
    onCommand: (AudioBarCommand) -> Unit,
    dragValue: Float?,
    modifier: Modifier = Modifier,
    onDragValueChange: (Float?) -> Unit,
) {
    val effectivePosition = dragValue?.roundToLong() ?: state.positionMs

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatMmSs(effectivePosition),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .padding(end = 8.dp)
                .widthIn(min = 24.dp),
        )

        AudioBarSlider(
            state = state,
            onCommand = onCommand,
            dragValue = dragValue,
            onDragValueChange = onDragValueChange,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = durationLabelText(state.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(start = 8.dp)
                .widthIn(min = 24.dp),
        )
    }
}

@Composable
private fun AudioBarSlider(
    state: AudioBarUiState,
    onCommand: (AudioBarCommand) -> Unit,
    dragValue: Float?,
    onDragValueChange: (Float?) -> Unit,
    modifier: Modifier,
) {
    val effectivePosition = dragValue?.roundToLong() ?: state.positionMs
    Slider(
        value = effectivePosition.toFloat(),
        valueRange = 0f..maxOf(state.durationMs.toFloat(), 1f),
        onValueChange = { v ->
            onDragValueChange(v)
            onCommand(AudioBarCommand.SeekDrag(v.roundToLong()))
        },
        onValueChangeFinished = {
            val v = dragValue ?: return@Slider
            onCommand(AudioBarCommand.SeekCommit(v.roundToLong()))
            onDragValueChange(null)
        },
        enabled = state.durationMs > 0L && state.error == null,
        modifier = modifier,
    )
}

@Composable
private fun AudioBarWideRow(
    state: AudioBarUiState,
    onCommand: (AudioBarCommand) -> Unit,
    dragValue: Float?,
    onDragValueChange: (Float?) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Row {
                SpeedButton(state = state, onCommand = onCommand)
                if (state.canChooseSet) {
                    SetChooserButton(onCommand = onCommand)
                }
            }
        }

        PrevVerseButton(state = state, onCommand = onCommand)
        PlayPauseButton(state = state, onCommand = onCommand)
        NextVerseButton(state = state, onCommand = onCommand)

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Row {
                DownloadButton(state = state, onCommand = onCommand)
                AudioBarSliderRow(
                    state = state,
                    onCommand = onCommand,
                    dragValue = dragValue,
                    onDragValueChange = onDragValueChange,
                    modifier = Modifier.weight(1f),
                )
                CloseButton(onCommand = onCommand)
            }
        }
    }
}

@Composable
private fun SourcePickerDialog(
    options: List<AudioSourceOption>,
    onCommand: (AudioBarCommand) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onCommand(AudioBarCommand.CancelPicker) },
        title = { Text(stringResource(R.string.audio_bar_pick_source_title)) },
        text = {
            Column {
                options.forEach { option ->
                    TextButton(
                        onClick = { onCommand(AudioBarCommand.PickSource(option.versionId)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = option.shortName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onCommand(AudioBarCommand.CancelPicker) }) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

internal fun formatMmSs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mm = totalSec / 60L
    val ss = totalSec % 60L
    return "%d:%02d".format(mm, ss)
}

internal fun durationLabelText(durationMs: Long): String =
    if (durationMs > 0L) formatMmSs(durationMs) else ""

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 320)
@Composable
private fun AudioBarPreviewNarrow() {
    AudioBar(
        state = AudioBarUiState(
            visible = true,
            isPlaying = true,
            preparing = false,
            positionMs = 42_000L,
            durationMs = 9_195_000L,
            verse_1 = 7,
            speed = 1.0f,
            error = null,
            timingAvailable = true,
            logs = emptyList(),
            showLogSheet = false,
            playingVersionId = "preset/in-tb",
            canChooseSet = true,
            pickerOptions = null,
            showSpeedSheet = false,
            setGroups = null,
            downloadState = DownloadState.NotDownloaded,
        ),
        onCommand = {},
        modifier = Modifier,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 600)
@Composable
private fun AudioBarPreviewWide() {
    AudioBar(
        state = AudioBarUiState(
            visible = true,
            isPlaying = true,
            preparing = false,
            positionMs = 142_000L,
            durationMs = 195_000L,
            verse_1 = 777,
            speed = 1.0f,
            error = null,
            timingAvailable = true,
            logs = emptyList(),
            showLogSheet = false,
            playingVersionId = "preset/in-tb",
            canChooseSet = true,
            pickerOptions = null,
            showSpeedSheet = false,
            setGroups = null,
            downloadState = DownloadState.Downloaded,
        ),
        onCommand = {},
        modifier = Modifier,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 400)
@Composable
private fun AudioBarPreviewPreparing() {
    AudioBar(
        state = AudioBarUiState(
            visible = true,
            isPlaying = false,
            preparing = true,
            positionMs = 0L,
            durationMs = 0L,
            verse_1 = 0,
            speed = 1.0f,
            error = null,
            timingAvailable = false,
            logs = emptyList(),
            showLogSheet = false,
            playingVersionId = "preset/in-tb",
            canChooseSet = false,
            pickerOptions = null,
            showSpeedSheet = false,
            setGroups = null,
            downloadState = null,
        ),
        onCommand = {},
        modifier = Modifier,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 400)
@Composable
private fun AudioBarPreviewSlowLoad() {
    AudioBar(
        state = AudioBarUiState(
            visible = true,
            isPlaying = false,
            preparing = true,
            positionMs = 0L,
            durationMs = 0L,
            verse_1 = 0,
            speed = 1.0f,
            error = null,
            timingAvailable = false,
            logs = listOf(
                AudioLogEntry(0L, "Loading Genesis 1"),
                AudioLogEntry(1_000L, "HTTP request started: https://audio.example/gen1.mp3"),
                AudioLogEntry(1_200L, "DNS lookup started (audio.example)"),
                AudioLogEntry(4_800L, "Connecting to 93.184.216.34:443"),
                AudioLogEntry(5_600L, "Waiting for response headers"),
            ),
            showLogSheet = false,
            playingVersionId = "preset/in-tb",
            canChooseSet = false,
            pickerOptions = null,
            showSpeedSheet = false,
            setGroups = null,
            downloadState = null,
        ),
        onCommand = {},
        modifier = Modifier,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 400, uiMode = 32)
@Composable
private fun AudioBarPreviewDarkError() {
    AudioBar(
        state = AudioBarUiState(
            visible = true,
            isPlaying = false,
            preparing = false,
            positionMs = 12_000L,
            durationMs = 60_000L,
            verse_1 = 0,
            speed = 1.0f,
            error = "IO_NETWORK_CONNECTION_FAILED",
            timingAvailable = false,
            logs = listOf(
                AudioLogEntry(0L, "Loading Genesis 1"),
                AudioLogEntry(1_000L, "HTTP request started: https://audio.example/gen1.mp3"),
                AudioLogEntry(1_200L, "DNS lookup started (audio.example)"),
                AudioLogEntry(6_500L, "Connect failed: Unable to resolve host"),
                AudioLogEntry(6_600L, "Player error: IO_NETWORK_CONNECTION_FAILED (Unable to connect)"),
            ),
            showLogSheet = false,
            playingVersionId = "preset/in-tb",
            canChooseSet = false,
            pickerOptions = null,
            showSpeedSheet = false,
            setGroups = null,
            downloadState = DownloadState.Failed,
        ),
        onCommand = {},
        modifier = Modifier,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 400)
@Composable
private fun AudioBarPreviewWithPicker() {
    AudioBar(
        state = AudioBarUiState(
            visible = true,
            isPlaying = false,
            preparing = false,
            positionMs = 0L,
            durationMs = 0L,
            verse_1 = 0,
            speed = 1.0f,
            error = null,
            timingAvailable = false,
            logs = emptyList(),
            showLogSheet = false,
            playingVersionId = null,
            canChooseSet = false,
            pickerOptions = listOf(
                AudioSourceOption(versionId = "preset/in-tb", shortName = "TB", audioId = "alkitabsuara", title = "Alkitab Suara"),
                AudioSourceOption(versionId = "preset/en-kjv", shortName = "KJV", audioId = "wordproject", title = "wordproject"),
            ),
            showSpeedSheet = false,
            setGroups = null,
            downloadState = null,
        ),
        onCommand = {},
        modifier = Modifier,
    )
}
