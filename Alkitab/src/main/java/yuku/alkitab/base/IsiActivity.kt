package yuku.alkitab.base

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.format.DateFormat
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.URLSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.util.PatternsCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.launch
import me.toptas.fancyshowcase.FancyShowCaseView
import me.toptas.fancyshowcase.listener.DismissListener
import yuku.afw.storage.Preferences
import yuku.alkitab.base.ac.GotoActivity
import yuku.alkitab.base.ac.MarkerListActivity
import yuku.alkitab.base.ac.MarkersActivity
import yuku.alkitab.base.ac.NoteActivity
import yuku.alkitab.base.ac.SearchActivity
import yuku.alkitab.base.ac.base.BaseLeftDrawerActivity
import yuku.alkitab.base.actionmode.RibkaEligibility
import yuku.alkitab.base.actionmode.VerseActionModeActions
import yuku.alkitab.base.actionmode.VerseActionModeController
import yuku.alkitab.base.actionmode.VerseActionModeHost
import yuku.alkitab.base.dialog.ProgressMarkListDialog
import yuku.alkitab.base.dialog.ProgressMarkRenameDialog
import yuku.alkitab.base.dialog.TypeBookmarkDialog
import yuku.alkitab.base.dialog.VersesDialog
import yuku.alkitab.base.dialog.XrefDialog
import yuku.alkitab.base.events.AppEvents
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.model.MVersionDb
import yuku.alkitab.base.settings.SettingsActivity
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Appearances
import yuku.alkitab.base.util.BackForwardListController
import yuku.alkitab.base.util.CurrentReading
import yuku.alkitab.base.util.History
import yuku.alkitab.base.util.InstallationUtil
import yuku.alkitab.base.audio.AudioBarController
import yuku.alkitab.base.audio.AudioCatalogRepository
import yuku.alkitab.base.audio.BibleNeighborResolver
import yuku.alkitab.base.audio.ui.AudioHighlightColor
import yuku.alkitab.base.audio.ui.AudioSourceOption
import yuku.alkitab.base.util.Jumper
import yuku.alkitab.base.util.LidToAri
import yuku.alkitab.base.util.OtherAppIntegration
import yuku.alkitab.base.util.RequestCodes
import yuku.alkitab.base.util.VersionDialogHelper
import yuku.alkitab.base.util.Sqlitil
import yuku.alkitab.base.util.TargetDecoder
import yuku.alkitab.base.util.safeQuery
import yuku.alkitab.base.util.toIntArray
import yuku.alkitab.base.verses.VerseAttributeLoader
import yuku.alkitab.base.verses.VersesController
import yuku.alkitab.base.verses.VersesControllerImpl
import yuku.alkitab.base.verses.VersesDataModel
import yuku.alkitab.base.verses.VersesListeners
import yuku.alkitab.base.verses.VersesUiModel
import yuku.alkitab.base.widget.ActiveSplit1
import yuku.alkitab.base.widget.AriParallelClickData
import yuku.alkitab.base.widget.DictionaryLinkInfo
import yuku.alkitab.base.widget.Floater
import yuku.alkitab.base.widget.FormattedTextRenderer
import yuku.alkitab.base.widget.GotoButton
import yuku.alkitab.base.widget.LabeledSplitHandleButton
import yuku.alkitab.base.widget.LeftDrawer
import yuku.alkitab.base.widget.MaterialDialogAdapterHelper
import yuku.alkitab.base.widget.ParallelClickData
import yuku.alkitab.base.widget.ReaderGestureActions
import yuku.alkitab.base.widget.ReaderGestureHandler
import yuku.alkitab.base.widget.ReaderGestureHost
import yuku.alkitab.base.widget.ReferenceParallelClickData
import yuku.alkitab.base.widget.SplitHandleButton
import yuku.alkitab.base.widget.SplitViewActions
import yuku.alkitab.base.widget.SplitViewHost
import yuku.alkitab.base.widget.SplitViewManager
import yuku.alkitab.base.widget.TextAppearancePanel
import yuku.alkitab.base.widget.TwofingerLinearLayout
import yuku.alkitab.base.widget.VerseInlineLinkSpan
import yuku.alkitab.base.widget.VerseRenderer
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R
import yuku.alkitab.model.Book
import yuku.alkitab.model.Marker
import yuku.alkitab.model.PericopeBlock
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList
import yuku.alkitab.versionmanager.VersionsActivity
import yuku.devoxx.flowlayout.FlowLayout

private const val TAG = "IsiActivity"
private const val EXTRA_verseUrl = "verseUrl"
private const val INSTANCE_STATE_ari = "ari"

class IsiActivity : BaseLeftDrawerActivity(), LeftDrawer.Text.Listener, VerseActionModeHost, VerseActionModeActions, ReaderGestureHost, ReaderGestureActions, SplitViewHost, SplitViewActions {
    override var uncheckVersesWhenActionModeDestroyed = true
    var needsRestart = false // whether this activity needs to be restarted

    private val actionModeController by lazy { VerseActionModeController(this, this) }

    // -- Audio bar (M3) -- thin glue from the activity to the Compose audio bar.
    // The controller binds to BibleAudioService, projects PlaybackState into a
    // UI-shaped flow, and renders the bar inside `R.id.audio_bar`. The host
    // implementation below feeds it the chapter context it needs to label
    // prev/next-chapter buttons and to navigate when the user taps them.
    private val audioBinder: AudioBarController by lazy { AudioBarController(applicationContext) }
    /** Cached overlay color, recomputed when the reading theme changes. */
    private var audioHighlightColorCached: Int = 0

    // --- VerseActionModeHost overrides ---
    // Thin read-only accessors so the controller can reach the Activity's state
    // without pulling the whole Activity type into its API.
    override val activity: androidx.appcompat.app.AppCompatActivity get() = this
    override val activeSplit0Book: Book get() = activeSplit0.book
    override val activeSplit0Version: Version get() = activeSplit0.version
    override val activeSplit0VersionId: String get() = activeSplit0.versionId
    override val activeSplit0MVersion: MVersion get() = activeSplit0.mv
    override val activeSplit1Version: Version? get() = activeSplit1?.version
    override val activeSplit1VersionId: String? get() = activeSplit1?.versionId
    override val activeSplit1MVersion: MVersion? get() = activeSplit1?.mv
    override fun activeSplit1BookById(bookId: Int): Book? = activeSplit1?.version?.getBook(bookId)
    override val selectedVersesSplit0_1: IntArrayList get() = lsSplit0.getCheckedVerses_1()
    override val selectedVersesSplit1_1: IntArrayList get() = lsSplit1.getCheckedVerses_1()

    // --- VerseActionModeActions overrides ---
    override fun uncheckAllVersesSplit0() { lsSplit0.uncheckAllVerses(true) }
    override fun onActionModeDestroyed() { actionMode = null }
    override fun isAudioAvailableForVerseAction(): Boolean = audioBinder.isAvailable
    override fun playAudioFromVerse(verse_1: Int) { audioBinder.showFromVerse(verse_1) }

    // --- ReaderGestureActions overrides (state is read via ReaderGestureHost below) ---
    override fun onFloaterAriSelected(ari: Int) = jumpToAri(ari)
    override fun goToPreviousChapter() = bLeft_click()
    override fun goToNextChapter() = bRight_click()
    override fun setFullScreenWithDrawerHandle(yes: Boolean) {
        setFullScreen(yes)
        leftDrawer.handle.setFullScreen(yes)
    }

    // --- ReaderGestureHost overrides ---
    // `floater` and `textAppearancePanel` are existing fields, marked with `override`
    // at their declarations below. The two resource-derived values are computed here.
    override val gestureDisplayDensity: Float get() = resources.displayMetrics.density
    override val defaultUkuranHuruf2: Float get() = resources.getInteger(R.integer.pref_ukuranHuruf2_default).toFloat()

    private val gestureHandler by lazy { ReaderGestureHandler(this, this) }

    // --- SplitViewActions overrides (state is read via SplitViewHost; the
    // remaining SplitViewHost properties — `splitRoot`, `splitHandleButton`,
    // `lsSplit0`, `lsSplit1`, `bVersion`, `leftDrawer` — are marked `override`
    // at their declarations below).
    override fun openPrimaryVersionsDialog() = openVersionsDialog()
    override fun loadChapterIntoSplit1(version: Version, versionId: String, book: Book, chapter_1: Int): Boolean {
        uncheckVersesWhenActionModeDestroyed = false
        return try {
            loadChapterToVersesController(contentResolver, lsSplit1, { dataSplit1 = it }, version, versionId, book, chapter_1, chapter_1, true)
        } finally {
            uncheckVersesWhenActionModeDestroyed = true
        }
    }

    override fun setSplit1DataModel(model: VersesDataModel) {
        dataSplit1 = model
    }

    private val splitViewManager by lazy { SplitViewManager(this, this) }

