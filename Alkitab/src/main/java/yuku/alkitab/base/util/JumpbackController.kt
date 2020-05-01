package yuku.alkitab.base.util

import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import yuku.alkitab.debug.R

class JumpbackController(
    private val group: MaterialButtonToggleGroup,
    private val onJumpbackAriChange: (ari: Int) -> Unit,
    private val onJumpbackClick: (ari: Int) -> Unit
) {
    private val bJumpback = group.findViewById<MaterialButton>(R.id.bJumpback)
    private val bJumpbackClear = group.findViewById<MaterialButton>(R.id.bJumpbackClear)

    /**
     * Cannot jump to an entry earlier than this.
     */
    private var earliestJumpback = System.currentTimeMillis()

    fun processEntries(entries: List<History.Entry>) {
        // Find the newest jumpback, or hide the jumpback panel if there is none
        val entry = entries.find { it.jumpback && it.timestamp >= earliestJumpback }
        if (entry == null) {
            group.visibility = View.GONE
        } else {
            group.visibility = View.VISIBLE
            onJumpbackAriChange(entry.ari)
            bJumpback.setOnClickListener {
                onJumpbackClick(entry.ari)
                group.clearChecked()
            }
            bJumpbackClear.setOnClickListener {
                group.clearChecked()
                group.visibility = View.GONE
                earliestJumpback = System.currentTimeMillis()
            }
        }
    }
}
