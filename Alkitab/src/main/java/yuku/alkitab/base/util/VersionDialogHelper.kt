package yuku.alkitab.base.util

import android.app.Activity
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
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

        val secondaryColor = resolveSecondaryTextColor(activity)
        val options: Array<CharSequence> = versions.map { formatVersionLabel(it, secondaryColor) }.toTypedArray()
        val dialog = MaterialAlertDialogBuilder(activity)
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
        scrollToTopIfSelectedFits(dialog, selected)
    }

    fun openVersionsDialogWithNone(activity: Activity, versionManager: VersionManager, selectedVersionId: String?, onVersionSelected: (MVersion?) -> Unit) {
        val versions = versionManager.getAvailableVersions()

        // determine the currently selected one
        val selected = if (selectedVersionId == null) {
            0 // "none"
        } else {
            versions.indexOfFirst { it.versionId == selectedVersionId } + 1
        }

        val secondaryColor = resolveSecondaryTextColor(activity)
        val options: Array<CharSequence> = (listOf<CharSequence>(activity.getString(R.string.split_version_none)) + versions.map { formatVersionLabel(it, secondaryColor) }).toTypedArray()
        val dialog = MaterialAlertDialogBuilder(activity)
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
        scrollToTopIfSelectedFits(dialog, selected)
    }

    /**
     * [MaterialAlertDialogBuilder.setSingleChoiceItems] scrolls the list so the checked item
     * lands as the first visible row. When the checked item is close enough to the top that it
     * would already be on screen with the list scrolled all the way up, that jump is unnecessary
     * and disorienting, so scroll back to the top in that case. If the checked item is far enough
     * down the list that scrolling to the top would hide it, leave the list where the library put it.
     */
    private fun scrollToTopIfSelectedFits(dialog: AlertDialog, selected: Int) {
        if (selected <= 0) return
        val listView: ListView = dialog.listView ?: return
        listView.post {
            listView.setSelectionFromTop(0, 0)
            listView.post {
                if (selected > listView.lastVisiblePosition) {
                    listView.setSelection(selected)
                }
            }
        }
    }

    /**
     * Builds a two-line label for a version row: shortName (bold) above
     * longName (smaller, muted). Falls back to bold longName alone when
     * the version has no shortName, to stay consistent with the version
     * manager row, where longName is promoted into the bold primary slot
     * in the same situation.
     */
    private fun formatVersionLabel(mv: MVersion, secondaryColor: Int): CharSequence {
        val shortName = mv.shortName
        val sb = SpannableStringBuilder()
        if (shortName.isNullOrBlank()) {
            sb.append(mv.longName)
            sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, 0)
            return sb
        }

        sb.append(shortName)
        sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, 0)
        sb.append("\n")
        val longStart = sb.length
        sb.append(mv.longName)
        sb.setSpan(RelativeSizeSpan(0.92f), longStart, sb.length, 0)
        sb.setSpan(ForegroundColorSpan(secondaryColor), longStart, sb.length, 0)
        return sb
    }

    private fun resolveSecondaryTextColor(activity: Activity): Int {
        val ta = activity.theme.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
        return try {
            ta.getColor(0, 0xff898989.toInt())
        } finally {
            ta.recycle()
        }
    }
}
