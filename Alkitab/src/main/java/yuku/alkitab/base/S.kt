package yuku.alkitab.base

import android.graphics.Color
import android.graphics.Typeface
import yuku.afw.storage.Preferences
import yuku.alkitab.base.config.AppConfig
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.services.StorageProvider
import yuku.alkitab.base.services.UiDimensionsProvider
import yuku.alkitab.base.services.VersionManager
import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.storage.SongDb
import yuku.alkitab.base.storage.SongDbHelper
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.FontManager
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version

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
        @Volatile
        var applied: CalculatedDimensions = calculateDimensionsFromPreferences()
    }

    @JvmStatic
    fun applied(): CalculatedDimensions {
        return CalculatedDimensionsHolder.applied
    }

    /** Re-derive [applied] from current preferences. Call after preference changes. */
    fun recalculate() {
        CalculatedDimensionsHolder.applied = calculateDimensionsFromPreferences()
    }

    /**
     * Snapshot of the currently active Bible version. Held behind a single
     * [Volatile] reference so readers always see a consistent (mVersion, version,
     * versionId) triple — previously the three fields could be observed mid-update.
     */
    private data class ActiveVersionState(
        val mVersion: MVersion,
        // [MVersion.getVersion] is @Nullable. Stored as-is so a caller that passes an
        // MVersion with no data file fails on the subsequent [activeVersion] read
        // (matching pre-refactor behaviour) rather than on the set.
        val version: Version?,
        val versionId: String,
    )

    private object ActiveVersionHolder {
        @Volatile
        var state: ActiveVersionState = run {
            // Load the version we want before everything, so we do not load it multiple times.
            val lastVersionId = Preferences.getString(Prefkey.lastVersionId)
            val actual = getVersionFromVersionId(lastVersionId) ?: getMVersionInternal()
            ActiveVersionState(actual, actual.version, actual.versionId)
        }
    }

    fun activeMVersion(): MVersion {
        return ActiveVersionHolder.state.mVersion
    }

    @JvmStatic
    fun activeVersion(): Version {
        return ActiveVersionHolder.state.version!!
    }

    fun activeVersionId(): String {
        return ActiveVersionHolder.state.versionId
    }

    @Synchronized
    fun setActiveVersion(mv: MVersion) {
        val version = mv.version
        val versionId = mv.versionId
        AppLog.d(TAG, "@@setActiveVersion version=$version versionId=$versionId")
        ActiveVersionHolder.state = ActiveVersionState(mv, version, versionId)
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
        val helper = SongDbHelper()
        val roomDb = yuku.alkitab.base.storage.room.SongRoomDatabase.get(App.context)
        // One-time copy of the legacy `SongInfo` / `SongBookInfo` tables
        // from the `SongDb` SQLite file into Room (REM-32). Idempotent —
        // a no-op after the first launch with this code, and safe to retry
        // if it fails partway.
        yuku.alkitab.base.storage.room.SongDbDataMigration.copyFromLegacyDbIfNeeded(roomDb, helper)
        SongDb(helper)
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

    /**
     * Adapter exposing [S]'s database access through the [StorageProvider] interface.
     * New code that only needs database access should depend on this (typically via
     * [yuku.alkitab.base.App.services]) instead of reaching into [S] directly.
     */
    @JvmField
    val storage: StorageProvider = object : StorageProvider {
        override val db get() = S.db
        override val songDb get() = S.songDb
    }

    /**
     * Adapter exposing [S]'s active-version state through the [VersionManager] interface.
     */
    @JvmField
    val versions: VersionManager = object : VersionManager {
        override fun activeVersion() = S.activeVersion()
        override fun activeMVersion() = S.activeMVersion()
        override fun activeVersionId() = S.activeVersionId()
        override fun setActiveVersion(mv: MVersion) = S.setActiveVersion(mv)
        override fun getVersionFromVersionId(versionId: String?) = S.getVersionFromVersionId(versionId)
        override fun getAvailableVersions() = S.getAvailableVersions()
        override fun getMVersionInternal() = S.getMVersionInternal()
    }

    /**
     * Adapter exposing [S]'s cached UI dimensions through the [UiDimensionsProvider] interface.
     */
    @JvmField
    val uiDimensions: UiDimensionsProvider = object : UiDimensionsProvider {
        override fun applied() = S.applied()
        override fun recalculate() = S.recalculate()
    }
}
