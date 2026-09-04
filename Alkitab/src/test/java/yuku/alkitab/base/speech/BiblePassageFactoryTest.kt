package yuku.alkitab.base.speech

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import yuku.alkitab.model.Book
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari

class BiblePassageFactoryTest {
    private val factory = BiblePassageFactory()
    private val genesis = Book().apply {
        bookId = 0
        shortName = "Kej"
        chapter_count = 1
        verse_counts = intArrayOf(3)
    }

    @Test
    fun `Indonesian preset produces Indonesian passages in ARI order`() {
        val ari1 = Ari.encode(0, 1, 1)
        val ari2 = Ari.encode(0, 1, 2)
        val version = version(mapOf(ari1 to "Pertama", ari2 to "Kedua"), locale = "en")

        val passages = factory.fromAris(version, "preset/in-tb", listOf(ari2, ari1))

        assertEquals(listOf(ari1.toString(), ari2.toString()), passages.map { it.id })
        assertTrue(passages.all { it.languageTag == "id-ID" })
        assertEquals(listOf("Kej 1:1", "Kej 1:2"), passages.map { it.reference })
    }

    @Test
    fun `missing or blank verses are skipped`() {
        val blankAri = Ari.encode(0, 1, 1)
        val validAri = Ari.encode(0, 1, 2)
        val missingAri = Ari.encode(0, 1, 3)
        val version = version(mapOf(blankAri to "  ", validAri to "Text"))

        val passages = factory.fromAris(
            version,
            "preset/en-web",
            listOf(missingAri, validAri, blankAri),
        )

        assertEquals(listOf(validAri.toString()), passages.map { it.id })
        assertEquals("en-US", passages.single().languageTag)
    }

    @Test
    fun `chapter passages start at requested verse and stop at chapter boundary`() {
        val verse1 = Ari.encode(0, 1, 1)
        val verse2 = Ari.encode(0, 1, 2)
        val verse3 = Ari.encode(0, 1, 3)
        val version = version(mapOf(verse1 to "One", verse2 to "Two", verse3 to "Three"))

        val passages = factory.fromChapter(version, "preset/en-web", genesis, 1, 2)

        assertEquals(listOf(verse2.toString(), verse3.toString()), passages.map { it.id })
    }

    @Test
    fun `version locale supplies deterministic fallback language`() {
        val ari = Ari.encode(0, 1, 1)
        val version = version(mapOf(ari to "Texte"), locale = "fr_FR")

        val passage = factory.fromAris(version, "custom-version", listOf(ari)).single()

        assertEquals("fr-FR", passage.languageTag)
    }

    private fun version(texts: Map<Int, String>, locale: String = "en") = mockk<Version> {
        every { getLocale() } returns locale
        every { loadVerseText(any<Int>()) } answers { texts[firstArg()] }
        every { reference(any<Int>()) } answers {
            val ari = firstArg<Int>()
            genesis.reference(Ari.toChapter(ari), Ari.toVerse(ari))
        }
    }
}
