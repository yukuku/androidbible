package yuku.alkitab.songs.parcel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import yuku.kpri.model.Lyric
import yuku.kpri.model.Song
import yuku.kpri.model.Verse
import yuku.kpri.model.VerseKind

/**
 * Unit tests for [CustomParcelDecoder].
 *
 * These tests construct reference byte buffers that mimic the output of
 * [android.os.Parcel] for both pre-Android 13 and Android 13+ formats,
 * then assert the decoder produces identical [Song] models from both.
 */
class CustomParcelDecoderTest {

    /**
     * Build a legacy-format buffer (pre-Android 13).
     */
    private fun buildLegacyBuffer(song: Song, dataFormatVersion: Int): ByteArray {
        val w = ParcelWriter()
        w.writeString(song.code)
        w.writeString(song.title)
        w.writeString(song.title_original)
        w.writeStringList(song.authors_lyric)
        w.writeStringList(song.authors_music)
        w.writeString(song.tune)
        w.writeString(song.keySignature)
        w.writeString(song.timeSignature)
        w.writeInt(song.lyrics?.size ?: -1)
        song.lyrics?.forEach { lyric ->
            w.writeInt(4) // VAL_PARCELABLE
            w.writeString("yuku.kpri.model.Lyric")
            w.writeString(lyric.caption)
            w.writeInt(lyric.verses?.size ?: -1)
            lyric.verses?.forEach { verse ->
                w.writeInt(4) // VAL_PARCELABLE
                w.writeString("yuku.kpri.model.Verse")
                w.writeInt(verse.ordering)
                w.writeInt(verse.kind.value)
                w.writeStringList(verse.lines)
            }
        }
        if (dataFormatVersion >= 2) {
            w.writeString(song.scriptureReferences)
        }
        return w.toByteArray()
    }

    /**
     * Build an Android 13+ format buffer.
     */
    private fun buildAndroid13Buffer(song: Song, dataFormatVersion: Int): ByteArray {
        val w = ParcelWriter()
        w.writeString(song.code)
        w.writeString(song.title)
        w.writeString(song.title_original)
        w.writeStringList(song.authors_lyric)
        w.writeStringList(song.authors_music)
        w.writeString(song.tune)
        w.writeString(song.keySignature)
        w.writeString(song.timeSignature)
        w.writeInt(song.lyrics?.size ?: -1)
        song.lyrics?.forEach { lyric ->
            // Measure lyric payload
            val lyricPayload = buildLyricPayload(lyric)
            w.writeInt(4) // VAL_PARCELABLE
            w.writeInt(lyricPayload.size)
            w.writeBytes(lyricPayload)
        }
        if (dataFormatVersion >= 2) {
            w.writeString(song.scriptureReferences)
        }
        return w.toByteArray()
    }

    private fun buildLyricPayload(lyric: Lyric): ByteArray {
        val w = ParcelWriter()
        w.writeString("yuku.kpri.model.Lyric")
        w.writeString(lyric.caption)
        w.writeInt(lyric.verses?.size ?: -1)
        lyric.verses?.forEach { verse ->
            val versePayload = buildVersePayload(verse)
            w.writeInt(4) // VAL_PARCELABLE
            w.writeInt(versePayload.size)
            w.writeBytes(versePayload)
        }
        return w.toByteArray()
    }

    private fun buildVersePayload(verse: Verse): ByteArray {
        val w = ParcelWriter()
        w.writeString("yuku.kpri.model.Verse")
        w.writeInt(verse.ordering)
        w.writeInt(verse.kind.value)
        w.writeStringList(verse.lines)
        return w.toByteArray()
    }

    @Test
    fun `decodeSong produces identical model from legacy and Android 13 buffers`() {
        val song = makeSampleSong()
        val legacyBuf = buildLegacyBuffer(song, 3)
        val android13Buf = buildAndroid13Buffer(song, 3)

        val decodedLegacy = CustomParcelDecoder.decodeSong(legacyBuf, 3)
        val decodedAndroid13 = CustomParcelDecoder.decodeSong(android13Buf, 3)

        assertSongsEqual(song, decodedLegacy)
        assertSongsEqual(song, decodedAndroid13)
    }

