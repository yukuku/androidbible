package yuku.alkitab.base.search.theme

enum class SearchMode {
    Exact,
    ThemeOffline;

    companion object {
        fun restore(value: String?): SearchMode = entries.firstOrNull { it.name == value } ?: Exact
    }
}