    private lateinit var drawerLayout: DrawerLayout
    override lateinit var leftDrawer: LeftDrawer.Text

    private lateinit var overlayContainer: FrameLayout
    override lateinit var root: ViewGroup
    lateinit var toolbar: Toolbar
    private lateinit var nontoolbar: View
    override lateinit var lsSplit0: VersesController
    override lateinit var lsSplit1: VersesController
    override lateinit var splitRoot: TwofingerLinearLayout
    override lateinit var splitHandleButton: LabeledSplitHandleButton
    private lateinit var bGoto: GotoButton
    private lateinit var bLeft: ImageButton
    private lateinit var bRight: ImageButton
    override lateinit var bVersion: TextView
    override lateinit var floater: Floater
    private lateinit var backForwardListController: BackForwardListController<ImageButton, ImageButton>
    private var fullscreenReferenceToast: Toast? = null

    override var dataSplit0 = VersesDataModel.EMPTY
        set(value) {
            field = value
            lsSplit0.versesDataModel = value
        }

    override var dataSplit1 = VersesDataModel.EMPTY
        set(value) {
            field = value
            lsSplit1.versesDataModel = value
        }

    private var uiSplit0 = VersesUiModel.EMPTY
        set(value) {
            field = value
            lsSplit0.versesUiModel = value
        }

    private var uiSplit1 = VersesUiModel.EMPTY
        set(value) {
            field = value
            lsSplit1.versesUiModel = value
        }

    override var chapter_1 = 0
    private var fullScreen = false

    val history get() = History

    override var actionMode: ActionMode? = null
    private var dictionaryMode = false
    override var textAppearancePanel: TextAppearancePanel? = null

    /**
     * The following "esvsbasal" thing is a personal thing by yuku that doesn't matter to anyone else.
     * Please ignore it and leave it intact.
     */
    override val hasEsvsbAsal by lazy {
        try {
            packageManager.getApplicationInfo("yuku.esvsbasal", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Container class to make sure that the fields are changed simultaneously.
     */
    data class ActiveSplit0(
        val mv: MVersion,
        val version: Version,
        val versionId: String,
        val book: Book,
    )

    private var _activeSplit0: ActiveSplit0? = null

    /**
     * The primary version, ensured to be always non-null.
     */
    var activeSplit0: ActiveSplit0
        get() {
            val _activeSplit0 = this._activeSplit0
            if (_activeSplit0 != null) {
                return _activeSplit0
            }
            val version = App.services.versions.activeVersion()
            val new = ActiveSplit0(
                mv = App.services.versions.activeMVersion(),
                version = version,
                versionId = App.services.versions.activeVersionId(),
                book = version.firstBook
            )
            this._activeSplit0 = new
            return new
        }
        set(value) {
            _activeSplit0 = value
        }

    /**
     * The secondary version. Read-only here; ownership lives on [splitViewManager]
     * (see REM-08). Set to null when the secondary version is not opened.
     */
    val activeSplit1: ActiveSplit1? get() = splitViewManager.activeSplit1

    private val parallelListener: (data: ParallelClickData) -> Unit = { data ->
        if (data is ReferenceParallelClickData) {
            jumpTo(data.reference)
        } else if (data is AriParallelClickData) {
            val ari = data.ari
            jumpToAri(ari)
        }
    }

    private val dictionaryListener: (DictionaryLinkInfo) -> Unit = fun(data: DictionaryLinkInfo) {
        val cr = contentResolver
        val uri = "content://org.sabda.kamus.provider/define".toUri().buildUpon()
            .appendQueryParameter("key", data.key)
            .appendQueryParameter("mode", "snippet")
            .build()

        try {
            cr.safeQuery(uri, null, null, null, null) ?: run {
                OtherAppIntegration.askToInstallDictionary(this)
                return
            }
        } catch (_: Exception) {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.dict_no_results)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }.use { c ->
            if (c.count == 0) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.dict_no_results)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } else {
                c.moveToNext()
                val rendered = HtmlCompat.fromHtml(c.getString(c.getColumnIndexOrThrow("definition")), HtmlCompat.FROM_HTML_MODE_COMPACT)
                val sb = rendered as? SpannableStringBuilder ?: SpannableStringBuilder(rendered)

                // remove links
                for (span in sb.getSpans(0, sb.length, URLSpan::class.java)) {
                    sb.removeSpan(span)
                }

                MaterialAlertDialogBuilder(this)
                    .setTitle(data.orig_text)
                    .setMessage(sb)
                    .setPositiveButton(R.string.dict_open_full) { _, _ ->
                        val intent = Intent("org.sabda.kamus.action.VIEW")
                            .putExtra("key", data.key)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                        try {
                            startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            OtherAppIntegration.askToInstallDictionary(this@IsiActivity)
                        }
                    }
                    .show()
            }
        }
    }

    private val pinDropListener = object : VersesController.PinDropListener() {
        override fun onPinDropped(presetId: Int, ari: Int) {

            val progressMark = App.services.storage.db.getProgressMarkByPresetId(presetId)
            if (progressMark != null) {
                progressMark.ari = ari
                progressMark.modifyTime = Date()
                App.services.storage.db.insertOrUpdateProgressMark(progressMark)
            }

            AppEvents.emitAttributeMapChanged()
        }
    }

    private val lsSplit0_selectedVerses = object : VersesController.SelectedVersesListener() {
        override fun onSomeVersesSelected(verses_1: IntArrayList) {
            if (activeSplit1 != null) {
                // synchronize the selection with the split view
                lsSplit1.checkVerses(verses_1, false)
            }

            if (actionMode == null) {
                actionMode = startSupportActionMode(actionModeController)
            }

            actionMode?.invalidate()
        }

        override fun onNoVersesSelected() {
            if (activeSplit1 != null) {
                // synchronize the selection with the split view
                lsSplit1.uncheckAllVerses(false)
            }

            actionMode?.finish()
            actionMode = null
        }
    }

    private val lsSplit1_selectedVerses = object : VersesController.SelectedVersesListener() {
        override fun onSomeVersesSelected(verses_1: IntArrayList) {
            // synchronize the selection with the main view
            lsSplit0.checkVerses(verses_1, true)
        }

        override fun onNoVersesSelected() {
            lsSplit0.uncheckAllVerses(true)
        }
    }

    private val lsSplit0_verseScroll = object : VersesController.VerseScrollListener() {
        override fun onVerseScroll(isPericope: Boolean, verse_1: Int, prop: Float) {
            if (activeSplit1 == null) return
            if (isPericope) {
                lsSplit1.scrollToPericope(verse_1, prop)
            } else {
                lsSplit1.scrollToVerse(verse_1, prop)
            }
        }

        override fun onScrollToTop() {
            if (activeSplit1 != null) {
                lsSplit1.scrollToTop()
            }
        }
    }

    private val lsSplit1_verseScroll = object : VersesController.VerseScrollListener() {
        override fun onVerseScroll(isPericope: Boolean, verse_1: Int, prop: Float) {
            if (isPericope) {
                lsSplit0.scrollToPericope(verse_1, prop)
            } else {
                lsSplit0.scrollToVerse(verse_1, prop)
            }
        }

        override fun onScrollToTop() {
            lsSplit0.scrollToTop()
        }
    }


    data class IntentResult(
        val ari: Int,
        val selectVerse: Boolean = false,
        val selectVerseCount: Int = 0,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLog.d(TAG, "@@onCreate start")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_isi)
        AppLog.d(TAG, "@@onCreate setCV")

