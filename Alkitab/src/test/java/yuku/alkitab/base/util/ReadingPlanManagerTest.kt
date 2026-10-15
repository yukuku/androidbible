package yuku.alkitab.base.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import yuku.alkitab.util.IntArrayList
import yuku.bintex.BintexWriter

class ReadingPlanManagerTest {
    @Test
    fun `progress for removed readings is ignored while valid progress is retained`() {
        val codes = IntArrayList().apply {
            add(2 shl 8)
            add((2 shl 8) or 1)
            add((2 shl 8) or 2)
            add((2 shl 8) or 255)
            add(3 shl 8)
        }
        val marks = BooleanArray(2)

        ReadingPlanManager.writeReadMarksByDay(codes, marks, 2)

        assertArrayEquals(booleanArrayOf(true, true), marks)
        ReadingPlanManager.writeReadMarksByDay(codes, BooleanArray(0), 2)
    }

    @Test
    fun `a valid footer is read before closing the reading plan stream`() {
        val stream = CloseAwareStream(planBytes(0))
        val plan = ReadingPlanManager.readVersion1(stream, "test")
        assertArrayEquals(intArrayOf(0x101, 0x102), plan.dailyVerses[0])
        assertTrue(stream.closed)
    }

    @Test
    fun `invalid and missing reading plan footers are rejected and streams are closed`() {
        for (bytes in listOf(planBytes(1), planBytes(0).dropLast(1).toByteArray())) {
            val stream = CloseAwareStream(bytes)
            assertThrows(RuntimeException::class.java) {
                ReadingPlanManager.readVersion1(stream, "test")
            }
            assertTrue(stream.closed)
        }
    }

    private fun planBytes(footer: Int): ByteArray {
        val output = ByteArrayOutputStream()
        BintexWriter(output).use {
            it.writeRaw(byteArrayOf(0x52, 0x8a.toByte(), 0x61, 0x34, 0, 0xe0.toByte(), 0xea.toByte(), 1))
            it.writeValueSimpleMap(mapOf("title" to "Test", "description" to "", "duration" to 1))
            it.writeUint8(2)
            it.writeInt(0x101)
            it.writeInt(0x102)
            it.writeUint8(footer)
        }
        return output.toByteArray()
    }

    private class CloseAwareStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false

        override fun read(): Int {
            if (closed) throw IOException("Stream is closed")
            return super.read()
        }

        override fun close() {
            closed = true
            super.close()
        }
    }
}
