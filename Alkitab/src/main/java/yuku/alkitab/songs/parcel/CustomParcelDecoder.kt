package yuku.alkitab.songs.parcel

import yuku.kpri.model.Lyric
import yuku.kpri.model.Song
import yuku.kpri.model.Verse
import yuku.kpri.model.VerseKind

/**
 * A pure Kotlin/JVM decoder that reads Android Parcel marshalled bytes directly,
 * without using [android.os.Parcel].
 *
 * This decoder supports both pre-Android 13 and Android 13+ Parcel formats.
 * It is specifically tailored for decoding marshalled [Song] objects that were
 * written by [Song.writeToParcelCompat] and then [android.os.Parcel.marshall].
 *
 * The Android 13 format divergence is a 4-byte length prefix inserted after the
 * [VAL_PARCELABLE] type tag and before the class-name string. Detection happens
 * once at the first [VAL_PARCELABLE] element and is then locked for the whole buffer.
 */
object CustomParcelDecoder {
    private const val VAL_NULL = -1
    private const val VAL_PARCELABLE = 4

    private const val CLASS_NAME_LYRIC = "yuku.kpri.model.Lyric"
    private const val CLASS_NAME_VERSE = "yuku.kpri.model.Verse"
    private const val CLASS_NAME_LENGTH = 21

    /**
     * Decode a marshalled Song byte buffer.
     *
     * @param buf The marshalled Parcel bytes
     * @param dataFormatVersion The data format version (2 or 3)
     * @return Decoded Song object
     * @throws IllegalStateException if the buffer is malformed or contains unsupported types
     */
    fun decodeSong(buf: ByteArray, dataFormatVersion: Int): Song {
        val reader = ParcelReader(buf)
        val song = Song()
        song.code = reader.readString()
        song.title = reader.readString()
        song.title_original = reader.readString()
        song.authors_lyric = reader.readStringList()
        song.authors_music = reader.readStringList()
        song.tune = reader.readString()
        song.keySignature = reader.readString()
        song.timeSignature = reader.readString()
        song.lyrics = reader.readLyricList()
        if (dataFormatVersion >= 2) {
            song.scriptureReferences = reader.readString()
        }
        return song
    }

    /**
     * Parcel format variants. The only divergence is the presence of a 4-byte
     * length prefix after [VAL_PARCELABLE] in Android 13+.
     */
    private enum class ParcelFormat {
        /** Pre-Android 13 format: tag → class-name string → fields. */
        LEGACY,

        /** Android 13+ format: tag → byteLength int → class-name string → fields. */
        ANDROID13
    }

    /**
     * Internal reader that wraps a byte buffer and provides sequential access
     * to Android Parcel marshalled values.
     *
     * All reads are little-endian and respect 4-byte alignment padding.
     */
    private class ParcelReader(private val buf: ByteArray) {
        private var pos = 0
        private var format: ParcelFormat? = null

