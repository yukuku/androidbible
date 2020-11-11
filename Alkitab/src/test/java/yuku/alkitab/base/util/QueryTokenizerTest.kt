package yuku.alkitab.base.util

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class QueryTokenizerTest {
    @Test
    fun tokenize() {
        assertArrayEquals(arrayOf("a", "b"), QueryTokenizer.tokenize("""a b"""))
        assertArrayEquals(arrayOf("+a b", "c"), QueryTokenizer.tokenize(""""a b" c"""))
        assertArrayEquals(arrayOf("""a"bc"d"""), QueryTokenizer.tokenize("""a"bc"d"""))
        assertArrayEquals(arrayOf("+a bc"), QueryTokenizer.tokenize(""""a bc""""))
        assertArrayEquals(arrayOf("+a", "bc"), QueryTokenizer.tokenize(""""a" bc"""))
        assertArrayEquals(arrayOf("+a+b"), QueryTokenizer.tokenize(""""a+b""""))

        // non-standard quotes
        assertArrayEquals(arrayOf("+a b"), QueryTokenizer.tokenize("\u201ca b\u201d"))
        assertArrayEquals(arrayOf("+a b"), QueryTokenizer.tokenize("\u201ca b\u201c"))
        assertArrayEquals(arrayOf("+a b"), QueryTokenizer.tokenize("\u201da b\u201d"))
        assertArrayEquals(arrayOf("x", "+ab", "+cd"), QueryTokenizer.tokenize("x \u201dab\u201d \u201dcd\u201c"))
    }
}
