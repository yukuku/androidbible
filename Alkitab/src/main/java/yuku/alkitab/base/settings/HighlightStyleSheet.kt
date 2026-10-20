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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.compose.ComposeBottomSheetHost
import yuku.alkitab.base.dialog.TypeHighlightDialog
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.base.verses.AttributeState
import yuku.alkitab.base.verses.VerseItemComposeContent
import yuku.alkitab.base.verses.VerseItemComposeState
import yuku.alkitab.base.verses.VersesController
import yuku.alkitab.base.widget.VerseRendererCompose
import yuku.alkitab.debug.R
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.Ari

object HighlightStyleSheet {
    internal data class Sample(
        val ari: Int,
        val rawText: String,
        val verseNumberText: String,
        val highlight: Highlights.Info,
    )

    internal fun selectSamples(
        ariBookChapter: Int,
        verses: SingleChapterVerses,
        highlights: Array<Highlights.Info?>,
    ): List<Sample> {
        val available = (0 until verses.verseCount).filter { verses.getVerse(it).isNotBlank() }
        val selected = available.filter { highlights[it] != null }.take(3)
        val fillers = available.filter { highlights[it] == null }.take(3 - selected.size)
        val sampleColors = intArrayOf(2, 4, 7).map { TypeHighlightDialog.PRESET_RGBS[it] }
        val sampleHighlights = fillers.mapIndexed { index, verseIndex ->
            verseIndex to Highlights.Info().apply { colorRgb = sampleColors[index] }
        }.toMap()

        return (selected + fillers).sorted().map { index ->
            val highlight = highlights[index] ?: sampleHighlights.getValue(index)
            Sample(
                ari = Ari.encodeWithBc(ariBookChapter, index + 1),
                rawText = verses.getVerse(index),
                verseNumberText = verses.getVerseNumberText(index),
                highlight = highlight,
            )
        }
    }

    internal fun renderPreview(
        sample: Sample,
        isVerseNumberShown: Boolean,
        amount: Float,
    ): VerseRendererCompose.Result = VerseRendererCompose.render(
        isVerseNumberShown = isVerseNumberShown,
        ari = sample.ari,
        text = sample.rawText,
        verseNumberText = sample.verseNumberText,
        highlightInfo = sample.highlight,
        highlightAdjustment = amount,
    )

    fun show(activity: Activity, onSave: (Float) -> Unit) {
        ComposeBottomSheetHost.show(activity) { dismiss ->
            Content(
                onSave = {
                    onSave(it)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
    }

    @Composable
    private fun Content(onSave: (Float) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
        val applied = remember { App.services.uiDimensions.applied() }
        var amount by rememberSaveable {
            mutableFloatStateOf(Highlights.normalizeAdjustment(Preferences.getFloat(Prefkey.highlightColorAdjustment, 1f)))
        }

        val version = remember { App.services.versions.activeVersion() }
        val samples by produceState<List<Sample>?>(null, version) {
            value = withContext(Dispatchers.IO) {
                val book = version.getBook(Preferences.getInt(Prefkey.lastBookId, 0)) ?: version.firstBook
                val chapter = Preferences.getInt(Prefkey.lastChapter, 1).coerceIn(1, book.chapter_count)

                val verses = version.loadChapterText(book, chapter) ?: return@withContext emptyList()
                val highlights = arrayOfNulls<Highlights.Info>(verses.verseCount)
                val ariBookChapter = Ari.encode(book.bookId, chapter, 0)
                App.services.storage.db.putAttributes(
                    ariBookChapter, IntArray(verses.verseCount), IntArray(verses.verseCount), highlights,
                )

                selectSamples(ariBookChapter, verses, highlights)
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
                modifier = Modifier.fillMaxWidth().background(Color(applied.backgroundColor)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val loadedSamples = samples

                when {
                    loadedSamples == null -> Text(stringResource(R.string.highlight_style_loading), color = Color(applied.fontColor))
                    loadedSamples.isEmpty() -> Text(stringResource(R.string.highlight_style_no_verses), color = Color(applied.fontColor))
                    else -> loadedSamples.forEach { sample ->
                        val isVerseNumberShown = remember {
                            Preferences.getBoolean(R.string.pref_verseNumberIsShown_key, R.bool.pref_verseNumberIsShown_default)
                        }
                        val rendered = remember(sample, isVerseNumberShown, amount, applied) {
                            renderPreview(sample, isVerseNumberShown, amount)
                        }

                        val state = VerseItemComposeState(
                            render = rendered,
                            fontSizeDp = applied.fontSize2dp,
                            verseNumberFontSizeDp = applied.fontSize2dp * 0.7f,
                            fontColor = applied.fontColor,
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

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { onSave(amount) }) { Text(stringResource(R.string.ok)) }
            }
        }
    }
}