        /**
         * Read a 32-bit signed integer in little-endian byte order.
         */
        fun readInt(): Int {
            checkRemaining(4)
            val result = (buf[pos].toInt() and 0xFF) or
                ((buf[pos + 1].toInt() and 0xFF) shl 8) or
                ((buf[pos + 2].toInt() and 0xFF) shl 16) or
                ((buf[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return result
        }

        /**
         * Read a nullable UTF-16 LE string written by [android.os.Parcel.writeString].
         *
         * Format: int length (-1 for null), then [length] UTF-16 code units,
         * a NUL terminator, and padding to the next 4-byte boundary.
         */
        fun readString(): String? {
            val length = readInt()
            if (length == -1) return null
            checkRemaining((length + 1) * 2)
            val chars = CharArray(length)
            for (i in 0 until length) {
                val b0 = buf[pos].toInt() and 0xFF
                val b1 = buf[pos + 1].toInt() and 0xFF
                chars[i] = ((b1 shl 8) or b0).toChar()
                pos += 2
            }
            // Skip NUL terminator
            pos += 2
            // Pad to 4-byte boundary
            val totalBytes = 4 + (length + 1) * 2
            val padding = (4 - (totalBytes % 4)) % 4
            pos += padding
            return String(chars)
        }

        /**
         * Read a list of strings written by [android.os.Parcel.readStringList].
         *
         * Format: int count (-1 for null), then each element via [readString].
         *
         * @throws IllegalStateException if a null element is encountered inside the list
         */
        fun readStringList(): List<String>? {
            val count = readInt()
            if (count == -1) return null
            return List(count) { index ->
                readString()
                    ?: throw IllegalStateException(
                        "Unexpected null string in string list at index $index"
                    )
            }
        }

        /**
         * Read a list of [Lyric] objects written by [android.os.Parcel.writeList].
         *
         * Format: int size (-1 for null), then each element via [readValue].
         *
         * @throws IllegalStateException if a null or non-Lyric element is encountered
         */
        fun readLyricList(): List<Lyric>? {
            val size = readInt()
            if (size == -1) return null
            return List(size) { index ->
                when (val value = readValue()) {
                    is Lyric -> value
                    else -> throw IllegalStateException(
                        "Expected Lyric but got ${value?.javaClass?.name ?: "null"} at index $index"
                    )
                }
            }
        }

        /**
         * Read a list of [Verse] objects written by [android.os.Parcel.writeList].
         *
         * Format: int size (-1 for null), then each element via [readValue].
         *
         * @throws IllegalStateException if a null or non-Verse element is encountered
         */
        fun readVerseList(): List<Verse>? {
            val size = readInt()
            if (size == -1) return null
            return List(size) { index ->
                when (val value = readValue()) {
                    is Verse -> value
                    else -> throw IllegalStateException(
                        "Expected Verse but got ${value?.javaClass?.name ?: "null"} at index $index"
                    )
                }
            }
        }

        /**
         * Read a typed value written by [android.os.Parcel.writeValue].
         *
         * Supported tags:
         * - [VAL_NULL] (-1) → `null`
         * - [VAL_PARCELABLE] (4) → [Lyric] or [Verse], dispatched by class-name string
         *
         * @return [Lyric], [Verse], or `null`
         * @throws IllegalStateException for any unsupported type tag
         */
        fun readValue(): Any? {
            val tag = readInt()
            when (tag) {
                VAL_NULL -> return null
                VAL_PARCELABLE -> {
                    if (format == null) {
                        detectFormat()
                    }
                    if (format == ParcelFormat.ANDROID13) {
                        // Read and discard the byteLength prefix
                        readInt()
                    }
                    val className = readString()
                        ?: throw IllegalStateException("Parcelable class name must not be null")
                    return when (className) {
                        CLASS_NAME_LYRIC -> readLyric()
                        CLASS_NAME_VERSE -> readVerse()
                        else -> throw IllegalStateException("Unknown parcelable class: $className")
                    }
                }
                else -> throw IllegalStateException("Unsupported value type tag: $tag")
            }
        }

        /**
         * Detect whether the buffer was written by a pre-Android 13 or Android 13+ Parcel.
         *
         * At the first [VAL_PARCELABLE] element, after reading the tag (4), we peek the
         * next int. In the legacy format that int is the class-name string length (21);
         * in Android 13+ it is the byteLength prefix, which is always larger than 21 for
         * a real Lyric/Verse payload.
         */
        private fun detectFormat() {
            checkRemaining(4)
            val peekInt = (buf[pos].toInt() and 0xFF) or
                ((buf[pos + 1].toInt() and 0xFF) shl 8) or
                ((buf[pos + 2].toInt() and 0xFF) shl 16) or
                ((buf[pos + 3].toInt() and 0xFF) shl 24)
            if (peekInt == CLASS_NAME_LENGTH) {
                val className = peekStringAt(pos + 4, CLASS_NAME_LENGTH)
                if (className == CLASS_NAME_LYRIC || className == CLASS_NAME_VERSE) {
                    format = ParcelFormat.LEGACY
                    return
                }
            }
            format = ParcelFormat.ANDROID13
        }

        /**
         * Peek [length] UTF-16 code units starting at [offset] without advancing [pos].
         */
        private fun peekStringAt(offset: Int, length: Int): String {
            checkBounds(offset, length * 2)
            val chars = CharArray(length)
            var p = offset
            for (i in 0 until length) {
                val b0 = buf[p].toInt() and 0xFF
                val b1 = buf[p + 1].toInt() and 0xFF
                chars[i] = ((b1 shl 8) or b0).toChar()
                p += 2
            }
            return String(chars)
        }

        /**
         * Read the fields of a [Lyric] object (after the class-name string has already
         * been consumed by [readValue]).
         */
        private fun readLyric(): Lyric {
            val lyric = Lyric()
            lyric.caption = readString()
            lyric.verses = readVerseList()
            return lyric
        }

        /**
         * Read the fields of a [Verse] object (after the class-name string has already
         * been consumed by [readValue]).
         */
        private fun readVerse(): Verse {
            val verse = Verse()
            verse.ordering = readInt()
            verse.kind = VerseKind.values()[readInt()]
            verse.lines = readStringList()
            return verse
        }

        private fun checkRemaining(need: Int) {
            if (pos + need > buf.size) {
                throw IllegalStateException(
                    "Buffer underflow: need $need bytes at position $pos, but buffer size is ${buf.size}"
                )
            }
        }

        private fun checkBounds(offset: Int, need: Int) {
            if (offset + need > buf.size) {
                throw IllegalStateException(
                    "Buffer bounds exceeded: need $need bytes at offset $offset, but buffer size is ${buf.size}"
                )
            }
        }
    }
}
