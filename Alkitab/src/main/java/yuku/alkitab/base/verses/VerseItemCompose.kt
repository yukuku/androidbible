package yuku.alkitab.base.verses

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.Context
import android.util.AttributeSet
import android.view.DragEvent
import android.view.accessibility.AccessibilityEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface as ComposeTypeface
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.safeQuery
import yuku.alkitab.base.widget.AttributeView
import yuku.alkitab.base.widget.DictionaryLinkInfo
import yuku.alkitab.base.widget.LeftDrawer.PROGRESS_MARK_DRAG_MIME_TYPE
import yuku.alkitab.base.widget.VerseInlineLinkSpan
import yuku.alkitab.base.widget.VerseRendererCompose
import yuku.alkitab.debug.R
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari

/**
 * Inputs to a single verse row that don't change between recompositions.
 * Held in `mutableStateOf` so [VerseItemComposeView.bind] triggers a
 * recomposition with the new values.
 *
 * Properties that DO change in place — `checked`, `collapsed`,
 * `audioHighlightColor`, `attentionStart`, `dragHover` — live directly on the
 * view so callers can poke them without rebuilding the whole state.
 */
data class VerseItemComposeState(
    val render: VerseRendererCompose.Result,
    val fontSizeDp: Float,
    /** Verse-number gutter text size in DIP (0.7 × main size). */
    val verseNumberFontSizeDp: Float,
    val fontColor: Int,
    val verseNumberColor: Int,
    val lineSpacingMult: Float,
    val typeface: android.graphics.Typeface?,
    val fontBold: Int,
    val attribute: AttributeState,
    val onClick: () -> Unit,
    val onInlineLinkClick: (VerseInlineLinkSpan.Type, Int) -> Unit,
    val onPinDropped: (presetId: Int) -> Unit,
)

data class AttributeState(
    val bookmarkCount: Int,
    val noteCount: Int,
    val progressMarkBits: Int,
    val hasMaps: Boolean,
    val scale: Float,
    val version: Version?,
    val versionId: String?,
    val ari: Int,
    val attributeListener: VersesController.AttributeListener,
    /**
     * Captions for set progress-mark bits, resolved once at bind time so the
     * accessibility path — which TalkBack can hit repeatedly per row — doesn't
     * run a DB query on every read. Length equals
     * [AttributeView.PROGRESS_MARK_TOTAL_COUNT]; entry is `null` when the
     * matching bit is unset (or no row exists).
     */
    val progressMarkCaptions: List<String?>,
)

private const val TAG = "VerseItemCompose"

private const val ATTENTION_DURATION_MS = 2000f
private const val AUDIO_HIGHLIGHT_FLASH_ALPHA = 0.60f
private const val AUDIO_HIGHLIGHT_STEADY_ALPHA = 0.20f
private const val AUDIO_HIGHLIGHT_FLASH_MS = 500

/**
 * Compose-backed verse row. Exposes a small mutable surface
 * (`checked`, `collapsed`, `audioHighlightColor`, `callAttention()`) so the
 * RecyclerView controller can update the row state without rebinding, and
 * handles drag-drop at the View level (it's simpler than translating
 * `DragEvent` into a Compose drag target).
 */
class VerseItemComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AbstractComposeView(context, attrs) {

    private var state by mutableStateOf<VerseItemComposeState?>(null)

    private val checkedState = mutableStateOf(false)
    var checked: Boolean
        get() = checkedState.value
        set(value) { checkedState.value = value }

    private val collapsedState = mutableStateOf(false)
    var collapsed: Boolean
        get() = collapsedState.value
        set(value) { collapsedState.value = value }

    private val audioHighlightColorState = mutableIntStateOf(0)
    /** Setting a non-zero color triggers the overlay fade in. */
    var audioHighlightColor: Int
        get() = audioHighlightColorState.intValue
        set(value) { audioHighlightColorState.intValue = value }

    private val attentionStartState = mutableLongStateOf(0L)
    /** `startTime` is wall-clock millis; pass `0` to clear the flash. */
    fun callAttention(startTime: Long) {
        attentionStartState.longValue = startTime
    }

    private val dragHoverState = mutableStateOf(false)
    private var onPinDroppedHandler: (presetId: Int) -> Unit = {}

    fun bind(newState: VerseItemComposeState) {
        this.state = newState
        this.onPinDroppedHandler = newState.onPinDropped
    }

