package yuku.alkitab.songs.newdoc

import java.io.ByteArrayOutputStream
import yuku.kpri.model.Lyric
import yuku.kpri.model.Song
import yuku.kpri.model.Verse

/**
 * Test-only re-implementation of the AOSP `Parcel` wire format for exactly
 * what `Song.writeToParcelCompat` / `Lyric.writeToParcel` /
 * `Verse.writeToParcel` write, in both the pre-13 and Android-13+ layouts.
 * This is the golden-fixture writer [LegacyParcelDecoder] is tested
 * against — Robolectric's `Parcel` shadow does not use the native Android
 * binary layout, so it cannot validate the decoder.
 */
enum class ParcelLayout { LEGACY, ANDROID13 }

class AospParcelWriter(private val layout: ParcelLayout) {
    private val out = ByteArrayOutputStream()

    private fun writeIntRaw(v: Int) {
        out.write(v and 0xff)
        out.write((v ushr 8) and 0xff)
        out.write((v ushr 16) and 0xff)
        out.write((v ushr 24) and 0xff)
    }

    /** `writeString16`: int length in UTF-16 code units (-1 = null), then that many UTF-16 chars
     * plus a NUL terminator, padded to a 4-byte boundary. */
    private fun writeStringRaw(s: String?) {
        if (s == null) {
            writeIntRaw(-1)
            return
        }
        writeIntRaw(s.length)
        val chars = ByteArrayOutputStream()
        for (c in s) {
            chars.write(c.code and 0xff)
            chars.write((c.code ushr 8) and 0xff)
        }
        chars.write(0) // NUL terminator (low byte)
        chars.write(0) // NUL terminator (high byte)
        val bytes = chars.toByteArray()
        out.write(bytes)
        repeat((4 - (bytes.size % 4)) % 4) { out.write(0) }
    }

    private fun writeStringListRaw(list: List<String>?) {
        if (list == null) {
            writeIntRaw(-1)
            return
        }
        writeIntRaw(list.size)
        for (s in list) writeStringRaw(s)
    }

    private fun writeParcelableTagAndClassName(className: String) {
        writeIntRaw(4) // VAL_PARCELABLE
        if (layout == ParcelLayout.ANDROID13) {
            // Real Android13 prefixes the byte-length of the serialized value; the decoder never
            // validates it, so any placeholder distinguishable from 21 works.
            writeIntRaw(0)
        }
        writeStringRaw(className)
    }

    private fun writeVerse(verse: Verse?) {
        if (verse == null) {
            writeIntRaw(-1) // VAL_NULL
            return
        }
        writeParcelableTagAndClassName("yuku.kpri.model.Verse")
        writeIntRaw(verse.ordering)
        writeIntRaw(verse.kind.value)
        writeStringListRaw(verse.lines)
    }

    private fun writeLyric(lyric: Lyric?) {
        if (lyric == null) {
            writeIntRaw(-1) // VAL_NULL
            return
        }
        writeParcelableTagAndClassName("yuku.kpri.model.Lyric")
        writeStringRaw(lyric.caption)
        writeIntRaw(lyric.verses?.size ?: -1)
        lyric.verses?.forEach { writeVerse(it) }
    }

    fun write(song: Song, dataFormatVersion: Int): ByteArray {
        writeStringRaw(song.code)
        writeStringRaw(song.title)
        writeStringRaw(song.title_original)
        writeStringListRaw(song.authors_lyric)
        writeStringListRaw(song.authors_music)
        writeStringRaw(song.tune)
        writeStringRaw(song.keySignature)
        writeStringRaw(song.timeSignature)
        writeIntRaw(song.lyrics?.size ?: -1)
        song.lyrics?.forEach { writeLyric(it) }
        if (dataFormatVersion >= 2) {
            writeStringRaw(song.scriptureReferences)
        }
        return out.toByteArray()
    }
}
