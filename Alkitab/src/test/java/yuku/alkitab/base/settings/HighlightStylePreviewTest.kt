package yuku.alkitab.base.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.test.core.app.ApplicationProvider
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.S
import yuku.alkitab.base.dialog.TypeHighlightDialog
import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.base.widget.VerseRendererCompose
import yuku.alkitab.model.Marker
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.Ari

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class HighlightStylePreviewTest {
    private lateinit var applied: S.CalculatedDimensions
    private val ari = 0x2a0301

    @Before
    fun setUp() {
        yuku.afw.App.initWithAppContext(ApplicationProvider.getApplicationContext())
        applied = S.CalculatedDimensions().apply {
            fontColor = 0xffeeeeee.toInt()
            fontRedColor = 0xffff8877.toInt()
            verseNumberColor = 0xff77bbff.toInt()
            backgroundColor = 0xff102030.toInt()
            highlightColorAdjustment = 1f
        }
        S.overrideAppliedDimensions(applied)
    }

    @Test
    fun `preview retains active red letter and verse number colors while adjusting only the highlight`() {
        val raw = "@@@0Normal @6Voice@5 @9italic@7"
        val original = VerseRendererCompose.render(true, ari, raw)
        for (amount in listOf(0f, 0.5f, 1f)) {
            val preview = HighlightStyleSheet.renderPreview(sample(raw, 0x00ff00), true, amount)
            assertEquals(original.text.text, preview.text.text)
            assertEquals(original.text.paragraphStyles, preview.text.paragraphStyles)
            assertTrue(preview.text.spanStyles.containsAll(original.text.spanStyles))
            assertTrue(preview.text.spanStyles.any { it.item.color == Color(applied.fontRedColor) })
            assertTrue(preview.text.spanStyles.any { it.item.color == Color(applied.verseNumberColor) })
            assertTrue(preview.text.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
            val band = preview.text.spanStyles.last()
            assertEquals(Color(Highlights.blendOver(0x00ff00, applied.backgroundColor, amount)), band.item.background)
            assertEquals(original.startPosAfterVerseNumber, band.start)
            assertEquals(original.text.length, band.end)
        }
        assertSame(applied, S.applied())
        assertEquals(0xff102030.toInt(), applied.backgroundColor)
        assertEquals(1f, applied.highlightColorAdjustment)
    }

    @Test
    fun `preview preserves ruby link and gutter metadata for the reader layout`() {
        val raw = "@@@1@6@<r=reading@>base@/@5 @<f1@>@/"
        val original = VerseRendererCompose.render(true, ari, raw)
        val preview = HighlightStyleSheet.renderPreview(sample(raw), true, 0.5f)
        assertTrue(original.rubies.isNotEmpty())
        assertTrue(original.inlineLinks.isNotEmpty())
        assertEquals(original.rubies, preview.rubies)
        assertEquals(original.inlineLinks, preview.inlineLinks)
        assertEquals(original.gutterVerseNumber, preview.gutterVerseNumber)
        assertEquals(raw, preview.sourceText)
        assertEquals(original.text.paragraphStyles, preview.text.paragraphStyles)
    }

    @Test
    fun `plain verses and hidden verse numbers retain their reader representation`() {
        for (raw in listOf("Plain verse", "@@@0Normal @6Voice@5")) {
            val original = VerseRendererCompose.render(false, ari, raw)
            val preview = HighlightStyleSheet.renderPreview(sample(raw), false, 0f)
            assertEquals(original.text.text, preview.text.text)
            assertEquals(original.gutterVerseNumber, preview.gutterVerseNumber)
            assertEquals(0, preview.text.spanStyles.last().start)
        }
    }

    @Test
    fun `preview prioritizes three saved highlights and retains their colors and partial ranges`() {
        val verses = chapter(8)
        val highlights = arrayOfNulls<Highlights.Info>(8)
        for ((index, color) in listOf(2 to 0x123456, 4 to 0x987654, 6 to 0x224466, 7 to 0xff00ff)) {
            highlights[index] = Highlights.Info().apply { colorRgb = color }
        }
        val saved = requireNotNull(highlights[4])
        saved.partial = Highlights.Info.Partial().apply {
            hashCode = Highlights.hashCode(verses.getVerse(4))
            startOffset = 2
            endOffset = 5
        }

        val samples = HighlightStyleSheet.selectSamples(Ari.toBookChapter(ari), verses, highlights)

        assertEquals(listOf(3, 5, 7), samples.map { Ari.toVerse(it.ari) })
        assertEquals(listOf(0x123456, 0x987654, 0x224466), samples.map { it.highlight.colorRgb })
        assertSame(saved, samples[1].highlight)
        for (amount in listOf(0f, 0.5f, 1f)) {
            val rendered = HighlightStyleSheet.renderPreview(samples[1], true, amount)
            val band = rendered.text.spanStyles.last()
            assertEquals(rendered.startPosAfterVerseNumber + 2, band.start)
            assertEquals(rendered.startPosAfterVerseNumber + 5, band.end)
            assertEquals(Color(Highlights.blendOver(0x987654, applied.backgroundColor, amount)), band.item.background)
        }
        assertEquals(1f, applied.highlightColorAdjustment)
    }

    @Test
    fun `missing saved highlights are filled by distinct unhighlighted verses in chapter order`() {
        val verses = chapter(6)
        for (savedCount in 0..2) {
            val highlights = arrayOfNulls<Highlights.Info>(6)
            for (index in 0 until savedCount) {
                highlights[5 - index] = Highlights.Info().apply { colorRgb = 0x123456 + index }
            }

            val samples = HighlightStyleSheet.selectSamples(Ari.toBookChapter(ari), verses, highlights)

            assertEquals(3, samples.size)
            assertEquals(3, samples.map { it.ari }.distinct().size)
            assertEquals(savedCount, samples.count { highlights[Ari.toVerse(it.ari) - 1] != null })
            val fillers = samples.filter { highlights[Ari.toVerse(it.ari) - 1] == null }
            assertEquals(listOf(0xffff00, 0x00ff00, 0x0080ff).take(3 - savedCount), fillers.map { it.highlight.colorRgb })
            assertTrue(fillers.all { it.highlight.colorRgb in TypeHighlightDialog.PRESET_RGBS })
            assertEquals(samples.map { it.ari }.sorted(), samples.map { it.ari })
            assertEquals(savedCount, highlights.count { it != null })
        }
    }

    @Test
    fun `short chapters and blank verses do not produce duplicates or samples outside the chapter`() {
        val verses = object : SingleChapterVerses {
            override val verseCount = 3
            override fun getVerse(verse_0: Int) = listOf("First verse", "", "Third verse")[verse_0]
            override fun getVerseNumberText(verse_0: Int) = "number-${verse_0 + 1}"
        }
        val highlights = arrayOfNulls<Highlights.Info>(3)
        highlights[1] = Highlights.Info().apply { colorRgb = 0xff0000 }

        val samples = HighlightStyleSheet.selectSamples(Ari.toBookChapter(ari), verses, highlights)

        assertEquals(listOf(1, 3), samples.map { Ari.toVerse(it.ari) })
        assertEquals(listOf("number-1", "number-3"), samples.map { it.verseNumberText })
        assertTrue(samples.all { Ari.toBookChapter(it.ari) == Ari.toBookChapter(ari) })
        assertEquals(emptyList<HighlightStyleSheet.Sample>(), HighlightStyleSheet.selectSamples(Ari.toBookChapter(ari), SingleChapterVerses.EMPTY, emptyArray()))
    }

    @Test
    fun `stale partial ranges use the same full verse fallback as the reader`() {
        val sample = sample("Plain verse").copy(highlight = Highlights.Info().apply {
            colorRgb = 0x123456
            partial = Highlights.Info.Partial().apply {
                hashCode = Highlights.hashCode("Other text")
                startOffset = 2
                endOffset = 5
            }
        })

        val rendered = HighlightStyleSheet.renderPreview(sample, true, 0.5f)
        val band = rendered.text.spanStyles.last()

        assertEquals(rendered.startPosAfterVerseNumber, band.start)
        assertEquals(rendered.text.length, band.end)
    }

    @Test
    fun `chapter markers supply actual colors for multi verse and partial highlights only within the chapter`() {
        val verses = chapter(6)
        val ariBookChapter = Ari.toBookChapter(ari)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        InternalDbHelper(context).use { helper ->
            val db = InternalDb(helper)
            val now = Date()
            db.insertMarker(Ari.encodeWithBc(ariBookChapter, 4), Marker.Kind.highlight, Highlights.encode(0x123456), 2, now, now)
            db.insertMarker(
                Ari.encodeWithBc(ariBookChapter, 6), Marker.Kind.highlight,
                Highlights.encode(0xabcdef, Highlights.hashCode(verses.getVerse(5)), 1, 4), 1, now, now,
            )
            db.insertMarker(Ari.encode(1, 1, 1), Marker.Kind.highlight, Highlights.encode(0xff0000), 1, now, now)

            val highlights = arrayOfNulls<Highlights.Info>(6)
            db.putAttributes(ariBookChapter, IntArray(6), IntArray(6), highlights)
            val samples = HighlightStyleSheet.selectSamples(ariBookChapter, verses, highlights)

            assertEquals(listOf(4, 5, 6), samples.map { Ari.toVerse(it.ari) })
            assertEquals(listOf(0x123456, 0x123456, 0xabcdef), samples.map { it.highlight.colorRgb })
            val rendered = HighlightStyleSheet.renderPreview(samples.last(), false, 0f)
            assertEquals(1, rendered.text.spanStyles.last().start)
            assertEquals(4, rendered.text.spanStyles.last().end)
            assertEquals(2, db.countMarkersForBookChapter(ariBookChapter))
        }
    }

    private fun sample(rawText: String, color: Int = 0xffff00) = HighlightStyleSheet.Sample(
        ari, rawText, "1", Highlights.Info().apply { colorRgb = color },
    )

    private fun chapter(count: Int) = object : SingleChapterVerses {
        override val verseCount = count
        override fun getVerse(verse_0: Int) = "Verse ${verse_0 + 1} text"
    }

}
