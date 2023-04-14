package yuku.alkitab.base.ac

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.SparseBooleanArray
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.Keep
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.cursoradapter.widget.CursorAdapter
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import java.util.ArrayList
import java.util.Locale
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.S
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Appearances
import yuku.alkitab.base.util.ClipboardUtil
import yuku.alkitab.base.util.Debouncer
import yuku.alkitab.base.util.Foreground
import yuku.alkitab.base.util.FormattedVerseText
import yuku.alkitab.base.util.Jumper
import yuku.alkitab.base.util.QueryTokenizer
import yuku.alkitab.base.util.SearchEngine
import yuku.alkitab.base.util.TextColorUtil
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList
import yuku.alkitabintegration.display.Launcher

private const val EXTRA_openedBookId = "openedBookId"
private const val REQCODE_bookFilter = 1
private const val ID_CLEAR_HISTORY = -1L
private const val COLINDEX_ID = 0
private const val COLINDEX_QUERY_STRING = 1
private const val TAG = "SearchActivity"

class SearchActivity : BaseActivity() {
    private lateinit var root: View
    private lateinit var bVersion: TextView
    private lateinit var searchView: SearchView
    private lateinit var progressbar: CircularProgressIndicator
    private lateinit var bSearch: ImageButton
    private lateinit var lsSearchResults: RecyclerView
    private lateinit var tSearchTips: TextView
    private lateinit var panelFilter: View
    private lateinit var cFilterOlds: CheckBox
    private lateinit var cFilterNews: CheckBox
    private lateinit var cFilterSingleBook: CheckBox
    private lateinit var tFilterAdvanced: TextView
    private lateinit var bEditFilter: View

    private var hiliteColor = 0
    private var selectedBookIds = SparseBooleanArray()
    private var openedBookId = 0
    private var filterUserAction = 0 // when it's not user action, set to nonzero
    private val adapter = SearchAdapter(IntArrayList(), emptyList())

    private var searchInVersion: Version = S.activeVersion()
    private var searchInVersionId: String = S.activeVersionId()
    private var textSizeMult = S.getDb().getPerVersionSettings(searchInVersionId).fontSizeMultiplier

    private lateinit var searchHistoryAdapter: SearchHistoryAdapter
    private var actionMode: ActionMode? = null

