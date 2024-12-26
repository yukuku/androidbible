package yuku.alkitab.base.util

import yuku.alkitab.util.IntArrayList

fun IntArrayList.toIntArray(): IntArray {
    val size = size()
    val result = IntArray(size)
    val buf = buffer()
    buf.copyInto(result, 0, 0, size)
    return result
}
