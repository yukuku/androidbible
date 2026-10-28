package yuku.alkitab.base.compose

import android.app.Activity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch

/**
 * Hosts a Compose [ModalBottomSheet] inside any activity by attaching a temporary
 * [ComposeView] under `android.R.id.content`. The `dismiss` lambda passed to [content]
 * runs the same hide animation that back-press and tap-outside trigger.
 */
object ComposeBottomSheetHost {
    @OptIn(ExperimentalMaterial3Api::class)
    @JvmStatic
    fun show(activity: Activity, sheetGesturesEnabled: Boolean = true, dimBackground: Boolean = true, content: @Composable (dismiss: () -> Unit) -> Unit) {
        show(activity, BottomSheetDefaults.SheetMaxWidth, sheetGesturesEnabled, dimBackground, content = content)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun show(
        activity: Activity,
        sheetMaxWidth: Dp,
        sheetGesturesEnabled: Boolean,
        dimBackground: Boolean,
        contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
        content: @Composable (dismiss: () -> Unit) -> Unit,
    ) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val composeView = ComposeView(activity)
        composeView.setContent {
            BibleAppTheme {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val scope = rememberCoroutineScope()
                var visible by remember { mutableStateOf(true) }

                if (visible) {
                    ModalBottomSheet(
                        sheetState = sheetState,
                        sheetMaxWidth = sheetMaxWidth,
                        contentWindowInsets = contentWindowInsets,
                        scrimColor = if (dimBackground) BottomSheetDefaults.ScrimColor else Color.Transparent,
                        sheetGesturesEnabled = sheetGesturesEnabled,
                        dragHandle = if (sheetGesturesEnabled) {
                            { BottomSheetDefaults.DragHandle() }
                        } else {
                            null
                        },
                        onDismissRequest = {
                            // Run the hide animation before tearing down so back-press
                            // and tap-outside don't snap the sheet away.
                            scope.launch {
                                sheetState.hide()
                                visible = false
                            }
                        },
                    ) {
                        if (!dimBackground) {
                            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
                            SideEffect {
                                // The dialog window can dim independently of the Compose scrim.
                                window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                                window?.setDimAmount(0f)
                            }
                        }
                        content {
                            scope.launch {
                                sheetState.hide()
                                visible = false
                            }
                        }
                    }
                }

                LaunchedEffect(visible) {
                    if (!visible) {
                        // Defer the detach by a frame so the exit animation completes
                        // before the Composition is disposed.
                        root.post { root.removeView(composeView) }
                    }
                }
            }
        }
        root.addView(composeView)
    }
}
