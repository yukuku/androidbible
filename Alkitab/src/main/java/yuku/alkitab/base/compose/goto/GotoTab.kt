package yuku.alkitab.base.compose.goto

enum class GotoTab(val ordinal1Based: Int) {
    DIALER(1),
    DIRECT(2),
    GRID(3),
}

typealias OnGotoFinished = (tab: GotoTab, bookId: Int, chapter_1: Int, verse_1: Int) -> Unit
