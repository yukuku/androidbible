package yuku.alkitab.yes2.lexicon

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import yuku.alkitab.yes2.section.LexiconSection
import yuku.bintex.BintexWriter

class LexiconSectionTest {
    private val indonesian = linkedMapOf(
        "kasih" to listOf("kasih", "mengasihi", "dikasihi", "kekasih-kekasihnya", "pengasih"),
        "kata" to listOf("kata", "mengatakan", "berkata", "perkataan", "dikatakan"),
        "kenal" to listOf("kenal", "mengenal", "dikenal", "pengenalan", "mengenali"),
        "kumpul" to listOf("mengumpulkan", "berkumpul", "dikumpulkan", "pengumpulan"),
        "kirim" to listOf("mengirim", "dikirim", "mengirimkan", "pengiriman"),
        "reka" to listOf("mereka-rekakan", "rekaan"),
        "hutan" to listOf("hutan", "mengutan"),
    )

    private val english = linkedMapOf(
        "love" to listOf("love", "loves", "loved", "loving", "loveth"),
        "give" to listOf("give", "gives", "giving", "giveth", "gave"),
        "live" to listOf("live", "lives", "lived", "living", "liveth"),
        "move" to listOf("move", "moved", "moving", "moveth"),
        "make" to listOf("make", "makes", "making", "maketh", "made"),
        "carry" to listOf("carry", "carried", "carrieth", "carrying"),
        "tarry" to listOf("tarry", "tarried", "tarrieth", "tarrying"),
        "marry" to listOf("marry", "married", "marrieth", "marrying"),
    )

    private fun write(families: Map<String, List<String>>): Pair<ByteArray, LexiconCompiler.Compiled> {
        val out = ByteArrayOutputStream()
        val compiled = LexiconSection.writeTo(BintexWriter(out), families)
        return out.toByteArray() to compiled
    }

    private fun roundTrip(families: Map<String, List<String>>) = LexiconSection.readFrom(ByteArrayInputStream(write(families).first))

    @Test
    fun `the longest matching rule wins, at either end of the root`() {
        val start = RootRewriteTable(RootRewriteTable.Side.START, listOf(RootRewriteTable.Rule("k", "ng"), RootRewriteTable.Rule("kh", "kh")))
        assertEquals("khianat", start.rewrite("khianat"))
        assertEquals("ngasih", start.rewrite("kasih"))
        assertNull(start.rewrite("ajar"))

        val end = RootRewriteTable(RootRewriteTable.Side.END, listOf(RootRewriteTable.Rule("e", ""), RootRewriteTable.Rule("ie", "y")))
        assertEquals("lov", end.rewrite("love"))
        assertEquals("dy", end.rewrite("die"))
        assertNull(end.rewrite("walk"))
    }

    @Test
    fun `the start rule a nasal prefix needs is found in the data`() {
        val compiled = write(indonesian).second
        assertEquals(listOf("k -> ng"), compiled.startTable.rules.map { it.toString() })
        assertEquals("ngasih", compiled.startTable.rewrite("kasih"))
        assertTrue(compiled.endTable.rules.isEmpty())
    }

    @Test
    fun `end rules for English spelling changes are found in the data`() {
        val rules = write(english).second.endTable.rules.map { it.toString() }
        assertTrue(rules.toString(), "e -> " in rules)
        // y -> i and y -> ie are both sound; which is smaller depends on the other forms
        assertTrue(rules.toString(), rules.any { it.startsWith("y -> i") })
    }

    @Test
    fun `any text can sit between roots`() {
        val compiled = write(indonesian).second
        val parts = compiled.families.first { it.root == "reka" }.forms[0]
        assertEquals(listOf("me", null, "-", null, "kan"), parts.map { it.text })
        assertEquals(LexiconSection.TOKEN_ROOT, parts[1].token)
    }

    @Test
    fun `the section round-trips through bintex`() {
        for (families in listOf(indonesian, english)) {
            val section = roundTrip(families)
            assertEquals(families.keys.toList(), section.families.keys.toList())
            assertEquals(families, section.families)
        }
    }

    @Test
    fun `a form no rule explains is kept whole`() {
        assertEquals(listOf("hutan", "mengutan"), roundTrip(indonesian).families["hutan"])
    }

    @Test
    fun `text used twice is a shared piece and text used once is written out`() {
        val (bytes, compiled) = write(indonesian)
        assertTrue("me" in compiled.pieces)
        assertTrue("mengutan" !in compiled.pieces)
        val text = String(bytes, Charsets.ISO_8859_1)
        assertEquals(1, Regex("mengutan").findAll(text).count())
    }

    @Test
    fun `a family whose forms all contain the root needs no rules`() {
        val compiled = write(linkedMapOf("iman" to listOf("iman", "beriman", "keimanan"))).second
        assertTrue(compiled.startTable.rules.isEmpty())
        assertTrue(compiled.endTable.rules.isEmpty())
    }

    @Test(expected = RuntimeException::class)
    fun `a reserved token is rejected`() {
        val out = ByteArrayOutputStream()
        val bw = BintexWriter(out)
        bw.writeUint8(1)
        bw.writeUint8(0)
        bw.writeUint8(0)
        bw.writeVarUint(0)
        bw.writeInt(1)
        bw.writeAutoString("kasih")
        bw.writeVarUint(1)
        bw.writeVarUint(1)
        bw.writeVarUint(4)
        LexiconSection.readFrom(ByteArrayInputStream(out.toByteArray()))
    }

    @Test(expected = RuntimeException::class)
    fun `a rewritten root with no rule for it is rejected`() {
        val out = ByteArrayOutputStream()
        val bw = BintexWriter(out)
        bw.writeUint8(1)
        bw.writeUint8(0)
        bw.writeUint8(0)
        bw.writeVarUint(0)
        bw.writeInt(1)
        bw.writeAutoString("kasih")
        bw.writeVarUint(1)
        bw.writeVarUint(1)
        bw.writeVarUint(LexiconSection.TOKEN_END_REWRITTEN)
        LexiconSection.readFrom(ByteArrayInputStream(out.toByteArray()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an empty form is refused`() {
        write(linkedMapOf("kasih" to listOf("kasih", "")))
    }
}
