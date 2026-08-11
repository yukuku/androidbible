package yuku.alkitab.base.audio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
 * connection-state transition and player state transition recorded for the
 * chapter currently (or most recently) loading — see [AudioLogEntry] and
 * [yuku.alkitab.base.audio.PlaybackState.logs]. Opened by tapping the status
 * line, which only ever surfaces the single latest entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLogBottomSheet(
    logs: List<AudioLogEntry>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.audio_bar_log_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (logs.isEmpty()) {
                Text(
                    text = stringResource(R.string.audio_bar_log_sheet_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(logs) { entry -> AudioLogEntryRow(entry) }
                }
            }
        }
    }
}

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
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

internal fun formatLogTimestamp(timestampMs: Long): String =
    LOG_TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()))
