package yuku.alkitab.yes2.lexicon

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun decode(root: String, vararg forms: String) = forms.map { LexiconCodec.decodeForm(root, it, table) }

    @Test
    fun `tilde stands for the root`() {
        assertEquals(listOf("kasih", "dikasihi", "kekasih"), decode("kasih", "~", "di~i", "ke~"))
    }

    @Test
    fun `less-than stands for the root with its start rewritten by the prefix table`() {
        assertEquals(listOf("mengasihi"), decode("kasih", "me<i"))
        assertEquals(listOf("menyembah", "penyembahan"), decode("sembah", "me<", "pe<an"))
        assertEquals(listOf("menulis"), decode("tulis", "me<"))
    }

    @Test
    fun `a form without markers is kept literally`() {
        assertEquals(listOf("mengutan"), decode("hutan", "mengutan"))
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
        assertEquals("me<i", LexiconCodec.encodeForm("kasih", "mengasihi", table))
        assertEquals("ke~-ke~nya", LexiconCodec.encodeForm("kasih", "kekasih-kekasihnya", table))
        assertEquals("me~-~kan", LexiconCodec.encodeForm("reka", "mereka-rekakan", table))
        assertEquals("meng~", LexiconCodec.encodeForm("ajar", "mengajar", table))
    }

    @Test
    fun `tokenizing splits at the root markers`() {
        assertEquals(listOf("me", "~", "-", "~", "kan"), LexiconCodec.tokenize("me~-~kan"))
        assertEquals(listOf("~"), LexiconCodec.tokenize("~"))
        assertEquals(listOf("mengutan"), LexiconCodec.tokenize("mengutan"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a rewritten root with no rule for it is rejected`() {
        decode("ajar", "me<")
    }

    private fun roundTrip(vararg families: EncodedFamily): Pair<ByteArray, LexiconSection> {
        val out = ByteArrayOutputStream()
        LexiconSection.writeTo(BintexWriter(out), table, families.toList())
        val bytes = out.toByteArray()
        return bytes to LexiconSection.readFrom(ByteArrayInputStream(bytes))
    }

    @Test
    fun `the section round-trips through bintex`() {
        val (_, section) = roundTrip(
            EncodedFamily("kasih", listOf("~", "me<i", "di~i", "ke~-ke~nya")),
            EncodedFamily("reka", listOf("me~-~kan", "~an")),
            EncodedFamily("hutan", listOf("~", "mengutan")),
        )
        assertEquals(listOf("k", "t", "s", "p"), section.prefixTable.rules.map { it.from })
        assertEquals(listOf("kasih", "reka", "hutan"), section.families.keys.toList())
        assertEquals(listOf("kasih", "mengasihi", "dikasihi", "kekasih-kekasihnya"), section.families["kasih"])
        assertEquals(listOf("mereka-rekakan", "rekaan"), section.families["reka"])
        assertEquals(listOf("hutan", "mengutan"), section.families["hutan"])
    }

    @Test
    fun `pieces used twice go in the table and pieces used once are written out`() {
        // "me" is used twice, so the second family's form refers to it by a one-byte token;
        // "mengutan" is used once, so it is written in full after a literal token.
        val (bytes, _) = roundTrip(
            EncodedFamily("kasih", listOf("me<i")),
            EncodedFamily("sembah", listOf("me<")),
            EncodedFamily("hutan", listOf("mengutan")),
        )
        val text = String(bytes, Charsets.ISO_8859_1)
        assertEquals(1, Regex("me").findAll(text.substringBefore("kasih")).count())
        assertTrue(text.contains("mengutan"))
    }

    @Test(expected = RuntimeException::class)
    fun `a reserved token is rejected`() {
        val out = ByteArrayOutputStream()
        val bw = BintexWriter(out)
        bw.writeUint8(2)
        bw.writeUint8(0)
        bw.writeVarUint(0)
        bw.writeInt(1)
        bw.writeAutoString("kasih")
        bw.writeVarUint(1)
        bw.writeVarUint(1)
        bw.writeVarUint(3)
        LexiconSection.readFrom(ByteArrayInputStream(out.toByteArray()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `the writer refuses a form the reader could not decode`() {
        LexiconSection.writeTo(BintexWriter(ByteArrayOutputStream()), LexiconPrefixTable.EMPTY, listOf(EncodedFamily("kasih", listOf("me<i"))))
    }
}
