package yuku.alkitab.versionmanager

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.zip.GZIPInputStream
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
import yuku.filechooser.FileChooserActivity
import yuku.filechooser.FileChooserConfig

private const val TAG = "VersionsActivity"
private const val REQCODE_openFile = 1

class VersionsActivity : BaseActivity() {
    private lateinit var sectionsPagerAdapter: SectionsPagerAdapter
    private lateinit var viewPager: ViewPager
    private lateinit var tablayout: TabLayout

    var query_text: String? = null

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

        // we are trying to open a file, so let's go to the DOWNLOADED tab, as it is more relevant.
        viewPager.currentItem = 1

        val uri = intent.data ?: return
        val isLocalFile = "file" == uri.scheme

        val isYesFile: Boolean? // false:pdb true:yes null:cannotdetermine
        val filelastname: String?

        // Determine file type from path if this is a local file
        if (isLocalFile) {
            val path = uri.path
            isYesFile = when {
                path == null -> null
                path.endsWith(".yes", ignoreCase = true) -> true
                path.endsWith(".pdb", ignoreCase = true) -> false
                else -> null
            }
            filelastname = uri.lastPathSegment
        } else {
            // try to read display name from content
            try {
                contentResolver.query(uri, null, null, null, null).use { c ->
                    if (c == null || !c.moveToNext()) {
                        MaterialDialog(this).show {
                            message(text = TextUtils.expandTemplate(getString(R.string.open_yes_error_read), uri.toString()))
                            positiveButton(R.string.ok)
                        }
                        return
                    }

                    val col = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (col != -1) {
                        val name = c.getString(col)
                        isYesFile = when {
                            name == null -> null
                            name.endsWith(".yes", ignoreCase = true) -> true
                            name.endsWith(".pdb", ignoreCase = true) -> false
                            else -> null
                        }
                        filelastname = name
                    } else {
                        isYesFile = null
                        filelastname = null
                    }
                }
            } catch (e: SecurityException) {
                MaterialDialog(this).show {
                    message(text = TextUtils.expandTemplate(getString(R.string.open_yes_error_read), uri.toString()))
                    positiveButton(R.string.ok)
                }
                return
            }
        }

        if (isYesFile == null) { // can't be determined
            MaterialDialog(this).show {
                message(R.string.open_file_unknown_file_format)
                positiveButton(R.string.ok)
            }
            return
        }

        try {
            if (!isYesFile) { // pdb file
                if (isLocalFile) {
                    handleFileOpenPdb(uri.path!!)
                } else {
                    // copy the file to cache first
                    val cacheFile = File(cacheDir, "datafile")
                    val input = contentResolver.openInputStream(uri)
                    if (input == null) {
                        MaterialDialog(this).show {
                            message(text = TextUtils.expandTemplate(getString(R.string.open_yes_error_read), uri.toString()))
                            positiveButton(R.string.ok)
                        }
                        return
                    }

                    input.use {
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    handleFileOpenPdb(cacheFile.absolutePath, filelastname)
                }
                return
            }

            if (isLocalFile) { // opening a local yes file
                handleFileOpenYes(File(uri.path!!))
                return
            }

            val existingFile = AddonManager.getReadableVersionFile(filelastname)
            if (existingFile != null) {
                MaterialDialog(this).show {
                    message(text = getString(R.string.open_yes_file_name_conflict, filelastname, existingFile.absolutePath))
                    positiveButton(R.string.ok)
                }
                return
            }

            val input = contentResolver.openInputStream(uri)
            if (input == null) {
                MaterialDialog(this).show {
                    message(text = TextUtils.expandTemplate(getString(R.string.open_yes_error_read), uri.toString()))
                    positiveButton(R.string.ok)
                }
                return
            }

            val localFile = AddonManager.getWritableVersionFile(filelastname)
            input.use {
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }
            handleFileOpenYes(localFile)

        } catch (e: Exception) {
            MaterialDialog(this).show {
                message(R.string.open_file_cant_read_source)
                positiveButton(R.string.ok)
            }
        }
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
                clickOnOpenFile()
                true
            }

