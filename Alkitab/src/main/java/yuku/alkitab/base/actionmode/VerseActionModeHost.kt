package yuku.alkitab.base.actionmode

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.verses.VersesDataModel
import yuku.alkitab.model.Book
import yuku.alkitab.model.Version
import yuku.alkitab.util.IntArrayList

/**
 * Read-only state surface that [VerseActionModeController] reads from to drive the
 * verse-selection action mode. Implemented by the hosting Activity.
 *
 * Property names mirror the Activity's existing `activeSplit0` / `activeSplit1`
 * vocabulary so changes read cleanly against the original code.
 */
interface VerseActionModeHost {
    val activity: AppCompatActivity
    val root: View
    val chapter_1: Int

    // Primary ("split 0") — always present.
    val activeSplit0Book: Book
    val activeSplit0Version: Version
    val activeSplit0VersionId: String
    val activeSplit0MVersion: MVersion

    // Secondary ("split 1") — null when the split view is closed.
    val activeSplit1Version: Version?
    val activeSplit1VersionId: String?
    val activeSplit1MVersion: MVersion?

    /** Looks up the book with the given id in the split-1 version, or null. */
    fun activeSplit1BookById(bookId: Int): Book?

    val selectedVersesSplit0_1: IntArrayList
    val selectedVersesSplit1_1: IntArrayList

    val dataSplit0: VersesDataModel
    val dataSplit1: VersesDataModel

    val hasEsvsbAsal: Boolean

    val hasAlkitabGpt: Boolean

    /**
     * Set to false by the Activity when it wants to keep the checked verses
     * visible after the action mode is destroyed (e.g. during some split-view
     * transitions). The controller reads this in `onDestroyActionMode`.
     */
    var uncheckVersesWhenActionModeDestroyed: Boolean
}
