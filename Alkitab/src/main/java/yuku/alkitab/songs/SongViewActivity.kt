package yuku.alkitab.songs

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.InputType
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import androidx.core.app.ShareCompat
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import java.util.Locale
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.AlertDialogActivity
import yuku.alkitab.base.ac.HelpActivity
import yuku.alkitab.base.ac.PatchTextActivity
import yuku.alkitab.base.ac.base.BaseLeftDrawerActivity
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.dialog.VersesDialog
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AlphanumComparator
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Background
import yuku.alkitab.base.util.ClipboardUtil
import yuku.alkitab.base.util.FontManager
import yuku.alkitab.base.util.Sqlitil
import yuku.alkitab.base.util.TargetDecoder
import yuku.alkitab.base.widget.LeftDrawer
import yuku.alkitab.base.widget.TwofingerLinearLayout
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R
import yuku.alkitab.songs.SongViewActivity.Companion.songAudioController
import yuku.alkitab.songs.newdoc.ScriptureBlock
import yuku.alkitab.songs.newdoc.SongDocument
import yuku.alkitab.songs.newdoc.SongDocumentJson
import yuku.alkitab.songs.newdoc.SongDocumentRenderer
import yuku.alkitab.songs.newdoc.SongDocumentText
import yuku.alkitabintegration.display.Launcher

private const val TAG = "SongViewActivity"

// package-visible (not private): reused by SongFragment when rendering an in-document ScriptureBlock
const val BIBLE_PROTOCOL = "bible"
private const val REQCODE_songList = 1
private const val REQCODE_downloadSongBook = 3
private const val FRAGMENT_TAG_SONG = "song"

class SongViewActivity : BaseLeftDrawerActivity(), SongFragment.ShouldOverrideUrlLoadingHandler, LeftDrawer.Songs.Listener, MediaStateListener {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var leftDrawer: LeftDrawer.Songs

    private lateinit var root: TwofingerLinearLayout
    private lateinit var no_song_data_container: ViewGroup
    private lateinit var bDownload: View
    private lateinit var circular_progress: View

    private val templateCustomVars = Bundle()
    private var currentBookName: String? = null
    private var currentSong: SongDocument? = null

    // for initially populating the search song activity
    private var last_searchState: SongListActivity.SearchState? = null

    // state for the keypad
    private var state_originalCode: String? = null
    private var state_tempCode = ""

    // cache of song codes for each book
    private var cache_codes = mutableMapOf<String /* bookName */, MutableList<String> /* ordered codes */>()

    private val song_container_listener = object : TwofingerLinearLayout.Listener {
        var textZoom = 0 // stays at 0 if zooming is not ready

        override fun onOnefingerLeft() {
            goTo(+1)
        }

        override fun onOnefingerRight() {
            goTo(-1)
        }

        override fun onTwofingerStart() {
            val f = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_SONG)
            if (f is SongFragment) {
                textZoom = f.webViewTextZoom
            }
        }

