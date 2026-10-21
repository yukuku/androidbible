package yuku.alkitab.base.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.TextView
import yuku.alkitab.debug.R

class DevotionGestureLayout(context: Context, attrs: AttributeSet) : TwofingerLinearLayout(context, attrs) {
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Keep day swipes and pinches observable when the article claims a drag.
        // Text selection retains exclusive control of its touch stream.
        super.requestDisallowInterceptTouchEvent(disallowIntercept && hasTextSelection())
        parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            setOnefingerEnabled(!hasTextSelection())
        }
        return super.onInterceptTouchEvent(event)
    }

    private fun hasTextSelection(): Boolean = findViewById<TextView>(R.id.lContent)?.hasSelection() == true
}
