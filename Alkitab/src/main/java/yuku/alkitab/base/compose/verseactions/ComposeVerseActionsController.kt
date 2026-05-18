package yuku.alkitab.base.compose.verseactions

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import yuku.alkitab.base.actionmode.CopyShareVariant
import yuku.alkitab.base.actionmode.RibkaEligibility
import yuku.alkitab.base.actionmode.VerseActionModeController
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.base.config.AppConfig
import yuku.alkitab.base.util.VerseTextFormatter
import yuku.alkitab.debug.R

/**
 * Compose-backed replacement for the legacy [androidx.appcompat.view.ActionMode]
 * verse-selection toolbar, gated by `pref_useComposeVerseActions`.
 *
 * The controller owns a [ComposeView] attached to the activity's
 * `R.id.overlayContainer` and renders a [VerseActionsSheet] at the bottom of the
 * screen. All actual side effects (copy, share, dialogs, intents) are delegated
 * back to [VerseActionModeController]'s extracted `handle…` methods so the two
 * code paths stay in lockstep.
 */
class ComposeVerseActionsController(
    private val controller: VerseActionModeController,
) {
    private var composeView: ComposeView? = null
    private var visibleState by mutableStateOf(false)
    private var sheetState by mutableStateOf<VerseActionsSheetState?>(null)

    private val host get() = controller.host
    private val actions get() = controller.actions

    /** True when the sheet is requesting to be visible (animation may still be running). */
    val isShowing: Boolean get() = visibleState

    /**
     * Re-compute state from the current selection and (re-)show the sheet. Safe
     * to call repeatedly — `mutableStateOf` deduplicates equal values.
     */
    fun show() {
        attachIfNeeded()
        controller.refreshExtensions()
        val next = computeState() ?: run {
            visibleState = false
            return
        }
        sheetState = next
        visibleState = true
    }

    /** Slide the sheet out. Verse selection itself is the caller's responsibility. */
    fun hide() {
        visibleState = false
    }

    /** Tears down the ComposeView. Call from `onDestroy`. */
    fun detach() {
        composeView?.let { cv ->
            (cv.parent as? ViewGroup)?.removeView(cv)
        }
        composeView = null
    }

    private fun attachIfNeeded() {
        if (composeView != null) return
        val parent = host.activity.findViewById<ViewGroup>(R.id.overlayContainer) ?: return
        val cv = ComposeView(host.activity).apply {
            setContent {
                BibleAppTheme {
                    VerseActionsSheet(
                        visible = visibleState,
                        state = sheetState,
                        callbacks = callbacks,
                    )
                }
            }
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        )
        parent.addView(cv, lp)
        composeView = cv
    }

    private fun computeState(): VerseActionsSheetState? {
        val selected = host.selectedVersesSplit0_1
        if (selected.size() == 0) return null

        val isSingle = selected.size() == 1
        val isContiguous = VerseActionModeController.isContiguous(selected)
        val isSplit = host.activeSplit1Version != null
        val reference = VerseTextFormatter
            .referenceFromSelectedVerses(selected, host.activeSplit0Book, host.chapter_1)

        val cfg = AppConfig.get()
        val autoDictionaryOn = controller.isAutoDictionaryOn()

        val ribkaEligible = actions.checkRibkaEligibility() != RibkaEligibility.None

        val extensions = controller.extensions.mapIndexedNotNull { index, ext ->
            if (isSingle || ext.supportsMultipleVerses) {
                ExtensionEntry(index = index, label = ext.label.toString())
            } else null
        }

        return VerseActionsSheetState(
            reference = reference,
            verseCount = selected.size(),
            isSingle = isSingle,
            isContiguous = isContiguous,
            isSplit = isSplit,
            showCompare = isSingle,
            showGuide = cfg.menuGuide,
            showCommentary = cfg.menuCommentary,
            showDictionary = cfg.menuDictionary && !autoDictionaryOn,
            showRibkaReport = isSingle && ribkaEligible,
            showEsvsb = host.hasEsvsbAsal,
            extensions = extensions,
        )
    }

    /**
     * Used to dismiss the sheet after an action that consumed the selection
     * (Copy/Share/Add*). Mirrors `onDestroyActionMode`'s uncheck-all behavior so
     * the verses on screen don't stay highlighted in the dark.
     */
    private val finishAndUncheck: () -> Unit = {
        actions.uncheckAllVersesSplit0()
        hide()
    }

    /** Sheet stays open; selection persists. */
    private val stay: () -> Unit = { /* no-op */ }

    private val callbacks: VerseActionsSheetCallbacks = object : VerseActionsSheetCallbacks {
        override fun onClose() {
            // Clearing verses fires `onNoVersesSelected` which calls hide().
            actions.uncheckAllVersesSplit0()
            hide()
        }

        override fun onCopy(variant: CopyShareVariant) {
            controller.handleCopy(variant, finishAndUncheck)
        }

        override fun onShare(variant: CopyShareVariant) {
            controller.handleShare(variant, finishAndUncheck)
        }

        override fun onCompare() = controller.handleCompare(stay)
        override fun onAddBookmark() = controller.handleAddBookmark(finishAndUncheck)
        override fun onAddNote() = controller.handleAddNote(finishAndUncheck)
        override fun onAddHighlight() = controller.handleAddHighlight(finishAndUncheck)
        override fun onGuide() = controller.handleGuide(stay)
        override fun onCommentary() = controller.handleCommentary(stay)
        override fun onDictionary() = controller.handleDictionary(stay)
        override fun onRibkaReport() = controller.handleRibkaReport(stay)
        override fun onEsvsb() = controller.handleEsvsb(stay)
        override fun onExtension(index: Int) {
            val ext = controller.extensions.getOrNull(index) ?: return
            controller.handleExtension(ext, stay)
        }
    }

}
