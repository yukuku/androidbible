package yuku.alkitab.base.ac

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.widget.Toolbar
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.settings.SettingsActivity.Companion.autoDisplayListPreference
import yuku.alkitab.base.util.Sqlitil.toLocaleDateMedium
import yuku.alkitab.base.widget.ConfigurationWrapper
import yuku.alkitab.debug.R

class SecretSettingsActivity : BaseActivity() {
    class SecretSettingsFragment : PreferenceFragmentCompat() {
        private val secret_progress_mark_history_click = Preference.OnPreferenceClickListener {
            val progressMarks = App.services.storage.db.listAllProgressMarks()
            val labels = progressMarks.map { "${it.caption} (preset_id ${it.preset_id})" }.toTypedArray()
            MaterialAlertDialogBuilder(requireActivity())
                .setItems(labels) { _, index ->
                    val pmhs = App.services.storage.db.listProgressMarkHistoryByPresetId(progressMarks[index].preset_id)
                    val items = pmhs.map { "'${it.progress_mark_caption}' ${toLocaleDateMedium(it.createTime)}: ${App.services.versions.activeVersion().reference(it.ari)}" }.toTypedArray()
                    MaterialAlertDialogBuilder(requireActivity())
                        .setItems(items, null)
                        .show()
                }
                .show()
            true
        }

        private val secret_version_table_click = Preference.OnPreferenceClickListener {
            val items = App.services.storage.db.listAllVersions().map { mv ->
                String.format(
                    Locale.US, "filename=%s preset_name=%s modifyTime=%s active=%s ordering=%s locale=%s shortName=%s longName=%s description=%s",
                    mv.filename, mv.preset_name, mv.modifyTime, mv.active, mv.ordering, mv.locale, mv.shortName, mv.longName, mv.description
                )
            }.toTypedArray()

            MaterialAlertDialogBuilder(requireActivity())
                .setItems(items, null)
                .show()
            true
        }

        private var secret_sync_debug = Preference.OnPreferenceClickListener {
            startActivity(Intent(App.context, SecretSyncDebugActivity::class.java))
            true
        }

        private val configurationPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
            val handler = Handler(Looper.getMainLooper())

            // do this after this method returns true
            handler.post {
                ConfigurationWrapper.notifyConfigurationNeedsUpdate()

                // restart this activity
                activity?.recreate()
            }
            true
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.secret_settings)
            findPreference<Preference>("secret_progress_mark_history")?.onPreferenceClickListener = secret_progress_mark_history_click
            findPreference<Preference>("secret_version_table")?.onPreferenceClickListener = secret_version_table_click
            findPreference<Preference>("secret_sync_debug")?.onPreferenceClickListener = secret_sync_debug
            val pref_forceFontScale = findPreference<Preference>(getString(R.string.pref_forceFontScale_key)) as ListPreference?
            if (pref_forceFontScale != null) {
                pref_forceFontScale.onPreferenceChangeListener = configurationPreferenceChangeListener
                autoDisplayListPreference(pref_forceFontScale)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secret_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        setupEdgeToEdgeDisplay(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    companion object {
        fun createIntent(): Intent {
            return Intent(App.context, SecretSettingsActivity::class.java)
        }
    }
}