        drawerLayout = findViewById(R.id.drawerLayout)
        leftDrawer = findViewById(R.id.left_drawer)
        leftDrawer.configure(this, drawerLayout)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
            setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
        }

        nontoolbar = findViewById(R.id.nontoolbar)

        bGoto = findViewById(R.id.bGoto)
        bLeft = findViewById(R.id.bLeft)
        bRight = findViewById(R.id.bRight)
        bVersion = findViewById(R.id.bVersion)

        overlayContainer = findViewById(R.id.overlayContainer)
        root = findViewById(R.id.root)
        splitRoot = findViewById(R.id.splitRoot)

        splitHandleButton = findViewById(R.id.splitHandleButton)
        floater = findViewById(R.id.floater)

        // If layout is changed, updateToolbarLocation must be updated as well. This will be called in DEBUG to make sure
        // updateToolbarLocation is also updated when layout is updated.
        if (BuildConfig.DEBUG) {
            if (root.childCount != 3 ||
                root.getChildAt(0).id != R.id.toolbar ||
                root.getChildAt(1).id != R.id.nontoolbar ||
                root.getChildAt(2).id != R.id.audio_bar
            ) {
                throw RuntimeException("Layout changed and this is no longer compatible with updateToolbarLocation")
            }
        }

        updateToolbarLocation()

        splitRoot.setListener(gestureHandler)

        bGoto.setOnClickListener { bGoto_click() }
        bGoto.setOnLongClickListener {
            bGoto_longClick()
            true
        }
        bGoto.setFloaterDragListener(gestureHandler)

        bLeft.setOnClickListener { bLeft_click() }
        bRight.setOnClickListener { bRight_click() }
        bVersion.setOnClickListener { openVersionsDialog() }

        floater.setListener(gestureHandler)

        // listeners
        lsSplit0 = VersesControllerImpl(
            findViewById(R.id.lsSplitView0),
            "lsSplit0",
            VersesDataModel.EMPTY,
            VersesUiModel.EMPTY,
            VersesListeners(
                AttributeListener(), // have to be distinct from lsSplit1
                lsSplit0_selectedVerses,
                lsSplit0_verseScroll,
                parallelListener,
                VerseInlineLinkSpanFactory { lsSplit0 },
                dictionaryListener,
                pinDropListener
            )
        )

        // additional setup for split1
        lsSplit1 = VersesControllerImpl(
            findViewById(R.id.lsSplitView1),
            "lsSplit1",
            VersesDataModel.EMPTY,
            VersesUiModel.EMPTY,
            VersesListeners(
                AttributeListener(), // have to be distinct from lsSplit0
                lsSplit1_selectedVerses,
                lsSplit1_verseScroll,
                parallelListener,
                VerseInlineLinkSpanFactory { lsSplit1 },
                dictionaryListener,
                pinDropListener
            )
        )

        // for splitting
        splitViewManager.installListeners()

        if (BuildConfig.DEBUG) {
            // Runtime assertions: splitRoot must have 3 children;
            // lsSplitView0, splitHandleButton, lsSplitView1 in that order.
            if (splitRoot.childCount != 3) throw RuntimeException("splitRoot does not have correct children")
            if (splitRoot.getChildAt(0) !== splitRoot.findViewById<View>(R.id.lsSplitView0)) throw RuntimeException("splitRoot does not have correct children")
            if (splitRoot.getChildAt(1) !== splitRoot.findViewById<View>(R.id.splitHandleButton)) throw RuntimeException("splitRoot does not have correct children")
            if (splitRoot.getChildAt(2) !== splitRoot.findViewById<View>(R.id.lsSplitView1)) throw RuntimeException("splitRoot does not have correct children")
        }

        backForwardListController = BackForwardListController(
            group = findViewById(R.id.panelBackForwardList),
            onBackButtonNeedUpdate = { button, ari ->
                if (ari == 0) {
                    button.isEnabled = false
                    button.alpha = 0.2f
                } else {
                    button.isEnabled = true
                    button.alpha = 1.0f
                }
            },
            onForwardButtonNeedUpdate = { button, ari ->
                if (ari == 0) {
                    button.isEnabled = false
                    button.alpha = 0.2f
                } else {
                    button.isEnabled = true
                    button.alpha = 1.0f
                }
            },
            onButtonPreMove = { controller ->
                controller.updateCurrentEntry(getCurrentAriForBackForwardList())
            },
            onButtonPostMove = { ari ->
                jumpToAri(
                    ari = ari,
                    updateBackForwardListCurrentEntryWithSource = false,
                    addHistoryEntry = false,
                    callAttention = false
                )
            },
            referenceDisplayer = { ari -> activeSplit0.version.reference(ari) }
        )

        val intentResult = if (savedInstanceState != null) {
            val ari = savedInstanceState.getInt(INSTANCE_STATE_ari)
            if (ari != 0) {
                IntentResult(ari)
            } else {
                null
            }
        } else {
            extractIntent(intent)
        }

        val openingAri: Int
        val selectVerse: Boolean
        val selectVerseCount: Int

        if (intentResult == null) {
            // restore the last (version; book; chapter and verse).
            val lastBookId = Preferences.getInt(Prefkey.lastBookId, 0)
            val lastChapter = Preferences.getInt(Prefkey.lastChapter, 0)
            val lastVerse = Preferences.getInt(Prefkey.lastVerse, 0)
            openingAri = Ari.encode(lastBookId, lastChapter, lastVerse)
            selectVerse = false
            selectVerseCount = 1
            AppLog.d(TAG, "Going to the last: bookId=$lastBookId chapter=$lastChapter verse=$lastVerse")
        } else {
            openingAri = intentResult.ari
            selectVerse = intentResult.selectVerse
            selectVerseCount = intentResult.selectVerseCount
        }

        // Configure active book
        run {
            val activeBook = activeSplit0.version.getBook(Ari.toBook(openingAri))
            if (activeBook != null) {
                activeSplit0 = activeSplit0.copy(book = activeBook)
            } else {
                // can't load last book or bookId 0
                val activeBook2 = activeSplit0.version.firstBook
                if (activeBook2 != null) {
                    activeSplit0 = activeSplit0.copy(book = activeBook2)
                } else {
                    // version failed to load, so books also failed to load. Fallback to internal!
                    val mv = App.services.versions.getMVersionInternal()
                    App.services.versions.setActiveVersion(mv)

                    val version = App.services.versions.activeVersion()
                    val versionId = App.services.versions.activeVersionId()
                    val book = version.firstBook // this is assumed to be never null
                    activeSplit0 = ActiveSplit0(
                        mv = mv,
                        version = version,
                        versionId = versionId,
                        book = book
                    )
                }
            }
        }

        // first display of active version
        displayActiveVersion()

        // load chapter and verse
        display(Ari.toChapter(openingAri), Ari.toVerse(openingAri))

        if (intentResult != null) { // also add to history if not opening the last seen verse
            history.add(openingAri)
        }

        backForwardListController.newEntry(openingAri)

        // load last split version. This must be after load book, chapter, and verse.
        splitViewManager.restoreFromPreferences(Ari.toVerse(openingAri))

        if (selectVerse) {
            for (i in 0 until selectVerseCount) {
                val verse_1 = Ari.toVerse(openingAri) + i
                callAttentionForVerseToBothSplits(verse_1)
            }
        }

        lifecycleScope.launch { AppEvents.attributeMapChanged.collect { reloadBothAttributeMaps() } }
        lifecycleScope.launch { AppEvents.needsRestart.collect { needsRestart = true } }

        // Audio bar (M3): attach the Compose host and wire up the menu refresh.
        // The catalog load is async, so we have to trigger a menu rebuild once
        // it lands — otherwise the toolbar icon shows up only on the second
        // resume of the activity.
        lifecycleScope.launch {
            AudioCatalogRepository.loadCatalog()
            invalidateOptionsMenu()
        }
        val audioBarView: ComposeView = findViewById(R.id.audio_bar)
        audioBinder.attach(audioBarHost, audioBarView)
        lifecycleScope.launch {
            // 100 ms tick rate while playing — avoid invalidateOptionsMenu() on
            // every emission. Most menu refreshes flow through
            // `audioBarVisibilityChanged`; the only periodic refresh we need is
            // when `preparing` flips, so the toolbar can swap the audio icon
            // for the spinner.
            var lastPreparing = false
            audioBinder.uiState.collect { state ->
                val playing = state.playingVersionId
                val split0Match = playing != null && playing == activeSplit0.versionId
                val split1Match = playing != null && playing == activeSplit1?.versionId
                applyAudioHighlightTo(lsSplit0, if (split0Match) state.verse_1 else 0)
                applyAudioHighlightTo(lsSplit1, if (split1Match) state.verse_1 else 0)
                if (state.preparing != lastPreparing) {
                    lastPreparing = state.preparing
                    invalidateOptionsMenu()
                }
            }
        }
        lifecycleScope.launch {
            AppEvents.activeVersionChanged.collect {
                audioBinder.onActiveVersionChanged()
                invalidateOptionsMenu()
            }
        }

        AppLog.d(TAG, "@@onCreate end")
    }

    private fun applyAudioHighlightTo(controller: VersesController, verse_1: Int) {
        if (verse_1 == 0) {
            controller.setAudioHighlight(0, 0)
            return
        }
        if (audioHighlightColorCached == 0) {
            audioHighlightColorCached = AudioHighlightColor.pickHighlightColor(
                readingBackground = App.services.uiDimensions.applied().backgroundColor,
                verseTextColor = App.services.uiDimensions.applied().fontColor,
            )
        }
        controller.setAudioHighlight(verse_1, audioHighlightColorCached)
    }

    /**
     * The audio bar's view of `IsiActivity`. Reads the activity's current
     * book/chapter/version and translates chapter-nav taps back into the
     * existing `display(...)` flow.
     */
    private val audioBarHost = object : AudioBarController.Host {
        override fun audioCurrentBook(): Book = activeSplit0.book
        override fun audioCurrentChapter1(): Int = chapter_1
        override fun audioVisibleVersionIds(): List<String> = listOfNotNull(
            activeSplit0.versionId,
            activeSplit1?.versionId,
        )

        override fun audioAvailableSources(): List<AudioSourceOption> = buildList {
            if (AudioCatalogRepository.isAudioAvailable(activeSplit0.versionId)) {
                add(AudioSourceOption(activeSplit0.versionId, activeSplit0.version.shortName))
            }
            activeSplit1?.let { s1 ->
                if (AudioCatalogRepository.isAudioAvailable(s1.versionId)) {
                    add(AudioSourceOption(s1.versionId, s1.version.shortName))
                }
            }
        }

        override fun audioBookInVersion(versionId: String, bookId: Int): Book? {
            val s1 = activeSplit1
            return when {
                versionId == activeSplit0.versionId -> activeSplit0.version.getBook(bookId)
                s1 != null && versionId == s1.versionId -> s1.version.getBook(bookId)
                else -> null
            }
        }

        override fun audioNeighborChapter(versionId: String, direction: Int): Pair<Book, Int>? {
            val s1 = activeSplit1
            val version = when {
                versionId == activeSplit0.versionId -> activeSplit0.version
                s1 != null && versionId == s1.versionId -> s1.version
                else -> return null
            }
            // chapter_1 belongs to split0's reader pane; resolve neighbor against that book id even when audio runs on split1.
            return BibleNeighborResolver.neighbor(
                version,
                activeSplit0.book.bookId,
                chapter_1,
                direction,
            )
        }

        override fun audioDisplayChapter(book: Book, chapter_1: Int) {
            // Switch book if needed - display() only retargets chapter
            // within the current book, so update activeSplit0 first. display()
            // refreshes the goto-button text itself.
            if (book.bookId != activeSplit0.book.bookId) {
                activeSplit0 = activeSplit0.copy(book = book)
            }
            display(chapter_1, 1, true)
        }

        override fun audioBarVisibilityChanged(visible: Boolean) {
            invalidateOptionsMenu()
        }
    }

    private fun callAttentionForVerseToBothSplits(verse_1: Int) {
        lsSplit0.callAttentionForVerse(verse_1)
        lsSplit1.callAttentionForVerse(verse_1)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(INSTANCE_STATE_ari, Ari.encode(activeSplit0.book.bookId, chapter_1, getVerse_1BasedOnScrolls()))
    }

    /**
     * @return non-null if the intent is handled by any of the intent handler (e.g. VIEW)
     */
    private fun extractIntent(intent: Intent): IntentResult? {
        return tryGetIntentResultFromView(intent)
    }

    /**
     * did we get here from VIEW intent?
     */
    private fun tryGetIntentResultFromView(intent: Intent): IntentResult? {
        if (intent.action != "yuku.alkitab.action.VIEW") return null

        val selectVerse = intent.getBooleanExtra("selectVerse", false)
        val selectVerseCount = intent.getIntExtra("selectVerseCount", 1)

        return when {
            intent.hasExtra("ari") -> {
                val ari = intent.getIntExtra("ari", 0)
                if (ari != 0) {
                    IntentResult(ari, selectVerse, selectVerseCount)
                } else {
                    null
                }
            }

            intent.hasExtra("lid") -> {
                val lid = intent.getIntExtra("lid", 0)
                val ari = LidToAri.lidToAri(lid)
                if (ari != 0) {
                    jumpToAri(ari)
                    IntentResult(ari, selectVerse, selectVerseCount)
                } else {
                    null
                }
            }

            else -> null
        }
    }

    private fun getCurrentAriForBackForwardList(): Int {
        val bookId = activeSplit0.book.bookId
        val chapter_1 = chapter_1
        val verse_1 = getVerse_1BasedOnScrolls()
        return Ari.encode(bookId, chapter_1, verse_1)
    }

    private fun updateBackForwardListCurrentEntry(ari: Int = getCurrentAriForBackForwardList()) {
        if (ari != 0) {
            backForwardListController.updateCurrentEntry(ari)
        }
    }

    /**
     * Try to get the verse_1 based on split0, when failed, try to get it from the split1.
     */
    override fun getVerse_1BasedOnScrolls(): Int {
        val split0verse_1 = lsSplit0.getVerse_1BasedOnScroll()
        if (split0verse_1 != 0) return split0verse_1

        val split1verse_1 = lsSplit1.getVerse_1BasedOnScroll()
        if (split1verse_1 != 0) return split1verse_1

        return 1 // default value for verse_1
    }

    override fun loadVersion(mv: MVersion) {
        try {
            val version = mv.version ?: throw RuntimeException() // caught below

            // we already have some other version loaded, so make the new version open the same book
            val bookId = activeSplit0.book.bookId

            // Set globally
            App.services.versions.setActiveVersion(mv)

            // If a book is not found, get any book
            val book = version.getBook(bookId) ?: version.firstBook
            activeSplit0 = ActiveSplit0(
                mv = mv,
                version = version,
                versionId = App.services.versions.activeVersionId(),
                book = book
            )

            displayActiveVersion()

            display(chapter_1, getVerse_1BasedOnScrolls(), false)

            AppEvents.emitActiveVersionChanged()
        } catch (e: Throwable) { // so we don't crash on the beginning of the app
            AppLog.e(TAG, "Error opening main version", e)

            MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.version_error_opening, mv.longName))
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    private fun displayActiveVersion() {
        bVersion.text = activeSplit0.version.initials
        splitHandleButton.setLabel1("\u25b2 ${activeSplit0.version.initials}")
    }

    private fun consumeKey(keyCode: Int): Boolean {
        // Handle dpad left/right, this always goes to prev/next chapter.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            bLeft_click()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            bRight_click()
            return true
        }

        // Handle volume up/down, the effect changes based on preferences.
        val pressResult = consumeUpDownKey(lsSplit0, keyCode)

        if (pressResult is VersesController.PressResult.Left) {
            bLeft_click()
            return true
        }

        if (pressResult is VersesController.PressResult.Right) {
            bRight_click()
            return true
        }

        if (pressResult is VersesController.PressResult.Consumed) {
            if (activeSplit1 != null) {
                lsSplit1.scrollToVerse(pressResult.targetVerse_1)
            }
            return true
        }

        return false
    }

    private fun consumeUpDownKey(versesController: VersesController, originalKeyCode: Int): VersesController.PressResult {
        var keyCode = originalKeyCode
        val volumeButtonsForNavigation = Preferences.getString(R.string.pref_volumeButtonNavigation_key, R.string.pref_volumeButtonNavigation_default)
        if (volumeButtonsForNavigation == "pasal" /* chapter */) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return VersesController.PressResult.Left
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                return VersesController.PressResult.Right
            }
        } else if (volumeButtonsForNavigation == "ayat" /* verse */) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) keyCode = KeyEvent.KEYCODE_DPAD_DOWN
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) keyCode = KeyEvent.KEYCODE_DPAD_UP
        } else if (volumeButtonsForNavigation == "page") {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return versesController.pageDown()
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                return versesController.pageUp()
            }
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            return versesController.verseDown()
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            return versesController.verseUp()
        }

        return VersesController.PressResult.Nop
    }

    /**
     * Jump to a given verse reference in string format.
     *
     * If successful, the destination will be added to history.
     */
    private fun jumpTo(reference: String) {
        if (reference.trim().isEmpty()) return

        AppLog.d(TAG, "going to jump to $reference")

        val jumper = Jumper(reference)
        if (!jumper.parseSucceeded) {
            MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.alamat_tidak_sah_alamat, reference))
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        val bookId = jumper.getBookId(activeSplit0.version.consecutiveBooks)
        val selected = if (bookId != -1) {
            activeSplit0.version.getBook(bookId) ?: activeSplit0.book // not avail, just fallback
        } else {
            activeSplit0.book
        }

        updateBackForwardListCurrentEntry()

        // set book
        activeSplit0 = activeSplit0.copy(book = selected)

        val chapter = jumper.chapter
        val verse = jumper.verse
        val ari_cv = if (chapter == -1 && verse == -1) {
            display(1, 1)
        } else {
            display(chapter, verse)
        }

        // Add target ari to history
        val target_ari = Ari.encode(selected.bookId, ari_cv)
        history.add(target_ari)
        backForwardListController.newEntry(target_ari)
    }

    /**
     * Jump to a given ari.
     *
     * If successful, the destination will be added to history.
     */
    fun jumpToAri(
        ari: Int,
        updateBackForwardListCurrentEntryWithSource: Boolean = true,
        addHistoryEntry: Boolean = true,
        callAttention: Boolean = true,
    ) {
        if (ari == 0) return

        val bookId = Ari.toBook(ari)
        val book = activeSplit0.version.getBook(bookId)

        if (book == null) {
            AppLog.w(TAG, "bookId=$bookId not found for ari=$ari")
            return
        }

        if (updateBackForwardListCurrentEntryWithSource) {
            updateBackForwardListCurrentEntry()
        }

        activeSplit0 = activeSplit0.copy(book = book)
        val ari_cv = display(Ari.toChapter(ari), Ari.toVerse(ari))

        // Add target ari to history
        if (addHistoryEntry) {
            history.add(ari)
            backForwardListController.newEntry(ari)
        }

        // call attention to the verse only if the displayed verse is equal to the requested verse
        if (callAttention && ari == Ari.encode(activeSplit0.book.bookId, ari_cv)) {
            callAttentionForVerseToBothSplits(Ari.toVerse(ari))
        }
    }

    override fun applyPreferences() {
        // make sure S applied variables are set first
        App.services.uiDimensions.recalculate()

        // apply background color, and clear window background to prevent overdraw
        window.setBackgroundDrawableResource(android.R.color.transparent)
        val backgroundColor = App.services.uiDimensions.applied().backgroundColor
        root.setBackgroundColor(backgroundColor)

        // scrollbar must be visible!
        val thumb = if (ColorUtils.calculateLuminance(backgroundColor) > 0.5) {
            ActivityCompat.getDrawable(this, R.drawable.scrollbar_handle_material_for_light)
        } else {
            ActivityCompat.getDrawable(this, R.drawable.scrollbar_handle_material_for_dark)
        }

        if (thumb != null) {
            lsSplit0.setViewScrollbarThumb(thumb)
            lsSplit1.setViewScrollbarThumb(thumb)
        }

        fun calculateTextSizeMult(versionId: String?): Float {
            return if (versionId == null) 1f else App.services.storage.db.getPerVersionSettings(versionId).fontSizeMultiplier
        }

        // necessary
        val isVerseNumberShown = Preferences.getBoolean(R.string.pref_verseNumberIsShown_key, R.bool.pref_verseNumberIsShown_default)
        uiSplit0 = uiSplit0.copy(
            textSizeMult = calculateTextSizeMult(activeSplit0.versionId),
            isVerseNumberShown = isVerseNumberShown
        )
        uiSplit1 = uiSplit1.copy(
            textSizeMult = calculateTextSizeMult(activeSplit1?.versionId),
            isVerseNumberShown = isVerseNumberShown
        )

        val useSmallerHorizontalPadding = when (activeSplit1) {
            null -> false
            else -> splitHandleButton.orientation == SplitHandleButton.Orientation.vertical
        }

        lsSplit0.setViewPadding(SettingsActivity.getPaddingBasedOnPreferences(useSmallerHorizontalPadding))
        lsSplit1.setViewPadding(SettingsActivity.getPaddingBasedOnPreferences(useSmallerHorizontalPadding))
    }

    override fun onStop() {
        super.onStop()

        Preferences.withTransaction {
            Preferences.setInt(Prefkey.lastBookId, activeSplit0.book.bookId)
            Preferences.setInt(Prefkey.lastChapter, chapter_1)
            Preferences.setInt(Prefkey.lastVerse, getVerse_1BasedOnScrolls())
            Preferences.setString(Prefkey.lastVersionId, activeSplit0.versionId)
            splitViewManager.saveToPreferences()
        }

        history.save()
    }

    override fun onStart() {
        super.onStart()

        applyPreferences()

        window.decorView.keepScreenOn = Preferences.getBoolean(R.string.pref_keepScreenOn_key, R.bool.pref_keepScreenOn_default)

        if (needsRestart) {
            needsRestart = false
            recreate()
        }
        // Re-resolve the audio overlay color in case the user changed the
        // reading theme via the textAppearancePanel while we were stopped.
        audioHighlightColorCached = 0
    }

    override fun onDestroy() {
        // Release the activity-side bindings; the service itself stays alive
        // if audio is playing (M4 lock-screen behavior).
        audioBinder.detach()
        super.onDestroy()
    }

    override fun onBackPressed() {
        when {
            textAppearancePanel != null -> {
                textAppearancePanel?.hide()
                textAppearancePanel = null
            }

            fullScreen -> {
                setFullScreen(false)
                leftDrawer.handle.setFullScreen(false)
            }

            else -> {
                super.onBackPressed()
            }
        }
    }

    private fun bGoto_click() {
        val r = {
            startActivityForResult(GotoActivity.createIntent(activeSplit0.book.bookId, this.chapter_1, getVerse_1BasedOnScrolls()), RequestCodes.FromActivity.Goto)
        }

        if (!Preferences.getBoolean(Prefkey.history_button_understood, false) && history.size > 0) {

            FancyShowCaseView.Builder(this)
                .focusOn(bGoto)
                .title(getString(R.string.goto_button_history_tip))
                .enableAutoTextPosition()
                .dismissListener(object : DismissListener {
                    override fun onDismiss(id: String?) {
                        Preferences.setBoolean(Prefkey.history_button_understood, true)
                        r()
                    }

                    override fun onSkipped(id: String?) = Unit
                })
                .closeOnTouch(true)
                .build()
                .show()

        } else {
            r()
        }
    }

    private fun bGoto_longClick() {
        if (history.size > 0) {
            MaterialDialogAdapterHelper.showDialogWithAdapter(this, HistoryAdapter())
            Preferences.setBoolean(Prefkey.history_button_understood, true)
        } else {
            Snackbar.make(root, R.string.recentverses_not_available, Snackbar.LENGTH_SHORT).show()
        }
    }

    class HistoryEntryHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text1: TextView = itemView.findViewById(android.R.id.text1)
    }

    inner class HistoryAdapter : MaterialDialogAdapterHelper.Adapter() {
        private val timeFormat = DateFormat.getTimeFormat(this@IsiActivity)
        private val mediumDateFormat = DateFormat.getMediumDateFormat(this@IsiActivity)

        private val thisCreatorId = InstallationUtil.getInstallationId()
        private var defaultTextColor = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val textView = layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
            defaultTextColor = textView.currentTextColor
            return HistoryEntryHolder(textView)
        }

        override fun onBindViewHolder(_holder_: RecyclerView.ViewHolder, position: Int) {
            val holder = _holder_ as HistoryEntryHolder

            run {
                val entry = history.getEntry(position)
                holder.text1.text = buildSpannedString {
                    append(activeSplit0.version.reference(entry.ari))
                    append("  ")
                    inSpans(ForegroundColorSpan(0xffaaaaaaL.toInt()), RelativeSizeSpan(0.7f)) {
                        this.append(formatTimestamp(entry.timestamp))
                    }
                }


                if (thisCreatorId == entry.creator_id) {
                    holder.text1.setTextColor(defaultTextColor)
                } else {
                    holder.text1.setTextColor(ResourcesCompat.getColor(resources, R.color.escape, theme))
                }
            }

            holder.itemView.setOnClickListener {
                dismissDialog()

                jumpToAri(history.getEntry(holder.bindingAdapterPosition).ari)
            }
        }

        private fun formatTimestamp(timestamp: Long): CharSequence {
            run {
                val now = System.currentTimeMillis()
                val delta = now - timestamp
                if (delta <= 200000) {
                    return getString(R.string.recentverses_just_now)
                } else if (delta <= 3600000) {
                    return getString(R.string.recentverses_min_plural_ago, (delta / 60000.0).roundToLong().toString())
                }
            }

            run {
                val now = GregorianCalendar.getInstance()
                val that = GregorianCalendar.getInstance()
                that.timeInMillis = timestamp
                if (now.get(Calendar.YEAR) == that.get(Calendar.YEAR)) {
                    if (now.get(Calendar.DAY_OF_YEAR) == that.get(Calendar.DAY_OF_YEAR)) {
                        return getString(R.string.recentverses_today_time, timeFormat.format(that.time))
                    } else if (now.get(Calendar.DAY_OF_YEAR) == that.get(Calendar.DAY_OF_YEAR) + 1) {
                        return getString(R.string.recentverses_yesterday_time, timeFormat.format(that.time))
                    }
                }

                return mediumDateFormat.format(that.time)
            }
        }

        override fun getItemCount(): Int {
            return history.size
        }
    }

    private fun buildMenu(menu: Menu) {
        menu.clear()
        menuInflater.inflate(R.menu.activity_isi, menu)

        // Audio bar (M3): hide the icon when none of the visible versions
        // have audio, and swap to the active variant (small accent dot in the
        // upper-end corner) while the bar is open so the user can tell at a
        // glance that an audio session is engaged. Both drawables are 24dp
        // — same toolbar slot, no reflow on the swap. While the player is
        // preparing, hide the icon entirely and reveal the toolbar spinner
        // (Kidung pattern) so the user has a single source of "I tapped, it's
        // working on it" feedback in the activity chrome.
        val menuAudio = menu.findItem(R.id.menuAudio)
        val preparing = audioBinder.isPreparing
        if (menuAudio != null) {
            menuAudio.isVisible = audioBinder.isAvailable && !preparing
            menuAudio.setIcon(
                if (audioBinder.isBarVisible) R.drawable.ic_audio_active
                else R.drawable.ic_audio
            )
        }
        toolbar.findViewById<View?>(R.id.audio_progress_circular)?.visibility =
            if (preparing) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        buildMenu(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (menu != null) {
            buildMenu(menu)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                leftDrawer.toggleDrawer()
                return true
            }

            R.id.menuSearch -> {
                menuSearch_click()
                return true
            }

            R.id.menuAudio -> {
                audioBinder.toggle()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    fun setFullScreen(yes: Boolean) {
        if (fullScreen == yes) return // no change

        val decorView = window.decorView

        if (yes) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            supportActionBar?.hide()
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            supportActionBar?.show()
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }

        fullScreen = yes

        updateToolbarLocation()
    }

    private fun updateToolbarLocation() {
        // 3 kinds of possible layout:
        // - fullscreen
        // - not fullscreen, toolbar at bottom
        // - not fullscreen, toolbar at top

        // root contains 3 children: toolbar, nontoolbar, and the audio bar.
        // The audio bar always sits directly below the content (above the
        // bottom-anchored verse-nav toolbar when that mode is enabled), so
        // the order varies with the toolbar-location preference.

        if (!fullScreen) {
            val audioBar = root.requireViewById<View>(R.id.audio_bar)
            root.removeView(toolbar)
            root.removeView(nontoolbar)
            root.removeView(audioBar)

            if (Preferences.getBoolean(R.string.pref_bottomToolbarOnText_key, R.bool.pref_bottomToolbarOnText_default)) {
                root.addView(nontoolbar)
                root.addView(audioBar)
                root.addView(toolbar)
            } else {
                root.addView(toolbar)
                root.addView(nontoolbar)
                root.addView(audioBar)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus && fullScreen) {
            val decorView = window.decorView
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE
        }
    }

    private fun setShowTextAppearancePanel(yes: Boolean) {
        if (!yes) {
            textAppearancePanel?.hide()
            textAppearancePanel = null
            return
        }

        if (textAppearancePanel == null) { // not showing yet
            textAppearancePanel = TextAppearancePanel(
                this,
                overlayContainer,
                object : TextAppearancePanel.Listener {
                    override fun onValueChanged() {
                        applyPreferences()
                    }

                    override fun onCloseButtonClick() {
                        textAppearancePanel?.hide()
                        textAppearancePanel = null
                    }
                },
                RequestCodes.FromActivity.TextAppearanceGetFonts,
                RequestCodes.FromActivity.TextAppearanceCustomColors
            )
            splitViewManager.configureTextAppearancePanelForSplitVersion()
            textAppearancePanel?.show()
        }
    }

    private fun setNightMode(yes: Boolean) {
        val previousValue = Preferences.getBoolean(Prefkey.is_night_mode, false)
        if (previousValue == yes) return

        Preferences.setBoolean(Prefkey.is_night_mode, yes)

        applyPreferences()
        applyNightModeColors()

        textAppearancePanel?.displayValues()

        AppEvents.emitNightModeChanged()
    }

    private fun openVersionsDialog() {
        // If there is no db versions, immediately open manage version screen.
        if (S.db.listAllVersions().isEmpty()) {
            startActivity(VersionsActivity.createIntent())
            return
        }

        VersionDialogHelper.openVersionsDialog(this, App.services.versions, activeSplit0.versionId) { mv ->
            loadVersion(mv)

            // We may need to apply PerVersion settings.
            applyPreferences()
        }
    }

    private fun menuSearch_click() {
        startActivity(SearchActivity.createIntent(activeSplit0.book.bookId))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RequestCodes.FromActivity.Goto && resultCode == RESULT_OK && data != null) {
            val result = GotoActivity.obtainResult(data)
            if (result != null) {
                val ari_cv: Int

                updateBackForwardListCurrentEntry()

                if (result.bookId == -1) {
                    // stay on the same book
                    ari_cv = display(result.chapter_1, result.verse_1)

                    // call attention to the verse only if the displayed verse is equal to the requested verse
                    if (Ari.encode(0, result.chapter_1, result.verse_1) == ari_cv) {
                        callAttentionForVerseToBothSplits(result.verse_1)
                    }
                } else {
                    // change book
                    val book = activeSplit0.version.getBook(result.bookId)
                    if (book != null) {
                        activeSplit0 = activeSplit0.copy(book = book)
                    } else { // no book, just chapter and verse.
                        result.bookId = activeSplit0.book.bookId
                    }

                    ari_cv = display(result.chapter_1, result.verse_1)

                    // select the verse only if the displayed verse is equal to the requested verse
                    if (Ari.encode(result.bookId, result.chapter_1, result.verse_1) == Ari.encode(activeSplit0.book.bookId, ari_cv)) {
                        callAttentionForVerseToBothSplits(result.verse_1)
                    }
                }

                val target_ari = if (result.verse_1 == 0 && Ari.toVerse(ari_cv) == 1) {
                    // verse 0 requested, but display method causes it to show verse_1 1.
                    // However, we want to store verse_1 0 on the history.
                    Ari.encode(activeSplit0.book.bookId, Ari.toChapter(ari_cv), 0)
                } else {
                    Ari.encode(activeSplit0.book.bookId, ari_cv)
                }

                // Add target ari to history
                history.add(target_ari)
                backForwardListController.newEntry(target_ari)
            }
        } else if (requestCode == RequestCodes.FromActivity.TextAppearanceGetFonts) {
            textAppearancePanel?.onActivityResult(requestCode)
        } else if (requestCode == RequestCodes.FromActivity.TextAppearanceCustomColors) {
            textAppearancePanel?.onActivityResult(requestCode)
        } else if (requestCode == RequestCodes.FromActivity.EditNote1 && resultCode == RESULT_OK) {
            reloadBothAttributeMaps()
        } else if (requestCode == RequestCodes.FromActivity.EditNote2 && resultCode == RESULT_OK) {
            lsSplit0.uncheckAllVerses(true)
            reloadBothAttributeMaps()
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Display specified chapter and verse of the active book. By default, all checked verses will be unchecked.
     *
     * @param uncheckAllVerses whether we want to always make all verses unchecked after this operation.
     * @return Ari that contains only chapter and verse. Book always set to 0.
     */
    @JvmOverloads
    fun display(chapter_1: Int, verse_1: Int, uncheckAllVerses: Boolean = true): Int {
        val current_chapter_1 = this.chapter_1

        val available_chapter_1 = chapter_1.coerceIn(1, activeSplit0.book.chapter_count)
        val available_verse_1 = verse_1.coerceIn(1, activeSplit0.book.verse_counts[available_chapter_1 - 1])

        run {
            // main
            this.uncheckVersesWhenActionModeDestroyed = false
            try {
                val ok = loadChapterToVersesController(contentResolver, lsSplit0, { dataSplit0 = it }, activeSplit0.version, activeSplit0.versionId, activeSplit0.book, available_chapter_1, current_chapter_1, uncheckAllVerses)
                if (!ok) return 0
            } finally {
                this.uncheckVersesWhenActionModeDestroyed = true
            }

            // tell activity
            this.chapter_1 = available_chapter_1

            lsSplit0.scrollToVerse(available_verse_1)
        }

        splitViewManager.displaySplitFollowingMaster(available_verse_1)

        // set goto button text
        val reference = activeSplit0.book.reference(available_chapter_1)
        bGoto.text = reference.replace(' ', '\u00a0')

        if (fullScreen) {
            fullscreenReferenceToast?.cancel()

            val toast = Toast.makeText(this, reference, Toast.LENGTH_SHORT).apply {
                setGravity(Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, 0)
            }
            toast.show()
            fullscreenReferenceToast = toast
        }

        if (dictionaryMode) {
            finishDictionaryMode()
        }

        audioBinder.onChapterChanged()

        return Ari.encode(0, available_chapter_1, available_verse_1)
    }

    private fun loadChapterToVersesController(
        cr: ContentResolver,
        versesController: VersesController,
        dataSetter: (VersesDataModel) -> Unit,
        version: Version,
        versionId: String,
        book: Book,
        chapter_1: Int,
        current_chapter_1: Int,
        uncheckAllVerses: Boolean,
    ): Boolean {
        val verses = version.loadChapterText(book, chapter_1) ?: return false

        val pericope_aris = IntArrayList()
        val pericope_blocks = mutableListOf<PericopeBlock>()
        val nblock = version.loadPericope(book.bookId, chapter_1, pericope_aris, pericope_blocks)

        val retainSelectedVerses = !uncheckAllVerses && chapter_1 == current_chapter_1
        setDataWithRetainSelectedVerses(
            cr = cr,
            versesController = versesController,
            dataSetter = dataSetter,
            retainSelectedVerses = retainSelectedVerses,
            ariBc = Ari.encode(book.bookId, chapter_1, 0),
            pericope_aris = pericope_aris.toIntArray(),
            pericope_blocks = pericope_blocks,
            nblock = nblock,
            verses = verses,
            version = version,
            versionId = versionId,
        )

        return true
    }

    // Moved from the old VersesView method
    private fun setDataWithRetainSelectedVerses(
        cr: ContentResolver,
        versesController: VersesController,
        dataSetter: (VersesDataModel) -> Unit,
        retainSelectedVerses: Boolean,
        ariBc: Int,
        pericope_aris: IntArray,
        pericope_blocks: List<PericopeBlock>,
        nblock: Int,
        verses: SingleChapterVerses,
        version: Version,
        versionId: String,
    ) {
        var selectedVerses_1: IntArrayList? = null
        if (retainSelectedVerses) {
            selectedVerses_1 = versesController.getCheckedVerses_1()
        }

        // # fill adapter with new data. make sure all checked states are reset
        versesController.uncheckAllVerses(true)

        val versesAttributes = VerseAttributeLoader.load(App.services.storage.db, cr, ariBc, verses)

        val newData = VersesDataModel(ariBc, verses, nblock, pericope_aris, pericope_blocks, version, versionId, versesAttributes)
        dataSetter(newData)

        if (selectedVerses_1 != null) {
            versesController.checkVerses(selectedVerses_1, true)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return consumeKey(keyCode) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        return consumeKey(keyCode) || super.onKeyMultiple(keyCode, repeatCount, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val volumeButtonsForNavigation = Preferences.getString(R.string.pref_volumeButtonNavigation_key, R.string.pref_volumeButtonNavigation_default)
        if (volumeButtonsForNavigation != "default") { // consume here
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return true
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun getLeftDrawer(): LeftDrawer {
        return leftDrawer
    }

    fun bLeft_click() {
        val currentBook = activeSplit0.book
        if (chapter_1 == 1) {
            // we are in the beginning of the book, so go to prev book
            var tryBookId = currentBook.bookId - 1
            while (tryBookId >= 0) {
                val newBook = activeSplit0.version.getBook(tryBookId)
                if (newBook != null) {
                    activeSplit0 = activeSplit0.copy(book = newBook)
                    val newChapter_1 = newBook.chapter_count // to the last chapter
                    display(newChapter_1, 1)
                    break
                }
                tryBookId--
            }
            // whileelse: now is already Genesis 1. No need to do anything
        } else {
            val newChapter = chapter_1 - 1
            display(newChapter, 1)
        }
    }

    fun bRight_click() {
        val currentBook = activeSplit0.book
        if (chapter_1 >= currentBook.chapter_count) {
            val maxBookId = activeSplit0.version.maxBookIdPlusOne
            var tryBookId = currentBook.bookId + 1
            while (tryBookId < maxBookId) {
                val newBook = activeSplit0.version.getBook(tryBookId)
                if (newBook != null) {
                    activeSplit0 = activeSplit0.copy(book = newBook)
                    display(1, 1)
                    break
                }
                tryBookId++
            }
            // whileelse: now is already Revelation (or the last book) at the last chapter. No need to do anything
        } else {
            val newChapter = chapter_1 + 1
            display(newChapter, 1)
        }
    }

    override fun onSearchRequested(): Boolean {
        menuSearch_click()

        return true
    }

    inner class AttributeListener : VersesController.AttributeListener() {
        fun openBookmarkDialog(_id: Long) {
            val dialog = TypeBookmarkDialog.EditExisting(this@IsiActivity, _id)
            dialog.setListener { reloadBothAttributeMaps() }
            dialog.show()
        }

        override fun onBookmarkAttributeClick(version: Version, versionId: String, ari: Int) {
            val markers = App.services.storage.db.listMarkersForAriKind(ari, Marker.Kind.bookmark)
            if (markers.size == 1) {
                openBookmarkDialog(markers[0]._id)
            } else {
                MaterialDialogAdapterHelper.showDialogWithAdapter(
                    this@IsiActivity,
                    MultipleMarkerSelectAdapter(version, versionId, markers, Marker.Kind.bookmark),
                    getString(R.string.edit_bookmark),
                )
            }
        }

        fun openNoteDialog(_id: Long) {
            startActivityForResult(NoteActivity.createEditExistingIntent(_id), RequestCodes.FromActivity.EditNote1)
        }

        override fun onNoteAttributeClick(version: Version, versionId: String, ari: Int) {
            val markers = App.services.storage.db.listMarkersForAriKind(ari, Marker.Kind.note)
            if (markers.size == 1) {
                openNoteDialog(markers[0]._id)
            } else {
                MaterialDialogAdapterHelper.showDialogWithAdapter(
                    this@IsiActivity,
                    MultipleMarkerSelectAdapter(version, versionId, markers, Marker.Kind.note),
                    getString(R.string.edit_note),
                )
            }
        }

        inner class MarkerHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val lDate: TextView = itemView.findViewById(R.id.lDate)
            val lCaption: TextView = itemView.findViewById(R.id.lCaption)
            val lSnippet: TextView = itemView.findViewById(R.id.lSnippet)
            val panelLabels: FlowLayout = itemView.findViewById(R.id.panelLabels)
        }

        inner class MultipleMarkerSelectAdapter(
            val version: Version,
            versionId: String,
            private val markers: List<Marker>,
            val kind: Marker.Kind,
        ) : MaterialDialogAdapterHelper.Adapter() {

            val textSizeMult = App.services.storage.db.getPerVersionSettings(versionId).fontSizeMultiplier

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                return MarkerHolder(layoutInflater.inflate(R.layout.item_marker, parent, false))
            }

            override fun onBindViewHolder(_holder_: RecyclerView.ViewHolder, position: Int) {
                val holder = _holder_ as MarkerHolder

                run {
                    val marker = markers[position]

                    run {
                        val addTime = marker.createTime
                        val modifyTime = marker.modifyTime

                        if (addTime == modifyTime) {
                            holder.lDate.text = Sqlitil.toLocaleDateMedium(addTime)
                        } else {
                            holder.lDate.text = getString(R.string.create_edited_modified_time, Sqlitil.toLocaleDateMedium(addTime), Sqlitil.toLocaleDateMedium(modifyTime))
                        }

                        Appearances.applyMarkerDateTextAppearance(holder.lDate, textSizeMult)
                    }

                    val ari = marker.ari
                    val reference = version.reference(ari)
                    val caption = marker.caption

                    if (kind == Marker.Kind.bookmark) {
                        holder.lCaption.text = caption
                        Appearances.applyMarkerTitleTextAppearance(holder.lCaption, textSizeMult)

                        holder.lSnippet.visibility = View.GONE

                        val labels = App.services.storage.db.listLabelsByMarker(marker)
                        if (labels.size != 0) {
                            holder.panelLabels.visibility = View.VISIBLE
                            holder.panelLabels.removeAllViews()
                            for (label in labels) {
                                holder.panelLabels.addView(MarkerListActivity.getLabelView(layoutInflater, holder.panelLabels, label))
                            }
                        } else {
                            holder.panelLabels.visibility = View.GONE
                        }
                    } else if (kind == Marker.Kind.note) {
                        holder.lCaption.text = reference
                        Appearances.applyMarkerTitleTextAppearance(holder.lCaption, textSizeMult)
                        holder.lSnippet.text = caption
                        Appearances.applyTextAppearance(holder.lSnippet, textSizeMult)
                    }

                    holder.itemView.setBackgroundColor(App.services.uiDimensions.applied().backgroundColor)
                }

                holder.itemView.setOnClickListener {
                    dismissDialog()

                    val which = holder.bindingAdapterPosition
                    val marker = markers[which]
                    if (kind == Marker.Kind.bookmark) {
                        openBookmarkDialog(marker._id)
                    } else if (kind == Marker.Kind.note) {
                        openNoteDialog(marker._id)
                    }
                }
            }

            override fun getItemCount(): Int {
                return markers.size
            }
        }

        override fun onProgressMarkAttributeClick(version: Version, versionId: String, preset_id: Int) {
            App.services.storage.db.getProgressMarkByPresetId(preset_id)?.let { progressMark ->
                ProgressMarkRenameDialog.show(this@IsiActivity, progressMark, object : ProgressMarkRenameDialog.Listener {
                    override fun onOked() {
                        lsSplit0.uncheckAllVerses(true)
                    }

                    override fun onDeleted() {
                        lsSplit0.uncheckAllVerses(true)
                    }
                })
            }
        }

        override fun onHasMapsAttributeClick(version: Version, versionId: String, ari: Int) {
            val locale = version.locale

            try {
                val intent = Intent("palki.maps.action.SHOW_MAPS_DIALOG")
                intent.putExtra("ari", ari)

                if (locale != null) {
                    intent.putExtra("locale", locale)
                }

                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                MaterialAlertDialogBuilder(this@IsiActivity)
                    .setMessage(R.string.maps_could_not_open)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }
    }

    inner class VerseInlineLinkSpanFactory(private val sourceSupplier: () -> VersesController) : VerseInlineLinkSpan.Factory {

        override fun create(type: VerseInlineLinkSpan.Type, arif: Int) = object : VerseInlineLinkSpan(type, arif) {
            override fun onClick(type: Type, arif: Int) {
                val source = sourceSupplier()
                val activeSplit1 = activeSplit1
                if (type == Type.xref) {
                    val dialog = XrefDialog.newInstance(arif)

                    val verseSelectedListener = { arif_source: Int, ari_target: Int ->
                        dialog.dismiss()

                        val ari_source = arif_source ushr 8
                        updateBackForwardListCurrentEntry(ari_source)
                        jumpToAri(ari_target, updateBackForwardListCurrentEntryWithSource = false)
                    }

                    if (source === lsSplit0 || activeSplit1 == null) { // use activeVersion
                        dialog.init(activeSplit0.version, activeSplit0.versionId, verseSelectedListener)
                    } else if (source === lsSplit1) { // use activeSplitVersion
                        dialog.init(activeSplit1.version, activeSplit1.versionId, verseSelectedListener)
                    }

                    val fm = supportFragmentManager
                    dialog.show(fm, "XrefDialog")
                } else if (type == Type.footnote) {
                    val fe = when {
                        source === lsSplit0 -> activeSplit0.version.getFootnoteEntry(arif)
                        source === lsSplit1 -> activeSplit1?.version?.getFootnoteEntry(arif)
                        else -> null
                    }

                    if (fe != null) {
                        val footnoteText = SpannableStringBuilder()
                        VerseRenderer.appendSuperscriptNumber(footnoteText, arif and 0xff)
                        footnoteText.append(" ")

                        var footnoteDialog: androidx.appcompat.app.AlertDialog? = null

                        val rendered = FormattedTextRenderer.render(
                            fe.content,
                            mustHaveFormattedHeader = false,
                            appendToThis = footnoteText,
                            tagListener = object : FormattedTextRenderer.TagListener {
                                override fun onTag(tag: String, buffer: Spannable, start: Int, end: Int) {
                                    when {
                                        tag.startsWith("t") -> { // target verse
                                            val encodedTarget = tag.substring(1)
                                            buffer.setSpan(object : ClickableSpan() {
                                                override fun onClick(widget: View) {
                                                    val ranges = TargetDecoder.decode(encodedTarget)
                                                    val versesDialog = VersesDialog.newInstance(ranges)
                                                    versesDialog.listener = object : VersesDialog.VersesDialogListener() {
                                                        override fun onVerseSelected(ari: Int) {
                                                            footnoteDialog?.dismiss()
                                                            versesDialog.dismiss()
                                                            jumpToAri(ari)
                                                        }
                                                    }
                                                    versesDialog.show(supportFragmentManager, "verses_dialog_from_footnote")
                                                }
                                            }, start, end, 0)
                                        }

                                        else -> {
                                            MaterialAlertDialogBuilder(this@IsiActivity)
                                                .setMessage(String.format(Locale.US, "Error: footnote at arif 0x%08x contains unsupported tag %s", arif, tag))
                                                .setPositiveButton(R.string.ok, null)
                                                .show()
                                        }
                                    }
                                }
                            },
                        )

                        // Detect URLs
                        val matcher = PatternsCompat.WEB_URL.matcher(rendered)
                        while (matcher.find()) {
                            val url = matcher.group()
                            val span = object : ClickableSpan() {
                                override fun onClick(widget: View) {
                                    val uri = if (url.startsWith("http:") || url.startsWith("https:")) {
                                        url.toUri()
                                    } else {
                                        "https://$url".toUri()
                                    }
                                    try {
                                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                            rendered.setSpan(span, matcher.start(), matcher.end(), 0)
                        }

                        val ari = arif ushr 8
                        val title = when {
                            source === lsSplit0 -> activeSplit0.version.reference(ari)
                            source === lsSplit1 -> activeSplit1?.version?.reference(ari)
                            else -> null
                        }.orEmpty()

                        footnoteDialog = MaterialAlertDialogBuilder(this@IsiActivity)
                            .setTitle(title)
                            .setMessage(rendered)
                            .setPositiveButton(R.string.ok, null)
                            .show()

                        footnoteDialog.findViewById<TextView>(android.R.id.message)
                            ?.movementMethod = LinkMovementMethod.getInstance()
                    } else {
                        MaterialAlertDialogBuilder(this@IsiActivity)
                            .setMessage(String.format(Locale.US, "Error: footnote arif 0x%08x couldn't be loaded", arif))
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                } else {
                    MaterialAlertDialogBuilder(this@IsiActivity)
                        .setMessage("Error: Unknown inline link type: $type")
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }
            }
        }
    }

    /**
     * Check whether we are using a version eligible for ribka.
     */
    override fun checkRibkaEligibility(): RibkaEligibility {
        val validPresetName = "in-ayt"

        val activeMVersion = activeSplit0.mv
        val activePresetName = if (activeMVersion is MVersionDb) activeMVersion.preset_name else null

        if (validPresetName == activePresetName) {
            return RibkaEligibility.Main
        }

        val splitPresetName = (activeSplit1?.mv as? MVersionDb)?.preset_name

        return if (validPresetName == splitPresetName) RibkaEligibility.Split else RibkaEligibility.None
    }

    override fun reloadBothAttributeMaps() {
        val newDataSplit0 = reloadAttributeMapsToVerseDataModel(dataSplit0)
        dataSplit0 = newDataSplit0

        if (activeSplit1 != null) {
            val newDataSplit1 = reloadAttributeMapsToVerseDataModel(dataSplit1)
            dataSplit1 = newDataSplit1
        }
    }

    private fun reloadAttributeMapsToVerseDataModel(
        data: VersesDataModel,
    ): VersesDataModel {
        val versesAttributes = VerseAttributeLoader.load(
            App.services.storage.db,
            contentResolver,
            data.ari_bc_,
            data.verses_
        )

        return data.copy(versesAttributes = versesAttributes)
    }

    /**
     * @param aris aris where the verses are to be checked for dictionary words.
     */
    override fun startDictionaryMode(aris: Set<Int>) {
        if (!OtherAppIntegration.hasIntegratedDictionaryApp()) {
            OtherAppIntegration.askToInstallDictionary(this)
            return
        }

        dictionaryMode = true

        uiSplit0 = uiSplit0.copy(dictionaryModeAris = aris)
        uiSplit1 = uiSplit1.copy(dictionaryModeAris = aris)
    }

    private fun finishDictionaryMode() {
        dictionaryMode = false

        uiSplit0 = uiSplit0.copy(dictionaryModeAris = emptySet())
        uiSplit1 = uiSplit1.copy(dictionaryModeAris = emptySet())
    }

    override fun bMarkers_click() {
        startActivity(MarkersActivity.createIntent())
    }

    override fun bDisplay_click() {
        setShowTextAppearancePanel(textAppearancePanel == null)
    }

    override fun cFullScreen_checkedChange(isChecked: Boolean) {
        setFullScreen(isChecked)
    }

    override fun cNightMode_checkedChange(isChecked: Boolean) {
        setNightMode(isChecked)
    }

    override fun cSplitVersion_checkedChange(cSplitVersion: SwitchCompat, isChecked: Boolean) {
        if (isChecked) {
            cSplitVersion.isChecked = false // do it later, at the version chooser dialog
            splitViewManager.openSplitVersionsDialog()
        } else {
            splitViewManager.disableSplitVersion()
        }
    }

    override fun bProgressMarkList_click() {
        if (App.services.storage.db.countAllProgressMarks() > 0) {
            val dialog = ProgressMarkListDialog()
            dialog.progressMarkSelectedListener = { preset_id ->
                gotoProgressMark(preset_id)
            }
            dialog.show(supportFragmentManager, "dialog_progress_mark_list")
            leftDrawer.closeDrawer()
        } else {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.pm_activate_tutorial)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    override fun bProgress_click(preset_id: Int) {
        gotoProgressMark(preset_id)
    }

    override fun bCurrentReadingClose_click() {
        CurrentReading.clear()
    }

    override fun bCurrentReadingReference_click() {
        val aris = CurrentReading.get() ?: return

        val ari_start = aris[0]
        jumpToAri(ari_start)

        leftDrawer.closeDrawer()
    }

    private fun gotoProgressMark(preset_id: Int) {
        val progressMark = App.services.storage.db.getProgressMarkByPresetId(preset_id) ?: return

        val ari = progressMark.ari

        if (ari != 0) {
            jumpToAri(ari)
        } else {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.pm_activate_tutorial)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    companion object {
        @JvmStatic
        fun createIntent(): Intent {
            return Intent(App.context, IsiActivity::class.java)
        }

        @JvmStatic
        fun createIntent(ari: Int) = Intent(App.context, IsiActivity::class.java).apply {
            action = "yuku.alkitab.action.VIEW"
            putExtra("ari", ari)
        }
    }
}
