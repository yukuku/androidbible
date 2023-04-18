package yuku.alkitab.base

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import yuku.afw.storage.Preferences
import yuku.alkitab.base.config.AppConfig
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.storage.SongDb
import yuku.alkitab.base.storage.SongDbHelper
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.FontManager
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version
import yuku.alkitab.versionmanager.VersionsActivity

object S {
    const val TAG = "S"

    /**
     * Values applied from settings, so we do not need to calculate it many times.
     */
    class CalculatedDimensions {
        /**
         * in dp
         */
        @JvmField
        var fontSize2dp = 0f

        @JvmField
        var fontFace: Typeface? = null

        @JvmField
        var lineSpacingMult = 0f

        @JvmField
        var fontBold = 0

        @JvmField
        var fontColor = 0

        @JvmField
        var fontRedColor = 0

        @JvmField
        var backgroundColor = 0

        @JvmField
        var verseNumberColor = 0

        /**
         * 0.f to 1.f
         */
        @JvmField
        var backgroundBrightness = 0f

        // everything below is in px
        @JvmField
        var indentParagraphFirst = 0

        @JvmField
        var indentParagraphRest = 0

        @JvmField
        var indentSpacing1 = 0

        @JvmField
        var indentSpacing2 = 0

        @JvmField
        var indentSpacing3 = 0

        @JvmField
        var indentSpacing4 = 0

        @JvmField
        var indentSpacingExtra = 0

        @JvmField
        var paragraphSpacingBefore = 0

        @JvmField
        var pericopeSpacingTop = 0

        @JvmField
        var pericopeSpacingBottom = 0
    }

    private object CalculatedDimensionsHolder {
        var applied = calculateDimensionsFromPreferences()
    }

    @JvmStatic
    fun applied(): CalculatedDimensions {
        return CalculatedDimensionsHolder.applied
    }

    // The process-global active Bible version.
    private object ActiveVersionHolder {
        var activeMVersion: MVersion? = null
        var activeVersion: Version? = null
        var activeVersionId: String? = null

        init {
            // Load the version we want before everything, so we do not load it multiple times.
            val lastVersionId = Preferences.getString(Prefkey.lastVersionId)
            val actual = getVersionFromVersionId(lastVersionId) ?: getMVersionInternal()
            setActiveVersion(actual)
        }

        @Synchronized
        fun setActiveVersion(mv: MVersion) {
            val version = mv.version
            val versionId = mv.versionId
            activeMVersion = mv
            val trace = if (BuildConfig.DEBUG) Throwable().fillInStackTrace() else null
            AppLog.d(TAG, "@@setActiveVersion version=$version versionId=$versionId", trace)
            activeVersion = version
            activeVersionId = versionId
        }
    }

    fun activeMVersion(): MVersion {
        return ActiveVersionHolder.activeMVersion!!
    }

    @JvmStatic
    fun activeVersion(): Version {
        return ActiveVersionHolder.activeVersion!!
    }

    fun activeVersionId(): String {
        return ActiveVersionHolder.activeVersionId!!
    }

    @Synchronized
    fun setActiveVersion(mv: MVersion) {
        ActiveVersionHolder.setActiveVersion(mv)
    }

    /**
     * @return null when the specified versionId is not found OR we really want internal.
     */
    fun getVersionFromVersionId(versionId: String?): MVersion? {
        if (versionId == null || MVersionInternal.getVersionInternalId() == versionId) {
            return null // internal is made the same as null
        }

        // let's look at yes versions
        for (mvDb in db.listAllVersions()) {
            if (mvDb.versionId == versionId) {
                return if (mvDb.hasDataFile()) {
                    mvDb
                } else {
                    // the data file is not available
                    null
                }
            }
        }
        return null // not known
    }

    fun recalculateAppliedValuesBasedOnPreferences() {
        CalculatedDimensionsHolder.applied = calculateDimensionsFromPreferences()
    }

