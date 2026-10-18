package yuku.alkitab.base.appwidget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
                    Text(stringResource(R.string.dv_activity_title), style = MaterialTheme.typography.titleLarge)
                }
            }
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = dimensionResource(R.dimen.padding_medium), vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.dv_section_version), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.selectableGroup()) {
                        versions.forEach { version ->
                            val isSelected = version.versionId == configuration.versionId
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .selectable(isSelected, role = Role.RadioButton) { onChange(configuration.copy(versionId = version.versionId)) }
                                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(version.longName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                RadioButton(isSelected, onClick = null, modifier = Modifier.size(48.dp))
                            }
                        }
                    }
                }
                Text(stringResource(R.string.dv_section_appearance), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        ConfigurationSlider(
                            configuration.textSize - 8, "${configuration.textSize}", stringResource(R.string.dv_option_text_size),
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        ) { onChange(configuration.copy(textSize = it + 8)) }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ConfigurationSlider(
                            configuration.transparency, "${configuration.transparency * 5}%", stringResource(R.string.dv_option_transparency),
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            startLabel = stringResource(R.string.dv_transparency_solid),
                            endLabel = stringResource(R.string.dv_transparency_clear),
                        ) { onChange(configuration.copy(transparency = it, isDarkText = it > 0 && configuration.isDarkText)) }
                        ConfigurationCheckbox(stringResource(R.string.dv_option_dark_text), configuration.isDarkText, configuration.isTransparent, onChange = {
                            onChange(configuration.copy(isDarkText = it))
                        })
                        ConfigurationCheckbox(stringResource(R.string.dv_option_hide_app_icon), configuration.isIconHidden, onChange = {
                            onChange(configuration.copy(isIconHidden = it))
                        })
                    }
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = dimensionResource(R.dimen.padding_medium), vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                Button(onClick = onSave, enabled = versions.any { it.versionId == configuration.versionId }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigurationSlider(
    progress: Int,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    startLabel: String? = null,
    endLabel: String? = null,
    onChange: (Int) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(value, Modifier.padding(start = 16.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = progress.toFloat(), onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..20f,
            track = { SliderDefaults.Track(sliderState = it, modifier = Modifier.height(4.dp), drawStopIndicator = null) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
        if (startLabel != null && endLabel != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(startLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(endLabel, Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ConfigurationCheckbox(label: String, isChecked: Boolean, isEnabled: Boolean = true, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(isChecked, enabled = isEnabled, role = Role.Checkbox, onValueChange = onChange).padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(isChecked, onCheckedChange = null, enabled = isEnabled, modifier = Modifier.size(48.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isEnabled) 1f else 0.38f))
    }
}
