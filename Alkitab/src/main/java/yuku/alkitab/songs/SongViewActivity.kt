package yuku.alkitab.songs

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
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
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import androidx.core.app.ShareCompat
import androidx.core.text.HtmlCompat
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.drawerlayout.widget.DrawerLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.analytics.FirebaseAnalytics
import java.io.IOException
import java.util.Locale
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.S
import yuku.alkitab.base.ac.AlertDialogActivity
import yuku.alkitab.base.ac.HelpActivity
import yuku.alkitab.base.ac.PatchTextActivity
import yuku.alkitab.base.ac.base.BaseLeftDrawerActivity
import yuku.alkitab.base.dialog.VersesDialog
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AlphanumComparator
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Background
import yuku.alkitab.base.util.ClipboardUtil
import yuku.alkitab.base.util.FontManager
import yuku.alkitab.base.util.OsisBookNames
import yuku.alkitab.base.util.Sqlitil
import yuku.alkitab.base.util.TargetDecoder
import yuku.alkitab.base.widget.LeftDrawer
import yuku.alkitab.base.widget.TwofingerLinearLayout
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R
import yuku.alkitab.tracking.Tracker
import yuku.alkitabintegration.display.Launcher
import yuku.kpri.model.Song
import yuku.kpri.model.VerseKind

private const val TAG = "SongViewActivity"

