package yuku.alkitab.base.search.theme.index

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import yuku.alkitab.util.Ari

class SemanticIndexReaderTest {
    @Test(expected = IndexFormatException::class)
    fun `reader rejects incompatible dimensions`() {
        SemanticIndexReader(writeFixture(dimensions = 383, emptyList()))
    }

    @Test
    fun `reader ranks allowed books only`() {
        val gen = Ari.encode(0, 1, 1)
        val exo = Ari.encode(1, 1, 1)
        val reader = SemanticIndexReader(
            writeFixture(
                3,
                listOf(
                    Row(gen, 0.01f, byteArrayOf(100, 0, 0)),
                    Row(exo, 0.01f, byteArrayOf(127, 0, 0)),
                    Row(Ari.encode(0, 1, 2), 0.01f, byteArrayOf(50, 50, 0)),
                ),
            ),
            expectedDimensions = 3,
        )

        val result = reader.topK(floatArrayOf(1f, 0f, 0f), booleanArrayOf(true, false), 2)

        assertEquals(listOf(gen, Ari.encode(0, 1, 2)), result.map { it.ari })
        assertTrue(result.all { Ari.toBook(it.ari) == 0 })
    }

    private data class Row(val ari: Int, val scale: Float, val vector: ByteArray)

    private fun writeFixture(dimensions: Int, rows: List<Row>): File {
        val file = Files.createTempFile("semantic", ".int8").toFile()
        file.outputStream().use { output ->
            val header = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
                .put("YUKUSEM1".toByteArray())
                .putInt(1)
                .putInt(dimensions)
                .putInt(rows.size)
                .array()
            output.write(header)
            rows.forEach { row ->
                output.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(row.ari).putFloat(row.scale).array())
                output.write(row.vector)
            }
        }
        return file
    }
}
