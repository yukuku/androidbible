package yuku.alkitab.base.util

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.model.Book
import yuku.alkitab.util.Ari

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SearchReferenceAriTest {
    private val books = arrayOf(
        Book().apply {
            bookId = 0
            shortName = "Kejadian"
            chapter_count = 50
            verse_counts = IntArray(50) { 32 }
        },
        Book().apply {
            bookId = 18
            shortName = "Mazmur"
            chapter_count = 150
            verse_counts = IntArray(150) { 31 }
        },
    )

    @Test
    fun `abbreviated book with chapter produces a chapter reference`() {
        assertEquals(Ari.encode(0, 5, 0), searchReferenceAri("kej5", books))
    }

    @Test
    fun `misspelled book with chapter and verse uses Direct parser matching`() {
        assertEquals(Ari.encode(18, 109, 3), searchReferenceAri("mzzmur 109 3", books))
    }

    @Test
    fun `book only and numbers without a book produce no suggestion`() {
        for (query in listOf("", "kej", "Kejadian", "5", "109 3")) {
            assertNull(query, searchReferenceAri(query, books))
        }
    }

    @Test
    fun `out of bounds references produce no suggestion`() {
        for (query in listOf("kej 0", "kej 51", "kej 5 33", "Mazmur 151")) {
            assertNull(query, searchReferenceAri(query, books))
        }
    }

    @Test
    fun `OSIS references require a book present in the version`() {
        assertEquals(Ari.encode(0, 5, 3), searchReferenceAri("Gen.5.3", books))
        assertNull(searchReferenceAri("John.3.16", books))
    }
}
