package yuku.alkitab.base.model

data class MVideo(
    val label: String,
    val videoId: String,
    val position: String,
    val title: String
) {
    val book: String
    val chapter: Int?

    init {
        val parts = position.split(" ")
        book = parts.first()
        chapter = parts.getOrNull(1)?.toIntOrNull()
    }
}
