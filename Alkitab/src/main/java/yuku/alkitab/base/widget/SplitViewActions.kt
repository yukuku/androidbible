package yuku.alkitab.base.widget

import yuku.alkitab.base.verses.VersesDataModel
import yuku.alkitab.model.Book
import yuku.alkitab.model.Version

/**
 * Write-side surface that [SplitViewManager] invokes to trigger Activity
 * operations when split state changes. Implemented by the hosting Activity.
 */
interface SplitViewActions {
    /** Re-apply preferences after a split change (PerVersion settings, padding etc.). */
    fun applyPreferences()

    /** Open the primary version chooser (used by the "start" label on the split handle button). */
    fun openPrimaryVersionsDialog()

    /**
     * Load a chapter of [book] from [version] into the split-1 verses controller.
     * The Activity wraps the underlying loader with the action-mode suppression
     * flag (so checked verses aren't dropped when split-1 reloads). Returns
     * true on success.
     */
    fun loadChapterIntoSplit1(version: Version, versionId: String, book: Book, chapter_1: Int): Boolean

    /** Replace split-1's data model — used to clear the pane when the split version can't display the current book. */
    fun setSplit1DataModel(model: VersesDataModel)
}
