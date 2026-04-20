package yuku.alkitab.yes2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import yuku.alkitab.yes2.compress.SnappyInputStream
import yuku.alkitab.yes2.compress.SnappyOutputStream
import yuku.alkitab.yes2.io.RandomAccessFileRandomInputStream
import java.io.FileOutputStream
import java.util.Random

class SnappyStreamRoundTripTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun compressedBlockOffsets(sizes: IntArray): IntArray {
        val offsets = IntArray(sizes.size + 1)
        var c = 0
        for (i in sizes.indices) {
            offsets[i] = c
            c += sizes[i]
        }
        offsets[sizes.size] = c
        return offsets
    }

    private fun writeCompressed(blockSize: Int, payload: ByteArray): Pair<java.io.File, IntArray> {
        val file = tmp.newFile("snappy-${System.nanoTime()}.bin")
        val snappy: SnappyOutputStream
        FileOutputStream(file).use { fos ->
            snappy = SnappyOutputStream(fos, blockSize)
            snappy.write(payload)
            snappy.flush()
        }
        return file to snappy.compressedBlockSizes
    }

    @Test
    fun `compresses and decompresses a payload that spans exactly one block`() {
        val blockSize = 1024
        val payload = ByteArray(blockSize) { (it and 0x7f).toByte() }

        val (file, sizes) = writeCompressed(blockSize, payload)
        assertEquals(1, sizes.size)

        val input = RandomAccessFileRandomInputStream(file.absolutePath)
        val snappy = SnappyInputStream(input, 0L, blockSize, sizes, compressedBlockOffsets(sizes))
        val out = ByteArray(blockSize)
        val read = snappy.read(out)
        assertEquals(blockSize, read)
        assertArrayEquals(payload, out)
        input.close()
    }

    @Test
    fun `compresses and decompresses a payload that spans multiple blocks`() {
        val blockSize = 1024
        val totalSize = blockSize * 3 + 100 // 4 blocks, last one partial
        val rng = Random(42)
        val payload = ByteArray(totalSize).also { rng.nextBytes(it) }

        val (file, sizes) = writeCompressed(blockSize, payload)
        assertEquals(4, sizes.size)

        val input = RandomAccessFileRandomInputStream(file.absolutePath)
        val snappy = SnappyInputStream(input, 0L, blockSize, sizes, compressedBlockOffsets(sizes))
        val out = ByteArray(totalSize)
        var totalRead = 0
        while (totalRead < totalSize) {
            val n = snappy.read(out, totalRead, totalSize - totalRead)
            if (n <= 0) break
            totalRead += n
        }
        assertEquals(totalSize, totalRead)
        assertArrayEquals(payload, out)
        // Intentionally don't read past the last byte: the production SnappyInputStream
        // only advances past `compressed_block_sizes` when its caller knows the exact
        // byte count in the current section (e.g. verse lengths in the text section),
        // and a naive call to read() at end-of-stream walks off the end of the block-size
        // array rather than returning -1. All real callers in Yes2Reader read exact-length
        // ranges, so we do the same here.
        input.close()
    }

    @Test
    fun `seek into a middle block returns bytes from that offset onward`() {
        val blockSize = 512
        // Three full blocks of a repeating pattern, easy to verify by index.
        val payload = ByteArray(blockSize * 3) { (it % 251).toByte() }

        val (file, sizes) = writeCompressed(blockSize, payload)
        assertEquals(3, sizes.size)

        val input = RandomAccessFileRandomInputStream(file.absolutePath)
        val snappy = SnappyInputStream(input, 0L, blockSize, sizes, compressedBlockOffsets(sizes))

        // Seek into the middle of the second block and read a chunk that crosses into the third block.
        val start = blockSize + 100
        val readLen = blockSize
        snappy.seek(start.toLong())
        val out = ByteArray(readLen)
        var totalRead = 0
        while (totalRead < readLen) {
            val n = snappy.read(out, totalRead, readLen - totalRead)
            if (n <= 0) break
            totalRead += n
        }
        assertEquals(readLen, totalRead)
        for (i in 0 until readLen) {
            assertEquals(
                "byte at payload[${start + i}] should match",
                payload[start + i],
                out[i],
            )
        }
        input.close()
    }

    @Test
    fun `highly repetitive input compresses to substantially smaller blocks than the raw size`() {
        val blockSize = 4096
        // Extremely compressible: one repeated byte for 8 blocks.
        val payload = ByteArray(blockSize * 8) { 'X'.code.toByte() }

        val (_, sizes) = writeCompressed(blockSize, payload)
        assertEquals(8, sizes.size)
        for (size in sizes) {
            // Each block of 4096 identical bytes should compress to well under the raw size.
            // The pure-Java Snappy codec used at test time is less aggressive than native, but
            // it still beats 10% of the block size comfortably.
            assertTrue(
                "expected block compressed to < blockSize/10 bytes, got $size",
                size < blockSize / 10,
            )
        }
    }
}
