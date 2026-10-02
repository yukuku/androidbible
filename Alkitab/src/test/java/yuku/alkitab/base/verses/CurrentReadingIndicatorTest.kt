package yuku.alkitab.base.verses

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.alkitab.base.compose.BibleAppTheme

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CurrentReadingIndicatorTest {
    @Test
    fun `tapping the fixed indicator invokes the return to reading action`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        var clicks = 0
        val view = ComposeView(activity)
        view.setContent {
            BibleAppTheme {
                CurrentReadingIndicator("1 Timothy 6:6–10", 0xff202020.toInt(), { clicks++ })
            }
        }
        activity.setContentView(view, ViewGroup.LayoutParams(360, ViewGroup.LayoutParams.WRAP_CONTENT))
        repeat(2) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        val down = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, 180f, 24f, 0))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        view.dispatchTouchEvent(MotionEvent.obtain(down, down + 20, MotionEvent.ACTION_UP, 180f, 24f, 0))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, clicks)
    }
}
