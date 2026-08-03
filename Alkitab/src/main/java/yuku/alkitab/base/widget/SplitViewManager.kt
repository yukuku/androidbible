package yuku.alkitab.base.widget

import android.graphics.Point
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.VersionDialogHelper
import yuku.alkitab.base.verses.VersesDataModel
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version

private const val TAG = "SplitViewManager"

/**
 * Master-pane heights that keep the stacked split's handle clear of the
 * system's mandatory gesture areas at the window top and bottom.
 *
 * Touches starting inside those areas are claimed by the system (home / app
 * switch, notification shade). Unlike the back-gesture areas along the left
 * and right edges, they cannot be excluded, so a handle parked there could be
 * pressed but never dragged back.
 */
internal fun masterHeightRange(rootHeight: Int, handleThickness: Int, mandatoryInsetTop: Int, mandatoryInsetBottom: Int): IntRange {
    val max = (rootHeight - handleThickness - mandatoryInsetBottom).coerceAtLeast(0)
    return mandatoryInsetTop.coerceAtMost(max)..max
}

/**
 * Container for the secondary ("split 1") version metadata. Kept as a single
 * data class so the three fields update atomically (matches the pattern used
 * by `ActiveSplit0` on `IsiActivity`).
 */
data class ActiveSplit1(
    val mv: MVersion,
    val version: Version,
    val versionId: String,
)

/**
 * Split-view (a.k.a. "compare versions side-by-side") manager extracted from
 * `IsiActivity` — see REM-08 in docs/tech-debt-remediation.md.
 *
 * Owns the secondary version state ([activeSplit1]) and drives the split-pane
 * UI: open/close transitions, the split handle drag and label buttons, master
 * → split chapter following, and persistence of the split version + orientation
 * + proportion across app restarts.
 *
 * The manager keeps no Activity references: it reads view + state through
 * [host] and triggers Activity operations through [actions].
 */
