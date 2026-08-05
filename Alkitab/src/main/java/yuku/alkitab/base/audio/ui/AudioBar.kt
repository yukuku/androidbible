package yuku.alkitab.base.audio.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yuku.alkitab.base.audio.PlaybackState
import yuku.alkitab.debug.R
import kotlin.math.roundToLong

/**
 * UI-facing snapshot consumed by [AudioBar]. The controller projects
 * [PlaybackState] + chapter-navigation context into this and pushes it through
 * a `StateFlow`. Keep the data class flat and primitive-typed: the Compose
 * recomposition cost is proportional to its hash.
 *
 *  - [visible]              — drives [AnimatedVisibility]; the controller flips
 *                             this in [yuku.alkitab.base.audio.AudioBarController.show] / `hide`.
 *  - [timingAvailable]      — when false, the prev/next-verse buttons grey out
 *                             and the slider label drops the `· v.N` suffix.
 *  - [error]                — non-null swaps the play button for an error icon;
 *                             tapping it retries the load.
 */
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
    /** Version driving audio, or null. Used to scope the verse highlight to matching panes. */
    val playingVersionId: String?,
    /**
     * Title of the selected recording, shown as a compact button next to the
     * speed control. Null hides the button — versions with a single recording
     * should not pay for a control that offers no choice.
     */
    val setTitle: String?,
    /** When non-null, the source-picker dialog is shown over the bar. */
    val pickerOptions: List<AudioSourceOption>?,
    /** When true, the playback-speed bottom sheet is shown over the bar. */
    val showSpeedSheet: Boolean,
    /**
     * When non-null, the recording-picker bottom sheet is shown over the bar.
     * One group per visible version with audio — two groups in split view when
     * both sides have audio, so the picker also serves as the mid-session way
     * to move audio between the splits.
     */
    val setGroups: List<AudioSetGroup>?,
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
            playingVersionId = null,
            setTitle = null,
            pickerOptions = null,
            showSpeedSheet = false,
            setGroups = null,
        )
    }
}

/**
 * One row in the source-picker dialog: which version drives audio in split
 * view, carrying the recording that would play for it. Split-source selection
 * and set selection remain distinct choices — which *version* drives audio,
 * then which *recording* of it.
 */
data class AudioSourceOption(
    val versionId: String,
    val shortName: String,
    /** Recording that plays when this version is picked (the persisted selection, or the default). */
    val audioId: String,
    /** Display title of that recording. */
    val title: String,
)

/**
 * One version's recordings in the recording-picker bottom sheet. [versionName]
 * is rendered as a group header only when more than one group is shown.
 */
data class AudioSetGroup(
    val versionId: String,
    val versionName: String,
    val options: List<AudioSetOption>,
)

/**
 * One row in the recording-picker bottom sheet. Sets not covering the current
 * book are listed but disabled ([coversCurrentBook] = false) with the reason
 * shown — hiding them would make the list appear to change size as the user
 * moves through the Bible.
 */
data class AudioSetOption(
    val audioId: String,
    val title: String,
    val selected: Boolean,
    val coversCurrentBook: Boolean,
)

/**
 * Commands raised by the bar's UI. The controller maps these onto
 * [yuku.alkitab.base.audio.BibleAudioService] calls.
 *
 * `SeekDrag` is fired continuously while the user drags the slider thumb so
 * the bar can show a live `mm:ss · v.N` preview. `SeekCommit` is fired on
 * release; the controller decides whether to snap to a verse boundary or
 * issue a plain seek (depending on whether timing is available).
 */
sealed interface AudioBarCommand {
    data object PlayPause : AudioBarCommand
    /** Fired instead of [PlayPause] while the bar is in an error state — retries the load. */
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
    data class SeekDrag(val positionMs: Long) : AudioBarCommand
    data class SeekCommit(val positionMs: Long) : AudioBarCommand
    data class PickSource(val versionId: String) : AudioBarCommand
    data object CancelPicker : AudioBarCommand
}

