package yuku.alkitab.base.settings

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.sync.SyncSettingsActivity
import yuku.alkitab.debug.R

private const val EXTRA_subClassName = "subClassName"

class SettingsActivity : BaseActivity() {
    class Header(
        val titleResId: Int,
        val clazz: Class<out PreferenceFragmentCompat>?,
        val clickIntent: Intent?,
    )

    val headers = listOf(
        Header(R.string.pref_sync_title, null, Intent(App.context, SyncSettingsActivity::class.java)),
        Header(R.string.pref_penampilan_layar, DisplayFragment::class.java, null),
        Header(R.string.pref_penggunaan, UsageFragment::class.java, null),
        Header(R.string.pref_copy_share, CopyShareFragment::class.java, null),
        Header(R.string.pref_data_transfer, DataTransferFragment::class.java, null),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val subClassName = intent.getStringExtra(EXTRA_subClassName)
        if (subClassName == null) {
            val lsHeaders = findViewById<RecyclerView>(R.id.lsHeaders)
            lsHeaders.layoutManager = LinearLayoutManager(this)
            lsHeaders.adapter = HeadersAdapter()
        } else {
            val header = headers.find { it.clazz?.name == subClassName }
            if (header == null) {
                finish()
                return
            }

            val fm: FragmentManager = supportFragmentManager
            val ft = fm.beginTransaction()
            ft.replace(
                R.id.fragment_container,
                fm.fragmentFactory.instantiate(classLoader, subClassName),
                subClassName,
            )
            ft.commit()
        }
    }

    private class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(android.R.id.title)
    }

    private inner class HeadersAdapter : RecyclerView.Adapter<VH>() {
        private val tv = TypedValue()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = layoutInflater.inflate(R.layout.preference_header_item_material, parent, false)
            theme.resolveAttribute(androidx.appcompat.R.attr.selectableItemBackground, tv, true)
            v.setBackgroundResource(tv.resourceId)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val header = headers[position]
            holder.title.setText(header.titleResId)
            holder.itemView.setOnClickListener {
                if (header.clickIntent != null) {
                    startActivity(header.clickIntent)
                } else if (header.clazz != null) {
                    startActivity(createSubIntent(header.clazz))
                }
            }
        }

        override fun getItemCount() = headers.size
    }

    private fun createSubIntent(subClass: Class<out PreferenceFragmentCompat>): Intent {
        return Intent(App.context, SettingsActivity::class.java)
            .putExtra(EXTRA_subClassName, subClass.name)
    }

    companion object {
        @JvmStatic
        fun createIntent(): Intent {
            return Intent(App.context, SettingsActivity::class.java)
        }

        @JvmStatic
        fun autoDisplayListPreference(pref: ListPreference) {
            val label = pref.entry
            if (label != null) {
                pref.summary = label
            }
            val originalChangeListener = pref.onPreferenceChangeListener
            pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference: Preference, newValue: Any? ->
                val changed = originalChangeListener?.onPreferenceChange(preference, newValue) ?: true
                if (changed) {
                    val index = pref.findIndexOfValue(newValue as String?)
                    if (index >= 0) {
                        pref.summary = pref.entries[index]
                    }
                }
                changed
            }
        }

        @JvmStatic
        @JvmOverloads
        fun getPaddingBasedOnPreferences(useSmallerHorizontal: Boolean = false): Rect {
            val r = App.context.resources
            return if (Preferences.getBoolean(R.string.pref_textPadding_key, R.bool.pref_textPadding_default)) {
                val top = r.getDimensionPixelOffset(R.dimen.text_top_padding)
                val bottom = r.getDimensionPixelOffset(R.dimen.text_bottom_padding)
                val side = r.getDimensionPixelOffset(R.dimen.text_side_padding) / if (useSmallerHorizontal) 2 else 1
                Rect(side, top, side, bottom)
            } else {
                val no = r.getDimensionPixelOffset(R.dimen.text_nopadding)
                val bottom = r.getDimensionPixelOffset(R.dimen.text_bottom_padding)
                Rect(no, no, no, bottom)
            }
        }
    }
}
