package yuku.alkitab.base.storage

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import yuku.alkitab.io.Utf8Decoder

class Utf8DecoderTest {
    @Test
    fun `concurrent version decoding keeps each buffer isolated`() {
        val executor = Executors.newFixedThreadPool(4)
        val barrier = CyclicBarrier(4)
        try {
            val tasks = (0..3).map { index ->
                executor.submit {
                    val text = ("Version$index Éא ").repeat(500 + index * 1000)
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    val lower = text.map { Character.toLowerCase(it) }.joinToString("")
                    barrier.await(10, TimeUnit.SECONDS)
                    repeat(200) {
                        assertEquals(text, Utf8Decoder.toString(bytes))
                        assertEquals(lower, Utf8Decoder.toStringLowerCase(bytes))
                    }
                }
            }
            tasks.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `truncated slices fail instead of reading past the slice or returning partial text`() {
        val bytes = "Aéא".toByteArray(Charsets.UTF_8)
        assertEquals("é", Utf8Decoder.toString(bytes, 1, 2))
        assertThrows(IllegalArgumentException::class.java) { Utf8Decoder.toString(bytes, 0, 2) }
        assertThrows(IllegalArgumentException::class.java) { Utf8Decoder.toStringLowerCase(bytes, 3, 1) }
    }
}