/**
 * Top-level audio bar surface. Anchored at the bottom of `IsiActivity`'s
 * [androidx.compose.ui.platform.ComposeView] host. Shown/hidden synchronously —
 * the controller adds/removes the Compose content on session start/end, so
 * an enter/exit transition would just delay the layout reflow that the
 * activity already commits when the host view appears.
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
        if (!state.visible) return@AudioTheme
        // Pull the bottom system inset out of WindowInsets so the bar
        // a) extends its background all the way under the gesture pill
        // (Spotify-style edge-to-edge), and b) keeps actual controls
        // above the inset so the slider's mm:ss labels aren't clipped.
        // We deliberately don't fix the bar's height — Material 3 Slider
        // has thumb-shadow overflow that eats more than the visible
        // track, and a fixed-height container clips it.
        val bottomInset = WindowInsets.safeDrawing
            .asPaddingValues()
            .calculateBottomPadding()
        Surface(
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = modifier.fillMaxWidth(),
        ) {
            // Hoisted above the orientation branch so an in-progress seek
            // drag carries across a portrait↔landscape rotation (and process
            // death via rememberSaveable) instead of resetting to the playback
            // position.
            var dragValue by rememberSaveable { mutableStateOf<Float?>(null) }
            val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (landscape) {
                AudioBarLandscapeRow(
                    state = state,
                    onCommand = onCommand,
                    dragValue = dragValue,
                    onDragValueChange = { dragValue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 8.dp,
                            end = 8.dp,
                            top = 4.dp,
                            bottom = 4.dp + bottomInset,
                        ),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 8.dp,
                            end = 8.dp,
                            top = 4.dp,
                            bottom = 4.dp + bottomInset,
                        ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    AudioBarTopRow(state = state, onCommand = onCommand)
                    AudioBarSliderRow(
                        state = state,
                        onCommand = onCommand,
                        dragValue = dragValue,
                        onDragValueChange = { dragValue = it },
                    )
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
    // A speed chip and the recording chip on the left, the transport cluster
    // (verse skip + play/pause) centered, and a close button on the right.
    // Chapter-name text labels are intentionally omitted — the toolbar already
    // shows the current chapter, and on a phone they crowd out the speed
    // indicator and force the close button to wrap.
    //
    // The speed chip, cluster, and close button take their intrinsic width and
    // never shrink. The recording chip sits inside the left weighted slot, so
    // however long the set title is, it only ever ellipsizes within the
    // leftover space — it cannot squeeze the transport controls. The two
    // weighted slots get equal shares, which keeps the cluster centered and
    // collapses to zero when the row is tight.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeedButton(state = state, onCommand = onCommand)

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            SetButton(state = state, onCommand = onCommand)
        }

        PrevVerseButton(state = state, onCommand = onCommand)

        Spacer(Modifier.width(4.dp))
        PlayPauseButton(state = state, onCommand = onCommand)
        Spacer(Modifier.width(4.dp))

        NextVerseButton(state = state, onCommand = onCommand)

        Spacer(Modifier.weight(1f))

        CloseButton(onCommand = onCommand)
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

// Speed chip — tapping opens the [SpeedBottomSheet]. `softWrap = false`
// keeps locales that render with a comma decimal (e.g. "1,0×" in
// Indonesian) from wrapping into a stacked "1," / "0×" when the row
// is tight.
@Composable
private fun SpeedButton(
    state: AudioBarUiState,
    onCommand: (AudioBarCommand) -> Unit,
) {
    val locale = appLocale()
    TextButton(
        onClick = { onCommand(AudioBarCommand.Speed) },
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.audio_bar_speed_format, formatSpeedNumber(state.speed, locale)),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Compact recording chip next to the speed control, showing the selected
 * set's title. Tapping opens the [AudioSetBottomSheet]. Rendered only when
 * there is a choice to make ([AudioBarUiState.setTitle] is non-null). The
 * caller places it inside a weighted slot, so a long title ellipsizes within
 * the leftover row space instead of squeezing the transport controls.
 */
