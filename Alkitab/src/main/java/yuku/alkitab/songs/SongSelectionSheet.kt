package yuku.alkitab.songs

import android.app.Activity
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import yuku.alkitab.base.App
import yuku.alkitab.base.compose.ComposeBottomSheetHost
import yuku.alkitab.base.compose.composeFontFamilyFor
import yuku.alkitab.base.widget.LeftDrawer
import yuku.alkitab.debug.R

object SongSelectionSheet {
    @OptIn(ExperimentalMaterial3Api::class)
    fun show(
        activity: Activity,
        onReady: (LeftDrawer.Songs.Handle, () -> Unit) -> Unit,
        onDisposed: () -> Unit,
        onKey: (Int) -> Unit,
        onBookSelected: (String) -> Unit,
        onMoreSelected: () -> Unit,
    ) {
        ComposeBottomSheetHost.show(
            activity = activity,
            sheetMaxWidth = 320.dp,
            sheetGesturesEnabled = true,
            dimBackground = false,
        ) { dismiss ->
            val state = remember { KeypadState() }
            val books = remember { App.services.storage.songDb.listSongBookInfos() }
            var expanded by remember { mutableStateOf(false) }
            val density = LocalDensity.current
            val popupTextStyle = remember(activity, density) {
                val attributes = activity.obtainStyledAttributes(intArrayOf(android.R.attr.textAppearanceLargePopupMenu))
                val textView = try {
                    TextView(activity).apply {
                        setTextAppearance(attributes.getResourceId(0, android.R.style.TextAppearance_Material_Widget_PopupMenu_Large))
                    }
                } finally {
                    attributes.recycle()
                }
                TextStyle(
                    color = Color(textView.currentTextColor),
                    fontSize = with(density) { textView.textSize.toSp() },
                    fontFamily = composeFontFamilyFor(textView.typeface),
                )
            }
            val escapeColor = colorResource(R.color.escape)
            DisposableEffect(Unit) {
                onReady(state, dismiss)
                onDispose(onDisposed)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = state.bookName,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .weight(1.5f)
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )
                        Box(Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
                            Text(state.enteredCode, fontSize = 24.sp, maxLines = 1)
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            IconButton(onClick = { onKey(R.id.bBackspace) }) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.desc_backspace))
                            }
                        }
                    }
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        books.forEach { book ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = SongBookUtil.escapeSongBookName(book.name).toString(),
                                            style = popupTextStyle,
                                            color = if (book.name?.startsWith("_") == true) escapeColor else popupTextStyle.color,
                                        )
                                        val title = book.title
                                        if (!title.isNullOrEmpty()) {
                                            Text(
                                                text = HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY).toString(),
                                                style = popupTextStyle.copy(fontSize = popupTextStyle.fontSize * 0.7f),
                                                color = Color(0xFFA0A0A0),
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    expanded = false
                                    book.name?.let(onBookSelected)
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.sn_bookselector_more),
                                    style = popupTextStyle,
                                    color = escapeColor,
                                )
                            },
                            onClick = { expanded = false; onMoreSelected() },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
                val rows = listOf(
                    listOf("1" to R.id.bDigit1, "2" to R.id.bDigit2, "3" to R.id.bDigit3, "A" to R.id.bDigitA),
                    listOf("4" to R.id.bDigit4, "5" to R.id.bDigit5, "6" to R.id.bDigit6, "B" to R.id.bDigitB),
                    listOf("7" to R.id.bDigit7, "8" to R.id.bDigit8, "9" to R.id.bDigit9, "C" to R.id.bDigitC),
                )
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { (label, id) ->
                            val enabled = when (id) {
                                R.id.bDigitA -> state.aEnabled
                                R.id.bDigitB -> state.bEnabled
                                R.id.bDigitC -> state.cEnabled
                                else -> true
                            }
                            Key(label, enabled, Modifier.weight(1f)) { onKey(id) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Key("0", true, Modifier.weight(1f)) { onKey(R.id.bDigit0) }
                    Box(Modifier
                        .weight(2f)
                        .height(56.dp)
                        .padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                        Button(onClick = { onKey(R.id.bOk) }, enabled = state.okEnabled, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Key(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
        Box(modifier
            .height(56.dp)
            .padding(2.dp), contentAlignment = Alignment.Center) {
            TextButton(
                onClick = onClick,
                enabled = enabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxSize(),
            ) { Text(label, fontSize = 28.sp) }
        }
    }

    private class KeypadState : LeftDrawer.Songs.Handle {
        var bookName by mutableStateOf("")
        var enteredCode by mutableStateOf("––––")
        var okEnabled by mutableStateOf(false)
        var aEnabled by mutableStateOf(false)
        var bEnabled by mutableStateOf(false)
        var cEnabled by mutableStateOf(false)

        override fun setBookName(bookName: CharSequence?) { this.bookName = bookName?.toString().orEmpty() }
        override fun setCode(code: String?) { enteredCode = code.orEmpty() }
        override fun setOkButtonEnabled(enabled: Boolean) { okEnabled = enabled }
        override fun setAButtonEnabled(enabled: Boolean) { aEnabled = enabled }
        override fun setBButtonEnabled(enabled: Boolean) { bEnabled = enabled }
        override fun setCButtonEnabled(enabled: Boolean) { cEnabled = enabled }
    }
}
