package yuku.alkitab.base.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.afw.App as AfwApp
import yuku.alkitab.debug.R

/**
 * Regression guard for the side-by-side (vertical bar) split handle keeping its
 * two version labels inside the safe area while the bar itself draws
 * edge-to-edge behind the system bars / gesture navigation pill.
 *
 * The bar spans the whole window height; the bottom label ("label2") must be
 * lifted by the supplied bottom inset so it never overlaps the gesture bar, and
 * the top label ("label1") lifted by the top inset. The test renders the handle
 * with native graphics and scans the inset bands for stray label pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LabeledSplitHandleButtonLabelInsetTest {

    @Before
    fun setUp() {
        AfwApp.initWithAppContext(ApplicationProvider.getApplicationContext())
    }

    private fun buildActivity(): AppCompatActivity {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(R.style.Theme_Alkitab)
        return activity
    }

    private data class Bands(val top: Int, val bottom: Int)

    /** Renders the vertical-bar handle and returns intruding label pixel counts in each inset band. */
    private fun renderAndScan(topInset: Int, bottomInset: Int): Bands {
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        val thickness = (24 * density).toInt()
        val height = (800 * density).toInt()

        val button = LabeledSplitHandleButton(buildActivity(), null).apply {
            setOrientation(SplitHandleButton.Orientation.horizontal) // vertical bar = side-by-side
            setLabel1("KJV ▼")
            setLabel2("TB ▼")
            setVerticalInsets(topInset, bottomInset)
        }
        val ws = View.MeasureSpec.makeMeasureSpec(thickness, View.MeasureSpec.EXACTLY)
        val hs = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        button.measure(ws, hs)
        button.layout(0, 0, thickness, height)

        val bitmap = Bitmap.createBitmap(thickness, height, Bitmap.Config.ARGB_8888)
        button.draw(Canvas(bitmap))

        // Sample the bar background from a spot with no label or center icon.
        val bg = bitmap.getPixel(1, (height * 0.5f).toInt() - (60 * density).toInt())

        fun scan(yFrom: Int, yTo: Int): Int {
            var n = 0
            for (y in yFrom until yTo) for (x in 0 until thickness) {
                val p = bitmap.getPixel(x, y)
                val d = Math.abs(AndroidColor.red(p) - AndroidColor.red(bg)) +
                    Math.abs(AndroidColor.green(p) - AndroidColor.green(bg)) +
                    Math.abs(AndroidColor.blue(p) - AndroidColor.blue(bg))
                if (d > 40) n++
            }
            return n
        }
        return Bands(top = scan(0, topInset), bottom = scan(height - bottomInset, height))
    }

    @Test
    fun `the bottom label is lifted out of the bottom inset band (gesture bar)`() {
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        val bottomInset = (48 * density).toInt()
        val bands = renderAndScan(topInset = 0, bottomInset = bottomInset)
        assertEquals("bottom label must not intrude into the gesture-bar band", 0, bands.bottom)
    }

    @Test
    fun `the top label is lifted out of the top inset band (status bar or cutout)`() {
        val density = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        val topInset = (32 * density).toInt()
        val bands = renderAndScan(topInset = topInset, bottomInset = (48 * density).toInt())
        assertEquals("top label must not intrude into the top inset band", 0, bands.top)
    }
}
