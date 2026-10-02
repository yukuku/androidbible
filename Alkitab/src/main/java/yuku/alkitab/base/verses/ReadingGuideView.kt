package yuku.alkitab.base.verses

import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import yuku.alkitab.debug.R

/** Hosts passage boundaries outside the verse's selection and audio overlays. */
class ReadingGuideView(val content: View) : LinearLayout(content.context) {
    private val startLabel = TextView(context)
    private val endLabel = TextView(context)
    private val paint = Paint()
    private var showLine = false
    private val body = object : FrameLayout(context) {
        init { setWillNotDraw(false) }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (showLine) canvas.drawRect(0f, 0f, 2 * resources.displayMetrics.density, height.toFloat(), paint)
        }
    }

    init {
        orientation = VERTICAL
        layoutParams = content.layoutParams ?: android.view.ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        for (label in listOf(startLabel, endLabel)) {
            label.textSize = 12f
            val padding = (6 * resources.displayMetrics.density).toInt()
            label.setPadding(0, padding, 0, padding)
            label.visibility = GONE
        }
        startLabel.setText(R.string.current_reading_start)
        endLabel.setText(R.string.current_reading_end)
        addView(startLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.addView(content, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(endLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun bind(guide: ReadingGuide, ari: Int, verse: Boolean, color: Int) {
        val labels = guide.mode == ReadingGuideMode.LABELS && verse
        startLabel.visibility = if (labels && guide.startsAt(ari)) VISIBLE else GONE
        endLabel.visibility = if (labels && guide.endsAt(ari)) VISIBLE else GONE
        startLabel.setTextColor(color)
        endLabel.setTextColor(color)
        paint.color = color
        showLine = guide.mode == ReadingGuideMode.LINE && guide.includes(ari)
        val gutter = if (guide.mode == ReadingGuideMode.LINE) (8 * resources.displayMetrics.density).toInt() else 0
        body.setPadding(gutter, 0, 0, 0)
        body.invalidate()
    }
}
