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
        // A no-break space survives the trim, and U+00A0 sorts after "Roma"
        val books = arrayOf(book(45, "1 Korintus"), book(44, "Roma"))

        val sorted = BookNameSorter.sortAlphabetically(books)

        assertEquals(listOf(44, 45), sorted.map { it.bookId })
    }
}