    @Composable
    override fun Content() {
        val s = state ?: return
        VerseItemComposeContent(
            state = s,
            checked = checkedState.value,
            collapsed = collapsedState.value,
            audioHighlightColor = audioHighlightColorState.intValue,
            attentionStart = attentionStartState.longValue,
            dragHover = dragHoverState.value,
            onAttentionDone = { attentionStartState.longValue = 0L },
        )
    }

    override fun onDragEvent(event: DragEvent): Boolean = when (event.action) {
        DragEvent.ACTION_DRAG_STARTED -> {
            val desc: ClipDescription? = event.clipDescription
            desc != null && desc.hasMimeType(PROGRESS_MARK_DRAG_MIME_TYPE)
        }
        DragEvent.ACTION_DRAG_ENTERED -> {
            dragHoverState.value = true
            true
        }
        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
            dragHoverState.value = false
            true
        }
        DragEvent.ACTION_DROP -> {
            // Refuse the drop if the payload is missing or non-numeric instead
            // of throwing NumberFormatException out of `Integer.parseInt`.
            val presetId = event.clipData?.getItemAt(0)?.text?.toString()?.toIntOrNull()
            if (presetId == null) {
                false
            } else {
                onPinDroppedHandler(presetId)
                true
            }
        }
        else -> false
    }

    /**
     * Make TalkBack read [getContentDescription] instead of descending into
     * the Compose subtree (which would announce nothing useful).
     */
    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
        event.text.add(contentDescription)
        return true
    }

    @SuppressLint("GetContentDescriptionOverride")
    override fun getContentDescription(): CharSequence {
        val s = state ?: return ""
        return verseItemContentDescription(context, s)
    }
}

/**
 * TalkBack description of a verse row: gutter verse number (when present),
 * verse text, then attribute summaries. Progress-mark captions come
 * pre-resolved on [AttributeState.progressMarkCaptions] so this path stays
 * free of DB I/O.
 */
@SuppressLint("StringFormatMatches")
internal fun verseItemContentDescription(context: Context, s: VerseItemComposeState): CharSequence {
    val res = StringBuilder()

    val gutter = s.render.gutterVerseNumber
    if (gutter != null) {
        res.append(gutter).append(' ')
    }
    res.append(s.render.text.text)

    val bookmarkCount = s.attribute.bookmarkCount
    if (bookmarkCount == 1) {
        res.append(' ').append(context.getString(R.string.desc_verse_attribute_one_bookmark))
    } else if (bookmarkCount > 1) {
        res.append(' ').append(context.getString(R.string.desc_verse_attribute_multiple_bookmarks, bookmarkCount))
    }

    val noteCount = s.attribute.noteCount
    if (noteCount == 1) {
        res.append(' ').append(context.getString(R.string.desc_verse_attribute_one_note))
    } else if (noteCount > 1) {
        res.append(' ').append(context.getString(R.string.desc_verse_attribute_multiple_notes, noteCount))
    }

    val progressMarkBits = s.attribute.progressMarkBits
    for (presetId in 0 until AttributeView.PROGRESS_MARK_TOTAL_COUNT) {
        if (progressMarkBits and (1 shl AttributeView.PROGRESS_MARK_BITS_START + presetId) != 0) {
            s.attribute.progressMarkCaptions.getOrNull(presetId)?.let { caption ->
                res.append(' ').append(context.getString(R.string.desc_verse_attribute_progress_mark, caption))
            }
        }
    }

    return res
}

/**
 * Runs the rendered verse text through the dictionary app's analyzer content
 * provider and wraps every recognized word in an underlined, tappable
 * [LinkAnnotation] that opens the dictionary — the Compose counterpart of the
 * legacy row's [yuku.alkitab.base.widget.DictionaryLinkSpan] decoration
 * (a ClickableSpan rendered underlined in the link color, which
 * `Appearances.applyTextAppearance` pins to the reading font color).
 *
 * Returns [render] unchanged when the provider is unavailable, reports
 * nothing, or the query fails.
 */
