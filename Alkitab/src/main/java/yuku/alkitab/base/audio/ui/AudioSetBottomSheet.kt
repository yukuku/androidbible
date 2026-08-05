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
 * Recording picker for versions with more than one audio set — the same
 * pattern and placement as [SpeedBottomSheet]. Single selection; sets not
 * covering the current book are listed but disabled, with the reason shown,
 * so the list doesn't appear to change size as the user moves through the
 * Bible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSetBottomSheet(
    options: List<AudioSetOption>,
    onSelect: (audioId: String) -> Unit,
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
            options.forEach { option ->
                AudioSetRow(option = option, onSelect = onSelect)
            }
        }
    }
}

@Composable
private fun AudioSetRow(
    option: AudioSetOption,
    onSelect: (audioId: String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = option.coversCurrentBook) { onSelect(option.audioId) }
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
