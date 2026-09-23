package yuku.alkitab.yes2.lexicon

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import yuku.alkitab.yes2.section.LexiconSection
import yuku.bintex.BintexWriter

class LexiconCodecTest {
    private val table = LexiconPrefixTable(
        listOf(
            LexiconPrefixTable.Rule("k", "ng"),
            LexiconPrefixTable.Rule("t", "n"),
            LexiconPrefixTable.Rule("s", "ny"),
            LexiconPrefixTable.Rule("p", "m"),
        )
    )

    @Test
    fun `tilde stands for the root`() {
        assertEquals("kasih" to listOf("kasih", "dikasihi", "kekasih"), LexiconCodec.decodeFamily("kasih ~ di~i ke~", table))
    }

    @Test
    fun `less-than stands for the root with its start rewritten by the prefix table`() {
        assertEquals(listOf("mengasihi"), LexiconCodec.decodeFamily("kasih me<i", table).second)
        assertEquals(listOf("menyembah", "penyembahan"), LexiconCodec.decodeFamily("sembah me< pe<an", table).second)
        assertEquals(listOf("menulis"), LexiconCodec.decodeFamily("tulis me<", table).second)
    }

    @Test
    fun `forms without markers are kept literally`() {
        assertEquals(listOf("kepada-nya"), LexiconCodec.decodeFamily("kepada kepada-nya", table).second)
    }

    @Test
    fun `the longest matching prefix rule wins`() {
        val t = LexiconPrefixTable(listOf(LexiconPrefixTable.Rule("k", "ng"), LexiconPrefixTable.Rule("kh", "kh")))
        assertEquals("khianat", t.rewrite("khianat"))
        assertEquals("ngasih", t.rewrite("kasih"))
        assertNull(t.rewrite("ajar"))
    }

    @Test
    fun `encoding picks the root, then the rewritten root, and leaves the rest alone`() {
        assertEquals(
            "kasih ~ me<i di~i ~-nya ke~-ke~nya",
            LexiconCodec.encodeFamily("kasih", listOf("kasih", "mengasihi", "dikasihi", "kasih-nya", "kekasih-kekasihnya"), table),
        )
        assertEquals("ajar meng~ ~an", LexiconCodec.encodeFamily("ajar", listOf("mengajar", "ajaran"), table))
    }

    @Test
    fun `decoding what was encoded gives the forms back`() {
        val forms = listOf("menyembuhkan", "sembuh", "kesembuhan", "penyembuhan", "sembuhkanlah")
        val (root, decoded) = LexiconCodec.decodeFamily(LexiconCodec.encodeFamily("sembuh", forms, table), table)
        assertEquals("sembuh", root)
        assertEquals(forms, decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a rewritten root with no rule for it is rejected`() {
        LexiconCodec.decodeFamily("ajar me<", table)
    }

    @Test
    fun `the section round-trips through bintex`() {
        val lines = listOf("kasih ~ me<i di~i", "sembah me< pe<an")
        val out = ByteArrayOutputStream()
        LexiconSection.writeTo(BintexWriter(out), table, lines)

        val section = LexiconSection.readFrom(ByteArrayInputStream(out.toByteArray()))
        assertEquals(listOf("k", "t", "s", "p"), section.prefixTable.rules.map { it.from })
        assertEquals(listOf("kasih", "sembah"), section.families.keys.toList())
        assertEquals(listOf("kasih", "mengasihi", "dikasihi"), section.families["kasih"])
        assertEquals(listOf("menyembah", "penyembahan"), section.families["sembah"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `the writer refuses a line the reader could not decode`() {
        LexiconSection.writeTo(BintexWriter(ByteArrayOutputStream()), LexiconPrefixTable.EMPTY, listOf("kasih me<i"))
    }
}
