package yuku.alkitab.songs

import android.app.Activity
import android.view.View
import android.widget.TextView
import yuku.alkitab.base.widget.LeftDrawer
import yuku.alkitab.debug.R

class SongSelectionControls(activity: Activity, root: View, listener: LeftDrawer.Songs.Listener) : LeftDrawer.Songs.Handle {
    private val book = root.findViewById<TextView>(R.id.bChangeBook)
    private val code = root.findViewById<TextView>(R.id.bChangeCode)
    private val ok = root.findViewById<View>(R.id.bOk)
    private val a = root.findViewById<View>(R.id.bDigitA)
    private val b = root.findViewById<View>(R.id.bDigitB)
    private val c = root.findViewById<View>(R.id.bDigitC)

    init {
        book.setOnClickListener {
            SongBookUtil.getSongBookPopupMenu(activity, false, true, book).apply {
                setOnMenuItemClickListener(SongBookUtil.getSongBookOnMenuItemClickListener(object : SongBookUtil.DefaultOnSongBookSelectedListener() {
                    override fun onSongBookSelected(name: String) = listener.songBookSelected(name)
                    override fun onMoreSelected() = listener.moreSelected()
                }))
                show()
            }
        }
        for (id in intArrayOf(R.id.bDigit0, R.id.bDigit1, R.id.bDigit2, R.id.bDigit3, R.id.bDigit4,
            R.id.bDigit5, R.id.bDigit6, R.id.bDigit7, R.id.bDigit8, R.id.bDigit9,
            R.id.bDigitA, R.id.bDigitB, R.id.bDigitC, R.id.bOk, R.id.bBackspace)) {
            root.findViewById<View>(id).setOnClickListener(listener::songKeypadButton_click)
        }
    }

    override fun setOkButtonEnabled(enabled: Boolean) { ok.isEnabled = enabled }
    override fun setAButtonEnabled(enabled: Boolean) { a.isEnabled = enabled }
    override fun setBButtonEnabled(enabled: Boolean) { b.isEnabled = enabled }
    override fun setCButtonEnabled(enabled: Boolean) { c.isEnabled = enabled }
    override fun setBookName(bookName: CharSequence?) { book.text = bookName }
    override fun setCode(code: String?) { this.code.text = code }
}
