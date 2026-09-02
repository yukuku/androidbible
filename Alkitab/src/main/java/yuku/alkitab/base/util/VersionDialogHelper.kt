package yuku.alkitab.base.util

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import yuku.alkitab.base.S
import yuku.alkitab.base.compose.ComposeBottomSheetHost
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.services.VersionManager
import yuku.alkitab.debug.R
import yuku.alkitab.versionmanager.VersionsActivity

/**
 * UI helpers for letting the user pick a Bible version. The [VersionManager] is injected so
 * callers can substitute a fake in tests instead of relying on [S].
 */
object VersionDialogHelper {
    fun openVersionsDialog(activity: Activity, versionManager: VersionManager, selectedVersionId: String, onVersionSelected: (MVersion) -> Unit) {
        val versions = versionManager.getAvailableVersions()
        val selected = versions.indexOfFirst { it.versionId == selectedVersionId }

        val rows = versions.map { mv -> mv.toRow { onVersionSelected(mv) } }
        showVersionListSheet(activity, rows, selected)
    }

    fun openVersionsDialogWithNone(activity: Activity, versionManager: VersionManager, selectedVersionId: String?, onVersionSelected: (MVersion?) -> Unit) {
        val versions = versionManager.getAvailableVersions()
        val selected = selectedVersionId?.let { id -> versions.indexOfFirst { it.versionId == id } } ?: -1

        // Nothing to close when the split isn't currently open.
        val onClose: (() -> Unit)? = if (selectedVersionId != null) {
            { onVersionSelected(null) }
        } else {
            null
        }

        val rows = versions.map { mv -> mv.toRow { onVersionSelected(mv) } }
        showVersionListSheet(activity, rows, selected, onClose = onClose)
    }

    private fun showVersionListSheet(activity: Activity, rows: List<VersionRow>, selected: Int, onClose: (() -> Unit)? = null) {
        // Gestures are off because a fling that runs the list past its bounds leaks residual
        // motion into the sheet's own drag handling, briefly expanding it before it springs back.
        ComposeBottomSheetHost.show(activity, sheetGesturesEnabled = false) { dismiss ->
            VersionListSheetContent(
                rows = rows,
                selectedIndex = selected,
                onRowClick = { row ->
                    row.onClick()
                    dismiss()
                },
                onManageVersions = {
                    activity.startActivity(VersionsActivity.createIntent())
                    dismiss()
                },
                onClose = onClose?.let { close -> { close(); dismiss() } },
            )
        }
    }

    /**
     * Builds a row for a version: shortName (bold) as the primary line, with longName as a
     * smaller, muted secondary line. Falls back to bold longName alone when the version has no
     * shortName, to stay consistent with the version manager row, where longName is promoted
     * into the bold primary slot in the same situation.
     */
    private fun MVersion.toRow(onClick: () -> Unit): VersionRow {
        val shortName = shortName?.takeIf { it.isNotBlank() }
        return VersionRow(
            primary = shortName ?: longName,
            secondary = shortName?.let { longName },
            onClick = onClick,
        )
    }
}

private class VersionRow(val primary: String, val secondary: String?, val onClick: () -> Unit)

@Composable
private fun VersionListSheetContent(
    rows: List<VersionRow>,
    selectedIndex: Int,
    onRowClick: (VersionRow) -> Unit,
    onManageVersions: () -> Unit,
    onClose: (() -> Unit)?,
) {
    val listState = rememberLazyListState()

    // A LazyColumn starts scrolled to the top already, which is what we want, unless the checked
    // item wouldn't be fully on screen there, in which case bring it into view instead. layoutInfo
    // counts an item as visible even when only a sliver of it pokes into the viewport, so the
    // check requires full visibility to avoid leaving the checked item clipped at the bottom edge.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex > 0) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                .filter { it.isNotEmpty() }
                .first()
            val viewportEnd = listState.layoutInfo.viewportEndOffset
            val lastFullyVisible = listState.layoutInfo.visibleItemsInfo
                .lastOrNull { it.offset + it.size <= viewportEnd }
                ?.index
                ?: -1
            if (selectedIndex > lastFullyVisible) {
                listState.scrollToItem(selectedIndex)
            }
        }
    }

    // Cap the sheet height so its rounded top stays a bit below the status bar (a
    // ModalBottomSheet's expanded height otherwise reaches right up to it), matching the
    // song search sheet. Shorter lists still wrap to their content instead of stretching.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val sheetHeight = maxHeight - 48.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = sheetHeight)
                .navigationBarsPadding(),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 8.dp),
                modifier = Modifier.weight(weight = 1f, fill = false),
            ) {
                itemsIndexed(rows) { index, row ->
                    VersionRowItem(
                        primary = row.primary,
                        secondary = row.secondary,
                        selected = index == selectedIndex,
                        onClick = { onRowClick(row) },
                    )
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = if (onClose != null) Arrangement.SpaceBetween else Arrangement.End,
            ) {
                if (onClose != null) {
                    TextButton(onClick = onClose) {
                        Text(stringResource(R.string.split_version_none))
                    }
                }
                TextButton(onClick = onManageVersions) {
                    Text(stringResource(R.string.versi_lainnya))
                }
            }
        }
    }
}

@Composable
private fun VersionRowItem(
    primary: String,
    secondary: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            // Matches shortName/longName sizing in the version manager's item_version.xml
            // (?android:textAppearanceMedium resolves to 18sp; longName is a plain 13sp).
            Text(text = primary, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (secondary != null) {
                Text(
                    text = secondary,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
