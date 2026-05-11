package yuku.alkitab.base.compose.colorpicker

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import yuku.alkitab.base.compose.ComposeBottomSheetHost
import yuku.alkitab.debug.R

/**
 * Java-friendly wrapper that presents [IosColorPicker] inside a Compose
 * [androidx.compose.material3.ModalBottomSheet].
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
        val activity = context.findActivity() ?: return
        ComposeBottomSheetHost.show(activity) { dismiss ->
            var currentColor by remember { mutableStateOf(initialColor or 0xff000000.toInt()) }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
            ) {
                IosColorPicker(
                    initialColor = initialColor,
                    onColorChanged = { currentColor = it },
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = dismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        listener.onColorPicked(currentColor)
                        dismiss()
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
