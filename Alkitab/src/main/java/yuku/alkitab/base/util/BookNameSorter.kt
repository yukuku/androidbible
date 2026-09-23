package yuku.alkitab.base.util

import yuku.alkitab.model.Book

object BookNameSorter {
    private val numberedBookStartsWiths = listOf("I ", "II ", "III ", "IV ", "V ")

    private val numberedBookMap: IntArray = run {
        // for these book numbers, replace "I", "II", "III", "IV", "V" with numbers
        // to save space, to make them still understandable when truncated
        val numberedBooks1 = intArrayOf(0 /*moses*/, 8, 10, 12, 45, 51, 53, 59, 61, 66, 70)
        val numberedBooks2 = intArrayOf(1 /*moses*/, 9, 11, 13, 46, 52, 54, 60, 62, 67, 71)
        val numberedBooks3 = intArrayOf(2 /*moses*/, 63 /*john*/, 72 /*makabe*/)
        val numberedBooks4 = intArrayOf(3 /*moses*/, 73 /*makabe*/)
        val numberedBooks5 = intArrayOf(4 /*moses*/)

        IntArray(74).apply { // as large as the max number above + 1
            for (bookId in numberedBooks1) this[bookId] = 1
            for (bookId in numberedBooks2) this[bookId] = 2
            for (bookId in numberedBooks3) this[bookId] = 3
            for (bookId in numberedBooks4) this[bookId] = 4
            for (bookId in numberedBooks5) this[bookId] = 5
        }
    }

    private val hardcodedAbbrs = mapOf(
        "Filemon" to "Flm",
        "Amos" to "Amos",
        "Ayub" to "Ayub",
        "Yoel" to "Yoel",
        "Pengkhotbah" to "Pkh",
        "Wahyu" to "Why",
        "1 Timotius" to "1Tim",
        "2 Timotius" to "2Tim",
        "1 Tesalonika" to "1Tes",
        "2 Tesalonika" to "2Tes",
        "1 Korintus" to "1Kor",
        "2 Korintus" to "2Kor",
        "1 Raja-raja" to "1Raj",
        "2 Raja-raja" to "2Raj",
        "1 Petrus" to "1Pet",
        "2 Petrus" to "2Pet",
        "1 Samuel" to "1Sam",
        "2 Samuel" to "2Sam",
        "1 Tawarikh" to "1Taw",
        "2 Tawarikh" to "2Taw",
        "1 Yohanes" to "1Yoh",
        "2 Yohanes" to "2Yoh",
        "3 Yohanes" to "3Yoh",

        "Philemon" to "Phm",
        "Philippians" to "Phil",
        "Song of Solomon" to "Song",
        "Zephaniah" to "Zeph",
        "Ruth" to "Ruth",
        "1 Corinthians" to "1Cor",
        "2 Corinthians" to "2Cor",
    )

    private fun numberedBookCategory(bookId: Int) = if (bookId >= numberedBookMap.size) 0 else numberedBookMap[bookId]

    @JvmStatic
    fun getBookAbbr(book: Book): String {
        book.abbreviation?.let { return it }
        hardcodedAbbrs[book.shortName]?.let { return it }

        var name: String = book.shortName

        val numberedBookCategory = numberedBookCategory(book.bookId)
        if (numberedBookCategory > 0) {
            val startsWith = numberedBookStartsWiths[numberedBookCategory - 1]
            if (name.startsWith(startsWith)) {
                name = numberedBookCategory.toString() + name.substring(startsWith.length)
            }
        }

        // remove spaces and '.'
        return name.replace(" ", "").replace(".", "").take(3)
    }

    private class Collation(val book: Book, val base: String, val number: Int)

    private fun collationOf(book: Book): Collation {
        var base: String = book.shortName // default
        var number = 0 // default

        val numberedBookCategory = numberedBookCategory(book.bookId)
        if (numberedBookCategory > 0) {
            val startsWith = numberedBookStartsWiths[numberedBookCategory - 1]
            val startsWithNumber = numberedBookCategory.toString()

            if (book.shortName.startsWith(startsWith)) {
                base = book.shortName.substring(startsWith.length).trim { it <= ' ' }
                number = numberedBookCategory
            } else if (book.shortName.startsWith(startsWithNumber)) {
                base = book.shortName.substring(startsWithNumber.length).trimStart('.').trim { it <= ' ' }
                number = numberedBookCategory
            }
        }

        return Collation(book, base, number)
    }

    /**
     * @param books This array will NOT be modified.
     * @return a new array of books already sorted.
     */
    @JvmStatic
    fun sortAlphabetically(books: Array<Book>): Array<Book> =
        books
            .map(::collationOf)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Collation::base).thenBy(Collation::number))
            .map(Collation::book)
            .toTypedArray()
}