internal fun addDictionaryLinks(
    context: Context,
    render: VerseRendererCompose.Result,
    linkColor: Int,
    dictionaryListener: (DictionaryLinkInfo) -> Unit,
): VerseRendererCompose.Result {
    // we have to exclude the verse numbers from analyze text
    val startPos = render.startPosAfterVerseNumber
    val analyzeString = render.text.text.substring(startPos)
    if (analyzeString.isEmpty()) return render

    val hits = mutableListOf<DictionaryLinkHit>()

    val uri = "content://org.sabda.kamus.provider/analyze".toUri().buildUpon().appendQueryParameter("text", analyzeString).build()
    try {
        context.contentResolver.safeQuery(uri, null, null, null, null)?.use { c ->
            val col_offset = c.getColumnIndexOrThrow("offset")
            val col_len = c.getColumnIndexOrThrow("len")
            val col_key = c.getColumnIndexOrThrow("key")

            while (c.moveToNext()) {
                val offset = c.getInt(col_offset)
                val len = c.getInt(col_len)
                val key = c.getString(col_key)

                val word = analyzeString.substring(offset, offset + len)
                hits += DictionaryLinkHit(startPos + offset, startPos + offset + len, DictionaryLinkInfo(word, key))
            }
        }
    } catch (e: Exception) {
        AppLog.e(TAG, "Error when querying dictionary content provider", e)
        return render
    }
    if (hits.isEmpty()) return render

    val decorated = buildAnnotatedString {
        append(render.text)
        for (hit in hits) {
            addStyle(SpanStyle(color = Color(linkColor), textDecoration = TextDecoration.Underline), hit.start, hit.end)
            addLink(LinkAnnotation.Clickable("dictionary") { dictionaryListener(hit.info) }, hit.start, hit.end)
        }
    }
    return render.copy(text = decorated)
}

private class DictionaryLinkHit(val start: Int, val end: Int, val info: DictionaryLinkInfo)

/**
 * Maps Android's built-in Typeface singletons to Compose's stock
 * FontFamilies. The stock families have italic and bold variants
 * registered, so FontStyle.Italic / FontWeight.Bold resolve to the
 * correct glyphs (or synthesise cleanly). A raw FontFamily(Typeface)
 * wrapper only carries the regular variant and silently renders
 * italic upright.
 */
internal fun composeFontFamilyFor(tf: android.graphics.Typeface?): FontFamily = when (tf) {
    null, android.graphics.Typeface.DEFAULT -> FontFamily.Default
    android.graphics.Typeface.SERIF -> FontFamily.Serif
    android.graphics.Typeface.MONOSPACE -> FontFamily.Monospace
    android.graphics.Typeface.SANS_SERIF -> FontFamily.SansSerif
    else -> FontFamily(ComposeTypeface(tf))
}

/** Whether the attribute column will draw at least one icon. */
internal val AttributeState.isShowingSomething: Boolean
    get() = bookmarkCount > 0 ||
        noteCount > 0 ||
        (progressMarkBits and AttributeView.PROGRESS_MARK_BIT_MASK) != 0 ||
        hasMaps

/** Icon scale for the attribute column, stepped by the effective font size in dp. */
fun attributeViewScale(fontSizeDp: Float) = when {
    fontSizeDp >= 13 /* 72% */ && fontSizeDp < 24 /* 133% */ -> 1f
    fontSizeDp < 8 -> 0.5f // 0 ~ 44%
    fontSizeDp < 18 -> 0.75f // 44% ~ 72%
    fontSizeDp >= 36 -> 2f // 200% ~
    else -> 1.5f // 24 to 36 // 133% ~ 200%
}

/**
 * Builds the immutable per-row state consumed by [VerseItemComposeContent].
 * Shared by the RecyclerView-hosted [VerseItemComposeView] rows and the fully
 * Compose verse list so both paths render identically.
 *
 * @param currentPosition resolves the row's position at interaction time
 * (a RecyclerView rebind can move a row, so the position must not be captured
 * eagerly); returns -1 when the row is no longer attached.
 * @param inlineLinkViewProvider supplies the View handed to
 * [VerseInlineLinkSpan.onClick] (the row view, or the hosting Compose view).
 */
