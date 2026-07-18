package yuku.alkitab.songs

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
        ComposeBottomSheetHost.show(activity) { dismiss ->
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
            _uiState.update { it.copy(loading = true) }
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
            _uiState.update { it.copy(loading = false, results = res) }
        }
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

    LaunchedEffect(Unit) {
        viewModel.searchIfNeeded()
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
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
                .padding(horizontal = 16.dp),
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
                    onClick = { onSongSelected(songInfo) },
                )
            }
        }
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
                Text(
                    if (selectedBookName == null) {
                        stringResource(R.string.sn_bookselector_all)
                    } else {
                        SongBookUtil.escapeSongBookName(selectedBookName).toString()
                    }
                )
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
                            Text(SongBookUtil.escapeSongBookName(book.name).toString())
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
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = "${songInfo.code}. ${songInfo.title}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val titleOriginal = songInfo.title_original
        if (!titleOriginal.isNullOrEmpty()) {
            Text(
                text = titleOriginal,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = SongBookUtil.escapeSongBookName(songInfo.bookName).toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
