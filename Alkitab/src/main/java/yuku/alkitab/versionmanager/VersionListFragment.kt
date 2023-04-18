package yuku.alkitab.versionmanager

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.DynamicDrawableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AnyThread
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.mobeta.android.dslv.DragSortController
import com.mobeta.android.dslv.DragSortListView
import java.io.File
import java.util.Locale
import java.util.regex.Matcher
import yuku.afw.widget.EasyAdapter
import yuku.alkitab.base.App
import yuku.alkitab.base.S
import yuku.alkitab.base.config.VersionConfig
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.model.MVersionDb
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.model.MVersionPreset
import yuku.alkitab.base.sv.VersionConfigUpdaterService
import yuku.alkitab.base.util.AddonManager
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Background
import yuku.alkitab.base.util.DownloadMapper
import yuku.alkitab.base.util.Foreground
import yuku.alkitab.base.util.QueryTokenizer
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R

private const val TAG = "VersionListFragment"

private const val ARG_DOWNLOADED_ONLY = "downloaded_only"
private const val ARG_INITIAL_QUERY_TEXT = "initial_query_text"

class VersionListFragment : Fragment(), QueryTextReceiver {
    private lateinit var inflater: LayoutInflater
    private lateinit var lsVersions: DragSortListView
    private lateinit var adapter: VersionAdapter
    private var swiper: SwipeRefreshLayout? = null

    var downloadedOnly = false
    var query_text: String? = null

