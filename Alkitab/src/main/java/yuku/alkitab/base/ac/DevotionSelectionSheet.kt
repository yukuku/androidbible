package yuku.alkitab.base.ac

import android.app.Activity
import android.app.DatePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Date
import yuku.alkitab.base.compose.ComposeBottomSheetHost
import yuku.alkitab.base.config.AppConfig
import yuku.alkitab.debug.R

object DevotionSelectionSheet {
    fun show(
        activity: Activity,
        initialKind: DevotionActivity.DevotionKind,
        initialDate: Date,
        onSelected: (DevotionActivity.DevotionKind, Date) -> Unit,
    ) {
        ComposeBottomSheetHost.show(activity) { dismiss ->
            var kind by remember { mutableStateOf(initialKind) }
            var date by remember { mutableStateOf(initialDate.time) }
            var expanded by remember { mutableStateOf(false) }
            val kinds = remember { AppConfig.get().devotionNames.mapNotNull { DevotionActivity.DevotionKind.getByName(it) } }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(stringResource(R.string.devotion_select), style = MaterialTheme.typography.titleLarge)
                Box {
                    TextButton(onClick = { expanded = true }) { Text("${kind.title} ▾") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                                },
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { date = Calendar.getInstance().apply { timeInMillis = date; add(Calendar.DATE, -1) }.timeInMillis }) {
                        Text(stringResource(R.string.desc_previous_day))
                    }
                    TextButton(onClick = { date = Calendar.getInstance().apply { timeInMillis = date; add(Calendar.DATE, 1) }.timeInMillis }) {
                        Text(stringResource(R.string.desc_next_day))
                    }
                }
                TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                    val calendar = Calendar.getInstance().apply { timeInMillis = date }
                    DatePickerDialog(activity, { _, year, month, day ->
                        date = Calendar.getInstance().apply { clear(); set(year, month, day, 12, 0) }.timeInMillis
                    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                }) { Text(DateFormat.getLongDateFormat(activity).format(Date(date))) }
                Button(modifier = Modifier.fillMaxWidth(), onClick = { onSelected(kind, Date(date)); dismiss() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
}
