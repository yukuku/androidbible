package yuku.alkitab.base.audio.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
 *  - [prevChapterLabel] /
 *    [nextChapterLabel]     — short reference text for the chapter-nav buttons
 *                             (`"Jn 2"`, `"Mt 28"`). null means "Bible boundary";
 *                             the slot is rendered with `alpha = 0f` so the bar
 *                             doesn't reflow.
 *  - [timingAvailable]      — when false, the prev/next-verse buttons grey out
 *                             and the slider label drops the `· v.N` suffix.
 *  - [error]                — non-null disables the play button. M5 will
 *                             surface this via snackbar; M3 just degrades the
 *                             control.
 */
data class AudioBarUiState(
    val visible: Boolean,
    val isPlaying: Boolean,
    val preparing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val verse_1: Int,
    val speed: Float,
    val prevChapterLabel: String?,
    val nextChapterLabel: String?,
    val error: String?,
    val timingAvailable: Boolean,
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
            prevChapterLabel = null,
            nextChapterLabel = null,
            error = null,
            timingAvailable = false,
        )
    }
}

/**
 * Commands raised by the bar's UI. The controller maps these onto
 * [yuku.alkitab.base.audio.BibleAudioService] calls and (for chapter nav) onto
 * the host activity's `display(...)` method.
 *
 * `SeekDrag` is fired continuously while the user drags the slider thumb so
 * the bar can show a live `mm:ss · v.N` preview. `SeekCommit` is fired on
 * release; the controller decides whether to snap to a verse boundary or
 * issue a plain seek (depending on whether timing is available).
 */
sealed interface AudioBarCommand {
    data object PlayPause : AudioBarCommand
    data object PrevVerse : AudioBarCommand
    data object NextVerse : AudioBarCommand
    data object PrevChapter : AudioBarCommand
    data object NextChapter : AudioBarCommand
    data object Close : AudioBarCommand
    data object Speed : AudioBarCommand
    data class SeekDrag(val positionMs: Long) : AudioBarCommand
    data class SeekCommit(val positionMs: Long) : AudioBarCommand
}

/**
 * Top-level audio bar surface. Anchored at the bottom of `IsiActivity`'s
 * [androidx.compose.ui.platform.ComposeView] host. Visibility is animated so
 * the bar slides in/out instead of popping; the `enter`/`exit` transitions
 * combine a vertical slide with a fade so the bar's elevation shadow doesn't
 * appear instantly above the chapter list.
 */
@Composable
fun AudioBar(
    state: AudioBarUiState,
    onCommand: (AudioBarCommand) -> Unit,
    modifier: Modifier,
) {
    AudioTheme {
        AnimatedVisibility(
            visible = state.visible,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(220)) +
                fadeIn(animationSpec = tween(220)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180)) +
                fadeOut(animationSpec = tween(180)),
            modifier = modifier,
        ) {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    AudioBarTopRow(state = state, onCommand = onCommand)
                    AudioBarSliderRow(state = state, onCommand = onCommand)
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
        ChapterNavButton(
            label = state.prevChapterLabel,
            descriptionRes = R.string.audio_bar_prev_chapter,
            iconRes = R.drawable.ic_audio_skip_previous,
            iconLeft = true,
            onClick = { onCommand(AudioBarCommand.PrevChapter) },
        )

        IconButton(
            onClick = { onCommand(AudioBarCommand.PrevVerse) },
            enabled = state.timingAvailable,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_audio_keyboard_arrow_left),
                contentDescription = stringResource(R.string.audio_bar_prev_verse),
            )
        }

        Spacer(Modifier.width(4.dp))
        PlayPauseButton(state = state, onCommand = onCommand)
        Spacer(Modifier.width(4.dp))

        IconButton(
            onClick = { onCommand(AudioBarCommand.NextVerse) },
            enabled = state.timingAvailable,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_audio_keyboard_arrow_right),
                contentDescription = stringResource(R.string.audio_bar_next_verse),
            )
        }

        ChapterNavButton(
            label = state.nextChapterLabel,
            descriptionRes = R.string.audio_bar_next_chapter,
            iconRes = R.drawable.ic_audio_skip_next,
            iconLeft = false,
            onClick = { onCommand(AudioBarCommand.NextChapter) },
        )

        Spacer(Modifier.weight(1f))

        // Speed chip — wired but inert in M3 (single 1.0× look). M5 swaps in
        // the speed bottom sheet on tap; for now the click forwards to the
        // controller, which is a no-op.
        Text(
            text = stringResource(R.string.audio_bar_speed_format, state.speed),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .alpha(0.6f)
                .padding(horizontal = 8.dp),
        )

        IconButton(onClick = { onCommand(AudioBarCommand.Close) }) {
            Icon(
                painter = painterResource(R.drawable.ic_audio_close),
                contentDescription = stringResource(R.string.audio_bar_close),
            )
        }
    }
}

@Composable
private fun ChapterNavButton(
    label: String?,
    descriptionRes: Int,
    iconRes: Int,
    iconLeft: Boolean,
    onClick: () -> Unit,
) {
    // `alpha 0` (not GONE / not removed) so the layout doesn't reflow at
    // Bible boundaries when prev/next is unavailable.
    val available = label != null
    val labelText = label ?: ""

    Row(
        modifier = Modifier.alpha(if (available) 1f else 0f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconLeft) {
            IconButton(onClick = onClick, enabled = available) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = stringResource(descriptionRes),
                )
            }
            Text(labelText, style = MaterialTheme.typography.labelMedium)
        } else {
            Text(labelText, style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = onClick, enabled = available) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = stringResource(descriptionRes),
                )
            }
        }
    }
}

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
                onCommand(AudioBarCommand.PlayPause)
            },
            enabled = state.error == null && !state.preparing,
            modifier = Modifier.size(48.dp),
        ) {
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
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }

    val effectivePosition = dragValue?.roundToLong() ?: state.positionMs
    // While dragging we don't yet know the snapped verse — show the live
    // service-reported verse, which lags slightly behind the thumb but is
    // still better than blanking out. This matches the PRD §4.2 description.
    val effectiveVerse = state.verse_1

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

        Slider(
            value = effectivePosition.toFloat(),
            valueRange = 0f..maxOf(state.durationMs.toFloat(), 1f),
            onValueChange = { v ->
                dragValue = v
                onCommand(AudioBarCommand.SeekDrag(v.roundToLong()))
            },
            onValueChangeFinished = {
                val v = dragValue ?: return@Slider
                onCommand(AudioBarCommand.SeekCommit(v.roundToLong()))
                dragValue = null
            },
            enabled = state.durationMs > 0L && state.error == null,
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

        if (state.timingAvailable && effectiveVerse > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "v.${effectiveVerse}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
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
            prevChapterLabel = "Jn 2",
            nextChapterLabel = "Jn 4",
            error = null,
            timingAvailable = true,
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
            prevChapterLabel = "Jn 2",
            nextChapterLabel = "Jn 4",
            error = null,
            timingAvailable = false,
        ),
        onCommand = {},
        modifier = Modifier,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 400, uiMode = 32)
@Composable
private fun AudioBarPreviewDarkBoundary() {
    AudioBar(
        state = AudioBarUiState(
            visible = true,
            isPlaying = false,
            preparing = false,
            positionMs = 12_000L,
            durationMs = 60_000L,
            verse_1 = 0,
            speed = 1.0f,
            prevChapterLabel = null, // Bible boundary — invisible-not-gone
            nextChapterLabel = "Mt 1",
            error = null,
            timingAvailable = false, // some chapters have audio but no timing
        ),
        onCommand = {},
        modifier = Modifier,
    )
}
