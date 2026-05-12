package yuku.alkitab.base.widget

import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import yuku.alkitab.base.verses.VersesController
import yuku.alkitab.model.Book
import yuku.alkitab.model.Version

/**
 * Read-only state surface that [SplitViewManager] reads from to drive the
 * split-pane UI. Implemented by the hosting Activity.
 *
 * The split manager is allowed to read the master ("split 0") state to know
 * which book/chapter to mirror in the secondary pane and which book to look up
 * in the secondary version when "split follows master" runs.
 */
interface SplitViewHost {
    val activity: AppCompatActivity
    val chapter_1: Int
    val activeSplit0Book: Book
    val activeSplit0Version: Version

    // --- Split-pane view refs ---
    val splitRoot: TwofingerLinearLayout
    val splitHandleButton: LabeledSplitHandleButton
    val lsSplit0: VersesController
    val lsSplit1: VersesController

    /** Toolbar version-name label — hidden while the split pane is open. */
    val bVersion: TextView

    /** Left drawer; the manager flips `handle.setSplitVersion(...)` on open/close. */
    val leftDrawer: LeftDrawer.Text

    val textAppearancePanel: TextAppearancePanel?

    /** Action mode is invalidated by the manager when the split state changes. */
    val actionMode: ActionMode?

    /** Anchor verse used by `openSplitVersionsDialog` after a new split version is loaded. */
    fun getVerse_1BasedOnScrolls(): Int
}
