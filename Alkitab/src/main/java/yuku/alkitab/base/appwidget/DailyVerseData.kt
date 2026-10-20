package yuku.alkitab.base.appwidget

import java.io.IOException
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Random
import yuku.afw.App
import yuku.afw.storage.Preferences
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.model.VersionImpl
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version
import yuku.alkitab.util.IntArrayList
import yuku.bintex.BintexReader

/** Handles the predefined daily verses and per-widget settings. */
object DailyVerseData {
    private val TAG = DailyVerseData::class.java.simpleName
    private var dailyVerses: IntArrayList? = null

    class SavedState {
        @JvmField var versionId: String? = null
        @JvmField var darkText: Boolean = false
        @JvmField var hideAppIcon: Boolean = false
        @JvmField var textSize: Float = 0f
        /** Legacy */
        @JvmField var transparentBackground: Boolean = false
        @JvmField var backgroundAlpha: Int = 0 // 0 to 255
        @JvmField var click: Int = 0
    }

    data class ResolvedVersion(val version: Version, val isFallback: Boolean)

    /**
     * Returns the selected verse addresses, or null if no available passage is found.
     * [savedState] is changed and saved when unavailable verses are skipped.
     * [direction] determines how the click counter advances between attempts.
     */
    @JvmStatic
    fun getAris(appWidgetId: Int, savedState: SavedState, version: Version, direction: Int): IntArray? {
        val allDailyVerses = listAllDailyVerses()
        var isSavedStateChanged = false
        var aris: IntArray? = null
        val maxTries = 20
        for (trial in 0 until maxTries) {
            val index = getIndexFromSeed(appWidgetId, allDailyVerses.size(), savedState.click)
            val encoded = allDailyVerses.get(index)
            val verseCount = encoded and 0xff
            val candidate = IntArray(verseCount) { (encoded ushr 8) + it }

            // All verses must be available in the selected version.
            if (candidate.all { version.loadVerseText(it) != null }) {
                aris = candidate
                break
            }

            AppLog.d(TAG, "ari 0x${Integer.toHexString(candidate[0])} verseCount=$verseCount click=${savedState.click} are not available in version ${savedState.versionId}")
            if (trial != maxTries - 1) {
                savedState.click += direction
                isSavedStateChanged = true
            }
        }

        if (isSavedStateChanged) {
            saveSavedState(appWidgetId, savedState)
        }
        return aris
    }

    private fun getIndexFromSeed(appWidgetId: Int, size: Int, click: Int): Int {
        val calendar = GregorianCalendar.getInstance()
        val year = calendar.get(Calendar.YEAR).toLong()
        val day = calendar.get(Calendar.DAY_OF_YEAR).toLong()
        val fifteenSecs = if (BuildConfig.DEBUG) {
            calendar.get(Calendar.HOUR_OF_DAY) * 240 + calendar.get(Calendar.MINUTE) * 4 + calendar.get(Calendar.SECOND) / 15
        } else {
            0
        }
        val randomDay = (((year - 1900) shl 9) or day) + fifteenSecs
        val seed = (appWidgetId.toLong() shl 20) or (randomDay + click)
        return Random(seed).nextInt(size)
    }

    @Synchronized
    private fun listAllDailyVerses(): IntArrayList {
        dailyVerses?.let { return it }
        val loadedVerses = IntArrayList()
        try {
            BintexReader(App.context.resources.openRawResource(R.raw.daily_verses_bt)).use { reader ->
                while (true) {
                    val ari = reader.readInt()
                    if (ari == -1) break
                    val verseCount = reader.readUint8()
                    loadedVerses.add((ari shl 8) or verseCount)
                }
            }
        } catch (e: IOException) {
            throw RuntimeException("Error reading daily verses", e)
        }
        dailyVerses = loadedVerses
        return loadedVerses
    }

    @JvmStatic
    fun getVersion(versionId: String?): Version = resolveVersion(versionId).version

    @JvmStatic
    fun resolveVersion(versionId: String?): ResolvedVersion {
        if (versionId == MVersionInternal.getVersionInternalId()) {
            return ResolvedVersion(VersionImpl.getInternalVersion(), isFallback = false)
        }

        if (versionId != null) {
            for (version in yuku.alkitab.base.App.services.storage.db.listAllVersions()) {
                if (version.versionId == versionId) {
                    if (version.hasDataFile()) {
                        version.version?.let { return ResolvedVersion(it, isFallback = false) }
                    }
                    break
                }
            }

            AppLog.w(TAG, "Version selected for app widget: $versionId is no longer available. Reverting to internal version.")
        }

        return ResolvedVersion(VersionImpl.getInternalVersion(), isFallback = true)
    }

    @JvmStatic
    fun loadSavedState(appWidgetId: Int): SavedState = SavedState().apply {
        darkText = Preferences.getBoolean("app_widget_${appWidgetId}_option_dark_text", false)
        hideAppIcon = Preferences.getBoolean("app_widget_${appWidgetId}_option_hide_app_icon", false)
        textSize = Preferences.getFloat("app_widget_${appWidgetId}_option_text_size", 14f)
        transparentBackground = Preferences.getBoolean("app_widget_${appWidgetId}_option_transparent_background", false)
        versionId = Preferences.getString("app_widget_${appWidgetId}_version")
        click = Preferences.getInt("app_widget_${appWidgetId}_click", 0)
        backgroundAlpha = Preferences.getInt("app_widget_${appWidgetId}_option_backgroundAlpha", 255)
    }

    /** Passing null for [savedState] deletes the widget's saved settings. */
    @JvmStatic
    fun saveSavedState(appWidgetId: Int, savedState: SavedState?) {
        Preferences.withTransaction {
            if (savedState == null) {
                Preferences.remove("app_widget_${appWidgetId}_option_dark_text")
                Preferences.remove("app_widget_${appWidgetId}_option_hide_app_icon")
                Preferences.remove("app_widget_${appWidgetId}_option_text_size")
                Preferences.remove("app_widget_${appWidgetId}_option_transparent_background")
                Preferences.remove("app_widget_${appWidgetId}_version")
                Preferences.remove("app_widget_${appWidgetId}_click")
                Preferences.remove("app_widget_${appWidgetId}_option_backgroundAlpha")
            } else {
                Preferences.setBoolean("app_widget_${appWidgetId}_option_dark_text", savedState.darkText)
                Preferences.setBoolean("app_widget_${appWidgetId}_option_hide_app_icon", savedState.hideAppIcon)
                Preferences.setFloat("app_widget_${appWidgetId}_option_text_size", savedState.textSize)
                Preferences.setBoolean("app_widget_${appWidgetId}_option_transparent_background", savedState.transparentBackground)
                Preferences.setString("app_widget_${appWidgetId}_version", savedState.versionId)
                Preferences.setInt("app_widget_${appWidgetId}_click", savedState.click)
                Preferences.setInt("app_widget_${appWidgetId}_option_backgroundAlpha", savedState.backgroundAlpha)
            }
        }
    }
}
