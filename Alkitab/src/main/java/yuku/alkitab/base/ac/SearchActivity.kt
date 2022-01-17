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
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.Keep
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.cursoradapter.widget.CursorAdapter
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import java.util.ArrayList
import java.util.Locale
import yuku.afw.storage.Preferences
import yuku.afw.widget.EasyAdapter
import yuku.alkitab.base.App
import yuku.alkitab.base.S
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.Appearances
import yuku.alkitab.base.util.Background
import yuku.alkitab.base.util.ClipboardUtil
import yuku.alkitab.base.util.Foreground
import yuku.alkitab.base.util.FormattedVerseText
import yuku.alkitab.base.util.Jumper
import yuku.alkitab.base.util.QueryTokenizer
import yuku.alkitab.base.util.SearchEngine
import yuku.alkitab.base.util.SearchEngine.ReadyTokens
import yuku.alkitab.base.util.TextColorUtil
import yuku.alkitab.debug.BuildConfig
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

class SearchActivity : BaseActivity() {
    private lateinit var root: View
    private lateinit var bVersion: TextView
    private lateinit var searchView: SearchView
    private lateinit var lsSearchResults: ListView
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
    private var adapter = SearchAdapter(IntArrayList(), emptyList())
        set(value) {
            field = value
            lsSearchResults.adapter = value
        }

    private lateinit var searchInVersion: Version
    private lateinit var searchInVersionId: String
    private var textSizeMult = 0f
    private lateinit var searchHistoryAdapter: SearchHistoryAdapter
    private var actionMode: ActionMode? = null

