package yuku.alkitab.base.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class OldVerseTextDecoderTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `Utf8 separateIntoVerses splits on newlines and decodes UTF-8`() {
        val ba = "In the beginning\nCafé א\n".toByteArray(Charsets.UTF_8)

        assertArrayEquals(arrayOf("In the beginning", "Café א"), OldVerseTextDecoder.Utf8().separateIntoVerses(ba, false))
    }

    @Test
    fun `Utf8 separateIntoVerses drops text after the last newline`() {
        val ba = "one\ntwo\nunterminated".toByteArray(Charsets.UTF_8)

        assertArrayEquals(arrayOf("one", "two"), OldVerseTextDecoder.Utf8().separateIntoVerses(ba, false))
    }

    @Test
    fun `Utf8 separateIntoVerses keeps empty verses`() {
        assertArrayEquals(arrayOf("a", "", "b"), OldVerseTextDecoder.Utf8().separateIntoVerses("a\n\nb\n".toByteArray(), false))
    }

    @Test
    fun `Utf8 separateIntoVerses lowercases when asked`() {
        assertArrayEquals(arrayOf("god said", "let there be light"), OldVerseTextDecoder.Utf8().separateIntoVerses("God Said\nLET there be Light\n".toByteArray(), true))
    }

    @Test
    fun `Utf8 makeIntoSingleString decodes the whole buffer including newlines`() {
        val ba = "Café\nNext\n".toByteArray(Charsets.UTF_8)

        assertEquals("Café\nNext\n", OldVerseTextDecoder.Utf8().makeIntoSingleString(ba, false))
        assertEquals("café\nnext\n", OldVerseTextDecoder.Utf8().makeIntoSingleString("Café\nNEXT\n".toByteArray(Charsets.UTF_8), true))
    }

    @Test
    fun `Ascii separateIntoVerses splits on newlines`() {
        assertArrayEquals(arrayOf("Ab", "cD", ""), OldVerseTextDecoder.Ascii().separateIntoVerses("Ab\ncD\n\n".toByteArray(), false))
    }

    @Test
    fun `Ascii separateIntoVerses drops text after the last newline`() {
        assertArrayEquals(arrayOf("one"), OldVerseTextDecoder.Ascii().separateIntoVerses("one\ntwo".toByteArray(), false))
    }

    @Test
    fun `Ascii separateIntoVerses lowercases only A to Z when asked`() {
        assertArrayEquals(arrayOf("hello, world! [@]"), OldVerseTextDecoder.Ascii().separateIntoVerses("HeLLo, World! [@]\n".toByteArray(), true))
    }

    @Test
    fun `Ascii separateIntoVerses sign-extends bytes above 0x7F, so only ASCII input decodes correctly`() {
        assertArrayEquals(arrayOf("￩"), OldVerseTextDecoder.Ascii().separateIntoVerses(bytes(0xe9, 0x0a), false))
    }

    @Test
    fun `Ascii makeIntoSingleString maps each byte to the char with the same value`() {
        assertEquals("Aé\n", OldVerseTextDecoder.Ascii().makeIntoSingleString(bytes(0x41, 0xe9, 0x0a), false))
        assertEquals("abÉ", OldVerseTextDecoder.Ascii().makeIntoSingleString(bytes(0x41, 0x42, 0xc9), true))
    }
}
