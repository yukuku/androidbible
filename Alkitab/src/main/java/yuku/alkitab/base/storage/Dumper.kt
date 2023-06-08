package yuku.alkitab.base.storage

import yuku.alkitab.base.util.AppLog

fun dumpBytes(b: ByteArray) {
    for ((index, chunk) in b.toList().chunked(16).withIndex()) {
        val line = buildString {
            append(chunk.joinToString(" ") { "%02x".format(it.toUByte().toInt()) })
            append("    ")
            append(chunk.joinToString("") { "%s".format(if (it in 0x20..0x7e) it.toInt().toChar() else '.') })
        }
        AppLog.d("Dumper", "$index: $line")
    }
}
