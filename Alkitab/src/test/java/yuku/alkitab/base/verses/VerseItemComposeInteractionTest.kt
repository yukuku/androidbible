package yuku.alkitab.base.verses

import android.app.Activity
import android.graphics.Typeface
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private val ROW_WIDTH_PX = 360

    private val LONG_BODY = "A bowl of steaming noodles arrived at the table, topped with a soft " +
        "boiled egg, spring onions and a spoonful of chili oil, and the broth smelled of ginger, " +
        "garlic and slowly braised beef."

    @Before
    fun setUp() {
        AfwApp.initWithAppContext(ApplicationProvider.getApplicationContext())

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
        // `setup()` runs through onResume so the decor view is attached, which
        // is what lets AbstractComposeView find a WindowRecomposer.
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val view = VerseItemComposeView(activity)
        attachAndLayout(activity, view)
        view.bind(buildState(text, recorder))
        layoutRow(view)
        return view
    }

    private fun attachAndLayout(activity: Activity, view: View) {
        val frame = FrameLayout(activity)
        frame.addView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        activity.setContentView(frame)
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
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    // Own sdk for a fresh Robolectric sandbox: only its first activity gets a
    // ticking frame clock, and without one BasicText never reports a
    // TextLayoutResult to hit-test against.
    @Config(sdk = [33])
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

        tap(view, 40f, view.measuredHeight - 6f)

        assertEquals(1, recorder.verseClicks)
    }

    private fun resolveTypeface(typeface: Typeface?, bold: Boolean): Typeface {
        val family = composeFontFamilyFor(typeface, bold)
        val resolver = createFontFamilyResolver(ApplicationProvider.getApplicationContext())
        val weight = if (bold) FontWeight.Bold else FontWeight.Normal
        return resolver.resolve(family, weight).value as Typeface
    }

    @Test
    fun `a custom typeface asked for bold resolves to a bold face, so the platform can fake the weight`() {
        // Not one of the stock singletons, so it maps to a wrapped-typeface
        // FontFamily, which Compose never synthesises for.
        val custom = Typeface.create("cursive", Typeface.NORMAL)

        assertTrue(resolveTypeface(custom, bold = true).isBold)
        assertFalse(resolveTypeface(custom, bold = false).isBold)
    }

    @Test
    fun `a stock typeface keeps mapping to its stock family, which resolves bold on its own`() {
        assertTrue(resolveTypeface(Typeface.SERIF, bold = true).isBold)
        assertFalse(resolveTypeface(Typeface.SERIF, bold = false).isBold)
    }
}
