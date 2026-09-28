package yuku.alkitab.base.smartsearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchLexiconTest {
    private val lex = lexiconOf("kasih kasih mengasihi dikasihi kasih-nya", "sembuh menyembuhkan disembuhkan")

    @Test
    fun `every form maps to its root, and a root that never occurs bare still maps to itself`() {
        assertEquals("kasih", lex.rootOf["mengasihi"])
        assertEquals("kasih", lex.rootOf["kasih-nya"])
        assertEquals("sembuh", lex.rootOf["sembuh"])
        assertFalse(lex.isKnown("berkata"))
    }

    @Test
    fun `formsOf always includes the root`() {
        assertEquals(listOf("menyembuhkan", "disembuhkan", "sembuh"), lex.formsOf("sembuh"))
        assertEquals(listOf("berkat"), lex.formsOf("berkat"))
    }

    @Test
    fun `formCount counts the listed forms`() {
        assertEquals(6, lex.formCount)
    }

    @Test
    fun `Indonesian matches whether it is written in, id or ind`() {
        assertTrue(SearchLexicon.sameLanguage("id", "in"))
        assertTrue(SearchLexicon.sameLanguage("id", "ind"))
        assertTrue(SearchLexicon.sameLanguage("id", "in_ID"))
        assertFalse(SearchLexicon.sameLanguage("id", "en"))
        assertFalse(SearchLexicon.sameLanguage("id", null))
    }
}
