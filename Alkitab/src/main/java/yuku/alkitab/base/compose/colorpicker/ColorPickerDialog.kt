package yuku.alkitab.base.compose.colorpicker

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.debug.R

/**
 * Java-friendly wrapper around [IosColorPicker]. Shows the picker inside a
 * Material alert dialog and reports the chosen RGB color via [Listener].
 */
object ColorPickerDialog {
    /** Java functional interface — called on OK with the chosen RGB color (alpha = 0xff). */
    fun interface Listener {
        fun onColorPicked(color: Int)
    }

    /**
     * @param context activity context — must have a Material theme
     * @param initialColor starting color; alpha bits are ignored
     * @param listener invoked when the user taps OK
     */
    @JvmStatic
    fun show(context: Context, initialColor: Int, listener: Listener) {
        val composeView = ComposeView(context)
        // The Compose runtime needs a lifecycle owner; ComposeView attaches itself
        // automatically when added to a View tree that has one (AppCompatActivity does).
        var currentColor = initialColor or 0xff000000.toInt()
        composeView.setContent {
            BibleAppTheme {
                var selected by remember { mutableStateOf(currentColor) }
                IosColorPicker(
                    initialColor = initialColor,
                    onColorChanged = {
                        selected = it
                        currentColor = it
                    },
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        MaterialAlertDialogBuilder(context)
            .setView(composeView)
            .setPositiveButton(R.string.ok) { _, _ -> listener.onColorPicked(currentColor) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
