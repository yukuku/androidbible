package yuku.alkitab.base.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import yuku.alkitab.base.widget.MaterialDialogAdapterHelper
import yuku.alkitab.base.widget.MaterialDialogAdapterHelper.withAdapter
import yuku.alkitab.debug.R

class BackForwardListController<BackButton : View, ForwardButton : View>(
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
    private val onButtonPostMove: (ari: Int) -> Unit,
    /**
     * Return a user-friendly reference of an ari.
     */
    private val referenceDisplayer: (ari: Int) -> CharSequence,
) {
    private val backForwardList = BackForwardList()
    private val bBack = group.findViewById<BackButton>(R.id.bBackForwardListBack)
    private val bForward = group.findViewById<ForwardButton>(R.id.bBackForwardListForward)

    class BackForwardEntryHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text1: TextView = itemView.findViewById(android.R.id.text1)
    }

    /**
     * Store history entries temporarily here so we can easily sort and refer back.
     */
    private class DisplayEntry(val index: Int, val entry: BackForwardList.Entry?)

    inner class BackForwardAdapter(context: Context) : MaterialDialogAdapterHelper.Adapter() {
        private var defaultTextColor: Int = 0
        private val escapeTextColor = ContextCompat.getColor(context, R.color.escape)

        private val displayEntries = MutableList(backForwardList.size) { index ->
            DisplayEntry(index, backForwardList.getEntry(index))
        }.apply {
            sortByDescending { it.entry?.createTime ?: 0 }
        }.toList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val textView = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
            defaultTextColor = textView.currentTextColor
            return BackForwardEntryHolder(textView)
        }

        override fun getItemCount() = displayEntries.size + 1

        override fun onBindViewHolder(_holder_: RecyclerView.ViewHolder, _position_: Int) {
            val holder = _holder_ as BackForwardEntryHolder

            run {
                if (_position_ != itemCount - 1) {
                    val entry = displayEntries[_position_].entry
                    if (entry != null) {
                        holder.text1.text = referenceDisplayer(entry.currentAri)
                    }
                    holder.text1.setTextColor(defaultTextColor)
                } else { // clear item
                    holder.text1.setText(R.string.backforwardlist_clear)
                    holder.text1.setTextColor(escapeTextColor)
                }
            }

            holder.itemView.setOnClickListener {
                dismissDialog()

                val position = holder.bindingAdapterPosition
                if (position != itemCount - 1) {
                    val displayEntry = displayEntries[position]
                    onButtonPreMove(this@BackForwardListController)
                    backForwardList.moveTo(displayEntry.index)
                    val ari = displayEntry.entry?.currentAri
                    if (ari != null) {
                        onButtonPostMove(ari)
                    }
                } else { // clear item
                    backForwardList.purgeOthers()
                }

                display()
            }
        }
    }

    private val longClickListener = View.OnLongClickListener { v ->
        val adapter = BackForwardAdapter(v.context)
        MaterialDialog(v.context).show {
            withAdapter(adapter)
        }
        true
    }

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

        bBack.setOnLongClickListener(longClickListener)
        bForward.setOnLongClickListener(longClickListener)
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