            R.id.menuAddFromUrl -> {
                openUrlInputDialog(null)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun clickOnOpenFile() {
        val config = FileChooserConfig()
        config.mode = FileChooserConfig.Mode.Open
        config.initialDir = Environment.getExternalStorageDirectory().absolutePath
        config.title = getString(R.string.ed_choose_pdb_or_yes_file)
        config.pattern = ".*\\.(?i:pdb|yes|yes\\.gz)"
        startActivityForResult(FileChooserActivity.createIntent(App.context, config), REQCODE_openFile)
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

    private fun handleFileOpenPdb(pdbFilename: String, filelastname: String? = null) {
        val yesName = yesNameForPdb(filelastname ?: pdbFilename)

        // check if it exists previously
        if (AddonManager.getReadableVersionFile(yesName) != null) {
            MaterialDialog(this).show {
                message(R.string.ed_this_file_is_already_on_the_list)
                positiveButton(R.string.ok)
            }
            return
        }

        val callback = object : ConvertOptionsDialog.ConvertOptionsCallback {
            private fun showPdbReadErrorDialog(exception: Throwable) {
                val sw = StringWriter(400)
                sw.append('(').append(exception.javaClass.name).append("): ").append(exception.message).append('\n')
                exception.printStackTrace(PrintWriter(sw))

                val message = if (exception is ConvertOptionsDialog.PdbKnownErrorException) exception.message.orEmpty() else getString(R.string.ed_details) + sw.toString()

                MaterialDialog(this@VersionsActivity).show {
                    title(R.string.ed_error_reading_pdb_file)
                    message(text = message)
                    positiveButton(R.string.ok)
                }
            }

            fun showResult(yesFile: File, exception: Throwable?, wronglyConvertedBookNames: List<String?>?) {
                if (exception != null) {
                    Tracker.trackEvent("versions_convert_pdb_error")
                    showPdbReadErrorDialog(exception)
                    return
                }

                // success.
                Tracker.trackEvent("versions_convert_pdb_success")
                handleFileOpenYes(yesFile)

                if (wronglyConvertedBookNames != null && wronglyConvertedBookNames.isNotEmpty()) {
                    val msg = buildString {
                        append(getString(R.string.ed_the_following_books_from_the_pdb_file_are_not_recognized))
                        for (s in wronglyConvertedBookNames) {
                            append("- ")
                            append(s)
                            append('\n')
                        }
                    }

                    MaterialDialog(this@VersionsActivity).show {
                        message(text = msg)
                        positiveButton(R.string.ok)
                    }
                }
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

                    val result = converter.convert(App.context, pdbFilename, yesFile, params)
                    onPostExecute(result)
                }
            }
        }

        Tracker.trackEvent("versions_convert_pdb_start")
        ConvertOptionsDialog(this, pdbFilename, callback).show()
    }

    fun handleFileOpenYes(file: File) {
        try {
            val reader = YesReaderFactory.createYesReader(file.absolutePath) ?: throw Exception("Not a valid YES file.")

            var maxOrdering = S.db.versionMaxOrdering
            if (maxOrdering == 0) maxOrdering = MVersionDb.DEFAULT_ORDERING_START

            val mvDb = MVersionDb().apply {
                locale = reader.locale
                shortName = reader.shortName
                longName = reader.longName
                description = reader.description
                filename = file.absolutePath
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
    }

    /**
     * @return a filename for yes that will be converted from pdb file, such as "pdb-XXX.yes"
     * XXX is the original filename without the .pdb or .PDB ending, converted to lowercase.
     * All except alphanumeric and . - _ are stripped.
     * Path not included.
     *
     * Previously it was like "pdb-1234abcd-1.yes".
     */
    private fun yesNameForPdb(filenamepdb: String): String {
        var base = File(filenamepdb).name.lowercase()
        if (base.endsWith(".pdb")) {
            base = base.substring(0, base.length - 4)
        }
        base = base.replace("[^0-9a-z_.-]".toRegex(), "")
        return "pdb-$base.yes"
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQCODE_openFile) {
            val result = FileChooserActivity.obtainResult(data) ?: return

            // we are trying to open a file, so let's go to the DOWNLOADED tab, as it is more relevant.
            viewPager.currentItem = 1
            val filename = result.firstFilename
            if (filename.endsWith(".yes.gz", ignoreCase = true)) {
                // decompress or see if the same filename without .gz exists
                val maybeDecompressed = File(filename.substring(0, filename.length - 3))
                if (maybeDecompressed.exists() && !maybeDecompressed.isDirectory && maybeDecompressed.canRead()) {
                    handleFileOpenYes(maybeDecompressed)
                } else {
                    val pd = MaterialDialog(this).show {
                        message(R.string.sedang_mendekompres_harap_tunggu)
                        cancelable(false)
                        progress(true, 0)
                    }

                    Background.run {
                        val tmpfile3 = filename + "-" + (Math.random() * 100000).toInt() + ".tmp3"
                        val result2 = try {
                            val input = GZIPInputStream(FileInputStream(filename))
                            val output = FileOutputStream(tmpfile3) // decompressed file
                            input.copyTo(output)
                            output.close()
                            input.close()

                            val renameOk = File(tmpfile3).renameTo(maybeDecompressed)
                            if (!renameOk) {
                                throw RuntimeException("Failed to rename!")
                            }

                            maybeDecompressed
                        } catch (e: Exception) {
                            null
                        } finally {
                            AppLog.d(TAG, "menghapus tmpfile3: $tmpfile3")
                            File(tmpfile3).delete()
                        }

                        Foreground.run {
                            pd.dismiss()

                            if (result2 != null) {
                                Tracker.trackEvent("versions_open_yes_gz")
                                handleFileOpenYes(result2)
                            }
                        }
                    }
                }
            } else if (filename.endsWith(".yes", ignoreCase = true)) {
                Tracker.trackEvent("versions_open_yes")
                handleFileOpenYes(File(filename))

            } else if (filename.endsWith(".pdb", ignoreCase = true)) {
                Tracker.trackEvent("versions_open_pdb")
                handleFileOpenPdb(filename)

            } else {
                MaterialDialog(this).show {
                    message(R.string.ed_invalid_file_selected)
                    positiveButton(R.string.ok)
                }
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
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
