package yuku.alkitab.base

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.format.DateFormat
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
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.app.ShareCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.text.HtmlCompat
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.util.PatternsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.roundToLong
import me.toptas.fancyshowcase.FancyShowCaseView
import me.toptas.fancyshowcase.listener.DismissListener
import yuku.afw.storage.Preferences
import yuku.alkitab.base.ac.GotoActivity
import yuku.alkitab.base.ac.MarkerListActivity
import yuku.alkitab.base.ac.MarkersActivity
import yuku.alkitab.base.ac.NoteActivity
import yuku.alkitab.base.ac.SearchActivity
import yuku.alkitab.base.ac.base.BaseLeftDrawerActivity
import yuku.alkitab.base.config.AppConfig
import yuku.alkitab.base.dialog.ProgressMarkListDialog
import yuku.alkitab.base.dialog.ProgressMarkRenameDialog
import yuku.alkitab.base.dialog.TypeBookmarkDialog
import yuku.alkitab.base.dialog.TypeHighlightDialog
import yuku.alkitab.base.dialog.VersesDialog
import yuku.alkitab.base.dialog.XrefDialog
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.model.MVersionDb
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.settings.SettingsActivity
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Appearances
import yuku.alkitab.base.util.BackForwardListController
import yuku.alkitab.base.util.ClipboardUtil
import yuku.alkitab.base.util.CurrentReading
import yuku.alkitab.base.util.ExtensionManager
import yuku.alkitab.base.util.FormattedVerseText
import yuku.alkitab.base.util.History
import yuku.alkitab.base.util.InstallationUtil
import yuku.alkitab.base.util.Jumper
import yuku.alkitab.base.util.LidToAri
import yuku.alkitab.base.util.OtherAppIntegration
import yuku.alkitab.base.util.RequestCodes
import yuku.alkitab.base.util.ShareUrl
import yuku.alkitab.base.util.Sqlitil
import yuku.alkitab.base.util.TargetDecoder
import yuku.alkitab.base.util.safeQuery
import yuku.alkitab.base.verses.VerseAttributeLoader
import yuku.alkitab.base.verses.VersesController
import yuku.alkitab.base.verses.VersesControllerImpl
import yuku.alkitab.base.verses.VersesDataModel
import yuku.alkitab.base.verses.VersesListeners
import yuku.alkitab.base.verses.VersesUiModel
import yuku.alkitab.base.widget.AriParallelClickData
import yuku.alkitab.base.widget.DictionaryLinkInfo
import yuku.alkitab.base.widget.Floater
import yuku.alkitab.base.widget.FormattedTextRenderer
import yuku.alkitab.base.widget.GotoButton
import yuku.alkitab.base.widget.LabeledSplitHandleButton
import yuku.alkitab.base.widget.LeftDrawer
import yuku.alkitab.base.widget.MaterialDialogAdapterHelper
import yuku.alkitab.base.widget.MaterialDialogAdapterHelper.withAdapter
import yuku.alkitab.base.widget.ParallelClickData
import yuku.alkitab.base.widget.ReferenceParallelClickData
import yuku.alkitab.base.widget.SplitHandleButton
import yuku.alkitab.base.widget.TextAppearancePanel
import yuku.alkitab.base.widget.TwofingerLinearLayout
import yuku.alkitab.base.widget.VerseInlineLinkSpan
import yuku.alkitab.base.widget.VerseRenderer
import yuku.alkitab.base.widget.VerseRendererJavaHelper
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R
import yuku.alkitab.model.Book
import yuku.alkitab.model.Marker
import yuku.alkitab.model.PericopeBlock
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.model.Version
import yuku.alkitab.ribka.RibkaReportActivity
import yuku.alkitab.tracking.Tracker
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList
import yuku.alkitab.versionmanager.VersionsActivity
import yuku.devoxx.flowlayout.FlowLayout

private const val TAG = "IsiActivity"
private const val EXTRA_verseUrl = "verseUrl"
private const val INSTANCE_STATE_ari = "ari"

class IsiActivity : BaseLeftDrawerActivity(), LeftDrawer.Text.Listener {
    var uncheckVersesWhenActionModeDestroyed = true
    var needsRestart = false // whether this activity needs to be restarted

    private val bGoto_floaterDrag = object : GotoButton.FloaterDragListener {
        val floaterLocationOnScreen = intArrayOf(0, 0)

        override fun onFloaterDragStart(screenX: Float, screenY: Float) {
            floater.show(activeSplit0.book.bookId, chapter_1)
            floater.onDragStart(activeSplit0.version.consecutiveBooks)
        }

        override fun onFloaterDragMove(screenX: Float, screenY: Float) {
            floater.getLocationOnScreen(floaterLocationOnScreen)
            floater.onDragMove(screenX - floaterLocationOnScreen[0], screenY - floaterLocationOnScreen[1])
        }

        override fun onFloaterDragComplete(screenX: Float, screenY: Float) {
            floater.hide()
            floater.onDragComplete(screenX - floaterLocationOnScreen[0], screenY - floaterLocationOnScreen[1])
        }
    }

    private val floater_listener = Floater.Listener { ari ->
        jumpToAri(ari)
    }

    private val splitRoot_listener = object : TwofingerLinearLayout.Listener {
        var startFontSize = 0f
        var startDx = Float.MIN_VALUE
        var chapterSwipeCellWidth = 0f // initted later
        var moreSwipeYAllowed = true // to prevent setting and unsetting fullscreen many times within one gesture

        override fun onOnefingerLeft() {
            Tracker.trackEvent("text_onefinger_left")
            bRight_click()
        }

        override fun onOnefingerRight() {
            Tracker.trackEvent("text_onefinger_right")
            bLeft_click()
        }

        override fun onTwofingerStart() {
            chapterSwipeCellWidth = 24f * resources.displayMetrics.density
            startFontSize = Preferences.getFloat(Prefkey.ukuranHuruf2, resources.getInteger(R.integer.pref_ukuranHuruf2_default).toFloat())
        }

        override fun onTwofingerScale(scale: Float) {
            var nowFontSize = startFontSize * scale

            if (nowFontSize < 2f) nowFontSize = 2f
            if (nowFontSize > 42f) nowFontSize = 42f

            Preferences.setFloat(Prefkey.ukuranHuruf2, nowFontSize)

            applyPreferences()

            textAppearancePanel?.displayValues()
        }

        override fun onTwofingerDragX(dx: Float) {
            if (startDx == Float.MIN_VALUE) { // just started
                startDx = dx

                if (dx < 0) {
                    bRight_click()
                } else {
                    bLeft_click()
                }
            } else { // more
                // more to the left
                while (dx < startDx - chapterSwipeCellWidth) {
                    startDx -= chapterSwipeCellWidth
                    bRight_click()
                }

                while (dx > startDx + chapterSwipeCellWidth) {
                    startDx += chapterSwipeCellWidth
                    bLeft_click()
                }
            }
        }

        override fun onTwofingerDragY(dy: Float) {
            if (!moreSwipeYAllowed) return

            if (dy < 0) {
                Tracker.trackEvent("text_twofinger_up")
                setFullScreen(true)
                leftDrawer.handle.setFullScreen(true)
            } else {
                Tracker.trackEvent("text_twofinger_down")
                setFullScreen(false)
                leftDrawer.handle.setFullScreen(false)
            }

            moreSwipeYAllowed = false
        }

        override fun onTwofingerEnd(mode: TwofingerLinearLayout.Mode?) {
            startFontSize = 0f
            startDx = Float.MIN_VALUE
            moreSwipeYAllowed = true
        }
    }

