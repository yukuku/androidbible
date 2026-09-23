package yuku.alkitab.base.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import yuku.alkitab.model.Book

class BookNameSorterTest {
    private fun book(bookId: Int, shortName: String, abbreviation: String? = null) = Book().apply {
        this.bookId = bookId
        this.shortName = shortName
        this.abbreviation = abbreviation
    }

    @Test
    fun `getBookAbbr prefers the abbreviation stored in the book`() {
        assertEquals("Gn", BookNameSorter.getBookAbbr(book(0, "Genesis", abbreviation = "Gn")))
    }

    @Test
    fun `getBookAbbr uses the hardcoded abbreviation when the short name has one`() {
        assertEquals("Flm", BookNameSorter.getBookAbbr(book(56, "Filemon")))
        assertEquals("1Kor", BookNameSorter.getBookAbbr(book(45, "1 Korintus")))
        assertEquals("Amos", BookNameSorter.getBookAbbr(book(29, "Amos")))
        assertEquals("Song", BookNameSorter.getBookAbbr(book(21, "Song of Solomon")))
    }

    @Test
    fun `getBookAbbr replaces a roman numeral prefix with a digit for numbered books`() {
        assertEquals("1Sa", BookNameSorter.getBookAbbr(book(8, "I Samuel")))
        assertEquals("2Ki", BookNameSorter.getBookAbbr(book(11, "II Kings")))
        assertEquals("3Jo", BookNameSorter.getBookAbbr(book(63, "III John")))
        assertEquals("4Ma", BookNameSorter.getBookAbbr(book(73, "IV Makabe")))
        assertEquals("5Mo", BookNameSorter.getBookAbbr(book(4, "V Moses")))
    }

    @Test
    fun `getBookAbbr leaves a roman numeral prefix alone when it does not match the book's number`() {
        assertEquals("IIS", BookNameSorter.getBookAbbr(book(8, "II Samuel")))
        assertEquals("IJo", BookNameSorter.getBookAbbr(book(5, "I Joshua")))
    }

    @Test
    fun `getBookAbbr strips spaces and dots and keeps at most three characters`() {
        assertEquals("SMa", BookNameSorter.getBookAbbr(book(39, "S. Mateo")))
        assertEquals("Ob", BookNameSorter.getBookAbbr(book(30, "Ob")))
        assertEquals("Bar", BookNameSorter.getBookAbbr(book(80, "Baruch")))
    }

    @Test
    fun `sortAlphabetically groups numbered books under their base name ordered by number`() {
        val books = arrayOf(
            book(9, "II Samuel"),
            book(8, "I Samuel"),
            book(1, "Exodus"),
            book(0, "Genesis"),
            book(46, "2 Korintus"),
            book(45, "1 Korintus"),
            book(44, "Roma"),
            book(39, "matius"),
        )

        val sorted = BookNameSorter.sortAlphabetically(books)

        assertEquals(listOf(1, 0, 45, 46, 39, 44, 8, 9), sorted.map { it.bookId })
    }

    @Test
    fun `sortAlphabetically groups books numbered as in 1 dot Mose under their base name`() {
        val books = arrayOf(
            book(0, "1. Mose"),
            book(1, "2. Mose"),
            book(2, "3. Mose"),
            book(3, "4. Mose"),
            book(4, "5. Mose"),
            book(5, "Josua"),
            book(6, "Richter"),
            book(8, "1. Samuel"),
            book(9, "2. Samuel"),
            book(22, "Jesaja"),
            book(39, "Matth\u00e4us"),
        )

        val sorted = BookNameSorter.sortAlphabetically(books)

        assertEquals(listOf(22, 5, 39, 0, 1, 2, 3, 4, 6, 8, 9), sorted.map { it.bookId })
    }

    @Test
    fun `sortAlphabetically ignores a dot right after the number even without a following space`() {
        val books = arrayOf(book(1, "2.Mose"), book(5, "Josua"), book(0, "1.Mose"), book(6, "Richter"))

        val sorted = BookNameSorter.sortAlphabetically(books)

        assertEquals(listOf(5, 0, 1, 6), sorted.map { it.bookId })
    }

    @Test
    fun `getBookAbbr drops the dot after the number`() {
        assertEquals("1Mo", BookNameSorter.getBookAbbr(book(0, "1. Mose")))
    }

    @Test
    fun `sortAlphabetically does not modify its input`() {
        val books = arrayOf(book(1, "Exodus"), book(0, "Genesis"), book(2, "Anything"))
        val copy = books.copyOf()

        BookNameSorter.sortAlphabetically(books)

        assertArrayEquals(copy, books)
    }

    @Test
    fun `sortAlphabetically keeps the input order of books that collate equally`() {
        val books = arrayOf(book(68, "Tobit"), book(100, "tobit"), book(101, "TOBIT"))

        val sorted = BookNameSorter.sortAlphabetically(books)

        assertEquals(listOf(68, 100, 101), sorted.map { it.bookId })
    }

    @Test
    fun `sortAlphabetically trims only ASCII control characters and spaces after the number prefix`() {
        val books = arrayOf(book(45, "1\u00A0Korintus"), book(44, "Roma"))

        val sorted = BookNameSorter.sortAlphabetically(books)

        assertEquals(listOf(44, 45), sorted.map { it.bookId })
    }
}
