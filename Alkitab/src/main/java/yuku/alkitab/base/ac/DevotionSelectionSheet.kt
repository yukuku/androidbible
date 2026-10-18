package yuku.alkitab.base.ac

import android.app.Activity
import android.app.DatePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Date
import yuku.alkitab.base.compose.ComposeBottomSheetHost
import yuku.alkitab.base.config.AppConfig
import yuku.alkitab.debug.R

object DevotionSelectionSheet {
    @OptIn(ExperimentalMaterial3Api::class)
    fun show(
        activity: Activity,
        initialKind: DevotionActivity.DevotionKind,
        initialDate: Date,
        onSelected: (DevotionActivity.DevotionKind, Date) -> Unit,
    ) {
        ComposeBottomSheetHost.show(activity) { _ ->
            var kind by remember { mutableStateOf(initialKind) }
            var date by remember { mutableStateOf(initialDate.time) }
            var expanded by remember { mutableStateOf(false) }
            val kinds = remember { AppConfig.get().devotionNames.mapNotNull { DevotionActivity.DevotionKind.getByName(it) } }

            fun selectDate(value: Long) {
                date = value
                onSelected(kind, Date(value))
            }

            fun changeDay(offset: Int) {
                selectDate(Calendar.getInstance().apply {
                    timeInMillis = date
                    add(Calendar.DATE, offset)
                }.timeInMillis)
            }

            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = kind.title,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        kinds.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(option.title)
                                        Text(option.subtitle, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    kind = option
                                    expanded = false
                                    onSelected(option, Date(date))
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { changeDay(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.desc_previous_day), tint = Color.White)
                    }
                    TextButton(
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        onClick = {
                            val calendar = Calendar.getInstance().apply { timeInMillis = date }
                            DatePickerDialog(activity, { _, year, month, day ->
                                selectDate(Calendar.getInstance().apply {
                                    clear()
                                    set(year, month, day, 12, 0)
                                }.timeInMillis)
                            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                        },
                    ) {
                        Text(DateFormat.getLongDateFormat(activity).format(Date(date)), textAlign = TextAlign.Center)
                    }
                    IconButton(onClick = { changeDay(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.desc_next_day), tint = Color.White)
                    }
                }
            }
        }
    }
}