fun buildVerseItemComposeState(
    context: Context,
    data: VersesDataModel,
    ui: VersesUiModel,
    listeners: VersesListeners,
    index: Int,
    checked: Boolean,
    currentPosition: () -> Int,
    toggleChecked: (position: Int) -> Unit,
    inlineLinkViewProvider: () -> android.view.View,
): VerseItemComposeState {
    val verse_1 = index + 1
    val ari = Ari.encodeWithBc(data.ari_bc_, verse_1)
    val text = data.verses_.getVerse(index)
    val verseNumberText = data.verses_.getVerseNumberText(index)
    val highlightInfo = data.versesAttributes.highlightInfoMap_[index]

    val renderResult = VerseRendererCompose.render(
        isVerseNumberShown = ui.isVerseNumberShown,
        ari = ari,
        text = text,
        verseNumberText = verseNumberText,
        highlightInfo = highlightInfo,
        checked = checked,
    )

    val textSizeMult = if (data.verses_ is SingleChapterVerses.WithTextSizeMult) {
        data.verses_.getTextSizeMult(index)
    } else {
        ui.textSizeMult
    }

    val applied = App.services.uiDimensions.applied()
    val fontSizeDp = applied.fontSize2dp * textSizeMult
    val verseNumberFontSizeDp = applied.fontSize2dp * 0.7f * textSizeMult
    val attributeScale = attributeViewScale(applied.fontSize2dp * ui.textSizeMult)

    /*
     * Dictionary mode is activated on either of these conditions:
     * 1. user manually activate dictionary mode after selecting verses
     * 2. automatic lookup is on and this verse is selected (checked)
     */
    val render = if (ari in ui.dictionaryModeAris ||
        checked && Preferences.getBoolean(context.getString(R.string.pref_autoDictionaryAnalyze_key), context.resources.getBoolean(R.bool.pref_autoDictionaryAnalyze_default))
    ) {
        addDictionaryLinks(context, renderResult, applied.fontColor, listeners.dictionaryListener_)
    } else {
        renderResult
    }

    // Pre-resolve progress-mark captions at build time so the accessibility
    // path — which TalkBack can hit repeatedly per row — doesn't run a DB
    // query on every read. Mirrors the values the legacy
    // VerseItem.getContentDescription resolves inline.
    val progressMarkBits = data.versesAttributes.progressMarkBitsMap_[index]
    val progressMarkCaptions: List<String?> = (0 until AttributeView.PROGRESS_MARK_TOTAL_COUNT).map { presetId ->
        if (progressMarkBits and (1 shl (AttributeView.PROGRESS_MARK_BITS_START + presetId)) == 0) {
            null
        } else {
            App.services.storage.db.getProgressMarkByPresetId(presetId)?.let { progressMark ->
                if (progressMark.caption.isNullOrEmpty()) {
                    context.getString(AttributeView.getDefaultProgressMarkStringResource(presetId))
                } else {
                    progressMark.caption
                }
            }
        }
    }

    return VerseItemComposeState(
        render = render,
        fontSizeDp = fontSizeDp,
        verseNumberFontSizeDp = verseNumberFontSizeDp,
        fontColor = applied.fontColor,
        verseNumberColor = applied.verseNumberColor,
        lineSpacingMult = applied.lineSpacingMult,
        typeface = applied.fontFace,
        fontBold = applied.fontBold,
        attribute = AttributeState(
            bookmarkCount = data.versesAttributes.bookmarkCountMap_[index],
            noteCount = data.versesAttributes.noteCountMap_[index],
            progressMarkBits = progressMarkBits,
            hasMaps = data.versesAttributes.hasMapsMap_[index],
            scale = attributeScale,
            version = data.version_,
            versionId = data.versionId_,
            ari = ari,
            attributeListener = listeners.attributeListener,
            progressMarkCaptions = progressMarkCaptions,
        ),
        onClick = {
            when (ui.verseSelectionMode) {
                VersesController.VerseSelectionMode.none -> Unit
                VersesController.VerseSelectionMode.singleClick -> {
                    val position = currentPosition()
                    if (position != -1) {
                        listeners.selectedVersesListener.onVerseSingleClick(data.getVerse_1FromPosition(position))
                    }
                }
                VersesController.VerseSelectionMode.multiple -> {
                    val position = currentPosition()
                    if (position != -1) {
                        toggleChecked(position)
                    }
                }
            }
        },
        onInlineLinkClick = { type, arif ->
            // Reuse the same factory as the legacy path so footnote / xref
            // dialogs etc. open with identical semantics.
            listeners.inlineLinkSpanFactory_.create(type, arif).onClick(inlineLinkViewProvider())
        },
        onPinDropped = { presetId ->
            val position = currentPosition()
            if (position != -1) {
                listeners.pinDropListener.onPinDropped(presetId, Ari.encodeWithBc(data.ari_bc_, data.getVerse_1FromPosition(position)))
            }
        },
    )
}

