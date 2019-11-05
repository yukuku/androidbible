package yuku.alkitab.base.widget

import android.text.style.ClickableSpan
import android.view.View

interface ParallelClickData

inline class AriParallelClickData(val ari: Int) : ParallelClickData

inline class ReferenceParallelClickData(val reference: String) : ParallelClickData

class ParallelSpan<T : ParallelClickData>(
    private val data: T,
    private val onClickListener: (ParallelClickData) -> Unit
) : ClickableSpan() {
    override fun onClick(widget: View) {
        onClickListener(data)
    }
}
