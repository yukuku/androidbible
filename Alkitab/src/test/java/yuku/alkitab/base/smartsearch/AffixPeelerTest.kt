package yuku.alkitab.base.smartsearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AffixPeelerTest {
    private fun peelTo(word: String, vararg known: String): String? {
        val set = known.toSet()
        return AffixPeeler.peel(word) { it in set }?.let { path -> path.lastOrNull()?.to ?: word }
    }

    @Test
    fun `a known word needs no peeling`() {
        assertEquals(emptyList<PeelStep>(), AffixPeeler.peel("kasih") { it == "kasih" })
    }

    @Test
    fun `a suffix is peeled to reach a known form`() {
        assertEquals("sembuh", peelTo("sembuhkan", "sembuh"))
    }

    @Test
    fun `peeling branches instead of committing to the first suffix that fits`() {
        // -kan fits first, leaving 'amu'; the right reading is amuk + -an.
        assertEquals("amuk", peelTo("amukan", "amuk"))
    }

    @Test
    fun `meng- offers the k it swallowed from a vowel-initial spelling`() {
        assertEquals("kasih", peelTo("mengasihkan", "kasih"))
    }

    @Test
    fun `meny- restores s, mem- restores p and men- restores t`() {
        assertEquals("sembah", peelTo("menyembahkan", "sembah"))
        assertEquals("puji", peelTo("memujikan", "puji"))
        assertEquals("tulis", peelTo("menuliskan", "tulis"))
    }

    @Test
    fun `mem- and men- before a consonant keep the root letter unchanged`() {
        assertEquals("bawa", peelTo("membawakanlah", "bawa"))
        assertEquals("dengar", peelTo("mendengarkanlah", "dengar"))
    }

    @Test
    fun `a vowel after mem- is never read as a vowel-initial root, which would take meng-`() {
        // memuji is puji or muji, never uji.
        assertNull(peelTo("memuji", "uji"))
    }

    @Test
    fun `several affixes are peeled in turn and the steps are reported`() {
        val path = AffixPeeler.peel("mengampunkan") { it == "ampun" }!!
        assertEquals(listOf("-kan", "meng-"), path.map { it.label })
        assertEquals("ampun", path.last().to)
    }

    @Test
    fun `peeling never leaves a stem shorter than three letters`() {
        assertNull(peelTo("dian", "di"))
    }

    @Test
    fun `nothing is returned when no peel lands on a known form`() {
        assertNull(peelTo("yerus", "yerusalem"))
    }
}
