package yuku.alkitab.base.storage

object OldVerseTextDecoder {
    class Ascii : VerseTextDecoder {
        override fun separateIntoVerses(ba: ByteArray, lowercased: Boolean): Array<String> {
            val text = makeIntoSingleString(ba, lowercased)
            val verses = ArrayList<String>(60)
            var from = 0
            for (pos in text.indices) {
                if (text[pos] == '\n') {
                    verses.add(text.substring(from, pos))
                    from = pos + 1
                }
            }
            return verses.toTypedArray()
        }

        override fun makeIntoSingleString(ba: ByteArray, lowercased: Boolean): String {
            val text = String(ba, Charsets.ISO_8859_1)
            return if (lowercased) {
                buildString(text.length) {
                    for (c in text) append(if (c in 'A'..'Z') c.lowercaseChar() else c)
                }
            } else {
                text
            }
        }
    }

    class Utf8 : VerseTextDecoder {
        override fun separateIntoVerses(ba: ByteArray, lowercased: Boolean): Array<String> {
            val versesBuf = ArrayList<String>(60)

            var from = 0
            for (pos in ba.indices) {
                if (ba[pos] == 0x0a.toByte()) {
                    val single = decode(ba, from, pos - from, lowercased)
                    versesBuf.add(single)
                    from = pos + 1
                }
            }

            return versesBuf.toTypedArray()
        }

        override fun makeIntoSingleString(ba: ByteArray, lowercased: Boolean): String =
            decode(ba, 0, ba.size, lowercased)

        private fun decode(ba: ByteArray, start: Int, length: Int, isLowercased: Boolean): String {
            val text = String(ba, start, length, Charsets.UTF_8)
            if (!isLowercased) return text

            // Search offsets require simple Unicode casing without character expansion.
            return buildString(text.length) {
                var offset = 0
                while (offset < text.length) {
                    val codePoint = text.codePointAt(offset)
                    appendCodePoint(Character.toLowerCase(codePoint))
                    offset += Character.charCount(codePoint)
                }
            }
        }
    }
}