private const val BIBLE_PROTOCOL = "bible"
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
    private var currentSong: Song? = null

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
            var newTextZoom = (textZoom * scale).toInt()
            if (newTextZoom < 50) newTextZoom = 50
            if (newTextZoom > 200) newTextZoom = 200

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
                val currentBookName = currentBookName
                val currentSong = currentSong

                if (currentBookName != null && currentSong != null) {
                    Tracker.trackEvent("song_playing",
                        FirebaseAnalytics.Param.ITEM_NAME, currentBookName + " " + currentSong.code,
                        FirebaseAnalytics.Param.ITEM_CATEGORY, currentBookName,
                        FirebaseAnalytics.Param.ITEM_VARIANT, currentSong.code
                    )
                }

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
        val activeMediaController = activeMediaController ?: return

        val (position, duration) = activeMediaController.getProgress()
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
            val songInfos = S.getSongDb().listSongInfosByBookName(currentBookName)
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
        val newSong = S.getSongDb().getSong(currentBookName, newCode) ?: return // should not happen

        trackSongSelect(currentBookName, newCode)
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
        if (S.getSongDb().countSongBookInfos() == 0) {
            openDownloadSongBookPage()
        }

        object : Handler() {
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
                BuildConfig.SERVER_HOST + "songs/downloads?app_versionCode=" + App.getVersionCode() + "&app_versionName=" + Uri.encode(App.getVersionName()),
                getString(R.string.sn_download_song_books),
                getString(R.string.sn_menu_private_song_book),
                AlertDialogActivity.createInputIntent(null,
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

        val applied = S.applied()

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
            displaySong(bookName, S.getSongDb().getSong(bookName, code), true)
        }

        window.decorView.keepScreenOn = Preferences.getBoolean(getString(R.string.pref_keepScreenOn_key), resources.getBoolean(R.bool.pref_keepScreenOn_default))
    }

    /**
     * Used after deleting a song, and the current song is no longer available
     */
    private fun displayAnySongOrFinish() {
        val pair = S.getSongDb().anySong
        if (pair == null) {
            finish()
        } else {
            displaySong(pair.first, pair.second)
        }
    }

    override fun onResume() {
        super.onResume()

        activeMediaController?.let { activeMediaController ->
            activeMediaController.setUI(this, this)
            activeMediaController.updateMediaState()
        }
    }

    private fun checkAudioExistance() {
        val currentBookName = currentBookName ?: return
        val currentSong = currentSong ?: return

        Background.run {
            try {
                val filename = getAudioFilename(currentBookName, currentSong.code)
                val response = App.downloadString(BuildConfig.SERVER_HOST + "addon/audio/exists?filename=" + Uri.encode(filename))
                if (response.startsWith("OK")) {
                    // make sure this is the correct one due to possible race condition
                    val currentCurrentBookName = this.currentBookName
                    val currentCurrentSong = this.currentSong
                    if (currentCurrentBookName == currentBookName && currentCurrentSong?.code == currentSong.code) {
                        runOnUiThread {
                            val prevMediaController = activeMediaController
                            if (prevMediaController == null || prevMediaController.canHaveNewUrl()) {
                                val baseUrl = BuildConfig.SERVER_HOST + "addon/audio/"
                                val url = baseUrl + getAudioFilename(currentBookName, currentSong.code)
                                if (response.contains("extension=mid")) {
                                    setActiveMediaController(midiController)
                                } else {
                                    setActiveMediaController(exoplayerController)
                                }
                                activeMediaController?.mediaKnownToExist(url)
                            } else {
                                AppLog.d(TAG, "activeMediaController can't have new URL at this moment.")
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

    private fun setActiveMediaController(controller: MediaController) {
        // reset the "other" media controller
        val prevMediaController = activeMediaController
        if (prevMediaController != null && prevMediaController !== controller) {
            prevMediaController.reset()
        }
        activeMediaController = controller
        controller.setUI(this, this)
        controller.updateMediaState()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_song_view, menu)

        Handler().post {
            val view = findViewById<View>(R.id.menuMediaControl) ?: return@post

            view.setOnLongClickListener {
                if (mediaState.icon == R.drawable.ic_action_play) {
                    MaterialDialog.Builder(this)
                        .content(R.string.sn_play_in_loop)
                        .negativeText(R.string.cancel)
                        .positiveText(R.string.ok)
                        .onPositive { _, _ ->
                            val activeMediaController = activeMediaController ?: return@onPositive
                            activeMediaController.playOrPause(true)
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
                        .setSubject("${SongBookUtil.escapeSongBookName(currentBookName)} ${currentSong.code} ${currentSong.title}")
                        .setText(convertSongToText(currentSong).toString())
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
                        activeMediaController?.playOrPause(false)
                    }
                }
                if (audioDisclaimerAcknowledged) {
                    proceed()
                } else {
                    MaterialDialog.Builder(this)
                        .content(R.string.sn_audio_disclaimer_message)
                        .positiveText(R.string.ok)
                        .onPositive { _, _ ->
                            audioDisclaimerAcknowledged = true
                            proceed()
                        }
                        .negativeText(R.string.cancel)
                        .show()
                }

                return true
            }

            R.id.menuUpdateBook -> {
                MaterialDialog.Builder(this)
                    .content(TextUtils.expandTemplate(getText(R.string.sn_update_book_explanation), SongBookUtil.escapeSongBookName(currentBookName)))
                    .positiveText(R.string.sn_update_book_confirm_button)
                    .onPositive { _, _ -> updateSongBook() }
                    .negativeText(R.string.cancel)
                    .show()
                return true
            }

            R.id.menuDeleteSongBook -> {
                MaterialDialog.Builder(this)
                    .content(TextUtils.expandTemplate(getText(R.string.sn_delete_song_book_explanation), SongBookUtil.escapeSongBookName(currentBookName)))
                    .positiveText(R.string.delete)
                    .onPositive { _, _ -> deleteSongBook() }
                    .negativeText(R.string.cancel)
                    .show()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun updateSongBook() {
        val currentBookName = currentBookName ?: return
        val currentSong = currentSong ?: return

        val songBookInfo = SongBookUtil.getSongBookInfo(currentBookName) ?: throw RuntimeException("SongBookInfo named $currentBookName was not found")

        val currentSongCode = currentSong.code
        val dataFormatVersion = S.getSongDb().getDataFormatVersionForSongs(currentBookName)

        SongBookUtil.downloadSongBook(this@SongViewActivity, songBookInfo, dataFormatVersion, object : SongBookUtil.OnDownloadSongBookListener {
            override fun onFailedOrCancelled(songBookInfo: SongBookUtil.SongBookInfo, e: Exception?) {
                showDownloadError(e)
            }

            override fun onDownloadedAndInserted(songBookInfo: SongBookUtil.SongBookInfo) {
                val song = S.getSongDb().getSong(songBookInfo.name, currentSongCode)
                cache_codes.remove(songBookInfo.name)
                displaySong(songBookInfo.name, song)
            }
        })
    }

    fun showDownloadError(e: Exception?) {
        if (e == null) return
        if (isFinishing) return

        if (e is SongBookUtil.NotOkException) {
            MaterialDialog.Builder(this)
                .content("HTTP error " + e.code)
                .positiveText(R.string.ok)
                .show()
        } else {
            MaterialDialog.Builder(this)
                .content(e.javaClass.simpleName + ": " + e.message)
                .positiveText(R.string.ok)
                .show()
        }
    }

    private fun deleteSongBook() {
        val pd = MaterialDialog.Builder(this)
            .content(R.string.please_wait_titik3)
            .cancelable(false)
            .progress(true, 0)
            .show()

        val bookName = currentBookName

        Background.run {
            val count = S.getSongDb().deleteSongBook(bookName)

            runOnUiThread {
                pd.dismiss()

                MaterialDialog.Builder(this)
                    .content(TextUtils.expandTemplate(getText(R.string.sn_delete_song_book_result), "" + count, SongBookUtil.escapeSongBookName(bookName)))
                    .positiveText(R.string.ok)
                    .dismissListener { displayAnySongOrFinish() }
                    .show()
            }
        }
    }

    private fun convertSongToText(song: Song): StringBuilder {
        // build text to copy
        val sb = StringBuilder()
        sb.append(SongBookUtil.escapeSongBookName(currentBookName)).append(' ')
        sb.append(song.code).append(". ")
        sb.append(song.title).append('\n')
        if (song.title_original != null) sb.append('(').append(song.title_original).append(')').append('\n')
        sb.append('\n')

        if (song.authors_lyric != null && song.authors_lyric.size > 0) sb.append(TextUtils.join("; ", song.authors_lyric)).append('\n')
        if (song.authors_music != null && song.authors_music.size > 0) sb.append(TextUtils.join("; ", song.authors_music)).append('\n')
        if (song.tune != null) sb.append(song.tune.toUpperCase(Locale.getDefault())).append('\n')
        sb.append('\n')

        if (song.scriptureReferences != null) sb.append(renderScriptureReferences(null, song.scriptureReferences)).append('\n')

        if (song.keySignature != null) sb.append(song.keySignature).append('\n')
        if (song.timeSignature != null) sb.append(song.timeSignature).append('\n')
        sb.append('\n')

        for (i in song.lyrics.indices) {
            val lyric = song.lyrics[i]

            if (song.lyrics.size > 1 || lyric.caption != null) { // otherwise, only lyric and has no name
                if (lyric.caption != null) {
                    sb.append(lyric.caption).append('\n')
                } else {
                    sb.append(getString(R.string.sn_lyric_version_version, (i + 1).toString())).append('\n')
                }
            }

            var verse_normal_no = 0
            for (verse in lyric.verses) {
                if (verse.kind == VerseKind.NORMAL) {
                    verse_normal_no++
                }

                var skipPad = false
                if (verse.kind == VerseKind.REFRAIN) {
                    sb.append(getString(R.string.sn_lyric_refrain_marker)).append('\n')
                } else {
                    sb.append(String.format(Locale.US, "%2d: ", verse_normal_no))
                    skipPad = true
                }

                for (line in verse.lines) {
                    if (!skipPad) {
                        sb.append("    ")
                    } else {
                        skipPad = false
                    }
                    sb.append(line).append("\n")
                }
                sb.append('\n')
            }
            sb.append('\n')
        }
        return sb
    }

    /**
     * Convert scripture ref lines like
     * B1.C1.V1-B2.C2.V2; B3.C3.V3 to:
     * <a href="protocol:B1.C1.V1-B2.C2.V2">Book 1 c1:v1-v2</a>; <a href="protocol:B3.C3.V3>Book 3 c3:v3</a>
     *
     * @param protocol null to output text
     * @param line scripture ref in osis
     */
    private fun renderScriptureReferences(protocol: String?, line: String?): String {
        if (line.isNullOrBlank()) return ""

        val sb = StringBuilder()

        val ranges = line.split("\\s*;\\s*".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (range in ranges) {
            val osisIds = if (range.indexOf('-') >= 0) {
                range.split("\\s*-\\s*".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            } else {
                arrayOf(range)
            }

            if (osisIds.size == 1) {
                if (sb.isNotEmpty()) {
                    sb.append("; ")
                }

                val osisId = osisIds[0]
                val readable = osisIdToReadable(line, osisId, null, null)
                if (readable != null) {
                    appendScriptureReferenceLink(sb, protocol, osisId, readable)
                }
            } else if (osisIds.size == 2) {
                if (sb.isNotEmpty()) {
                    sb.append("; ")
                }

                val bcv = intArrayOf(-1, 0, 0)

                val osisId0 = osisIds[0]
                val readable0 = osisIdToReadable(line, osisId0, null, bcv)
                val osisId1 = osisIds[1]
                val readable1 = osisIdToReadable(line, osisId1, bcv, null)
                if (readable0 != null && readable1 != null) {
                    appendScriptureReferenceLink(sb, protocol, "$osisId0-$osisId1", "$readable0-$readable1")
                }
            }
        }

        return sb.toString()
    }

    private fun appendScriptureReferenceLink(sb: StringBuilder, protocol: String?, osisId: String, readable: String) {
        if (protocol != null) {
            sb.append("<a href='")
            sb.append(protocol)
            sb.append(':')
            sb.append(osisId)
            sb.append("'>")
        }
        sb.append(readable)
        if (protocol != null) {
            sb.append("</a>")
        }
    }

    /**
     * @param compareWithRangeStart if this is the second part of a range, set this to non-null, with [0] is bookId and [1] chapter_1.
     * @param outBcv if not null and length is >= 3, will be filled with parsed bcv
     */
    private fun osisIdToReadable(line: String, osisId: String, compareWithRangeStart: IntArray?, outBcv: IntArray?): String? {
        var res: String? = null

        val parts = osisId.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size != 2 && parts.size != 3) {
            AppLog.w(TAG, "osisId invalid: $osisId in $line")
        } else {
            val bookName = parts[0]
            val chapter_1 = Integer.parseInt(parts[1])
            val verse_1 = if (parts.size < 3) 0 else Integer.parseInt(parts[2])

            val bookId = OsisBookNames.osisBookNameToBookId(bookName)

            if (outBcv != null && outBcv.size >= 3) {
                outBcv[0] = bookId
                outBcv[1] = chapter_1
                outBcv[2] = verse_1
            }

            if (bookId < 0) {
                AppLog.w(TAG, "osisBookName invalid: $bookName in $line")
            } else {
                val book = S.activeVersion().getBook(bookId)

                if (book != null) {
                    var full = true
                    if (compareWithRangeStart != null) {
                        if (compareWithRangeStart[0] == bookId) {
                            if (compareWithRangeStart[1] == chapter_1) {
                                res = verse_1.toString()
                                full = false
                            } else {
                                res = "$chapter_1:$verse_1"
                                full = false
                            }
                        }
                    }

                    if (full) {
                        res = if (verse_1 == 0) book.reference(chapter_1) else book.reference(chapter_1, verse_1)
                    }
                }
            }
        }
        return res
    }

    @JvmOverloads
    fun displaySong(bookName: String?, song: Song?, onCreate: Boolean = false) {
        root.visibility = if (song != null) VISIBLE else GONE
        no_song_data_container.visibility = if (song != null) GONE else VISIBLE

        if (!onCreate) {
            activeMediaController?.reset()
        }

        if (song == null) return

        val handle = leftDrawer.handle
        handle.setBookName(SongBookUtil.escapeSongBookName(bookName))
        handle.setCode(song.code)

        // construct rendition of scripture references
        val scripture_references = renderScriptureReferences(BIBLE_PROTOCOL, song.scriptureReferences)
        templateCustomVars.putString("scripture_references", scripture_references)
        val copyright = SongBookUtil.getCopyright(bookName)
        templateCustomVars.putString("copyright", copyright ?: "")
        templateCustomVars.putString("patch_text_open_link", getString(R.string.patch_text_open_link))

        val ft = supportFragmentManager.beginTransaction()
        ft.replace(R.id.root, SongFragment.create(song, "templates/song.html", templateCustomVars), FRAGMENT_TAG_SONG)
        ft.commitAllowingStateLoss()

        currentBookName = bookName
        currentSong = song

        updateActivityTitle()

        // save latest viewed song
        Preferences.setString(Prefkey.song_last_bookName, bookName)
        Preferences.setString(Prefkey.song_last_code, song.code)

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
                if (resultCode == Activity.RESULT_OK) {
                    val result = SongListActivity.obtainResult(data)
                    if (result != null) {
                        trackSongSelect(result.bookName, result.code)
                        displaySong(result.bookName, S.getSongDb().getSong(result.bookName, result.code))
                        // store this for next search
                        last_searchState = result.last_searchState
                    }
                }
                return
            }
            REQCODE_downloadSongBook -> {
                if (resultCode == Activity.RESULT_OK) {
                    val uri = data?.data
                    if (uri != null) {
                        downloadByAlkitabUri(uri)
                    } else {
                        val input = data?.getStringExtra(AlertDialogActivity.EXTRA_INPUT)
                        if (!input.isNullOrEmpty()) {
                            downloadByAlkitabUri(Uri.parse("alkitab:///addon/download?kind=songbook&type=ser&dataFormatVersion=3&name=_" + Uri.encode(input.toUpperCase(Locale.US))))
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
            MaterialDialog.Builder(this)
                .content("Invalid uri:\n\n$uri")
                .positiveText(R.string.ok)
                .show()
            return
        }

        val dataFormatVersion_s = uri.getQueryParameter("dataFormatVersion")
        val dataFormatVersion: Int
        try {
            dataFormatVersion = Integer.parseInt("" + dataFormatVersion_s)
        } catch (e: NumberFormatException) {
            MaterialDialog.Builder(this)
                .content("Invalid uri:\n\n$uri")
                .positiveText(R.string.ok)
                .show()
            return
        } catch (e: NullPointerException) {
            MaterialDialog.Builder(this).content("Invalid uri:\n\n$uri").positiveText(R.string.ok).show()
            return
        }

        if (!SongBookUtil.isSupportedDataFormatVersion(dataFormatVersion)) {
            MaterialDialog.Builder(this)
                .content("Unsupported data format version: $dataFormatVersion")
                .positiveText(R.string.ok)
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
                val song = S.getSongDb().getFirstSongFromBook(name)
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

    override fun shouldOverrideUrlLoading(client: WebViewClient, view: WebView, url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme
        if ("patchtext" == scheme) {
            val song = currentSong

            if (song != null) {
                // do not proceed if the song is too old
                val updateTime = S.getSongDb().getSongUpdateTime(currentBookName, song.code)
                if (updateTime == 0 || Sqlitil.nowDateTime() - updateTime > 21 * 86400) {
                    MaterialDialog.Builder(this)
                        .content(TextUtils.expandTemplate(getText(R.string.sn_update_book_because_too_old), SongBookUtil.escapeSongBookName(currentBookName)))
                        .positiveText(R.string.sn_update_book_confirm_button)
                        .negativeText(R.string.cancel)
                        .onPositive { _, _ -> updateSongBook() }
                        .show()
                } else {
                    val extraInfo = PatchTextExtraInfoJson()
                    extraInfo.type = "song"
                    extraInfo.bookName = currentBookName
                    extraInfo.code = song.code

                    val songHeader = song.code + " " + song.title + nonullbr(song.title_original) + nonullbr(song.tune) + nonullbr(song.keySignature) + nonullbr(song.timeSignature) + nonullbr(song.authors_lyric) + nonullbr(song.authors_music)
                    val songHtml = SongFragment.songToHtml(song, true)
                    val baseBody = HtmlCompat.fromHtml(songHeader + "\n\n" + songHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    startActivity(PatchTextActivity.createIntent(baseBody, App.getDefaultGson().toJson(extraInfo), null))
                }
            }
            return true
        } else if (BIBLE_PROTOCOL == scheme) {
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
        return false
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

            handle.setOkButtonEnabled(S.getSongDb().songExists(currentBookName, state_tempCode))
            handle.setAButtonEnabled(state_tempCode.length <= 3 && S.getSongDb().songExists(currentBookName, state_tempCode + "A"))
            handle.setBButtonEnabled(state_tempCode.length <= 3 && S.getSongDb().songExists(currentBookName, state_tempCode + "B"))
            handle.setCButtonEnabled(state_tempCode.length <= 3 && S.getSongDb().songExists(currentBookName, state_tempCode + "C"))
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

                val letter = ('A'.toInt() + num - 10).toChar()
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
                    val song = S.getSongDb().getSong(currentBookName, state_tempCode)
                    if (song != null) {
                        trackSongSelect(currentBookName, song.code)
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
        val song = S.getSongDb().getFirstSongFromBook(name)

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
         * (This has to be static to prevent double media player.)
         * The "active" media player, either [midiController] or [exoplayerController].
         * When no song has been examined, this is null.
         */
        var activeMediaController: MediaController? = null

        val midiController = MidiController()
        val exoplayerController = ExoplayerController(App.context)

        var audioDisclaimerAcknowledged = false

        @JvmStatic
        fun createIntent(): Intent {
            return Intent(App.context, SongViewActivity::class.java)
        }

        fun getAudioFilename(bookName: String, code: String): String {
            return String.format(Locale.US, "songs/v2/%s_%s", bookName, code)
        }

        private fun trackSongSelect(bookName: String, code: String) {
            Tracker.trackEvent("song_select",
                FirebaseAnalytics.Param.ITEM_NAME, "$bookName $code",
                FirebaseAnalytics.Param.ITEM_CATEGORY, bookName,
                FirebaseAnalytics.Param.ITEM_VARIANT, code
            )
        }

        fun nonullbr(s: String?): String {
            return if (s == null) "" else "<br/>$s"
        }

        fun nonullbr(s: List<String>?): String {
            return if (s == null || s.isEmpty()) "" else "<br/>$s"
        }
    }
}
