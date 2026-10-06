package yuku.alkitab.base.ac

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.annotation.Keep
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ShareCompat
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.base.BaseLeftDrawerActivity
import yuku.alkitab.base.devotion.ArticleMeidA
import yuku.alkitab.base.devotion.ArticleMorningEveningEnglish
import yuku.alkitab.base.devotion.ArticleRenunganHarian
import yuku.alkitab.base.devotion.ArticleRoc
import yuku.alkitab.base.devotion.ArticleSantapanHarian
import yuku.alkitab.base.devotion.DevotionArticle
import yuku.alkitab.base.devotion.DevotionDownloader
import yuku.alkitab.base.events.AppEvents
import yuku.alkitab.base.settings.SettingsActivity
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Background
import yuku.alkitab.base.util.ClipboardUtil
import yuku.alkitab.base.util.Jumper
import yuku.alkitab.base.widget.CallbackSpan
import yuku.alkitab.base.widget.LeftDrawer
import yuku.alkitab.base.widget.MaterialDialogJavaHelper
import yuku.alkitab.base.widget.TwofingerLinearLayout
import yuku.alkitab.debug.R
import yuku.alkitab.util.Ari
import yuku.alkitabintegration.display.Launcher

open class DevotionActivity : BaseLeftDrawerActivity(), LeftDrawer.Devotion.Listener {
    private val rootListener = object : TwofingerLinearLayout.OnefingerListener() {
        override fun onOnefingerLeft() = bNext_click()
        override fun onOnefingerRight() = bPrev_click()
    }

    override fun bPrev_click() {
        currentDate.time -= 3600 * 24 * 1000
        display()
    }

    override fun bNext_click() {
        currentDate.time += 3600 * 24 * 1000
        display()
    }

    override fun bReload_click() {
        getDownloader().retry(currentKind.sourceName, getDateFormat().format(currentDate))
        display()
    }

    override fun cbKind_itemSelected(kind: DevotionKind) {
        currentKind = kind
        Preferences.setString(Prefkey.devotion_last_kind_name, currentKind.sourceName)
        display()
        startPrefetch(kind)
    }

    override fun getLeftDrawer(): LeftDrawer = devotionDrawer

    enum class DevotionKind(
        @JvmField val sourceName: String,
        @JvmField val title: String,
        @JvmField val subtitle: String,
    ) {
        SH("sh", "Santapan Harian", "Persekutuan Pembaca Alkitab") {
            override fun getArticle(date: String): DevotionArticle = ArticleSantapanHarian(date)
            override fun getShareUrl(format: SimpleDateFormat, date: Date): String =
                "https://www.sabda.org/publikasi/e-sh/print/?edisi=" + getDateFormat().format(date)
        },
        MEID_A("meid-a", "Renungan Pagi", "Charles H. Spurgeon") {
            override fun getArticle(date: String): DevotionArticle = ArticleMeidA(date)
            override fun getShareUrl(format: SimpleDateFormat, date: Date): String =
                "https://alkitab.app/renunganpagi/" + getDateFormat().format(date).substring(4)
        },
        ROC("roc", "My Utmost (B. Indonesia)", "Oswald Chambers") {
            override fun getArticle(date: String): DevotionArticle = ArticleRoc(date)
            override fun getShareUrl(format: SimpleDateFormat, date: Date): String? = null
        },
        RH("rh", "Renungan Harian", "Yayasan Gloria") {
            override fun getArticle(date: String): DevotionArticle = ArticleRenunganHarian(date)
            override fun getShareUrl(format: SimpleDateFormat, date: Date): String =
                "https://www.sabda.org/publikasi/e-rh/print/?edisi=" + getDateFormat().format(date)
            override fun getPrefetchDays(): Int = 3
        },
        ME_EN("me-en", "Morning & Evening", "Charles H. Spurgeon") {
            override fun getArticle(date: String): DevotionArticle = ArticleMorningEveningEnglish(date)
            override fun getShareUrl(format: SimpleDateFormat, date: Date): String =
                "https://www.ccel.org/ccel/spurgeon/morneve.d" + getDateFormat().format(date) + "am.html"
        };

        abstract fun getArticle(date: String): DevotionArticle
        abstract fun getShareUrl(format: SimpleDateFormat, date: Date): String?
        open fun getPrefetchDays(): Int = 15

        companion object {
            @JvmStatic
            fun getByName(name: String?): DevotionKind? = entries.firstOrNull { it.sourceName == name }
        }
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var devotionDrawer: LeftDrawer.Devotion
    private lateinit var root: TwofingerLinearLayout
    internal lateinit var lContent: TextView
    private lateinit var scrollContent: NestedScrollView
    private lateinit var downloadStatus: View
    internal lateinit var lDownloadStatus: TextView
    internal lateinit var bRetry: MaterialButton
    private lateinit var downloadProgress: CircularProgressIndicator
    private var displayedKey: String? = null
    private var renderedKey: String? = null
    private var renderedBody: String? = null
    private var renderSucceeded = false

    // currently shown
    internal lateinit var currentKind: DevotionKind
    internal lateinit var currentDate: Date

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_devotion)

