package yuku.alkitab.base.util

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatViewInflater
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.textview.MaterialTextView

@Keep
class TypographyViewInflater : AppCompatViewInflater() {
    override fun createTextView(context: Context, attrs: AttributeSet): AppCompatTextView = MaterialTextView(context, attrs)
}
