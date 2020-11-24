package yuku.alkitab.datatransfer.model

data class Rpp(
    val gid: Gid,
    val startTime: Long,
    val done: List<Int>,
)
