package yuku.alkitab.base.audio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import yuku.alkitab.debug.R

/**
 * Recording picker, using the same pattern and placement as [SpeedBottomSheet].
 * Single selection across the whole sheet; sets not covering the current book
 * are listed but disabled, with the reason shown, so the list doesn't appear to
 * change size as the user moves through the Bible.
 *
 * In split view with audio on both sides, one group is shown per version
 * (headed by the version's short name), and picking a row from the other
 * version's group moves the audio source to that version as well as selecting
 * the recording.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSetBottomSheet(
    groups: List<AudioSetGroup>,
    onSelect: (versionId: String, audioId: String) -> Unit,
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
                text = stringResource(R.string.audio_bar_set_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            groups.forEach { group ->
                // A single group needs no header: the version is implied.
                if (groups.size > 1) {
                    Text(
                        text = group.versionName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                group.options.forEach { option ->
                    AudioSetRow(option = option, onSelect = { onSelect(group.versionId, option.audioId) })
                }
            }
        }
    }
}

@Composable
private fun AudioSetRow(
    option: AudioSetOption,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = option.coversCurrentBook, onClick = onSelect)
            .padding(vertical = 8.dp)
            .alpha(if (option.coversCurrentBook) 1f else 0.5f),
    ) {
        RadioButton(
            selected = option.selected,
            // The whole row is the touch target; the radio only renders state.
            onClick = null,
            enabled = option.coversCurrentBook,
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!option.coversCurrentBook) {
                Text(
                    text = stringResource(R.string.audio_bar_set_not_available_for_book),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
