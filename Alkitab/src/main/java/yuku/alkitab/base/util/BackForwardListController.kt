package yuku.alkitab.base.util

import android.view.View
import android.widget.Button
import android.widget.ImageButton
import yuku.alkitab.debug.R

class BackForwardListController(
    private val group: View,
    private val onBackButtonNeedUpdate: (backButton: Button, ari: Int) -> Unit,
    private val onBackButtonClick: (ari: Int) -> Unit
) {
    private val backForwardList = BackForwardList()
    private val bBack = group.findViewById<Button>(R.id.bBackForwardListBack)
    private val bMenu = group.findViewById<ImageButton>(R.id.bBackForwardListMenu)

    fun newEntry(initialAri: Int) {
        backForwardList.newEntry(initialAri)
        display()
    }

    fun display() {
        if (backForwardList.size <= 1) {
            group.visibility = View.GONE
            return
        }

        group.visibility = View.VISIBLE

        run {
            // update back button text
            val index = backForwardList.currentIndex
            val previous = backForwardList.getEntry(index - 1)
            if (previous != null) {
                onBackButtonNeedUpdate(bBack, previous.currentAri)
            }
        }

        bBack.setOnClickListener {
            val index = backForwardList.currentIndex
            if (index >= 1) {
                backForwardList.moveTo(index - 1)
            }
            val ari = backForwardList.currentEntry?.currentAri
            if (ari != null) {
                onBackButtonClick(ari)
            }

            // and we need to redisplay after going back
            display()
        }
        bMenu.setOnClickListener {
            // TODO
        }
    }
}