@Composable
internal fun VerseItemComposeContent(
    state: VerseItemComposeState,
    checked: Boolean,
    collapsed: Boolean,
    audioHighlightColor: Int,
    attentionStart: Long,
    dragHover: Boolean,
    onAttentionDone: () -> Unit,
) {
    // Strip fontScale from the density so sp values render at dp dimensions
    // — verse text uses dp sizing (not sp), so system font-scale must not
    // multiply on top.
    val baseDensity = LocalDensity.current
    val unscaledDensity = remember(baseDensity.density) {
        Density(density = baseDensity.density, fontScale = 1f)
    }

    CompositionLocalProvider(LocalDensity provides unscaledDensity) {
        val lineMetrics = rememberLineMetrics(state)

        val sizeModifier = if (collapsed) {
            Modifier.fillMaxWidth().height(0.dp)
        } else {
            Modifier.fillMaxWidth().wrapContentHeight()
        }

        Box(
            modifier = sizeModifier
                .checkedOverlay(checked)
                .audioHighlightOverlay(audioHighlightColor)
                .attentionOverlay(attentionStart, onAttentionDone)
                .dragHoverOverlay(dragHover)
                .pointerInput(state.onClick) {
                    detectTapGestures(onTap = { state.onClick() })
                }
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                VerseTextRegion(
                    state = state,
                    checked = checked,
                    lineMetrics = lineMetrics,
                    modifier = Modifier.weight(1f),
                )
                // Extending the attribute column by `rowExtraPaddingPx` makes
                // the Row's height `max(textHeight, attrHeight + extra)` —
                // the right shape whether text or attributes dominate the row.
                AttributeColumn(
                    attribute = state.attribute,
                    modifier = Modifier.padding(bottom = with(LocalDensity.current) { lineMetrics.rowExtraPaddingPx.toDp() }),
                )
            }
        }
    }
}

internal data class LineMetrics(
    val lineHeightSp: Float,
    val rowExtraPaddingPx: Int,
    /**
     * Extra space the body text's first line gains above its glyphs from
     * `lineHeight` + `LineHeightStyle(Proportional, Trim.None)`; the gutter
     * verse number is padded down by this much to stay level with it.
     */
    val gutterTopPaddingPx: Int,
)

@Composable
private fun rememberLineMetrics(state: VerseItemComposeState): LineMetrics {
    val density = LocalDensity.current
    return remember(state.typeface, state.fontSizeDp, state.fontBold, state.lineSpacingMult, density.density) {
        computeLineMetrics(state.typeface, state.fontSizeDp, state.fontBold, state.lineSpacingMult, density.density)
    }
}

/**
 * Derives Compose line metrics from Android font metrics so text laid out with
 * `lineHeight` + `LineHeightStyle(Proportional, Trim.None)` matches a TextView
 * using `setLineSpacing(0, lineSpacingMult)`. The caller composes under a
 * density with `fontScale == 1f`, so px ↔ sp conversion is `/densityFactor`.
 */
internal fun computeLineMetrics(
    typeface: android.graphics.Typeface?,
    fontSizeDp: Float,
    fontBold: Int,
    lineSpacingMult: Float,
    densityFactor: Float,
): LineMetrics {
    val paint = android.text.TextPaint().apply {
        isAntiAlias = true
        textSize = fontSizeDp * densityFactor
        this.typeface = if (fontBold == android.graphics.Typeface.BOLD) {
            android.graphics.Typeface.create(typeface ?: android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        } else {
            typeface ?: android.graphics.Typeface.DEFAULT
        }
    }
    val fm = paint.fontMetrics
    val naturalLineHeightPx = fm.descent - fm.ascent + fm.leading
    val targetLineHeightPx = naturalLineHeightPx * lineSpacingMult
    val rowExtraPaddingPx = (naturalLineHeightPx * (lineSpacingMult - 1f) + 0.5f).toInt()
    val glyphHeightPx = fm.descent - fm.ascent
    val gutterTopPaddingPx = if (glyphHeightPx <= 0f) 0 else {
        ((targetLineHeightPx - glyphHeightPx) * (-fm.ascent) / glyphHeightPx + 0.5f).toInt().coerceAtLeast(0)
    }
    return LineMetrics(
        lineHeightSp = targetLineHeightPx / densityFactor,
        rowExtraPaddingPx = rowExtraPaddingPx,
        gutterTopPaddingPx = gutterTopPaddingPx,
    )
}

/**
 * The verse text region. Two children share the same coordinate space: the
 * wrapped verse text fills the available width, and (when the renderer puts
 * the verse number in the gutter) a small overlay places the number at
 * top-start, sitting inside the leading margin reserved by the paragraph's
 * TextIndent.
 */
@Composable
private fun VerseTextRegion(state: VerseItemComposeState, checked: Boolean, lineMetrics: LineMetrics, modifier: Modifier = Modifier) {
    val textColor = if (checked) {
        Color(yuku.alkitab.base.util.TextColorUtil.getForCheckedVerse(
            Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default)
        ))
    } else {
        Color(state.fontColor)
    }

    val textStyle = remember(
        state.fontSizeDp,
        textColor,
        lineMetrics,
        state.typeface,
        state.fontBold,
    ) {
        val fontFamily = composeFontFamilyFor(state.typeface)
        TextStyle(
            color = textColor,
            fontSize = state.fontSizeDp.sp,
            lineHeight = lineMetrics.lineHeightSp.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Proportional,
                trim = LineHeightStyle.Trim.None,
            ),
            fontWeight = if (state.fontBold == android.graphics.Typeface.BOLD) FontWeight.Bold else FontWeight.Normal,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            fontFamily = fontFamily,
        )
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(modifier = modifier) {
        BasicText(
            text = state.render.text,
            style = textStyle,
            modifier = Modifier
                .fillMaxWidth()
                .inlineLinkTapDetector(
                    layoutResultProvider = { textLayoutResult },
                    inlineLinks = state.render.inlineLinks,
                    onClick = state.onInlineLinkClick,
                ),
            onTextLayout = { textLayoutResult = it },
        )

        val gutter = state.render.gutterVerseNumber
        if (gutter != null) {
            BasicText(
                text = AnnotatedString(gutter),
                style = textStyle.copy(
                    color = if (checked) textColor else Color(state.verseNumberColor),
                    fontSize = state.verseNumberFontSizeDp.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = TextUnit.Unspecified,
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = with(LocalDensity.current) { lineMetrics.gutterTopPaddingPx.toDp() }),
            )
        }
    }
}