    @Test
    fun `decodeSong handles null fields correctly`() {
        val song = Song().apply {
            code = "1"
            title = "Test"
            title_original = null
            authors_lyric = null
            authors_music = null
            tune = null
            keySignature = null
            timeSignature = null
            lyrics = null
            scriptureReferences = null
        }
        val legacyBuf = buildLegacyBuffer(song, 3)
        val android13Buf = buildAndroid13Buffer(song, 3)

        val decodedLegacy = CustomParcelDecoder.decodeSong(legacyBuf, 3)
        val decodedAndroid13 = CustomParcelDecoder.decodeSong(android13Buf, 3)

        assertSongsEqual(song, decodedLegacy)
        assertSongsEqual(song, decodedAndroid13)
    }

    @Test
    fun `decodeSong handles empty lists`() {
        val song = Song().apply {
            code = "1"
            title = "Test"
            title_original = "Original"
            authors_lyric = emptyList()
            authors_music = emptyList()
            tune = "Tune"
            keySignature = "C"
            timeSignature = "4/4"
            lyrics = listOf(
                Lyric().apply {
                    caption = "Lyric 1"
                    verses = listOf(
                        Verse().apply {
                            ordering = 1
                            kind = VerseKind.NORMAL
                            lines = emptyList()
                        }
                    )
                }
            )
            scriptureReferences = "Gen 1:1"
        }
        val legacyBuf = buildLegacyBuffer(song, 3)
        val android13Buf = buildAndroid13Buffer(song, 3)

        val decodedLegacy = CustomParcelDecoder.decodeSong(legacyBuf, 3)
        val decodedAndroid13 = CustomParcelDecoder.decodeSong(android13Buf, 3)

        assertSongsEqual(song, decodedLegacy)
        assertSongsEqual(song, decodedAndroid13)
    }

    @Test
    fun `decodeSong handles multi group lyrics with refrain and text verses`() {
        val song = Song().apply {
            code = "25"
            title = "Malam Kudus"
            title_original = "Silent Night"
            authors_lyric = listOf("Joseph Mohr")
            authors_music = listOf("Franz X. Gruber")
            tune = "STILLE NACHT"
            keySignature = "1=Bes"
            timeSignature = "6/8"
            lyrics = listOf(
                Lyric().apply {
                    caption = null
                    verses = listOf(
                        Verse().apply {
                            ordering = 1
                            kind = VerseKind.NORMAL
                            lines = listOf("Line 1", "Line 2")
                        },
                        Verse().apply {
                            ordering = 2
                            kind = VerseKind.REFRAIN
                            lines = listOf("Ref line")
                        },
                        Verse().apply {
                            ordering = 3
                            kind = VerseKind.TEXT
                            lines = listOf("Text line")
                        }
                    )
                },
                Lyric().apply {
                    caption = "English"
                    verses = listOf(
                        Verse().apply {
                            ordering = 1
                            kind = VerseKind.NORMAL
                            lines = listOf("English line 1")
                        }
                    )
                }
            )
            scriptureReferences = "Luke 2:8-14"
        }
        val legacyBuf = buildLegacyBuffer(song, 3)
        val android13Buf = buildAndroid13Buffer(song, 3)

        val decodedLegacy = CustomParcelDecoder.decodeSong(legacyBuf, 3)
        val decodedAndroid13 = CustomParcelDecoder.decodeSong(android13Buf, 3)

        assertSongsEqual(song, decodedLegacy)
        assertSongsEqual(song, decodedAndroid13)
    }

    @Test(expected = IllegalStateException::class)
    fun `decodeSong throws on unsupported type tag`() {
        val w = ParcelWriter()
        w.writeString("code")
        w.writeString("title")
        w.writeString(null)
        w.writeStringList(null)
        w.writeStringList(null)
        w.writeString(null)
        w.writeString(null)
        w.writeString(null)
        w.writeInt(1)
        w.writeInt(99) // unsupported tag
        val buf = w.toByteArray()
        CustomParcelDecoder.decodeSong(buf, 3)
    }

    @Test(expected = IllegalStateException::class)
    fun `decodeSong throws on unknown parcelable class`() {
        val w = ParcelWriter()
        w.writeString("code")
        w.writeString("title")
        w.writeString(null)
        w.writeStringList(null)
        w.writeStringList(null)
        w.writeString(null)
        w.writeString(null)
        w.writeString(null)
        w.writeInt(1)
        w.writeInt(4) // VAL_PARCELABLE
        w.writeString("unknown.Class")
        val buf = w.toByteArray()
        CustomParcelDecoder.decodeSong(buf, 3)
    }

