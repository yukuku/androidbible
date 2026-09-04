package yuku.alkitab.base.search.theme.model

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class ByteLevelBpeTokenizerTest {
    @Test
    fun `Indonesian and Unicode match official token IDs`() {
        val tokenizerFile = File("/tmp/granite-embedding-97m-r2-tokenizer.json")
        assumeTrue("Pinned tokenizer fixture is required for the golden integration test", tokenizerFile.isFile)
        val tokenizer = ByteLevelBpeTokenizer(TokenizerData.load(tokenizerFile))

        assertArrayEquals(longArrayOf(179934, 32242, 2374, 4120, 6815, 237, 179938), tokenizer.encode("kasih yang sabar", 32).unpaddedIds())
        assertArrayEquals(longArrayOf(179934, 74, 962, 307, 18089, 783, 16189, 8519, 7078, 179938), tokenizer.encode("kecemasan & pengharapan", 32).unpaddedIds())
        assertArrayEquals(longArrayOf(179934, 33397, 758, 2989, 179938), tokenizer.encode("God’s love", 32).unpaddedIds())
        assertArrayEquals(longArrayOf(179934, 56, 2252, 27327, 180, 18, 25, 1078, 179938), tokenizer.encode("Yohanes 3:16", 32).unpaddedIds())
        assertArrayEquals(longArrayOf(179934, 165798, 1466, 6391, 25681, 22618, 179938), tokenizer.encode("mengampuni orang lain", 32).unpaddedIds())
    }

    @Test
    fun `truncation preserves boundary tokens and padding masks the rest`() {
        val tokenizerFile = File("/tmp/granite-embedding-97m-r2-tokenizer.json")
        assumeTrue(tokenizerFile.isFile)
        val tokenizer = ByteLevelBpeTokenizer(TokenizerData.load(tokenizerFile))

        val encoded = tokenizer.encode("satu dua tiga empat lima enam", 5)

        assertEquals(5, encoded.inputIds.size)
        assertEquals(179934L, encoded.inputIds.first())
        assertEquals(179938L, encoded.inputIds.last())
        assertArrayEquals(LongArray(5) { 1L }, encoded.attentionMask)
    }
}