private const val ATTRIBUTE_COUNT_TEXT_SIZE_DP = 12f

private data class AttributeItem(
    val bitmap: android.graphics.Bitmap,
    val count: Int,
    val countWithShadow: Boolean,
    val countYRatio: Float,
    val hasDrawOffset: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun AttributeColumn(attribute: AttributeState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pxDensity = density.density

    val items = remember(
        attribute.bookmarkCount,
        attribute.noteCount,
        attribute.progressMarkBits,
        attribute.hasMaps,
        attribute.scale,
        attribute.version,
        attribute.versionId,
        attribute.ari,
        attribute.attributeListener,
        context.resources,
    ) {
        buildAttributeItems(attribute, context)
    }
    if (items.isEmpty()) return

    val widthPx = items.maxOf { it.bitmap.width }
    val heightPx = items.sumOf { it.bitmap.height }
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }

    val countTextSizePx = ATTRIBUTE_COUNT_TEXT_SIZE_DP * pxDensity * attribute.scale
    val drawOffsetLeftPx = Math.round(0.5f * pxDensity * attribute.scale)

    val countPaint = remember(countTextSizePx) {
        android.graphics.Paint().apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            color = 0xff000000.toInt()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = countTextSizePx
        }
    }
    val countPaintWithShadow = remember(countTextSizePx, pxDensity) {
        android.graphics.Paint(countPaint).apply {
            setShadowLayer(pxDensity * 4f, 0f, 0f, 0xffffffff.toInt())
        }
    }

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .size(widthDp, heightDp)
            .pointerInput(items) {
                detectTapGestures { offset ->
                    var y = 0
                    for (item in items) {
                        val itemBottom = y + item.bitmap.height
                        if (offset.y < itemBottom) {
                            item.onClick()
                            return@detectTapGestures
                        }
                        y = itemBottom
                    }
                }
            },
    ) {
        var y = 0
        for (item in items) {
            val xOffset = if (item.hasDrawOffset) drawOffsetLeftPx else 0
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.drawBitmap(item.bitmap, xOffset.toFloat(), y.toFloat(), null)
                if (item.count > 1) {
                    val paint = if (item.countWithShadow) countPaintWithShadow else countPaint
                    native.drawText(
                        item.count.toString(),
                        xOffset + item.bitmap.width / 2f,
                        y + item.bitmap.height * item.countYRatio,
                        paint,
                    )
                }
            }
            y += item.bitmap.height
        }
    }
}

