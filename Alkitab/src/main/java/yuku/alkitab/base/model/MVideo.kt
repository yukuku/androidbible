package yuku.alkitab.base.model

data class MVideo(
    val title: String,
    val yid: String,
    val position: String
) {
    val book: String
    val chapter: Int?

    init {
        val parts = position.split(" ")
        book = parts.first()
        chapter = parts.getOrNull(1)?.toIntOrNull()
    }
}