    private lateinit var drawerLayout: DrawerLayout
    lateinit var leftDrawer: LeftDrawer.Text

    private lateinit var overlayContainer: FrameLayout
    lateinit var root: ViewGroup
    lateinit var toolbar: Toolbar
    private lateinit var nontoolbar: View
    lateinit var lsSplit0: VersesController
    lateinit var lsSplit1: VersesController
    lateinit var splitRoot: TwofingerLinearLayout
    lateinit var splitHandleButton: LabeledSplitHandleButton
    private lateinit var bGoto: GotoButton
    private lateinit var bLeft: ImageButton
    private lateinit var bRight: ImageButton
    private lateinit var bVersion: TextView
    lateinit var floater: Floater
    private lateinit var backForwardListController: BackForwardListController<ImageButton, ImageButton>
    private var fullscreenReferenceToast: Toast? = null

    private var dataSplit0 = VersesDataModel.EMPTY
        set(value) {
            field = value
            lsSplit0.versesDataModel = value
        }

    private var dataSplit1 = VersesDataModel.EMPTY
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

    var chapter_1 = 0
    private var fullScreen = false

    val history get() = History

    var actionMode: ActionMode? = null
    private var dictionaryMode = false
    var textAppearancePanel: TextAppearancePanel? = null

    /**
     * The following "esvsbasal" thing is a personal thing by yuku that doesn't matter to anyone else.
     * Please ignore it and leave it intact.
     */
    val hasEsvsbAsal by lazy {
        try {
            packageManager.getApplicationInfo("yuku.esvsbasal", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
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
            val version = S.activeVersion()
            val new = ActiveSplit0(
                mv = S.activeMVersion(),
                version = version,
                versionId = S.activeVersionId(),
                book = version.firstBook
            )
            this._activeSplit0 = new
            return new
        }
        set(value) {
            _activeSplit0 = value
        }

    /**
     * Container class to make sure that the fields are changed simultaneously.
     */
    data class ActiveSplit1(
        val mv: MVersion,
        val version: Version,
        val versionId: String,
    )

    /**
     * The secondary version. Set to null if the secondary version is not opened,
     * and to non-null if the secondary version is opened.
     */
    var activeSplit1: ActiveSplit1? = null

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
        val uri = Uri.parse("content://org.sabda.kamus.provider/define").buildUpon()
            .appendQueryParameter("key", data.key)
            .appendQueryParameter("mode", "snippet")
            .build()

        try {
            cr.safeQuery(uri, null, null, null, null) ?: run {
                OtherAppIntegration.askToInstallDictionary(this)
                return
            }
        } catch (e: Exception) {
            MaterialDialog(this).show {
                message(R.string.dict_no_results)
                positiveButton(R.string.ok)
            }
            return
        }.use { c ->
            if (c.count == 0) {
                MaterialDialog(this).show {
                    message(R.string.dict_no_results)
                    positiveButton(R.string.ok)
                }
            } else {
                c.moveToNext()
                val rendered = HtmlCompat.fromHtml(c.getString(c.getColumnIndexOrThrow("definition")), HtmlCompat.FROM_HTML_MODE_COMPACT)
                val sb = if (rendered is SpannableStringBuilder) rendered else SpannableStringBuilder(rendered)

                // remove links
                for (span in sb.getSpans(0, sb.length, URLSpan::class.java)) {
                    sb.removeSpan(span)
                }

                MaterialDialog(this).show {
                    title(text = data.orig_text)
                    message(text = sb)
                    positiveButton(R.string.dict_open_full) {
                        val intent = Intent("org.sabda.kamus.action.VIEW")
                            .putExtra("key", data.key)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                        try {
                            startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            OtherAppIntegration.askToInstallDictionary(this@IsiActivity)
                        }
                    }
                }
            }
        }
    }

    private val pinDropListener = object : VersesController.PinDropListener() {
        override fun onPinDropped(presetId: Int, ari: Int) {
            Tracker.trackEvent("pin_drop")

            val progressMark = S.db.getProgressMarkByPresetId(presetId)
            if (progressMark != null) {
                progressMark.ari = ari
                progressMark.modifyTime = Date()
                S.db.insertOrUpdateProgressMark(progressMark)
            }

            App.getLbm().sendBroadcast(Intent(ACTION_ATTRIBUTE_MAP_CHANGED))
        }
    }

    private val splitRoot_globalLayout = object : ViewTreeObserver.OnGlobalLayoutListener {
        val lastSize = Point()

        override fun onGlobalLayout() {
            if (lastSize.x == splitRoot.width && lastSize.y == splitRoot.height) {
                return // no need to layout now
            }

            if (activeSplit1 == null) {
                return // we are not splitting
            }

            configureSplitSizes()

            lastSize.x = splitRoot.width
            lastSize.y = splitRoot.height
        }
    }

    private val reloadAttributeMapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reloadBothAttributeMaps()
        }
    }

    private val needsRestartReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            needsRestart = true
        }
    }

    private val lsSplit0_selectedVerses = object : VersesController.SelectedVersesListener() {
        override fun onSomeVersesSelected(verses_1: IntArrayList) {
            if (activeSplit1 != null) {
                // synchronize the selection with the split view
                lsSplit1.checkVerses(verses_1, false)
            }

            if (actionMode == null) {
                actionMode = startSupportActionMode(actionMode_callback)
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
            if (!isPericope && activeSplit1 != null) {
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
            if (!isPericope) {
                lsSplit0.scrollToVerse(verse_1, prop)
            }
        }

        override fun onScrollToTop() {
            lsSplit0.scrollToTop()
        }
    }

    val actionMode_callback = object : ActionMode.Callback {
        private val MENU_GROUP_EXTENSIONS = Menu.FIRST + 1
        private val MENU_EXTENSIONS_FIRST_ID = 0x1000

        val extensions = mutableListOf<ExtensionManager.Info>()

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menuInflater.inflate(R.menu.context_isi, menu)

            AppLog.d(TAG, "@@onCreateActionMode")

            if (hasEsvsbAsal) {
                val esvsb = menu.findItem(R.id.menuEsvsb)
                esvsb?.isVisible = true
            }

            // show book name and chapter
            val reference = activeSplit0.book.reference(chapter_1)
            mode.title = reference

            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val menuAddBookmark = menu.findItem(R.id.menuAddBookmark)
            val menuAddNote = menu.findItem(R.id.menuAddNote)
            val menuCompare = menu.findItem(R.id.menuCompare)

            val selected = lsSplit0.getCheckedVerses_1()
            val single = selected.size() == 1

            // For unknown reasons the size of selected can be zero and get(0) causes crash.
            // https://console.firebase.google.com/u/0/project/alkitab-host-hrd/crashlytics/app/android:yuku.alkitab/issues/cc11d3466c89303f88b9e27ab3fdd534
            if (selected.size() == 0) {
                AppLog.e(TAG, "@@onPrepareActionMode checked verses is empty.")
                mode.finish()
                return true
            }

            var contiguous = true
            if (!single) {
                var next = selected.get(0) + 1
                var i = 1
                val len = selected.size()
                while (i < len) {
                    val cur = selected.get(i)
                    if (next != cur) {
                        contiguous = false
                        break
                    }
                    next = cur + 1
                    i++
                }
            }

            menuAddBookmark.isVisible = contiguous
            menuAddNote.isVisible = contiguous
            menuCompare.isVisible = single

            // just "copy" or ("copy primary" "copy secondary" "copy both")
            // same with "share".
            val menuCopy = menu.findItem(R.id.menuCopy)
            val menuCopySplit0 = menu.findItem(R.id.menuCopySplit0)
            val menuCopySplit1 = menu.findItem(R.id.menuCopySplit1)
            val menuCopyBothSplits = menu.findItem(R.id.menuCopyBothSplits)
            val menuShare = menu.findItem(R.id.menuShare)
            val menuShareSplit0 = menu.findItem(R.id.menuShareSplit0)
            val menuShareSplit1 = menu.findItem(R.id.menuShareSplit1)
            val menuShareBothSplits = menu.findItem(R.id.menuShareBothSplits)

            val split = activeSplit1 != null

            menuCopy.isVisible = !split
            menuCopySplit0.isVisible = split
            menuCopySplit1.isVisible = split
            menuCopyBothSplits.isVisible = split
            menuShare.isVisible = !split
            menuShareSplit0.isVisible = split
            menuShareSplit1.isVisible = split
            menuShareBothSplits.isVisible = split

            // show selected verses
            if (single) {
                mode.setSubtitle(R.string.verse_select_one_verse_selected)
            } else {
                mode.subtitle = getString(R.string.verse_select_multiple_verse_selected, selected.size().toString())
            }

            val menuGuide = menu.findItem(R.id.menuGuide)
            val menuCommentary = menu.findItem(R.id.menuCommentary)
            val menuDictionary = menu.findItem(R.id.menuDictionary)

            // force-show these items on sw600dp, otherwise never show
            val showAsAction = if (resources.configuration.smallestScreenWidthDp >= 600) MenuItem.SHOW_AS_ACTION_ALWAYS else MenuItem.SHOW_AS_ACTION_NEVER
            menuGuide.setShowAsActionFlags(showAsAction)
            menuCommentary.setShowAsActionFlags(showAsAction)
            menuDictionary.setShowAsActionFlags(showAsAction)

            // set visibility according to appconfig
            val c = AppConfig.get()
            menuGuide.isVisible = c.menuGuide
            menuCommentary.isVisible = c.menuCommentary

            // do not show dictionary item if not needed because of auto-lookup from
            menuDictionary.isVisible = c.menuDictionary && !Preferences.getBoolean(getString(R.string.pref_autoDictionaryAnalyze_key), resources.getBoolean(R.bool.pref_autoDictionaryAnalyze_default))

            val menuRibkaReport = menu.findItem(R.id.menuRibkaReport)
            menuRibkaReport.isVisible = single && checkRibkaEligibility() != RibkaEligibility.None

            // extensions
            extensions.clear()
            extensions.addAll(ExtensionManager.getExtensions())

            menu.removeGroup(MENU_GROUP_EXTENSIONS)

            for ((i, extension) in extensions.withIndex()) {
                if (single || /* not single */ extension.supportsMultipleVerses) {
                    menu.add(MENU_GROUP_EXTENSIONS, MENU_EXTENSIONS_FIRST_ID + i, 0, extension.label)
                }
            }

            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val selected = lsSplit0.getCheckedVerses_1()

            if (selected.size() == 0) return true

            return when (val itemId = item.itemId) {
                R.id.menuCopy, R.id.menuCopySplit0, R.id.menuCopySplit1, R.id.menuCopyBothSplits -> {

                    // copy, can be multiple verses

                    val reference = referenceFromSelectedVerses(selected, activeSplit0.book)
                    val activeSplit1 = activeSplit1
                    val t = if (itemId == R.id.menuCopy || itemId == R.id.menuCopySplit0 || itemId == R.id.menuCopyBothSplits || activeSplit1 == null) {
                        prepareTextForCopyShare(selected, reference, false)
                    } else { // menuCopySplit1, do not use split0 reference
                        val book = activeSplit1.version.getBook(activeSplit0.book.bookId) ?: activeSplit0.book
                        prepareTextForCopyShare(selected, referenceFromSelectedVerses(selected, book), true)
                    }

                    if (itemId == R.id.menuCopyBothSplits && activeSplit1 != null) {
                        val book = activeSplit1.version.getBook(activeSplit0.book.bookId) ?: activeSplit0.book
                        appendSplitTextForCopyShare(book, lsSplit1.getCheckedVerses_1(), t)
                    }

                    val textToCopy = t[0]
                    val textToSubmit = t[1]

                    ShareUrl.make(this@IsiActivity, !Preferences.getBoolean(getString(R.string.pref_copyWithShareUrl_key), resources.getBoolean(R.bool.pref_copyWithShareUrl_default)), textToSubmit, Ari.encode(activeSplit0.book.bookId, chapter_1, 0), selected, reference.toString(), activeSplit0.version, MVersionDb.presetNameFromVersionId(activeSplit0.versionId), object : ShareUrl.Callback {
                        override fun onSuccess(shareUrl: String) {
                            ClipboardUtil.copyToClipboard(textToCopy + "\n\n" + shareUrl)
                        }

                        override fun onUserCancel() {
                            ClipboardUtil.copyToClipboard(textToCopy)
                        }

                        override fun onError(e: Exception) {
                            AppLog.e(TAG, "Error in ShareUrl, copying without shareUrl", e)
                            ClipboardUtil.copyToClipboard(textToCopy)
                        }

                        override fun onFinally() {
                            lsSplit0.uncheckAllVerses(true)

                            Snackbar.make(root, getString(R.string.alamat_sudah_disalin, reference), Snackbar.LENGTH_SHORT).show()
                            mode.finish()
                        }
                    })

                    true
                }

                R.id.menuShare, R.id.menuShareSplit0, R.id.menuShareSplit1, R.id.menuShareBothSplits -> {
                    // share, can be multiple verses
                    val reference = referenceFromSelectedVerses(selected, activeSplit0.book)
                    val activeSplit1 = activeSplit1

                    val t = if (itemId == R.id.menuShare || itemId == R.id.menuShareSplit0 || itemId == R.id.menuShareBothSplits || activeSplit1 == null) {
                        prepareTextForCopyShare(selected, reference, false)
                    } else { // menuShareSplit1, do not use split0 reference
                        val book = activeSplit1.version.getBook(activeSplit0.book.bookId) ?: activeSplit0.book
                        prepareTextForCopyShare(selected, referenceFromSelectedVerses(selected, book), true)
                    }

                    if (itemId == R.id.menuShareBothSplits && activeSplit1 != null) {
                        val book = activeSplit1.version.getBook(activeSplit0.book.bookId) ?: activeSplit0.book
                        appendSplitTextForCopyShare(book, lsSplit1.getCheckedVerses_1(), t)
                    }

                    val textToShare = t[0]
                    val textToSubmit = t[1]

                    val intent = ShareCompat.IntentBuilder(this@IsiActivity)
                        .setType("text/plain")
                        .setSubject(reference.toString())
                        .intent

                    ShareUrl.make(this@IsiActivity, !Preferences.getBoolean(getString(R.string.pref_copyWithShareUrl_key), resources.getBoolean(R.bool.pref_copyWithShareUrl_default)), textToSubmit, Ari.encode(activeSplit0.book.bookId, chapter_1, 0), selected, reference.toString(), activeSplit0.version, MVersionDb.presetNameFromVersionId(activeSplit0.versionId), object : ShareUrl.Callback {
                        override fun onSuccess(shareUrl: String) {
                            intent.putExtra(Intent.EXTRA_TEXT, textToShare + "\n\n" + shareUrl)
                            intent.putExtra(EXTRA_verseUrl, shareUrl)
                        }

                        override fun onUserCancel() {
                            intent.putExtra(Intent.EXTRA_TEXT, textToShare)
                        }

                        override fun onError(e: Exception) {
                            AppLog.e(TAG, "Error in ShareUrl, sharing without shareUrl", e)
                            intent.putExtra(Intent.EXTRA_TEXT, textToShare)
                        }

                        override fun onFinally() {
                            startActivity(Intent.createChooser(intent, getString(R.string.bagikan_alamat, reference)))

                            lsSplit0.uncheckAllVerses(true)
                            mode.finish()
                        }
                    })
                    true
                }

                R.id.menuCompare -> {
                    val ari = Ari.encode(activeSplit0.book.bookId, this@IsiActivity.chapter_1, selected.get(0))
                    val dialog = VersesDialog.newCompareInstance(ari)
                    dialog.listener = object : VersesDialog.VersesDialogListener() {
                        override fun onComparedVerseSelected(ari: Int, mversion: MVersion) {
                            loadVersion(mversion)
                            dialog.dismiss()
                        }
                    }

                    // Allow state loss to prevent
                    // https://console.firebase.google.com/u/0/project/alkitab-host-hrd/crashlytics/app/android:yuku.alkitab/issues/b80d5209ee90ebd9c5eb30f87f19c85f
                    val ft = supportFragmentManager.beginTransaction()
                    ft.add(dialog, "compare_dialog")
                    ft.commitAllowingStateLoss()

                    true
                }

                R.id.menuAddBookmark -> {

                    // contract: this menu only appears when contiguous verses are selected
                    if (selected.get(selected.size() - 1) - selected.get(0) != selected.size() - 1) {
                        throw RuntimeException("Non contiguous verses when adding bookmark: $selected")
                    }

                    val ari = Ari.encode(activeSplit0.book.bookId, this@IsiActivity.chapter_1, selected.get(0))
                    val verseCount = selected.size()

                    // always create a new bookmark
                    val dialog = TypeBookmarkDialog.NewBookmark(this@IsiActivity, ari, verseCount)
                    dialog.setListener {
                        lsSplit0.uncheckAllVerses(true)
                        reloadBothAttributeMaps()
                    }
                    dialog.show()

                    mode.finish()
                    true
                }

                R.id.menuAddNote -> {

                    // contract: this menu only appears when contiguous verses are selected
                    if (selected.get(selected.size() - 1) - selected.get(0) != selected.size() - 1) {
                        throw RuntimeException("Non contiguous verses when adding note: $selected")
                    }

                    val ari = Ari.encode(activeSplit0.book.bookId, this@IsiActivity.chapter_1, selected.get(0))
                    val verseCount = selected.size()

                    // always create a new note
                    startActivityForResult(NoteActivity.createNewNoteIntent(activeSplit0.version.referenceWithVerseCount(ari, verseCount), ari, verseCount), RequestCodes.FromActivity.EditNote2)
                    mode.finish()

                    true
                }

                R.id.menuAddHighlight -> {
                    val ariBc = Ari.encode(activeSplit0.book.bookId, this@IsiActivity.chapter_1, 0)
                    val colorRgb = S.db.getHighlightColorRgb(ariBc, selected)

                    val listener = TypeHighlightDialog.Listener {
                        lsSplit0.uncheckAllVerses(true)
                        reloadBothAttributeMaps()
                    }

                    val reference = referenceFromSelectedVerses(selected, activeSplit0.book)
                    if (selected.size() == 1) {
                        val ftr = VerseRenderer.FormattedTextResult()
                        val ari = Ari.encodeWithBc(ariBc, selected.get(0))
                        val rawVerseText = activeSplit0.version.loadVerseText(ari) ?: ""
                        val info = S.db.getHighlightColorRgb(ari)

                        VerseRendererJavaHelper.render(ari = ari, text = rawVerseText, ftr = ftr)
                        TypeHighlightDialog(this@IsiActivity, ari, listener, colorRgb, info, reference, ftr.result)
                    } else {
                        TypeHighlightDialog(this@IsiActivity, ariBc, selected, listener, colorRgb, reference)
                    }
                    mode.finish()
                    true
                }

                R.id.menuEsvsb -> {

                    val ari = Ari.encode(activeSplit0.book.bookId, this@IsiActivity.chapter_1, selected.get(0))

                    try {
                        val intent = Intent("yuku.esvsbasal.action.GOTO")
                        intent.putExtra("ari", ari)
                        startActivity(intent)
                    } catch (e: Exception) {
                        AppLog.e(TAG, "ESVSB starting", e)
                    }
                    true
                }

                R.id.menuGuide -> {

                    val ari = Ari.encode(activeSplit0.book.bookId, this@IsiActivity.chapter_1, 0)

                    try {
                        packageManager.getPackageInfo("org.sabda.pedia", 0)

                        val intent = Intent("org.sabda.pedia.action.VIEW")
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.putExtra("ari", ari)
                        startActivity(intent)
                    } catch (e: PackageManager.NameNotFoundException) {
                        OtherAppIntegration.openMarket(this@IsiActivity, "org.sabda.pedia")
                    }
                    true
                }

                R.id.menuCommentary -> {

                    val ari = Ari.encode(activeSplit0.book.bookId, this@IsiActivity.chapter_1, selected.get(0))

                    try {
                        packageManager.getPackageInfo("org.sabda.tafsiran", 0)

                        val intent = Intent("org.sabda.tafsiran.action.VIEW")
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.putExtra("ari", ari)
                        startActivity(intent)
                    } catch (e: PackageManager.NameNotFoundException) {
                        OtherAppIntegration.openMarket(this@IsiActivity, "org.sabda.tafsiran")
                    }
                    true
                }

                R.id.menuDictionary -> {

                    val ariBc = Ari.encode(activeSplit0.book.bookId, this@IsiActivity.chapter_1, 0)
                    val aris = HashSet<Int>()
                    var i = 0
                    val len = selected.size()
                    while (i < len) {
                        val verse_1 = selected.get(i)
                        val ari = Ari.encodeWithBc(ariBc, verse_1)
                        aris.add(ari)
                        i++
                    }

                    startDictionaryMode(aris)
                    true
                }

                R.id.menuRibkaReport -> {

                    val ribkaEligibility = checkRibkaEligibility()
                    if (ribkaEligibility != RibkaEligibility.None) {
                        val ari = Ari.encode(activeSplit0.book.bookId, this@IsiActivity.chapter_1, selected.get(0))

                        val reference: CharSequence?
                        val verseText: String?
                        val versionDescription: String?

                        if (ribkaEligibility == RibkaEligibility.Main) {
                            reference = activeSplit0.version.reference(ari)
                            verseText = activeSplit0.version.loadVerseText(ari)
                            versionDescription = activeSplit0.mv.description
                        } else {
                            reference = activeSplit1?.version?.reference(ari)
                            verseText = activeSplit1?.version?.loadVerseText(ari)
                            versionDescription = activeSplit1?.mv?.description
                        }

                        if (reference != null && verseText != null) {
                            startActivity(RibkaReportActivity.createIntent(ari, reference.toString(), verseText, versionDescription))
                        }
                    }
                    true
                }

                in MENU_EXTENSIONS_FIRST_ID until MENU_EXTENSIONS_FIRST_ID + extensions.size -> {
                    val extension = extensions[itemId - MENU_EXTENSIONS_FIRST_ID]

                    val intent = Intent(ExtensionManager.ACTION_SHOW_VERSE_INFO)
                    intent.component = ComponentName(extension.activityInfo.packageName, extension.activityInfo.name)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    // prepare extra "aris"
                    val aris = IntArray(selected.size())
                    val ariBc = Ari.encode(activeSplit0.book.bookId, this@IsiActivity.chapter_1, 0)
                    run {
                        var i = 0
                        val len = selected.size()
                        while (i < len) {
                            val verse_1 = selected.get(i)
                            val ari = Ari.encodeWithBc(ariBc, verse_1)
                            aris[i] = ari
                            i++
                        }
                    }
                    intent.putExtra("aris", aris)

                    if (extension.includeVerseText) {
                        // prepare extra "verseTexts"
                        val verseTexts = arrayOfNulls<String>(selected.size())
                        var i = 0
                        val len = selected.size()
                        while (i < len) {
                            val verse_1 = selected.get(i)

                            val verseText = dataSplit0.getVerseText(verse_1)
                            if (extension.includeVerseTextFormatting) {
                                verseTexts[i] = verseText
                            } else {
                                verseTexts[i] = FormattedVerseText.removeSpecialCodes(verseText)
                            }
                            i++
                        }
                        intent.putExtra("verseTexts", verseTexts)
                    }

                    try {
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        MaterialDialog(this@IsiActivity).show {
                            message(text = "Error ANFE starting extension\n\n${extension.activityInfo.packageName}/${extension.activityInfo.name}")
                            positiveButton(R.string.ok)
                        }
                    }

                    true
                }

                else -> false
            }
        }

        /**
         * @param t [0] is text to copy, [1] is text to submit
         */
        fun appendSplitTextForCopyShare(book: Book?, selectedVerses_1: IntArrayList, t: Array<String>) {
            if (book == null) return
            val referenceSplit = referenceFromSelectedVerses(selectedVerses_1, book)
            val a = prepareTextForCopyShare(selectedVerses_1, referenceSplit, true)
            t[0] += "\n\n" + a[0]
            t[1] += "\n\n" + a[1]
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null

            // FIXME even with this guard, verses are still unchecked when switching version while both Fullscreen and Split is active.
            // This guard only fixes unchecking of verses when in fullscreen mode.
            if (uncheckVersesWhenActionModeDestroyed) {
                lsSplit0.uncheckAllVerses(true)
            }
        }
    }

    private val splitHandleButton_listener = object : SplitHandleButton.SplitHandleButtonListener {
        var first = 0
        var handle = 0
        var root = 0
        var prop = 0f // proportion from top or left

        override fun onHandleDragStart() {
            splitRoot.setOnefingerEnabled(false)

            if (splitHandleButton.orientation == SplitHandleButton.Orientation.vertical) {
                first = splitHandleButton.top
                handle = splitHandleButton.height
                root = splitRoot.height
            } else {
                first = splitHandleButton.left
                handle = splitHandleButton.width
                root = splitRoot.width
            }

            prop = Float.MIN_VALUE // guard against glitches
        }

        override fun onHandleDragMoveX(dxSinceLast: Float, dxSinceStart: Float) {
            val newW = (first + dxSinceStart).toInt()
            val maxW = root - handle
            val width = if (newW < 0) 0 else if (newW > maxW) maxW else newW
            lsSplit0.setViewLayoutSize(width, ViewGroup.LayoutParams.MATCH_PARENT)
            prop = width.toFloat() / maxW
        }

        override fun onHandleDragMoveY(dySinceLast: Float, dySinceStart: Float) {
            val newH = (first + dySinceStart).toInt()
            val maxH = root - handle
            val height = if (newH < 0) 0 else if (newH > maxH) maxH else newH
            lsSplit0.setViewLayoutSize(ViewGroup.LayoutParams.MATCH_PARENT, height)
            prop = height.toFloat() / maxH
        }

        override fun onHandleDragStop() {
            splitRoot.setOnefingerEnabled(true)

            if (prop != Float.MIN_VALUE) {
                Preferences.setFloat(Prefkey.lastSplitProp, prop)
            }
        }
    }

    private val splitHandleButton_labelPressed = LabeledSplitHandleButton.ButtonPressListener { which ->
        when (which) {
            LabeledSplitHandleButton.Button.rotate -> {
                closeSplitDisplay()
                openSplitDisplay()
            }

            LabeledSplitHandleButton.Button.start -> openVersionsDialog()
            LabeledSplitHandleButton.Button.end -> openSplitVersionsDialog()
            else -> throw IllegalStateException("should not happen")
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
        splitRoot.viewTreeObserver.addOnGlobalLayoutListener(splitRoot_globalLayout)

        splitHandleButton = findViewById(R.id.splitHandleButton)
        floater = findViewById(R.id.floater)

        // If layout is changed, updateToolbarLocation must be updated as well. This will be called in DEBUG to make sure
        // updateToolbarLocation is also updated when layout is updated.
        if (BuildConfig.DEBUG) {
            if (root.childCount != 2 || root.getChildAt(0).id != R.id.toolbar || root.getChildAt(1).id != R.id.nontoolbar) {
                throw RuntimeException("Layout changed and this is no longer compatible with updateToolbarLocation")
            }
        }

        updateToolbarLocation()

        splitRoot.setListener(splitRoot_listener)

        bGoto.setOnClickListener { bGoto_click() }
        bGoto.setOnLongClickListener {
            bGoto_longClick()
            true
        }
        bGoto.setFloaterDragListener(bGoto_floaterDrag)

        bLeft.setOnClickListener { bLeft_click() }
        bRight.setOnClickListener { bRight_click() }
        bVersion.setOnClickListener { bVersion_click() }

        floater.setListener(floater_listener)

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
        splitHandleButton.setListener(splitHandleButton_listener)
        splitHandleButton.setButtonPressListener(splitHandleButton_labelPressed)

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
                    val mv = S.getMVersionInternal()
                    S.setActiveVersion(mv)

                    val version = S.activeVersion()
                    val versionId = S.activeVersionId()
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

        run {
            // load last split version. This must be after load book, chapter, and verse.
            val lastSplitVersionId = Preferences.getString(Prefkey.lastSplitVersionId, null)
            if (lastSplitVersionId != null) {
                val splitOrientation = Preferences.getString(Prefkey.lastSplitOrientation)
                if (SplitHandleButton.Orientation.horizontal.name == splitOrientation) {
                    splitHandleButton.orientation = SplitHandleButton.Orientation.horizontal
                } else {
                    splitHandleButton.orientation = SplitHandleButton.Orientation.vertical
                }

                val splitMv = S.getVersionFromVersionId(lastSplitVersionId)
                val splitMvActual = splitMv ?: S.getMVersionInternal()

                if (loadSplitVersion(splitMvActual)) {
                    openSplitDisplay()
                    displaySplitFollowingMaster(Ari.toVerse(openingAri))
                }
            }
        }

        if (selectVerse) {
            for (i in 0 until selectVerseCount) {
                val verse_1 = Ari.toVerse(openingAri) + i
                callAttentionForVerseToBothSplits(verse_1)
            }
        }

        App.getLbm().registerReceiver(reloadAttributeMapReceiver, IntentFilter(ACTION_ATTRIBUTE_MAP_CHANGED))

        App.getLbm().registerReceiver(needsRestartReceiver, IntentFilter(ACTION_NEEDS_RESTART))
        AppLog.d(TAG, "@@onCreate end")
    }

    private fun calculateTextSizeMult(versionId: String?): Float {
        return if (versionId == null) 1f else S.db.getPerVersionSettings(versionId).fontSizeMultiplier
    }

    private fun callAttentionForVerseToBothSplits(verse_1: Int) {
        lsSplit0.callAttentionForVerse(verse_1)
        lsSplit1.callAttentionForVerse(verse_1)
    }

    override fun onDestroy() {
        super.onDestroy()

        App.getLbm().unregisterReceiver(reloadAttributeMapReceiver)

        App.getLbm().unregisterReceiver(needsRestartReceiver)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(INSTANCE_STATE_ari, Ari.encode(activeSplit0.book.bookId, chapter_1, getVerse_1BasedOnScrolls()))
    }

    /**
     * @return non-null if the intent is handled by any of the intent handler (e.g. VIEW)
     */
    private fun extractIntent(intent: Intent): IntentResult? {
        dumpIntent(intent, "IsiActivity#onCreate")

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
    private fun getVerse_1BasedOnScrolls(): Int {
        val split0verse_1 = lsSplit0.getVerse_1BasedOnScroll()
        if (split0verse_1 != 0) return split0verse_1

        val split1verse_1 = lsSplit1.getVerse_1BasedOnScroll()
        if (split1verse_1 != 0) return split1verse_1

        return 1 // default value for verse_1
    }

    fun loadVersion(mv: MVersion) {
        try {
            val version = mv.version ?: throw RuntimeException() // caught below

            // we already have some other version loaded, so make the new version open the same book
            val bookId = activeSplit0.book.bookId

            // Set globally
            S.setActiveVersion(mv)

            // If a book is not found, get any book
            val book = version.getBook(bookId) ?: version.firstBook
            activeSplit0 = ActiveSplit0(
                mv = mv,
                version = version,
                versionId = S.activeVersionId(),
                book = book
            )

            displayActiveVersion()

            display(chapter_1, getVerse_1BasedOnScrolls(), false)

            App.getLbm().sendBroadcast(Intent(ACTION_ACTIVE_VERSION_CHANGED))
        } catch (e: Throwable) { // so we don't crash on the beginning of the app
            AppLog.e(TAG, "Error opening main version", e)

            MaterialDialog(this).show {
                message(text = getString(R.string.version_error_opening, mv.longName))
                positiveButton(R.string.ok)
            }
        }
    }

    private fun displayActiveVersion() {
        bVersion.text = activeSplit0.version.initials
        splitHandleButton.setLabel1("\u25b2 " + activeSplit0.version.initials)
    }

    private fun loadSplitVersion(mv: MVersion): Boolean {
        try {
            val version = mv.version ?: throw RuntimeException() // caught below

            activeSplit1 = ActiveSplit1(mv, version, mv.versionId)

            splitHandleButton.setLabel2(version.initials + " \u25bc")

            configureTextAppearancePanelForSplitVersion()

            return true
        } catch (e: Throwable) { // so we don't crash on the beginning of the app
            AppLog.e(TAG, "Error opening split version", e)

            MaterialDialog(this@IsiActivity).show {
                message(text = getString(R.string.version_error_opening, mv.longName))
                positiveButton(R.string.ok)
            }

            return false
        }
    }

    private fun configureTextAppearancePanelForSplitVersion() {
        textAppearancePanel?.let { textAppearancePanel ->
            val activeSplit1 = activeSplit1
            if (activeSplit1 == null) {
                textAppearancePanel.clearSplitVersion()
            } else {
                textAppearancePanel.setSplitVersion(activeSplit1.versionId, activeSplit1.version.longName)
            }
        }
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
            MaterialDialog(this).show {
                message(text = getString(R.string.alamat_tidak_sah_alamat, reference))
                positiveButton(R.string.ok)
            }
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

    fun referenceFromSelectedVerses(selectedVerses: IntArrayList, book: Book): String {
        return when (selectedVerses.size()) {
            // should not be possible. So we don't do anything.
            0 -> book.reference(this.chapter_1)
            1 -> book.reference(this.chapter_1, selectedVerses.get(0))
            else -> book.reference(this.chapter_1, selectedVerses)
        }
    }

    /**
     * Construct text for copying or sharing (in plain text).
     *
     * @param isSplitVersion whether take the verse text from the main or from the split version.
     * @return [0] text for copying/sharing, [1] text to be submitted to the share url service
     */
    fun prepareTextForCopyShare(selectedVerses_1: IntArrayList, reference: CharSequence, isSplitVersion: Boolean): Array<String> {
        val res0 = StringBuilder()
        val res1 = StringBuilder()

        res0.append(reference)

        if (Preferences.getBoolean(getString(R.string.pref_copyWithVersionName_key), resources.getBoolean(R.bool.pref_copyWithVersionName_default))) {
            // Fallback to primary version for safety
            val version = if (isSplitVersion) activeSplit1?.version ?: activeSplit0.version else activeSplit0.version
            val versionShortName = version.shortName
            if (versionShortName != null) {
                res0.append(" (").append(versionShortName).append(")")
            }
        }

        if (Preferences.getBoolean(getString(R.string.pref_copyWithVerseNumbers_key), false) && selectedVerses_1.size() > 1) {
            res0.append('\n')

            // append each selected verse with verse number prepended
            var i = 0
            val len = selectedVerses_1.size()
            while (i < len) {
                val verse_1 = selectedVerses_1.get(i)
                val verseText = if (isSplitVersion) dataSplit1.getVerseText(verse_1) else dataSplit0.getVerseText(verse_1)

                if (verseText != null) {
                    val verseTextPlain = FormattedVerseText.removeSpecialCodes(verseText)

                    res0.append(verse_1)
                    res1.append(verse_1)
                    res0.append(' ')
                    res1.append(' ')

                    res0.append(verseTextPlain)
                    res1.append(verseText)

                    if (i != len - 1) {
                        res0.append('\n')
                        res1.append('\n')
                    }
                }
                i++
            }
        } else {
            res0.append("  ")

            // append each selected verse without verse number prepended
            for (i in 0 until selectedVerses_1.size()) {
                val verse_1 = selectedVerses_1.get(i)
                val verseText = if (isSplitVersion) dataSplit1.getVerseText(verse_1) else dataSplit0.getVerseText(verse_1)

                if (verseText != null) {
                    val verseTextPlain = FormattedVerseText.removeSpecialCodes(verseText)

                    if (i != 0) {
                        res0.append('\n')
                        res1.append('\n')
                    }
                    res0.append(verseTextPlain)
                    res1.append(verseText)
                }
            }
        }

        return arrayOf(res0.toString(), res1.toString())
    }

    fun applyPreferences() {
        // make sure S applied variables are set first
        S.recalculateAppliedValuesBasedOnPreferences()

        // apply background color, and clear window background to prevent overdraw
        window.setBackgroundDrawableResource(android.R.color.transparent)
        val backgroundColor = S.applied().backgroundColor
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

        lsSplit0.setViewPadding(SettingsActivity.getPaddingBasedOnPreferences())
        lsSplit1.setViewPadding(SettingsActivity.getPaddingBasedOnPreferences())
    }

    override fun onStop() {
        super.onStop()

        Preferences.hold()
        try {
            Preferences.setInt(Prefkey.lastBookId, activeSplit0.book.bookId)
            Preferences.setInt(Prefkey.lastChapter, chapter_1)
            Preferences.setInt(Prefkey.lastVerse, getVerse_1BasedOnScrolls())
            Preferences.setString(Prefkey.lastVersionId, activeSplit0.versionId)
            val activeSplit1 = activeSplit1
            if (activeSplit1 == null) {
                Preferences.remove(Prefkey.lastSplitVersionId)
            } else {
                Preferences.setString(Prefkey.lastSplitVersionId, activeSplit1.versionId)
                Preferences.setString(Prefkey.lastSplitOrientation, splitHandleButton.orientation.name)
            }
        } finally {
            Preferences.unhold()
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
        Tracker.trackEvent("nav_goto_button_click")

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
        Tracker.trackEvent("nav_goto_button_long_click")
        if (history.size > 0) {
            MaterialDialog(this).show {
                withAdapter(HistoryAdapter())
            }
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
                Tracker.trackEvent("nav_search_click")
                menuSearch_click()
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

        // root contains exactly 2 children: toolbar and nontoolbar.
        // Need to move toolbar and nontoolbar in order to accomplish this.

        if (!fullScreen) {
            root.removeView(toolbar)
            root.removeView(nontoolbar)

            if (Preferences.getBoolean(R.string.pref_bottomToolbarOnText_key, R.bool.pref_bottomToolbarOnText_default)) {
                root.addView(nontoolbar)
                root.addView(toolbar)
            } else {
                root.addView(toolbar)
                root.addView(nontoolbar)
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
            configureTextAppearancePanelForSplitVersion()
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

        App.getLbm().sendBroadcast(Intent(ACTION_NIGHT_MODE_CHANGED))
    }

    private fun openVersionsDialog() {
        // If there is no db versions, immediately open manage version screen.
        if (S.db.listAllVersions().isEmpty()) {
            startActivity(VersionsActivity.createIntent())
            return
        }

        S.openVersionsDialog(this, activeSplit0.versionId) { mv ->
            trackVersionSelect(mv, false)
            loadVersion(mv)
        }
    }

    private fun openSplitVersionsDialog() {
        S.openVersionsDialogWithNone(this, activeSplit1?.versionId) { mv: MVersion? ->
            if (mv == null) { // closing split version
                disableSplitVersion()
            } else {
                trackVersionSelect(mv, true)
                val ok = loadSplitVersion(mv)
                if (ok) {
                    openSplitDisplay()
                    displaySplitFollowingMaster(getVerse_1BasedOnScrolls())
                } else {
                    disableSplitVersion()
                }
            }
        }
    }

    private fun trackVersionSelect(mv: MVersion?, isSplit: Boolean) {
        if (mv is MVersionDb) {
            val preset_name = mv.preset_name
            Tracker.trackEvent("versions_dialog_select", "is_split", isSplit, FirebaseAnalytics.Param.ITEM_NAME, preset_name ?: "no_preset_name")
        } else if (mv is MVersionInternal) {
            Tracker.trackEvent("versions_dialog_select", "is_split", isSplit, FirebaseAnalytics.Param.ITEM_NAME, "internal")
        }
    }

    private fun disableSplitVersion() {
        activeSplit1 = null
        closeSplitDisplay()

        configureTextAppearancePanelForSplitVersion()
    }

    private fun openSplitDisplay() {
        if (splitHandleButton.visibility == View.VISIBLE) {
            return // it's already split, no need to do anything
        }

        configureSplitSizes()

        bVersion.visibility = View.GONE
        actionMode?.invalidate()
        leftDrawer.handle.setSplitVersion(true)
    }

    fun configureSplitSizes() {
        splitHandleButton.visibility = View.VISIBLE

        var prop = Preferences.getFloat(Prefkey.lastSplitProp, Float.MIN_VALUE)
        if (prop == Float.MIN_VALUE || prop < 0f || prop > 1f) {
            prop = 0.5f // guard against invalid values
        }

        val splitHandleThickness = resources.getDimensionPixelSize(R.dimen.split_handle_thickness)
        if (splitHandleButton.orientation == SplitHandleButton.Orientation.vertical) {
            splitRoot.orientation = LinearLayout.VERTICAL

            val totalHeight = splitRoot.height
            val masterHeight = ((totalHeight - splitHandleThickness) * prop).toInt()

            run {
                // divide the screen space
                lsSplit0.setViewLayoutSize(ViewGroup.LayoutParams.MATCH_PARENT, masterHeight)
            }

            // no need to set height, because it has been set to match_parent, so it takes the remaining space.
            lsSplit1.setViewVisibility(View.VISIBLE)

            run {
                val lp = splitHandleButton.layoutParams
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = splitHandleThickness
                splitHandleButton.layoutParams = lp
            }
        } else {
            splitRoot.orientation = LinearLayout.HORIZONTAL

            val totalWidth = splitRoot.width
            val masterWidth = ((totalWidth - splitHandleThickness) * prop).toInt()

            run {
                // divide the screen space
                lsSplit0.setViewLayoutSize(masterWidth, ViewGroup.LayoutParams.MATCH_PARENT)
            }

            // no need to set width, because it has been set to match_parent, so it takes the remaining space.
            lsSplit1.setViewVisibility(View.VISIBLE)

            run {
                val lp = splitHandleButton.layoutParams
                lp.width = splitHandleThickness
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                splitHandleButton.layoutParams = lp
            }
        }
    }

    private fun closeSplitDisplay() {
        if (splitHandleButton.visibility == View.GONE) {
            return // it's already not split, no need to do anything
        }

        splitHandleButton.visibility = View.GONE
        lsSplit1.setViewVisibility(View.GONE)

        run { lsSplit0.setViewLayoutSize(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }

        bVersion.visibility = View.VISIBLE
        actionMode?.invalidate()
        leftDrawer.handle.setSplitVersion(false)
    }

    private fun menuSearch_click() {
        startActivity(SearchActivity.createIntent(activeSplit0.book.bookId))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RequestCodes.FromActivity.Goto && resultCode == Activity.RESULT_OK && data != null) {
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
        } else if (requestCode == RequestCodes.FromActivity.EditNote1 && resultCode == Activity.RESULT_OK) {
            reloadBothAttributeMaps()
        } else if (requestCode == RequestCodes.FromActivity.EditNote2 && resultCode == Activity.RESULT_OK) {
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

        displaySplitFollowingMaster(available_verse_1)

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

        val pericope_aris = mutableListOf<Int>()
        val pericope_blocks = mutableListOf<PericopeBlock>()
        val nblock = version.loadPericope(book.bookId, chapter_1, pericope_aris, pericope_blocks)

        val retainSelectedVerses = !uncheckAllVerses && chapter_1 == current_chapter_1
        setDataWithRetainSelectedVerses(cr, versesController, dataSetter, retainSelectedVerses, Ari.encode(book.bookId, chapter_1, 0), pericope_aris, pericope_blocks, nblock, verses, version, versionId)

        return true
    }

    // Moved from the old VersesView method
    private fun setDataWithRetainSelectedVerses(
        cr: ContentResolver,
        versesController: VersesController,
        dataSetter: (VersesDataModel) -> Unit,
        retainSelectedVerses: Boolean,
        ariBc: Int,
        pericope_aris: List<Int>,
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

        val versesAttributes = VerseAttributeLoader.load(S.db, cr, ariBc, verses)

        val newData = VersesDataModel(ariBc, verses, nblock, pericope_aris, pericope_blocks, version, versionId, versesAttributes)
        dataSetter(newData)

        if (selectedVerses_1 != null) {
            versesController.checkVerses(selectedVerses_1, true)
        }
    }

    private fun displaySplitFollowingMaster(verse_1: Int) {
        val activeSplit1 = activeSplit1
        if (activeSplit1 != null) { // split1
            val splitBook = activeSplit1.version.getBook(activeSplit0.book.bookId)
            if (splitBook == null) {
                lsSplit1.setEmptyMessage(getString(R.string.split_version_cant_display_verse, activeSplit0.book.reference(this.chapter_1), activeSplit1.version.shortName), S.applied().fontColor)
                dataSplit1 = VersesDataModel.EMPTY
            } else {
                lsSplit1.setEmptyMessage(null, S.applied().fontColor)
                this.uncheckVersesWhenActionModeDestroyed = false
                try {
                    loadChapterToVersesController(contentResolver, lsSplit1, { dataSplit1 = it }, activeSplit1.version, activeSplit1.versionId, splitBook, this.chapter_1, this.chapter_1, true)
                } finally {
                    this.uncheckVersesWhenActionModeDestroyed = true
                }
                lsSplit1.scrollToVerse(verse_1)
            }
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
        Tracker.trackEvent("nav_left_click")
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
        Tracker.trackEvent("nav_right_click")
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

    private fun bVersion_click() {
        Tracker.trackEvent("nav_version_click")
        openVersionsDialog()
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
            val markers = S.db.listMarkersForAriKind(ari, Marker.Kind.bookmark)
            if (markers.size == 1) {
                openBookmarkDialog(markers[0]._id)
            } else {
                MaterialDialog(this@IsiActivity).show {
                    title(R.string.edit_bookmark)
                    withAdapter(MultipleMarkerSelectAdapter(version, versionId, markers, Marker.Kind.bookmark))
                }
            }
        }

        fun openNoteDialog(_id: Long) {
            startActivityForResult(NoteActivity.createEditExistingIntent(_id), RequestCodes.FromActivity.EditNote1)
        }

        override fun onNoteAttributeClick(version: Version, versionId: String, ari: Int) {
            val markers = S.db.listMarkersForAriKind(ari, Marker.Kind.note)
            if (markers.size == 1) {
                openNoteDialog(markers[0]._id)
            } else {
                MaterialDialog(this@IsiActivity).show {
                    title(R.string.edit_note)
                    withAdapter(MultipleMarkerSelectAdapter(version, versionId, markers, Marker.Kind.note))
                }
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

            val textSizeMult = S.db.getPerVersionSettings(versionId).fontSizeMultiplier

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

                        val labels = S.db.listLabelsByMarker(marker)
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

                    holder.itemView.setBackgroundColor(S.applied().backgroundColor)
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
            S.db.getProgressMarkByPresetId(preset_id)?.let { progressMark ->
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
            } catch (e: ActivityNotFoundException) {
                MaterialDialog(this@IsiActivity).show {
                    message(R.string.maps_could_not_open)
                    positiveButton(R.string.ok)
                }
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

                        var footnoteDialog: MaterialDialog? = null

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
                                            MaterialDialog(this@IsiActivity).show {
                                                message(text = String.format(Locale.US, "Error: footnote at arif 0x%08x contains unsupported tag %s", arif, tag))
                                                positiveButton(R.string.ok)
                                            }
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
                                        Uri.parse(url)
                                    } else {
                                        Uri.parse("http://$url")
                                    }
                                    try {
                                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    } catch (ignored: Exception) {
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

                        footnoteDialog = MaterialDialog(this@IsiActivity).show {
                            title(text = title)
                            message(text = rendered)
                            positiveButton(R.string.ok)
                        }
                    } else {
                        MaterialDialog(this@IsiActivity).show {
                            message(text = String.format(Locale.US, "Error: footnote arif 0x%08x couldn't be loaded", arif))
                            positiveButton(R.string.ok)
                        }
                    }
                } else {
                    MaterialDialog(this@IsiActivity).show {
                        message(text = "Error: Unknown inline link type: $type")
                        positiveButton(R.string.ok)
                    }
                }
            }
        }
    }

    enum class RibkaEligibility {
        None,
        Main,
        Split,
    }

    /**
     * Check whether we are using a version eligible for ribka.
     */
    fun checkRibkaEligibility(): RibkaEligibility {
        val validPresetName = "in-ayt"

        val activeMVersion = activeSplit0.mv
        val activePresetName = if (activeMVersion is MVersionDb) activeMVersion.preset_name else null

        if (validPresetName == activePresetName) {
            return RibkaEligibility.Main
        }

        val splitPresetName = (activeSplit1?.mv as? MVersionDb)?.preset_name

        return if (validPresetName == splitPresetName) RibkaEligibility.Split else RibkaEligibility.None
    }

    fun reloadBothAttributeMaps() {
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
            S.db,
            contentResolver,
            data.ari_bc_,
            data.verses_
        )

        return data.copy(versesAttributes = versesAttributes)
    }

    /**
     * @param aris aris where the verses are to be checked for dictionary words.
     */
    private fun startDictionaryMode(aris: Set<Int>) {
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
        Tracker.trackEvent("left_drawer_display_click")
        setShowTextAppearancePanel(textAppearancePanel == null)
    }

    override fun cFullScreen_checkedChange(isChecked: Boolean) {
        Tracker.trackEvent("left_drawer_full_screen_click")
        setFullScreen(isChecked)
    }

    override fun cNightMode_checkedChange(isChecked: Boolean) {
        Tracker.trackEvent("left_drawer_night_mode_click")
        setNightMode(isChecked)
    }

    override fun cSplitVersion_checkedChange(cSplitVersion: SwitchCompat, isChecked: Boolean) {
        Tracker.trackEvent("left_drawer_split_click")
        if (isChecked) {
            cSplitVersion.isChecked = false // do it later, at the version chooser dialog
            openSplitVersionsDialog()
        } else {
            disableSplitVersion()
        }
    }

    override fun bProgressMarkList_click() {
        Tracker.trackEvent("left_drawer_progress_mark_list_click")
        if (S.db.countAllProgressMarks() > 0) {
            val dialog = ProgressMarkListDialog()
            dialog.progressMarkSelectedListener = { preset_id ->
                gotoProgressMark(preset_id)
            }
            dialog.show(supportFragmentManager, "dialog_progress_mark_list")
            leftDrawer.closeDrawer()
        } else {
            MaterialDialog(this).show {
                message(R.string.pm_activate_tutorial)
                positiveButton(R.string.ok)
            }
        }
    }

    override fun bProgress_click(preset_id: Int) {
        gotoProgressMark(preset_id)
    }

    override fun bCurrentReadingClose_click() {
        Tracker.trackEvent("left_drawer_current_reading_close_click")

        CurrentReading.clear()
    }

    override fun bCurrentReadingReference_click() {
        Tracker.trackEvent("left_drawer_current_reading_verse_reference_click")

        val aris = CurrentReading.get() ?: return

        val ari_start = aris[0]
        jumpToAri(ari_start)

        leftDrawer.closeDrawer()
    }

    private fun gotoProgressMark(preset_id: Int) {
        val progressMark = S.db.getProgressMarkByPresetId(preset_id) ?: return

        val ari = progressMark.ari

        if (ari != 0) {
            Tracker.trackEvent("left_drawer_progress_mark_pin_click_succeed")
            jumpToAri(ari)
        } else {
            Tracker.trackEvent("left_drawer_progress_mark_pin_click_failed")
            MaterialDialog(this).show {
                message(R.string.pm_activate_tutorial)
                positiveButton(R.string.ok)
            }
        }
    }

    companion object {
        const val ACTION_ATTRIBUTE_MAP_CHANGED = "yuku.alkitab.action.ATTRIBUTE_MAP_CHANGED"
        const val ACTION_ACTIVE_VERSION_CHANGED = "yuku.alkitab.base.IsiActivity.action.ACTIVE_VERSION_CHANGED"
        const val ACTION_NIGHT_MODE_CHANGED = "yuku.alkitab.base.IsiActivity.action.NIGHT_MODE_CHANGED"
        const val ACTION_NEEDS_RESTART = "yuku.alkitab.base.IsiActivity.action.NEEDS_RESTART"

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
