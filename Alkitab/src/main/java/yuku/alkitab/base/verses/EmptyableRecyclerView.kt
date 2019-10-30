package yuku.alkitab.base.verses

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

class EmptyableRecyclerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RecyclerView(context, attrs) {
    var emptyMessage: CharSequence? = null
        set(value) {
            field = value
            invalidate()
        }

    val emptyMessagePaint = Paint().apply {
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)

        val message = emptyMessage ?: return
        val paint = emptyMessagePaint
        paint.textSize = 14f * resources.displayMetrics.density
        c.drawText(message, 0, message.length, width / 2f, height / 2f, paint)
    }
}
