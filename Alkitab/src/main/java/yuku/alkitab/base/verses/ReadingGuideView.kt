package yuku.alkitab.base.verses

import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView

class ReadingGuideView(val content: View) : LinearLayout(content.context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private var showLine = false
    private var startsLine = false
    private var endsLine = false
    private val body = FrameLayout(context)

    fun drawEdgeLine(canvas: Canvas) {
        if (!showLine) return
        val density = resources.displayMetrics.density
        val top = y + body.top
        val bottom = y + body.bottom
        paint.strokeWidth = 2 * density
        canvas.save()
        canvas.clipRect(4 * density, top, 6 * density, bottom)
        canvas.drawLine(5 * density, top + if (startsLine) density else 0f, 5 * density, bottom - if (endsLine) density else 0f, paint)
        canvas.restore()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        updateBodyPadding()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun updateBodyPadding() {
        val margin = (parent as? View)?.paddingLeft ?: 0
        val extra = if (showLine) (kotlin.math.ceil(10 * resources.displayMetrics.density).toInt() - margin).coerceAtLeast(0) else 0
        body.setPadding(extra, 0, 0, 0)
    }

    init {
        orientation = VERTICAL
        layoutParams = content.layoutParams ?: android.view.ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        body.addView(content, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun bind(guide: ReadingGuide, ari: Int, color: Int, backgroundColor: Int) {
        paint.color = readingGuideLineColor(color, backgroundColor)
        showLine = guide.mode == ReadingGuideMode.LINE && guide.includes(ari)
        startsLine = guide.startsAt(ari)
        endsLine = guide.endsAt(ari)
        updateBodyPadding()
        (parent as? View)?.invalidate()
    }
}

internal class ReadingGuideDecoration : RecyclerView.ItemDecoration() {
    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        for (index in 0 until parent.childCount) {
            (parent.getChildAt(index) as? ReadingGuideView)?.drawEdgeLine(canvas)
        }
    }
}
