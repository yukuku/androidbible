package yuku.alkitab.base.appwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.CheckedTextView
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import yuku.afw.widget.EasyAdapter
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.br.DailyVerseAppWidgetReceiver
import yuku.alkitab.base.events.AppEvents
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.debug.R

private const val STATE_VERSION_ID = "selectedVersionId"

class DailyVerseAppWidgetConfigurationActivity : BaseActivity() {
    private lateinit var adapter: VersionAdapter
    private var selectedVersionId: String? = null
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    internal var selectedVersionPosition = -1
        private set
    private lateinit var bOk: Button
    private lateinit var cDarkText: CheckBox
    private lateinit var cHideAppIcon: CheckBox
    private lateinit var sbTextSize: SeekBar
    private lateinit var tTextSize: TextView
    private lateinit var panelTransparent: View
    private lateinit var sbTransparent: SeekBar
    private lateinit var tTransparent: TextView
    private lateinit var cTransparentBackground: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_daily_verse_configuration)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        setupEdgeToEdgeDisplay(toolbar)
        checkNotNull(supportActionBar).apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_action_remove)
        }

        val lsVersionsAppWidget = findViewById<ListView>(R.id.lsVersionsAppWidget)
        bOk = findViewById(R.id.bOk)
        val bCancel = findViewById<Button>(R.id.bCancel)
        cTransparentBackground = findViewById(R.id.cTransparentBackground)
        cDarkText = findViewById(R.id.cDarkText)
        cHideAppIcon = findViewById(R.id.cHideAppIcon)
        sbTextSize = findViewById(R.id.sbTextSize)
        tTextSize = findViewById(R.id.tTextSize)
        panelTransparent = findViewById(R.id.panelTransparent)
        sbTransparent = findViewById(R.id.sbTransparent)
        tTransparent = findViewById(R.id.tTransparent)

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val widgetState = DailyVerseData.loadSavedState(appWidgetId)
        selectedVersionId = if (savedInstanceState != null) savedInstanceState.getString(STATE_VERSION_ID) else widgetState.versionId

        adapter = VersionAdapter()
        adapter.reload()
        lsVersionsAppWidget.adapter = adapter
        lsVersionsAppWidget.setOnItemClickListener { _, _, position, _ ->
            selectedVersionPosition = position
            selectedVersionId = adapter.versions[position].versionId
            bOk.isEnabled = true
            adapter.notifyDataSetChanged()
        }
        bOk.isEnabled = selectedVersionPosition >= 0
        bOk.setOnClickListener { saveConfiguration() }
        bCancel.setOnClickListener { finish() }

        cTransparentBackground.setOnCheckedChangeListener { _, isChecked ->
            panelTransparent.visibility = if (isChecked) View.VISIBLE else View.GONE
            cDarkText.isEnabled = isChecked
            if (!isChecked) cDarkText.isChecked = false
        }

        sbTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateTextSizeLabel(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        updateTextSizeLabel(sbTextSize.progress)

        sbTransparent.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateTransparencyLabel(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        updateTransparencyLabel(sbTransparent.progress)

        if (widgetState.versionId != null) {
            cTransparentBackground.isChecked = widgetState.transparentBackground
            cDarkText.isChecked = widgetState.darkText
            cHideAppIcon.isChecked = widgetState.hideAppIcon
            sbTextSize.progress = Math.round(widgetState.textSize - 8f)
            sbTransparent.progress = Math.round((255 - widgetState.backgroundAlpha) * 20f / 255f)
        }

        AppEvents.observe(this, AppEvents.versionListReload) { adapter.reload() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_VERSION_ID, selectedVersionId)
    }

    private fun updateTextSizeLabel(progress: Int) {
        tTextSize.text = progressToActualTextSize(progress).toInt().toString()
    }

    private fun updateTransparencyLabel(progress: Int) {
        tTransparent.text = "${progressToActualTransparentPercent(progress)}%"
    }

    private fun progressToActualTransparentPercent(progress: Int): Int = progress * 5

    private fun progressToActualAlpha(progress: Int): Int =
        (255f * ((100 - progressToActualTransparentPercent(progress)) / 100f)).toInt()

    private fun progressToActualTextSize(progress: Int): Float = progress + 8f

    private fun saveConfiguration() {
        val selectedVersion = adapter.versions.getOrNull(selectedVersionPosition) ?: return
        val savedState = DailyVerseData.loadSavedState(appWidgetId).apply {
            versionId = selectedVersion.versionId
            transparentBackground = cTransparentBackground.isChecked
            backgroundAlpha = if (transparentBackground) progressToActualAlpha(sbTransparent.progress) else 255
            darkText = cDarkText.isChecked
            hideAppIcon = cHideAppIcon.isChecked
            textSize = progressToActualTextSize(sbTextSize.progress)
        }
        DailyVerseData.saveSavedState(appWidgetId, savedState)
        DailyVerseAppWidgetReceiver.buildUpdate(this, appWidgetId)

        val provider = ComponentName(this, DailyVerseAppWidgetReceiver::class.java)
        val ids = AppWidgetManager.getInstance(this).getAppWidgetIds(provider) ?: intArrayOf()
        DailyVerseAppWidgetReceiver.setAlarm(this, ids)

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    private inner class VersionAdapter : EasyAdapter() {
        var versions: List<MVersion> = emptyList()
            private set

        fun reload() {
            versions = App.services.versions.getAvailableVersions()
            selectedVersionPosition = versions.indexOfFirst { it.versionId == selectedVersionId }
            bOk.isEnabled = selectedVersionPosition >= 0
            notifyDataSetChanged()
        }

        override fun newView(position: Int, parent: ViewGroup): View =
            layoutInflater.inflate(android.R.layout.simple_list_item_single_choice, parent, false)

        override fun bindView(view: View, position: Int, parent: ViewGroup) {
            view.findViewById<CheckedTextView>(android.R.id.text1).apply {
                text = versions[position].longName
                isChecked = position == selectedVersionPosition
            }
        }

        override fun getCount(): Int = versions.size
    }
}
