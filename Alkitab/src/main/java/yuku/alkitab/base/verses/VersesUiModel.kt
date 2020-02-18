package yuku.alkitab.base.verses

data class VersesUiModel(
    val textSizeMult: Float,
    val verseSelectionMode: VersesController.VerseSelectionMode,
    val isVerseNumberShown: Boolean,
    val dictionaryModeAris: Set<Int>
) {
    companion object {
        @JvmField
        val EMPTY = VersesUiModel(
            textSizeMult = 1f,
            verseSelectionMode = VersesController.VerseSelectionMode.multiple,
            isVerseNumberShown = false,
            dictionaryModeAris = emptySet()
        )
    }
}
