package yuku.alkitab.base.verses

private const val TAG = "VersesUiModel"

class VersesUiModel(
    val textSizeMult: Float
) {
    companion object {
        @JvmField
        val EMPTY = VersesUiModel(
            textSizeMult = 1f
        )
    }
}
