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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.compose.ComposeBottomSheetHost
import yuku.alkitab.base.compose.colorpicker.ColorPickerDialog
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.base.verses.AttributeState
import yuku.alkitab.base.verses.VerseItemComposeContent
import yuku.alkitab.base.verses.VerseItemComposeState
import yuku.alkitab.base.verses.VersesController
import yuku.alkitab.base.widget.VerseRendererCompose
import yuku.alkitab.debug.R
import yuku.alkitab.util.Ari

object HighlightStyleSheet {
    private data class Sample(val ari: Int, val reference: String, val rawText: String)

    internal fun renderPreview(
        ari: Int,
        rawText: String,
        isVerseNumberShown: Boolean,
        highlight: Int,
        background: Int,
        amount: Float,
    ): VerseRendererCompose.Result {
        val rendered = VerseRendererCompose.render(isVerseNumberShown, ari, rawText)
        val text = AnnotatedString.Builder(rendered.text).apply {
            addStyle(
                SpanStyle(background = Color(Highlights.blendOver(highlight, background, amount))),
                rendered.startPosAfterVerseNumber,
                rendered.text.length,
            )
        }.toAnnotatedString()
        return rendered.copy(text = text)
    }

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
        val samples by produceState<List<Sample>?>(null, version) {
            value = withContext(Dispatchers.IO) {
                val book = version.getBook(Preferences.getInt(Prefkey.lastBookId, 0)) ?: version.firstBook
                val chapter = Preferences.getInt(Prefkey.lastChapter, 1).coerceIn(1, book.chapter_count)
                (1..minOf(3, book.verse_counts[chapter - 1])).mapNotNull { verse ->
                    val ari = Ari.encode(book.bookId, chapter, verse)
                    val text = version.loadVerseText(ari)
                    if (text.isNullOrBlank()) null else Sample(ari, version.reference(ari), text)
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
                    else -> samples!!.forEach { sample ->
                        Text(sample.reference, color = Color(foreground), style = MaterialTheme.typography.labelMedium)
                        val isVerseNumberShown = remember {
                            Preferences.getBoolean(R.string.pref_verseNumberIsShown_key, R.bool.pref_verseNumberIsShown_default)
                        }
                        val rendered = remember(sample, isVerseNumberShown, highlight, background, amount, applied) {
                            renderPreview(sample.ari, sample.rawText, isVerseNumberShown, highlight, background, amount)
                        }
                        val state = VerseItemComposeState(
                            render = rendered,
                            fontSizeDp = applied.fontSize2dp,
                            verseNumberFontSizeDp = applied.fontSize2dp * 0.7f,
                            fontColor = foreground,
                            verseNumberColor = applied.verseNumberColor,
                            lineSpacingMult = applied.lineSpacingMult,
                            typeface = applied.fontFace,
                            fontBold = applied.fontBold,
                            attribute = AttributeState(
                                bookmarkCount = 0, noteCount = 0, progressMarkBits = 0,
                                hasMaps = false, scale = 1f, version = null, versionId = null,
                                ari = sample.ari,
                                attributeListener = remember { object : VersesController.AttributeListener() {} },
                                progressMarkCaptions = emptyList(),
                            ),
                            onClick = {}, onInlineLinkClick = { _, _ -> }, onPinDropped = {},
                        )
                        VerseItemComposeContent(
                            state = state, checked = false, collapsed = false,
                            audioHighlightColor = 0, attentionStart = 0L, dragHover = false,
                            onAttentionDone = {},
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
