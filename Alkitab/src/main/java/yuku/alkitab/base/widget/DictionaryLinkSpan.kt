package yuku.alkitab.base.widget

import android.text.style.ClickableSpan
import android.view.View

class DictionaryLinkInfo(val orig_text: String, val key: String)

class DictionaryLinkSpan(
    private val data: DictionaryLinkInfo,
    private val onClickListener: (DictionaryLinkInfo) -> Unit
) : ClickableSpan() {
    override fun onClick(widget: View) {
        onClickListener(data)
    }
}
