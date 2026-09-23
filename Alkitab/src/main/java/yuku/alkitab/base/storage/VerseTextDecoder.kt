package yuku.alkitab.base.storage

interface VerseTextDecoder {
    fun separateIntoVerses(ba: ByteArray, lowercased: Boolean): Array<String>
    fun makeIntoSingleString(ba: ByteArray, lowercased: Boolean): String
}
