package yuku.alkitab.base.verses

import android.graphics.Typeface
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.afw.App as AfwApp
import yuku.alkitab.base.S
import yuku.alkitab.debug.R
import yuku.alkitab.model.PericopeBlock
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.Ari

/**
 * Composition-level tests for [VersesComposeControllerImpl]: the LazyColumn
 * content (verse rows and pericope headers) is actually composed, measured,
 * and laid out inside a Robolectric activity, then the controller's scroll
 * contract is exercised against the live layout.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VersesComposeListCompositionTest {

    private val VIEWPORT_WIDTH_PX = 360
    private val VIEWPORT_HEIGHT_PX = 640

    private class FakeVerses(private val texts: List<String>) : SingleChapterVerses {
        override val verseCount: Int get() = texts.size
        override fun getVerse(verse_0: Int): String = texts[verse_0]
    }

    @Before
    fun setUp() {
        AfwApp.initWithAppContext(ApplicationProvider.getApplicationContext())

        val prefContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        yuku.afw.storage.Preferences.setInt(
            prefContext.getString(R.string.pref_selectedVerseBgColor_key),
            0xfffff59d.toInt(),
        )

        // Deterministic dimensions so the render pipeline never consults real
        // preferences/resources (mirrors VerseItemSideBySideSnapshotTest).
        val dims = S.CalculatedDimensions().apply {
            fontSize2dp = 17f
            fontFace = Typeface.DEFAULT
            fontBold = Typeface.NORMAL
            fontColor = 0xff202020.toInt()
            fontRedColor = 0xffcc0000.toInt()
            verseNumberColor = 0xff445566.toInt()
            backgroundColor = 0xffffffff.toInt()
            lineSpacingMult = 1.15f
            indentParagraphFirst = 38
            indentParagraphRest = 5
            indentSpacing1 = 22
            indentSpacing2 = 38
            indentSpacing3 = 54
            indentSpacing4 = 70
            indentSpacingExtra = 6
            pericopeSpacingTop = 14
            pericopeSpacingBottom = 4
        }
        S.applied()
        overrideAppliedDimensions(dims)
    }

    private fun overrideAppliedDimensions(d: S.CalculatedDimensions) {
        val holderClass = Class.forName("${S::class.java.name}\$CalculatedDimensionsHolder")
        val instance = holderClass.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
        holderClass.getDeclaredField("applied").apply { isAccessible = true }.set(instance, d)
    }

    private fun idleLoopers() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * A chapter of [verseCount] verses with a pericope header before verse 5
     * (when the chapter is long enough), so both item types get composed.
     */
    private fun dataModel(verseCount: Int): VersesDataModel {
        // Several lines per verse so a 60-verse chapter is many viewports
        // tall; mid-chapter verses can then really be scrolled to the top
        // instead of clamping at the end of the content.
        val filler = "and more words to push the verse onto several lines ".repeat(3)
        val verses = FakeVerses(List(verseCount) { "Verse ${it + 1} $filler." })
        val withPericope = verseCount >= 5
        return VersesDataModel(
            ari_bc_ = Ari.encode(1, 2, 0),
            verses_ = verses,
            pericopeBlockCount_ = if (withPericope) 1 else 0,
            pericopeAris_ = if (withPericope) intArrayOf(Ari.encode(1, 2, 5)) else IntArray(0),
            pericopeBlocks_ = if (withPericope) {
                listOf(PericopeBlock().apply {
                    title = "@@A @9formatted@7 pericope title"
                    parallels = arrayOf("Matthew 1:1", "Mark 2:2")
                })
            } else {
                emptyList()
            },
        )
    }

    private class Harness(
        val controller: VersesComposeControllerImpl,
        val view: VersesComposeView,
    )

    /**
     * Builds the controller with its data already set BEFORE the view
     * attaches, so the chapter renders in the attach-time initial composition
     * and scrolls compose new rows inside the measure pass (LazyLayout
     * subcomposition). This keeps the tests independent of Robolectric's
     * recomposition frame clock, which only reliably ticks for the first
     * activity of a test sandbox — production recomposition paths are
     * exercised by the sandbox-isolated data-change test below.
     */
    private fun composeList(verseCount: Int): Harness {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val view = VersesComposeView(activity)
        val controller = VersesComposeControllerImpl(view, "test")
        controller.versesDataModel = dataModel(verseCount)
        activity.setContentView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        val harness = Harness(controller, view)
        var rounds = 0
        while (harness.controller.listState.layoutInfo.visibleItemsInfo.isEmpty()) {
            pumpLayout(harness)
            rounds++
            assertTrue("list never composed any items", rounds < 10)
        }
        return harness
    }

    private fun pumpLayout(harness: Harness) {
        // Two rounds of apply-notification + measure/idle so recomposition →
        // scroll-command → forced remeasure chains fully settle before
        // assertions.
        repeat(2) {
            idleLoopers()
            harness.view.measure(
                View.MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEWPORT_HEIGHT_PX, View.MeasureSpec.EXACTLY),
            )
            harness.view.layout(0, 0, VIEWPORT_WIDTH_PX, VIEWPORT_HEIGHT_PX)
            idleLoopers()
        }
    }

    @Test
    fun `composing a chapter with verses and a pericope header lays out items and reports verse 1 at the top`() {
        val harness = composeList(30)

        val info = harness.controller.listState.layoutInfo
        assertTrue("expected laid-out items, got none", info.visibleItemsInfo.isNotEmpty())
        // 30 verses + 1 pericope header
        assertEquals(31, info.totalItemsCount)

        assertEquals(1, harness.controller.getVerse_1BasedOnScroll())
    }

    @Test
    fun `scrollToVerse snaps a mid-chapter verse to the top of the viewport`() {
        val harness = composeList(60)

        harness.controller.scrollToVerse(40)
        pumpLayout(harness)

        assertEquals(40, harness.controller.getVerse_1BasedOnScroll())
    }

    @Test
    fun `scrollToVerse on a verse with a pericope header above scrolls to the header keeping the verse next`() {
        val harness = composeList(60)

        // Verse 5 has the pericope header directly above it; scrollToVerse
        // must land on the header, and the verse-based-on-scroll resolves
        // through the header to verse 5.
        harness.controller.scrollToVerse(5)
        pumpLayout(harness)

        assertEquals(5, harness.controller.getVerse_1BasedOnScroll())
        val firstVisible = harness.controller.listState.layoutInfo.visibleItemsInfo.first()
        assertEquals(ItemTypeOf(harness.controller.versesDataModel, firstVisible.index), VersesDataModel.ItemType.pericope)
    }

    @Test
    fun `scrollToVerse with prop offsets the verse by the requested fraction of its height`() {
        val harness = composeList(60)

        harness.controller.scrollToVerse(30, 0.5f)
        pumpLayout(harness)

        val data = harness.controller.versesDataModel
        val pos = data.getPositionIgnoringPericopeFromVerse(30)
        val item = harness.controller.listState.layoutInfo.visibleItemsInfo.first { it.index == pos }
        // Half the verse's height is scrolled past the top edge (offset is
        // relative to the content start; there is no content padding here).
        assertEquals(-(item.size / 2).toFloat(), item.offset.toFloat(), 1.5f)
    }

    @Test
    // Runs on its own sdk so it gets a fresh Robolectric sandbox whose
    // recomposition frame clock works: this test is the one that depends on a
    // recomposition (data set while the view is already attached).
    @Config(sdk = [33])
    fun `a scroll requested together with a data change applies to the new chapter's positions`() {
        val harness = composeList(10)

        harness.controller.versesDataModel = dataModel(60)
        harness.controller.scrollToVerse(50)
        pumpLayout(harness)
        // A second pump lets the post-recomposition scroll command land in
        // case the first pass only recomposed the new chapter.
        pumpLayout(harness)

        assertEquals(50, harness.controller.getVerse_1BasedOnScroll())
    }

    @Test
    fun `pageDown advances the top verse and reports the new target`() {
        val harness = composeList(60)

        val result = harness.controller.pageDown()
        assertTrue(result is VersesController.PressResult.Consumed)
        pumpLayout(harness)

        val topVerseAfter = harness.controller.getVerse_1BasedOnScroll()
        assertTrue("expected pageDown to move past verse 1, still at $topVerseAfter", topVerseAfter > 1)
        assertEquals((result as VersesController.PressResult.Consumed).targetVerse_1, topVerseAfter)
    }

    @Test
    fun `verseDown then verseUp returns to the same top verse`() {
        val harness = composeList(60)

        harness.controller.scrollToVerse(20)
        pumpLayout(harness)
        assertEquals(20, harness.controller.getVerse_1BasedOnScroll())

        harness.controller.verseDown()
        pumpLayout(harness)
        assertEquals(21, harness.controller.getVerse_1BasedOnScroll())

        harness.controller.verseUp()
        pumpLayout(harness)
        assertEquals(20, harness.controller.getVerse_1BasedOnScroll())
    }

    @Test
    fun `padding changes and audio highlight on a composed list do not disturb the top-of-list anchor`() {
        val harness = composeList(30)

        harness.controller.setViewPadding(Rect(10, 20, 10, 20))
        pumpLayout(harness)
        // At the very top, the first verse follows the padding into place and
        // the reported top verse stays 1.
        assertEquals(1, harness.controller.getVerse_1BasedOnScroll())

        harness.controller.setAudioHighlight(2, 0x40ff0000)
        pumpLayout(harness)
        harness.controller.setAudioHighlight(0, 0)
        pumpLayout(harness)
        assertEquals(1, harness.controller.getVerse_1BasedOnScroll())
    }

    @Test
    fun `checked verses survive a data reload via checkVerses round trip`() {
        val harness = composeList(30)

        val toCheck = yuku.alkitab.util.IntArrayList()
        toCheck.add(3)
        toCheck.add(8)
        harness.controller.checkVerses(toCheck, callSelectedVersesListener = false)
        pumpLayout(harness)

        val checked = harness.controller.getCheckedVerses_1()
        assertEquals(2, checked.size())
        assertEquals(3, checked.get(0))
        assertEquals(8, checked.get(1))
    }

    private fun ItemTypeOf(data: VersesDataModel, position: Int): VersesDataModel.ItemType =
        data.getItemViewType(position)
}