    private fun createActionModeCallback() = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menuInflater.inflate(R.menu.context_search, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val checked_count = adapter.checkedPositions.size
            if (checked_count == 1) {
                mode.setTitle(R.string.verse_select_one_verse_selected)
            } else {
                mode.title = getString(R.string.verse_select_multiple_verse_selected, checked_count.toString())
            }
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.menuSelectAll -> {
                    adapter.checkAll()
                    true
                }

                R.id.menuCopy -> {
                    val sb = SpannableStringBuilder()
                    val aris = adapter.searchResults
                    for (i in adapter.checkedPositions.sorted()) {
                        val ari = aris[i]
                        val reference = searchInVersion.reference(ari)
                        val verseText = FormattedVerseText.removeSpecialCodes(searchInVersion.loadVerseText(ari))
                        val sb_len = sb.length
                        sb.append(reference).append("\n").append(verseText).append("\n\n")
                        if (adapter.checkedPositions.size < 1000) { // too much spans is very slow
                            sb.setSpan(UnderlineSpan(), sb_len, sb_len + reference.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                    ClipboardUtil.copyToClipboard(sb)
                    Snackbar.make(root, R.string.search_selected_verse_copied, Snackbar.LENGTH_SHORT).show()
                    mode.finish()
                    true
                }

                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            adapter.uncheckAll()
            actionMode = null
        }
    }

    private fun onCheckedVerseChanged() {
        @Suppress("NotifyDataSetChanged")
        adapter.notifyDataSetChanged()

        if (adapter.checkedPositions.isEmpty()) {
            actionMode?.finish()
        } else {
            actionMode?.invalidate()
        }
    }

    @Keep
    class SearchHistory(val entries: MutableList<Entry>) {
        @Keep
        class Entry(val query_string: String)
    }

    inner class SearchHistoryAdapter : CursorAdapter(App.context, null, 0) {
        var entries: MutableList<SearchHistory.Entry> = ArrayList()
        private var query_string: String? = null
        override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
            return layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false)
        }

        override fun bindView(view: View, context: Context, cursor: Cursor) {
            val text1 = view.findViewById<TextView>(android.R.id.text1)
            val _id = cursor.getLong(COLINDEX_ID)
            val text = if (_id == -1L) {
                val sb = SpannableStringBuilder(getString(R.string.search_clear_history))
                sb.setSpan(ForegroundColorSpan(ResourcesCompat.getColor(resources, R.color.escape, theme)), 0, sb.length, 0)
                sb
            } else {
                cursor.getString(COLINDEX_QUERY_STRING)
            }
            text1.text = text
        }

        override fun convertToString(cursor: Cursor): CharSequence {
            return cursor.getString(COLINDEX_QUERY_STRING)
        }

        fun setData(searchHistory: SearchHistory) {
            entries.clear()
            entries.addAll(searchHistory.entries)
            filter()
        }

        fun setQuery(query_string: String?) {
            this.query_string = query_string
            filter()
        }

        private fun filter() {
            val mc = MatrixCursor(arrayOf("_id", "query_string") /* Can be any string, but this must correspond to COLINDEX_ID and COLINDEX_QUERY_STRING */)
            for (i in entries.indices) {
                val entry = entries[i]
                val query_string = query_string
                if (query_string.isNullOrEmpty() || entry.query_string.lowercase(Locale.getDefault()).startsWith(query_string.lowercase(Locale.getDefault()))) {
                    mc.addRow(arrayOf(i.toLong(), entry.query_string))
                }
            }

            // add last item to clear search history only if there is something else
            if (mc.count > 0) {
                mc.addRow(arrayOf(ID_CLEAR_HISTORY, ""))
            }

            // sometimes this is called from bg. So we need to make sure this is run on UI thread.
            runOnUiThread { swapCursor(mc) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        root = findViewById(R.id.root)
        lsSearchResults = findViewById(R.id.lsSearchResults)
        lsSearchResults.adapter = adapter
        FastScrollerBuilder(lsSearchResults).build()
        tSearchTips = findViewById(R.id.tSearchTips)
        panelFilter = findViewById(R.id.panelFilter)
        cFilterOlds = findViewById(R.id.cFilterOlds)
        cFilterNews = findViewById(R.id.cFilterNews)
        cFilterSingleBook = findViewById(R.id.cFilterSingleBook)
        tFilterAdvanced = findViewById(R.id.tFilterAdvanced)
        bEditFilter = findViewById(R.id.bEditFilter)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        bVersion = findViewById(R.id.bVersion)
        bVersion.setOnClickListener(bVersion_click)
        searchView = findViewById<SearchView>(R.id.searchView).apply {
            findAutoCompleteTextView()?.threshold = 0
            suggestionsAdapter = SearchHistoryAdapter().also { searchHistoryAdapter = it }
            setOnSuggestionListener(object : SearchView.OnSuggestionListener {
                override fun onSuggestionSelect(position: Int): Boolean {
                    return false
                }

                override fun onSuggestionClick(position: Int): Boolean {
                    val c = searchHistoryAdapter.cursor ?: return false
                    val ok = c.moveToPosition(position)
                    if (!ok) return false
                    val _id = c.getLong(COLINDEX_ID)
                    if (_id == ID_CLEAR_HISTORY) {
                        saveSearchHistory(null)
                        searchHistoryAdapter.setData(loadSearchHistory())
                    } else {
                        searchView.setQuery(c.getString(COLINDEX_QUERY_STRING), true)
                    }
                    return true
                }
            })
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    search(query)
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    suggester.submit(newText)
                    return true
                }
            })

            // stop opening suggestion dropdown
            post {
                searchView.findAutoCompleteTextView()?.dismissDropDown()
            }
        }

        progressbar = findViewById(R.id.progressbar)
        bSearch = findViewById(R.id.bSearch)
        bSearch.setOnClickListener {
            search(searchView.query.toString())
        }

        run {
            val sb = SpannableStringBuilder(tSearchTips.text)
            while (true) {
                val pos = TextUtils.indexOf(sb, "[q]")
                if (pos < 0) break
                sb.replace(pos, pos + 3, "\"")
            }
            tSearchTips.text = sb
        }

        val applied = S.applied()
        tSearchTips.setBackgroundColor(applied.backgroundColor)
        lsSearchResults.setBackgroundColor(applied.backgroundColor)
        Appearances.applyTextAppearance(tSearchTips, textSizeMult)
        hiliteColor = TextColorUtil.getSearchKeywordByBrightness(applied.backgroundBrightness)