@Composable
private fun SetButton(
    state: AudioBarUiState,
    onCommand: (AudioBarCommand) -> Unit,
) {
    val title = state.setTitle ?: return
    TextButton(onClick = { onCommand(AudioBarCommand.OpenSetSheet) }) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

/**
 * Play/pause, with two special states sharing the slot: a progress ring while
 * preparing, and an error icon when the last load failed — tapping the error
 * icon retries the load instead of toggling playback.
 */
@Composable
private fun PlayPauseButton(
    state: AudioBarUiState,
    onCommand: (AudioBarCommand) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    Box(contentAlignment = Alignment.Center) {
        FilledIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCommand(if (state.error != null) AudioBarCommand.Retry else AudioBarCommand.PlayPause)
            },
            enabled = !state.preparing,
            modifier = Modifier.size(48.dp),
        ) {
            if (state.error != null) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = stringResource(R.string.audio_bar_error_retry),
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
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_audio_play),
                            contentDescription = stringResource(R.string.audio_bar_play),
                        )
                    }
                }
            }
        }
        if (state.preparing) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun AudioBarSliderRow(
    state: AudioBarUiState,
    onCommand: (AudioBarCommand) -> Unit,
    dragValue: Float?,
    onDragValueChange: (Float?) -> Unit,
) {
    val effectivePosition = dragValue?.roundToLong() ?: state.positionMs

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatMmSs(effectivePosition),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            modifier = Modifier.width(40.dp),
        )

        AudioBarSlider(
            state = state,
            onCommand = onCommand,
            dragValue = dragValue,
            onDragValueChange = onDragValueChange,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = formatMmSs(state.durationMs),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = LocalContentColor.current.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(start = 8.dp)
                .width(40.dp),
        )
        // We used to render a "v.N" indicator after the duration label, but
        // it was redundant with the verse highlight in the reader and just
        // looked like a stray code to users. The highlight in the verse list
        // is the authoritative current-verse cue.
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

/**
 * Landscape variant — collapses the bar into a single row to reclaim the
 * vertical space the two-row portrait layout costs (worse with split view).
 * Order: slider · spacer · transport cluster · recording chip · speed · close.
 * The mm:ss position/duration labels are dropped here — the slider conveys
 * progress and keeping the labels would push the control cluster into
 * wrapping on a single row. The recording chip sits in its own weighted slot
 * so a long set title ellipsizes there rather than squeezing the transport
 * controls.
 */
@Composable
private fun AudioBarLandscapeRow(
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
        AudioBarSlider(
            state = state,
            onCommand = onCommand,
            dragValue = dragValue,
            onDragValueChange = onDragValueChange,
            modifier = Modifier.weight(2f),
        )

        Spacer(Modifier.weight(1f))

        PrevVerseButton(state = state, onCommand = onCommand)

        Spacer(Modifier.width(4.dp))
        PlayPauseButton(state = state, onCommand = onCommand)
        Spacer(Modifier.width(4.dp))

        NextVerseButton(state = state, onCommand = onCommand)

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            SetButton(state = state, onCommand = onCommand)
        }

        SpeedButton(state = state, onCommand = onCommand)

        CloseButton(onCommand = onCommand)
    }
}

/** Shown when split view is active and both visible versions have audio. */
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

private fun formatMmSs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mm = totalSec / 60L
    val ss = totalSec % 60L
    return "%d:%02d".format(mm, ss)
}

// -- Previews ------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 400)
@Composable
private fun AudioBarPreviewPlaying() {
    AudioBar(
        state = AudioBarUiState(
            visible = true,
            isPlaying = true,
            preparing = false,
            positionMs = 42_000L,
            durationMs = 195_000L,
            verse_1 = 7,
            speed = 1.0f,
            error = null,
            timingAvailable = true,
            playingVersionId = "preset/in-tb",
            setTitle = "Alkitab Suara — a deliberately long recording title",
            pickerOptions = null,
            showSpeedSheet = false,
            setGroups = null,
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
            playingVersionId = "preset/in-tb",
            setTitle = null,
            pickerOptions = null,
            showSpeedSheet = false,
            setGroups = null,
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
            error = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", // play button becomes a retry
            timingAvailable = false, // some chapters have audio but no timing
            playingVersionId = "preset/in-tb",
            setTitle = null,
            pickerOptions = null,
            showSpeedSheet = false,
            setGroups = null,
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
            playingVersionId = null,
            setTitle = null,
            pickerOptions = listOf(
                AudioSourceOption(versionId = "preset/in-tb", shortName = "TB", audioId = "alkitabsuara", title = "Alkitab Suara"),
                AudioSourceOption(versionId = "preset/en-kjv", shortName = "KJV", audioId = "wordproject", title = "wordproject"),
            ),
            showSpeedSheet = false,
            setGroups = null,
        ),
        onCommand = {},
        modifier = Modifier,
    )
}
