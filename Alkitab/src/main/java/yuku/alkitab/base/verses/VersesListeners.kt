package yuku.alkitab.base.verses

import yuku.alkitab.base.widget.DictionaryLinkInfo
import yuku.alkitab.base.widget.ParallelClickData
import yuku.alkitab.base.widget.VerseInlineLinkSpan

class VersesListeners(
    val attributeListener: VersesController.AttributeListener,
    val selectedVersesListener: VersesController.SelectedVersesListener,
    val onVerseScrollListener: VersesController.OnVerseScrollListener,
    val parallelListener_: (ParallelClickData) -> Unit,
    val inlineLinkSpanFactory_: VerseInlineLinkSpan.Factory,
    val dictionaryListener_: (DictionaryLinkInfo) -> Unit
) {
    companion object {
        @JvmField
        val EMPTY = VersesListeners(
            attributeListener = object : VersesController.AttributeListener() {},
            selectedVersesListener = object : VersesController.SelectedVersesListener() {},
            onVerseScrollListener = object : VersesController.OnVerseScrollListener() {},
            parallelListener_ = {},
            inlineLinkSpanFactory_ = VerseInlineLinkSpan.Factory { type, arif ->
                object : VerseInlineLinkSpan(type, arif, null) {
                }
            },
            dictionaryListener_ = {}
        )
    }
}