class SplitViewManager(
    private val host: SplitViewHost,
    private val actions: SplitViewActions,
) {
    /**
     * The secondary version. Set to null when split view is closed, non-null
     * while open. Owned by the manager — `IsiActivity` exposes a read-only
     * accessor that delegates here.
     */
    var activeSplit1: ActiveSplit1? = null
        private set

    // --- View listeners (registered via installListeners) ---

    private val splitRoot_globalLayout = object : ViewTreeObserver.OnGlobalLayoutListener {
        val lastSize = Point()

        override fun onGlobalLayout() {
            val splitRoot = host.splitRoot
            if (lastSize.x == splitRoot.width && lastSize.y == splitRoot.height) {
                return // no need to layout now
            }

            if (activeSplit1 == null) {
                return // we are not splitting
            }

            configureSplitSizes()

            lastSize.x = splitRoot.width
            lastSize.y = splitRoot.height
        }
    }

    private val splitHandleButton_listener = object : SplitHandleButton.SplitHandleButtonListener {
        var first = 0
        var handle = 0
        var root = 0
        var prop = 0f // proportion from top or left

        override fun onHandleDragStart() {
            val splitRoot = host.splitRoot
            val splitHandleButton = host.splitHandleButton
            splitRoot.setOnefingerEnabled(false)

            if (splitHandleButton.orientation == SplitHandleButton.Orientation.vertical) {
                first = splitHandleButton.top
                handle = splitHandleButton.height
                root = splitRoot.height
            } else {
                first = splitHandleButton.left
                handle = splitHandleButton.width
                root = splitRoot.width
            }

            prop = Float.MIN_VALUE // guard against glitches
        }

        override fun onHandleDragMoveX(dxSinceLast: Float, dxSinceStart: Float) {
            val newW = (first + dxSinceStart).toInt()
            val maxW = root - handle
            val width = if (newW < 0) 0 else if (newW > maxW) maxW else newW
            host.lsSplit0.setViewLayoutSize(width, ViewGroup.LayoutParams.MATCH_PARENT)
            prop = width.toFloat() / maxW
        }

        override fun onHandleDragMoveY(dySinceLast: Float, dySinceStart: Float) {
            val newH = (first + dySinceStart).toInt()
            val maxH = root - handle
            val height = newH.coerceIn(masterHeightRange(root, handle))
            host.lsSplit0.setViewLayoutSize(ViewGroup.LayoutParams.MATCH_PARENT, height)
            prop = if (maxH > 0) height.toFloat() / maxH else 0f
        }

        override fun onHandleDragStop() {
            host.splitRoot.setOnefingerEnabled(true)

            if (prop != Float.MIN_VALUE) {
                Preferences.setFloat(Prefkey.lastSplitProp, prop)
            }
        }
    }

    private val splitHandleButton_labelPressed = LabeledSplitHandleButton.ButtonPressListener { which ->
        when (which) {
            LabeledSplitHandleButton.Button.rotate -> {
                closeSplitDisplay()
                openSplitDisplay()
            }

            LabeledSplitHandleButton.Button.start -> actions.openPrimaryVersionsDialog()
            LabeledSplitHandleButton.Button.end -> openSplitVersionsDialog()
            else -> throw IllegalStateException("should not happen")
        }
    }

    /** Registers the global layout, drag, and label listeners on the split-view widgets. */
    fun installListeners() {
        host.splitRoot.viewTreeObserver.addOnGlobalLayoutListener(splitRoot_globalLayout)
        host.splitHandleButton.setListener(splitHandleButton_listener)
        host.splitHandleButton.setButtonPressListener(splitHandleButton_labelPressed)
    }

    // --- Persistence ---

    /**
     * Restore the split version + orientation from preferences and, if present,
     * open the split pane scrolled to [verse_1]. Called from `IsiActivity.onCreate`
     * after the master version has been displayed.
     */
    fun restoreFromPreferences(verse_1: Int) {
        val lastSplitVersionId = Preferences.getString(Prefkey.lastSplitVersionId, null) ?: return

        val splitOrientation = Preferences.getString(Prefkey.lastSplitOrientation)
        host.splitHandleButton.orientation = if (SplitHandleButton.Orientation.horizontal.name == splitOrientation) {
            SplitHandleButton.Orientation.horizontal
        } else {
            SplitHandleButton.Orientation.vertical
        }

        val splitMv = App.services.versions.getVersionFromVersionId(lastSplitVersionId)
        val splitMvActual = splitMv ?: App.services.versions.getMVersionInternal()

        if (loadSplitVersion(splitMvActual)) {
            openSplitDisplay()
            displaySplitFollowingMaster(verse_1)
        }
    }

    /**
     * Persist the current split version + orientation (or clear it if the pane
     * is closed). Called from `IsiActivity.onStop`.
     */
    fun saveToPreferences() {
        val activeSplit1 = activeSplit1
        if (activeSplit1 == null) {
            Preferences.remove(Prefkey.lastSplitVersionId)
        } else {
            Preferences.setString(Prefkey.lastSplitVersionId, activeSplit1.versionId)
            Preferences.setString(Prefkey.lastSplitOrientation, host.splitHandleButton.orientation.name)
        }
    }

    // --- Public API ---

    /**
     * Prompt the user to pick a secondary version (or "None" to close split).
     * Called from the left drawer "split version" toggle and the split-handle
     * "end" label tap.
     */
    fun openSplitVersionsDialog() {
        VersionDialogHelper.openVersionsDialogWithNone(host.activity, App.services.versions, activeSplit1?.versionId) { mv: MVersion? ->
            if (mv == null) { // closing split version
                disableSplitVersion()
            } else {
                val ok = loadSplitVersion(mv)
                if (ok) {
                    openSplitDisplay()
                    displaySplitFollowingMaster(host.getVerse_1BasedOnScrolls())
                } else {
                    disableSplitVersion()
                }
            }

            // We may need to apply PerVersion settings.
            actions.applyPreferences()
        }
    }

    /** Close the split pane and forget the secondary version. */
    fun disableSplitVersion() {
        activeSplit1 = null
        closeSplitDisplay()

        configureTextAppearancePanelForSplitVersion()
    }

    /**
     * Mirror the master pane's chapter into the split pane, scrolled to [verse_1].
     * No-op when the split pane is closed. If the secondary version doesn't have
     * the master book, the pane is cleared and an explanatory message is shown.
     */
    fun displaySplitFollowingMaster(verse_1: Int) {
        val activeSplit1 = activeSplit1 ?: return

        val splitBook = activeSplit1.version.getBook(host.activeSplit0Book.bookId)
        if (splitBook == null) {
            host.lsSplit1.setEmptyMessage(
                host.activity.getString(
                    R.string.split_version_cant_display_verse,
                    host.activeSplit0Book.reference(host.chapter_1),
                    activeSplit1.version.shortName,
                ),
                App.services.uiDimensions.applied().fontColor,
            )
            actions.setSplit1DataModel(VersesDataModel.EMPTY)
        } else {
            host.lsSplit1.setEmptyMessage(null, App.services.uiDimensions.applied().fontColor)
            actions.loadChapterIntoSplit1(activeSplit1.version, activeSplit1.versionId, splitBook, host.chapter_1)
            host.lsSplit1.scrollToVerse(verse_1)
        }
    }

    /**
     * Push the current split version into the text-appearance panel (or clear
     * it when split is closed). Called by `IsiActivity` when the panel opens
     * and by [loadSplitVersion] / [disableSplitVersion] when split state changes.
     */
    fun configureTextAppearancePanelForSplitVersion() {
        val textAppearancePanel = host.textAppearancePanel ?: return
        val activeSplit1 = activeSplit1
        if (activeSplit1 == null) {
            textAppearancePanel.clearSplitVersion()
        } else {
            textAppearancePanel.setSplitVersion(activeSplit1.versionId, activeSplit1.version.longName)
        }
    }

    // --- Internal ---

    /** Loads [mv] as the split version (no UI side effects beyond updating panel + handle label). */
    private fun loadSplitVersion(mv: MVersion): Boolean {
        try {
            val version = mv.version ?: throw RuntimeException() // caught below

            activeSplit1 = ActiveSplit1(mv, version, mv.versionId)

            host.splitHandleButton.setLabel2("${version.initials} \u25bc")

            configureTextAppearancePanelForSplitVersion()

            return true
        } catch (e: Throwable) { // so we don't crash on the beginning of the app
            AppLog.e(TAG, "Error opening split version", e)

            MaterialAlertDialogBuilder(host.activity)
                .setMessage(host.activity.getString(R.string.version_error_opening, mv.longName))
                .setPositiveButton(R.string.ok, null)
                .show()

            return false
        }
    }

    private fun openSplitDisplay() {
        if (host.splitHandleButton.isVisible) {
            return // it's already split, no need to do anything
        }

        configureSplitSizes()

        host.bVersion.visibility = View.GONE
        host.actionMode?.invalidate()
        host.leftDrawer.handle.setSplitVersion(true)
    }

    private fun configureSplitSizes() {
        val splitHandleButton = host.splitHandleButton
        val splitRoot = host.splitRoot

        splitHandleButton.visibility = View.VISIBLE

        var prop = Preferences.getFloat(Prefkey.lastSplitProp, Float.MIN_VALUE)
        if (prop == Float.MIN_VALUE || prop < 0f || prop > 1f) {
            prop = 0.5f // guard against invalid values
        }

        val splitHandleThickness = host.activity.resources.getDimensionPixelSize(R.dimen.split_handle_thickness)
        if (splitHandleButton.orientation == SplitHandleButton.Orientation.vertical) {
            splitRoot.orientation = LinearLayout.VERTICAL

            val totalHeight = splitRoot.height
            // splitRoot may not have been laid out yet (height 0, e.g. when
            // the split is restored during activity creation). A negative pane
            // height measures as an UNSPECIFIED (infinite) constraint; the
            // global-layout listener redistributes the real sizes later.
            val masterHeight = ((totalHeight - splitHandleThickness) * prop).toInt()
                .coerceIn(masterHeightRange(totalHeight, splitHandleThickness))

            run {
                // divide the screen space
                host.lsSplit0.setViewLayoutSize(ViewGroup.LayoutParams.MATCH_PARENT, masterHeight)
            }

            // no need to set height, because it has been set to match_parent, so it takes the remaining space.
            host.lsSplit1.setViewVisibility(View.VISIBLE)

            splitHandleButton.updateLayoutParams {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = splitHandleThickness
            }
        } else {
            splitRoot.orientation = LinearLayout.HORIZONTAL

            val totalWidth = splitRoot.width
            val masterWidth = ((totalWidth - splitHandleThickness) * prop).toInt().coerceAtLeast(0)

            run {
                // divide the screen space
                host.lsSplit0.setViewLayoutSize(masterWidth, ViewGroup.LayoutParams.MATCH_PARENT)
            }

            // no need to set width, because it has been set to match_parent, so it takes the remaining space.
            host.lsSplit1.setViewVisibility(View.VISIBLE)

            splitHandleButton.updateLayoutParams {
                width = splitHandleThickness
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
        }
    }

    private fun masterHeightRange(rootHeight: Int, handleThickness: Int): IntRange {
        val insets = ViewCompat.getRootWindowInsets(host.splitRoot)
            ?.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
            ?: Insets.NONE
        return masterHeightRange(rootHeight, handleThickness, insets.top, insets.bottom)
    }

    private fun closeSplitDisplay() {
        if (host.splitHandleButton.isGone) {
            return // it's already not split, no need to do anything
        }

        host.splitHandleButton.visibility = View.GONE
        host.lsSplit1.setViewVisibility(View.GONE)

        run { host.lsSplit0.setViewLayoutSize(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }

        host.bVersion.visibility = View.VISIBLE
        host.actionMode?.invalidate()
        host.leftDrawer.handle.setSplitVersion(false)
    }
}
