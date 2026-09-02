package yuku.alkitab.songs

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yuku.alkitab.base.App
import yuku.alkitab.base.compose.ComposeBottomSheetHost
import yuku.alkitab.debug.R

/**
 * Song search as a Compose [androidx.compose.material3.ModalBottomSheet] hosted over
 * [SongViewActivity] via [ComposeBottomSheetHost] (same pattern as the highlight sheet).
 * Search state (query, book filter, deep-search flag, results, and list scroll position)
 * lives in the activity-scoped [SongSearchViewModel], so dismissing the sheet to view a
 * song and reopening it later restores the search exactly where it was left off.
 */
object SongSearchSheet {
    fun show(activity: ComponentActivity, onSongSelected: (SongInfo) -> Unit) {
        val viewModel = ViewModelProvider(activity)[SongSearchViewModel::class.java]
        // Gestures are off for the same reason as the version picker: a fling that runs the
        // results list past its bounds leaks residual motion into the sheet's own drag handling,
        // briefly expanding it before it springs back.
        ComposeBottomSheetHost.show(activity, sheetGesturesEnabled = false) { dismiss ->
            SongSearchSheetContent(
                viewModel = viewModel,
                onSongSelected = { songInfo ->
                    onSongSelected(songInfo)
                    dismiss()
                },
            )
        }
    }
}

class SongSearchViewModel : ViewModel() {
    data class UiState(
        val filterString: String = "",
        /** null means all song books */
        val selectedBookName: String? = null,
        val deepSearch: Boolean = false,
        val loading: Boolean = false,
        /** null means no search has completed yet */
        val results: List<SongInfo>? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // scroll restoration for the results list, saved when the sheet leaves the composition
    var listScrollIndex = 0
    var listScrollOffset = 0

    private var searchJob: Job? = null

    fun searchIfNeeded() {
        if (_uiState.value.results == null) startSearch()
    }

    fun setFilterString(filterString: String) {
        if (filterString == _uiState.value.filterString) return
        _uiState.update { it.copy(filterString = filterString) }
        startSearch()
    }

    fun setSelectedBookName(bookName: String?) {
        _uiState.update { it.copy(selectedBookName = bookName) }
        startSearch()
    }

    fun setDeepSearch(deepSearch: Boolean) {
        _uiState.update { it.copy(deepSearch = deepSearch) }
        startSearch()
    }

    private fun startSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            // Most searches finish in well under this delay, so the progress bar never shows for
            // them; cancelling it once the search resolves is what keeps it from flashing on screen.
            val loadingJob = launch {
                delay(LOADING_INDICATOR_DELAY_MS)
                _uiState.update { it.copy(loading = true) }
            }
            val s = _uiState.value
            val filter = s.filterString.trim().takeIf { it.isNotEmpty() }
            val res = withContext(Dispatchers.IO) {
                val songDb = App.services.storage.songDb
                if (s.deepSearch) {
                    songDb.listSongInfosByBookNameAndDeepFilter(s.selectedBookName, filter)
                } else {
                    SongFilter.filterSongInfosByString(songDb.listSongInfosByBookName(s.selectedBookName), filter)
                }
            }
            // if this job was cancelled by a newer search, withContext throws and we never get here
            loadingJob.cancel()
            _uiState.update { it.copy(loading = false, results = res) }
        }
    }

    companion object {
        private const val LOADING_INDICATOR_DELAY_MS = 200L
    }
}

