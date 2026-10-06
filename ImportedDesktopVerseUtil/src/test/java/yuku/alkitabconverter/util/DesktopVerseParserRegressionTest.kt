package yuku.alkitabconverter.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopVerseParserRegressionTest {
    private fun assertPairs(text: String, vararg expected: Int) {
        val result = DesktopVerseParser.verseStringToAri(text)
        assertArrayEquals(text, expected, result?.let { values -> IntArray(values.size()) { values.get(it) } })
    }

    @Test
    fun `comma connected chapter numbers each produce a whole chapter range`() {
        assertPairs("Gen 1,3,5", 0x000100, 0x000100, 0x000300, 0x000300, 0x000500, 0x000500)
        assertPairs("Please read Gen 1, 3, 5 today.", 0x000100, 0x000100, 0x000300, 0x000300, 0x000500, 0x000500)
        assertPairs("Kej 1 dan 3", 0x000100, 0x000100, 0x000300, 0x000300)
        assertPairs("Gen 1-3,5-7,9", 0x000100, 0x000300, 0x000500, 0x000700, 0x000900, 0x000900)
    }

    @Test
    fun `explicit verses establish chapter context for subsequent verse numbers`() {
        assertPairs("Gen 1:2,3,5", 0x000102, 0x000102, 0x000103, 0x000103, 0x000105, 0x000105)
        assertPairs("Gen 1,3:4,6", 0x000100, 0x000100, 0x000304, 0x000304, 0x000306, 0x000306)
        assertPairs("Gen 1:2-3:4,6", 0x000102, 0x000304, 0x000306, 0x000306)
    }

    @Test
    fun `prose outside the reference does not become chapter or verse list entries`() {
        assertPairs("Please read <Gen 1>. See on the verse <3> and verse <5>, bla bla", 0x000100, 0x000100)
        assertPairs("Gen 1:3", 0x000103, 0x000103)
        assertPairs("Gen 5", 0x000500, 0x000500)
        assertNull(DesktopVerseParser.verseStringToAri("See verse 3 and verse 5"))
    }

    @Test
    fun `single chapter book shorthand still denotes verses`() {
        assertPairs("Jude 1,3,5", 0x400101, 0x400101, 0x400103, 0x400103, 0x400105, 0x400105)
        assertPairs("Obaja 1-3,5", 0x1e0101, 0x1e0103, 0x1e0105, 0x1e0105)
    }

    @Test
    fun `oversized numbers reject the reference without throwing or returning a partial prefix`() {
        for (number in listOf("256", "257", "65536", "2147483647", "2147483648", "999999999999", "9".repeat(100))) {
            for (reference in listOf(
                "Gen $number", "Gen $number:1", "Gen 1:$number", "Gen 1-$number", "Gen 1:2-$number",
                "Gen 1,3,$number", "Gen 1:2,3,$number", "Gen 1:2-3:$number", "Gen 1:2,$number:3", "Jude $number",
            )) assertNull(reference, DesktopVerseParser.verseStringToAri(reference))
        }
    }

    @Test
    fun `the largest encodable numbers remain intact without wrapping`() {
        assertPairs("Gen 255", 0x00ff00, 0x00ff00)
        assertPairs("Gen 255:255", 0x00ffff, 0x00ffff)
        assertPairs("Gen 1-255", 0x000100, 0x00ff00)
        assertPairs("Gen 1:1-255", 0x000101, 0x0001ff)
    }
}
