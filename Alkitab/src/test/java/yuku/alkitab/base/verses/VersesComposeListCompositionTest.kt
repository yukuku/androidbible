package yuku.alkitab.base.verses

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.android.asCoroutineDispatcher
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
        S.overrideAppliedDimensions(dims)
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
        val automaticFrameClock: BroadcastFrameClock?,
        var viewportHeight: Int = 640,
    )

    /**
     * Each list has its own recomposition clock so tests do not depend on
     * Robolectric's shared Choreographer. A supplied clock lets animation
     * tests control frame time themselves.
     */
    private fun composeList(
        verseCount: Int,
        frameClock: BroadcastFrameClock? = null,
        initialData: VersesDataModel = dataModel(verseCount),
    ): Harness {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val view = VersesComposeView(activity)
        val controller = VersesComposeControllerImpl(view, "test")
        controller.versesDataModel = initialData
        val frame = FrameLayout(activity)
        activity.setContentView(frame)
        val compositionFrameClock = frameClock ?: BroadcastFrameClock()
        val dispatcher = Handler(Looper.getMainLooper()).asCoroutineDispatcher()
        view.setParentCompositionContext(frame.createLifecycleAwareWindowRecomposer(dispatcher + compositionFrameClock))
        frame.addView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        val harness = Harness(controller, view, compositionFrameClock.takeIf { frameClock == null })
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
            Snapshot.sendApplyNotifications()
            idleLoopers()
            harness.automaticFrameClock?.sendFrame(System.nanoTime())
            idleLoopers()
            harness.view.measure(
                View.MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(harness.viewportHeight, View.MeasureSpec.EXACTLY),
            )
            harness.view.layout(0, 0, VIEWPORT_WIDTH_PX, harness.viewportHeight)
            harness.view.draw(Canvas())
            idleLoopers()
        }
    }

    /**
     * Like [pumpLayout], but advances the clock so Choreographer frame
     * callbacks — and with them the frame clock that Compose animations
     * suspend on — actually fire.
     */
    private fun pumpFrames(harness: Harness, frames: Int = 60) {
        repeat(frames) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            Snapshot.sendApplyNotifications()
            harness.automaticFrameClock?.sendFrame(System.nanoTime())
            idleLoopers()
            harness.view.measure(
                View.MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEWPORT_HEIGHT_PX, View.MeasureSpec.EXACTLY),
            )
            harness.view.layout(0, 0, VIEWPORT_WIDTH_PX, VIEWPORT_HEIGHT_PX)
        }
    }

    /** Runs [block] with every uncaught exception collected instead of merely logged. */
    private fun collectingUncaughtExceptions(block: () -> Unit): List<Throwable> {
        val caught = mutableListOf<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> caught += e }
        try {
            block()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        return caught
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
    fun `verse navigation uses the destination pane size when its viewport shrinks`() {
        val harness = composeList(12)

        harness.viewportHeight = 160
        harness.controller.scrollToVerse(10)
        pumpLayout(harness)

        assertEquals(10, harness.controller.getVerse_1BasedOnScroll())
    }

    private fun assertVerseVisible(harness: Harness, verse: Int) {
        val info = harness.controller.listState.layoutInfo
        val position = harness.controller.versesDataModel.getPositionIgnoringPericopeFromVerse(verse)
        val item = info.visibleItemsInfo.firstOrNull { it.index == position }
        assertTrue("verse $verse must be laid out", item != null)
        if (item != null) {
            val top = item.offset + info.beforeContentPadding
            assertTrue("verse $verse must intersect the viewport", top < info.viewportSize.height && top + item.size > 0)
        }
    }

    @Test
    fun `an explicit scroll cancels correction of an earlier verse navigation`() {
        val harness = composeList(60)
        harness.controller.scrollToVerse(40)
        pumpLayout(harness)
        assertVerseVisible(harness, 40)

        harness.controller.scrollToTop()
        pumpLayout(harness)
        pumpLayout(harness)
        assertEquals(1, harness.controller.getVerse_1BasedOnScroll())

        harness.controller.scrollToVerse(40)
        harness.controller.scrollToTop()
        pumpLayout(harness)
        assertEquals(1, harness.controller.getVerse_1BasedOnScroll())
    }

    @Test
    fun `a manual drag cancels correction of an earlier verse navigation`() {
        val harness = composeList(60)
        harness.controller.scrollToVerse(40)
        pumpLayout(harness)
        assertVerseVisible(harness, 40)

        val interactions = harness.controller.listState.interactionSource as MutableInteractionSource
        assertTrue(interactions.tryEmit(DragInteraction.Start()))
        idleLoopers()
        harness.controller.listState.dispatchRawDelta(-100_000f)
        pumpLayout(harness)
        pumpLayout(harness)
        assertEquals(1, harness.controller.getVerse_1BasedOnScroll())
    }

    @Test
    fun `verse navigation keeps the target visible in translations with different chapter ending lengths`() {
        val chapter = dataModel(12)
        val primary = composeList(12, initialData = chapter.copy(
            verses_ = FakeVerses(List(12) { "Long verse ${it + 1}. " + "More words in this translation. ".repeat(20) }),
        ))
        val secondary = composeList(12, initialData = chapter.copy(
            verses_ = FakeVerses(List(12) { "Short verse ${it + 1} with enough words to fill two lines." }),
        ))
        for (pane in listOf(primary, secondary)) {
            pane.viewportHeight = 320
            pumpLayout(pane)
            pane.controller.scrollToVerse(10)
            pumpLayout(pane)
        }

        assertVerseVisible(primary, 10)
        assertVerseVisible(secondary, 10)
        assertTrue("the shorter translation may stop above the target", secondary.controller.getVerse_1BasedOnScroll() < 10)
    }

    @Test
    fun `chapter ending targets remain visible after resizing and reloading without extra end space`() {
        val chapter = dataModel(25).copy(
            verses_ = FakeVerses(List(25) { "Short verse ${it + 1}." }),
        )
        val harness = composeList(25, initialData = chapter)
        harness.controller.setViewPadding(Rect(8, 16, 8, 24))
        harness.controller.setViewVerticalInsets(20, 30)

        for (height in listOf(320, 480, 160)) {
            harness.viewportHeight = height
            for (verse in listOf(22, 25, 1)) {
                harness.controller.versesDataModel = chapter.copy()
                harness.controller.scrollToVerse(verse)
                pumpLayout(harness)
                pumpLayout(harness)

                assertVerseVisible(harness, verse)
                assertEquals("only the configured bottom padding is used", 54, harness.controller.listState.layoutInfo.afterContentPadding)
            }
        }
    }

    @Test
    fun `verse navigation keeps the target visible as chapter rows grow and shrink`() {
        val shortChapter = dataModel(25).copy(
            verses_ = FakeVerses(List(25) { "Short verse ${it + 1}." }),
        )
        val longChapter = shortChapter.copy(
            ari_bc_ = Ari.encode(1, 3, 0),
            verses_ = FakeVerses(List(25) {
                "A much longer verse ${it + 1}. ".repeat(30)
            }),
        )
        val clock = BroadcastFrameClock()
        val harness = composeList(25, clock, initialData = longChapter)
        var time = 0L
        fun settle() {
            repeat(30) {
                Snapshot.sendApplyNotifications()
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
                time += 20_000_000L
                clock.sendFrame(time)
                pumpLayout(harness)
            }
        }
        settle()
        for (verse in listOf(22, 25)) {
            harness.controller.versesDataModel = longChapter
            harness.controller.scrollToVerse(25)
            settle()
            assertVerseVisible(harness, 25)

            harness.controller.versesDataModel = shortChapter
            harness.controller.scrollToVerse(verse)
            settle()

            assertVerseVisible(harness, verse)
        }
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
    fun `verse navigation uses the destination chapter when section heading positions change`() {
        val chapter = dataModel(30)
        val withHeadings = chapter.copy(
            pericopeBlockCount_ = 30,
            pericopeAris_ = IntArray(30) { Ari.encode(1, 2, it + 1) },
            pericopeBlocks_ = List(30) {
                PericopeBlock().apply {
                    title = "Section ${it + 1}"
                    parallels = emptyArray()
                }
            },
        )
        val withoutHeadings = chapter.copy(
            ari_bc_ = Ari.encode(1, 3, 0),
            pericopeBlockCount_ = 0,
            pericopeAris_ = IntArray(0),
            pericopeBlocks_ = emptyList(),
        )
        val frameClock = BroadcastFrameClock()
        val harness = composeList(30, frameClock, initialData = withHeadings)
        var frameTimeNanos = 0L
        fun settle() {
            repeat(20) {
                Snapshot.sendApplyNotifications()
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
                frameTimeNanos += 20_000_000L
                frameClock.sendFrame(frameTimeNanos)
                pumpLayout(harness)
                harness.view.draw(Canvas())
            }
        }
        settle()

        harness.controller.versesDataModel = withoutHeadings
        harness.controller.scrollToVerse(10)
        settle()
        assertEquals(10, harness.controller.getVerse_1BasedOnScroll())

        harness.controller.versesDataModel = withoutHeadings.copy()
        harness.controller.scrollToVerse(20)
        settle()
        assertEquals(20, harness.controller.getVerse_1BasedOnScroll())

        harness.controller.versesDataModel = withHeadings
        harness.controller.scrollToVerse(10)
        settle()
        assertEquals(10, harness.controller.getVerse_1BasedOnScroll())
        val first = harness.controller.listState.layoutInfo.visibleItemsInfo.first()
        assertEquals(VersesDataModel.ItemType.pericope, withHeadings.getItemViewType(first.index))
    }

    @Test
    fun `chapter changes animate verse height in both directions`() {
        val frameClock = BroadcastFrameClock()
        val harness = composeList(30, frameClock)
        var frameTimeNanos = 0L
        fun advanceFrames(frames: Int = 60) {
            repeat(frames) {
                Snapshot.sendApplyNotifications()
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
                frameTimeNanos += 20_000_000L
                frameClock.sendFrame(frameTimeNanos)
                pumpLayout(harness)
                // Compose flushes pending LazyColumn remeasurement during drawing.
                harness.view.draw(Canvas())
            }
        }
        advanceFrames()
        fun firstHeight() = harness.controller.listState.layoutInfo.visibleItemsInfo.first().size
        val tallHeight = firstHeight()
        val shortChapter = VersesDataModel(
            ari_bc_ = Ari.encode(1, 3, 0),
            verses_ = FakeVerses(List(30) { "Short verse." }),
            pericopeBlockCount_ = 0,
            pericopeAris_ = IntArray(0),
            pericopeBlocks_ = emptyList(),
        )

        harness.controller.versesDataModel = shortChapter
        advanceFrames(5)
        val shrinkingHeight = firstHeight()
        advanceFrames()
        val shortHeight = firstHeight()
        assertEquals("chapter should recompose", shortChapter.itemCount, harness.controller.listState.layoutInfo.totalItemsCount)
        assertTrue("verse should shrink over multiple frames: $tallHeight -> $shrinkingHeight -> $shortHeight", shrinkingHeight in (shortHeight + 1) until tallHeight)

        harness.controller.versesDataModel = dataModel(30)
        advanceFrames(5)
        val growingHeight = firstHeight()
        advanceFrames()
        assertTrue("verse should grow over multiple frames: $shortHeight -> $growingHeight -> ${firstHeight()}", growingHeight in (shortHeight + 1) until tallHeight)
        assertEquals(tallHeight, firstHeight())
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
    // Own sdk for a fresh Robolectric sandbox, so the Choreographer this test
    // drives is the one the frame clock posts its callbacks to.
    @Config(sdk = [32])
    fun `audio highlight on an offscreen verse smooth-scrolls it into view`() {
        val harness = composeList(60)

        // The animated scroll suspends on the frame clock, which the scroll
        // coroutine's context must carry — it is launched from the host view's
        // lifecycle scope, not from a composition.
        val caught = collectingUncaughtExceptions {
            harness.controller.setAudioHighlight(30, 0x40ff0000)
            pumpFrames(harness)
        }
        assertEquals("scroll coroutine failed: ${caught.firstOrNull()}", emptyList<Throwable>(), caught)

        val pos = harness.controller.versesDataModel.getPositionIgnoringPericopeFromVerse(30)
        val item = harness.controller.listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == pos }
        assertTrue("verse 30 was never scrolled into view", item != null)
        // Landed near the upper 10% of the viewport, as setAudioHighlight aims for.
        assertEquals((VIEWPORT_HEIGHT_PX * 0.10f), item!!.offset.toFloat(), 8f)
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