    fun calculateDimensionsFromPreferences(): CalculatedDimensions {
        val res = CalculatedDimensions()
        val resources = App.context.resources

        //# configure font size
        run { res.fontSize2dp = Preferences.getFloat(Prefkey.ukuranHuruf2, resources.getInteger(R.integer.pref_ukuranHuruf2_default).toFloat()) }

        //# configure fonts
        run {
            res.fontFace = FontManager.typeface(Preferences.getString(Prefkey.jenisHuruf, null))
            res.lineSpacingMult = Preferences.getFloat(Prefkey.lineSpacingMult, 1.15f)
            res.fontBold = if (Preferences.getBoolean(Prefkey.boldHuruf, false)) Typeface.BOLD else Typeface.NORMAL
        }

        //# configure text color, red text color, bg color, and verse color
        run {
            if (Preferences.getBoolean(Prefkey.is_night_mode, false)) {
                res.fontColor = Preferences.getInt(R.string.pref_textColor_night_key, R.integer.pref_textColor_night_default)
                res.backgroundColor = Preferences.getInt(R.string.pref_backgroundColor_night_key, R.integer.pref_backgroundColor_night_default)
                res.verseNumberColor = Preferences.getInt(R.string.pref_verseNumberColor_night_key, R.integer.pref_verseNumberColor_night_default)
                res.fontRedColor = Preferences.getInt(R.string.pref_redTextColor_night_key, R.integer.pref_redTextColor_night_default)
            } else {
                res.fontColor = Preferences.getInt(R.string.pref_textColor_key, R.integer.pref_textColor_default)
                res.backgroundColor = Preferences.getInt(R.string.pref_backgroundColor_key, R.integer.pref_backgroundColor_default)
                res.verseNumberColor = Preferences.getInt(R.string.pref_verseNumberColor_key, R.integer.pref_verseNumberColor_default)
                res.fontRedColor = Preferences.getInt(R.string.pref_redTextColor_key, R.integer.pref_redTextColor_default)
            }

            // calculation of backgroundColor brightness. Used somewhere else.
            run {
                val c = res.backgroundColor
                res.backgroundBrightness = 0.30f * Color.red(c) + 0.59f * Color.green(c) + 0.11f * Color.blue(c) * 0.003921568627f
            }
        }
        val scaleBasedOnFontSize = res.fontSize2dp / 17f
        res.indentParagraphFirst = (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.indentParagraphFirst) + 0.5f).toInt()
        res.indentParagraphRest = (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.indentParagraphRest) + 0.5f).toInt()
        res.indentSpacing1 = (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.indent_1) + 0.5f).toInt()
        res.indentSpacing2 = (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.indent_2) + 0.5f).toInt()
        res.indentSpacing3 = (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.indent_3) + 0.5f).toInt()
        res.indentSpacing4 = (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.indent_4) + 0.5f).toInt()
        res.indentSpacingExtra = (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.indentExtra) + 0.5f).toInt()
        res.paragraphSpacingBefore = (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.paragraphSpacingBefore) + 0.5f).toInt()
        res.pericopeSpacingTop = (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.pericopeSpacingTop) + 0.5f).toInt()
        res.pericopeSpacingBottom = (scaleBasedOnFontSize * resources.getDimensionPixelOffset(R.dimen.pericopeSpacingBottom) + 0.5f).toInt()
        return res
    }

    @JvmStatic
    val db: InternalDb by lazy {
        InternalDb(InternalDbHelper(App.context))
    }

    @JvmStatic
    val songDb: SongDb by lazy {
        SongDb(SongDbHelper())
    }

    /**
     * Returns the list of versions that are:
     * 1. internal, or
     * 2. database versions that have the data file and active
     */
    @JvmStatic
    fun getAvailableVersions(): List<MVersion> {
        val res = mutableListOf<MVersion>()

        // 1. Internal version
        res.add(getMVersionInternal())

        // 2. Database versions
        for (mvDb in db.listAllVersions()) {
            if (mvDb.hasDataFile() && mvDb.active) {
                res.add(mvDb)
            }
        }

        // sort based on ordering
        res.sortWith { lhs, rhs -> lhs.ordering - rhs.ordering }

        return res
    }

    /**
     * Get the internal version model. This does not return a singleton. The ordering is the latest taken from preferences.
     */
    fun getMVersionInternal(): MVersionInternal {
        val ac = AppConfig.get()
        val res = MVersionInternal()
        res.locale = ac.internalLocale
        res.shortName = ac.internalShortName
        res.longName = ac.internalLongName
        res.description = null
        res.ordering = Preferences.getInt(Prefkey.internal_version_ordering, MVersionInternal.DEFAULT_ORDERING)
        return res
    }

    fun openVersionsDialog(activity: Activity, selectedVersionId: String, onVersionSelected: (MVersion) -> Unit) {
        val versions = getAvailableVersions()

        // determine the currently selected one
        var selected = -1
        for (i in versions.indices) {
            if (versions[i].versionId == selectedVersionId) {
                selected = i
                break
            }
        }

        val options = versions.map { it.longName }
        MaterialDialog(activity)
            .listItemsSingleChoice(items = options, initialSelection = selected, waitForPositiveButton = false) { dialog, index, _ ->
                if (index >= 0) {
                    val mv = versions[index]
                    onVersionSelected(mv)
                    dialog.dismiss()
                }
            }
            .positiveButton(R.string.versi_lainnya) {
                activity.startActivity(VersionsActivity.createIntent())
            }
            .show()
    }

    fun openVersionsDialogWithNone(activity: Activity, selectedVersionId: String?, onVersionSelected: (MVersion?) -> Unit) {
        val versions = getAvailableVersions()

        // determine the currently selected one
        val selected = if (selectedVersionId == null) {
            0 // "none"
        } else {
            versions.indexOfFirst { it.versionId == selectedVersionId }
        }

        val options = listOf(activity.getString(R.string.split_version_none)) + versions.map { it.longName }

        MaterialDialog(activity)
            .listItemsSingleChoice(items = options, initialSelection = selected, waitForPositiveButton = false) { dialog, index, _ ->
                if (index == 0) {
                    onVersionSelected(null)
                } else if (index > 0) {
                    val mv = versions[index - 1]
                    onVersionSelected(mv)
                    dialog.dismiss()
                }
            }
            .positiveButton(R.string.versi_lainnya) {
                activity.startActivity(VersionsActivity.createIntent())
            }
            .show()
    }
}
