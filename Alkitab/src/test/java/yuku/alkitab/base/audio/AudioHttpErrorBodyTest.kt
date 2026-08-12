package yuku.alkitab.base.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure-logic tests for the audio load log's HTTP error-body formatting. See [formatErrorBody]. */
class AudioHttpErrorBodyTest {

    @Test
    fun `a small printable body is shown as text`() {
        assertEquals(
            "Internal Server Error",
            formatErrorBody("Internal Server Error".toByteArray(Charsets.US_ASCII)),
        )
    }

    @Test
    fun `newlines tabs and carriage returns are folded to spaces so one entry stays one line`() {
        assertEquals(
            "line one line two\tafter tab".replace('\t', ' '),
            formatErrorBody("line one\nline two\tafter tab".toByteArray(Charsets.US_ASCII)),
        )
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("oops", formatErrorBody("\n  oops  \n".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `an empty body yields nothing to log`() {
        assertNull(formatErrorBody(ByteArray(0)))
    }

    @Test
    fun `a whitespace-only body yields nothing to log`() {
        assertNull(formatErrorBody("\n\n  \t".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `a body at or above the size cap is skipped as an error page rather than a message`() {
        val atCap = ByteArray(MAX_ERROR_BODY_BYTES) { 'a'.code.toByte() }
        assertNull(formatErrorBody(atCap))
        assertNull(formatErrorBody(ByteArray(MAX_ERROR_BODY_BYTES + 1) { 'a'.code.toByte() }))
    }

    @Test
    fun `a body just under the size cap is still shown`() {
        val underCap = ByteArray(MAX_ERROR_BODY_BYTES - 1) { 'a'.code.toByte() }
        assertEquals(MAX_ERROR_BODY_BYTES - 1, formatErrorBody(underCap)?.length)
    }

    @Test
    fun `a body containing a non-printable byte is hex-dumped to the first 16 bytes`() {
        val binary = ByteArray(32) { it.toByte() }
        assertEquals(
            "00 01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f ...",
            formatErrorBody(binary),
        )
    }

    @Test
    fun `a hex dump of a body shorter than 16 bytes dumps only what is there`() {
        val binary = byteArrayOf(0x00, 0xff.toByte(), 0x41)
        assertEquals("00 ff 41 ...", formatErrorBody(binary))
    }

    @Test
    fun `high bytes above 126 are treated as binary rather than text`() {
        val body = byteArrayOf('h'.code.toByte(), 'i'.code.toByte(), 0x80.toByte())
        assertEquals("68 69 80 ...", formatErrorBody(body))
    }
}
