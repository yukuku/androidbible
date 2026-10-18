package yuku.alkitab.base.appwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.br.DailyVerseAppWidgetReceiver
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.base.events.AppEvents
import yuku.alkitab.base.model.MVersion

internal data class DailyVerseConfiguration(
    val versionId: String? = null,
    val textSize: Int = 16,
    val isTransparent: Boolean = false,
    val transparency: Int = 20,
    val isDarkText: Boolean = false,
    val isIconHidden: Boolean = false,
)

class DailyVerseAppWidgetConfigurationActivity : BaseActivity() {
    internal var configuration by mutableStateOf(DailyVerseConfiguration())
    private var versions by mutableStateOf<List<MVersion>>(emptyList())
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val saved = DailyVerseData.loadSavedState(appWidgetId)
        configuration = if (savedInstanceState != null) {
            DailyVerseConfiguration(
                savedInstanceState.getString("selectedVersionId"),
                savedInstanceState.getInt("textSize", 16),
                savedInstanceState.getBoolean("transparent"),
                savedInstanceState.getInt("transparency", 20),
                savedInstanceState.getBoolean("darkText"),
                savedInstanceState.getBoolean("hideIcon"),
            )
        } else if (saved.versionId != null) {
            DailyVerseConfiguration(
                saved.versionId,
                Math.round(saved.textSize).coerceIn(8, 28),
                saved.transparentBackground,
                Math.round((255 - saved.backgroundAlpha) * 20f / 255f).coerceIn(0, 20),
                saved.transparentBackground && saved.darkText,
                saved.hideAppIcon,
            )
        } else DailyVerseConfiguration()
        versions = App.services.versions.getAvailableVersions()
        AppEvents.observe(this, AppEvents.versionListReload) {
            versions = App.services.versions.getAvailableVersions()
        }
        setupEdgeToEdgeComposeToolbar()
        setContent {
            BibleAppTheme {
                DailyVerseConfigurationScreen(
                    configuration = configuration,
                    versions = versions,
                    isToolbarAtBottom = isToolbarAtBottom,
                    toolbarColor = Color(toolbarBackgroundColor),
                    onChange = { configuration = it },
                    onSave = ::saveConfiguration,
                    onCancel = ::finish,
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selectedVersionId", configuration.versionId)
        outState.putInt("textSize", configuration.textSize)
        outState.putBoolean("transparent", configuration.isTransparent)
        outState.putInt("transparency", configuration.transparency)
        outState.putBoolean("darkText", configuration.isDarkText)
        outState.putBoolean("hideIcon", configuration.isIconHidden)
    }

    internal fun saveConfiguration() {
        val selectedVersion = versions.firstOrNull { it.versionId == configuration.versionId } ?: return
        val savedState = DailyVerseData.loadSavedState(appWidgetId).apply {
            versionId = selectedVersion.versionId
            transparentBackground = configuration.isTransparent
            backgroundAlpha = if (transparentBackground) (255f * ((100 - configuration.transparency * 5) / 100f)).toInt() else 255
            darkText = configuration.isTransparent && configuration.isDarkText
            hideAppIcon = configuration.isIconHidden
            textSize = configuration.textSize.toFloat()
        }
        DailyVerseData.saveSavedState(appWidgetId, savedState)
        DailyVerseAppWidgetReceiver.buildUpdate(this, appWidgetId)

        val provider = ComponentName(this, DailyVerseAppWidgetReceiver::class.java)
        val ids = AppWidgetManager.getInstance(this).getAppWidgetIds(provider) ?: intArrayOf()
        DailyVerseAppWidgetReceiver.setAlarm(this, ids)

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}