        override fun onTwofingerScale(scale: Float) {
            val newTextZoom = (textZoom * scale).toInt().coerceIn(50, 200)

            val f = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_SONG)
            if (f is SongFragment) {
                f.webViewTextZoom = newTextZoom
            }
        }

        override fun onTwofingerDragX(dx: Float) {}

        override fun onTwofingerDragY(dy: Float) {}

        override fun onTwofingerEnd(mode: TwofingerLinearLayout.Mode?) {}
    }

    private val mediaState = MediaState()

    override fun getLeftDrawer() = leftDrawer

    inner class MediaState {
        var enabled = false

        @DrawableRes
        var icon = 0

        @StringRes
        var label = 0
        var loading = false
        var progress: String? = null
            set(value) {
                field = value
                updateActivityTitle()
            }
    }

    /**
     * This method might be called from non-UI thread. Be careful when manipulating UI.
     */
    @WorkerThread
    override fun onControllerStateChanged(state: MediaController.State) {
        when (state) {
            MediaController.State.reset -> {
                mediaState.enabled = false
                mediaState.icon = R.drawable.ic_action_hollowplay
                mediaState.label = R.string.menuPlay
            }

            MediaController.State.reset_media_known_to_exist, MediaController.State.paused, MediaController.State.complete -> {
                mediaState.enabled = true
                mediaState.icon = R.drawable.ic_action_play
                mediaState.label = R.string.menuPlay
            }

            MediaController.State.playing -> {
                // we start playing now
                mediaState.enabled = true
                mediaState.icon = R.drawable.ic_action_pause
                mediaState.label = R.string.menuPause
            }

            else -> {
            }
        }

        mediaState.loading = state == MediaController.State.preparing

        runOnUiThread {
            obtainSongProgress()
            invalidateOptionsMenu()
        }
    }

    fun obtainSongProgress() {
        val (position, duration) = songAudioController.getProgress()
        if (position == -1L) {
            mediaState.progress = null
            return
        }

        if (duration == -1L) {
            mediaState.progress = String.format(Locale.US, "%d:%02d", position / 60000, position % 60000 / 1000)
            return
        }

        mediaState.progress = String.format(Locale.US, "%d:%02d / %d:%02d", position / 60000, position % 60000 / 1000, duration / 60000, duration % 60000 / 1000)
    }

    fun goTo(dir: Int) {
        val currentBookName = currentBookName ?: return
        val currentSong = currentSong ?: return

        val codes = cache_codes.getOrPut(currentBookName) {
            val songInfos = App.services.storage.songDb.listSongInfosByBookName(currentBookName)
            val codes = mutableListOf<String>()
            for (songInfo in songInfos) {
                codes.add(songInfo.code)
            }
            // sort codes based on numeric
            codes.sortWith(AlphanumComparator())
            codes
        }

        // find index of current song
        val pos = codes.indexOf(currentSong.code)
        if (pos == -1) {
            return // should not happen
        }

        val newPos = pos + dir
        if (newPos < 0 || newPos >= codes.size) {
            return // can't go left or right
        }

        val newCode = codes[newPos]
        val newSong = App.services.storage.songDb.getSong(currentBookName, newCode) ?: return // should not happen

        displaySong(currentBookName, newSong)
    }

    @SuppressLint("HandlerLeak")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_view)

        circular_progress = findViewById(R.id.progress_circular)

        setCustomProgressBarIndeterminateVisible(false)

        drawerLayout = findViewById(R.id.drawerLayout)
        leftDrawer = findViewById(R.id.left_drawer)
        leftDrawer.configure(this, drawerLayout)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)

        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                drawer_opened()
            }
        })

        root = findViewById(R.id.root)
        no_song_data_container = findViewById(R.id.no_song_data_container)
        bDownload = findViewById(R.id.bDownload)

        root.setListener(song_container_listener)

        bDownload.setOnClickListener { openDownloadSongBookPage() }

        // if no song books is downloaded, open download page immediately
        if (App.services.storage.songDb.countSongBookInfos() == 0) {
            openDownloadSongBookPage()
        }

        object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (isFinishing) return

                obtainSongProgress()
                invalidateOptionsMenu()

                sendEmptyMessageDelayed(0, 1000)
            }
        }.sendEmptyMessage(0)
    }

    private fun openDownloadSongBookPage() {
        startActivityForResult(
            HelpActivity.createIntentWithOverflowMenu(
                // dataFormatVersion tells the page which payload version to link to (it should emit
                // alkitab://...&dataFormatVersion=5 download links pointing at the gzipped JSON
                // song-book wrapper) — same query param name SongBookUtil.downloadSongBook itself
                // sends to get_songs.
                "${BuildConfig.SERVER_HOST}/songs/downloads?dataFormatVersion=${SongDocumentJson.DATA_FORMAT_VERSION}&${App.getAppIdentifierParamsEncoded()}",
                getString(R.string.sn_download_song_books),
                getString(R.string.sn_menu_private_song_book),
                AlertDialogActivity.createInputIntent(
                    null,
                    getString(R.string.sn_private_song_book_dialog_desc),
                    getString(R.string.cancel),
                    getString(R.string.ok),
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS,
                    getString(R.string.sn_private_song_book_name_hint)
                )
            ),
            REQCODE_downloadSongBook
        )
    }

    override fun onStart() {
        super.onStart()

        val applied = App.services.uiDimensions.applied()

        // apply background color, and clear window background to prevent overdraw
        window.setBackgroundDrawableResource(android.R.color.transparent)
        findViewById<View>(android.R.id.content).setBackgroundColor(applied.backgroundColor)

        templateCustomVars.clear()
        templateCustomVars.putString("background_color", String.format(Locale.US, "#%06x", applied.backgroundColor and 0xffffff))
        templateCustomVars.putString("text_color", String.format(Locale.US, "#%06x", applied.fontColor and 0xffffff))
        templateCustomVars.putString("verse_number_color", String.format(Locale.US, "#%06x", applied.verseNumberColor and 0xffffff))
        templateCustomVars.putString("text_size", applied.fontSize2dp.toString() + "px")
        templateCustomVars.putString("line_spacing_mult", applied.lineSpacingMult.toString())

        val fontName = Preferences.getString(Prefkey.jenisHuruf, null)
        if (FontManager.isCustomFont(fontName)) {
            val customFontUri = FontManager.getCustomFontUri(fontName)
            if (customFontUri != null) {
                templateCustomVars.putString("custom_font_loader", String.format(Locale.US, "@font-face{ font-family: '%s'; src: url('%s'); }", fontName, customFontUri))
            } else {
                templateCustomVars.putString("custom_font_loader", "")
            }
        } else {
            templateCustomVars.putString("custom_font_loader", "")
        }
        templateCustomVars.putString("text_font", fontName)

        // show latest viewed song
        val bookName = Preferences.getString(Prefkey.song_last_bookName, null)
        val code = Preferences.getString(Prefkey.song_last_code, null)

        if (bookName == null || code == null) {
            displaySong(null, null, true)
        } else {
            displaySong(bookName, App.services.storage.songDb.getSong(bookName, code), true)
        }

        window.decorView.keepScreenOn = Preferences.getBoolean(getString(R.string.pref_keepScreenOn_key), resources.getBoolean(R.bool.pref_keepScreenOn_default))
    }

    /**
     * Used after deleting a song, and the current song is no longer available
     */
    private fun displayAnySongOrFinish() {
        val pair = App.services.storage.songDb.anySong
        if (pair == null) {
            finish()
        } else {
            displaySong(pair.first, pair.second)
        }
    }

    override fun onResume() {
        super.onResume()

        songAudioController.setUI(this, this)
        songAudioController.updateMediaState()
    }

    private fun checkAudioExistance() {
        val currentBookName = currentBookName ?: return
        val currentSong = currentSong ?: return

        Background.run {
            try {
                val filename = getAudioFilename(currentBookName, currentSong.code)
                val response = Connections.downloadString(BuildConfig.SERVER_HOST + "/addon/audio/exists?filename=" + Uri.encode(filename))
                if (response.startsWith("OK")) {
                    // make sure this is the correct one due to possible race condition
                    val currentCurrentBookName = this.currentBookName
                    val currentCurrentSong = this.currentSong
                    if (currentCurrentBookName == currentBookName && currentCurrentSong?.code == currentSong.code) {
                        runOnUiThread {
                            if (songAudioController.canHaveNewUrl()) {
                                // The service's ExoPlayer auto-detects MP3 vs MIDI,
                                // so no extension branching is needed here.
                                val url = "${BuildConfig.SERVER_HOST}/addon/audio/${getAudioFilename(currentBookName, currentSong.code)}"
                                songAudioController.setUI(this, this)
                                songAudioController.setDisplayInfo(
                                    "${SongBookUtil.escapeSongBookName(currentBookName)} ${currentSong.code}",
                                    currentSong.meta.title ?: "",
                                )
                                songAudioController.mediaKnownToExist(url)
                            } else {
                                AppLog.d(TAG, "songAudioController can't have new URL at this moment.")
                            }
                        }
                    }
                } else {
                    AppLog.d(TAG, "@@checkAudioExistance response: $response")
                }
            } catch (e: IOException) {
                AppLog.e(TAG, "@@checkAudioExistance", e)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_song_view, menu)

        Handler(Looper.getMainLooper()).post {
            val view = findViewById<View>(R.id.menuMediaControl) ?: return@post

            view.setOnLongClickListener {
                if (mediaState.icon == R.drawable.ic_action_play) {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(R.string.sn_play_in_loop)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            songAudioController.playOrPause(true)
                        }
                        .show()
                    true
                } else {
                    false
                }
            }
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val menuMediaControl = menu.findItem(R.id.menuMediaControl)
        menuMediaControl.isEnabled = mediaState.enabled
        if (mediaState.icon != 0) menuMediaControl.setIcon(mediaState.icon)
        if (mediaState.label != 0) menuMediaControl.setTitle(mediaState.label)
        if (mediaState.loading) {
            setCustomProgressBarIndeterminateVisible(true)
            menuMediaControl.isVisible = false
        } else {
            setCustomProgressBarIndeterminateVisible(false)
            menuMediaControl.isVisible = true
        }

        val songShown = currentBookName != null

        val menuCopy = menu.findItem(R.id.menuCopy)
        menuCopy.isVisible = songShown
        val menuShare = menu.findItem(R.id.menuShare)
        menuShare.isVisible = songShown
        val menuUpdateBook = menu.findItem(R.id.menuUpdateBook)
        menuUpdateBook.isVisible = songShown
        val menuDeleteSongBook = menu.findItem(R.id.menuDeleteSongBook)
        menuDeleteSongBook.isVisible = songShown

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                leftDrawer.toggleDrawer()
                return true
            }

            R.id.menuCopy -> {
                currentSong?.let { currentSong ->
                    ClipboardUtil.copyToClipboard(convertSongToText(currentSong))

                    Snackbar.make(root, R.string.sn_copied, Snackbar.LENGTH_SHORT).show()
                }
                return true
            }

            R.id.menuShare -> {
                currentSong?.let { currentSong ->
                    ShareCompat.IntentBuilder(this@SongViewActivity)
                        .setType("text/plain")
                        .setSubject("${SongBookUtil.escapeSongBookName(currentBookName)} ${currentSong.code} ${currentSong.meta.title}")
                        .setText(convertSongToText(currentSong))
                        .setChooserTitle(getString(R.string.sn_share_title))
                        .startChooser()
                }
                return true
            }

            R.id.menuSearch -> {
                startActivityForResult(SongListActivity.createIntent(last_searchState), REQCODE_songList)
                return true
            }

            R.id.menuMediaControl -> {
                val proceed = {
                    val currentBookName = currentBookName
                    val currentSong = currentSong
                    if (currentBookName != null && currentSong != null) {
                        songAudioController.playOrPause(false)
                    }
                }
                if (audioDisclaimerAcknowledged) {
                    proceed()
                } else {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(R.string.sn_audio_disclaimer_message)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            audioDisclaimerAcknowledged = true
                            proceed()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }

                return true
            }

            R.id.menuUpdateBook -> {
                MaterialAlertDialogBuilder(this)
                    .setMessage(TextUtils.expandTemplate(getText(R.string.sn_update_book_explanation), SongBookUtil.escapeSongBookName(currentBookName)))
                    .setPositiveButton(R.string.sn_update_book_confirm_button) { _, _ -> updateSongBook() }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                return true
            }

            R.id.menuDeleteSongBook -> {
                MaterialAlertDialogBuilder(this)
                    .setMessage(TextUtils.expandTemplate(getText(R.string.sn_delete_song_book_explanation), SongBookUtil.escapeSongBookName(currentBookName)))
                    .setPositiveButton(R.string.delete) { _, _ -> deleteSongBook() }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun updateSongBook() {
        val currentBookName = currentBookName ?: return
        val currentSong = currentSong ?: return

        val songBookInfo = SongBookUtil.getSongBookInfo(currentBookName)

        val currentSongCode = currentSong.code
        val dataFormatVersion = App.services.storage.songDb.getDataFormatVersionForSongs(currentBookName)

        SongBookUtil.downloadSongBook(this@SongViewActivity, songBookInfo, dataFormatVersion, object : SongBookUtil.OnDownloadSongBookListener {
            override fun onFailedOrCancelled(songBookInfo: SongBookUtil.SongBookInfo, e: Exception?) {
                showDownloadError(e)
            }

            override fun onDownloadedAndInserted(songBookInfo: SongBookUtil.SongBookInfo) {
                val song = App.services.storage.songDb.getSong(songBookInfo.name, currentSongCode)
                cache_codes.remove(songBookInfo.name)
                displaySong(songBookInfo.name, song)
            }
        })
    }

    fun showDownloadError(e: Exception?) {
        if (e == null) return
        if (isFinishing) return

        if (e is SongBookUtil.NotOkException) {
            MaterialAlertDialogBuilder(this)
                .setMessage("HTTP error " + e.code)
                .setPositiveButton(R.string.ok, null)
                .show()
        } else {
            MaterialAlertDialogBuilder(this)
                .setMessage("${e.javaClass.simpleName}: ${e.message}")
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    private fun deleteSongBook() {
        val pd = MaterialAlertDialogBuilder(this)
            .setMessage(R.string.please_wait_titik3)
            .setCancelable(false)
            .show()

        val bookName = currentBookName

        Background.run {
            val count = App.services.storage.songDb.deleteSongBook(bookName)

            runOnUiThread {
                pd.dismiss()

                val dialog = MaterialAlertDialogBuilder(this)
                    .setMessage(TextUtils.expandTemplate(getText(R.string.sn_delete_song_book_result), "" + count, SongBookUtil.escapeSongBookName(bookName)))
                    .setPositiveButton(R.string.ok, null)
                    .create()
                dialog.setOnDismissListener { displayAnySongOrFinish() }
                dialog.show()
            }
        }
    }

    private fun scriptureReferencesOsis(doc: SongDocument): String? =
        doc.blocks.filterIsInstance<ScriptureBlock>().firstOrNull()?.osis

    private fun convertSongToText(doc: SongDocument): String {
        return SongDocumentText.render(
            doc = doc,
            bookNameDisplay = SongBookUtil.escapeSongBookName(currentBookName),
            scriptureReferencesText = scriptureReferencesOsis(doc)?.let { ScriptureReferenceRenderer.render(null, it) },
            versionCaption = { n -> getString(R.string.sn_lyric_version_version, n.toString()) },
            refrainMarker = getString(R.string.sn_lyric_refrain_marker),
        )
    }

    @JvmOverloads
    fun displaySong(bookName: String?, doc: SongDocument?, onCreate: Boolean = false) {
        root.visibility = if (doc != null) VISIBLE else GONE
        no_song_data_container.visibility = if (doc != null) GONE else VISIBLE

        if (!onCreate) {
            songAudioController.reset()
        }

        if (doc == null) return

        val handle = leftDrawer.handle
        handle.setBookName(SongBookUtil.escapeSongBookName(bookName))
        handle.setCode(doc.code)

        val copyright = SongBookUtil.getCopyright(bookName)
        templateCustomVars.putString("copyright", copyright ?: "")
        templateCustomVars.putString("patch_text_open_link", getString(R.string.patch_text_open_link))

        val ft = supportFragmentManager.beginTransaction()
        ft.replace(R.id.root, SongFragment.create(doc, templateCustomVars), FRAGMENT_TAG_SONG)
        ft.commitAllowingStateLoss()

        currentBookName = bookName
        currentSong = doc

        updateActivityTitle()

        // save latest viewed song
        Preferences.setString(Prefkey.song_last_bookName, bookName)
        Preferences.setString(Prefkey.song_last_code, doc.code)

        checkAudioExistance()
    }

    private fun updateActivityTitle() {
        val bookName = currentBookName ?: return
        val song = currentSong ?: return

        title = buildSpannedString {
            append(SongBookUtil.escapeSongBookName(bookName))
            append(" ")
            append(song.code)

            val progress = mediaState.progress
            if (progress != null) {
                append(" ")
                inSpans(RelativeSizeSpan(0.75f)) {
                    append(progress)
                }
            }
        }
    }

    fun drawer_opened() {
        if (currentBookName == null) return

        val handle = leftDrawer.handle
        handle.setOkButtonEnabled(false)
        handle.setAButtonEnabled(false)
        handle.setBButtonEnabled(false)
        handle.setCButtonEnabled(false)

        val originalCode = currentSong?.code ?: "––––"
        handle.setCode(originalCode)

        state_originalCode = originalCode
        state_tempCode = ""
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQCODE_songList -> {
                if (resultCode == RESULT_OK) {
                    val result = SongListActivity.obtainResult(data)
                    if (result != null) {
                        displaySong(result.bookName, App.services.storage.songDb.getSong(result.bookName, result.code))
                        // store this for next search
                        last_searchState = result.last_searchState
                    }
                }
                return
            }

            REQCODE_downloadSongBook -> {
                if (resultCode == RESULT_OK) {
                    val uri = data?.data
                    if (uri != null) {
                        downloadByAlkitabUri(uri)
                    } else {
                        val input = data?.getStringExtra(AlertDialogActivity.EXTRA_INPUT)
                        if (!input.isNullOrEmpty()) {
                            downloadByAlkitabUri(("alkitab:///addon/download?kind=songbook&type=ser&dataFormatVersion=${SongDocumentJson.DATA_FORMAT_VERSION}&name=_${Uri.encode(input.uppercase(Locale.US))}").toUri())
                        }
                    }
                    return
                }
                return
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun downloadByAlkitabUri(uri: Uri) {
        if ("alkitab" != uri.scheme || "/addon/download" != uri.path || "songbook" != uri.getQueryParameter("kind") || "ser" != uri.getQueryParameter("type") || uri.getQueryParameter("name") == null) {
            MaterialAlertDialogBuilder(this)
                .setMessage("Invalid uri:\n\n$uri")
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        val dataFormatVersion_s = uri.getQueryParameter("dataFormatVersion")
        val dataFormatVersion: Int
        try {
            dataFormatVersion = Integer.parseInt("" + dataFormatVersion_s)
        } catch (_: NumberFormatException) {
            MaterialAlertDialogBuilder(this)
                .setMessage("Invalid uri:\n\n$uri")
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        } catch (_: NullPointerException) {
            MaterialAlertDialogBuilder(this)
                .setMessage("Invalid uri:\n\n$uri")
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        if (!SongBookUtil.isSupportedDataFormatVersion(dataFormatVersion)) {
            MaterialAlertDialogBuilder(this)
                .setMessage("Unsupported data format version: $dataFormatVersion")
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        val info = SongBookUtil.SongBookInfo()
        info.name = uri.getQueryParameter("name")
        info.title = uri.getQueryParameter("title")
        info.copyright = uri.getQueryParameter("copyright")

        SongBookUtil.downloadSongBook(this, info, dataFormatVersion, object : SongBookUtil.OnDownloadSongBookListener {
            override fun onDownloadedAndInserted(songBookInfo: SongBookUtil.SongBookInfo) {
                val name = songBookInfo.name
                val song = App.services.storage.songDb.getFirstSongFromBook(name)
                displaySong(name, song)
            }

            override fun onFailedOrCancelled(songBookInfo: SongBookUtil.SongBookInfo, e: Exception?) {
                showDownloadError(e)
            }
        })
    }

    @Keep
    class PatchTextExtraInfoJson {
        var type: String? = null
        var bookName: String? = null
        var code: String? = null
    }

    override fun shouldOverrideUrlLoading(client: WebViewClient, request: WebResourceRequest): Boolean {
        val uri = request.url ?: return false

        when (uri.scheme) {
            "patchtext" -> {
                val doc = currentSong

                if (doc != null) {
                    // do not proceed if the song is too old
                    val updateTime = App.services.storage.songDb.getSongUpdateTime(currentBookName, doc.code)
                    if (updateTime == 0 || Sqlitil.nowDateTime() - updateTime > 21 * 86400) {
                        MaterialAlertDialogBuilder(this)
                            .setMessage(TextUtils.expandTemplate(getText(R.string.sn_update_book_because_too_old), SongBookUtil.escapeSongBookName(currentBookName)))
                            .setPositiveButton(R.string.sn_update_book_confirm_button) { _, _ -> updateSongBook() }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    } else {
                        val extraInfo = PatchTextExtraInfoJson()
                        extraInfo.type = "song"
                        extraInfo.bookName = currentBookName
                        extraInfo.code = doc.code

                        val codeLine = "<div>${doc.code}</div>"
                        val songHtml = SongDocumentRenderer.renderDocument(doc, renderScripture = { osis -> ScriptureReferenceRenderer.render(null, osis) }, forPatchText = true)
                        val baseBody = HtmlCompat.fromHtml(codeLine + songHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
                        startActivity(PatchTextActivity.createIntent(baseBody, App.getDefaultGson().toJson(extraInfo), null))
                    }
                }
                return true
            }

            BIBLE_PROTOCOL -> {
                val ariRanges = TargetDecoder.decode("o:" + uri.schemeSpecificPart)
                if (ariRanges != null) {
                    val versesDialog = VersesDialog.newInstance(ariRanges)
                    versesDialog.listener = object : VersesDialog.VersesDialogListener() {
                        override fun onVerseSelected(ari: Int) {
                            startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(ari))
                        }
                    }
                    versesDialog.show(supportFragmentManager, "VersesDialog")
                }
                return true
            }

            else -> return false
        }
    }

    private val keypadViewToNumConverter by lazy {
        val numIds = intArrayOf(R.id.bDigit0, R.id.bDigit1, R.id.bDigit2, R.id.bDigit3, R.id.bDigit4, R.id.bDigit5, R.id.bDigit6, R.id.bDigit7, R.id.bDigit8, R.id.bDigit9)
        val alphaIds = intArrayOf(R.id.bDigitA, R.id.bDigitB, R.id.bDigitC) // num = 10, 11, 12

        fun(v: View): Int {
            val id = v.id

            for (i in numIds.indices) if (id == numIds[i]) return i
            for (i in alphaIds.indices) if (id == alphaIds[i]) return 10 + i // special code for alpha
            if (id == R.id.bBackspace) return 20 // special code for the backspace
            if (id == R.id.bOk) return 21 // special code for OK
            return -1
        }
    }

    override fun songKeypadButton_click(v: View) {
        val currentBookName = currentBookName ?: return

        val handle = leftDrawer.handle

        fun updateHandle() {
            handle.setCode(state_tempCode)

            handle.setOkButtonEnabled(App.services.storage.songDb.songExists(currentBookName, state_tempCode))
            handle.setAButtonEnabled(state_tempCode.length <= 3 && App.services.storage.songDb.songExists(currentBookName, state_tempCode + "A"))
            handle.setBButtonEnabled(state_tempCode.length <= 3 && App.services.storage.songDb.songExists(currentBookName, state_tempCode + "B"))
            handle.setCButtonEnabled(state_tempCode.length <= 3 && App.services.storage.songDb.songExists(currentBookName, state_tempCode + "C"))
        }

        when (val num = keypadViewToNumConverter(v)) {
            in 0..9 -> { // digits
                if (state_tempCode.length >= 4) state_tempCode = "" // can't be more than 4 digits

                if (state_tempCode.isNotEmpty() || num != 0) {
                    state_tempCode += num
                }

                updateHandle()
            }

            in 10..19 -> { // letters
                if (state_tempCode.length >= 4) state_tempCode = "" // can't be more than 4 digits

                val letter = ('A'.code + num - 10).toChar()
                if (state_tempCode.isNotEmpty()) {
                    state_tempCode += letter
                }

                updateHandle()
            }

            20 -> { // backspace
                if (state_tempCode.isNotEmpty()) {
                    state_tempCode = state_tempCode.substring(0, state_tempCode.length - 1)
                }

                updateHandle()
            }

            21 -> { // OK
                if (state_tempCode.isNotEmpty()) {
                    val song = App.services.storage.songDb.getSong(currentBookName, state_tempCode)
                    if (song != null) {
                        displaySong(currentBookName, song)
                    } else {
                        handle.setCode(state_originalCode) // revert
                    }
                } else {
                    handle.setCode(state_originalCode) // revert
                }
                leftDrawer.closeDrawer()
            }
        }
    }

    override fun songBookSelected(name: String) {
        val song = App.services.storage.songDb.getFirstSongFromBook(name)

        if (song != null) {
            displaySong(name, song)
        }

        state_tempCode = ""
    }

    override fun moreSelected() {
        openDownloadSongBookPage()
    }

    private fun setCustomProgressBarIndeterminateVisible(visible: Boolean) {
        circular_progress.visibility = if (visible) VISIBLE else GONE
    }

    companion object {
        /**
         * Single hymn-audio controller, backed by the background [SongAudioService]
         * (MP3 + MIDI). Static so it survives activity recreation while audio plays
         * in the background.
         */
        val songAudioController = SongAudioController(App.context)

        var audioDisclaimerAcknowledged = false

        @JvmStatic
        fun createIntent(): Intent {
            return Intent(App.context, SongViewActivity::class.java)
        }

        fun getAudioFilename(bookName: String, code: String): String {
            return String.format(Locale.US, "songs/v2/%s_%s", bookName, code)
        }
    }
}
