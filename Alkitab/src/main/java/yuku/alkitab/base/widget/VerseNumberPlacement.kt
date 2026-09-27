package yuku.alkitab.base.widget

/** A paragraph indent reserves space for a separate verse number, even after leading style codes. */
internal fun startsWithGutterParagraph(text: CharArray, length: Int): Boolean {
    var pos = 2 // skip the formatted-verse prefix "@@"
    while (pos + 1 < length && text[pos] == '@') {
        when (text[pos + 1]) {
            '5', '6', '7', '9' -> pos += 2 // color and italic markers emit no text
            '^', '1', '2', '3', '4' -> return true
            else -> return false // @0 and content-bearing markers keep an inline number
        }
    }
    return false
}