private fun buildAttributeItems(attribute: AttributeState, context: android.content.Context): List<AttributeItem> {
    val list = mutableListOf<AttributeItem>()

    if (attribute.bookmarkCount > 0) {
        list += AttributeItem(
            bitmap = scaledAttributeBitmap(context, R.drawable.ic_attr_bookmark, attribute.scale),
            count = attribute.bookmarkCount,
            countWithShadow = false,
            countYRatio = 3f / 4f,
            hasDrawOffset = true,
            onClick = {
                val v = attribute.version ?: return@AttributeItem
                attribute.attributeListener.onBookmarkAttributeClick(v, attribute.versionId ?: "", attribute.ari)
            },
        )
    }
    if (attribute.noteCount > 0) {
        list += AttributeItem(
            bitmap = scaledAttributeBitmap(context, R.drawable.ic_attr_note, attribute.scale),
            count = attribute.noteCount,
            countWithShadow = true,
            countYRatio = 7f / 10f,
            hasDrawOffset = true,
            onClick = {
                val v = attribute.version ?: return@AttributeItem
                attribute.attributeListener.onNoteAttributeClick(v, attribute.versionId ?: "", attribute.ari)
            },
        )
    }
    if (attribute.progressMarkBits != 0) {
        for (presetId in 0 until AttributeView.PROGRESS_MARK_TOTAL_COUNT) {
            if (attribute.progressMarkBits and (1 shl (AttributeView.PROGRESS_MARK_BITS_START + presetId)) != 0) {
                list += AttributeItem(
                    bitmap = scaledAttributeBitmap(
                        context,
                        AttributeView.getProgressMarkIconResource(presetId),
                        attribute.scale,
                    ),
                    count = 0,
                    countWithShadow = false,
                    countYRatio = 0f,
                    hasDrawOffset = false,
                    onClick = {
                        val v = attribute.version ?: return@AttributeItem
                        attribute.attributeListener.onProgressMarkAttributeClick(v, attribute.versionId ?: "", presetId)
                    },
                )
            }
        }
    }
    if (attribute.hasMaps) {
        list += AttributeItem(
            bitmap = scaledAttributeBitmap(context, R.drawable.ic_attr_has_maps, attribute.scale),
            count = 0,
            countWithShadow = false,
            countYRatio = 0f,
            hasDrawOffset = true,
            onClick = {
                val v = attribute.version ?: return@AttributeItem
                attribute.attributeListener.onHasMapsAttributeClick(v, attribute.versionId ?: "", attribute.ari)
            },
        )
    }

    return list
}

private fun scaledAttributeBitmap(
    context: android.content.Context,
    @androidx.annotation.DrawableRes resId: Int,
    scale: Float,
): android.graphics.Bitmap {
    val original = android.graphics.BitmapFactory.decodeResource(context.resources, resId)
    if (scale == 1f) return android.graphics.Bitmap.createBitmap(original)
    val filter = !(scale == 2f || scale == 3f || scale == 4f)
    return android.graphics.Bitmap.createScaledBitmap(
        original,
        Math.round(original.width * scale),
        Math.round(original.height * scale),
        filter,
    )
}

// ---- Overlays / decorations ----

private fun Modifier.checkedOverlay(checked: Boolean): Modifier = if (!checked) this else this.drawBehind {
    val colorRgb = Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default)
    val color = ColorUtils.setAlphaComponent(colorRgb, 0xa0)
    drawRect(color = Color(color))
}

@Composable
private fun Modifier.audioHighlightOverlay(audioHighlightColor: Int): Modifier {
    // Snap to 60% the moment a verse is highlighted, then decay to 20% over
    // 0.3 s so verse changes (driven by playback or scrubbing) read as a clear
    // pulse rather than a constant glow. The color's alpha channel is ignored
    // — the animation drives opacity end-to-end. Clearing the color removes the
    // overlay immediately (matches the controller's prev-row clear pattern).
    if (audioHighlightColor == 0) return this
    val alpha = remember { Animatable(AUDIO_HIGHLIGHT_FLASH_ALPHA) }
    LaunchedEffect(audioHighlightColor) {
        alpha.snapTo(AUDIO_HIGHLIGHT_FLASH_ALPHA)
        alpha.animateTo(
            AUDIO_HIGHLIGHT_STEADY_ALPHA,
            tween(durationMillis = AUDIO_HIGHLIGHT_FLASH_MS, easing = LinearEasing),
        )
    }
    return drawBehind {
        val a = alpha.value
        if (a <= 0f) return@drawBehind
        val tinted = ColorUtils.setAlphaComponent(audioHighlightColor, (a * 255f).toInt().coerceIn(0, 255))
        drawRect(color = Color(tinted))
    }
}

