package yuku.alkitab.base.util

import android.view.View
import android.widget.Button
import yuku.alkitab.debug.R

class BackForwardListController<BackButton: View, ForwardButton: View>(
    private val group: View,
    /**
     * Called when the back button needs to be updated.
     * The ari is 0 if it should be disabled.
     */
    private val onBackButtonNeedUpdate: (button: BackButton, ari: Int) -> Unit,
    /**
     * Called when the forward button needs to be updated.
     * The ari is 0 if it should be disabled.
     */
    private val onForwardButtonNeedUpdate: (button: ForwardButton, ari: Int) -> Unit,
    /**
     * Called when the back/forward button is tapped before the current index is changed.
     */
    private val onButtonPreMove: (controller: BackForwardListController<BackButton, ForwardButton>) -> Unit,
    /**
     * Called when the back/forward button is tapped after the current index is changed.
     */
    private val onButtonPostMove: (ari: Int) -> Unit
) {
    private val backForwardList = BackForwardList()
    private val bBack = group.findViewById<BackButton>(R.id.bBackForwardListBack)
    private val bForward = group.findViewById<ForwardButton>(R.id.bBackForwardListForward)

    init {
        bBack.setOnClickListener {
            val index = backForwardList.currentIndex
            if (index >= 1) {
                onButtonPreMove(this)
                backForwardList.moveTo(index - 1)
                val ari = backForwardList.currentEntry?.currentAri
                if (ari != null) {
                    onButtonPostMove(ari)
                }
            }

            display()
        }
        bForward.setOnClickListener {
            val index = backForwardList.currentIndex
            if (index < backForwardList.size - 1) {
                onButtonPreMove(this)
                backForwardList.moveTo(index + 1)
                val ari = backForwardList.currentEntry?.currentAri
                if (ari != null) {
                    onButtonPostMove(ari)
                }
            }

            display()
        }
    }

    fun newEntry(initialAri: Int) {
        backForwardList.newEntry(initialAri)
        display()
    }

    fun updateCurrentEntry(ari: Int) {
        backForwardList.updateCurrentEntry(ari)
    }

    fun display() {
        if (backForwardList.size <= 1) {
            // there is only one entry, hide everything
            group.visibility = View.GONE
            return
        }

        group.visibility = View.VISIBLE

        // update back button text
        val index = backForwardList.currentIndex
        if (index == 0) {
            // we are at the beginning of the list
            onBackButtonNeedUpdate(bBack, 0)
        } else {
            val previous = backForwardList.getEntry(index - 1)
            if (previous != null) {
                onBackButtonNeedUpdate(bBack, previous.currentAri)
            }
        }

        // update forward button text
        if (index == backForwardList.size - 1) {
            // we are at the end of the list
            onForwardButtonNeedUpdate(bForward, 0)
        } else {
            val next = backForwardList.getEntry(index + 1)
            if (next != null) {
                onForwardButtonNeedUpdate(bForward, next.currentAri)
            }
        }
    }
}