    private val lsSearchResults_itemLongClick = OnItemLongClickListener { _, _, position, _ ->
        if (actionMode == null) {
            actionMode = startSupportActionMode(object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    menuInflater.inflate(R.menu.context_search, menu)
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                    val checked_count = lsSearchResults.checkedItemCount
                    if (checked_count == 1) {
                        mode.setTitle(R.string.verse_select_one_verse_selected)
                    } else {
                        mode.title = getString(R.string.verse_select_multiple_verse_selected, checked_count.toString())
                    }
                    return true
                }

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    val itemId = item.itemId
                    if (itemId == R.id.menuSelectAll) {
                        var i = 0
                        val size = adapter.count
                        while (i < size) {
                            lsSearchResults.setItemChecked(i, true)
                            i++
                        }
                        onCheckedVerseChanged()
                    } else if (itemId == R.id.menuCopy) {
                        val sb = SpannableStringBuilder()
                        val aris = adapter.searchResults
                        val checkeds = lsSearchResults.checkedItemPositions
                        var i = 0
                        val size = checkeds.size()
                        while (i < size) {
                            if (!checkeds.valueAt(i)) {
                                i++
                                continue
                            }
                            val ari = aris[checkeds.keyAt(i)]
                            val reference = searchInVersion.reference(ari)
                            val verseText = FormattedVerseText.removeSpecialCodes(searchInVersion.loadVerseText(ari))
                            val sb_len = sb.length
                            sb.append(reference).append("\n").append(verseText).append("\n\n")
                            if (size < 1000) { // too much spans is very slow
                                sb.setSpan(UnderlineSpan(), sb_len, sb_len + reference.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                            i++
                        }
                        ClipboardUtil.copyToClipboard(sb)
                        Snackbar.make(root, R.string.search_selected_verse_copied, Snackbar.LENGTH_SHORT).show()
                        mode.finish()
                        return true
                    }
                    return false
                }

                override fun onDestroyActionMode(mode: ActionMode) {
                    uncheckAllVerses()
                    actionMode = null
                }
            })
        }
        val old = lsSearchResults.isItemChecked(position)
        lsSearchResults.setItemChecked(position, !old)
        onCheckedVerseChanged()
        true
    }

    private fun uncheckAllVerses() {
        val checkeds = lsSearchResults.checkedItemPositions
        for (i in checkeds.size() - 1 downTo 0) {
            if (checkeds.valueAt(i)) lsSearchResults.setItemChecked(checkeds.keyAt(i), false)
        }
    }

    private fun onCheckedVerseChanged() {
        adapter.notifyDataSetChanged()
        if (lsSearchResults.checkedItemCount == 0) {
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
        searchInVersion = S.activeVersion()
        searchInVersionId = S.activeVersionId()

        textSizeMult = S.getDb().getPerVersionSettings(searchInVersionId).fontSizeMultiplier
        bVersion.setOnClickListener(bVersion_click)
        searchView = findViewById(R.id.searchView)
        searchView.isSubmitButtonEnabled = true
        val autoCompleteTextView = findAutoCompleteTextView(searchView)
        if (autoCompleteTextView != null) {
            autoCompleteTextView.threshold = 0
        }
        searchView.suggestionsAdapter = SearchHistoryAdapter().also { searchHistoryAdapter = it }
        searchView.setOnSuggestionListener(object : SearchView.OnSuggestionListener {
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
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query1: String): Boolean {
                search(query1)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                searchHistoryAdapter.setQuery(newText)
                return false
            }
        })

        // stop opening suggestion dropdown
        searchView.post {
            findAutoCompleteTextViewRecursive(searchView)?.dismissDropDown()
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
        lsSearchResults.cacheColorHint = applied.backgroundColor
        lsSearchResults.emptyView = tSearchTips
        Appearances.applyTextAppearance(tSearchTips, textSizeMult)
        hiliteColor = TextColorUtil.getSearchKeywordByBrightness(applied.backgroundBrightness)
        lsSearchResults.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE
        lsSearchResults.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (actionMode != null) {
                // By default setItemChecked will be called when action mode is on.
                // We just need to invalidate the view and the selected verse count.
                onCheckedVerseChanged()
            } else {
                val ari = adapter.searchResults[position]
                startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(ari))
                // Because we are in CHOICE_MODE_MULTIPLE, this verse is automatically marked as checked.
                // so we have to manually uncheck this.
                uncheckAllVerses()
            }
        }
        lsSearchResults.onItemLongClickListener = lsSearchResults_itemLongClick
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

    private fun findAutoCompleteTextView(group: ViewGroup): AutoCompleteTextView? {
        var i = 0
        val len = group.childCount
        while (i < len) {
            val child = group.getChildAt(i)
            if (child is AutoCompleteTextView) {
                return child
            } else if (child is ViewGroup) {
                return findAutoCompleteTextView(child)
            }
            i++
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
            var i = 0
            val len = selectedBookIds.size()
            while (i < len) {
                if (selectedBookIds.valueAt(i)) {
                    k = selectedBookIds.keyAt(i)
                    c++
                    if (c > 1) break
                }
                i++
            }
            when (c) {
                1 -> k
                else -> -1
            }
        }
        filterUserAction++

        if (olds != null && news != null) {    // both are either true or false
            cFilterOlds.visibility = View.VISIBLE
            cFilterOlds.isChecked = olds
            cFilterNews.visibility = View.VISIBLE
            cFilterNews.isChecked = news
            cFilterSingleBook.visibility = View.VISIBLE
            cFilterSingleBook.isChecked = false
            tFilterAdvanced.visibility = View.GONE
        } else {
            if (oneOfThemOn != -1 && oneOfThemOn == openedBookId) {
                cFilterOlds.visibility = View.VISIBLE
                cFilterOlds.isChecked = false
                cFilterNews.visibility = View.VISIBLE
                cFilterNews.isChecked = false
                cFilterSingleBook.visibility = View.VISIBLE
                cFilterSingleBook.isChecked = true
                tFilterAdvanced.visibility = View.GONE
            } else {
                cFilterOlds.visibility = View.VISIBLE
                cFilterOlds.isChecked = false
                cFilterNews.visibility = View.VISIBLE
                cFilterNews.isChecked = false
                cFilterSingleBook.visibility = View.GONE
                tFilterAdvanced.visibility = View.VISIBLE
                var cnt = 0
                var bookId = 0
                var i = 0
                val len = selectedBookIds.size()
                while (i < len) {
                    if (selectedBookIds.valueAt(i)) {
                        cnt++
                        bookId = selectedBookIds.keyAt(i)
                    }
                    i++
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
        run {
            if (isChecked) {
                cFilterSingleBook.visibility = View.VISIBLE
                cFilterSingleBook.isChecked = false
                tFilterAdvanced.visibility = View.GONE
            }
            setSelectedBookIdsBasedOnFilter()
        }
        filterUserAction--
    }

    private val cFilterNews_checkedChange = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (filterUserAction != 0) return@OnCheckedChangeListener
        filterUserAction++
        run {
            if (isChecked) {
                cFilterSingleBook.visibility = View.VISIBLE
                cFilterSingleBook.isChecked = false
                tFilterAdvanced.visibility = View.GONE
            }
            setSelectedBookIdsBasedOnFilter()
        }
        filterUserAction--
    }

    private val cFilterSingleBook_checkedChange = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (filterUserAction != 0) return@OnCheckedChangeListener
        filterUserAction++
        run {
            if (isChecked) {
                cFilterOlds.isChecked = false
                cFilterNews.isChecked = false
            }
            setSelectedBookIdsBasedOnFilter()
        }
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

    private fun getQuery(): SearchEngine.Query {
        val res = SearchEngine.Query()
        res.query_string = searchView.query.toString()
        res.bookIds = selectedBookIds
        return res
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
            }
            return
        }
        @Suppress("deprecation")
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun search(query_string: String) {
        if (query_string.trim().isEmpty()) {
            return
        }
        run {
            // check if there is anything chosen
            val firstSelected = selectedBookIds.indexOfValue(true)
            if (firstSelected < 0) {
                MaterialDialog.Builder(this)
                    .content(R.string.pilih_setidaknya_satu_kitab)
                    .positiveText(R.string.ok)
                    .show()
                return
            }
        }
        val tokens = QueryTokenizer.tokenize(query_string).toList()
        val pd = MaterialDialog.Builder(this)
            .content(getString(R.string.search_searching_tokens, tokens))
            .cancelable(false)
            .progress(true, 0)
            .show()


        Background.run {
            val debugstats_revIndexUsed: Boolean
            searchHistoryAdapter.setData(addSearchHistoryEntry(query_string))
            val totalMs = System.currentTimeMillis()
            val cpuMs = SystemClock.currentThreadTimeMillis()
            val result: IntArrayList = synchronized(this@SearchActivity) {
                if (usingRevIndex()) {
                    debugstats_revIndexUsed = true
                    SearchEngine.searchByRevIndex(searchInVersion, getQuery())
                } else {
                    debugstats_revIndexUsed = false
                    SearchEngine.searchByGrep(searchInVersion, getQuery())
                }
            }
            val debugstats_totalTimeMs = System.currentTimeMillis() - totalMs
            val debugstats_cpuTimeMs = SystemClock.currentThreadTimeMillis() - cpuMs

            Foreground.run {
                actionMode?.finish()
                uncheckAllVerses()
                adapter = SearchAdapter(result, tokens)

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
                        if (!jumper.parseSucceeded) {
                            return 0
                        }
                        val chapter_1 = jumper.chapter
                        if (chapter_1 == 0) return 0
                        val version = searchInVersion
                        val bookId = jumper.getBookId(version.consecutiveBooks)
                        if (bookId == -1) return 0
                        val book = version.getBook(bookId) ?: return 0
                        if (chapter_1 > book.chapter_count) return 0
                        val verse_1 = jumper.verse
                        return if (verse_1 != 0 && verse_1 > book.verse_counts[chapter_1 - 1]) 0 else Ari.encode(bookId, chapter_1, verse_1)
                    }

                    val jumper = Jumper(query_string)
                    var noresult: CharSequence? = getText(R.string.search_no_result)
                    noresult = TextUtils.expandTemplate(noresult, query_string)
                    val fallbackAri = shouldShowFallback(jumper)
                    if (fallbackAri != 0) {
                        val sb = SpannableStringBuilder()
                        sb.append(noresult)
                        sb.append("\n\n")
                        var fallback: CharSequence? = getText(R.string.search_no_result_fallback)
                        fallback = TextUtils.expandTemplate(fallback, searchInVersion.reference(fallbackAri))
                        sb.append(fallback)
                        tSearchTips.text = sb
                        tSearchTips.setOnClickListener {
                            if (Ari.toVerse(fallbackAri) == 0) {
                                startActivity(Launcher.openAppAtBibleLocation(fallbackAri))
                            } else {
                                startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(fallbackAri))
                            }
                        }
                    } else {
                        tSearchTips.text = noresult
                        tSearchTips.isClickable = false
                        tSearchTips.setOnClickListener(null)
                    }
                }
                if (BuildConfig.DEBUG) {
                    MaterialDialog.Builder(this@SearchActivity)
                        .content("This msg is shown only on DEBUG build\n\n" +
                            "Search results: ${result.size()}\n" +
                            "Method: ${if (debugstats_revIndexUsed) "revindex" else "grep"}\n" +
                            "Total time: $debugstats_totalTimeMs ms\n" +
                            "CPU (thread) time: $debugstats_cpuTimeMs ms"
                        )
                        .positiveText(R.string.ok)
                        .show()
                }
                pd.setOnDismissListener(null)
                try {
                    pd.dismiss()
                } catch (ignored: Exception) {
                }
            }
        }
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

    inner class SearchAdapter(val searchResults: IntArrayList, tokens: List<String>) : EasyAdapter() {
        val rt: ReadyTokens = ReadyTokens(tokens.toTypedArray())

        override fun getCount(): Int {
            return searchResults.size()
        }

        override fun newView(position: Int, parent: ViewGroup): View {
            return layoutInflater.inflate(R.layout.item_search_result, parent, false)
        }

        override fun bindView(view: View, position: Int, parent: ViewGroup) {
            val checked = lsSearchResults.isItemChecked(position)
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
            val lReference = view.findViewById<TextView>(R.id.lReference)
            val lSnippet = view.findViewById<TextView>(R.id.lSnippet)
            val ari = searchResults[position]
            val sb = SpannableStringBuilder(searchInVersion.reference(ari))
            Appearances.applySearchResultReferenceAppearance(lReference, sb, textSizeMult)
            if (checked) {
                lReference.setTextColor(checkedTextColor)
            }
            Appearances.applyTextAppearance(lSnippet, textSizeMult)
            if (checked) {
                lSnippet.setTextColor(checkedTextColor)
            }
            val verseText = FormattedVerseText.removeSpecialCodes(searchInVersion.loadVerseText(ari))
            if (verseText != null) {
                lSnippet.text = SearchEngine.hilite(verseText, rt, if (checked) checkedTextColor else hiliteColor)
            } else {
                lSnippet.setText(R.string.generic_verse_not_available_in_this_version)
            }
            if (checked) {
                view.setBackgroundColor(checkedBgColor)
            } else {
                view.setBackgroundColor(0x0)
            }
        }

    }

    companion object {
        fun createIntent(openedBookId: Int): Intent {
            val res = Intent(App.context, SearchActivity::class.java)
            res.putExtra(EXTRA_openedBookId, openedBookId)
            return res
        }

        fun findAutoCompleteTextViewRecursive(group: ViewGroup): AutoCompleteTextView? {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child is AutoCompleteTextView) {
                    return child
                }
                if (child is ViewGroup) {
                    val res = findAutoCompleteTextViewRecursive(child)
                    if (res != null) {
                        return res
                    }
                }
            }
            return null
        }
    }
}