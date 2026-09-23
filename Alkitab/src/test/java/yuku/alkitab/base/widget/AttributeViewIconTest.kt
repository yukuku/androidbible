package yuku.alkitab.base.widget

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.afw.App as AfwApp
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AttributeViewIconTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val attributeIcons = listOf(
        R.drawable.ic_attr_bookmark,
        R.drawable.ic_attr_note,
        R.drawable.ic_attr_has_maps,
    ) + (0 until AttributeView.PROGRESS_MARK_TOTAL_COUNT).map { AttributeView.getProgressMarkIconResource(it) }

    @Test
    fun `renderScaledIcon sizes every attribute icon to its 30dp intrinsic size times the scale`() {
        for (scale in listOf(1f, 1.5f, 2.25f)) {
            val expected = Math.round(30 * 3 * scale)
            for (resId in attributeIcons) {
                val bitmap = AttributeView.renderScaledIcon(context, resId, scale)
                assertEquals("width of ${context.resources.getResourceEntryName(resId)} at $scale", expected, bitmap.width)
                assertEquals("height of ${context.resources.getResourceEntryName(resId)} at $scale", expected, bitmap.height)
            }
        }
    }

    @Test
    fun `renderScaledIcon draws the vector into the bitmap instead of returning a blank one`() {
        for (resId in attributeIcons) {
            val bitmap = AttributeView.renderScaledIcon(context, resId, 2f)
            assertTrue("${context.resources.getResourceEntryName(resId)} rendered blank", opaquePixelCount(bitmap) > bitmap.width * bitmap.height / 10)
        }
    }

    private fun opaquePixelCount(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { it ushr 24 == 0xff }
    }
}
