package yuku.alkitab.base.verses

import yuku.alkitab.base.widget.VerseInlineLinkSpan

class VersesControllerImpl(
    override var name: String,
    override var verseSelectionMode: VersesController.VerseSelectionMode,
    override var attributeListener: VersesController.AttributeListener,
    override var listener: VersesController.SelectedVersesListener,
    override var onVerseScrollListener: VersesController.OnVerseScrollListener,
    override var parallelListener_: (VersesController.ParallelClickData) -> Unit,
    override var inlineLinkSpanFactory_: VerseInlineLinkSpan.Factory
) : VersesController {

    override val versesDataModel: VersesDataModel
        get() = TODO()
}