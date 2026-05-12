package yuku.alkitab.base.util

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import yuku.alkitab.base.S
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.debug.R
import yuku.alkitab.versionmanager.VersionsActivity

/**
 * UI helpers for letting the user pick a Bible version. Previously lived on
 * [S]; moved here as part of REM-24 so the service locator is purely
 * non-UI state.
 */
object VersionDialogHelper {
    fun openVersionsDialog(activity: Activity, selectedVersionId: String, onVersionSelected: (MVersion) -> Unit) {
        val versions = S.getAvailableVersions()

        // determine the currently selected one
        val selected = versions.indexOfFirst { it.versionId == selectedVersionId }

        val options = versions.map { it.longName }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setSingleChoiceItems(options, selected) { dialog, index ->
                if (index >= 0) {
                    val mv = versions[index]
                    onVersionSelected(mv)
                    dialog.dismiss()
                }
            }
            .setPositiveButton(R.string.versi_lainnya) { _, _ ->
                activity.startActivity(VersionsActivity.createIntent())
            }
            .show()
    }

    fun openVersionsDialogWithNone(activity: Activity, selectedVersionId: String?, onVersionSelected: (MVersion?) -> Unit) {
        val versions = S.getAvailableVersions()

        // determine the currently selected one
        val selected = if (selectedVersionId == null) {
            0 // "none"
        } else {
            versions.indexOfFirst { it.versionId == selectedVersionId } + 1
        }

        val options = (listOf(activity.getString(R.string.split_version_none)) + versions.map { it.longName }).toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setSingleChoiceItems(options, selected) { dialog, index ->
                when {
                    index == 0 -> onVersionSelected(null)
                    index > 0 -> onVersionSelected(versions[index - 1])
                }
                dialog.dismiss()
            }
            .setPositiveButton(R.string.versi_lainnya) { _, _ ->
                activity.startActivity(VersionsActivity.createIntent())
            }
            .show()
    }
}
