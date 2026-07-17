package yuku.alkitab.songs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.concurrent.atomic.AtomicInteger
import yuku.alkitab.base.App
import yuku.alkitab.base.util.Background
import yuku.alkitab.debug.R

/**
 * Song search as a bottom sheet hosted by [SongViewActivity], replacing the old
 * standalone SongListActivity. Search state (query, book filter, deep-search flag,
 * results, and list scroll position) lives in an activity-scoped [SongListViewModel],
 * so dismissing the sheet to view a song and reopening it later restores the search
 * exactly where it was left off — without marshalling results through a Binder.
 */
class SongListBottomSheet : BottomSheetDialogFragment() {
    /**
     * The host activity must implement this to receive the selection.
     */
    interface Listener {
        fun onSongInfoSelected(songInfo: SongInfo)
    }

    private val viewModel: SongListViewModel by activityViewModels()

    private lateinit var searchView: SearchView
    private lateinit var lsSong: ListView
    private lateinit var bChangeBook: TextView
    private lateinit var cDeepSearch: CheckBox
    private lateinit var circular_progress: View

    private lateinit var adapter: SongAdapter
    private lateinit var popupChangeBook: PopupMenu

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.skipCollapsed = true
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_song_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchView = view.findViewById(R.id.searchView)
        lsSong = view.findViewById(R.id.lsSong)
        bChangeBook = view.findViewById(R.id.bChangeBook)
        cDeepSearch = view.findViewById(R.id.cDeepSearch)
        circular_progress = view.findViewById(R.id.progress_circular)

        adapter = SongAdapter()
        lsSong.adapter = adapter
        lsSong.setOnItemClickListener { _, _, position, _ ->
            val songInfo = adapter.getItem(position)
            (requireActivity() as Listener).onSongInfoSelected(songInfo)
            dismiss()
        }

        // restore state before attaching listeners, so restoring does not trigger a new search
        searchView.setQuery(viewModel.filterString, false)
        cDeepSearch.isChecked = viewModel.deepSearch
        val selectedBookName = viewModel.selectedBookName
        if (selectedBookName == null) {
            bChangeBook.setText(R.string.sn_bookselector_all)
        } else {
            bChangeBook.text = SongBookUtil.escapeSongBookName(selectedBookName)
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                setFilterStringAndSearch(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                setFilterStringAndSearch(newText)
                return true
            }
        })

        cDeepSearch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.deepSearch = isChecked
            viewModel.startSearch()
        }

        popupChangeBook = SongBookUtil.getSongBookPopupMenu(requireContext(), true, false, bChangeBook)
        popupChangeBook.setOnMenuItemClickListener(SongBookUtil.getSongBookOnMenuItemClickListener(songBookSelected))
        bChangeBook.setOnClickListener { popupChangeBook.show() }

        var listPositionRestored = false
        viewModel.results.observe(viewLifecycleOwner) { data ->
            adapter.setData(data)
            if (!listPositionRestored) {
                listPositionRestored = true
                lsSong.setSelectionFromTop(viewModel.listPosition, viewModel.listPositionTop)
            }
        }
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            circular_progress.visibility = if (loading) View.VISIBLE else View.INVISIBLE
        }

        if (viewModel.results.value == null) {
            viewModel.startSearch()
        }
    }

    override fun onStart() {
        super.onStart()

        // occupy the full screen height, and start expanded
        val dialog = dialog as? BottomSheetDialog ?: return
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.updateLayoutParams<ViewGroup.LayoutParams> {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onDestroyView() {
        viewModel.listPosition = lsSong.firstVisiblePosition
        viewModel.listPositionTop = lsSong.getChildAt(0)?.top ?: 0
        super.onDestroyView()
    }

    private fun setFilterStringAndSearch(query: String) {
        viewModel.filterString = query
        viewModel.startSearch()
    }

    private val songBookSelected = object : SongBookUtil.DefaultOnSongBookSelectedListener() {
        override fun onAllSelected() {
            bChangeBook.setText(R.string.sn_bookselector_all)
            viewModel.selectedBookName = null
            viewModel.startSearch()
        }

        override fun onSongBookSelected(name: String) {
            bChangeBook.text = SongBookUtil.escapeSongBookName(name)
            viewModel.selectedBookName = name
            viewModel.startSearch()
        }
    }

    inner class SongAdapter : BaseAdapter() {
        private var list: List<SongInfo>? = null

        override fun getCount() = list?.size ?: 0

        fun setData(data: List<SongInfo>?) {
            list = data
            notifyDataSetChanged()
        }

        override fun getItem(position: Int): SongInfo = list!![position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val res = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)

            val lTitle = res.findViewById<TextView>(R.id.lTitle)
            val lTitleOriginal = res.findViewById<TextView>(R.id.lTitleOriginal)
            val lBookName = res.findViewById<TextView>(R.id.lBookName)

            val songInfo = getItem(position)
            lTitle.text = "${songInfo.code}. ${songInfo.title}"
            if (songInfo.title_original != null) {
                lTitleOriginal.visibility = View.VISIBLE
                lTitleOriginal.text = songInfo.title_original
            } else {
                lTitleOriginal.visibility = View.GONE
            }
            lBookName.text = SongBookUtil.escapeSongBookName(songInfo.bookName)

            return res
        }
    }

    companion object {
        const val FRAGMENT_TAG = "SongListBottomSheet"
    }
}

class SongListViewModel : ViewModel() {
    var filterString = ""
    var selectedBookName: String? = null
    var deepSearch = false
    var listPosition = 0
    var listPositionTop = 0

    val results = MutableLiveData<List<SongInfo>>()
    val loading = MutableLiveData(false)

    /**
     * Bumped on every [startSearch] so that a result delivered for a stale query
     * (the executor does not guarantee ordering across threads) is dropped.
     */
    private val searchGeneration = AtomicInteger()

    fun startSearch() {
        val generation = searchGeneration.incrementAndGet()
        loading.value = true

        val bookName = selectedBookName
        val deep = deepSearch
        val filter = filterString.trim().takeIf { it.isNotEmpty() }

        Background.run {
            val songDb = App.services.storage.songDb
            val res = if (deep) {
                songDb.listSongInfosByBookNameAndDeepFilter(bookName, filter)
            } else {
                SongFilter.filterSongInfosByString(songDb.listSongInfosByBookName(bookName), filter)
            }
            if (searchGeneration.get() == generation) {
                results.postValue(res)
                loading.postValue(false)
            }
        }
    }
}