        drawerLayout = findViewById(R.id.drawerLayout)
        devotionDrawer = findViewById(R.id.left_drawer)
        devotionDrawer.configure(this, drawerLayout)
        applySafeAreaPadding(devotionDrawer)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        setupEdgeToEdgeDisplayWithoutBottomInset(toolbar)
        checkNotNull(supportActionBar).apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
        }

        root = findViewById(R.id.root)
        lContent = findViewById(R.id.lContent)
        scrollContent = findViewById(R.id.scrollContent)
        downloadStatus = findViewById(R.id.downloadStatus)
        lDownloadStatus = findViewById(R.id.lDownloadStatus)
        bRetry = findViewById(R.id.bRetry)
        downloadProgress = findViewById(R.id.downloadProgress)
        bRetry.setOnClickListener { bReload_click() }
        applyScrollPastBottomInset(scrollContent)
        root.setTwofingerEnabled(false)
        root.setListener(rootListener)

        val storedKind = DevotionKind.getByName(Preferences.getString(Prefkey.devotion_last_kind_name, DEFAULT_DEVOTION_KIND.sourceName))
        val restoredKind = DevotionKind.getByName(savedInstanceState?.getString("devotionKind"))
        currentKind = restoredKind ?: storedKind ?: DEFAULT_DEVOTION_KIND
        currentDate = Date(savedInstanceState?.getLong("devotionDate", System.currentTimeMillis()) ?: System.currentTimeMillis())
        display()
        startPrefetch(currentKind)
        AppEvents.observeWhileStarted(this, getDownloader().changes) { display() }
    }

    override fun onStart() {
        super.onStart()
        val applied = App.services.uiDimensions.applied()

        // apply background color, and clear window background to prevent overdraw
        getWindow().setBackgroundDrawableResource(android.R.color.transparent)
        root.setBackgroundColor(applied.backgroundColor)
        scrollContent.setBackgroundColor(applied.backgroundColor)
        lDownloadStatus.setTextColor(applied.fontColor)
        bRetry.setTextColor(applied.fontColor)
        bRetry.strokeColor = ColorStateList.valueOf(applied.fontColor)
        downloadProgress.setIndicatorColor(applied.fontColor)

        // text formats
        lContent.setTextColor(applied.fontColor)
        lContent.setTypeface(applied.fontFace, applied.fontBold)
        lContent.setTextSize(TypedValue.COMPLEX_UNIT_DIP, applied.fontSize2dp)
        lContent.setLineSpacing(0f, applied.lineSpacingMult)
        val padding: Rect = SettingsActivity.getPaddingBasedOnPreferences()
        lContent.setPadding(padding.left, padding.top, padding.right, padding.bottom)
        display()
        getWindow().decorView.keepScreenOn = Preferences.getBoolean(getString(R.string.pref_keepScreenOn_key), resources.getBoolean(R.bool.pref_keepScreenOn_default))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("devotionKind", currentKind.sourceName)
        outState.putLong("devotionDate", currentDate.time)
        super.onSaveInstanceState(outState)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menuCopy).isEnabled = renderSucceeded
        menu.findItem(R.id.menuShare).isEnabled = renderSucceeded
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_devotion, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> devotionDrawer.toggleDrawer()
            R.id.menuCopy -> {
                ClipboardUtil.copyToClipboard(currentKind.title + "\n" + lContent.text)
                Snackbar.make(root, R.string.renungan_sudah_disalin, Snackbar.LENGTH_SHORT).show()
            }
            R.id.menuShare -> {
                val shareUrl = currentKind.getShareUrl(getDateFormat(), currentDate)
                ShareCompat.IntentBuilder(this)
                    .setType("text/plain")
                    .setSubject(currentKind.title)
                    .setText(currentKind.title + '\n' + getCurrentDateDisplay() + (shareUrl?.let { "\n$it" } ?: "") + "\n\n" + lContent.text)
                    .setChooserTitle(getString(R.string.bagikan_renungan))
                    .startChooser()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    internal fun display() {
        val date = getDateFormat().format(currentDate)
        val article = App.services.storage.db.tryGetDevotion(currentKind.sourceName, date)
        val key = currentKind.sourceName + ":" + date
        if (key != displayedKey) {
            displayedKey = key
            scrollContent.scrollTo(0, 0)
        }
        getDownloader().select(currentKind.sourceName, date, article == null)
        val state = getDownloader().getState(currentKind.sourceName, date)

        if (article == null) {
            AppLog.d(TAG, "rendering null article")
        } else {
            AppLog.d(TAG, "rendering article name=${article.kind.sourceName} date=${article.date} readyToUse=${article.readyToUse}")
        }

        renderSucceeded = article?.readyToUse == true
        lContent.visibility = if (renderSucceeded) View.VISIBLE else View.GONE
        if (article != null && renderSucceeded && (key != renderedKey || article.body != renderedBody)) {
            renderedKey = key
            renderedBody = article.body
            lContent.setText(article.getContent(verseClickListener), TextView.BufferType.SPANNABLE)
            lContent.linksClickable = true
            lContent.movementMethod = LinkMovementMethod.getInstance()
        } else if (!renderSucceeded) {
            renderedKey = null
            renderedBody = null
            lContent.text = ""
        }

        val waiting = state == DevotionDownloader.State.QUEUED
        val downloading = state == DevotionDownloader.State.DOWNLOADING
        val failed = state == DevotionDownloader.State.FAILED
        val unavailable = !waiting && !downloading && (state == DevotionDownloader.State.UNAVAILABLE || (article != null && !article.readyToUse))
        val showStatus = waiting || downloading || failed || unavailable
        downloadStatus.visibility = if (showStatus) View.VISIBLE else View.GONE
        downloadProgress.visibility = if (waiting || downloading) View.VISIBLE else View.GONE
        bRetry.visibility = if (failed || unavailable) View.VISIBLE else View.GONE
        when {
            waiting -> lDownloadStatus.setText(R.string.devotion_download_queued)
            downloading -> lDownloadStatus.setText(R.string.devotion_downloading)
            failed -> lDownloadStatus.setText(R.string.devotion_download_failed)
            unavailable -> lDownloadStatus.setText(R.string.devotion_unavailable)
        }
        invalidateOptionsMenu()

        // widget texts
        val dateDisplay = getCurrentDateDisplay()
        // action bar
        supportActionBar?.apply {
            title = currentKind.title
            subtitle = dateDisplay
        }
        // drawer texts
        devotionDrawer.handle.apply {
            setDevotionKind(currentKind)
            setDevotionDate(dateDisplay)
        }
    }

    private fun getCurrentDateDisplay(): String = dayOfWeekName(currentDate) + ", " + DateFormat.getDateFormat(this).format(currentDate)

    @Keep
    class PatchTextExtraInfoJson {
        @JvmField var type: String? = null
        @JvmField var kind: String? = null
        @JvmField var date: String? = null
    }

    private val verseClickListener = CallbackSpan.OnClickListener<String> { _, reference ->
        AppLog.d(TAG, "Clicked verse reference inside devotion: $reference")
        if (reference.startsWith("patchtext:")) {
            val referenceUrl = Uri.parse(reference).getQueryParameter("referenceUrl")
            val extraInfo = PatchTextExtraInfoJson().apply {
                type = "devotion"
                kind = currentKind.sourceName
                date = getDateFormat().format(currentDate)
            }
            startActivity(PatchTextActivity.createIntent(lContent.text, App.getDefaultGson().toJson(extraInfo), referenceUrl))
        } else {
            if (reference.startsWith("ari:")) {
                val ari = reference.substring(4).toInt()
                startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(ari))
            } else { // we need to parse it manually by text
                val jumper = Jumper(reference)
                if (!jumper.parseSucceeded) {
                    MaterialDialogJavaHelper.showOkDialog(this, getString(R.string.alamat_tidak_sah_alamat, reference))
                } else {
                    // Make sure references are parsed using Indonesian book names.
                    val bookNames = resources.getStringArray(R.array.standard_book_names_in)
                    val bookIds = IntArray(bookNames.size) { it }
                    val bookId = jumper.getBookId(bookNames, bookIds)
                    val chapter_1 = jumper.chapter
                    val verse_1 = jumper.verse
                    val ari = Ari.encode(bookId, chapter_1, verse_1)
                    val hasRange = jumper.hasRange
                    if (hasRange || verse_1 == 0) {
                        startActivity(Launcher.openAppAtBibleLocation(ari))
                    } else {
                        startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(ari))
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun dayOfWeekName(date: Date): String = getString(WEEKDAY_NAMES_RESIDS[date.day])

    protected open fun getDownloader(): DevotionDownloader = devotionDownloader

    protected open fun startPrefetch(kind: DevotionKind) {
        Background.run { prefetch(kind) }
    }

    companion object {
        private const val TAG = "DevotionActivity"
        @JvmField val devotionDownloader = DevotionDownloader.create()
        private val yyyymmdd = ThreadLocal.withInitial { SimpleDateFormat("yyyyMMdd", Locale.US) }
        @JvmField val DEFAULT_DEVOTION_KIND = DevotionKind.SH
        private val WEEKDAY_NAMES_RESIDS = intArrayOf(R.string.hari_minggu, R.string.hari_senin, R.string.hari_selasa, R.string.hari_rabu, R.string.hari_kamis, R.string.hari_jumat, R.string.hari_sabtu)

        @JvmStatic
        fun createIntent(): Intent = Intent(App.context, DevotionActivity::class.java)

        private fun prefetch(kind: DevotionKind) {
            val today = Date()

            // delete those older than 180 days!
            val deleted = App.services.storage.db.deleteDevotionsWithTouchTimeBefore(Date(today.time - 180 * 86400_000L))
            if (deleted > 0) {
                AppLog.d(TAG, "old devotions deleted: $deleted")
            }
            for (i in 0 until kind.getPrefetchDays()) {
                val date = getDateFormat().format(today)
                if (App.services.storage.db.tryGetDevotion(kind.sourceName, date) == null) {
                    AppLog.d(TAG, "Prefetcher need to get $kind $date")
                    devotionDownloader.addPrefetch(kind.sourceName, date)
                }
                // go to the next day
                today.time += 86400_000L
            }
        }

        private fun getDateFormat(): SimpleDateFormat = checkNotNull(yyyymmdd.get())
    }
}
