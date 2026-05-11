package yuku.alkitab.base.compose

import android.app.Activity
import android.view.ViewGroup
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
 * Bridges Java/View activities to Compose's [ModalBottomSheet]. Mounts a temporary
 * [ComposeView] under `android.R.id.content`, shows the sheet, and tears the host
 * view down once the sheet's hide animation completes.
 *
 * The lambda is given a `dismiss` callback so the caller can trigger the same exit
 * animation programmatically (e.g. when the user taps an action button).
 */
object ComposeBottomSheetHost {
    @OptIn(ExperimentalMaterial3Api::class)
    @JvmStatic
    fun show(activity: Activity, content: @Composable (dismiss: () -> Unit) -> Unit) {
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
                        onDismissRequest = { visible = false },
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
                        // Wait one frame so ModalBottomSheet finishes its exit animation
                        // before the host view detaches and disposes the Composition.
                        root.post { root.removeView(composeView) }
                    }
                }
            }
        }
        root.addView(composeView)
    }
}
