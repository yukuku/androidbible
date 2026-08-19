package yuku.alkitab.base.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import yuku.alkitab.base.App
import yuku.alkitab.base.compose.ComposeBottomSheetHost
import yuku.alkitab.base.compose.colorpicker.ColorPickerDialog
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.debug.R
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList

/**
 * Highlight-color picker shown as a Compose [androidx.compose.material3.ModalBottomSheet].
 * Two entry points: single verse (with optional partial-highlight offsets) and multiple
 * verses (full-verse highlight only).
 */
class TypeHighlightDialog {
    fun interface Listener {
        /** @param colorRgb -1 if not colored (i.e. delete) */
        fun onOk(colorRgb: Int)
    }

    /** Single verse, with optional partial-highlight range. */
    constructor(
        context: Context,
        ari: Int,
        listener: Listener,
        defaultColorRgb: Int,
        info: Highlights.Info?,
        title: String,
        verseText: CharSequence?,
    ) {
        val verses = IntArrayList(1).apply { add(Ari.toVerse(ari)) }
        show(context, Ari.toBookChapter(ari), verses, listener, defaultColorRgb, info, title, verseText)
    }

    /** Multiple verses — no partial highlight. */
    constructor(
        context: Context,
        ariBookChapter: Int,
        selectedVerses: IntArrayList,
        listener: Listener,
        defaultColorRgb: Int,
        title: String,
    ) {
        show(context, ariBookChapter, selectedVerses, listener, defaultColorRgb, info = null, title = title, verseText = null)
    }

    private fun show(
        context: Context,
        ariBookChapter: Int,
        selectedVerses: IntArrayList,
        listener: Listener,
        defaultColorRgb: Int,
        info: Highlights.Info?,
        title: String,
        verseText: CharSequence?,
    ) {
        val activity = context.findActivity() ?: return
        val hasExistingHighlight = App.services.storage.db.anyVerseHasHighlight(ariBookChapter, selectedVerses)
        ComposeBottomSheetHost.show(activity) { dismiss ->
            HighlightSheetContent(
                title = title,
                verseText = verseText,
                selectedVerseCount = selectedVerses.size(),
                defaultColorRgb = defaultColorRgb,
                info = info,
                hasExistingHighlight = hasExistingHighlight,
                onPickColor = { colorRgb, range ->
                    applySelection(ariBookChapter, selectedVerses, colorRgb, range, verseText)
                    listener.onOk(colorRgb)
                    dismiss()
                },
                onOpenColorPicker = { activeColorRgb, currentRange ->
                    ColorPickerDialog.show(
                        context,
                        if (activeColorRgb == -1) 0xff000000.toInt() else activeColorRgb,
                    ) { color ->
                        applySelection(ariBookChapter, selectedVerses, color and 0xffffff, currentRange, verseText)
                        listener.onOk(color and 0xffffff)
                        dismiss()
                    }
                },
                onDelete = {
                    applySelection(ariBookChapter, selectedVerses, -1, null, verseText)
                    listener.onOk(-1)
                    dismiss()
                },
                onConfirmPartialEdit = { range ->
                    // OK commits only when the partial-highlight range changed against
                    // what's stored; otherwise it's a no-op.
                    if (info != null && verseText != null && defaultColorRgb != -1 && range != null) {
                        val partial = info.partial
                        val changed = (partial == null && (range.first != 0 || range.second != verseText.length)) ||
                            (partial != null && (partial.startOffset != range.first || partial.endOffset != range.second))
                        if (changed) {
                            applySelection(ariBookChapter, selectedVerses, defaultColorRgb, range, verseText)
                            listener.onOk(defaultColorRgb)
                        }
                    }
                    dismiss()
                },
                onCancel = dismiss,
            )
        }
    }

