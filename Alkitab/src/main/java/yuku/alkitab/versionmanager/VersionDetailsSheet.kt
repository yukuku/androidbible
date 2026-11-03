package yuku.alkitab.versionmanager

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import yuku.alkitab.base.compose.ComposeBottomSheetHost
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.model.MVersionDb
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.model.MVersionPreset
import yuku.alkitab.debug.R

internal fun showVersionDetailsSheet(
    activity: Activity,
    version: MVersion,
    onRemove: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    onUpdate: (() -> Unit)?,
) {
    ComposeBottomSheetHost.show(activity) { dismiss ->
        fun action(callback: (() -> Unit)?): (() -> Unit)? = callback?.let { { dismiss(); it() } }
        VersionDetailsContent(version, action(onRemove), action(onShare), action(onDownload), action(onUpdate))
    }
}

@Composable
internal fun VersionDetailsContent(
    version: MVersion,
    onRemove: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    onUpdate: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val fields = buildList {
        val type = when (version) {
            is MVersionInternal -> R.string.ed_type_internal
            is MVersionPreset -> R.string.ed_type_preset
            else -> R.string.ed_type_db
        }
        add(R.string.ed_type_key to stringResource(type))
        version.locale?.takeIf { it.isNotBlank() }?.let { add(R.string.ed_locale_locale to it) }
        when (version) {
            is MVersionPreset -> add(R.string.ed_default_filename_file to version.preset_name)
            is MVersionDb -> add(R.string.ed_stored_in_file to version.filename)
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Text(
            stringResource(R.string.ed_version_details),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                version.shortName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(version.longName.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            }
            fields.forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyLarge)
                }
            }
            version.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            if (onUpdate != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.ed_details_update_available), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onUpdate) {
                        Icon(painterResource(R.drawable.ic_version_update), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ed_update_button))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        if (onRemove != null || onShare != null || onDownload != null) {
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onRemove != null) {
                    TextButton(
                        onClick = onRemove,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f).wrapContentWidth(Alignment.Start),
                    ) {
                        Icon(painterResource(R.drawable.ic_baseline_delete_outline_24_white), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.buang_dari_daftar))
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (onShare != null) {
                    Button(onClick = onShare, modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End)) {
                        Icon(painterResource(R.drawable.ic_menu_share), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.version_menu_share))
                    }
                } else if (onDownload != null) {
                    Button(onClick = onDownload, modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End)) {
                        Icon(painterResource(R.drawable.ic_action_download), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ed_download_button))
                    }
                }
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }
}
