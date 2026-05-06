package yuku.alkitab.base.compose.goto

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.unit.dp
import yuku.afw.storage.Preferences
import yuku.alkitab.base.S
import yuku.alkitab.base.util.BookColorUtil
import yuku.alkitab.base.util.BookNameSorter
import yuku.alkitab.debug.R
import yuku.alkitab.model.Book

@Composable
fun GridTab(
    askForVerse: Boolean,
    viewModel: GotoViewModel,
    onGotoFinished: OnGotoFinished,
) {
    val books: Array<Book> = remember {
        val raw = S.activeVersion().consecutiveBooks
        if (Preferences.getBoolean(R.string.pref_alphabeticBookSort_key, R.bool.pref_alphabeticBookSort_default)) {
            BookNameSorter.sortAlphabetically(raw)
        } else raw.copyOf()
    }

    val stage = viewModel.gridStage
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        if (stage !is GridStage.Books) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val book = when (stage) {
                    is GridStage.Chapters -> stage.book
                    is GridStage.Verses -> stage.book
                    GridStage.Books -> null
                }
                book?.let {
                    AssistChip(
                        onClick = { viewModel.gridStage = GridStage.Books },
                        label = {
                            Text(it.shortName, color = Color(BookColorUtil.getForegroundOnDark(it.bookId)))
                        },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                        },
                    )
                }
                if (stage is GridStage.Verses) {
                    AssistChip(
                        onClick = { viewModel.gridStage = GridStage.Chapters(stage.book) },
                        label = { Text(stage.chapter_1.toString()) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                        },
                    )
                }
            }
        }

        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                val forward = targetState.depth >= initialState.depth
                if (forward) {
                    (slideInHorizontally(tween(200)) { it } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(tween(200)) { -it } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(200)) { it } + fadeOut(tween(200)))
                }
            },
            label = "GridTabStage",
        ) { current ->
            when (current) {
                is GridStage.Books -> BooksGrid(books) { book ->
                    if (book.chapter_count == 1) {
                        viewModel.gridStage = GridStage.Verses(book, 1)
                    } else {
                        viewModel.gridStage = GridStage.Chapters(book)
                    }
                }
                is GridStage.Chapters -> NumericGrid(count = current.book.chapter_count) { ch ->
                    if (askForVerse) {
                        viewModel.gridStage = GridStage.Verses(current.book, ch)
                    } else {
                        onGotoFinished(GotoTab.GRID, current.book.bookId, ch, 0)
                    }
                }
                is GridStage.Verses -> {
                    val ch0 = current.chapter_1 - 1
                    val verseCount = if (ch0 in current.book.verse_counts.indices) current.book.verse_counts[ch0] else 0
                    NumericGrid(count = verseCount) { v ->
                        onGotoFinished(GotoTab.GRID, current.book.bookId, current.chapter_1, v)
                    }
                }
            }
        }
    }
}

@Composable
private fun BooksGrid(books: Array<Book>, onBookClick: (Book) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.fillMaxWidth()) {
        items(books.size) { idx ->
            val book = books[idx]
            GridCell(
                text = BookNameSorter.getBookAbbr(book).toString(),
                color = Color(BookColorUtil.getForegroundOnDark(book.bookId)),
                onClick = { onBookClick(book) },
            )
        }
    }
}

@Composable
private fun NumericGrid(count: Int, onClick: (Int) -> Unit) {
    val cols = integerResource(R.integer.goto_grid_numeric_num_columns)
    LazyVerticalGrid(columns = GridCells.Fixed(cols), modifier = Modifier.fillMaxWidth()) {
        items(count) { i ->
            GridCell(
                text = (i + 1).toString(),
                color = MaterialTheme.colorScheme.onSurface,
                onClick = { onClick(i + 1) },
            )
        }
    }
}

@Composable
private fun GridCell(text: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().aspectRatio(1.4f).padding(2.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            Text(text, color = color)
        }
    }
}

