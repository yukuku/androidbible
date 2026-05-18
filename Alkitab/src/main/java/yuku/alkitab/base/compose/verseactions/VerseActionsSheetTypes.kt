package yuku.alkitab.base.compose.verseactions

import yuku.alkitab.base.actionmode.CopyShareVariant

/**
 * Immutable snapshot of what the Compose verse-selection sheet should render for
 * the current selection. Built by [ComposeVerseActionsController] from the host's
 * live state; the sheet itself stays pure.
 *
 * Mirrors the visibility/enablement rules in
 * `VerseActionModeController.onPrepareActionMode` so the new UI matches the
 * legacy ActionMode menu byte-for-byte at parity.
 */
data class VerseActionsSheetState(
    val reference: String,
    val verseCount: Int,
    val isSingle: Boolean,
    val isContiguous: Boolean,
    val isSplit: Boolean,
    val showCompare: Boolean,
    val showGuide: Boolean,
    val showCommentary: Boolean,
    val showDictionary: Boolean,
    val showRibkaReport: Boolean,
    val showEsvsb: Boolean,
    val extensions: List<ExtensionEntry>,
)

data class ExtensionEntry(
    /** Stable index into `VerseActionModeController.extensions`. */
    val index: Int,
    val label: String,
)

interface VerseActionsSheetCallbacks {
    fun onClose()
    fun onCopy(variant: CopyShareVariant)
    fun onShare(variant: CopyShareVariant)
    fun onCompare()
    fun onAddBookmark()
    fun onAddNote()
    fun onAddHighlight()
    fun onGuide()
    fun onCommentary()
    fun onDictionary()
    fun onRibkaReport()
    fun onEsvsb()
    fun onExtension(index: Int)
}