@Composable
private fun Modifier.attentionOverlay(attentionStart: Long, onDone: () -> Unit): Modifier {
    if (attentionStart == 0L) return this
    // `attentionStart` is wall-clock millis. `withFrameMillis` provides
    // monotonic uptime, which can't be subtracted from wall-clock — so we
    // use it purely for frame pacing and read `System.currentTimeMillis()`
    // inside the lambda for the actual elapsed-time measurement.
    val now = remember(attentionStart) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(attentionStart) {
        while (true) {
            withFrameMillis { now.longValue = System.currentTimeMillis() }
            if (now.longValue - attentionStart >= ATTENTION_DURATION_MS) {
                onDone()
                break
            }
        }
    }
    return drawBehind {
        val elapsed = now.longValue - attentionStart
        if (elapsed >= ATTENTION_DURATION_MS) return@drawBehind
        val colorRgb = Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default)
        val alpha = (0.4f * 255f * (1f - elapsed.toFloat() / ATTENTION_DURATION_MS)).toInt().coerceIn(0, 255)
        val tinted = ColorUtils.setAlphaComponent(colorRgb, alpha)
        drawRect(color = Color(tinted))
    }
}

@Composable
private fun Modifier.dragHoverOverlay(dragHover: Boolean): Modifier {
    if (!dragHover) return this
    val context = LocalContext.current
    val drawable = remember(context) {
        ResourcesCompat.getDrawable(context.resources, R.drawable.item_verse_bg_draghovered, context.theme)
    } ?: return this
    return drawBehind {
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }
}

/**
 * Tap detector with a 24dp "easy-hit" radius: each tap walks every inline-link
 * range, computes the link's bounding rect via the [TextLayoutResult], and
 * picks the nearest link whose squared distance from the tap is within (24dp)².
 * Lets users hit small footnote/xref markers without pixel-precise aim.
 */
private fun Modifier.inlineLinkTapDetector(
    layoutResultProvider: () -> TextLayoutResult?,
    inlineLinks: List<VerseRendererCompose.InlineLinkRange>,
    onClick: (VerseInlineLinkSpan.Type, Int) -> Unit,
): Modifier {
    if (inlineLinks.isEmpty()) return this
    return this.pointerInput(inlineLinks) {
        val maxDistanceSquaredPx = (24.dp.toPx()).let { (it * it).toInt() }
        detectTapGestures(onTap = { offset ->
            val layout = layoutResultProvider() ?: return@detectTapGestures
            var best: VerseRendererCompose.InlineLinkRange? = null
            var bestDistSq = Int.MAX_VALUE
            for (link in inlineLinks) {
                val rects = boundingRectsFor(layout, link.start, link.end)
                for (rect in rects) {
                    if (rect.contains(offset)) {
                        best = link
                        bestDistSq = 0
                        break
                    }
                    val d = squaredDistanceFromRect(offset, rect)
                    if (d <= maxDistanceSquaredPx && d < bestDistSq) {
                        best = link
                        bestDistSq = d
                    }
                }
                if (bestDistSq == 0) break
            }
            best?.let { onClick(it.type, it.arif) }
        })
    }
}

/**
 * Produces 1–3 bounding rectangles for a substring: single line → one rect;
 * multi-line → tail of the first line + head of the last line + the middle
 * block (when the line gap is > 1).
 */
private fun boundingRectsFor(layout: TextLayoutResult, start: Int, end: Int): List<Rect> {
    val lineStart = layout.getLineForOffset(start)
    val lineEnd = layout.getLineForOffset(end)
    val xStart = layout.getHorizontalPosition(start, usePrimaryDirection = true)
    val xEnd = layout.getHorizontalPosition(end, usePrimaryDirection = true)
    return if (lineStart == lineEnd) {
        listOf(Rect(xStart, layout.getLineTop(lineStart), xEnd, layout.getLineBottom(lineStart)))
    } else {
        val width = layout.size.width.toFloat()
        val out = mutableListOf<Rect>()
        out += Rect(xStart, layout.getLineTop(lineStart), width, layout.getLineBottom(lineStart))
        out += Rect(0f, layout.getLineTop(lineEnd), xEnd, layout.getLineBottom(lineEnd))
        if (lineEnd - lineStart > 1) {
            out += Rect(0f, layout.getLineBottom(lineStart), width, layout.getLineTop(lineEnd))
        }
        out
    }
}

private fun squaredDistanceFromRect(offset: Offset, rect: Rect): Int {
    val dx = when {
        offset.x < rect.left -> rect.left - offset.x
        offset.x > rect.right -> offset.x - rect.right
        else -> 0f
    }
    val dy = when {
        offset.y < rect.top -> rect.top - offset.y
        offset.y > rect.bottom -> offset.y - rect.bottom
        else -> 0f
    }
    return (dx * dx + dy * dy).toInt()
}
