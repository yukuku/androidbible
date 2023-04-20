package yuku.alkitab.versionmanager

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.io.IOException
import yuku.alkitab.base.App
import yuku.alkitab.base.S
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.model.MVersionDb
import yuku.alkitab.base.pdbconvert.ConvertOptionsDialog
import yuku.alkitab.base.pdbconvert.ConvertPdbToYes2
import yuku.alkitab.base.storage.YesReaderFactory
import yuku.alkitab.base.sv.VersionConfigUpdaterService
import yuku.alkitab.base.util.AddonManager
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Background
import yuku.alkitab.base.util.DownloadMapper
import yuku.alkitab.base.util.Foreground
import yuku.alkitab.base.widget.MaterialDialogProgressHelper.progress
import yuku.alkitab.debug.R
import yuku.alkitab.tracking.Tracker

private const val TAG = "VersionsActivity"

class VersionsActivity : BaseActivity() {
    private lateinit var sectionsPagerAdapter: SectionsPagerAdapter
    private lateinit var viewPager: ViewPager
    private lateinit var tablayout: TabLayout

    var query_text: String? = null

    private val getContentRequest = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        importFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_versions)
        setTitle(R.string.kelola_versi)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        sectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager)

        // Set up the ViewPager with the sections adapter.
        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = sectionsPagerAdapter

        tablayout = findViewById(R.id.tablayout)
        tablayout.tabMode = TabLayout.MODE_SCROLLABLE
        tablayout.setupWithViewPager(viewPager)

        if (savedInstanceState == null) {
            processIntent(intent)
        }

        // try to auto-update version list
        VersionConfigUpdaterService.checkUpdate(true)
    }

    private fun processIntent(intent: Intent) {
        dumpIntent(intent, "VersionsActivity#onCreate")
        checkAndProcessOpenFileIntent(intent)
    }

    private fun checkAndProcessOpenFileIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data
            ?: run {
                MaterialDialog(this).show {
                    title(R.string.ed_error_encountered)
                    message(text = "Intent data is null.")
                    positiveButton(R.string.ok)
                }
                return
            }
        importFromUri(uri)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_versions, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val menuSearch = menu.findItem(R.id.menuSearch)
        menuSearch.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                setOthersVisible(item, false)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                setOthersVisible(item, true)
                return true
            }

            private fun setOthersVisible(except: MenuItem, visible: Boolean) {
                var i = 0
                val len = menu.size()
                while (i < len) {
                    val other = menu.getItem(i)
                    if (except !== other) {
                        other.isVisible = visible
                    }
                    i++
                }
            }
        })

        val searchView = menuSearch.actionView as SearchView
        searchView.setOnQueryTextListener(searchView_change)

        return true
    }

    private val searchView_change: SearchView.OnQueryTextListener = object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String): Boolean {
            query_text = query
            startSearch()
            return false
        }

        override fun onQueryTextChange(newText: String): Boolean {
            query_text = newText
            startSearch()
            return false
        }
    }

    fun startSearch() {
        // broadcast to all fragments that we have a new query_text
        for (tag in listOf(makeFragmentName(R.id.viewPager, 0), makeFragmentName(R.id.viewPager, 1))) {
            val f = supportFragmentManager.findFragmentByTag(tag)
            if (f is QueryTextReceiver) {
                f.setQueryText(query_text)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menuAddFromLocal -> {
                getContentRequest.launch("*/*")
                true
            }

            R.id.menuAddFromUrl -> {
                openUrlInputDialog(null)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    inner class SectionsPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getItem(position: Int) = VersionListFragment.newInstance(position == 1, query_text)

        override fun getCount() = 2

        override fun getPageTitle(position: Int): CharSequence? {
            when (position) {
                0 -> return getString(R.string.ed_section_all)
                1 -> return getString(R.string.ed_section_downloaded)
            }
            return null
        }
    }

    /**
     * We have a local persistent yes file, register that to the DB.
     */
    private fun registerLocalYesFile(localYesFile: File) {
        try {
            val reader = YesReaderFactory.createYesReader(localYesFile.absolutePath)
                ?: throw IOException("Local file $localYesFile is not a valid YES file.")

            var maxOrdering = S.db.versionMaxOrdering
            if (maxOrdering == 0) maxOrdering = MVersionDb.DEFAULT_ORDERING_START

            val mvDb = MVersionDb().apply {
                locale = reader.locale
                shortName = reader.shortName
                longName = reader.longName
                description = reader.description
                filename = localYesFile.absolutePath
                ordering = maxOrdering + 1
                preset_name = null
            }

            S.db.insertOrUpdateVersionWithActive(mvDb, true)
            MVersionDb.clearVersionImplCache()

            App.getLbm().sendBroadcast(Intent(VersionListFragment.ACTION_RELOAD))
        } catch (e: Exception) {
            MaterialDialog(this).show {
                title(R.string.ed_error_encountered)
                message(text = "${e.javaClass.simpleName}: ${e.message}")
                positiveButton(R.string.ok)
            }
        }

        // we are trying to open a file, so let's go to the DOWNLOADED tab, as it is more relevant.
        viewPager.currentItem = 1
    }

    private fun openUrlInputDialog(prefill: String?) {
        MaterialDialog(this)
            .title(R.string.version_download_add_from_url_prompt_yes_only)
            .input(prefill = prefill) { _: MaterialDialog?, input: CharSequence ->
                val url = input.toString().trim()
                if (url.isEmpty()) return@input

                val uri = Uri.parse(url)
                val scheme = uri.scheme
                if ("http" != scheme && "https" != scheme) {
                    MaterialDialog(this@VersionsActivity).show {
                        message(R.string.version_download_invalid_url)
                        positiveButton(R.string.ok) { openUrlInputDialog(url) }
                    }
                    return@input
                }

                // guess destination filename
                val last = uri.lastPathSegment ?: return@input
                if (last.isEmpty() || !last.endsWith(".yes", ignoreCase = true)) {
                    MaterialDialog(this@VersionsActivity).show {
                        message(R.string.version_download_not_yes)
                        positiveButton(R.string.ok)
                    }
                    return@input
                }

                val downloadKey = "version:url:$url"
                val status = DownloadMapper.instance.getStatus(downloadKey)
                if (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING) {
                    // it's downloading!
                    return@input
                }

                val attrs = mapOf(
                    "download_type" to "url",
                    "filename_last_segment" to last,
                )
                DownloadMapper.instance.enqueue(downloadKey, url, last, attrs)

                Toast.makeText(this@VersionsActivity, R.string.mulai_mengunduh, Toast.LENGTH_SHORT).show()
            }
            .positiveButton(R.string.ok)
            .show()
    }

    private fun importFromUri(uri: Uri) {
        val importer = VersionFileImporter(this)

        try {
            when (val result = importer.importFromUri(uri)) {
                is VersionFileImporter.Result.YesFileIsAvailableLocally -> {
                    registerLocalYesFile(result.localYesFile)
                }

                is VersionFileImporter.Result.ShouldImportPdb -> {
                    showPdbConvertDialog(result.cacheFile, result.yesName)
                }
            }
        } catch (e: Exception) {
            MaterialDialog(this).show {
                title(R.string.ed_error_encountered)
                message(text = "${e.javaClass.simpleName}: ${e.message}")
                positiveButton(R.string.ok)
            }
        }
    }

    private fun showPdbConvertDialog(cacheFile: File, yesName: String) {
        fun showPdbReadErrorDialog(exception: Throwable) {
            val message = when (exception) {
                is ConvertOptionsDialog.PdbKnownErrorException -> exception.message.orEmpty()
                else -> "${getString(R.string.ed_details)}(${exception.javaClass.name}): ${exception.message}\n${exception.stackTraceToString()}"
            }

            MaterialDialog(this@VersionsActivity).show {
                title(R.string.ed_error_reading_pdb_file)
                message(text = message)
                positiveButton(R.string.ok)
            }
        }

        val callback = object : ConvertOptionsDialog.ConvertOptionsCallback {
            fun showResult(yesFile: File, exception: Throwable?, wronglyConvertedBookNames: List<String>?) {
                if (exception != null) {
                    Tracker.trackEvent("versions_convert_pdb_error")
                    showPdbReadErrorDialog(exception)
                    return
                }

                // success.
                Tracker.trackEvent("versions_convert_pdb_success")
                registerLocalYesFile(yesFile)

                if (!wronglyConvertedBookNames.isNullOrEmpty()) {
                    val msg = buildString {
                        append(getString(R.string.ed_the_following_books_from_the_pdb_file_are_not_recognized))
                        for (s in wronglyConvertedBookNames) {
                            append("- $s\n")
                        }
                    }

                    MaterialDialog(this@VersionsActivity).show {
                        message(text = msg)
                        positiveButton(R.string.ok)
                    }
                }

                // we are trying to open a file, so let's go to the DOWNLOADED tab, as it is more relevant.
                viewPager.currentItem = 1
            }

            override fun onPdbReadError(e: Throwable) {
                showPdbReadErrorDialog(e)
            }

            override fun onOkYes2(params: ConvertPdbToYes2.ConvertParams) {
                val yesFile = AddonManager.getWritableVersionFile(yesName)
                val pd = MaterialDialog(this@VersionsActivity).show {
                    message(R.string.ed_reading_pdb_file)
                    cancelable(false)
                    progress(true, 0)
                }

                fun onProgressUpdate(at: Int?, message: String?) = Foreground.run {
                    if (at == null) {
                        pd.message(R.string.ed_finished)
                    } else {
                        pd.message(text = "($at) $message...")
                    }
                }

                fun onPostExecute(result: ConvertPdbToYes2.ConvertResult) = Foreground.run {
                    pd.dismiss()
                    showResult(yesFile, result.exception, result.wronglyConvertedBookNames)
                }

                Background.run {
                    val converter = ConvertPdbToYes2()
                    converter.setConvertProgressListener(object : ConvertPdbToYes2.ConvertProgressListener {
                        override fun onProgress(at: Int, message: String) {
                            AppLog.d(TAG, "Progress $at: $message")
                            onProgressUpdate(at, message)
                        }

                        override fun onFinish() {
                            AppLog.d(TAG, "Finish")
                            onProgressUpdate(null, null)
                        }
                    })

                    val result = converter.convert(App.context, cacheFile.absolutePath, yesFile, params)
                    onPostExecute(result)
                }
            }
        }

        Tracker.trackEvent("versions_convert_pdb_start")
        ConvertOptionsDialog(this, cacheFile.absolutePath, callback).show()
    }

    companion object {
        @JvmStatic
        fun createIntent(): Intent {
            return Intent(App.context, VersionsActivity::class.java)
        }

        // taken from FragmentPagerAdapter
        private fun makeFragmentName(viewId: Int, index: Int): String {
            return "android:switcher:$viewId:$index"
        }
    }
}

internal interface QueryTextReceiver {
    fun setQueryText(query_text: String?)
}
