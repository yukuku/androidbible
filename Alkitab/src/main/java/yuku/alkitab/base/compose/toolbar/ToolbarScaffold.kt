package yuku.alkitab.base.compose.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun ToolbarScaffold(
    isToolbarAtBottom: Boolean,
    toolbarColor: Color,
    toolbar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val bar: @Composable () -> Unit = {
        val edge = if (isToolbarAtBottom) WindowInsetsSides.Bottom else WindowInsetsSides.Top
        Box(
            Modifier
                .background(toolbarColor)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(edge + WindowInsetsSides.Horizontal)),
        ) {
            toolbar()
        }
    }
    Scaffold(
        modifier = modifier.imePadding(),
        containerColor = containerColor,
        topBar = { if (!isToolbarAtBottom) bar() },
        bottomBar = { if (isToolbarAtBottom) bar() },
        snackbarHost = snackbarHost,
        content = content,
    )
}
