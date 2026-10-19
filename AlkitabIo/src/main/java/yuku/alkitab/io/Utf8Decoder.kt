package yuku.alkitab.io

import java.io.IOException
import yuku.bintex.BintexReader

/**
 * UTF-8 decoder supporting Unicode code points through U+10FFFF.
 */
object Utf8Decoder {
    private val byteBuffer = ThreadLocal.withInitial { ByteArray(1000) }
    private val charBuffer = ThreadLocal.withInitial { CharArray(8000) }

    @JvmStatic
    fun toString(ba: ByteArray): String = toString(ba, 0, ba.size)

    @JvmStatic
    fun toString(ba: ByteArray, start: Int, length: Int): String = decodeString(ba, start, length, false)

    @JvmStatic
    fun toStringLowerCase(ba: ByteArray): String = toStringLowerCase(ba, 0, ba.size)

    @JvmStatic
    fun toStringLowerCase(ba: ByteArray, start: Int, length: Int): String = decodeString(ba, start, length, true)

    private fun decodeString(ba: ByteArray, start: Int, length: Int, isLowercased: Boolean): String {
        if (start < 0 || length < 0 || start > ba.size - length) {
            throw IndexOutOfBoundsException()
        }
        var buffer = charBuffer.get()!!
        if (buffer.size < length) {
            buffer = CharArray(length + 1000)
            charBuffer.set(buffer)
        }
        val count = decode(ba, start, length, buffer, 0, isLowercased)
        return String(buffer, 0, count)
    }

    private fun decode(input: ByteArray, start: Int, length: Int, output: CharArray, outputStart: Int, isLowercased: Boolean): Int {
        val end = start + length
        var i = start
        var pos = outputStart
        while (i < end) {
            val c0 = input[i].toInt() and 0xff
            if (c0 < 0x80) {
                // input 1 byte, output 7 bit
                output[pos++] = (if (isLowercased && c0 in 'A'.code..'Z'.code) c0 or 0x20 else c0).toChar()
                i++
                continue
            }

            var codePoint: Int
            if (c0 in 0xc2..0xdf && i + 1 < end && isContinuation(input[i + 1])) {
                // input 2 byte, output 5+6 = 11 bit
                codePoint = ((c0 and 0x1f) shl 6) or (input[i + 1].toInt() and 0x3f)
                i += 2
            } else if (c0 in 0xe0..0xef && i + 2 < end
                && isContinuation(input[i + 1]) && isContinuation(input[i + 2])
                && (c0 != 0xe0 || (input[i + 1].toInt() and 0xff) >= 0xa0)
                && (c0 != 0xed || (input[i + 1].toInt() and 0xff) < 0xa0)
            ) {
                // input 3 byte, output 4+6+6 = 16 bit
                codePoint = ((c0 and 0x0f) shl 12) or ((input[i + 1].toInt() and 0x3f) shl 6) or (input[i + 2].toInt() and 0x3f)
                i += 3
            } else if (c0 in 0xf0..0xf4 && i + 3 < end
                && isContinuation(input[i + 1]) && isContinuation(input[i + 2]) && isContinuation(input[i + 3])
                && (c0 != 0xf0 || (input[i + 1].toInt() and 0xff) >= 0x90)
                && (c0 != 0xf4 || (input[i + 1].toInt() and 0xff) < 0x90)
            ) {
                // input 4 byte, output 3+6+6+6 = 21 bit, encoded as a UTF-16 surrogate pair
                codePoint = ((c0 and 0x07) shl 18) or ((input[i + 1].toInt() and 0x3f) shl 12) or
                    ((input[i + 2].toInt() and 0x3f) shl 6) or (input[i + 3].toInt() and 0x3f)
                i += 4
            } else {
                // Consume only the invalid byte so recovery cannot swallow the next character or verse.
                output[pos++] = '\ufffd'
                i++
                continue
            }

            // Simple code-point casing preserves search offsets without expanding characters.
            if (isLowercased) codePoint = Character.toLowerCase(codePoint)
            if (codePoint < 0x10000) {
                output[pos++] = codePoint.toChar()
            } else {
                output[pos++] = Character.highSurrogate(codePoint)
                output[pos++] = Character.lowSurrogate(codePoint)
            }
        }
        return pos
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun isContinuation(value: Byte): Boolean = (value.toInt() and 0xc0) == 0x80

    @JvmStatic
    @Throws(IOException::class)
    fun toStringFromVersesWithPrependedLengths(br: BintexReader, verseCount: Int, isLowercased: Boolean): String {
        var bytes = byteBuffer.get()!!
        var chars = charBuffer.get()!!

        var charPos = 0
        for (v in 0 until verseCount) {
            val verseLength = br.readVarUint()

            if (verseLength > bytes.size) {
                bytes = ByteArray(verseLength + 100)
                byteBuffer.set(bytes)
            }

            val requiredLength = charPos + verseLength + 1 /*for separator*/
            if (requiredLength > chars.size) {
                chars = chars.copyOf(requiredLength + 1000)
                charBuffer.set(chars)
            }

            br.readRaw(bytes, 0, verseLength)
            charPos = decode(bytes, 0, verseLength, chars, charPos, isLowercased)

            // verse separator
            chars[charPos++] = '\n'
        }

        return String(chars, 0, charPos)
    }
}
