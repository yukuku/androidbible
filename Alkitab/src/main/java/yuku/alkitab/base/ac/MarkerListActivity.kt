package yuku.alkitab.base.ac

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.ListView
import android.widget.SearchView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.appcompat.view.ActionMode
import androidx.core.graphics.ColorUtils
import androidx.core.util.size
import com.google.android.material.snackbar.Snackbar
import java.util.Locale
import kotlin.properties.Delegates
import yuku.afw.storage.Preferences
import yuku.afw.widget.EasyAdapter
import yuku.alkitab.base.App
import yuku.alkitab.base.IsiActivity
import yuku.alkitab.base.S
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.dialog.TypeBookmarkDialog
import yuku.alkitab.base.dialog.TypeHighlightDialog
import yuku.alkitab.base.storage.Db
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.Appearances
import yuku.alkitab.base.util.ClipboardUtil
import yuku.alkitab.base.util.Debouncer
import yuku.alkitab.base.util.FormattedVerseText
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.base.util.LabelColorUtil
import yuku.alkitab.base.util.QueryTokenizer
import yuku.alkitab.base.util.SearchEngine
import yuku.alkitab.base.util.SearchEngine.ReadyTokens
import yuku.alkitab.base.util.Sqlitil
import yuku.alkitab.base.util.TextColorUtil
import yuku.alkitab.base.widget.VerseRenderer
import yuku.alkitab.base.widget.VerseRenderer.FormattedTextResult
import yuku.alkitab.base.widget.VerseRendererHelper
import yuku.alkitab.debug.R
import yuku.alkitab.model.Label
import yuku.alkitab.model.Marker
import yuku.alkitab.model.Version
import yuku.alkitabintegration.display.Launcher
import yuku.devoxx.flowlayout.FlowLayout

private const val REQCODE_edit_note = 1

// in
private const val EXTRA_filter_kind = "filter_kind"
private const val EXTRA_filter_labelId = "filter_labelId"

class MarkerListActivity : BaseActivity() {
    private lateinit var root: View
    private lateinit var empty: View
    private lateinit var tEmpty: TextView
    private lateinit var bClearFilter: View
    private lateinit var progress: View
    private lateinit var lv: ListView
    private lateinit var adapter: MarkerListAdapter

    // from intent
    private lateinit var filter_kind: Marker.Kind
    private var filter_labelId by Delegates.notNull<Long>()

    private var searchView: SearchView? = null
    private var actionMode: ActionMode? = null

    private var sort_column = Db.Marker.createTime
    private var sort_ascending = false

    @IdRes
    private var sort_columnId = R.id.menuSortCreateTime

