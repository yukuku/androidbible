package yuku.alkitabconverter.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DesktopVerseParserGoldenTest(private val category: String, private val language: String, private val input: String, private val expected: String) {
    @Test
    fun `prose parsing matches the reviewed golden outcome`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag(language))
            val text = input.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\\", "\\")
            val actual = try {
                val result = DesktopVerseParser.verseStringToAri(text)
                if (result == null) "null" else (0 until result.size()).joinToString(",", "[", "]") { "%06x".format(Locale.ROOT, result.get(it)) }
            } catch (e: RuntimeException) {
                "throws:" + e.javaClass.name
            }
            assertEquals("$category: $input ($language)", expected, actual)
        } finally {
            Locale.setDefault(original)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0} [{1}] {2}")
        fun cases(): List<Array<String>> = DesktopVerseParserGoldenTest::class.java
            .getResourceAsStream("/desktop-verse-parser-golden.tsv")!!.bufferedReader().useLines { lines ->
                lines.map { it.split('\t').toTypedArray() }.toList()
            }
    }
}