    @Test
    fun `decodeSong respects dataFormatVersion for scriptureReferences`() {
        val song = Song().apply {
            code = "1"
            title = "Test"
            title_original = null
            authors_lyric = null
            authors_music = null
            tune = null
            keySignature = null
            timeSignature = null
            lyrics = null
            scriptureReferences = "John 3:16"
        }
        // dataFormatVersion = 1 should NOT read scriptureReferences
        val w = ParcelWriter()
        w.writeString(song.code)
        w.writeString(song.title)
        w.writeString(song.title_original)
        w.writeStringList(song.authors_lyric)
        w.writeStringList(song.authors_music)
        w.writeString(song.tune)
        w.writeString(song.keySignature)
        w.writeString(song.timeSignature)
        w.writeInt(-1) // lyrics = null
        // NO scriptureReferences written for version 1
        val buf = w.toByteArray()

        val decoded = CustomParcelDecoder.decodeSong(buf, 1)
        assertNull(decoded.scriptureReferences)
    }

    private fun makeSampleSong(): Song {
        return Song().apply {
            code = "25"
            title = "Malam Kudus"
            title_original = "Silent Night"
            authors_lyric = listOf("Joseph Mohr")
            authors_music = listOf("Franz X. Gruber")
            tune = "STILLE NACHT"
            keySignature = "1=Bes"
            timeSignature = "6/8"
            lyrics = listOf(
                Lyric().apply {
                    caption = null
                    verses = listOf(
                        Verse().apply {
                            ordering = 1
                            kind = VerseKind.NORMAL
                            lines = listOf("Malam Kudus, sunyi senyap,")
                        }
                    )
                }
            )
            scriptureReferences = "Luke 2:8-14"
        }
    }

    private fun assertSongsEqual(expected: Song, actual: Song) {
        assertEquals(expected.code, actual.code)
        assertEquals(expected.title, actual.title)
        assertEquals(expected.title_original, actual.title_original)
        assertEquals(expected.authors_lyric, actual.authors_lyric)
        assertEquals(expected.authors_music, actual.authors_music)
        assertEquals(expected.tune, actual.tune)
        assertEquals(expected.keySignature, actual.keySignature)
        assertEquals(expected.timeSignature, actual.timeSignature)
        assertEquals(expected.scriptureReferences, actual.scriptureReferences)

        assertEquals(expected.lyrics?.size, actual.lyrics?.size)
        expected.lyrics?.zip(actual.lyrics ?: emptyList())?.forEach { (expLyric, actLyric) ->
            assertEquals(expLyric.caption, actLyric.caption)
            assertEquals(expLyric.verses?.size, actLyric.verses?.size)
            expLyric.verses?.zip(actLyric.verses ?: emptyList())?.forEach { (expVerse, actVerse) ->
                assertEquals(expVerse.ordering, actVerse.ordering)
                assertEquals(expVerse.kind, actVerse.kind)
                assertEquals(expVerse.lines, actVerse.lines)
            }
        }
    }

    /**
     * A faithful re-implementation of the subset of [android.os.Parcel] write methods
     * that songs use, for generating test fixtures.
     */
    private class ParcelWriter {
        private val buf = java.io.ByteArrayOutputStream()

        fun writeInt(value: Int) {
            buf.write(value and 0xFF)
            buf.write((value shr 8) and 0xFF)
            buf.write((value shr 16) and 0xFF)
            buf.write((value shr 24) and 0xFF)
        }

        fun writeString(value: String?) {
            if (value == null) {
                writeInt(-1)
                return
            }
            writeInt(value.length)
            for (ch in value) {
                buf.write(ch.code and 0xFF)
                buf.write((ch.code shr 8) and 0xFF)
            }
            // NUL terminator
            buf.write(0)
            buf.write(0)
            // Pad to 4-byte boundary
            val totalBytes = 4 + (value.length + 1) * 2
            val padding = (4 - (totalBytes % 4)) % 4
            repeat(padding) { buf.write(0) }
        }

        fun writeStringList(list: List<String>?) {
            if (list == null) {
                writeInt(-1)
                return
            }
            writeInt(list.size)
            for (s in list) {
                writeString(s)
            }
        }

        fun writeBytes(bytes: ByteArray) {
            buf.write(bytes)
        }

        fun toByteArray(): ByteArray = buf.toByteArray()
    }
}
