package yuku.alkitab.datatransfer.process


fun interface LogInterface {
    fun log(line: String)

    fun logger(prefix: String): (String) -> Unit {
        return { line -> log("[$prefix] $line") }
    }
}
