package yuku.alkitab.base.smartsearch

import org.junit.Assert.assertEquals
import org.junit.Test

class WordScannerTest {
    @Test
    fun `hyphens between letters keep a word whole while other punctuation splits it`() {
        assertEquals(listOf("orang-orang", "itu", "memuji", "kasih-nya"), WordScanner.words("Orang-orang itu memuji kasih-Nya."))
    }

    @Test
    fun `a hyphen that does not join two letters is not part of a word`() {
        assertEquals(listOf("ya", "tidak"), WordScanner.words("ya - tidak-"))
    }

    @Test
    fun `formatting codes glued to a word are skipped rather than read as part of it`() {
        assertEquals(listOf("pada", "mulanya", "kasih", "terang"), WordScanner.words("@@@^Pada mulanya @6kasih@5 terang @<x1@>@/"))
    }

    @Test
    fun `text inside a tagged span is read while the tag itself is not`() {
        assertEquals(listOf("roh", "kudus"), WordScanner.words("@<r=rūaḥ@>Roh@/ Kudus"))
    }

    @Test
    fun `starting inside a word skips the rest of that word`() {
        val text = "mengasihi kasih"
        val found = mutableListOf<String>()
        WordScanner.forEachWord(text, from = 3) { s, e -> found += text.substring(s, e); true }
        assertEquals(listOf("kasih"), found)
    }

    @Test
    fun `starting right after a formatting code does not skip the word that follows it`() {
        val text = "@9kasih"
        val found = mutableListOf<String>()
        WordScanner.forEachWord(text, from = 2) { s, e -> found += text.substring(s, e); true }
        assertEquals(listOf("kasih"), found)
    }

    @Test
    fun `forEachPart splits a hyphenated word into its parts`() {
        val w = "kekasih-kekasihnya"
        val parts = mutableListOf<String>()
        WordScanner.forEachPart(w, 0, w.length) { s, e -> parts += w.substring(s, e); true }
        assertEquals(listOf("kekasih", "kekasihnya"), parts)
    }
}
