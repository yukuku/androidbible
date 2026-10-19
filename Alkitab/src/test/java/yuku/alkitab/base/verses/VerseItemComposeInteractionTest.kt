package yuku.alkitab.base.verses

import android.app.Activity
import android.content.ClipData
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
import yuku.alkitab.base.widget.AttributeView
import yuku.alkitab.base.widget.VerseInlineLinkSpan
import yuku.alkitab.base.widget.VerseRendererCompose
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

    private fun composeRow(text: String, recorder: TapRecorder): VerseItemComposeView {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        activities += controller
        val activity = controller.get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val view = VerseItemComposeView(activity)
        attachAndLayout(activity, view)
        view.bind(buildState(text, recorder))
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