        bEditFilter.setOnClickListener { bEditFilter_click() }
        cFilterOlds.setOnCheckedChangeListener(cFilterOlds_checkedChange)
        cFilterNews.setOnCheckedChangeListener(cFilterNews_checkedChange)
        cFilterSingleBook.setOnCheckedChangeListener(cFilterSingleBook_checkedChange)

        run {
            openedBookId = intent.getIntExtra(EXTRA_openedBookId, -1)
            val book = S.activeVersion().getBook(openedBookId)
            if (book == null) { // active version has changed somehow when this activity fainted. so, invalidate openedBookId
                openedBookId = -1
                cFilterSingleBook.isEnabled = false
            } else {
                cFilterSingleBook.text = getString(R.string.search_bookname_only, book.shortName)
            }
        }

        for (book in searchInVersion.consecutiveBooks) {
            selectedBookIds.put(book.bookId, true)
        }
        configureFilterDisplayOldNewTest()

        if (usingRevIndex()) {
            SearchEngine.preloadRevIndex()
        }
        displaySearchInVersion()
    }

    private fun ViewGroup.findAutoCompleteTextView(): AutoCompleteTextView? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is AutoCompleteTextView) {
                return child
            } else if (child is ViewGroup) {
                val res = child.findAutoCompleteTextView()
                if (res != null) {
                    return res
                }
            }
        }
        return null
    }

    override fun onStart() {
        super.onStart()
        searchHistoryAdapter.setData(loadSearchHistory())
    }

    private fun displaySearchInVersion() {
        val versionInitials = searchInVersion.initials
        bVersion.text = versionInitials
        searchView.queryHint = getString(R.string.search_in_version_short_name_placeholder, versionInitials)

        @Suppress("NotifyDataSetChanged")
        adapter.notifyDataSetChanged()
    }

    private fun configureFilterDisplayOldNewTest() {
        // the following variables will have value:
        // if some are off and some are on -> null.
        // if all on -> true.
        // if all off -> false.
        val olds = run {
            var c_on = 0
            var c_off = 0
            for (i in 0..38) {
                val on = selectedBookIds[i, false]
                if (on) c_on++ else c_off++
            }
            when {
                c_on == 39 -> true
                c_off == 39 -> false
                else -> null
            }
        }
        val news = run {
            var c_on = 0
            var c_off = 0
            for (i in 39..65) {
                val nyala = selectedBookIds[i, false]
                if (nyala) c_on++ else c_off++
            }
            when {
                c_on == 27 -> true
                c_off == 27 -> false
                else -> null
            }
        }
        val oneOfThemOn = run {
            var c = 0
            var k = 0
            for (i in 0 until selectedBookIds.size()) {
                if (selectedBookIds.valueAt(i)) {
                    k = selectedBookIds.keyAt(i)
                    c++
                    if (c > 1) break
                }
            }
            when (c) {
                1 -> k
                else -> -1
            }
        }
        filterUserAction++

        when {
            olds != null && news != null -> {    // both are either true or false
                cFilterOlds.isChecked = olds
                cFilterNews.isChecked = news
                cFilterSingleBook.visibility = View.VISIBLE
                cFilterSingleBook.isChecked = false
                tFilterAdvanced.visibility = View.GONE
            }
            oneOfThemOn != -1 && oneOfThemOn == openedBookId -> {
                cFilterOlds.isChecked = false
                cFilterNews.isChecked = false
                cFilterSingleBook.visibility = View.VISIBLE
                cFilterSingleBook.isChecked = true
                tFilterAdvanced.visibility = View.GONE
            }
            else -> {
                cFilterOlds.isChecked = false
                cFilterNews.isChecked = false
                cFilterSingleBook.visibility = View.GONE
                tFilterAdvanced.visibility = View.VISIBLE
                var cnt = 0
                var bookId = 0
                for (i in 0 until selectedBookIds.size()) {
                    if (selectedBookIds.valueAt(i)) {
                        cnt++
                        bookId = selectedBookIds.keyAt(i)
                    }
                }
                if (cnt != 1) {
                    tFilterAdvanced.text = getString(R.string.search_filter_multiple_books_selected, cnt.toString())
                } else {
                    tFilterAdvanced.text = searchInVersion.reference(bookId, 0, 0)
                }
            }
        }

        filterUserAction--

        val singleBookReference = searchInVersion.reference(openedBookId, 0, 0)
        cFilterSingleBook.text = getString(R.string.search_bookname_only, singleBookReference)
    }

    private val cFilterOlds_checkedChange = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (filterUserAction != 0) return@OnCheckedChangeListener
        filterUserAction++

        if (isChecked) {
            cFilterSingleBook.visibility = View.VISIBLE
            cFilterSingleBook.isChecked = false
            tFilterAdvanced.visibility = View.GONE
        }
        setSelectedBookIdsBasedOnFilter()
        search(searchView.query.toString())

        filterUserAction--
    }

    private val cFilterNews_checkedChange = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (filterUserAction != 0) return@OnCheckedChangeListener
        filterUserAction++

        if (isChecked) {
            cFilterSingleBook.visibility = View.VISIBLE
            cFilterSingleBook.isChecked = false
            tFilterAdvanced.visibility = View.GONE
        }
        setSelectedBookIdsBasedOnFilter()
        search(searchView.query.toString())

        filterUserAction--
    }

    private val cFilterSingleBook_checkedChange = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (filterUserAction != 0) return@OnCheckedChangeListener
        filterUserAction++

        if (isChecked) {
            cFilterOlds.isChecked = false
            cFilterNews.isChecked = false
        }
        setSelectedBookIdsBasedOnFilter()
        search(searchView.query.toString())

        filterUserAction--
    }

    private val bVersion_click = View.OnClickListener {
        S.openVersionsDialog(this, false, searchInVersionId) { mv: MVersion ->
            val selectedVersion = mv.version
            if (selectedVersion == null) {
                MaterialDialog.Builder(this@SearchActivity)
                    .content(getString(R.string.version_error_opening, mv.longName))
                    .positiveText(R.string.ok)
                    .show()
                return@openVersionsDialog
            }

            searchInVersion = selectedVersion
            searchInVersionId = mv.versionId
            textSizeMult = S.getDb().getPerVersionSettings(searchInVersionId).fontSizeMultiplier

            Appearances.applyTextAppearance(tSearchTips, textSizeMult)
            displaySearchInVersion()
            configureFilterDisplayOldNewTest()
            bVersion.text = selectedVersion.initials

            @Suppress("NotifyDataSetChanged")
            adapter.notifyDataSetChanged()
        }
    }

    private fun setSelectedBookIdsBasedOnFilter() {
        selectedBookIds.clear()
        if (cFilterOlds.isChecked) for (i in 0..38) selectedBookIds.put(i, true)
        if (cFilterNews.isChecked) for (i in 39..65) selectedBookIds.put(i, true)
        if (openedBookId != -1) {
            if (cFilterSingleBook.isChecked) selectedBookIds.put(openedBookId, true)
        }
    }

    private fun bEditFilter_click() {
        @Suppress("deprecation")
        startActivityForResult(SearchBookFilterActivity.createIntent(selectedBookIds, searchInVersion.consecutiveBooks), REQCODE_bookFilter)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQCODE_bookFilter && resultCode == RESULT_OK) {
            val result = SearchBookFilterActivity.obtainResult(data)
            if (result != null) {
                selectedBookIds = result.selectedBookIds
                configureFilterDisplayOldNewTest()
                search(searchView.query.toString())
            }
            return
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    @JvmInline
    value class SearchRequest(val query: SearchEngine.Query)

    data class SearchResult(val query: SearchEngine.Query, val result: IntArrayList)

    /**
     * So we can delay a bit before updating suggestions.
     */
    private val suggester = object : Debouncer<String, String>(200) {
        override fun process(payload: String): String {
            return payload
        }

        override fun onResult(result: String) {
            searchHistoryAdapter.setQuery(result)
        }
    }

    private val searcher = object : Debouncer<SearchRequest, SearchResult>(0) {
        override fun process(request: SearchRequest): SearchResult {
            val query = request.query
            val totalMs = System.currentTimeMillis()
            val cpuMs = SystemClock.currentThreadTimeMillis()
            val debugstats_revIndexUsed: Boolean
            val result = if (usingRevIndex()) {
                debugstats_revIndexUsed = true
                SearchEngine.searchByRevIndex(searchInVersion, query)
            } else {
                debugstats_revIndexUsed = false
                SearchEngine.searchByGrep(searchInVersion, query)
            }
            val debugstats_totalTimeMs = System.currentTimeMillis() - totalMs
            val debugstats_cpuTimeMs = SystemClock.currentThreadTimeMillis() - cpuMs

            AppLog.d(
                TAG,
                "Search results: ${result.size()}\n" +
                    "Method: ${if (debugstats_revIndexUsed) "revindex" else "grep"}\n" +
                    "Total time: $debugstats_totalTimeMs ms\n" +
                    "CPU (thread) time: $debugstats_cpuTimeMs ms"
            )

            return SearchResult(query, result)
        }

        override fun onResult(searchResult: SearchResult) {
            val (query, result) = searchResult
            progressbar.isVisible = false
            bSearch.isVisible = true
            actionMode?.finish()

            val tokens = QueryTokenizer.tokenize(query.query_string).toList()
            adapter.uncheckAll()
            adapter.setData(result, tokens)

            tSearchTips.isVisible = result.size() == 0
            lsSearchResults.isVisible = result.size() > 0

            if (result.size() > 0) {
                Snackbar.make(lsSearchResults, getString(R.string.size_hasil, result.size()), Snackbar.LENGTH_LONG).show()

                //# close soft keyboard
                val inputManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                inputManager.hideSoftInputFromWindow(searchView.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
                searchView.clearFocus()
                lsSearchResults.requestFocus()
            } else {
                /**
                 * @return ari not 0 if fallback is to be shown
                 */
                fun shouldShowFallback(jumper: Jumper): Int {
                    if (!jumper.parseSucceeded) return 0
                    val chapter_1 = jumper.chapter
                    if (chapter_1 == 0) return 0
                    val version = searchInVersion
                    val bookId = jumper.getBookId(version.consecutiveBooks)
                    if (bookId == -1) return 0
                    val book = version.getBook(bookId) ?: return 0
                    if (chapter_1 > book.chapter_count) return 0
                    val verse_1 = jumper.verse
                    return if (verse_1 != 0 && verse_1 > book.verse_counts[chapter_1 - 1]) {
                        0
                    } else {
                        Ari.encode(bookId, chapter_1, verse_1)
                    }
                }

                val zeroResultMessage = TextUtils.expandTemplate(getText(R.string.search_no_result), query.query_string)
                val fallbackAri = shouldShowFallback(Jumper(query.query_string))
                if (fallbackAri != 0) {
                    tSearchTips.text = buildString {
                        append(zeroResultMessage)
                        append("\n\n")
                        append(TextUtils.expandTemplate(getText(R.string.search_no_result_fallback), searchInVersion.reference(fallbackAri)))
                    }

                    tSearchTips.setOnClickListener {
                        if (Ari.toVerse(fallbackAri) == 0) {
                            startActivity(Launcher.openAppAtBibleLocation(fallbackAri))
                        } else {
                            startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(fallbackAri))
                        }
                    }
                } else {
                    // If there is no books chosen, display a different message
                    if (selectedBookIds.indexOfValue(true) < 0) {
                        tSearchTips.setText(R.string.pilih_setidaknya_satu_kitab)
                    } else {
                        tSearchTips.text = zeroResultMessage
                    }
                    tSearchTips.isClickable = false
                    tSearchTips.setOnClickListener(null)
                }
            }
        }
    }

    private fun search(query_string: String) {
        if (query_string.isBlank()) return

        val query = SearchEngine.Query()
        query.query_string = query_string
        query.bookIds = selectedBookIds

        progressbar.isVisible = true
        bSearch.isVisible = false

        searchHistoryAdapter.setData(addSearchHistoryEntry(query.query_string))
        searchView.findAutoCompleteTextView()?.dismissDropDown()

        searcher.submit(SearchRequest(query))
    }

    fun loadSearchHistory(): SearchHistory {
        val json = Preferences.getString(Prefkey.searchHistory, null) ?: return SearchHistory(mutableListOf())
        return App.getDefaultGson().fromJson(json, SearchHistory::class.java)
    }

    fun saveSearchHistory(sh: SearchHistory?) {
        if (sh == null) {
            Preferences.remove(Prefkey.searchHistory)
        } else {
            val json = App.getDefaultGson().toJson(sh)
            Preferences.setString(Prefkey.searchHistory, json)
        }
    }

    // returns the modified SearchHistory
    private fun addSearchHistoryEntry(query_string: String): SearchHistory {
        val sh = loadSearchHistory()
        // look for this query_string and remove
        for (i in sh.entries.indices.reversed()) {
            if (query_string == sh.entries[i].query_string) {
                sh.entries.removeAt(i)
            }
        }
        sh.entries.add(0, SearchHistory.Entry(query_string))

        // if more than MAX, remove last
        while (sh.entries.size > 20) {
            sh.entries.removeAt(sh.entries.size - 1)
        }
        saveSearchHistory(sh)
        return sh
    }

    private fun usingRevIndex(): Boolean {
        return searchInVersionId == MVersionInternal.getVersionInternalId()
    }

    class ResultHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val lReference: TextView = itemView.findViewById(R.id.lReference)
        val lSnippet: TextView = itemView.findViewById(R.id.lSnippet)
    }

    inner class SearchAdapter(val searchResults: IntArrayList, tokens: List<String>) : RecyclerView.Adapter<ResultHolder>() {
        init {
            setHasStableIds(true)
        }

        var rt = SearchEngine.ReadyTokens(tokens.toTypedArray())
        val checkedPositions = mutableSetOf<Int>()

        override fun getItemCount(): Int {
            return searchResults.size()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultHolder {
            return ResultHolder(layoutInflater.inflate(R.layout.item_search_result, parent, false))
        }

        override fun onBindViewHolder(holder: ResultHolder, bindPosition: Int) {
            val checked = bindPosition in checkedPositions
            val checkedBgColor: Int
            val checkedTextColor: Int
            if (checked) {
                val colorRgb = Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default)
                checkedBgColor = ColorUtils.setAlphaComponent(colorRgb, 0xa0)
                checkedTextColor = TextColorUtil.getForCheckedVerse(checkedBgColor)
            } else {
                // no need to calculate
                checkedBgColor = 0
                checkedTextColor = 0
            }

            val ari = searchResults[bindPosition]
            val sb = SpannableStringBuilder(searchInVersion.reference(ari))
            Appearances.applySearchResultReferenceAppearance(holder.lReference, sb, textSizeMult)
            if (checked) {
                holder.lReference.setTextColor(checkedTextColor)
            }

            Appearances.applyTextAppearance(holder.lSnippet, textSizeMult)
            if (checked) {
                holder.lSnippet.setTextColor(checkedTextColor)
            }

            val verseText = FormattedVerseText.removeSpecialCodes(searchInVersion.loadVerseText(ari))
            if (verseText != null) {
                holder.lSnippet.text = SearchEngine.hilite(verseText, rt, if (checked) checkedTextColor else hiliteColor)
            } else {
                holder.lSnippet.setText(R.string.generic_verse_not_available_in_this_version)
            }

            if (checked) {
                holder.itemView.setBackgroundColor(checkedBgColor)
            } else {
                holder.itemView.setBackgroundColor(0x0)
            }

            holder.itemView.setOnLongClickListener {
                if (actionMode == null) {
                    actionMode = startSupportActionMode(createActionModeCallback())
                }

                val position = holder.bindingAdapterPosition
                if (position in adapter.checkedPositions) {
                    adapter.checkedPositions -= position
                } else {
                    adapter.checkedPositions += position
                }
                onCheckedVerseChanged()

                true
            }

            holder.itemView.setOnClickListener {
                val position = holder.bindingAdapterPosition
                if (actionMode != null) {
                    if (position in adapter.checkedPositions) {
                        adapter.checkedPositions -= position
                    } else {
                        adapter.checkedPositions += position
                    }
                    onCheckedVerseChanged()
                } else {
                    startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(searchResults[position]))
                    uncheckAll()
                }
            }
        }

        override fun getItemId(position: Int): Long {
            return searchResults[position].toLong()
        }

        fun checkAll() {
            val size = adapter.itemCount
            for (i in 0 until size) {
                checkedPositions += i
            }
            onCheckedVerseChanged()
        }

        fun uncheckAll() {
            checkedPositions.clear()
            onCheckedVerseChanged()
        }

        fun setData(searchResults: IntArrayList, tokens: List<String>) {
            this.searchResults.clear()
            for (i in 0 until searchResults.size()) {
                this.searchResults.add(searchResults[i])
            }

            this.rt = SearchEngine.ReadyTokens(tokens.toTypedArray())
            uncheckAll()
        }
    }

    companion object {
        fun createIntent(openedBookId: Int): Intent {
            return Intent(App.context, SearchActivity::class.java)
                .putExtra(EXTRA_openedBookId, openedBookId)
        }
    }
}
