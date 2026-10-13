package yuku.alkitab.base.widget

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import kotlin.math.abs
import kotlin.math.roundToInt

class FloatingSelectionToolbar(context: Context, attrs: AttributeSet?) : Toolbar(context, attrs) {
    private val density = resources.displayMetrics.density
    private val edgeMargin = (16 * density).roundToInt()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var safeInsets = Insets.NONE
    private var isFloating = false
    private var isToolbarAtBottom = false
    private var isDragging = false
    private var downX = 0f
    private var downY = 0f
    private var initialTranslationY = 0f

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, if (isFloating) 4 * density else 0f)
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            safeInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            updateFloatingBounds()
            insets
        }
    }

    fun configure(isFloating: Boolean, isToolbarAtBottom: Boolean) {
        animate().cancel()
        translationY = 0f
        isDragging = false
        this.isFloating = isFloating
        this.isToolbarAtBottom = isToolbarAtBottom
        elevation = if (isFloating) 8 * density else 0f
        clipToOutline = isFloating
        invalidateOutline()
        updateFloatingBounds()
        ViewCompat.requestApplyInsets(this)
    }

    private fun updateFloatingBounds() {
        if (!isFloating) return
        val size = TypedValue()
        context.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, size, true)
        updateLayoutParams<FrameLayout.LayoutParams> {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = TypedValue.complexToDimensionPixelSize(size.data, resources.displayMetrics)
            gravity = if (isToolbarAtBottom) Gravity.BOTTOM else Gravity.TOP
            leftMargin = edgeMargin + safeInsets.left
            topMargin = edgeMargin + safeInsets.top
            rightMargin = edgeMargin + safeInsets.right
            bottomMargin = edgeMargin + safeInsets.bottom
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (isFloating && event.actionMasked == MotionEvent.ACTION_DOWN) {
            animate().cancel()
            downX = event.rawX
            downY = event.rawY
            initialTranslationY = translationY
            isDragging = false
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return startVerticalDrag(event) || super.onInterceptTouchEvent(event)
    }

    private fun startVerticalDrag(event: MotionEvent): Boolean {
        if (!isFloating) return false
        if (event.actionMasked == MotionEvent.ACTION_MOVE &&
            abs(event.rawY - downY) > touchSlop && abs(event.rawY - downY) > abs(event.rawX - downX)
        ) {
            isDragging = true
            parent.requestDisallowInterceptTouchEvent(true)
        }
        return isDragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!startVerticalDrag(event)) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val container = parent as View
                val minimum = (edgeMargin + safeInsets.top - top).toFloat()
                val maximum = (container.height - edgeMargin - safeInsets.bottom - bottom).toFloat().coerceAtLeast(minimum)
                translationY = (initialTranslationY + event.rawY - downY).coerceIn(minimum, maximum)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent.requestDisallowInterceptTouchEvent(false)
                animate().translationY(0f).setDuration(180).setInterpolator(DecelerateInterpolator()).start()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        animate().cancel()
        translationY = 0f
        isDragging = false
        super.onDetachedFromWindow()
    }
}
