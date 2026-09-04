package yuku.alkitab.base.search.theme

enum class ThemeSearchError {
    PACK_REQUIRED,
    MODEL_ERROR,
}

data class ThemeSearchResponse(
    val aris: List<Int>,
    val elapsedMs: Long,
    val error: ThemeSearchError? = null,
)
