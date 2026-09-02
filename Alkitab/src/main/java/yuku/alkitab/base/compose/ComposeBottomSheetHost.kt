package yuku.alkitab.base.compose

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.launch

/**
 * Hosts a Compose [ModalBottomSheet] inside any activity by attaching a temporary
 * [ComposeView] under `android.R.id.content`. The `dismiss` lambda passed to [content]
 * runs the same hide animation that back-press and tap-outside trigger.
 */
object ComposeBottomSheetHost {
    @OptIn(ExperimentalMaterial3Api::class)
    @JvmStatic
    fun show(activity: Activity, sheetGesturesEnabled: Boolean = true, content: @Composable (dismiss: () -> Unit) -> Unit) {
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
                        sheetGesturesEnabled = sheetGesturesEnabled,
                        // The handle implies a drag gesture that isn't there when disabled.
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
