package yuku.alkitab.base.actionmode

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import androidx.core.app.ShareCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.NoteActivity
import yuku.alkitab.base.config.AppConfig
import yuku.alkitab.base.dialog.TypeBookmarkDialog
import yuku.alkitab.base.dialog.TypeHighlightDialog
import yuku.alkitab.base.dialog.VersesDialog
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.model.MVersionDb
import yuku.alkitab.base.util.AlkitabGptIntegration
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.ClipboardUtil
import yuku.alkitab.base.util.ExtensionManager
import yuku.alkitab.base.util.FormattedVerseText
import yuku.alkitab.base.util.OtherAppIntegration
import yuku.alkitab.base.util.RequestCodes
import yuku.alkitab.base.util.ShareUrl
import yuku.alkitab.base.util.VerseTextFormatter
import yuku.alkitab.base.verses.VersesDataModel
import yuku.alkitab.base.widget.VerseRenderer
import yuku.alkitab.debug.R
import yuku.alkitab.model.Book
import yuku.alkitab.model.Version
import yuku.alkitab.ribka.RibkaReportActivity
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList

private const val TAG = "VerseActionModeController"
private const val EXTRA_verseUrl = "verseUrl"

/**
 * Action-mode callback for verse selection (copy, share, bookmark, highlight,
 * compare, dictionary, extensions, etc.). Extracted from `IsiActivity` — see
 * REM-07 in docs/tech-debt-remediation.md.
 *
 * The controller is deliberately kept free of direct Activity references: it
 * reads state through [host] and invokes operations through [actions]. Framework
 * calls that genuinely require an Activity (dialogs, fragment transactions,
 * `startActivity`) go through `host.activity`.
 */
