package yuku.alkitab.base.appwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.ColorUtils
import yuku.alkitab.base.App
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.FormattedVerseText
import yuku.alkitab.base.widget.Localized
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari

private const val TAG = "DailyVerseFactory"

class DailyVerseFactory(intent: Intent) : RemoteViewsService.RemoteViewsFactory {
    private val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    private lateinit var savedState: DailyVerseData.SavedState
    private lateinit var version: Version
    private var isVersionFallback = false
    private var aris: IntArray? = null

    override fun onCreate() {
        AppLog.d(TAG, "@@onCreate")
        reload()
    }

    override fun onDataSetChanged() {
        AppLog.d(TAG, "@@onDataSetChanged")
        reload()
    }

    private fun reload() {
        savedState = DailyVerseData.loadSavedState(appWidgetId)
        val resolvedVersion = DailyVerseData.resolveVersion(savedState.versionId)
        version = resolvedVersion.version
        isVersionFallback = resolvedVersion.isFallback
        aris = DailyVerseData.getAris(appWidgetId, savedState, version, 1)
    }

    override fun onDestroy() = Unit

    override fun getCount(): Int = 1 + (aris?.size ?: 0)

    override fun getViewAt(position: Int): RemoteViews =
        if (position == 0) getHeaderView() else getVerseViewAt(position - 1)

    private fun getHeaderView(): RemoteViews {
        val rv = RemoteViews(App.context.packageName, R.layout.appwidget_daily_verse_item_header)
        if (savedState.darkText) {
            rv.setTextColor(R.id.tReference, Color.BLACK)
            rv.setImageViewResource(R.id.bPrev, R.drawable.ic_nav_left_dark)
            rv.setImageViewResource(R.id.bNext, R.drawable.ic_nav_right_dark)
        } else {
            rv.setTextColor(R.id.tReference, Color.WHITE)
        }

        rv.setViewVisibility(R.id.imgLogo, if (savedState.hideAppIcon) View.GONE else View.VISIBLE)
        rv.setOnClickFillInIntent(R.id.imgLogo, Intent().putExtra("app_widget_action", "open_app"))

        rv.setViewVisibility(R.id.tVersionFallback, if (isVersionFallback) View.VISIBLE else View.GONE)
        if (isVersionFallback) {
            val message = if (savedState.versionId == null) R.string.dv_version_unset else R.string.dv_version_unavailable
            rv.setTextViewText(R.id.tVersionFallback, Localized.string(message, version.longName))
            rv.setTextColor(R.id.tVersionFallback, ColorUtils.setAlphaComponent(if (savedState.darkText) Color.BLACK else Color.WHITE, 204))
            rv.setFloat(R.id.tVersionFallback, "setTextSize", savedState.textSize * 0.8f)
            rv.setOnClickFillInIntent(R.id.tVersionFallback, Intent().apply {
                putExtra("app_widget_action", "configure_widget")
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            })
        }

        rv.setOnClickFillInIntent(R.id.bPrev, Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            putExtra("app_widget_action", "update_widget")
            putExtra("app_widget_button", 1)
        })
        rv.setOnClickFillInIntent(R.id.bNext, Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            putExtra("app_widget_action", "update_widget")
            putExtra("app_widget_button", 2)
        })

        rv.setFloat(R.id.tReference, "setTextSize", savedState.textSize)
        val selectedAris = aris
        if (selectedAris != null && selectedAris.isNotEmpty()) {
            rv.setTextViewText(R.id.tReference, version.referenceWithVerseCount(selectedAris[0], selectedAris.size))
            rv.setOnClickFillInIntent(R.id.tReference, Intent().putExtra("app_widget_action", "open_verse").putExtra("ari", selectedAris[0]))
        } else {
            rv.setTextViewText(R.id.tReference, Localized.string(R.string.generic_verse_not_available_in_this_version))
        }
        return rv
    }

    private fun getVerseViewAt(position: Int): RemoteViews {
        val row = RemoteViews(App.context.packageName, R.layout.appwidget_daily_verse_item_verse)
        val selectedAris = aris
        // The host can request a stale position after the selection changes.
        if (selectedAris == null || position !in selectedAris.indices) {
            row.setTextViewText(R.id.text1, "")
            row.setOnClickFillInIntent(R.id.text1, Intent())
        } else {
            val ari = selectedAris[position]
            row.setTextViewText(R.id.text1, getText(version, ari, selectedAris.size > 1))
            row.setTextColor(R.id.text1, if (savedState.darkText) Color.BLACK else Color.WHITE)
            row.setFloat(R.id.text1, "setTextSize", savedState.textSize)
            row.setOnClickFillInIntent(R.id.text1, Intent().putExtra("app_widget_action", "open_verse").putExtra("ari", ari))
        }
        return row
    }

    private fun getText(version: Version, ari: Int, isVerseNumberShown: Boolean): SpannableStringBuilder {
        val verseText = FormattedVerseText.removeSpecialCodes(version.loadVerseText(ari))
            ?: Localized.string(R.string.generic_verse_not_available_in_this_version)
        return SpannableStringBuilder().apply {
            if (isVerseNumberShown) {
                val verseNumber = Ari.toVerse(ari).toString()
                append(verseNumber).append(" ").append(verseText)
                setSpan(RelativeSizeSpan(0.7f), 0, verseNumber.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(SuperscriptSpan(), 0, verseNumber.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                append(verseText)
            }
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
