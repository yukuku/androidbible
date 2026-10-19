package yuku.alkitab.base.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.compose.ComposeBottomSheetHost
import yuku.alkitab.base.compose.colorpicker.ColorPickerDialog
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.FormattedVerseText
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.debug.R
import yuku.alkitab.util.Ari

object HighlightStyleSheet {
    fun show(activity: Activity, onSave: (Float) -> Unit) {
        ComposeBottomSheetHost.show(activity) { dismiss ->
            Content(activity, onSave = { onSave(it); dismiss() }, onDismiss = dismiss)
        }
    }

    @Composable
    private fun Content(activity: Activity, onSave: (Float) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
        val applied = remember { App.services.uiDimensions.applied() }
        var amount by rememberSaveable {
            mutableFloatStateOf(Highlights.normalizeAdjustment(Preferences.getFloat(Prefkey.highlightColorAdjustment, 1f)))
        }
        var background by rememberSaveable { mutableIntStateOf(applied.backgroundColor) }
        var foreground by rememberSaveable { mutableIntStateOf(applied.fontColor) }
        var highlight by rememberSaveable { mutableIntStateOf(0xffffff00.toInt()) }
        val version = remember { App.services.versions.activeVersion() }
        val samples by produceState<List<Pair<String, String>>?>(null, version) {
            value = withContext(Dispatchers.IO) {
                val book = version.getBook(Preferences.getInt(Prefkey.lastBookId, 0)) ?: version.firstBook
                val chapter = Preferences.getInt(Prefkey.lastChapter, 1).coerceIn(1, book.chapter_count)
                (1..minOf(3, book.verse_counts[chapter - 1])).mapNotNull { verse ->
                    val ari = Ari.encode(book.bookId, chapter, verse)
                    val text = FormattedVerseText.removeSpecialCodes(version.loadVerseText(ari))
                    if (text.isNullOrBlank()) null else version.reference(ari) to text
                }
            }
        }
        val sliderLabel = stringResource(R.string.highlight_style_adjustment)
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.highlight_style_customize), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.highlight_style_explanation))
            Text(when (amount) {
                0f -> stringResource(R.string.highlight_style_original)
                1f -> stringResource(R.string.highlight_style_current)
                else -> stringResource(R.string.highlight_style_custom_value, (amount * 100).roundToInt())
            })
            Slider(
                value = amount,
                onValueChange = { amount = it },
                modifier = Modifier.semantics { contentDescription = sliderLabel },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { amount = 0f }) { Text(stringResource(R.string.highlight_style_zero)) }
                TextButton(onClick = { amount = 1f }) { Text(stringResource(R.string.highlight_style_one)) }
            }
            Text(stringResource(R.string.highlight_style_preview), style = MaterialTheme.typography.titleMedium)
            Text(version.longName, style = MaterialTheme.typography.labelMedium)
            Column(
                modifier = Modifier.fillMaxWidth().background(Color(background)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    samples == null -> Text(stringResource(R.string.highlight_style_loading), color = Color(foreground))
                    samples!!.isEmpty() -> Text(stringResource(R.string.highlight_style_no_verses), color = Color(foreground))
                    else -> samples!!.forEach { (reference, text) ->
                        Text(
                            text = buildAnnotatedString {
                                append("$reference\n")
                                withStyle(SpanStyle(background = Color(Highlights.blendOver(highlight, background, amount)))) {
                                    append(text)
                                }
                            },
                            color = Color(foreground),
                            fontFamily = applied.fontFace?.let { FontFamily(Typeface(it)) },
                            fontWeight = if (applied.fontBold == android.graphics.Typeface.BOLD) FontWeight.Bold else FontWeight.Normal,
                            fontSize = applied.fontSize2dp.sp,
                        )
                    }
                }
            }
            Text(stringResource(R.string.highlight_style_preview_only), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { ColorPickerDialog.show(activity, background) { background = it } }) {
                Text(stringResource(R.string.highlight_style_background))
            }
            TextButton(onClick = { ColorPickerDialog.show(activity, foreground) { foreground = it } }) {
                Text(stringResource(R.string.highlight_style_text))
            }
            TextButton(onClick = { ColorPickerDialog.show(activity, highlight) { highlight = it } }) {
                Text(stringResource(R.string.highlight_style_color))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { onSave(amount) }) { Text(stringResource(R.string.ok)) }
            }
        }
    }
}
