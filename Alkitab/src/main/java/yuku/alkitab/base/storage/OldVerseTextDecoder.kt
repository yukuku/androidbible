package yuku.alkitab.base.storage

import yuku.alkitab.io.Utf8Decoder

object OldVerseTextDecoder {
    class Ascii : VerseTextDecoder {
        private fun lowercase(ba: ByteArray) {
            for (i in ba.indices) {
                if (ba[i] in 'A'.code..'Z'.code) {
                    ba[i] = (ba[i].toInt() or 0x20).toByte()
                }
            }
        }

        override fun separateIntoVerses(ba: ByteArray, lowercased: Boolean): Array<String> {
            val versesBuf = ArrayList<String>(60)
            val verseBuf = CharArray(4000)
            var i = 0

            if (lowercased) {
                lowercase(ba)
            }

            //# WARNING: This will work only if all bytes are less than 0x80.
            for (c in ba) {
                if (c == 0x0a.toByte()) {
                    versesBuf.add(String(verseBuf, 0, i))
                    i = 0
                } else {
                    verseBuf[i++] = c.toInt().toChar()
                }
            }

            return versesBuf.toTypedArray()
        }

        override fun makeIntoSingleString(ba: ByteArray, lowercased: Boolean): String {
            if (lowercased) {
                lowercase(ba)
            }

            return String(ba, Charsets.ISO_8859_1)
        }
    }

    class Utf8 : VerseTextDecoder {
        override fun separateIntoVerses(ba: ByteArray, lowercased: Boolean): Array<String> {
            val versesBuf = ArrayList<String>(60)

            var from = 0
            for (pos in ba.indices) {
                if (ba[pos] == 0x0a.toByte()) {
                    val single = if (lowercased) {
                        Utf8Decoder.toStringLowerCase(ba, from, pos - from)
                    } else {
                        Utf8Decoder.toString(ba, from, pos - from)
                    }
                    versesBuf.add(single)
                    from = pos + 1
                }
            }

            return versesBuf.toTypedArray()
        }

        override fun makeIntoSingleString(ba: ByteArray, lowercased: Boolean): String =
            if (lowercased) Utf8Decoder.toStringLowerCase(ba) else Utf8Decoder.toString(ba)
    }
}