    /** Apply the highlight color & optional range to the database. -1 deletes. */
    private fun applySelection(
        ariBookChapter: Int,
        selectedVerses: IntArrayList,
        colorRgb: Int,
        range: Pair<Int, Int>?,
        verseText: CharSequence?,
    ) {
        val partial = selectedVerses.size() == 1 &&
            verseText != null &&
            colorRgb != -1 &&
            range != null &&
            (range.first != 0 || range.second != verseText.length) &&
            range.first != range.second
        if (partial) {
            val start = minOf(range!!.first, range.second)
            val end = maxOf(range.first, range.second)
            App.services.storage.db.updateOrInsertPartialHighlight(
                Ari.encodeWithBc(ariBookChapter, selectedVerses.get(0)),
                colorRgb,
                verseText,
                start,
                end,
            )
        } else {
            App.services.storage.db.updateOrInsertHighlights(ariBookChapter, selectedVerses, colorRgb)
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    companion object {
        /** Preset colors (rgb without alpha). Order matches the on-screen swatch layout. */
        internal val PRESET_RGBS = intArrayOf(
            0xff0000, 0xff8000, 0xffff00, 0x80ff00, 0x00ff00, 0x00ff80,
            0x00ffff, 0x0080ff, 0x0000ff, 0x8000ff, 0xff00ff, 0xff0080,
        )
    }
}

@Composable
private fun HighlightSheetContent(
    title: String,
    verseText: CharSequence?,
    selectedVerseCount: Int,
    defaultColorRgb: Int,
    info: Highlights.Info?,
    hasExistingHighlight: Boolean,
    onPickColor: (colorRgb: Int, range: Pair<Int, Int>?) -> Unit,
    onOpenColorPicker: (currentColorRgb: Int, currentRange: Pair<Int, Int>?) -> Unit,
    onDelete: () -> Unit,
    onConfirmPartialEdit: (range: Pair<Int, Int>?) -> Unit,
    onCancel: () -> Unit,
) {
    val showVerseText = selectedVerseCount == 1 && verseText != null
    val verseTextString = verseText?.toString().orEmpty()

    // The range the stored highlight covers, or null when this verse has no highlight yet.
    val existingRange = remember(verseText, info, defaultColorRgb) {
        when {
            !showVerseText || defaultColorRgb == -1 -> null
            info != null && info.shouldRenderAsPartialForVerseText(verseText) ->
                info.partial!!.startOffset to info.partial!!.endOffset
            else -> 0 to verseTextString.length
        }
    }

    var textFieldValue by remember { mutableStateOf(TextFieldValue(verseTextString)) }

    // Drag-selecting picks a new range. With nothing selected the sheet keeps whatever the
    // highlight already covers, so re-coloring a partial highlight stays partial.
    fun currentRange(): Pair<Int, Int>? = when {
        !showVerseText -> null
        !textFieldValue.selection.collapsed -> textFieldValue.selection.start to textFieldValue.selection.end
        else -> existingRange ?: (0 to verseTextString.length)
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_ink_highlighter),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(12.dp))

        if (showVerseText) {
            VerseTextSelectable(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                highlightColorRgb = if (defaultColorRgb == -1) null else defaultColorRgb,
                highlightRange = existingRange,
            )
            Spacer(Modifier.height(12.dp))
        }

        PresetColorGrid(
            selectedColorRgb = defaultColorRgb,
            onColorPicked = { rgb -> onPickColor(rgb, currentRange()) },
        )

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = { onOpenColorPicker(defaultColorRgb, currentRange()) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.highlight_custom_color))
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (hasExistingHighlight) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete))
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onConfirmPartialEdit(currentRange()) }) {
                Text(stringResource(R.string.ok))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Read-only verse text, showing the highlight the verse currently carries. Drag-select within
 * it picks the partial-highlight range.
 *
 * It sits on the reading background so the verse reads the same here as it does
 * in [yuku.alkitab.base.IsiActivity], where the user just selected it.
 */
@Composable
private fun VerseTextSelectable(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    highlightColorRgb: Int?,
    highlightRange: Pair<Int, Int>?,
) {
    val applied = App.services.uiDimensions.applied()
    val backgroundColor = applied.backgroundColor
    val transformation = remember(highlightColorRgb, highlightRange, backgroundColor) {
        if (highlightColorRgb == null || highlightRange == null) {
            VisualTransformation.None
        } else {
            val blended = Color(Highlights.blendOver(highlightColorRgb, backgroundColor))
            VisualTransformation { text ->
                val start = minOf(highlightRange.first, highlightRange.second).coerceIn(0, text.length)
                val end = maxOf(highlightRange.first, highlightRange.second).coerceIn(start, text.length)
                val styled = AnnotatedString.Builder(text)
                    .apply { addStyle(SpanStyle(background = blended), start, end) }
                    .toAnnotatedString()
                TransformedText(styled, OffsetMapping.Identity)
            }
        }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = true,
        visualTransformation = transformation,
        textStyle = LocalTextStyle.current.copy(
            color = Color(applied.fontColor),
            fontSize = 16.sp,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(backgroundColor))
            .padding(8.dp),
    )
}

@Composable
private fun PresetColorGrid(
    selectedColorRgb: Int,
    onColorPicked: (Int) -> Unit,
) {
    val rgbs = TypeHighlightDialog.PRESET_RGBS
    val rowsLayout = listOf(
        listOf(0, 1, 2, 3, 4, 5),
        listOf(11, 10, 9, 8, 7, 6),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rowsLayout.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { idx ->
                    val rgb = rgbs[idx]
                    val isSelected = (rgb and 0xffffff) == (selectedColorRgb and 0xffffff)
                    ColorSwatch(
                        rgb = rgb,
                        selected = isSelected,
                        onClick = { onColorPicked(rgb) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    rgb: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = App.services.uiDimensions.applied().backgroundColor
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(backgroundColor))
            .background(Color(Highlights.blendOver(rgb, backgroundColor)))
            .border(
                width = if (selected) 3.dp else 0.5.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(6.dp),
            )
            .pointerInput(rgb) {
                detectTapGestures { onClick() }
            },
    )
}
