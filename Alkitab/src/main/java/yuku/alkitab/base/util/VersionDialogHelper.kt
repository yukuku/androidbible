package yuku.alkitab.base.util

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import yuku.alkitab.base.S
import yuku.alkitab.base.compose.ComposeBottomSheetHost
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.services.VersionManager
import yuku.alkitab.debug.R
import yuku.alkitab.versionmanager.VersionsActivity

/**
 * UI helpers for letting the user pick a Bible version. Previously lived on
 * [S]; moved here as part of REM-24 so the service locator is purely
 * non-UI state. The [VersionManager] is injected so callers can substitute
 * a fake in tests instead of relying on [S].
 */
object VersionDialogHelper {
    fun openVersionsDialog(activity: Activity, versionManager: VersionManager, selectedVersionId: String, onVersionSelected: (MVersion) -> Unit) {
        val versions = versionManager.getAvailableVersions()

        // determine the currently selected one
        val selected = versions.indexOfFirst { it.versionId == selectedVersionId }

        val rows = versions.map { mv -> mv.toRow { onVersionSelected(mv) } }
        showVersionListSheet(activity, rows, selected)
    }

    fun openVersionsDialogWithNone(activity: Activity, versionManager: VersionManager, selectedVersionId: String?, onVersionSelected: (MVersion?) -> Unit) {
        val versions = versionManager.getAvailableVersions()

        // determine the currently selected one
        val selected = if (selectedVersionId == null) {
            0 // "none"
        } else {
            versions.indexOfFirst { it.versionId == selectedVersionId } + 1
        }

        val noneRow = VersionRow(primary = activity.getString(R.string.split_version_none), secondary = null, onClick = { onVersionSelected(null) })
        val rows = listOf(noneRow) + versions.map { mv -> mv.toRow { onVersionSelected(mv) } }
        showVersionListSheet(activity, rows, selected)
    }

    private fun showVersionListSheet(activity: Activity, rows: List<VersionRow>, selected: Int) {
        ComposeBottomSheetHost.show(activity) { dismiss ->
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
) {
    val listState = rememberLazyListState()

    // A LazyColumn starts scrolled to the top already, which is what we want, unless the
    // checked item wouldn't be on screen there — then bring it into view instead.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex > 0) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                .filter { it.isNotEmpty() }
                .first()
            val lastVisible = listState.layoutInfo.visibleItemsInfo.last().index
            if (selectedIndex > lastVisible) {
                listState.scrollToItem(selectedIndex)
            }
        }
    }

    Column(modifier = Modifier.navigationBarsPadding()) {
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
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onManageVersions) {
                Text(stringResource(R.string.versi_lainnya))
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
            Text(text = primary, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
