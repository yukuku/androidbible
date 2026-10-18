package yuku.alkitab.base.appwidget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import yuku.alkitab.base.compose.toolbar.ToolbarScaffold
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.debug.R

@Composable
internal fun DailyVerseConfigurationScreen(
    configuration: DailyVerseConfiguration,
    versions: List<MVersion>,
    isToolbarAtBottom: Boolean,
    toolbarColor: Color,
    onChange: (DailyVerseConfiguration) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolbarScaffold(
        modifier = modifier,
        isToolbarAtBottom = isToolbarAtBottom,
        toolbarColor = toolbarColor,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {},
        toolbar = {
            Surface(color = toolbarColor) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, stringResource(R.string.cancel))
                    }
                    Text(stringResource(R.string.dv_activity_title), fontSize = 20.sp)
                }
            }
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = dimensionResource(R.dimen.padding_medium)).selectableGroup(),
            ) {
                items(versions, key = { it.versionId }) { version ->
                    val isSelected = version.versionId == configuration.versionId
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(isSelected, role = Role.RadioButton) { onChange(configuration.copy(versionId = version.versionId)) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(version.longName, Modifier.weight(1f), fontSize = 16.sp)
                        RadioButton(isSelected, onClick = null)
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
            Column(Modifier.padding(horizontal = dimensionResource(R.dimen.padding_medium))) {
                val sizeLabel = stringResource(R.string.dv_option_text_size)
                Text(sizeLabel, Modifier.padding(start = 8.dp, top = 8.dp), fontSize = 12.sp)
                ConfigurationSlider(configuration.textSize - 8, "${configuration.textSize}", sizeLabel) {
                    onChange(configuration.copy(textSize = it + 8))
                }
                ConfigurationCheckbox(stringResource(R.string.dv_option_transparent_background), configuration.isTransparent, onChange = {
                    onChange(configuration.copy(isTransparent = it, isDarkText = it && configuration.isDarkText))
                })
                if (configuration.isTransparent) {
                    ConfigurationSlider(configuration.transparency, "${configuration.transparency * 5}%", stringResource(R.string.dv_option_transparent_background)) {
                        onChange(configuration.copy(transparency = it))
                    }
                }
                ConfigurationCheckbox(stringResource(R.string.dv_option_dark_text), configuration.isDarkText, configuration.isTransparent, onChange = {
                    onChange(configuration.copy(isDarkText = it))
                })
                ConfigurationCheckbox(stringResource(R.string.dv_option_hide_app_icon), configuration.isIconHidden, onChange = {
                    onChange(configuration.copy(isIconHidden = it))
                })
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = onSave, enabled = versions.any { it.versionId == configuration.versionId }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
}

@Composable
private fun ConfigurationSlider(progress: Int, value: String, label: String, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = progress.toFloat(), onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..20f, steps = 19,
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
        )
        Text(value, Modifier.widthIn(min = 64.dp), textAlign = TextAlign.End, fontSize = 16.sp)
    }
}

@Composable
private fun ConfigurationCheckbox(label: String, isChecked: Boolean, isEnabled: Boolean = true, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(isChecked, enabled = isEnabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(isChecked, onCheckedChange = null, enabled = isEnabled)
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isEnabled) 1f else 0.38f))
    }
}
