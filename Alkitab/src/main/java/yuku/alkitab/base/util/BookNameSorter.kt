package yuku.alkitab.base.util

import yuku.alkitab.model.Book

object BookNameSorter {
    /**
     * For these book ids, the leading "I", "II", "III", "IV" or "V" is replaced with a digit to save space,
     * and to keep the abbreviation understandable when truncated.
     */
    private val numberedBookCategories: Map<Int, Int> = mapOf(
        1 to listOf(0 /* Moses */, 8, 10, 12, 45, 51, 53, 59, 61, 66, 70),
        2 to listOf(1 /* Moses */, 9, 11, 13, 46, 52, 54, 60, 62, 67, 71),
        3 to listOf(2 /* Moses */, 63 /* John */, 72 /* Maccabees */),
        4 to listOf(3 /* Moses */, 73 /* Maccabees */),
        5 to listOf(4 /* Moses */),
    ).flatMap { (category, bookIds) -> bookIds.map { it to category } }.toMap()

    private val romanNumeralPrefixes = listOf("I ", "II ", "III ", "IV ", "V ")

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

    /**
     * 1 to 5 for books that are the first to fifth of a numbered series (e.g. "2 Samuel"), 0 otherwise.
     */
    private fun numberedBookCategory(bookId: Int) = numberedBookCategories[bookId] ?: 0

    private fun romanNumeralPrefix(category: Int) = romanNumeralPrefixes[category - 1]

    @JvmStatic
    fun getBookAbbr(book: Book): String {
        book.abbreviation?.let { return it }
        hardcodedAbbrs[book.shortName]?.let { return it }

        var name: String = book.shortName

        val category = numberedBookCategory(book.bookId)
        if (category > 0) {
            val prefix = romanNumeralPrefix(category)
            if (name.startsWith(prefix)) {
                name = category.toString() + name.substring(prefix.length)
            }
        }

        return name.replace(" ", "").replace(".", "").take(3)
    }

    private class Collation(val book: Book, val base: String, val number: Int)

    private fun collationOf(book: Book): Collation {
        val shortName: String = book.shortName

        val category = numberedBookCategory(book.bookId)
        if (category > 0) {
            val prefix = listOf(romanNumeralPrefix(category), category.toString()).firstOrNull { shortName.startsWith(it) }
            if (prefix != null) {
                // Same as java.lang.String.trim(); Kotlin's trim() would also strip Unicode spaces such as U+00A0.
                val base = shortName.substring(prefix.length).trim { it <= ' ' }
                return Collation(book, base, category)
            }
        }

        return Collation(book, shortName, 0)
    }

    /**
     * Returns a new array of [books] sorted by name, with numbered books (e.g. "1 Samuel" and "II Samuel")
     * grouped under their base name. [books] itself is not modified.
     */
    @JvmStatic
    fun sortAlphabetically(books: Array<Book>): Array<Book> =
        books
            .map(::collationOf)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Collation::base).thenBy(Collation::number))
            .map(Collation::book)
            .toTypedArray()
}
