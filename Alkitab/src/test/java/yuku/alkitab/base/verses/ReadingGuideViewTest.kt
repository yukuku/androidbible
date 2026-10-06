package yuku.alkitab.base.verses

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    fun `recycled rows release the clearance reserved for a marked verse`() {
        val view = view()
        view.bind(ReadingGuide(ReadingGuideMode.LINE, ranges), 0x280905, 0xff000000.toInt(), Color.WHITE)
        view.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        assertEquals(10, view.getChildAt(0).paddingLeft)
        assertEquals(10, view.content.left)
        assertEquals(190, view.content.width)
        view.bind(ReadingGuide.NONE, 0x280905, 0xff000000.toInt(), Color.WHITE)
        assertEquals(0, view.getChildAt(0).paddingLeft)
    }

    private fun checkRecycler(leftMargin: Int) {
        val recycler = RecyclerView(ApplicationProvider.getApplicationContext())
        recycler.setBackgroundColor(Color.WHITE)
        recycler.setPadding(leftMargin, 0, 0, 0)
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
                (holder.itemView as ReadingGuideView).bind(ReadingGuide(ReadingGuideMode.LINE, ranges), 0x280905 + position, Color.BLACK, Color.WHITE)
            }
        }
        recycler.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(90, View.MeasureSpec.EXACTLY))
        recycler.layout(0, 0, 200, 90)
        val bitmap = Bitmap.createBitmap(200, 90, Bitmap.Config.ARGB_8888)
        recycler.draw(Canvas(bitmap))
        assertEquals(Color.BLACK, bitmap.getPixel(4, 10))
        assertEquals(Color.BLACK, bitmap.getPixel(5, 40))
        for (index in 0..2) {
            val y = index * 30 + 10
            val expectedLeft = if (index < 2) maxOf(leftMargin, 10) else leftMargin
            if (expectedLeft > 0) assertEquals(Color.WHITE, bitmap.getPixel(expectedLeft - 1, y))
            assertEquals(Color.YELLOW, bitmap.getPixel(expectedLeft, y))
            val row = recycler.getChildAt(index) as ReadingGuideView
            assertEquals(expectedLeft, row.left + row.content.left)
            assertEquals(200 - expectedLeft, row.content.width)
        }
    }

    @Test
    fun `normal margins preserve verse placement beside the inset line`() = checkRecycler(16)

    @Test
    fun `small margins add clearance only to marked verses`() {
        for (margin in listOf(0, 4, 8, 10)) checkRecycler(margin)
    }
}
