package yuku.alkitab.base.widget

import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import yuku.alkitab.base.util.AppLog
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TAG = "ScrollbarSetter"

object ScrollbarSetter {
    class ReflectionHolder(
        val scrollCacheField: Field,
        val scrollBarField: Field,
        val setVerticalThumbDrawable: Method
    )

    private val reflectionHolder: ReflectionHolder? by lazy {
        try {
            val scrollCacheField = View::class.java.getDeclaredField("mScrollCache")
            scrollCacheField.isAccessible = true
            val scrollCacheClass = scrollCacheField.type
            val scrollBarField = scrollCacheClass.getDeclaredField("scrollBar")
            scrollBarField.isAccessible = true
            val scrollBarClass = scrollBarField.type
            val setVerticalThumbDrawable = scrollBarClass.getDeclaredMethod("setVerticalThumbDrawable", Drawable::class.java)
            setVerticalThumbDrawable.isAccessible = true
            ReflectionHolder(scrollCacheField, scrollBarField, setVerticalThumbDrawable)
        } catch (e: Exception) {
            AppLog.e(TAG, "reflection init error", e)
            null
        }
    }

    fun RecyclerView.setVerticalThumb(drawable: Drawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            this.verticalScrollbarThumbDrawable = drawable
        } else {
            val reflectionHolder = reflectionHolder ?: return

            try {
                val scrollCache = reflectionHolder.scrollCacheField.get(this)
                val scrollBar = reflectionHolder.scrollBarField.get(scrollCache)
                reflectionHolder.setVerticalThumbDrawable.invoke(scrollBar, drawable)
            } catch (e: Exception) {
                AppLog.e(TAG, "reflection call error", e)
            }
        }
    }
}
