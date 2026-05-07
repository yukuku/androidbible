package yuku.alkitab.base.compose.goto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yuku.afw.storage.Preferences
import yuku.alkitab.base.S
import yuku.alkitab.base.compose.bookForegroundColor
import yuku.alkitab.base.util.BookNameSorter
import yuku.alkitab.debug.R
import yuku.alkitab.model.Book

private enum class ActiveField { CHAPTER, VERSE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerTab(
    initialBookId: Int,
    initialChapter_1: Int,
    initialVerse_1: Int,
    askForVerse: Boolean,
    onGotoFinished: OnGotoFinished,
) {
    val books: Array<Book> = remember {
        val raw = S.activeVersion().consecutiveBooks
        if (Preferences.getBoolean(R.string.pref_alphabeticBookSort_key, R.bool.pref_alphabeticBookSort_default)) {
            BookNameSorter.sortAlphabetically(raw)
        } else raw.copyOf()
    }

    var bookIndex by rememberSaveable {
        mutableStateOf(books.indexOfFirst { it.bookId == initialBookId }.coerceAtLeast(0))
    }
    val selectedBook: Book = books[bookIndex]

    var chapterText by rememberSaveable { mutableStateOf(if (initialChapter_1 != 0) initialChapter_1.toString() else "") }
    var verseText by rememberSaveable { mutableStateOf(if (initialVerse_1 != 0) initialVerse_1.toString() else "") }
    var active by rememberSaveable { mutableStateOf(ActiveField.CHAPTER) }
    var chapterFirstTime by rememberSaveable { mutableStateOf(true) }
    var verseFirstTime by rememberSaveable { mutableStateOf(true) }

    if (!askForVerse && active == ActiveField.VERSE) active = ActiveField.CHAPTER

    fun tryReadChapter(): Int = ("0$chapterText").toIntOrNull() ?: 0
    fun tryReadVerse(): Int = ("0$verseText").toIntOrNull() ?: 0

    val maxChapter: Int = selectedBook.chapter_count
    val maxVerse: Int = run {
        val ch0 = tryReadChapter() - 1
        if (ch0 in 0 until selectedBook.verse_counts.size) selectedBook.verse_counts[ch0] else 0
    }

    fun press(s: String) {
        when (s) {
            "backspace" -> {
                if (active == ActiveField.CHAPTER && chapterText.isNotEmpty()) {
                    chapterText = chapterText.dropLast(1)
                } else if (active == ActiveField.VERSE && verseText.isNotEmpty()) {
                    verseText = verseText.dropLast(1)
                }
            }
            else -> {
                if (active == ActiveField.CHAPTER) {
                    chapterText = if (chapterFirstTime) s else chapterText + s
                    chapterFirstTime = false
                    val read = tryReadChapter()
                    if (read > maxChapter || read <= 0) chapterText = s
                } else {
                    verseText = if (verseFirstTime) s else verseText + s
                    verseFirstTime = false
                    val read = tryReadVerse()
                    if (read > maxVerse || read <= 0) verseText = s
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        var bookMenuOpen by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = bookMenuOpen,
            onExpandedChange = { bookMenuOpen = !bookMenuOpen },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        ) {
            OutlinedTextField(
                value = selectedBook.shortName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bookMenuOpen) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            DropdownMenu(
                expanded = bookMenuOpen,
                onDismissRequest = { bookMenuOpen = false },
            ) {
                books.forEachIndexed { idx, book ->
                    DropdownMenuItem(
                        text = { Text(book.shortName, color = bookForegroundColor(book.bookId)) },
                        onClick = {
                            bookIndex = idx
                            bookMenuOpen = false
                            chapterFirstTime = true
                            verseFirstTime = true
                        },
                    )
                }
            }
        }

        Spacer(Modifier.size(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.pasal_sebelumangka))
            Spacer(Modifier.width(8.dp))
            DialerField(
                text = chapterText,
                isActive = active == ActiveField.CHAPTER,
                onClick = { active = ActiveField.CHAPTER },
            )
            if (askForVerse) {
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.ayat_sebelumangka))
                Spacer(Modifier.width(8.dp))
                DialerField(
                    text = verseText,
                    isActive = active == ActiveField.VERSE,
                    onClick = { active = ActiveField.VERSE },
                )
            }
        }

        Spacer(Modifier.size(16.dp))

        HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))

        Spacer(Modifier.size(16.dp))

        val onDigit: (String) -> Unit = { d -> press(d) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")) { d ->
                KeypadDigit(d, onDigit)
            }
            item {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { press("backspace") }) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
                    }
                }
            }
            item { KeypadDigit("0", onDigit) }
            item {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).padding(4.dp), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            val ch = chapterText.toIntOrNull() ?: 0
                            val v = if (askForVerse) verseText.toIntOrNull() ?: 0 else 0
                            onGotoFinished(GotoTab.DIALER, selectedBook.bookId, ch, v)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
}

@Composable
private fun DialerField(text: String, isActive: Boolean, onClick: () -> Unit) {
    val bg = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = Modifier.size(width = 64.dp, height = 64.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            Text(text, fontSize = 24.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun KeypadDigit(d: String, onDigit: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).padding(2.dp), contentAlignment = Alignment.Center) {
        TextButton(onClick = { onDigit(d) }, modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            Text(d, fontSize = 28.sp, fontWeight = FontWeight.Normal)
        }
    }
}
