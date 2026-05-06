package yuku.alkitab.base.compose.goto

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.debug.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GotoScreen(
    initialBookId: Int,
    initialChapter_1: Int,
    initialVerse_1: Int,
    onUp: () -> Unit,
    onGotoFinished: OnGotoFinished,
    viewModel: GotoViewModel = viewModel(),
) {
    val askForVerse by viewModel.askForVerse.collectAsState()
    val initialPage = remember {
        val saved = Preferences.getInt(Prefkey.goto_last_tab, 0)
        (saved - 1).coerceIn(0, 2)
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != GotoTab.DIRECT.ordinal1Based - 1) focusManager.clearFocus()
    }

    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("") },
                    navigationIcon = {
                        IconButton(onClick = onUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                        }
                    },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menuAskForVerse)) },
                                leadingIcon = { Checkbox(checked = askForVerse, onCheckedChange = null) },
                                onClick = {
                                    Preferences.setBoolean(Prefkey.gotoAskForVerse, !askForVerse)
                                    menuOpen = false
                                },
                            )
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                    val titles = listOf(
                        R.string.goto_tab_dialer_label,
                        R.string.goto_tab_direct_label,
                        R.string.goto_tab_grid_label,
                    )
                    titles.forEachIndexed { idx, resId ->
                        Tab(
                            selected = pagerState.currentPage == idx,
                            onClick = { scope.launch { pagerState.animateScrollToPage(idx) } },
                            text = { Text(stringResource(resId)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(padding)) { page ->
            when (page) {
                0 -> DialerTab(initialBookId, initialChapter_1, initialVerse_1, askForVerse, onGotoFinished)
                1 -> DirectTab(
                    initialBookId,
                    initialChapter_1,
                    initialVerse_1,
                    autoFocus = (initialPage == 1),
                    onGotoFinished = onGotoFinished,
                )
                2 -> GridTab(askForVerse = askForVerse, viewModel = viewModel, onGotoFinished = onGotoFinished)
            }
        }
    }
}
