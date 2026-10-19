package yuku.alkitab.base.verses

import android.app.Activity
import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.android.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.afw.App as AfwApp
import yuku.alkitab.base.S
import yuku.alkitab.base.compose.composeFontFamilyFor
import yuku.alkitab.base.storage.InternalReader
import yuku.alkitab.base.storage.OldVerseTextDecoder
import yuku.alkitab.base.widget.AttributeView
import yuku.alkitab.base.widget.VerseInlineLinkSpan
import yuku.alkitab.base.widget.VerseRendererCompose
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari

/**
 * Tap routing and typeface resolution for the Compose verse row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VerseItemComposeInteractionTest {

    private val frameClock = BroadcastFrameClock()
    private var frameTimeNanos = 0L
    private val activities = mutableListOf<ActivityController<AppCompatActivity>>()
    private lateinit var originalDimensions: S.CalculatedDimensions

    private val ROW_WIDTH_PX = 360

    private val LONG_BODY = "A bowl of steaming noodles arrived at the table, topped with a soft " +
        "boiled egg, spring onions and a spoonful of chili oil, and the broth smelled of ginger, " +
        "garlic and slowly braised beef."

    @Before
    fun setUp() {
        AfwApp.initWithAppContext(ApplicationProvider.getApplicationContext())
        originalDimensions = S.applied()

        S.overrideAppliedDimensions(
            S.CalculatedDimensions().apply {
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
            }
        )
    }

    @After
    fun tearDown() {
        activities.asReversed().forEach { it.pause().stop().destroy() }
        idleLoopers()
        S.overrideAppliedDimensions(originalDimensions)
    }

    private class TapRecorder {
        var verseClicks = 0
        val linkClicks = mutableListOf<Pair<VerseInlineLinkSpan.Type, Int>>()
    }

    private fun buildState(text: String, recorder: TapRecorder): VerseItemComposeState {
        val ari = Ari.encode(1, 2, 1)
        val applied = S.applied()
        return VerseItemComposeState(
            render = VerseRendererCompose.render(
                isVerseNumberShown = false,
                ari = ari,
                text = text,
                verseNumberText = "1",
                highlightInfo = null,
                checked = false,
            ),
            fontSizeDp = applied.fontSize2dp,
            verseNumberFontSizeDp = applied.fontSize2dp * 0.7f,
            fontColor = applied.fontColor,
            verseNumberColor = applied.verseNumberColor,
            lineSpacingMult = applied.lineSpacingMult,
            typeface = applied.fontFace,
            fontBold = applied.fontBold,
            attribute = AttributeState(
                bookmarkCount = 0,
                noteCount = 0,
                progressMarkBits = 0,
                hasMaps = false,
                scale = 1f,
                version = null,
                versionId = null,
                ari = ari,
                attributeListener = object : VersesController.AttributeListener() {},
                progressMarkCaptions = List(AttributeView.PROGRESS_MARK_TOTAL_COUNT) { null },
            ),
            onClick = { recorder.verseClicks++ },
            onInlineLinkClick = { type, arif -> recorder.linkClicks += type to arif },
            onPinDropped = {},
        )
    }

    private fun composeRow(
        text: String,
        recorder: TapRecorder,
        transform: (VerseItemComposeState) -> VerseItemComposeState = { it },
    ): VerseItemComposeView {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        activities += controller
        val activity = controller.get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val view = VerseItemComposeView(activity)
        attachAndLayout(activity, view)
        view.bind(transform(buildState(text, recorder)))
        layoutRow(view)
        return view
    }

    private fun attachAndLayout(activity: Activity, view: VerseItemComposeView) {
        val frame = FrameLayout(activity)
        activity.setContentView(frame)
        // Robolectric resets Choreographer between tests, but Compose caches its
        // main dispatcher. Each fixture needs a dispatcher and clock it can drive.
        val dispatcher = Handler(Looper.getMainLooper()).asCoroutineDispatcher()
        view.setParentCompositionContext(
            frame.createLifecycleAwareWindowRecomposer(dispatcher + frameClock)
        )
        frame.addView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        idleLoopers()
    }

    private fun layoutRow(view: View) {
        repeat(2) {
            idleLoopers()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(ROW_WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight.coerceAtLeast(1))
            idleLoopers()
        }
    }

    private fun tap(view: View, x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
        idleLoopers()
        view.dispatchTouchEvent(MotionEvent.obtain(down, down + 20, MotionEvent.ACTION_UP, x, y, 0))
        idleLoopers()
    }

    private fun idleLoopers() {
        Snapshot.sendApplyNotifications()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        frameTimeNanos += 16_000_000L
        frameClock.sendFrame(frameTimeNanos)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a tap near the footnote marker opens it and a tap away from it selects the verse`() {
        val recorder = TapRecorder()
        // The marker sits at the very start of the verse, so the bottom of a
        // multi-line row is well outside its 24dp easy-hit radius.
        val view = composeRow("@@@<f1@>@/$LONG_BODY", recorder)
        assertTrue("row should wrap onto several lines", view.measuredHeight > 60)

        // Hitting the marker first also proves the layout is live, so the miss
        // below is a real miss rather than an absent layout.
        tap(view, 3f, 6f)
        assertEquals(listOf(VerseInlineLinkSpan.Type.footnote to (Ari.encode(1, 2, 1) shl 8 or 1)), recorder.linkClicks)
        assertEquals(0, recorder.verseClicks)

        tap(view, 40f, view.measuredHeight - 6f)
        assertEquals(1, recorder.verseClicks)
        assertEquals("the far tap must not reach the footnote", 1, recorder.linkClicks.size)
    }

    @Test
    fun `tapping a verse with no inline links selects it`() {
        val recorder = TapRecorder()
        val view = composeRow("@@$LONG_BODY", recorder)
        assertTrue("row should wrap onto several lines", view.measuredHeight > 60)

        tap(view, 40f, view.measuredHeight - 6f)

        assertEquals(1, recorder.verseClicks)
    }

    private fun measureText(text: String, width: Int = ROW_WIDTH_PX): TextLayoutResult = TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(ApplicationProvider.getApplicationContext()),
        defaultDensity = Density(1f),
        defaultLayoutDirection = LayoutDirection.Ltr,
    ).measure(
        text = AnnotatedString(text),
        style = TextStyle(fontSize = 17.sp),
        constraints = Constraints(maxWidth = width),
    )

    @Test
    fun `a link ending at a line wrap has no hit target on the next line`() {
        val layout = measureText("MMMMMMMMMMMMMMMMMMMM", width = 80)
        assertTrue(layout.lineCount > 1)
        val end = layout.getLineEnd(0)
        val rects = boundingRectsFor(layout, 0, end)

        assertTrue(rects.isNotEmpty())
        assertTrue(rects.all { it.bottom <= layout.getLineBottom(0) })
        assertTrue(rects.all { it.width > 0f })
    }

    @Test
    fun `right to left links have non inverted hit targets`() {
        val text = "שלום"
        val layout = measureText(text)
        val rects = boundingRectsFor(layout, 0, text.length)

        assertTrue(rects.isNotEmpty())
        assertTrue(rects.all { it.width > 0f && it.height > 0f })
        assertTrue(rects.any { it.contains(layout.getBoundingBox(0).center) })
        assertTrue(rects.any { it.contains(layout.getBoundingBox(text.lastIndex).center) })
    }

    @Test
    fun `pin drops accept only valid preset ids`() {
        assertNull(progressMarkPresetId(null))
        for (payload in listOf("", "bad", "-1", AttributeView.PROGRESS_MARK_TOTAL_COUNT.toString())) {
            assertNull(progressMarkPresetId(ClipData.newPlainText("pin", payload)))
        }
        for (presetId in 0 until AttributeView.PROGRESS_MARK_TOTAL_COUNT) {
            assertEquals(presetId, progressMarkPresetId(ClipData.newPlainText("pin", presetId.toString())))
        }
    }

    @Test
    fun `rebinding an attached verse row renders its new text and routes taps to its new callback`() {
        val first = TapRecorder()
        val view = composeRow("@@Short verse", first)
        val firstHeight = view.measuredHeight
        tap(view, 40f, firstHeight - 6f)
        assertEquals(1, first.verseClicks)

        val second = TapRecorder()
        view.bind(buildState("@@$LONG_BODY", second))
        layoutRow(view)
        assertTrue("the longer verse should increase the row height", view.measuredHeight > firstHeight)
        tap(view, 40f, view.measuredHeight - 6f)

        assertEquals(1, second.verseClicks)
        assertEquals("the rebound row must not call the first callback", 1, first.verseClicks)
        assertTrue(second.linkClicks.isEmpty())
    }

    @Test
    fun `rebinding between plain and ruby text updates row height and tap routing`() {
        val plain = TapRecorder()
        val view = composeRow("@@word", plain)
        val plainHeight = view.measuredHeight
        assertTrue(plainHeight > 0)

        val ruby = TapRecorder()
        view.bind(buildState("@@@<r=reading@>word@/", ruby))
        layoutRow(view)
        assertTrue("ruby needs a band above the base text", view.measuredHeight > plainHeight)
        tap(view, 10f, view.measuredHeight - 6f)
        assertEquals(1, ruby.verseClicks)
        assertEquals(0, plain.verseClicks)

        view.bind(buildState("@@word", plain))
        layoutRow(view)
        assertEquals(plainHeight, view.measuredHeight)
        tap(view, 10f, view.measuredHeight - 6f)
        assertEquals(1, plain.verseClicks)
        assertEquals(1, ruby.verseClicks)
    }

    private fun drawRow(view: View): Bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
        val canvas = Canvas(it)
        canvas.drawColor(android.graphics.Color.WHITE)
        view.draw(canvas)
    }

    private fun hasInk(bitmap: Bitmap, fromX: Int, toX: Int, fromY: Int): Boolean =
        (fromY until bitmap.height).any { y ->
            (fromX until toX).any { x -> bitmap.getPixel(x, y) != android.graphics.Color.WHITE }
        }

    @Test
    fun `text occupies space below the attribute icon and remains tappable there`() {
        val recorder = TapRecorder()
        var bookmarkClicks = 0
        val version = mockk<Version>()
        val listener = object : VersesController.AttributeListener() {
            override fun onBookmarkAttributeClick(version: Version, versionId: String, ari: Int) {
                bookmarkClicks++
            }
        }
        val view = composeRow("@@" + LONG_BODY.repeat(3), recorder) {
            it.copy(attribute = it.attribute.copy(bookmarkCount = 1, scale = 3f, version = version, attributeListener = listener))
        }
        val icon = AttributeView.renderScaledIcon(view.context, yuku.alkitab.debug.R.drawable.ic_attr_bookmark, 3f)
        val bitmap = drawRow(view)
        assertTrue(view.height > icon.height)
        assertTrue("text should extend into the end-side strip below the icon", hasInk(bitmap, view.width - icon.width, view.width, icon.height))

        tap(view, view.width - icon.width / 2f, icon.height / 2f)
        assertEquals(1, bookmarkClicks)
        assertEquals(0, recorder.verseClicks)
        tap(view, view.width - 5f, view.height - 6f)
        assertEquals(1, recorder.verseClicks)
        assertEquals(1, bookmarkClicks)

        val file = File("build/snapshots/verse-attribute-flow.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `text also flows below end-side attributes in a right to left layout`() {
        val recorder = TapRecorder()
        val view = composeRow("@@" + "שלום וברכה לכל קוראי הספר הזה ".repeat(20), recorder) {
            it.copy(attribute = it.attribute.copy(bookmarkCount = 1, scale = 3f))
        }
        view.layoutDirection = View.LAYOUT_DIRECTION_RTL
        layoutRow(view)
        val icon = AttributeView.renderScaledIcon(view.context, yuku.alkitab.debug.R.drawable.ic_attr_bookmark, 3f)
        val bitmap = drawRow(view)
        assertTrue("text should extend below the icon on the left", hasInk(bitmap, 0, icon.width, icon.height))
        tap(view, 5f, view.height - 6f)
        assertEquals(1, recorder.verseClicks)
    }

    @Test
    fun `footnotes remain tappable after text expands below the attributes`() {
        val recorder = TapRecorder()
        val view = composeRow("@@" + LONG_BODY.repeat(3) + "@<f1@>@/", recorder) {
            it.copy(attribute = it.attribute.copy(bookmarkCount = 1, scale = 3f))
        }
        for (x in 5 until view.width step 20) {
            tap(view, x.toFloat(), view.height - 6f)
            if (recorder.linkClicks.isNotEmpty()) break
        }

        assertEquals(listOf(VerseInlineLinkSpan.Type.footnote to (Ari.encode(1, 2, 1) shl 8 or 1)), recorder.linkClicks)
    }

    @Test
    fun `bundled ruby gallery renders with end-side attributes`() {
        val reader = InternalReader("ddd", "in", "DDD", "Dummy Debug Data", OldVerseTextDecoder.Utf8())
        val book = reader.loadBooks().first()
        val samples = listOf(3 to 3, 3 to 4, 3 to 16, 5 to 16, 5 to 29, 6 to 16, 6 to 17, 6 to 18)
        for ((chapter, verse) in samples) {
            val text = reader.loadVerseText(book, chapter, false, false).getVerse(verse - 1)
            val view = composeRow(text, TapRecorder()) {
                it.copy(attribute = it.attribute.copy(bookmarkCount = 1, noteCount = 1))
            }
            val bitmap = drawRow(view)
            assertTrue("Visible content for $chapter:$verse", hasInk(bitmap, 0, bitmap.width, 0))
            val file = File("build/snapshots/ddd-$chapter-$verse.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun `ruby text fills space below attributes without losing its reading band`() {
        val recorder = TapRecorder()
        val view = composeRow("@@" + "In the @<r=H7225@>beginning@/ @<r=H430@>God@/ created the heavens and the earth. ".repeat(8), recorder) {
            it.copy(attribute = it.attribute.copy(bookmarkCount = 1, scale = 3f))
        }
        val icon = AttributeView.renderScaledIcon(view.context, yuku.alkitab.debug.R.drawable.ic_attr_bookmark, 3f)
        val bitmap = drawRow(view)
        assertTrue(hasInk(bitmap, view.width - icon.width, view.width, icon.height))
        tap(view, view.width - 5f, view.height - 6f)
        assertEquals(1, recorder.verseClicks)

        val file = File("build/snapshots/verse-attribute-flow-ruby.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun resolveTypeface(typeface: Typeface?, isBold: Boolean): Typeface {
        val family = composeFontFamilyFor(typeface)
        val resolver = createFontFamilyResolver(ApplicationProvider.getApplicationContext())
        val weight = if (isBold) FontWeight.Bold else FontWeight.Normal
        return resolver.resolve(family, weight).value as Typeface
    }

    @Test
    fun `a custom typeface asked for bold resolves to a bold face, so the platform can fake the weight`() {
        val custom = Typeface.create("cursive", Typeface.NORMAL)

        assertTrue(resolveTypeface(custom, isBold = true).isBold)
        assertFalse(resolveTypeface(custom, isBold = false).isBold)
    }

    @Test
    fun `a stock typeface resolves both normal and bold faces`() {
        assertTrue(resolveTypeface(Typeface.SERIF, isBold = true).isBold)
        assertFalse(resolveTypeface(Typeface.SERIF, isBold = false).isBold)
    }
}
