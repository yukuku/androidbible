package yuku.alkitab.songs.newdoc

import android.app.Application
import android.os.Parcel
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.songs.SongBookUtil
import yuku.alkitab.songs.SongFilter
import yuku.kpri.model.Lyric
import yuku.kpri.model.Song
import yuku.kpri.model.Verse
import yuku.kpri.model.VerseKind

/**
 * Drives a shared corpus of representative legacy [Song] fixtures through
 * both the pre-migration client algorithms (kept here only as reference
 * implementations for comparison — production no longer has them) and the
 * new [SongDocument] path, and asserts equivalence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PortableSongsBridgeTest {

    // region shared fixture corpus

    private fun verse(ordering: Int, kind: VerseKind, vararg lines: String): Verse =
        Verse().apply {
            this.ordering = ordering
            this.kind = kind
            this.lines = lines.toMutableList()
        }

    private fun lyric(caption: String?, vararg verses: Verse): Lyric =
        Lyric().apply {
            this.caption = caption
            this.verses = verses.toMutableList()
        }

    /** Multi-group lyrics, inline `<u>`, title_original, tune, two authors, musical key/time,
     * scripture. */
    private fun kri25(): Song = Song().apply {
        code = "25"
        title = "Malam Kudus"
        title_original = "Silent Night"
        authors_lyric = mutableListOf("Joseph Mohr")
        authors_music = mutableListOf("Franz X. Gruber")
        tune = "STILLE NACHT"
        keySignature = "1=Bes"
        timeSignature = "6/8"
        scriptureReferences = "Luke.2.1-Luke.2.20"
        lyrics = mutableListOf(
            lyric(null, verse(1, VerseKind.NORMAL, "Malam Kudus, sunyi senyap,", "<u>Sia</u>pa yang b'lum lelap;")),
            lyric(null, verse(1, VerseKind.NORMAL, "Silent night, holy night,", "All is calm, all is bright")),
        )
    }

    /** null title_original/tune/keySignature/timeSignature/scriptureReferences, empty author lists. */
    private fun nullsAndEmpties(): Song = Song().apply {
        code = "N1"
        title = "No Frills"
        title_original = null
        authors_lyric = mutableListOf()
        authors_music = mutableListOf()
        tune = null
        keySignature = null
        timeSignature = null
        scriptureReferences = null
        lyrics = mutableListOf(lyric(null, verse(1, VerseKind.NORMAL, "Just one line.")))
    }

    /** REFRAIN and TEXT verses interleaved with NORMAL; multi-group lyric with explicit captions. */
    private fun mixedKinds(): Song = Song().apply {
        code = "M1"
        title = "Mixed Kinds"
        title_original = "Original Mixed"
        authors_lyric = mutableListOf("Author A")
        authors_music = mutableListOf("Author B")
        tune = "TUNEY"
        keySignature = "C"
        timeSignature = "4/4"
        scriptureReferences = "John.3.16"
        lyrics = mutableListOf(
            lyric(
                "Bait Pertama",
                verse(1, VerseKind.NORMAL, "Verse one line one"),
                verse(2, VerseKind.TEXT, "Spoken instruction"),
                verse(3, VerseKind.REFRAIN, "Refrain line"),
                verse(4, VerseKind.NORMAL, "Verse two line one"),
            ),
            lyric("Bait Kedua", verse(1, VerseKind.NORMAL, "Second group line")),
        )
    }

    /** lines with `<u>/<b>/<i>` and a literal `&` needing escaping at render time. */
    private fun inlineStyled(): Song = Song().apply {
        code = "I1"
        title = "Inline & Styles"
        title_original = "Orig <title>"
        authors_lyric = mutableListOf("A & B")
        authors_music = mutableListOf()
        tune = null
        keySignature = "D"
        timeSignature = null
        scriptureReferences = null
        lyrics = mutableListOf(
            lyric(
                null,
                verse(
                    1,
                    VerseKind.NORMAL,
                    "Plain & simple line",
                    "<u>Underlined</u> and <b>bold</b> and <i>italic</i>",
                ),
            ),
        )
    }

    private fun songs(): List<Song> = listOf(kri25(), nullsAndEmpties(), mixedKinds(), inlineStyled())

    // endregion

    // region reference (pre-migration) algorithms — kept only for this bridge test's comparison

    private fun legacySongToHtml(song: Song, forPatchText: Boolean): String {
        val sb = StringBuilder()
        for (i in song.lyrics.indices) {
            val lyric = song.lyrics[i] ?: continue
            sb.append("<div class='lyric'>")
            if (song.lyrics.size > 1 || lyric.caption != null) {
                if (lyric.caption != null) {
                    sb.append("<div class='lyric_caption'>").append(lyric.caption).append("</div>")
                } else {
                    sb.append("<div class='lyric_caption'>Versi ").append(i + 1).append("</div>")
                }
            }
            var verseNumberNormal = 0
            var verseNumberReff = 0
            for (verseItem in lyric.verses) {
                sb.append("<div class='verse").append(if (verseItem.kind == VerseKind.REFRAIN) " refrain" else "").append("'>")
                when (verseItem.kind) {
                    VerseKind.REFRAIN -> verseNumberReff++
                    VerseKind.NORMAL -> verseNumberNormal++
                    else -> {}
                }
                if (forPatchText) {
                    when (verseItem.kind) {
                        VerseKind.REFRAIN -> sb.append("reff ").append(verseNumberReff)
                        VerseKind.NORMAL -> sb.append(verseNumberNormal)
                        else -> {}
                    }
                } else {
                    when (verseItem.kind) {
                        VerseKind.REFRAIN -> sb.append("<div class='verse_ordering'>").append(verseNumberReff).append("</div>")
                        VerseKind.NORMAL -> sb.append("<div class='verse_ordering'>").append(verseNumberNormal).append("</div>")
                        else -> {}
                    }
                }
                sb.append("<div class='verse_content'>")
                for (line in verseItem.lines) {
                    if (forPatchText) {
                        sb.append(line).append("<br/>")
                    } else {
                        sb.append("<p class='line'>").append(line).append("</p>")
                    }
                }
                sb.append("</div>")
                sb.append("</div>")
            }
            sb.append("</div>")
        }
        return sb.toString()
    }

    // endregion

    // region current-code control (Robolectric)

    private fun assertSongFieldsEqual(expected: Song, actual: Song?) {
        assertNotNull(actual)
        actual!!
        assertEquals(expected.code, actual.code)
        assertEquals(expected.title, actual.title)
        assertEquals(expected.title_original, actual.title_original)
        assertEquals(expected.authors_lyric, actual.authors_lyric)
        assertEquals(expected.authors_music, actual.authors_music)
        assertEquals(expected.tune, actual.tune)
        assertEquals(expected.keySignature, actual.keySignature)
        assertEquals(expected.timeSignature, actual.timeSignature)
        assertEquals(expected.scriptureReferences, actual.scriptureReferences)
        assertEquals(expected.lyrics.size, actual.lyrics.size)
        for (i in expected.lyrics.indices) {
            val a = expected.lyrics[i]
            val b = actual.lyrics[i]
            assertEquals(a?.caption, b?.caption)
            assertEquals(a?.verses?.size, b?.verses?.size)
            for (j in (a?.verses ?: emptyList()).indices) {
                assertEquals(a!!.verses[j].ordering, b!!.verses[j].ordering)
                assertEquals(a.verses[j].kind, b.verses[j].kind)
                assertEquals(a.verses[j].lines, b.verses[j].lines)
            }
        }
    }

    @Test
    fun `the legacy Parcel round-trip through Song writeToParcelCompat and Parcel unmarshall still holds`() {
        for (song in songs()) {
            val p = Parcel.obtain()
            song.writeToParcelCompat(3, p, 0)
            val bytes = p.marshall()
            p.recycle()

            val p2 = Parcel.obtain()
            p2.unmarshall(bytes, 0, bytes.size)
            p2.setDataPosition(0)
            val roundTripped = Song.createFromParcelCompat(3, p2)
            p2.recycle()

            assertSongFieldsEqual(song, roundTripped)
        }
    }

    @Test
    fun `the legacy Java-serialized download round-trip still holds`() {
        for (song in songs()) {
            val baos = ByteArrayOutputStream()
            ObjectOutputStream(baos).use { it.writeObject(listOf(song)) }
            @Suppress("UNCHECKED_CAST")
            val result = java.io.ObjectInputStream(baos.toByteArray().inputStream()).use { it.readObject() } as List<Song>
            assertSongFieldsEqual(song, result[0])
        }
    }

    // endregion

    // region decode -> convert -> JSON round-trip (no data loss)

    @Test
    fun `LegacyParcelDecoder reconstructs the legacy model from faithful legacy-layout bytes`() {
        for (song in songs()) {
            val bytes = AospParcelWriter(ParcelLayout.LEGACY).write(song, 3)
            val decoded = LegacyParcelDecoder.decode(bytes, 3)
            assertSongFieldsEqual(song, decoded)
        }
    }

    @Test
    fun `decode-convert-encode-decode round-trip is lossless, including the meta LegacySongConverter computed at conversion time`() {
        for (song in songs()) {
            val bytes = AospParcelWriter(ParcelLayout.LEGACY).write(song, 3)
            val decodedSong = LegacyParcelDecoder.decode(bytes, 3)
            val doc = LegacySongConverter.convert(decodedSong)
            val json = SongDocumentJson.encode(doc)
            val doc2 = SongDocumentJson.decode(json)
            assertEquals(doc, doc2)
        }
    }

    @Test
    fun `decode trusts meta from the JSON as-is instead of recomputing it from blocks`() {
        // meta deliberately disagrees with the "title" block below it. authoring tools (kidung-data's
        // OutputJson, or the app's own LegacySongConverter) are the trusted producers of meta; decode
        // must not silently "fix" a mismatch by re-deriving it from blocks.
        val json = """
            {"v":1,"code":"X1","meta":{"title":"Trusted Title","title_original":null},
             "blocks":[{"type":"p","role":"title","content":"Different Block Text"}]}
        """.trimIndent()
        val doc = SongDocumentJson.decode(json)
        assertEquals("Trusted Title", doc.meta.title)
    }

    @Test
    fun `the converter emits blocks in the app's layout order- title, title_original, tune, authors row, scripture, musical, lyric groups`() {
        val doc = LegacySongConverter.convert(kri25())
        val typeNames = doc.blocks.map { it::class.simpleName }
        assertEquals(
            listOf("PBlock", "PBlock", "PBlock", "RowBlock", "ScriptureBlock", "PBlock", "LyricBlock", "LyricBlock"),
            typeNames,
        )
        assertEquals("title", (doc.blocks[0] as PBlock).role)
        assertEquals("title_original", (doc.blocks[1] as PBlock).role)
        assertEquals("tune", (doc.blocks[2] as PBlock).role)
        assertEquals("musical", (doc.blocks[5] as PBlock).role)
        assertEquals("1=Bes 6/8", (doc.blocks[5] as PBlock).content.plainText())
    }

    @Test
    fun `the converter drops Verse ordering and maps VerseKind by name`() {
        val doc = LegacySongConverter.convert(mixedKinds())
        val firstLyric = doc.blocks.filterIsInstance<LyricBlock>()[0]
        val expectedKinds: List<yuku.alkitab.songs.newdoc.VerseKind> = listOf(
            yuku.alkitab.songs.newdoc.VerseKind.NORMAL,
            yuku.alkitab.songs.newdoc.VerseKind.TEXT,
            yuku.alkitab.songs.newdoc.VerseKind.REFRAIN,
            yuku.alkitab.songs.newdoc.VerseKind.NORMAL,
        )
        assertEquals(expectedKinds, firstLyric.verses.map { it.kind })
        assertTrue(firstLyric.verses.all { it.marker == null })
    }

    @Test
    fun `the converter parses inline u b i tags into spans`() {
        val doc = LegacySongConverter.convert(inlineStyled())
        val lines = doc.blocks.filterIsInstance<LyricBlock>()[0].verses[0].lines

        val plain = (lines[0] as VerseLine.Simple).line
        assertEquals(Line.Plain("Plain & simple line"), plain)

        val styled = (lines[1] as VerseLine.Simple).line as Line.Styled
        assertEquals(
            listOf(
                Span("Underlined", listOf("u")),
                Span(" and "),
                Span("bold", listOf("b")),
                Span(" and "),
                Span("italic", listOf("i")),
            ),
            styled.spans,
        )
    }

    // endregion

    // region render equivalence (old vs new)

    private fun stripTagsAndUnescape(html: String): String {
        val noTags = html.replace(Regex("<[^>]*>"), "")
        return noTags
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    private fun verseOrderings(html: String): List<String> =
        Regex("<div class='verse_ordering'>(.*?)</div>").findAll(html).map { it.groupValues[1] }.toList()

    private fun refrainCount(html: String): Int = Regex("class='verse refrain'").findAll(html).count()

    private fun lyricCaptions(html: String): List<String> =
        Regex("<div class='lyric_caption'>(.*?)</div>").findAll(html).map { stripTagsAndUnescape(it.groupValues[1]) }.toList()

    private fun lineTexts(html: String): List<String> =
        Regex("<p class='line'>(.*?)</p>").findAll(html).map { stripTagsAndUnescape(it.groupValues[1]) }.toList()

    @Test
    fun `render equivalence- same verse numbering, refrain markers, captions, and lines as the legacy renderer`() {
        for (song in songs()) {
            val legacyHtml = legacySongToHtml(song, false)
            val newHtml = SongDocumentRenderer.renderLyrics(LegacySongConverter.convert(song), false)

            assertEquals(verseOrderings(legacyHtml), verseOrderings(newHtml))
            assertEquals(refrainCount(legacyHtml), refrainCount(newHtml))
            assertEquals(lyricCaptions(legacyHtml), lyricCaptions(newHtml))
            assertEquals(lineTexts(legacyHtml), lineTexts(newHtml))
        }
    }

    @Test
    fun `SongDocumentText reproduces the header and lyric body shape of the legacy plain-text export`() {
        for (song in songs()) {
            val doc = LegacySongConverter.convert(song)
            val text = SongDocumentText.render(
                doc = doc,
                bookNameDisplay = "BOOK",
                scriptureReferencesText = null,
                versionCaption = { n -> "Versi $n" },
                refrainMarker = "Ref.:",
            )

            assertTrue(text.contains(song.code))
            song.title?.let { assertTrue(text.contains(it)) }
            song.title_original?.let { assertTrue(text.contains("($it)")) }
            for (lyricGroup in song.lyrics) {
                for (verseItem in lyricGroup.verses) {
                    for (line in verseItem.lines) {
                        assertTrue("expected text to contain line: $line", text.contains(stripTagsAndUnescape(line)))
                    }
                }
            }
        }
    }

    // endregion

    // region Android-13 vs pre-13 golden parcels

    @Test
    fun `legacy and Android13 layouts genuinely differ but the decoder produces identical models from both`() {
        for (song in songs()) {
            val legacyBytes = AospParcelWriter(ParcelLayout.LEGACY).write(song, 3)
            val a13Bytes = AospParcelWriter(ParcelLayout.ANDROID13).write(song, 3)
            assertNotEquals(legacyBytes.toList(), a13Bytes.toList())

            val songL = LegacyParcelDecoder.decode(legacyBytes, 3)
            val songA = LegacyParcelDecoder.decode(a13Bytes, 3)
            assertSongFieldsEqual(song, songL)
            assertSongFieldsEqual(song, songA)
        }
    }

    @Test
    fun `an unsupported writeValue tag throws IllegalStateException`() {
        // Song.lyrics count = 1, then a bogus tag (2) instead of VAL_NULL(-1) or VAL_PARCELABLE(4).
        val out = ByteArrayOutputStream()
        fun writeIntRaw(v: Int) {
            out.write(v and 0xff); out.write((v ushr 8) and 0xff); out.write((v ushr 16) and 0xff); out.write((v ushr 24) and 0xff)
        }
        fun writeStringRaw(s: String?) {
            if (s == null) { writeIntRaw(-1); return }
            writeIntRaw(s.length)
            for (c in s) { out.write(c.code and 0xff); out.write((c.code ushr 8) and 0xff) }
            out.write(0); out.write(0)
            val byteLenWithNul = (s.length + 1) * 2
            repeat((4 - (byteLenWithNul % 4)) % 4) { out.write(0) }
        }
        writeStringRaw("C1") // code
        writeStringRaw(null) // title
        writeStringRaw(null) // title_original
        writeIntRaw(-1) // authors_lyric
        writeIntRaw(-1) // authors_music
        writeStringRaw(null) // tune
        writeStringRaw(null) // keySignature
        writeStringRaw(null) // timeSignature
        writeIntRaw(1) // lyrics.size
        writeIntRaw(2) // bogus writeValue tag

        assertThrows(IllegalStateException::class.java) {
            LegacyParcelDecoder.decode(out.toByteArray(), 2)
        }
    }

    @Test
    fun `an unexpected class name at a Parcelable element throws IllegalStateException`() {
        // Legacy layout, but the first list element claims to be a Verse instead of a Lyric.
        val out = ByteArrayOutputStream()
        fun writeIntRaw(v: Int) {
            out.write(v and 0xff); out.write((v ushr 8) and 0xff); out.write((v ushr 16) and 0xff); out.write((v ushr 24) and 0xff)
        }
        fun writeStringRaw(s: String?) {
            if (s == null) { writeIntRaw(-1); return }
            writeIntRaw(s.length)
            for (c in s) { out.write(c.code and 0xff); out.write((c.code ushr 8) and 0xff) }
            out.write(0); out.write(0)
            val byteLenWithNul = (s.length + 1) * 2
            repeat((4 - (byteLenWithNul % 4)) % 4) { out.write(0) }
        }
        writeStringRaw("C1")
        writeStringRaw(null)
        writeStringRaw(null)
        writeIntRaw(-1)
        writeIntRaw(-1)
        writeStringRaw(null)
        writeStringRaw(null)
        writeStringRaw(null)
        writeIntRaw(1) // lyrics.size
        writeIntRaw(4) // VAL_PARCELABLE
        writeStringRaw("yuku.kpri.model.Verse") // wrong class name for a Lyric slot

        assertThrows(IllegalStateException::class.java) {
            LegacyParcelDecoder.decode(out.toByteArray(), 2)
        }
    }

    // endregion

    // region search equivalence

    @Test
    fun `SongFilter match on SongDocument agrees with SongFilter match on the legacy Song`() {
        val queries = listOf("25", "kudus", "silent", "gruber", "senyap", "nomatchxyz", "mixed", "instruction")
        for (song in songs()) {
            val doc = LegacySongConverter.convert(song)
            for (query in queries) {
                val cf = SongFilter.compileFilter(query)
                assertEquals(
                    "query=$query song=${song.code}",
                    SongFilter.match(song, cf),
                    SongFilter.match(doc, cf),
                )
            }
        }
    }

    // endregion

    // region legacy vs @doc convergence

    @Test
    fun `converted-legacy KRI 25 is structurally identical to hand-authored canonical doc JSON`() {
        val fromDoc = javaClass.getResourceAsStream("/songs/kri25-doc.json")!!.use {
            SongDocumentJson.decode(it.reader().readText())
        }
        val fromLegacy = LegacySongConverter.convert(kri25())
        assertEquals(fromDoc, fromLegacy)
    }

    // endregion

    // region download-wrapper parse

    @Test
    fun `SongBookUtil deserializeSongs parses a gzipped JSON song-book wrapper`() {
        val wrapper = SongDocumentJson.SongBookWrapper(
            dataFormatVersion = SongDocumentJson.DATA_FORMAT_VERSION,
            songs = songs().map(LegacySongConverter::convert),
        )
        val json = SongDocumentJson.encodeSongBook(wrapper)
        val gzipped = ByteArrayOutputStream().also { baos -> GZIPOutputStream(baos).use { it.write(json.toByteArray(Charsets.UTF_8)) } }.toByteArray()

        val result = SongBookUtil.deserializeSongs(gzipped.inputStream())

        assertEquals(songs().map { it.code }, result.map { it.code })
        assertEquals(songs().map { it.title }, result.map { it.meta.title })
    }

    @Test
    fun `isSupportedDataFormatVersion accepts only the JSON version`() {
        assertFalse(SongBookUtil.isSupportedDataFormatVersion(3))
        assertFalse(SongBookUtil.isSupportedDataFormatVersion(4))
        assertTrue(SongBookUtil.isSupportedDataFormatVersion(5))
    }

    @Test
    fun `the old Java-serialization download path is gone- a legacy-format payload fails to parse as JSON`() {
        val baos = ByteArrayOutputStream()
        ObjectOutputStream(baos).use { it.writeObject(listOf(kri25())) }
        val gzipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(baos.toByteArray()) } }.toByteArray()

        assertThrows(Exception::class.java) {
            SongBookUtil.deserializeSongs(gzipped.inputStream())
        }
    }

    // endregion
}
