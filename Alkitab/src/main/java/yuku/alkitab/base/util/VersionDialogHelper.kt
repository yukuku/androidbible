package yuku.alkitab.base.util

import android.app.Activity
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import yuku.alkitab.base.S
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

        val options: Array<CharSequence> = versions.map { formatVersionLabel(it) }.toTypedArray()
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

    fun openVersionsDialogWithNone(activity: Activity, versionManager: VersionManager, selectedVersionId: String?, onVersionSelected: (MVersion?) -> Unit) {
        val versions = versionManager.getAvailableVersions()

        // determine the currently selected one
        val selected = if (selectedVersionId == null) {
            0 // "none"
        } else {
            versions.indexOfFirst { it.versionId == selectedVersionId } + 1
        }

        val options: Array<CharSequence> = (listOf<CharSequence>(activity.getString(R.string.split_version_none)) + versions.map { formatVersionLabel(it) }).toTypedArray()
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

    /**
     * Builds a two-line label for a version row: shortName (bold) above
     * longName (smaller, muted). Falls back to longName alone when the
     * version has no shortName.
     */
    private fun formatVersionLabel(mv: MVersion): CharSequence {
        val shortName = mv.shortName
        if (shortName.isNullOrBlank()) return mv.longName

        val sb = SpannableStringBuilder()
        val shortStart = sb.length
        sb.append(shortName)
        sb.setSpan(StyleSpan(Typeface.BOLD), shortStart, sb.length, 0)
        sb.append("\n")
        val longStart = sb.length
        sb.append(mv.longName)
        sb.setSpan(RelativeSizeSpan(0.92f), longStart, sb.length, 0)
        sb.setSpan(ForegroundColorSpan(0xff898989.toInt()), longStart, sb.length, 0)
        return sb
    }
}
