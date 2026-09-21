package yuku.alkitab.base.compose.toolbar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.afw.App as AfwApp
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.debug.R

class ComposeToolbarHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Alkitab)
        super.onCreate(savedInstanceState)
    }
}

/**
 * Lays out [ReaderToolbar] the same way the audit measures the view toolbar:
 * a real layout pass at every width the app ships on, at xxhdpi so 1 dp is
 * exactly 3 px.
 *
 * The bar reports the reference button's window bounds through
 * [ReaderToolbarActions.onReferenceBoundsChanged], which is enough to derive
 * every slot width without reaching into the composition.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "sw360dp-w360dp-h640dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderToolbarComposeTest {

    private val density = 3f
    private val barHeightDp = 56

    private fun px(dp: Number) = (dp.toFloat() * density).roundToInt()
    private fun dp(px: Number) = px.toFloat() / density

    @Before
    fun setUp() {
        AfwApp.initWithAppContext(ApplicationProvider.getApplicationContext())
    }

    private class Laid(
        val widthDp: Int,
        val arrowDp: Float,
        val referenceTouch: Rect,
        val bitmap: Bitmap,
    ) {
        /** The cluster is the reference target grown back by one arrow on each side. */
        fun clusterWidthPx(arrowPx: Int) = referenceTouch.width() + arrowPx * 2
    }

    private fun layOut(widthDp: Int, state: ReaderToolbarState, fontScale: Float = 1f): Laid {
        RuntimeEnvironment.setQualifiers("sw${widthDp}dp-w${widthDp}dp-h640dp-port-xxhdpi")
        val activity = Robolectric.buildActivity(ComposeToolbarHostActivity::class.java).setup().get()

        var reference = Rect()
        val actions = object : ReaderToolbarActions {
            override fun onDrawerClick() = Unit
            override fun onPreviousChapter() = Unit
            override fun onNextChapter() = Unit
            override fun onReferenceClick() = Unit
            override fun onReferenceLongClick() = Unit
            override fun onVersionClick() = Unit
            override fun onAudioClick() = Unit
            override fun onSearchClick() = Unit
            override fun onReferenceDragStart(screenX: Float, screenY: Float) = Unit
            override fun onReferenceDragMove(screenX: Float, screenY: Float) = Unit
            override fun onReferenceDragComplete(screenX: Float, screenY: Float) = Unit
            override fun onReferenceBoundsChanged(x: Int, y: Int, width: Int, height: Int) {
                reference = Rect(x, y, x + width, y + height)
            }
        }

        val view = ComposeView(activity).apply {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                    BibleAppTheme { ReaderToolbar(state, actions) }
                }
            }
        }
        val host = FrameLayout(activity).apply {
            setBackgroundColor(activity.getColor(R.color.primary))
            addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        activity.setContentView(host, ViewGroup.LayoutParams(px(widthDp), px(barHeightDp)))

        val widthSpec = View.MeasureSpec.makeMeasureSpec(px(widthDp), View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(px(barHeightDp), View.MeasureSpec.EXACTLY)
        repeat(2) {
            host.measure(widthSpec, heightSpec)
            host.layout(0, 0, host.measuredWidth, host.measuredHeight)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }

        val bitmap = Bitmap.createBitmap(host.measuredWidth, host.measuredHeight, Bitmap.Config.ARGB_8888)
        host.draw(Canvas(bitmap))

        val hostLocation = IntArray(2)
        host.getLocationInWindow(hostLocation)
        reference.offset(-hostLocation[0], -hostLocation[1])

        return Laid(
            widthDp = widthDp,
            arrowDp = dp(activity.resources.getDimensionPixelSize(R.dimen.nav_prevnext_width)),
            referenceTouch = reference,
            bitmap = bitmap,
        )
    }

    private val shippingWidths = listOf(320, 360, 384, 411, 480, 600)

    /** Leftmost column inside [box] that the bar painted something on. */
    private fun firstInkColumn(bitmap: Bitmap, box: Rect): Int {
        val background = bitmap.getPixel(box.left, box.top)
        for (x in box.left until box.right) {
            for (y in box.top until box.bottom) {
                if (bitmap.getPixel(x, y) != background) return x
            }
        }
        throw AssertionError("no chevron drawn in $box")
    }

    /** Transparent margin the chevron path leaves inside its own 24dp glyph box. */
    private val chevronInkInsetDp = 8f / 24f * ReaderToolbarDimens.iconSize.value

    private fun stateFor(initials: String = "TB", audio: Boolean = true, audioOn: Boolean = false) =
        ReaderToolbarState(
            reference = "Kejadian 1",
            versionInitials = initials,
            versionVisible = true,
            audioAvailable = audio,
            audioBarVisible = audioOn,
        )

    /** 2 characters: a 48dp version half, a 1dp hairline, a 32dp speaker. */
    private val shortSegmentDp =
        ReaderToolbarDimens.chipMinWidth.value + 1f + ReaderToolbarDimens.speakerWidth.value

    /** Either half on its own keeps the stadium and takes the 48dp minimum. */
    private val loneSegmentDp = ReaderToolbarDimens.chipMinWidth.value

    private fun clusterWidthDp(widthDp: Int, segmentDp: Float) = minOf(
        widthDp - ReaderToolbarDimens.drawerWidth.value - ReaderToolbarDimens.searchWidth.value - segmentDp,
        ReaderToolbarDimens.clusterMaxWidth.value,
    )

    @Test
    fun `the drawer button gives up the 56dp the navigation style forces on the view toolbar`() {
        for (widthDp in shippingWidths) {
            val laid = layOut(widthDp, stateFor())
            assertEquals(
                "reference starts after the drawer plus one arrow at ${widthDp}dp",
                ReaderToolbarDimens.drawerWidth.value + laid.arrowDp,
                dp(laid.referenceTouch.left),
                0.4f,
            )
        }
    }

    @Test
    fun `the reference touch area is the full bar height at every width`() {
        for (widthDp in shippingWidths) {
            val laid = layOut(widthDp, stateFor())
            assertEquals("bar height at ${widthDp}dp", barHeightDp.toFloat(), dp(laid.referenceTouch.height()), 0.4f)
        }
    }

    @Test
    fun `the navigation cluster takes everything the other three controls leave, up to 250dp`() {
        for (widthDp in shippingWidths) {
            val laid = layOut(widthDp, stateFor())
            val clusterDp = dp(laid.clusterWidthPx(px(laid.arrowDp)))
            assertEquals("cluster width at ${widthDp}dp", clusterWidthDp(widthDp, shortSegmentDp), clusterDp, 0.7f)
        }
    }

    @Test
    fun `a six character version name never widens the reference`() {
        for (widthDp in listOf(320, 360, 384, 411)) {
            val short = layOut(widthDp, stateFor("TB"))
            val long = layOut(widthDp, stateFor("VERSNM"))
            assertTrue(
                "a 6-character name should not widen the reference at ${widthDp}dp",
                long.referenceTouch.width() <= short.referenceTouch.width(),
            )
        }
    }

    @Test
    fun `hiding the version changer leaves the speaker a capsule of its own`() {
        for (widthDp in listOf(320, 360, 411)) {
            val withVersion = layOut(widthDp, stateFor())
            val withoutVersion = layOut(widthDp, stateFor().copy(versionVisible = false))
            val gainedDp = dp(withoutVersion.referenceTouch.width() - withVersion.referenceTouch.width())
            val expected = clusterWidthDp(widthDp, loneSegmentDp) - clusterWidthDp(widthDp, shortSegmentDp)
            assertEquals("width handed back at ${widthDp}dp", expected, gainedDp, 0.7f)
        }
    }

    @Test
    fun `hiding both the version changer and the speaker leaves nothing behind`() {
        for (widthDp in listOf(320, 360, 411)) {
            val withBoth = layOut(widthDp, stateFor())
            val withNeither = layOut(widthDp, stateFor(audio = false).copy(versionVisible = false))
            val gainedDp = dp(withNeither.referenceTouch.width() - withBoth.referenceTouch.width())
            val expected = clusterWidthDp(widthDp, 0f) - clusterWidthDp(widthDp, shortSegmentDp)
            assertEquals("width handed back at ${widthDp}dp", expected, gainedDp, 0.7f)
        }
    }

    @Test
    fun `a version without audio drops the speaker segment`() {
        val withAudio = layOut(320, stateFor(audio = true))
        val withoutAudio = layOut(320, stateFor(audio = false))
        val gainedDp = dp(withoutAudio.referenceTouch.width() - withAudio.referenceTouch.width())
        assertEquals(1f + ReaderToolbarDimens.speakerWidth.value, gainedDp, 0.7f)
    }

    @Test
    fun `the chapter chevrons sit flush outward only below 411dp`() {
        for (widthDp in shippingWidths) {
            val laid = layOut(widthDp, stateFor())
            val arrowPx = px(laid.arrowDp)
            val box = Rect(
                laid.referenceTouch.left - arrowPx,
                laid.referenceTouch.top,
                laid.referenceTouch.left,
                laid.referenceTouch.bottom,
            )
            val inkStart = dp(firstInkColumn(laid.bitmap, box) - box.left)
            val glyphMargin = (laid.arrowDp - ReaderToolbarDimens.iconSize.value) / 2f
            val expected = if (widthDp < ReaderToolbarDimens.flushBelowWidth.value) 0f else glyphMargin
            // The chevron's own artwork is inset inside its 24dp glyph box, so
            // only the difference between the two placements is asserted.
            assertEquals("chevron inset at ${widthDp}dp", expected, inkStart - chevronInkInsetDp, 0.4f)
        }
    }

    @Test
    fun `a version name longer than six characters is cut to five plus an ellipsis`() {
        assertEquals("TB", versionLabelFor("TB"))
        assertEquals("VERSNM", versionLabelFor("VERSNM"))
        assertEquals("VERSI…", versionLabelFor("VERSION"))
    }

    @Test
    fun `the speaker segment marks the audio bar being open without moving anything`() {
        val off = layOut(360, stateFor(audioOn = false))
        val on = layOut(360, stateFor(audioOn = true))
        assertEquals(off.referenceTouch, on.referenceTouch)
        assertTrue("the speaker segment should look different when the bar is open", !off.bitmap.sameAs(on.bitmap))
    }


    /**
     * The chapter number sits at the end of the reference, so anything cut off
     * takes it with it. A bar too small for "2 Tesalonika 1" falls back to
     * "2Tes 1", and gives up once even that cannot be shown whole.
     */
    private fun longReference(abbreviated: String) = stateFor().copy(
        reference = "2 Tesalonika 1",
        referenceAbbreviated = abbreviated,
    )

    private fun abbreviationUsedAt(fontScale: Float): Boolean {
        val without = layOut(360, longReference(""), fontScale)
        val with = layOut(360, longReference("2Tes 1"), fontScale)
        return !without.bitmap.sameAs(with.bitmap)
    }

    @Test
    fun `a reference that fits whole is left alone`() {
        assertTrue("the abbreviation should not be used at ordinary font sizes", !abbreviationUsedAt(1f))
        assertTrue(!abbreviationUsedAt(1.3f))
    }

    @Test
    fun `a reference whose chapter number is cut off falls back to the book abbreviation`() {
        assertTrue(abbreviationUsedAt(1.6f))
        assertTrue(abbreviationUsedAt(2.5f))
    }

    @Test
    fun `a bar too small for the abbreviation too keeps the full reference`() {
        assertTrue("nothing is gained by abbreviating here", !abbreviationUsedAt(3f))
    }

    /**
     * Not a pass/fail guard: writes the bar at every shipping width so the
     * Compose result can be put next to the audit's own renders.
     */
    @Test
    fun `render the compose toolbar at every shipping width`() {
        val laids = shippingWidths.map { layOut(it, stateFor()) } +
            layOut(360, stateFor("VERSNM")) +
            layOut(360, stateFor(audioOn = true)) +
            layOut(360, stateFor().copy(versionVisible = false)) +
            layOut(360, stateFor(audio = false)) +
            layOut(360, longReference("2Tes 1"), fontScale = 1f) +
            layOut(360, longReference("2Tes 1"), fontScale = 2f)
        val labels = shippingWidths.map { "${it}dp" } +
            "360dp, 6-char version" +
            "360dp, audio bar open" +
            "360dp, split view open (no version changer)" +
            "360dp, version with no audio" +
            "360dp, long reference" +
            "360dp, long reference at a 2x font scale (abbreviated)"

        val gutter = px(8)
        val labelHeight = px(14)
        val sheetWidth = laids.maxOf { it.bitmap.width } + gutter * 2
        val sheetHeight = gutter + laids.sumOf { it.bitmap.height + labelHeight + gutter }

        val sheet = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.parseColor("#0B2A46"))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8FB6D6")
            textSize = px(9).toFloat()
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        var y = gutter
        laids.forEachIndexed { index, laid ->
            canvas.drawText(labels[index], gutter.toFloat(), (y + px(9)).toFloat(), text)
            y += labelHeight
            canvas.drawBitmap(laid.bitmap, gutter.toFloat(), y.toFloat(), null)
            y += laid.bitmap.height + gutter
        }

        val outputDir = File(System.getenv("TOOLBAR_AUDIT_DIR") ?: "build/reports/toolbar-audit")
        outputDir.mkdirs()
        val file = File(outputDir, "compose-toolbar.png")
        file.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("compose toolbar render written to ${file.absolutePath}")
    }
}