    private val br = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_RELOAD == action) {
                Background.run { adapter.reload() }
            } else if (ACTION_UPDATE_REFRESHING_STATUS == action) {
                val refreshing = intent.getBooleanExtra(EXTRA_refreshing, false)
                swiper?.isRefreshing = refreshing
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        App.getLbm().registerReceiver(br, IntentFilter(ACTION_RELOAD))
        App.getLbm().registerReceiver(br, IntentFilter(ACTION_UPDATE_REFRESHING_STATUS))

        downloadedOnly = requireArguments().getBoolean(ARG_DOWNLOADED_ONLY)
        query_text = requireArguments().getString(ARG_INITIAL_QUERY_TEXT)
    }

    override fun onDestroy() {
        super.onDestroy()

        App.getLbm().unregisterReceiver(br)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.inflater = inflater

        this.adapter = VersionAdapter()

        val rootView = inflater.inflate(if (downloadedOnly) R.layout.fragment_versions_downloaded else R.layout.fragment_versions_all, container, false)
        lsVersions = rootView.findViewById(R.id.lsVersions)
        lsVersions.adapter = adapter

        if (downloadedOnly) {
            val c = VersionOrderingController(lsVersions)
            lsVersions.setFloatViewManager(c)
            lsVersions.setOnTouchListener(c)
        }

        // Can be null, if the layout used is fragment_versions_downloaded.
        swiper = rootView.findViewById<SwipeRefreshLayout?>(R.id.swiper)?.apply {
            val accentColor = ResourcesCompat.getColor(resources, R.color.accent, null)
            setColorSchemeColors(accentColor, -0x343435)
            setOnRefreshListener(swiper_refresh)
        }

        return rootView
    }

    private val swiper_refresh = SwipeRefreshLayout.OnRefreshListener { VersionConfigUpdaterService.checkUpdate(false) }

    private val cache_displayLanguage = mutableMapOf<String, String?>()

    private fun getDisplayLanguage(locale: String?): String {
        if (locale.isNullOrEmpty()) return "not specified"

        var display = cache_displayLanguage[locale]
        if (display != null) return display

        display = Locale(locale).displayLanguage
        if (display == locale) {
            // try asking version config locale display
            display = VersionConfig.get().locale_display[locale]
        }

        if (display == null) {
            display = locale // can't be null now
        }

        cache_displayLanguage[locale] = display
        return display
    }

    private fun getGroupOrderDisplay(group_order: Int): String? {
        val map = VersionConfig.get().group_order_display ?: return null
        return map[group_order.toString()]
    }

    override fun setQueryText(query_text: String?) {
        this.query_text = query_text
        Background.run { adapter.reload() } // do not broadcast, since the query only changes this fragment
    }

    class Item(var mv: MVersion)

    fun itemCheckboxClick(item: Item, itemView: View) {
        when (val mv = item.mv) {
            is MVersionPreset -> clickOnPresetVersion(itemView.findViewById(R.id.cActive), mv)
            is MVersionDb -> clickOnDbVersion(itemView.findViewById(R.id.cActive), mv)
        }
        App.getLbm().sendBroadcast(Intent(ACTION_RELOAD))
    }

    fun itemNameClick(item: Item) {
        val details = SpannableStringBuilder()

        fun addDetail(key: String, value: String?) {
            val sb_len = details.length
            details.append(key.uppercase(Locale.getDefault())).append(": ")
            details.setSpan(ForegroundColorSpan(-0x555556), sb_len, details.length, 0)
            details.setSpan(RelativeSizeSpan(0.7f), sb_len, details.length, 0)
            details.setSpan(StyleSpan(Typeface.BOLD), sb_len, details.length, 0)
            details.append(value)
            details.append("\n")
        }

        val mv = item.mv
        when (mv) {
            is MVersionInternal -> addDetail(getString(R.string.ed_type_key), getString(R.string.ed_type_internal))
            is MVersionPreset -> addDetail(getString(R.string.ed_type_key), getString(R.string.ed_type_preset))
            is MVersionDb -> addDetail(getString(R.string.ed_type_key), getString(R.string.ed_type_db))
        }

        if (mv.locale != null) addDetail(getString(R.string.ed_locale_locale), mv.locale)
        if (mv.shortName != null) addDetail(getString(R.string.ed_shortName_shortName), mv.shortName)
        addDetail(getString(R.string.ed_title_title), mv.longName)

        if (mv is MVersionPreset) {
            addDetail(getString(R.string.ed_default_filename_file), mv.preset_name)
            addDetail(getString(R.string.ed_download_url_url), mv.download_url)
        }

        if (mv is MVersionDb) {
            addDetail(getString(R.string.ed_stored_in_file), mv.filename)
        }

        if (mv.description != null) details.append('\n').append(mv.description).append('\n')

        val b = MaterialDialog(requireActivity())
        var button_count = 0

        // can we update?
        if (mv is MVersionDb && hasUpdateAvailable(mv)) {
            button_count++
            b.positiveButton(R.string.ed_update_button) { startDownload(VersionConfig.get().getPreset(mv.preset_name)) }
            details.append("\n")
            val details_len = details.length
            details.append("  ")
            details.setSpan(ImageSpan(App.context, R.drawable.ic_version_update, DynamicDrawableSpan.ALIGN_BASELINE), details_len, details_len + 1, 0)
            details.append(getString(R.string.ed_details_update_available))
        }

        // can we share?
        if (mv is MVersionDb && mv.hasDataFile()) {
            button_count++
            b.negativeButton(R.string.version_menu_share) {
                val file = File(mv.filename)
                try {
                    val uri = FileProvider.getUriForFile(requireActivity(), App.context.packageName + ".file_provider", file)
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(activity, "Uri: $uri", Toast.LENGTH_LONG).show()
                    }

                    ShareCompat.IntentBuilder(requireActivity())
                        .setType("application/octet-stream")
                        .addStream(uri)
                        .setChooserTitle(getString(R.string.version_share_title))
                        .startChooser()
                } catch (e: Exception) {
                    MaterialDialog(requireActivity()).show {
                        message(text = "Can't share " + file.absolutePath + ": [" + e.javaClass + "] " + e.message)
                        positiveButton(R.string.ok)
                    }
                }
            }
        }

        // can we delete?
        if (mv is MVersionDb) {
            button_count++
            b.neutralButton(R.string.buang_dari_daftar) {
                val filename = mv.filename
                if (AddonManager.isInSharedStorage(filename)) {
                    MaterialDialog(requireActivity()).show {
                        message(text = getString(R.string.juga_hapus_file_datanya_file, filename))
                        positiveButton(R.string.delete) {
                            S.db.deleteVersion(mv)
                            App.getLbm().sendBroadcast(Intent(ACTION_RELOAD))
                            File(filename).delete()
                        }
                        negativeButton(R.string.no) {
                            S.db.deleteVersion(mv)
                            App.getLbm().sendBroadcast(Intent(ACTION_RELOAD))
                        }
                        neutralButton(R.string.cancel)
                    }
                } else { // just delete the file!
                    S.db.deleteVersion(mv)
                    App.getLbm().sendBroadcast(Intent(ACTION_RELOAD))
                    File(filename).delete()
                }
            }
        }

        // can we download?
        if (mv is MVersionPreset) {
            button_count++
            b.positiveButton(R.string.ed_download_button) { startDownload(mv) }
        }

        // if we have no buttons at all, add a no-op OK
        if (button_count == 0) {
            b.positiveButton(R.string.ok)
        }
        b.title(R.string.ed_version_details)
        b.message(text = details)
        b.show()
    }

    private fun clickOnPresetVersion(cActive: CheckBox, mv: MVersionPreset) {
        if (cActive.isChecked) {
            throw RuntimeException("THIS SHOULD NOT HAPPEN: preset may not have the active checkbox checked.")
        }
        startDownload(mv)
    }

    private fun startDownload(mv: MVersionPreset) {
        val downloadKey = "version:preset_name:" + mv.preset_name
        val status = DownloadMapper.instance.getStatus(downloadKey)
        if (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING) {
            // it's downloading!
            return
        }

        val attrs = mapOf(
            "download_type" to "preset",
            "preset_name" to mv.preset_name,
            "modifyTime" to "${mv.modifyTime}",
        )
        DownloadMapper.instance.enqueue(downloadKey, mv.download_url, mv.longName, attrs)
        App.getLbm().sendBroadcast(Intent(ACTION_RELOAD))
    }

    private fun clickOnDbVersion(cActive: CheckBox, mv: MVersionDb) {
        when {
            cActive.isChecked -> mv.active = false
            mv.hasDataFile() -> mv.active = true
            else -> {
                MaterialDialog(requireActivity()).show {
                    message(R.string.the_file_for_this_version_is_no_longer_available_file, mv.filename)
                    positiveButton(R.string.delete) {
                        S.db.deleteVersion(mv)
                        App.getLbm().sendBroadcast(Intent(ACTION_RELOAD))
                    }
                    negativeButton(R.string.no)
                }
            }
        }
    }

    inner class VersionAdapter internal constructor() : EasyAdapter(), DragSortListView.DropListener {
        private val items = mutableListOf<Item>()

        init {
            reload()
        }

        /**
         * The list of versions are loaded as follows:
         * - Internal version [yuku.alkitab.base.model.MVersionInternal], is always there
         * - Versions stored in database [yuku.alkitab.base.model.MVersionDb] is all loaded
         * - For each non-hidden [yuku.alkitab.base.model.MVersionPreset] defined in [yuku.alkitab.base.config.VersionConfig],
         * check if the [yuku.alkitab.base.model.MVersionPreset.preset_name] corresponds to one of the
         * database version above. If it does, do not add to the resulting list. Otherwise, add it so user can download it.
         *
         * Note: Downloaded preset version will become database version after added.
         */
        @AnyThread
        fun reload() {
            val items = mutableListOf<Item>()
            // internal
            items.add(Item(S.getMVersionInternal()))

            val presetsInDb = mutableMapOf<String, MVersionDb>()

            // db
            for (mv in S.db.listAllVersions()) {
                items.add(Item(mv))
                if (mv.preset_name != null) {
                    presetsInDb[mv.preset_name] = mv
                }
            }

            // presets (only for "all" tab)
            if (!downloadedOnly) {
                for (preset in VersionConfig.get().presets) {
                    // set group_order of db version from preset list
                    if (preset.preset_name in presetsInDb) {
                        presetsInDb.getValue(preset.preset_name).group_order = preset.group_order
                        continue
                    }
                    items.add(Item(preset))
                }
            }

            if (!query_text.isNullOrEmpty()) { // filter items based on query_text
                val matchers = QueryTokenizer.matcherizeTokens(QueryTokenizer.tokenize(query_text))
                for (i in items.indices.reversed()) {
                    val item = items[i]
                    if (!matchMatchers(item.mv, matchers)) {
                        items.removeAt(i)
                    }
                }
            }

            // Sort items. For "all" tab, sort is based on display language. For "downloaded" tab, sort is based on ordering.
            if (!downloadedOnly) {
                items.sortWith { a: Item, b: Item ->
                    // if group_order is defined (not 0), sort by group order
                    val go_a = if (a.mv.group_order == 0) Int.MAX_VALUE else a.mv.group_order
                    val go_b = if (b.mv.group_order == 0) Int.MAX_VALUE else b.mv.group_order
                    if (go_a != go_b) return@sortWith if (go_a < go_b) -1 else 1

                    val locale_a = a.mv.locale
                    val locale_b = b.mv.locale
                    if (locale_a == locale_b) return@sortWith a.mv.longName.compareTo(b.mv.longName, ignoreCase = true)

                    if (locale_a == null) return@sortWith +1
                    if (locale_b == null) return@sortWith -1
                    getDisplayLanguage(locale_a).compareTo(getDisplayLanguage(locale_b), ignoreCase = true)
                }
            } else {
                items.sortWith { a: Item, b: Item -> a.mv.ordering - b.mv.ordering }
                if (BuildConfig.DEBUG) {
                    AppLog.d(TAG, "ordering   type                   versionId")
                    AppLog.d(TAG, "========   ===================    =================")
                    for (item in items) {
                        AppLog.d(TAG, String.format(Locale.US, "%8d   %-20s   %s", item.mv.ordering, item.mv.javaClass.simpleName, item.mv.versionId))
                    }
                }
            }

            Foreground.run {
                this.items.clear()
                this.items.addAll(items)
                notifyDataSetChanged()
            }
        }

        private fun matchMatchers(mv: MVersion, matchers: Array<Matcher>): Boolean {
            // have to match all tokens
            for (m in matchers) {
                if (m.reset(mv.longName).find()) continue
                if (mv.shortName != null && m.reset(mv.shortName).find()) continue
                if (mv.description != null && m.reset(mv.description).find()) continue
                if (mv.locale != null && m.reset(getDisplayLanguage(mv.locale)).find()) continue
                return false
            }
            return true
        }

        override fun getCount() = items.size

        override fun getItem(position: Int) = items[position]

        override fun newView(position: Int, parent: ViewGroup): View {
            return inflater.inflate(R.layout.item_version, parent, false)
        }

        override fun bindView(view: View, position: Int, parent: ViewGroup) {
            val panelRight = view.findViewById<View>(R.id.panelRight)
            val cActive = view.findViewById<CheckBox>(R.id.cActive)
            val progress = view.findViewById<View>(R.id.progress)
            val bLongName = view.findViewById<Button>(R.id.bLongName)
            val header = view.findViewById<View>(R.id.header)
            val tLanguage = view.findViewById<TextView>(R.id.tLanguage)
            val drag_handle = view.findViewById<View>(R.id.drag_handle)
            val item = getItem(position)
            val mv = item.mv
            bLongName.setOnClickListener { itemNameClick(item) }
            panelRight.setOnClickListener { itemCheckboxClick(item, view) }
            cActive.isChecked = mv.active
            bLongName.text = mv.longName

            when (mv) {
                is MVersionInternal -> cActive.isEnabled = false
                is MVersionPreset -> cActive.isEnabled = true
                is MVersionDb -> cActive.isEnabled = true
            }

            val prev = if (position == 0) null else getItem(position - 1).mv
            if (prev == null || prev.group_order != mv.group_order || prev.group_order == 0 && prev.locale != mv.locale) {
                header.visibility = View.VISIBLE
                if (mv.group_order != 0) {
                    val groupName = getGroupOrderDisplay(mv.group_order)
                    tLanguage.text = groupName ?: getDisplayLanguage(mv.locale)
                } else {
                    tLanguage.text = getDisplayLanguage(mv.locale)
                }
            } else {
                header.visibility = View.GONE
            }

            // Update icon
            if (mv is MVersionDb && hasUpdateAvailable(mv)) {
                bLongName.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_version_update, 0, 0, 0)
            } else {
                bLongName.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }

            // downloading or not?
            val downloading = if (mv is MVersionInternal) {
                false
            } else if (mv is MVersionPreset) {
                val downloadKey = "version:preset_name:" + mv.preset_name
                val status = DownloadMapper.instance.getStatus(downloadKey)
                status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING
            } else if (mv is MVersionDb && mv.preset_name != null) { // probably downloading, in case of updating
                val downloadKey = "version:preset_name:" + mv.preset_name
                val status = DownloadMapper.instance.getStatus(downloadKey)
                status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING
            } else {
                false
            }

            if (downloading) {
                cActive.visibility = View.INVISIBLE
                progress.visibility = View.VISIBLE
            } else {
                cActive.visibility = View.VISIBLE
                progress.visibility = View.INVISIBLE
            }

            if (downloadedOnly) {
                drag_handle.visibility = View.VISIBLE
            } else {
                drag_handle.visibility = View.GONE
            }
        }

        override fun drop(from: Int, to: Int) {
            if (from == to) return
            val fromItem = getItem(from)
            val toItem = getItem(to)
            S.db.reorderVersions(fromItem.mv, toItem.mv)
            App.getLbm().sendBroadcast(Intent(ACTION_RELOAD))
        }
    }

    fun hasUpdateAvailable(mvDb: MVersionDb): Boolean {
        if (mvDb.preset_name == null || mvDb.modifyTime == 0) {
            return false
        }
        val available = VersionConfig.get().getModifyTime(mvDb.preset_name)
        return !(available == 0 || available <= mvDb.modifyTime)
    }

    private inner class VersionOrderingController(private val lv: DragSortListView) :
        DragSortController(lv, R.id.drag_handle, ON_DOWN, 0) {

        init {
            isRemoveEnabled = false
        }

        override fun startDragPosition(ev: MotionEvent): Int {
            return super.dragHandleHitPosition(ev)
        }

        override fun onCreateFloatView(position: Int): View {
            val res = adapter.getView(position, null, lv)
            res.setBackgroundColor(0x22ffffff)
            return res
        }

        override fun onDestroyFloatView(floatView: View) {
            // Do not call super and do not remove this override.
            floatView.setBackgroundColor(0)
        }
    }

    companion object {
        @JvmField
        val ACTION_RELOAD = VersionListFragment::class.java.name + ".action.RELOAD"

        @JvmField
        val ACTION_UPDATE_REFRESHING_STATUS = VersionListFragment::class.java.name + ".action.UPDATE_REFRESHING_STATUS"

        const val EXTRA_refreshing = "refreshing"

        fun newInstance(downloadedOnly: Boolean, initial_query_text: String?): VersionListFragment {
            val res = VersionListFragment()
            val args = Bundle()
            args.putBoolean(ARG_DOWNLOADED_ONLY, downloadedOnly)
            args.putString(ARG_INITIAL_QUERY_TEXT, initial_query_text)
            res.arguments = args
            return res
        }
    }
}
