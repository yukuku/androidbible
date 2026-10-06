package yuku.alkitabconverter.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DesktopVerseParserAliasTest(private val input: String, private val book: Int) {
    @Test
    fun `complete references resolve every frozen alias to its original book`() {
        val alias = input.removeSuffix(" 2:3")
        assertEquals(book, DesktopVerseParser.bookIdFromName(alias))
        val result = DesktopVerseParser.parseReference(input)
        val ari = (book shl 16) or 0x203
        assertArrayEquals(input, intArrayOf(ari, ari), result?.let { values -> IntArray(values.size()) { values.get(it) } })
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun cases(): List<Array<Any>> = requireNotNull(
            DesktopVerseParserAliasTest::class.java.getResourceAsStream("/desktop-verse-parser-golden.tsv"),
        ).bufferedReader().useLines { lines ->
            lines.filter { it.startsWith("alias-") }.map {
                val columns = it.split('\t')
                arrayOf<Any>(columns[2], columns[0].removePrefix("alias-").toInt())
            }.toList()
        }
    }
}