    private var currentlyUsedFilter: String? = null
    private var allMarkers = emptyList<Marker>()
    private val version = S.activeVersion()
    private val versionId = S.activeVersionId()
    private val textSizeMult = S.getDb().getPerVersionSettings(versionId).fontSizeMultiplier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_marker_list)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        root = findViewById(R.id.root)
        empty = findViewById(android.R.id.empty)
        tEmpty = findViewById(R.id.tEmpty)
        bClearFilter = findViewById(R.id.bClearFilter)
        progress = findViewById(R.id.progress)
        lv = findViewById(android.R.id.list)

        filter_kind = Marker.Kind.fromCode(intent.getIntExtra(EXTRA_filter_kind, 0)) ?: error("Must specify marker kind")
        filter_labelId = intent.getLongExtra(EXTRA_filter_labelId, 0)

        bClearFilter.setOnClickListener { searchView?.setQuery("", true) }
        setTitleAndNothingText()

        // default sort ...
        sort_column = Db.Marker.createTime
        sort_columnId = R.id.menuSortCreateTime

        // .. but probably there is a stored preferences about the last sort used
        when (Preferences.getString(Prefkey.marker_list_sort_column)) {
            Db.Marker.createTime -> {
                sort_column = Db.Marker.createTime
                sort_columnId = R.id.menuSortCreateTime
            }
            Db.Marker.modifyTime -> {
                sort_column = Db.Marker.modifyTime
                sort_columnId = R.id.menuSortModifyTime
            }
            Db.Marker.ari -> {
                sort_column = Db.Marker.ari
                sort_columnId = R.id.menuSortAri
            }
            Db.Marker.caption -> {
                sort_column = Db.Marker.caption
                sort_columnId = R.id.menuSortCaption
            }
        }

        sort_ascending = Preferences.getBoolean(Prefkey.marker_list_sort_ascending, false)

        adapter = MarkerListAdapter()
        lv.adapter = adapter
        lv.cacheColorHint = S.applied().backgroundColor
        lv.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE
        lv.onItemClickListener = lv_itemClick
        lv.onItemLongClickListener = lv_itemLongClick
        lv.emptyView = empty

        App.getLbm().registerReceiver(br, IntentFilter(ACTION_RELOAD))
    }

    override fun onDestroy() {
        super.onDestroy()
        App.getLbm().unregisterReceiver(br)
    }

    private val br = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_RELOAD == intent.action) {
                loadAndFilter()
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // apply background color, and clear window background to prevent overdraw
        window.setBackgroundDrawableResource(android.R.color.transparent)
        root.setBackgroundColor(S.applied().backgroundColor)

        tEmpty.setTextColor(S.applied().fontColor)
        loadAndFilter()
    }

    fun loadAndFilter() {
        allMarkers = S.getDb().listMarkers(filter_kind, filter_labelId, sort_column, sort_ascending)
        filter.submit(currentlyUsedFilter)
    }

    fun setTitleAndNothingText() {
        var title: String? = null
        var nothingText: String? = null

        // set title based on filter
        when (filter_kind) {
            Marker.Kind.note -> {
                title = getString(R.string.bmcat_notes)
                nothingText = getString(R.string.bl_no_notes_written_yet)
            }

            Marker.Kind.highlight -> {
                title = getString(R.string.bmcat_highlights)
                nothingText = getString(R.string.bl_no_highlighted_verses)
            }

            Marker.Kind.bookmark -> {
                if (filter_labelId == 0L) {
                    title = getString(R.string.bmcat_all_bookmarks)
                    nothingText = getString(R.string.belum_ada_pembatas_buku)
                } else if (filter_labelId == LABELID_noLabel.toLong()) {
                    title = getString(R.string.bmcat_unlabeled_bookmarks)
                    nothingText = getString(R.string.bl_there_are_no_bookmarks_without_any_labels)
                } else {
                    val label = S.getDb().getLabelById(filter_labelId)
                    if (label != null) {
                        title = label.title
                        nothingText = getString(R.string.bl_there_are_no_bookmarks_with_the_label_label, label.title)
                    }
                }
            }
        }

        // if we're using text filter (as opposed to kind filter), we use a different nothingText
        if (currentlyUsedFilter != null) {
            nothingText = getString(R.string.bl_no_items_match_the_filter_above)
            bClearFilter.visibility = View.VISIBLE
        } else {
            bClearFilter.visibility = View.GONE
        }
        if (title != null) {
            setTitle(title)
            tEmpty.text = nothingText
        } else {
            finish() // shouldn't happen
        }
    }

    data class FilterResult(
        val query: String?,
        val needFilter: Boolean,
        val filteredMarkers: List<Marker>,
        val rt: ReadyTokens?,
    )

    private val filter = object : Debouncer<String?, FilterResult>(200) {
        override fun process(payload: String?): FilterResult {
            val query = payload?.trim()
            val needFilter = if (query.isNullOrEmpty()) {
                false
            } else {
                QueryTokenizer.tokenize(query).isNotEmpty()
            }

            val tokens = if (query.isNullOrEmpty()) {
                emptyArray()
            } else {
                QueryTokenizer.tokenize(query)
            }

            val rt = if (tokens.isEmpty()) null else ReadyTokens(tokens)
            val filteredMarkers = filterEngine(version, allMarkers, filter_kind, rt)

            return FilterResult(
                query = query,
                needFilter = needFilter,
                filteredMarkers = filteredMarkers,
                rt = rt,
            )
        }

        override fun onResult(result: FilterResult) {
            currentlyUsedFilter = if (result.needFilter) {
                result.query
            } else {
                null
            }
            setTitleAndNothingText()
            adapter.setData(result.filteredMarkers, result.rt)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_marker_list, menu)

        searchView = (menu.findItem(R.id.menuSearch).actionView as SearchView).apply {
            queryHint = getString(R.string.bl_filter_by_some_keywords)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextChange(newText: String): Boolean {
                    filter.submit(newText)
                    return true
                }

                override fun onQueryTextSubmit(query: String): Boolean {
                    filter.submit(query)
                    return true
                }
            })
        }

        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQCODE_edit_note && resultCode == RESULT_OK) {
            loadAndFilter()
            App.getLbm().sendBroadcast(Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED))
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val menuSortCaption = menu.findItem(R.id.menuSortCaption)
        when (filter_kind) {
            Marker.Kind.bookmark -> {
                menuSortCaption.isVisible = true
                menuSortCaption.setTitle(R.string.menuSortCaption)
            }
            Marker.Kind.highlight -> {
                menuSortCaption.isVisible = true
                menuSortCaption.setTitle(R.string.menuSortCaption_color)
            }
            else -> {
                menuSortCaption.isVisible = false
            }
        }
        checkSortMenuItem(menu, sort_columnId, R.id.menuSortAri)
        checkSortMenuItem(menu, sort_columnId, R.id.menuSortCaption)
        checkSortMenuItem(menu, sort_columnId, R.id.menuSortCreateTime)
        checkSortMenuItem(menu, sort_columnId, R.id.menuSortModifyTime)
        return true
    }

    private fun checkSortMenuItem(menu: Menu, checkThis: Int, whenThis: Int) {
        if (checkThis == whenThis) {
            val item = menu.findItem(whenThis)
            item?.isChecked = true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (val itemId = item.itemId) {
            R.id.menuSortAri -> {
                sort(Db.Marker.ari, true, itemId)
                return true
            }
            R.id.menuSortCaption -> {
                sort(Db.Marker.caption, true, itemId)
                return true
            }
            R.id.menuSortCreateTime -> {
                sort(Db.Marker.createTime, false, itemId)
                return true
            }
            R.id.menuSortModifyTime -> {
                sort(Db.Marker.modifyTime, false, itemId)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    fun sort(column: String, ascending: Boolean, columnId: Int) {
        // store for next time use
        Preferences.setString(Prefkey.marker_list_sort_column, column)
        Preferences.setBoolean(Prefkey.marker_list_sort_ascending, ascending)

        searchView?.setQuery("", true)
        currentlyUsedFilter = null
        setTitleAndNothingText()

        sort_column = column
        sort_ascending = ascending
        sort_columnId = columnId

        loadAndFilter()
        invalidateOptionsMenu()
    }

    private val lv_itemClick = OnItemClickListener { _, _, position, _ ->
        if (actionMode != null) {
            // By default setItemChecked will be called when action mode is on.
            // We just need to invalidate the view and the selected verse count.
            onCheckedItemsChanged()
        } else {
            val marker = adapter.getItem(position)
            startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(marker.ari, marker.verseCount))

            // Because we are in CHOICE_MODE_MULTIPLE, this verse is automatically marked as checked.
            // so we have to manually uncheck this.
            uncheckAllItems()
        }
    }

    private fun onCheckedItemsChanged() {
        adapter.notifyDataSetChanged()

        val actionMode = actionMode
        if (actionMode != null) {
            if (lv.checkedItemCount == 0) {
                actionMode.finish()
            } else {
                actionMode.invalidate()
            }
        }
    }

    private fun uncheckAllItems() {
        val checkeds = lv.checkedItemPositions
        for (i in checkeds.size() - 1 downTo 0) {
            if (checkeds.valueAt(i)) {
                lv.setItemChecked(checkeds.keyAt(i), false)
            }
        }
        onCheckedItemsChanged()
    }

    private val lv_itemLongClick = OnItemLongClickListener { _, _, position, _ ->
        if (actionMode == null) {
            actionMode = startSupportActionMode(object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    menuInflater.inflate(R.menu.context_marker_list, menu)
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                    val checked_count = lv.checkedItemCount

                    if (checked_count == 1) {
                        mode.setTitle(R.string.bl_selection_one_selected)
                    } else {
                        mode.title = TextUtils.expandTemplate(getText(R.string.bl_selection_many_selected), "$checked_count")
                    }

                    menu.findItem(R.id.menuEdit).isVisible = checked_count == 1
                    menu.findItem(R.id.menuDelete).isVisible = checked_count == 1

                    return true
                }

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    val singlePosition = fun(): Int? {
                        val positions = lv.checkedItemPositions ?: return null
                        if (lv.checkedItemCount != 1) return null

                        for (i in 0 until positions.size) {
                            if (positions.valueAt(i)) {
                                return positions.keyAt(i)
                            }
                        }
                        return null
                    }()

                    when (item.itemId) {
                        R.id.menuSelectAll -> {
                            for (i in 0 until adapter.count) {
                                lv.setItemChecked(i, true)
                            }
                            onCheckedItemsChanged()
                        }

                        R.id.menuCopy -> {
                            val markers = adapter.filteredMarkers
                            val checkeds = lv.checkedItemPositions
                            val sb = buildString {
                                for (i in 0 until checkeds.size) {
                                    if (!checkeds.valueAt(i)) continue

                                    dumpMarker(markers[checkeds.keyAt(i)])
                                    appendLine()
                                }
                            }

                            ClipboardUtil.copyToClipboard(sb)
                            Snackbar.make(root, R.string.bl_copied_toast, Snackbar.LENGTH_SHORT).show()
                            mode.finish()
                        }

                        R.id.menuEdit -> {
                            if (singlePosition != null) {
                                val marker = adapter.getItem(singlePosition)

                                when (filter_kind) {
                                    Marker.Kind.bookmark -> {
                                        TypeBookmarkDialog.EditExisting(this@MarkerListActivity, marker._id).apply {
                                            setListener {
                                                loadAndFilter()
                                                App.getLbm().sendBroadcast(Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED))
                                            }
                                        }.show()
                                    }

                                    Marker.Kind.note -> {
                                        startActivityForResult(NoteActivity.createEditExistingIntent(marker._id), REQCODE_edit_note)
                                    }

                                    Marker.Kind.highlight -> {
                                        val ari = marker.ari
                                        val info = Highlights.decode(marker.caption)
                                        val reference = version.referenceWithVerseCount(ari, marker.verseCount)
                                        val rawVerseText = version.loadVerseText(ari)
                                        val ftr = FormattedTextResult()
                                        if (rawVerseText != null) {
                                            VerseRendererHelper.render(
                                                ari = ari,
                                                text = rawVerseText,
                                                verseNumberText = "",
                                                ftr = ftr,
                                            )
                                        } else {
                                            ftr.result = "" // verse not available
                                        }

                                        TypeHighlightDialog(this@MarkerListActivity, ari, {
                                            loadAndFilter()
                                            App.getLbm().sendBroadcast(Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED))
                                        }, info.colorRgb, info, reference, ftr.result)
                                    }
                                }
                            }
                        }

                        R.id.menuDelete -> {
                            if (singlePosition != null) {
                                val marker = adapter.getItem(singlePosition)

                                // whatever the kind is, the way to delete is the same
                                S.getDb().deleteMarkerById(marker._id)
                                mode.finish()
                                loadAndFilter()
                                App.getLbm().sendBroadcast(Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED))
                            }
                        }
                    }

                    return true
                }

                override fun onDestroyActionMode(mode: ActionMode) {
                    uncheckAllItems()
                    actionMode = null
                }
            })
        }

        val old = lv.isItemChecked(position)
        lv.setItemChecked(position, !old)
        onCheckedItemsChanged()

        true
    }

    private fun StringBuilder.dumpMarker(marker: Marker) {
        val reference = version.referenceWithVerseCount(marker.ari, marker.verseCount)
        val rawVerseText = version.loadVerseText(marker.ari)
        val verseText = if (rawVerseText == null) {
            getString(R.string.generic_verse_not_available_in_this_version)
        } else {
            FormattedVerseText.removeSpecialCodes(rawVerseText)
        }

        val kind = marker.kind ?: return
        when (kind) {
            Marker.Kind.bookmark -> {
                appendLine(marker.caption)
                // the caption defaults to the same as reference, so prevent double-mention
                if (marker.caption != reference) {
                    appendLine(reference)
                }
                appendLine(verseText)
                if (marker.verseCount > 1) {
                    appendLine("...")
                }

                val labels = S.getDb().listLabelsByMarker(marker)
                if (labels.isNotEmpty()) {
                    appendLine(labels.joinToString { it.title })
                }
            }

            Marker.Kind.note -> {
                appendLine(reference)
                appendLine(marker.caption)
            }

            Marker.Kind.highlight -> {
                appendLine(reference)
                appendLine(verseText)
                if (marker.verseCount > 1) {
                    appendLine("...")
                }
            }
        }
    }

    inner class MarkerListAdapter : EasyAdapter() {
        var filteredMarkers = emptyList<Marker>()
        private var rt: ReadyTokens? = null

        override fun getItem(position: Int): Marker {
            return filteredMarkers[position]
        }

        override fun newView(position: Int, parent: ViewGroup): View {
            return layoutInflater.inflate(R.layout.item_marker, parent, false)
        }

        override fun bindView(view: View, position: Int, parent: ViewGroup) {
            val lDate = view.findViewById<TextView>(R.id.lDate)
            val lCaption = view.findViewById<TextView>(R.id.lCaption)
            val lSnippet = view.findViewById<TextView>(R.id.lSnippet)
            val panelLabels = view.findViewById<FlowLayout>(R.id.panelLabels)
            val marker = getItem(position)

            lDate.text = run {
                val createTimeDisplay = Sqlitil.toLocaleDateMedium(marker.createTime)
                if (marker.createTime == marker.modifyTime) {
                    createTimeDisplay
                } else {
                    val modifyTimeDisplay = Sqlitil.toLocaleDateMedium(marker.modifyTime)
                    if (createTimeDisplay == modifyTimeDisplay) {
                        // show time for modifyTime when createTime and modifyTime is on the same day
                        getString(R.string.create_edited_modified_time, createTimeDisplay, Sqlitil.toLocaleTime(marker.modifyTime))
                    } else {
                        getString(R.string.create_edited_modified_time, createTimeDisplay, modifyTimeDisplay)
                    }
                }
            }
            Appearances.applyMarkerDateTextAppearance(lDate, textSizeMult)

            val ari = marker.ari
            val rawVerseText = version.loadVerseText(ari)
            val verseText: CharSequence = if (rawVerseText == null) {
                getString(R.string.generic_verse_not_available_in_this_version)
            } else {
                val ftr = FormattedTextResult()
                VerseRenderer.render(null, null, false, ari, rawVerseText, "", null, false, null, ftr)
                ftr.result
            }

            val reference = version.referenceWithVerseCount(ari, marker.verseCount)
            val caption = marker.caption
            val hiliteColor = TextColorUtil.getSearchKeywordByBrightness(S.applied().backgroundBrightness)

            when (filter_kind) {
                Marker.Kind.bookmark -> {
                    lCaption.text = if (currentlyUsedFilter != null) SearchEngine.hilite(caption, rt, hiliteColor) else caption
                    Appearances.applyMarkerTitleTextAppearance(lCaption, textSizeMult)

                    val snippet = if (currentlyUsedFilter != null) SearchEngine.hilite(verseText, rt, hiliteColor) else verseText
                    Appearances.applyMarkerSnippetContentAndAppearance(lSnippet, reference, snippet, textSizeMult)

                    val labels = S.getDb().listLabelsByMarker(marker)
                    if (labels.isNotEmpty()) {
                        panelLabels.visibility = View.VISIBLE
                        panelLabels.removeAllViews()
                        for (label in labels) {
                            panelLabels.addView(getLabelView(layoutInflater, panelLabels, label))
                        }
                    } else {
                        panelLabels.visibility = View.GONE
                    }
                }

                Marker.Kind.note -> {
                    lCaption.text = reference
                    Appearances.applyMarkerTitleTextAppearance(lCaption, textSizeMult)

                    lSnippet.text = if (currentlyUsedFilter != null) SearchEngine.hilite(caption, rt, hiliteColor) else caption
                    Appearances.applyTextAppearance(lSnippet, textSizeMult)
                }

                Marker.Kind.highlight -> {
                    lCaption.text = reference
                    Appearances.applyMarkerTitleTextAppearance(lCaption, textSizeMult)

                    val snippet = if (currentlyUsedFilter != null) SearchEngine.hilite(verseText, rt, hiliteColor) else SpannableStringBuilder(verseText)
                    val info = Highlights.decode(caption)
                    if (info != null) {
                        val span = BackgroundColorSpan(Highlights.alphaMix(info.colorRgb))
                        if (info.shouldRenderAsPartialForVerseText(verseText)) {
                            snippet.setSpan(span, info.partial.startOffset, info.partial.endOffset, 0)
                        } else {
                            snippet.setSpan(span, 0, snippet.length, 0)
                        }
                    }
                    lSnippet.text = snippet
                    Appearances.applyTextAppearance(lSnippet, textSizeMult)
                }
            }

            val checked = lv.isItemChecked(position)

            if (checked) {
                val colorRgb = Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default)
                val checkedBgColor = ColorUtils.setAlphaComponent(colorRgb, 0xa0)
                val checkedTextColor = TextColorUtil.getForCheckedVerse(checkedBgColor)
                view.setBackgroundColor(checkedBgColor)
                lDate.setTextColor(checkedTextColor)
                lSnippet.setTextColor(checkedTextColor)
                lCaption.setTextColor(checkedTextColor)
            } else {
                view.setBackgroundColor(0x0)
            }
        }

        override fun getCount() = filteredMarkers.size

        fun setData(filteredMarkers: List<Marker>, rt: ReadyTokens?) {
            this.filteredMarkers = filteredMarkers
            this.rt = rt

            // set up empty view to make sure it does not show loading progress again
            tEmpty.visibility = View.VISIBLE
            progress.visibility = View.GONE
            notifyDataSetChanged()
        }
    }

    companion object {
        const val LABELID_noLabel = -1

        /**
         * Action to broadcast when marker list needs to be reloaded due to some background changes
         */
        const val ACTION_RELOAD = "yuku.alkitab.base.ac.MarkerListActivity.action.RELOAD"

        @JvmStatic
        fun createIntent(context: Context, filter_kind: Marker.Kind, filter_labelId: Long): Intent {
            val res = Intent(context, MarkerListActivity::class.java)
            res.putExtra(EXTRA_filter_kind, filter_kind.code)
            res.putExtra(EXTRA_filter_labelId, filter_labelId)
            return res
        }

        fun getLabelView(inflater: LayoutInflater, panelLabels: FlowLayout, label: Label): View {
            return inflater.inflate(R.layout.label, panelLabels, false).apply {
                layoutParams = panelLabels.generateDefaultLayoutParams()
                val lCaption = findViewById<TextView>(R.id.lCaption)
                lCaption.text = label.title
                LabelColorUtil.apply(label, lCaption)
            }
        }

        /**
         * The real work of filtering happens here.
         *
         * @param rt Tokens have to be already lowercased.
         */
        fun filterEngine(version: Version, allMarkers: List<Marker>, filter_kind: Marker.Kind, rt: ReadyTokens?): List<Marker> {
            val res = mutableListOf<Marker>()
            if (rt == null) {
                res.addAll(allMarkers)
                return res
            }

            for (marker in allMarkers) {
                if (filter_kind != Marker.Kind.highlight) { // "caption" in highlights only stores color information, so it's useless to check
                    val caption_lc = marker.caption.toLowerCase(Locale.getDefault())
                    if (SearchEngine.satisfiesTokens(caption_lc, rt)) {
                        res.add(marker)
                        continue
                    }
                }

                // try the verse text!
                val verseText = version.loadVerseText(marker.ari)
                if (verseText != null) { // this can be null! so beware.
                    val verseText_lc = verseText.toLowerCase(Locale.getDefault())
                    if (SearchEngine.satisfiesTokens(verseText_lc, rt)) {
                        res.add(marker)
                    }
                }
            }
            return res
        }
    }
}