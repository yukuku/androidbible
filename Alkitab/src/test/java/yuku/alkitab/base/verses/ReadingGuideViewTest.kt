package yuku.alkitab.base.verses

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.alkitab.base.util.ReadingRange

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadingGuideViewTest {
    private fun view() = ReadingGuideView(TextView(ApplicationProvider.getApplicationContext()).apply { text = "Verse text" })
    private val ranges = listOf(ReadingRange(0x280905, 0x280906), ReadingRange(0x28090e, 0x280917))

    @Test
    fun `only the actual boundaries receive labels and a recycled row drops them`() {
        val view = view()
        val guide = ReadingGuide(ReadingGuideMode.LABELS, ranges)
        view.bind(guide, 0x280905, true, 0xff000000.toInt(), Color.WHITE)
        assertEquals(View.VISIBLE, view.getChildAt(0).visibility)
        assertEquals(View.GONE, view.getChildAt(2).visibility)
        view.bind(guide, 0x280906, true, 0xff000000.toInt(), Color.WHITE)
        assertEquals(View.GONE, view.getChildAt(0).visibility)
        assertEquals(View.VISIBLE, view.getChildAt(2).visibility)
        view.bind(guide, 0x280909, true, 0xff000000.toInt(), Color.WHITE)
        assertEquals(View.GONE, view.getChildAt(0).visibility)
        assertEquals(View.GONE, view.getChildAt(2).visibility)
    }

    @Test
    fun `line mode and recycled rows keep the verse at its original position and width`() {
        val view = view()
        view.bind(ReadingGuide(ReadingGuideMode.LINE, ranges), 0x280905, true, 0xff000000.toInt(), Color.WHITE)
        view.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        assertEquals(0, view.getChildAt(1).paddingLeft)
        assertEquals(0, view.content.left)
        assertEquals(200, view.content.width)
        view.bind(ReadingGuide.NONE, 0x280905, true, 0xff000000.toInt(), Color.WHITE)
        assertEquals(0, view.getChildAt(1).paddingLeft)
        assertEquals(View.GONE, view.getChildAt(0).visibility)
        assertEquals(View.GONE, view.getChildAt(2).visibility)
    }
    @Test
    fun `recycler decoration draws at the pane edge outside highlighted verse content`() {
        val recycler = RecyclerView(ApplicationProvider.getApplicationContext())
        recycler.setBackgroundColor(Color.WHITE)
        recycler.setPadding(16, 0, 0, 0)
        recycler.layoutManager = LinearLayoutManager(recycler.context)
        recycler.addItemDecoration(ReadingGuideDecoration())
        recycler.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = 3
            override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
                val content = View(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 30)
                    setBackgroundColor(Color.YELLOW)
                }
                return object : RecyclerView.ViewHolder(ReadingGuideView(content)) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as ReadingGuideView).bind(ReadingGuide(ReadingGuideMode.LINE, ranges), 0x280905 + position, true, Color.BLACK, Color.WHITE)
            }
        }
        recycler.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(90, View.MeasureSpec.EXACTLY))
        recycler.layout(0, 0, 200, 90)
        val bitmap = Bitmap.createBitmap(200, 90, Bitmap.Config.ARGB_8888)
        recycler.draw(Canvas(bitmap))
        assertEquals(Color.BLACK, bitmap.getPixel(0, 10))
        assertEquals(Color.BLACK, bitmap.getPixel(1, 40))
        assertEquals(Color.WHITE, bitmap.getPixel(0, 70))
        for (y in listOf(10, 40, 70)) {
            assertEquals(Color.WHITE, bitmap.getPixel(15, y))
            assertEquals(Color.YELLOW, bitmap.getPixel(16, y))
        }
        assertEquals(16, recycler.getChildAt(0).left)
        assertEquals(184, recycler.getChildAt(0).width)
    }

}
