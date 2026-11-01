package yuku.alkitab.base.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.alkitab.base.App
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [34], qualifiers = "w640dp-h360dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LeftDrawerInsetsTest {
    @Test
    fun `each drawer keeps its usable width outside the unsafe start edge in both layout directions`() {
        val layouts = listOf(R.layout.left_drawer_text, R.layout.left_drawer_songs, R.layout.left_drawer_devotion, R.layout.left_drawer_reading_plan)
        for (layout in layouts) {
            for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
                activity.setTheme(R.style.Theme_Alkitab)
                val parent = DrawerLayout(activity)
                parent.layoutDirection = direction
                parent.addView(FrameLayout(activity), DrawerLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                val drawer = activity.layoutInflater.inflate(layout, parent, false) as LeftDrawer
                parent.addView(drawer)
                drawer.layoutDirection = direction
                val content = drawer.getChildAt(0) as ViewGroup
                val header = content.getChildAt(0)
                val body = content.getChildAt(1)
                val footer = drawer.findViewById<View>(R.id.bSettings).parent as ViewGroup
                val baseStartMargin = (body.layoutParams as ViewGroup.MarginLayoutParams).marginStart
                val startInset = if (direction == View.LAYOUT_DIRECTION_RTL) 48 else 32
                repeat(2) {
                    drawer.setSafeAreaInsets(32, 24, 48, 16)
                    parent.measure(
                        View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                    )
                    parent.layout(0, 0, 640, 360)
                    assertEquals(280 + startInset, drawer.width)
                    assertEquals(drawer.width, header.width)
                    assertEquals(280, drawer.findViewById<View>(R.id.bBible).width)
                    assertEquals(drawer.width, footer.width)
                    assertEquals(0, (footer.layoutParams as ViewGroup.MarginLayoutParams).marginStart)
                    for (id in listOf(R.id.bSettings, R.id.bHelp)) {
                        val button = drawer.findViewById<View>(id)
                        assertEquals(280, button.width)
                        assertEquals(if (direction == View.LAYOUT_DIRECTION_RTL) 0 else startInset, button.left)
                    }
                    assertEquals(baseStartMargin + startInset, (body.layoutParams as ViewGroup.MarginLayoutParams).marginStart)
                    assertEquals(0, drawer.paddingLeft)
                    assertEquals(0, drawer.paddingRight)
                    val bitmap = Bitmap.createBitmap(drawer.width, drawer.height, Bitmap.Config.ARGB_8888)
                    drawer.draw(Canvas(bitmap))
                    val unsafeX = if (direction == View.LAYOUT_DIRECTION_RTL) drawer.width - 2 else 1
                    assertEquals(Color.rgb(55, 71, 79), bitmap.getPixel(unsafeX, 30))
                    bitmap.recycle()
                    val footerBitmap = Bitmap.createBitmap(footer.width, footer.height, Bitmap.Config.ARGB_8888)
                    footer.draw(Canvas(footerBitmap))
                    for (id in listOf(R.id.bSettings, R.id.bHelp)) {
                        val button = drawer.findViewById<View>(id)
                        assertEquals(Color.rgb(55, 71, 79), footerBitmap.getPixel(unsafeX, button.top + button.height / 2))
                    }
                    footerBitmap.recycle()
                }
                drawer.setSafeAreaInsets(0, 0, 0, 0)
                assertEquals(280, drawer.layoutParams.width)
                assertEquals(baseStartMargin, (body.layoutParams as ViewGroup.MarginLayoutParams).marginStart)
                assertEquals(0, footer.paddingStart)
                activity.finish()
            }
        }
    }

    @Test
    fun `a navigation bar on the opposite edge does not reduce or enlarge the drawer`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(R.style.Theme_Alkitab)
        val parent = DrawerLayout(activity)
        val drawer = activity.layoutInflater.inflate(R.layout.left_drawer_text, parent, false) as LeftDrawer
        parent.addView(drawer)
        drawer.setSafeAreaInsets(0, 24, 48, 0)
        assertEquals(280, drawer.layoutParams.width)
        assertEquals(0, (drawer.getChildAt(0) as ViewGroup).getChildAt(0).paddingRight)
        activity.finish()
    }
}
