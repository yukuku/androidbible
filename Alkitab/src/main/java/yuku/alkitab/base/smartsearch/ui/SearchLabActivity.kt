package yuku.alkitab.base.smartsearch.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.base.util.VersionDialogHelper
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version

private const val EXTRA_versionId = "versionId"
private const val EXTRA_query = "query"

/**
 * Search settings, query comparisons, and diagnostics for testers.
 */
class SearchLabActivity : BaseActivity() {
    private lateinit var viewModel: SearchLabViewModel
    private var versionId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[SearchLabViewModel::class.java]

        versionId = savedInstanceState?.getString(EXTRA_versionId)
            ?: intent.getStringExtra(EXTRA_versionId)
            ?: App.services.versions.activeVersionId()
        viewModel.setVersion(versionId, resolveVersion(versionId))

        if (savedInstanceState == null) {
            intent.getStringExtra(EXTRA_query)?.takeIf { it.isNotBlank() }?.let { viewModel.setQuery(it) }
        }

        setContent {
            BibleAppTheme {
                SearchLabScreen(
                    viewModel = viewModel,
                    onUp = { finish() },
                    onChangeVersion = { changeVersion() },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_versionId, versionId)
    }

    private fun resolveVersion(id: String): Version {
        val versions = App.services.versions
        return versions.getVersionFromVersionId(id)?.version
            ?: versions.getMVersionInternal().version
            ?: versions.activeVersion()
    }

    private fun changeVersion() {
        VersionDialogHelper.openVersionsDialog(this, App.services.versions, versionId) { mv ->
            val v = mv.version
            if (v == null) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.version_error_opening, mv.longName))
                    .setPositiveButton(R.string.ok, null)
                    .show()
                return@openVersionsDialog
            }
            versionId = mv.versionId
            viewModel.setVersion(mv.versionId, v)
        }
    }

    companion object {
        fun createIntent(versionId: String, query: String?): Intent =
            Intent(App.context, SearchLabActivity::class.java)
                .putExtra(EXTRA_versionId, versionId)
                .putExtra(EXTRA_query, query)
    }
}
