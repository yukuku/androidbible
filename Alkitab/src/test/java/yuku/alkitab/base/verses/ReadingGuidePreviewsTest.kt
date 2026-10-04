package yuku.alkitab.base.verses

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import io.mockk.mockk
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.alkitab.base.App
import yuku.alkitab.base.services.AppServices

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadingGuidePreviewsTest {
    private lateinit var previousServices: AppServices

    @Before
    fun setUp() {
        previousServices = App.services
        App.services = AppServices(mockk(), mockk(), mockk())
    }

    @After
    fun tearDown() {
        App.services = previousServices
    }

    private fun render(mode: ReadingGuideMode, dark: Boolean, multiplePassages: Boolean, leftMarginDp: Int) {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        val view = ComposeView(activity)
        view.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ReadingGuidePreviewContent(mode, multiplePassages, leftMarginDp)
            }
        }
        activity.setContentView(view, ViewGroup.LayoutParams(360, ViewGroup.LayoutParams.WRAP_CONTENT))
        repeat(2) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        assertTrue("The preview should contain the passage and its guide", view.height > 300)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val background = if (dark) 0xff202020.toInt() else android.graphics.Color.WHITE
        if (mode == ReadingGuideMode.LINE) {
            val ink = if (dark) 0xffeeeeee.toInt() else 0xff202020.toInt()
            assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(ink, background) >= 3.0)
            val markedRows = (0 until bitmap.height).filter { y -> bitmap.getPixel(4, y) == ink && bitmap.getPixel(5, y) == ink && ((0 until 4) + (6 until maxOf(10, leftMarginDp))).all { bitmap.getPixel(it, y) == background } }
            assertTrue(markedRows.size > 20)
            for (y in markedRows) {
                assertTrue(bitmap.getPixel(5, y) == ink)
                assertTrue(((0 until 4) + (6 until maxOf(10, leftMarginDp))).all { bitmap.getPixel(it, y) == background })
            }
        } else {
            assertTrue(bitmap.getPixel(0, bitmap.height / 2) == background)
        }
        assertTrue((0 until bitmap.height).any { y -> (16 until bitmap.width - 16).any { x -> bitmap.getPixel(x, y) != background } })
        val path = File("build/test-artifacts/reading-guide-previews/${mode.preferenceValue}-${if (dark) "dark" else "light"}${if (multiplePassages) "-multiple" else ""}${if (leftMarginDp != 16) "-margin-$leftMarginDp" else ""}.png")
        path.parentFile?.mkdirs()
        path.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `start and end marker preview renders without application services`() = render(ReadingGuideMode.LABELS, false, false, 16)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-mdpi")
    fun `start and end marker preview renders in dark mode`() = render(ReadingGuideMode.LABELS, true, false, 16)

    @Test
    fun `left side line preview renders without application services`() = render(ReadingGuideMode.LINE, false, false, 16)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-mdpi")
    fun `left side line preview renders in dark mode`() = render(ReadingGuideMode.LINE, true, false, 16)

    @Test
    fun `fixed current reading indicator preview renders without application services`() = render(ReadingGuideMode.CAPTION, false, false, 16)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-mdpi")
    fun `fixed current reading indicator preview renders in dark mode`() = render(ReadingGuideMode.CAPTION, true, false, 16)

    @Test
    fun `multiple passage label preview renders without application services`() = render(ReadingGuideMode.LABELS, false, true, 16)

    @Test
    fun `multiple passage line preview renders without application services`() = render(ReadingGuideMode.LINE, false, true, 16)

    @Test
    fun `multiple passage caption preview renders without application services`() = render(ReadingGuideMode.CAPTION, false, true, 16)
    @Test
    fun `rounded line preview renders with zero text margin`() = render(ReadingGuideMode.LINE, false, false, 0)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-mdpi")
    fun `rounded line preview renders with reduced margin in dark mode`() = render(ReadingGuideMode.LINE, true, false, 4)

}
