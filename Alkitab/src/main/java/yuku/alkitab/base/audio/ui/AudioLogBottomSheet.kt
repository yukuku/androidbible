package yuku.alkitab.base.audio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import yuku.alkitab.base.audio.AudioLogEntry
import yuku.alkitab.debug.R

private val LOG_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

/**
 * Full timestamped log behind the audio bar's status line: every HTTP
 * connection-state transition, player state transition, and user interaction
 * recorded for the chapter currently (or most recently) loading. See
 * [AudioLogEntry] and [yuku.alkitab.base.audio.PlaybackState.logs]. Opened by
 * tapping the status line, which only ever surfaces the single latest entry.
 *
 * The sheet opens partially expanded and can be dragged out to full screen: a
 * stuck load can accumulate hundreds of entries, and diagnosing one means
 * reading a long run of them at once. The log list therefore takes the sheet's
 * remaining height rather than a fixed cap, so dragging up actually reveals
 * more rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLogBottomSheet(
    logs: List<AudioLogEntry>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = stringResource(R.string.audio_bar_log_sheet_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(logs) { entry -> AudioLogEntryRow(entry) }
                }
            }
        }
    }
}

/**
 * The timestamp column takes its intrinsic width. [LOG_TIMESTAMP_FORMATTER]
 * is fixed-width and the font is monospace, so every row's timestamp measures
 * identically and the message column lines up anyway; a hard-coded dp width
 * would instead clip or wrap the timestamp under a wider locale digit or a
 * font change.
 */
@Composable
private fun AudioLogEntryRow(entry: AudioLogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = formatLogTimestamp(entry.timestampMs),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = LocalContentColor.current.copy(alpha = 0.6f),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

internal fun formatLogTimestamp(timestampMs: Long): String =
    LOG_TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()))