class VerseActionModeController(
    private val host: VerseActionModeHost,
    private val actions: VerseActionModeActions,
) : ActionMode.Callback {

    private val MENU_GROUP_EXTENSIONS = Menu.FIRST + 1
    private val MENU_EXTENSIONS_FIRST_ID = 0x1000

    private val extensions = mutableListOf<ExtensionManager.Info>()

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        host.activity.menuInflater.inflate(R.menu.context_isi, menu)

        AppLog.d(TAG, "@@onCreateActionMode")

        if (host.hasEsvsbAsal) {
            val esvsb = menu.findItem(R.id.menuEsvsb)
            esvsb?.isVisible = true
        }

        // show book name and chapter
        val reference = host.activeSplit0Book.reference(host.chapter_1)
        mode.title = reference

        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val menuAddBookmark = menu.findItem(R.id.menuAddBookmark)
        val menuAddNote = menu.findItem(R.id.menuAddNote)
        val menuCompare = menu.findItem(R.id.menuCompare)

        val selected = host.selectedVersesSplit0_1
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

        val split = host.activeSplit1Version != null

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
            mode.subtitle = host.activity.getString(R.string.verse_select_multiple_verse_selected, selected.size().toString())
        }

        val menuGuide = menu.findItem(R.id.menuGuide)
        val menuCommentary = menu.findItem(R.id.menuCommentary)
        val menuDictionary = menu.findItem(R.id.menuDictionary)

        // force-show these items on sw600dp, otherwise never show
        val showAsAction = if (host.activity.resources.configuration.smallestScreenWidthDp >= 600) MenuItem.SHOW_AS_ACTION_ALWAYS else MenuItem.SHOW_AS_ACTION_NEVER
        menuGuide.setShowAsActionFlags(showAsAction)
        menuCommentary.setShowAsActionFlags(showAsAction)
        menuDictionary.setShowAsActionFlags(showAsAction)

        // set visibility according to appconfig
        val c = AppConfig.get()
        menuGuide.isVisible = c.menuGuide
        menuCommentary.isVisible = c.menuCommentary

        // do not show dictionary item if not needed because of auto-lookup from
        menuDictionary.isVisible = c.menuDictionary && !Preferences.getBoolean(host.activity.getString(R.string.pref_autoDictionaryAnalyze_key), host.activity.resources.getBoolean(R.bool.pref_autoDictionaryAnalyze_default))

        // Alkitab GPT is only offered when that app is already installed. The lookup that answers
        // that runs off the main thread, so its result can land after the action mode was created;
        // deciding visibility here (rather than in onCreateActionMode) means every `invalidate()`
        // picks up the answer as soon as it arrives.
        menu.findItem(R.id.menuAlkitabGpt).isVisible = host.hasAlkitabGpt

        val menuRibkaReport = menu.findItem(R.id.menuRibkaReport)
        menuRibkaReport.isVisible = single && actions.checkRibkaEligibility() != RibkaEligibility.None

        val menuPlayAudioFromVerse = menu.findItem(R.id.menuPlayAudioFromVerse)
        menuPlayAudioFromVerse.isVisible = single && actions.isAudioAvailableForVerseAction()

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
        val selected = host.selectedVersesSplit0_1

        if (selected.size() == 0) return true

        return when (val itemId = item.itemId) {
            R.id.menuCopy, R.id.menuCopySplit0, R.id.menuCopySplit1, R.id.menuCopyBothSplits -> {
                // copy, can be multiple verses
                val reference = VerseTextFormatter.referenceFromSelectedVerses(selected, host.activeSplit0Book, host.chapter_1)
                val activeSplit1Version = host.activeSplit1Version
                val t = if (itemId == R.id.menuCopy || itemId == R.id.menuCopySplit0 || itemId == R.id.menuCopyBothSplits || activeSplit1Version == null) {
                    buildCopyShareText(selected, reference, isSplitVersion = false)
                } else { // menuCopySplit1, do not use split0 reference
                    val book = host.activeSplit1BookById(host.activeSplit0Book.bookId) ?: host.activeSplit0Book
                    buildCopyShareText(selected, VerseTextFormatter.referenceFromSelectedVerses(selected, book, host.chapter_1), isSplitVersion = true)
                }

                if (itemId == R.id.menuCopyBothSplits && activeSplit1Version != null) {
                    val book = host.activeSplit1BookById(host.activeSplit0Book.bookId) ?: host.activeSplit0Book
                    appendSplitTextForCopyShare(book, host.selectedVersesSplit1_1, t)
                }

                val textToCopy = t[0]
                val textToSubmit = t[1]

                val meta = pickShareUrlMetadata(useSplit1 = itemId == R.id.menuCopySplit1 && activeSplit1Version != null)

                ShareUrl.make(
                    activity = host.activity,
                    immediatelyCancel = !Preferences.getBoolean(host.activity.getString(R.string.pref_copyWithShareUrl_key), host.activity.resources.getBoolean(R.bool.pref_copyWithShareUrl_default)),
                    verseText = textToSubmit,
                    ari_bc = Ari.encode(meta.bookId, host.chapter_1, 0),
                    selectedVerses_1 = selected,
                    reference = reference,
                    version = meta.version,
                    preset_name = MVersionDb.presetNameFromVersionId(meta.versionId),
                    callback = object : ShareUrl.Callback {
                        override fun onSuccess(shareUrl: String) {
                            ClipboardUtil.copyToClipboard("$textToCopy\n\n$shareUrl")
                        }

                        override fun onUserCancel() {
                            ClipboardUtil.copyToClipboard(textToCopy)
                        }

                        override fun onError(e: Exception) {
                            AppLog.e(TAG, "Error in ShareUrl, copying without shareUrl", e)
                            ClipboardUtil.copyToClipboard(textToCopy)
                        }

                        override fun onFinally() {
                            actions.uncheckAllVersesSplit0()

                            Snackbar.make(host.root, host.activity.getString(R.string.alamat_sudah_disalin, reference), Snackbar.LENGTH_SHORT).show()
                            mode.finish()
                        }
                    }
                )

                true
            }

            R.id.menuShare, R.id.menuShareSplit0, R.id.menuShareSplit1, R.id.menuShareBothSplits -> {
                // share, can be multiple verses
                val reference = VerseTextFormatter.referenceFromSelectedVerses(selected, host.activeSplit0Book, host.chapter_1)
                val activeSplit1Version = host.activeSplit1Version

                val t = if (itemId == R.id.menuShare || itemId == R.id.menuShareSplit0 || itemId == R.id.menuShareBothSplits || activeSplit1Version == null) {
                    buildCopyShareText(selected, reference, isSplitVersion = false)
                } else { // menuShareSplit1, do not use split0 reference
                    val book = host.activeSplit1BookById(host.activeSplit0Book.bookId) ?: host.activeSplit0Book
                    buildCopyShareText(selected, VerseTextFormatter.referenceFromSelectedVerses(selected, book, host.chapter_1), isSplitVersion = true)
                }

                if (itemId == R.id.menuShareBothSplits && activeSplit1Version != null) {
                    val book = host.activeSplit1BookById(host.activeSplit0Book.bookId) ?: host.activeSplit0Book
                    appendSplitTextForCopyShare(book, host.selectedVersesSplit1_1, t)
                }

                val textToShare = t[0]
                val textToSubmit = t[1]

                val intent = ShareCompat.IntentBuilder(host.activity)
                    .setType("text/plain")
                    .setSubject(reference)
                    .intent

                val meta = pickShareUrlMetadata(useSplit1 = itemId == R.id.menuShareSplit1 && activeSplit1Version != null)

                ShareUrl.make(
                    activity = host.activity,
                    immediatelyCancel = !Preferences.getBoolean(host.activity.getString(R.string.pref_copyWithShareUrl_key), host.activity.resources.getBoolean(R.bool.pref_copyWithShareUrl_default)),
                    verseText = textToSubmit,
                    ari_bc = Ari.encode(meta.bookId, host.chapter_1, 0),
                    selectedVerses_1 = selected,
                    reference = reference,
                    version = meta.version,
                    preset_name = MVersionDb.presetNameFromVersionId(meta.versionId),
                    callback = object : ShareUrl.Callback {
                        override fun onSuccess(shareUrl: String) {
                            intent.putExtra(Intent.EXTRA_TEXT, "$textToShare\n\n$shareUrl")
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
                            host.activity.startActivity(Intent.createChooser(intent, host.activity.getString(R.string.bagikan_alamat, reference)))

                            actions.uncheckAllVersesSplit0()
                            mode.finish()
                        }
                    }
                )
                true
            }

            R.id.menuPlayAudioFromVerse -> {
                actions.playAudioFromVerse(selected.get(0))
                mode.finish()
                true
            }

            R.id.menuCompare -> {
                val ari = Ari.encode(host.activeSplit0Book.bookId, host.chapter_1, selected.get(0))
                val dialog = VersesDialog.newCompareInstance(ari)
                dialog.listener = object : VersesDialog.VersesDialogListener() {
                    override fun onComparedVerseSelected(ari: Int, mversion: MVersion) {
                        actions.loadVersion(mversion)
                        dialog.dismiss()
                    }
                }

                // Allow state loss to prevent
                // https://console.firebase.google.com/u/0/project/alkitab-host-hrd/crashlytics/app/android:yuku.alkitab/issues/b80d5209ee90ebd9c5eb30f87f19c85f
                val ft = host.activity.supportFragmentManager.beginTransaction()
                ft.add(dialog, "compare_dialog")
                ft.commitAllowingStateLoss()

                true
            }

            R.id.menuAddBookmark -> {

                // contract: this menu only appears when contiguous verses are selected
                if (selected.get(selected.size() - 1) - selected.get(0) != selected.size() - 1) {
                    throw RuntimeException("Non contiguous verses when adding bookmark: $selected")
                }

                val ari = Ari.encode(host.activeSplit0Book.bookId, host.chapter_1, selected.get(0))
                val verseCount = selected.size()

                // always create a new bookmark
                val dialog = TypeBookmarkDialog.NewBookmark(host.activity, ari, verseCount)
                dialog.setListener {
                    actions.uncheckAllVersesSplit0()
                    actions.reloadBothAttributeMaps()
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

                val ari = Ari.encode(host.activeSplit0Book.bookId, host.chapter_1, selected.get(0))
                val verseCount = selected.size()

                // always create a new note
                host.activity.startActivityForResult(NoteActivity.createNewNoteIntent(host.activeSplit0Version.referenceWithVerseCount(ari, verseCount), ari, verseCount), RequestCodes.FromActivity.EditNote2)
                mode.finish()

                true
            }

            R.id.menuAddHighlight -> {
                val ariBc = Ari.encode(host.activeSplit0Book.bookId, host.chapter_1, 0)
                val colorRgb = App.services.storage.db.getHighlightColorRgb(ariBc, selected)

                val listener = TypeHighlightDialog.Listener {
                    actions.uncheckAllVersesSplit0()
                    actions.reloadBothAttributeMaps()
                }

                val reference = VerseTextFormatter.referenceFromSelectedVerses(selected, host.activeSplit0Book, host.chapter_1)
                if (selected.size() == 1) {
                    val ftr = VerseRenderer.FormattedTextResult()
                    val ari = Ari.encodeWithBc(ariBc, selected.get(0))
                    val rawVerseText = host.activeSplit0Version.loadVerseText(ari) ?: ""
                    val info = App.services.storage.db.getHighlightColorRgb(ari)

                    VerseRenderer.render(ari = ari, text = rawVerseText, ftr = ftr)
                    TypeHighlightDialog(host.activity, ari, listener, colorRgb, info, reference, ftr.result)
                } else {
                    TypeHighlightDialog(host.activity, ariBc, selected, listener, colorRgb, reference)
                }
                mode.finish()
                true
            }

            R.id.menuEsvsb -> {

                val ari = Ari.encode(host.activeSplit0Book.bookId, host.chapter_1, selected.get(0))

                try {
                    val intent = Intent("yuku.esvsbasal.action.GOTO")
                    intent.putExtra("ari", ari)
                    host.activity.startActivity(intent)
                } catch (e: Exception) {
                    AppLog.e(TAG, "ESVSB starting", e)
                }
                true
            }

            R.id.menuAlkitabGpt -> {

                // Alkitab GPT takes a passage, not a verse list, so a non-contiguous selection is
                // sent as the range that spans it.
                val intent = AlkitabGptIntegration.chatPopupIntent(
                    bookName = host.activeSplit0Book.shortName,
                    chapter_1 = host.chapter_1,
                    verseStart_1 = selected.get(0),
                    verseEnd_1 = selected.get(selected.size() - 1),
                )

                try {
                    host.activity.startActivity(intent)
                } catch (e: Exception) {
                    AppLog.e(TAG, "Alkitab GPT starting", e)
                }
                true
            }

            R.id.menuGuide -> {

                val ari = Ari.encode(host.activeSplit0Book.bookId, host.chapter_1, 0)

                try {
                    host.activity.packageManager.getPackageInfo("org.sabda.pedia", 0)

                    val intent = Intent("org.sabda.pedia.action.VIEW")
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.putExtra("ari", ari)
                    host.activity.startActivity(intent)
                } catch (_: PackageManager.NameNotFoundException) {
                    OtherAppIntegration.openMarket(host.activity, "org.sabda.pedia")
                }
                true
            }

            R.id.menuCommentary -> {

                val ari = Ari.encode(host.activeSplit0Book.bookId, host.chapter_1, selected.get(0))

                try {
                    host.activity.packageManager.getPackageInfo("org.sabda.tafsiran", 0)

                    val intent = Intent("org.sabda.tafsiran.action.VIEW")
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.putExtra("ari", ari)
                    host.activity.startActivity(intent)
                } catch (_: PackageManager.NameNotFoundException) {
                    OtherAppIntegration.openMarket(host.activity, "org.sabda.tafsiran")
                }
                true
            }

            R.id.menuDictionary -> {

                val ariBc = Ari.encode(host.activeSplit0Book.bookId, host.chapter_1, 0)
                val aris = HashSet<Int>()
                var i = 0
                val len = selected.size()
                while (i < len) {
                    val verse_1 = selected.get(i)
                    val ari = Ari.encodeWithBc(ariBc, verse_1)
                    aris.add(ari)
                    i++
                }

                actions.startDictionaryMode(aris)
                true
            }

            R.id.menuRibkaReport -> {

                val ribkaEligibility = actions.checkRibkaEligibility()
                if (ribkaEligibility != RibkaEligibility.None) {
                    val ari = Ari.encode(host.activeSplit0Book.bookId, host.chapter_1, selected.get(0))

                    val reference: String?
                    val verseText: String?
                    val versionDescription: String?

                    if (ribkaEligibility == RibkaEligibility.Main) {
                        reference = host.activeSplit0Version.reference(ari)
                        verseText = host.activeSplit0Version.loadVerseText(ari)
                        versionDescription = host.activeSplit0MVersion.description
                    } else {
                        reference = host.activeSplit1Version?.reference(ari)
                        verseText = host.activeSplit1Version?.loadVerseText(ari)
                        versionDescription = host.activeSplit1MVersion?.description
                    }

                    if (reference != null && verseText != null) {
                        host.activity.startActivity(RibkaReportActivity.createIntent(ari, reference, verseText, versionDescription))
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
                val ariBc = Ari.encode(host.activeSplit0Book.bookId, host.chapter_1, 0)
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

                        val verseText = host.dataSplit0.getVerseText(verse_1)
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
                    host.activity.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    MaterialAlertDialogBuilder(host.activity)
                        .setMessage("Error ANFE starting extension\n\n${extension.activityInfo.packageName}/${extension.activityInfo.name}")
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }

                true
            }

            else -> false
        }
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        actions.onActionModeDestroyed()

        // FIXME even with this guard, verses are still unchecked when switching version while both Fullscreen and Split is active.
        // This guard only fixes unchecking of verses when in fullscreen mode.
        if (host.uncheckVersesWhenActionModeDestroyed) {
            actions.uncheckAllVersesSplit0()
        }
    }

    /**
     * Resolves the two "copy/share" preferences and the split/non-split version
     * short-name choice, then delegates to [VerseTextFormatter].
     */
    private fun buildCopyShareText(
        selectedVerses_1: IntArrayList,
        reference: CharSequence,
        isSplitVersion: Boolean,
    ): Array<String> {
        val data: VersesDataModel
        val version: Version
        if (isSplitVersion) {
            // Fallback to primary version for safety (matches previous IsiActivity behavior).
            data = host.dataSplit1
            version = host.activeSplit1Version ?: host.activeSplit0Version
        } else {
            data = host.dataSplit0
            version = host.activeSplit0Version
        }

        val versionShortName = if (Preferences.getBoolean(host.activity.getString(R.string.pref_copyWithVersionName_key), host.activity.resources.getBoolean(R.bool.pref_copyWithVersionName_default))) {
            version.shortName
        } else {
            null
        }

        val includeVerseNumbers = Preferences.getBoolean(host.activity.getString(R.string.pref_copyWithVerseNumbers_key), false)

        return VerseTextFormatter.prepareTextForCopyShare(
            selectedVerses_1 = selectedVerses_1,
            reference = reference,
            data = data,
            versionShortName = versionShortName,
            includeVerseNumbers = includeVerseNumbers,
        )
    }

    /**
     * @param t [0] is text to copy, [1] is text to submit
     */
    private fun appendSplitTextForCopyShare(book: Book?, selectedVerses_1: IntArrayList, t: Array<String>) {
        if (book == null) return
        val referenceSplit = VerseTextFormatter.referenceFromSelectedVerses(selectedVerses_1, book, host.chapter_1)
        val a = buildCopyShareText(selectedVerses_1, referenceSplit, isSplitVersion = true)
        t[0] += "\n\n${a[0]}"
        t[1] += "\n\n${a[1]}"
    }

    /**
     * The share-URL metadata (version, versionId, book id) for the current copy/share click.
     *
     * Pass `useSplit1 = true` only for the "...Split1" menu variants — the clipboard/share
     * text is built from split1, so the URL must also point at split1. For the "...BothSplits"
     * variants we intentionally pass `useSplit1 = false`: both verse texts are included in the
     * payload, but the URL is anchored to the primary (split0) version.
     */
    private fun pickShareUrlMetadata(useSplit1: Boolean): ShareUrlMetadata {
        return if (useSplit1) {
            ShareUrlMetadata(
                bookId = host.activeSplit1BookById(host.activeSplit0Book.bookId)?.bookId ?: host.activeSplit0Book.bookId,
                version = host.activeSplit1Version ?: host.activeSplit0Version,
                versionId = host.activeSplit1VersionId ?: host.activeSplit0VersionId,
            )
        } else {
            ShareUrlMetadata(
                bookId = host.activeSplit0Book.bookId,
                version = host.activeSplit0Version,
                versionId = host.activeSplit0VersionId,
            )
        }
    }

    private data class ShareUrlMetadata(
        val bookId: Int,
        val version: Version,
        val versionId: String,
    )
}
