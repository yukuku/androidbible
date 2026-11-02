package yuku.alkitab.base.dialog

import android.graphics.Rect
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.view.ViewGroup
import kotlin.math.roundToInt
import yuku.alkitab.base.dialog.base.BaseDialog

abstract class VerseContentDialog : BaseDialog() {
    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        val background = window.decorView.background
        if (background is InsetDrawable) {
            val padding = Rect()
            background.getPadding(padding)
            window.setBackgroundDrawable(InsetDrawable(background.drawable, 0, padding.top, 0, padding.bottom))
        }
        val activity = requireActivity()
        val screenWidth = if (Build.VERSION.SDK_INT >= 30) {
            activity.windowManager.currentWindowMetrics.bounds.width()
        } else {
            resources.displayMetrics.widthPixels
        }
        val density = resources.displayMetrics.density
        val width = versesDialogWidth(screenWidth / density)
        window.setLayout((width * density).roundToInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}

internal fun versesDialogWidth(screenWidthDp: Float): Float {
    val maxWidth = minOf(screenWidthDp - 64f, 640f).coerceAtLeast(0f)
    val minWidth = minOf(screenWidthDp * 0.8f, 360f, maxWidth)
    return (screenWidthDp * 0.7f).coerceIn(minWidth, maxWidth)
}
