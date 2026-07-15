package yuku.alkitab.songs.newdoc

import yuku.kpri.model.Lyric
import yuku.kpri.model.Song
import yuku.kpri.model.Verse
import yuku.kpri.model.VerseKind

/**
 * Version-agnostic decoder for the marshalled `song_info.data` byte buffer,
 * without `android.os.Parcel`. Required because the Android 13 `Parcel`
 * wire format changed — a device that stored a song pre-13 and upgraded
 * can fail to `Parcel.unmarshall()` its own BLOB. This decoder replicates
 * the wire bytes directly and auto-detects which layout it is reading.
 *
 * Supports exactly what songs use: [readInt], [Reader.readString]
 * (`writeString16`), string lists, and a typed list reader understanding
 * only `VAL_NULL` (-1) and `VAL_PARCELABLE` (4). Anything else is a hard
 * error.
 */
object LegacyParcelDecoder {
    private const val VAL_NULL = -1
    private const val VAL_PARCELABLE = 4
    private const val CLASS_NAME_LYRIC = "yuku.kpri.model.Lyric"
    private const val CLASS_NAME_VERSE = "yuku.kpri.model.Verse"

    private enum class Format { LEGACY, ANDROID13 }

    private class Reader(private val buf: ByteArray) {
        var pos = 0
        private var format: Format? = null

        fun readInt(): Int {
            require(pos + 4 <= buf.size) { "Unexpected end of buffer reading int at $pos" }
            val v = (buf[pos].toInt() and 0xff) or
                ((buf[pos + 1].toInt() and 0xff) shl 8) or
                ((buf[pos + 2].toInt() and 0xff) shl 16) or
                ((buf[pos + 3].toInt() and 0xff) shl 24)
            pos += 4
            return v
        }

        private fun peekInt(): Int {
            val save = pos
            val v = readInt()
            pos = save
            return v
        }

        /** `writeString16`: int length in UTF-16 code units (-1 = null), then that many
         * UTF-16 chars plus a NUL terminator, padded to a 4-byte boundary. */
        fun readString(): String? {
            val len = readInt()
            if (len < 0) return null
            require(pos + (len + 1) * 2 <= buf.size) { "Unexpected end of buffer reading string of length $len at $pos" }
            val chars = CharArray(len)
            for (i in 0 until len) {
                chars[i] = ((buf[pos].toInt() and 0xff) or ((buf[pos + 1].toInt() and 0xff) shl 8)).toChar()
                pos += 2
            }
            pos += 2 // NUL terminator
            val byteLenWithNul = (len + 1) * 2
            pos += (4 - (byteLenWithNul % 4)) % 4 // pad to 4 bytes
            return String(chars)
        }

        fun readStringList(): MutableList<String> {
            val count = readInt()
            val res = ArrayList<String>(if (count > 0) count else 0)
            for (i in 0 until count) {
                res.add(readString() ?: throw IllegalStateException("Unexpected null element in string list"))
            }
            return res
        }

        /**
         * Reads the class-name string of a `VAL_PARCELABLE` element (the
         * tag itself has already been consumed by the caller). Detects and
         * locks the Android-13-vs-legacy layout on the first call (design
         * §4.3): both `Lyric` and `Verse` class names are exactly 21
         * (`0x15`) UTF-16 chars, and a real Android-13 length prefix is far
         * larger, so peeking 21 disambiguates unambiguously.
         */
        fun readParcelableClassNameAndLockFormat(): String {
            if (format == null) {
                val peeked = peekInt()
                if (peeked == 21) {
                    val save = pos
                    val name = readString()
                    if (name == CLASS_NAME_LYRIC || name == CLASS_NAME_VERSE) {
                        format = Format.LEGACY
                        return name
                    }
                    pos = save // not a legacy class name at that length; rewind and try Android13
                }
                format = Format.ANDROID13
            }
            if (format == Format.ANDROID13) {
                readInt() // length prefix (byte size of the serialized value); decoder ignores it
            }
            return readString() ?: throw IllegalStateException("Parcelable class name was null")
        }
    }

    @JvmStatic
    fun decode(buf: ByteArray, dataFormatVersion: Int): Song {
        val r = Reader(buf)
        val song = Song()
        song.code = r.readString()
        song.title = r.readString()
        song.title_original = r.readString()
        song.authors_lyric = r.readStringList()
        song.authors_music = r.readStringList()
        song.tune = r.readString()
        song.keySignature = r.readString()
        song.timeSignature = r.readString()
        song.lyrics = readParcelableList(r) { readLyric(r) }
        if (dataFormatVersion >= 2) {
            song.scriptureReferences = r.readString()
        }
        return song
    }

    /** Mirrors `Parcel.readList` into a pre-created list: `-1` yields an empty (not null) list;
     * individual elements may themselves be `VAL_NULL` — [Song.lyrics] can contain a null [Lyric]. */
    private fun <T> readParcelableList(r: Reader, readElement: () -> T): MutableList<T?> {
        val count = r.readInt()
        val res = ArrayList<T?>(if (count > 0) count else 0)
        for (i in 0 until count) {
            when (val tag = r.readInt()) {
                VAL_NULL -> res.add(null)
                VAL_PARCELABLE -> res.add(readElement())
                else -> throw IllegalStateException("Unsupported writeValue tag: $tag")
            }
        }
        return res
    }

    private fun readLyric(r: Reader): Lyric {
        val className = r.readParcelableClassNameAndLockFormat()
        if (className != CLASS_NAME_LYRIC) throw IllegalStateException("Expected $CLASS_NAME_LYRIC, got $className")
        val lyric = Lyric()
        lyric.caption = r.readString()
        lyric.verses = readParcelableList(r) { readVerse(r) }
        return lyric
    }

    private fun readVerse(r: Reader): Verse {
        val className = r.readParcelableClassNameAndLockFormat()
        if (className != CLASS_NAME_VERSE) throw IllegalStateException("Expected $CLASS_NAME_VERSE, got $className")
        val verse = Verse()
        verse.ordering = r.readInt() // consumed but discarded: never used for display
        val kindValue = r.readInt()
        verse.kind = VerseKind.entries.getOrNull(kindValue) ?: throw IllegalStateException("Unknown VerseKind value: $kindValue")
        verse.lines = r.readStringList()
        return verse
    }
}