@Composable
private fun SongSearchSheetContent(
    viewModel: SongSearchViewModel,
    onSongSelected: (SongInfo) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf(state.filterString) }
    val keyboard = LocalSoftwareKeyboardController.current
    val queryFocusRequester = remember { FocusRequester() }

    // Compiled once per committed filter, reused to highlight matched substrings in every
    // visible result (same idea as the verse search hit highlighting).
    val compiledFilter = remember(state.filterString) { SongFilter.compileFilter(state.filterString) }

    LaunchedEffect(Unit) {
        viewModel.searchIfNeeded()
        queryFocusRequester.requestFocus()
        keyboard?.show()
    }

    // Cap the sheet height so its rounded top stays a bit below the status bar
    // (a ModalBottomSheet's expanded height reaches right up to the status bar,
    // which makes a fill-max-height sheet read as another fullscreen screen
    // instead of a dialog). maxHeight here is the available sheet content height,
    // already excluding the status bar; leaving a fixed gap keeps the curve visible.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val sheetHeight = maxHeight - 48.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.setFilterString(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(queryFocusRequester),
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = {
                            query = ""
                            viewModel.setFilterString("")
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.delete))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SongBookFilterChip(
                    selectedBookName = state.selectedBookName,
                    onBookSelected = viewModel::setSelectedBookName,
                )

                FilterChip(
                    selected = state.deepSearch,
                    onClick = { viewModel.setDeepSearch(!state.deepSearch) },
                    label = { Text(stringResource(R.string.sn_deep_search_enabled)) },
                    leadingIcon = if (state.deepSearch) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            ) {
                if (state.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            val listState = rememberLazyListState(viewModel.listScrollIndex, viewModel.listScrollOffset)
            DisposableEffect(Unit) {
                onDispose {
                    viewModel.listScrollIndex = listState.firstVisibleItemIndex
                    viewModel.listScrollOffset = listState.firstVisibleItemScrollOffset
                }
            }

            val results = state.results.orEmpty()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                itemsIndexed(results) { index, songInfo ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SongResultItem(
                        songInfo = songInfo,
                        compiledFilter = compiledFilter,
                        onClick = { onSongSelected(songInfo) },
                    )
                }
            }
        }
    }
}

/**
 * Compose counterpart of [SongBookUtil.escapeSongBookName]: private song books
 * (name prefixed with `_`) are shown without the underscore, in [R.color.escape].
 */
@Composable
private fun songBookNameAnnotated(name: String?): AnnotatedString {
    return if (name != null && name.startsWith("_")) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = colorResource(R.color.escape))) {
                append(name.substring(1))
            }
        }
    } else {
        AnnotatedString(name.orEmpty())
    }
}

@Composable
private fun SongBookFilterChip(
    selectedBookName: String?,
    onBookSelected: (String?) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var books by remember { mutableStateOf<List<SongBookUtil.SongBookInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        books = withContext(Dispatchers.IO) {
            App.services.storage.songDb.listSongBookInfos()
        }
    }

    Box {
        FilterChip(
            selected = selectedBookName != null,
            onClick = { menuOpen = true },
            label = {
                if (selectedBookName == null) {
                    Text(stringResource(R.string.sn_bookselector_all))
                } else {
                    Text(songBookNameAnnotated(selectedBookName))
                }
            },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(R.string.sn_bookselector_all))
                        Text(
                            stringResource(R.string.sn_bookselector_all_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = {
                    menuOpen = false
                    onBookSelected(null)
                },
            )
            books.forEach { book ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(songBookNameAnnotated(book.name))
                            val title = book.title
                            if (!title.isNullOrEmpty()) {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        menuOpen = false
                        onBookSelected(book.name)
                    },
                )
            }
        }
    }
}

@Composable
private fun SongResultItem(
    songInfo: SongInfo,
    compiledFilter: SongFilter.CompiledFilter,
    onClick: () -> Unit,
) {
    val hiliteColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = highlightMatches("${songInfo.code}. ${songInfo.title}", compiledFilter, hiliteColor),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val titleOriginal = songInfo.title_original
        if (!titleOriginal.isNullOrEmpty()) {
            Text(
                text = highlightMatches(titleOriginal, compiledFilter, hiliteColor),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = songBookNameAnnotated(songInfo.bookName),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        // Deep ("include lyrics") search: preview up to 2 matching lyric lines.
        val snippet = songInfo.snippet
        if (!snippet.isNullOrEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = highlightMatches(snippet, compiledFilter, hiliteColor),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Bolds and recolors every filter-token match inside [text] (via [SongFilter.matchRanges]),
 * mirroring how the verse search highlights hits. Returns the plain string when nothing matches.
 */
private fun highlightMatches(
    text: String,
    compiledFilter: SongFilter.CompiledFilter,
    color: Color,
): AnnotatedString {
    val ranges = SongFilter.matchRanges(text, compiledFilter)
    if (ranges.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text)
        val len = text.length
        for (range in ranges) {
            val start = range[0].coerceIn(0, len)
            val end = range[1].coerceIn(start, len)
            if (end > start) {
                addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), start, end)
            }
        }
    }
}
