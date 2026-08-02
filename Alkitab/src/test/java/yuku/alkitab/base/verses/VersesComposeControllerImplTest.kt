package yuku.alkitab.base.verses

import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.widget.AriParallelClickData
import yuku.alkitab.base.widget.FormattedTextRenderer
import yuku.alkitab.base.widget.ParallelClickData
import yuku.alkitab.base.widget.ReferenceParallelClickData
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList

/**
 * Tests for the fully Compose-based verses controller, exercising the parts
 * of the [VersesController] contract that don't require a running composition
 * (verse checking, listener callbacks, scroll queries against an un-composed
 * list) plus the pure text-conversion helpers used by the Compose pericope
 * header.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VersesComposeControllerImplTest {

    private class FakeVerses(private val texts: List<String>) : SingleChapterVerses {
        override val verseCount: Int get() = texts.size
        override fun getVerse(verse_0: Int): String = texts[verse_0]
    }

    private lateinit var view: VersesComposeView

    private fun dataModel(verseCount: Int): VersesDataModel {
        val verses = FakeVerses(List(verseCount) { "Verse ${it + 1}" })
        return VersesDataModel(
            ari_bc_ = Ari.encode(1, 2, 0),
            verses_ = verses,
        )
    }

    @Before
    fun setUp() {
        view = VersesComposeView(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `checkVerses marks the requested verses and reports them ascending through getCheckedVerses_1`() {
        val controller = VersesComposeControllerImpl(view, "test")
        controller.versesDataModel = dataModel(10)

        val toCheck = IntArrayList()
        toCheck.add(7)
        toCheck.add(2)
        toCheck.add(5)
        controller.checkVerses(toCheck, callSelectedVersesListener = false)

        val checked = controller.getCheckedVerses_1()
        assertEquals(3, checked.size())
        assertEquals(2, checked.get(0))
        assertEquals(5, checked.get(1))
        assertEquals(7, checked.get(2))
    }

    @Test
    fun `checkVerses ignores verse numbers outside the chapter`() {
        val controller = VersesComposeControllerImpl(view, "test")
        controller.versesDataModel = dataModel(5)

        val toCheck = IntArrayList()
        toCheck.add(3)
        toCheck.add(99)
        controller.checkVerses(toCheck, callSelectedVersesListener = false)

        val checked = controller.getCheckedVerses_1()
        assertEquals(1, checked.size())
        assertEquals(3, checked.get(0))
    }

    @Test
    fun `checkVerses with callSelectedVersesListener notifies onSomeVersesSelected and uncheckAllVerses notifies onNoVersesSelected`() {
        var someSelected: IntArrayList? = null
        var noneSelectedCalled = false
        val listeners = VersesListeners.EMPTY.copy(
            selectedVersesListener = object : VersesController.SelectedVersesListener() {
                override fun onSomeVersesSelected(verses_1: IntArrayList) {
                    someSelected = verses_1
                }

                override fun onNoVersesSelected() {
                    noneSelectedCalled = true
                }
            }
        )
        val controller = VersesComposeControllerImpl(view, "test", versesListeners = listeners)
        controller.versesDataModel = dataModel(10)

        val toCheck = IntArrayList()
        toCheck.add(4)
        controller.checkVerses(toCheck, callSelectedVersesListener = true)

        val reported = someSelected
        assertNotNull(reported)
        assertEquals(1, reported!!.size())
        assertEquals(4, reported.get(0))
        assertFalse(noneSelectedCalled)

        controller.uncheckAllVerses(callSelectedVersesListener = true)
        assertTrue(noneSelectedCalled)
        assertEquals(0, controller.getCheckedVerses_1().size())
    }

    @Test
    fun `getVerse_1BasedOnScroll returns 0 when the list has never been laid out`() {
        val controller = VersesComposeControllerImpl(view, "test")
        controller.versesDataModel = dataModel(10)

        assertEquals(0, controller.getVerse_1BasedOnScroll())
    }

    @Test
    fun `scroll requests and highlights on an un-composed list do not throw`() {
        val controller = VersesComposeControllerImpl(view, "test")
        controller.versesDataModel = dataModel(10)

        // The view is not attached to a lifecycle, so these must no-op safely.
        controller.scrollToTop()
        controller.scrollToVerse(5)
        controller.scrollToVerse(5, 0.5f)
        controller.scrollToPericope(5, 0.5f)
        controller.callAttentionForVerse(3)
        controller.setAudioHighlight(2, 0x40ff0000)
        controller.setAudioHighlight(0, 0)

        val down = controller.pageDown()
        assertTrue(down is VersesController.PressResult.Consumed)
        val up = controller.pageUp()
        assertTrue(up is VersesController.PressResult.Consumed)
    }

    @Test
    fun `verseDown and verseUp stay within chapter bounds`() {
        val controller = VersesComposeControllerImpl(view, "test")
        controller.versesDataModel = dataModel(3)

        // Un-composed list scrolls report verse 0, so verseDown moves to 1.
        val down = controller.pageDown()
        assertTrue(down is VersesController.PressResult.Consumed)

        val result = controller.verseDown()
        assertTrue(result is VersesController.PressResult.Consumed)
        assertEquals(1, (result as VersesController.PressResult.Consumed).targetVerse_1)
    }

    @Test
    fun `spannedToAnnotatedString maps italic StyleSpans to Compose italic ranges`() {
        val rendered = FormattedTextRenderer.render("@@Before @9italic part@7 after")
        assertTrue(rendered.getSpans(0, rendered.length, StyleSpan::class.java).isNotEmpty())

        val annotated = spannedToAnnotatedString(rendered)
        assertEquals("Before italic part after", annotated.text)

        val italicRanges = annotated.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertEquals(1, italicRanges.size)
        val range = italicRanges[0]
        assertEquals("italic part", annotated.text.substring(range.start, range.end))
    }

    @Test
    fun `spannedToAnnotatedString of a plain title has no styles`() {
        val annotated = spannedToAnnotatedString(SpannableStringBuilder("Plain title"))
        assertEquals("Plain title", annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }

    @Test
    fun `buildParallelsAnnotatedString wraps every parallel in a clickable link and separates them`() {
        val clicked = mutableListOf<ParallelClickData>()
        val annotated = buildParallelsAnnotatedString(
            arrayOf("Matthew 1:1", "Mark 2:2"),
            { data -> clicked.add(data) },
        )

        assertEquals("(Matthew 1:1; Mark 2:2)", annotated.text)

        val links = annotated.getLinkAnnotations(0, annotated.text.length)
        assertEquals(2, links.size)
        assertEquals("Matthew 1:1", annotated.text.substring(links[0].start, links[0].end))
        assertEquals("Mark 2:2", annotated.text.substring(links[1].start, links[1].end))

        // Tapping the second link reports a reference-based parallel.
        val link = links[1].item as LinkAnnotation.Clickable
        link.linkInteractionListener!!.onClick(link)
        assertEquals(1, clicked.size)
        assertTrue(clicked[0] is ReferenceParallelClickData)
        assertEquals("Mark 2:2", (clicked[0] as ReferenceParallelClickData).reference)
    }

    @Test
    fun `buildParallelsAnnotatedString decodes @-encoded targets into ari click data`() {
        val clicked = mutableListOf<ParallelClickData>()
        // "@" target syntax: encoded target, space, display text. The "a:"
        // target type carries a raw ari decoded by TargetDecoder.
        val annotated = buildParallelsAnnotatedString(
            arrayOf("@a:${Ari.encode(0, 1, 1)} Gen. 1:1"),
            { data -> clicked.add(data) },
        )

        assertEquals("(Gen. 1:1)", annotated.text)
        val links = annotated.getLinkAnnotations(0, annotated.text.length)
        assertEquals(1, links.size)

        val link = links[0].item as LinkAnnotation.Clickable
        link.linkInteractionListener!!.onClick(link)
        assertEquals(1, clicked.size)
        assertTrue(clicked[0] is AriParallelClickData)
    }

    @Test
    fun `buildParallelsAnnotatedString forces line breaks for the 4-parallel pattern`() {
        val annotated = buildParallelsAnnotatedString(
            arrayOf("A 1", "B 2", "C 3", "D 4"),
            {},
        )
        assertEquals("(A 1; B 2; \nC 3; D 4)", annotated.text)
    }
}
