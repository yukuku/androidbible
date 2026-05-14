package yuku.alkitab.base.verses

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.DragEvent
import android.view.accessibility.AccessibilityEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface as ComposeTypeface
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.widget.AttributeView
import yuku.alkitab.base.widget.LeftDrawer.PROGRESS_MARK_DRAG_MIME_TYPE
import yuku.alkitab.base.widget.VerseInlineLinkSpan
import yuku.alkitab.base.widget.VerseRendererCompose
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version

/**
 * Pure data: everything VerseItemComposeView needs to render that does NOT
 * change in-place. Held in a `mutableStateOf` so [VerseItemComposeView.bind]
 * triggers recomposition.
 *
 * Live-mutable view properties — `checked`, `collapsed`, `audioHighlightColor`,
 * `attentionStart`, `dragHover` — live directly on [VerseItemComposeView] so
 * callers (e.g. [VersesControllerImpl.setAudioHighlight]) can poke them the
 * same way they poke the legacy [VerseItem].
 */
data class VerseItemComposeState(
    val render: VerseRendererCompose.Result,
    /** Text size in DIP (matches [yuku.alkitab.base.util.Appearances.applyTextAppearance]). */
    val fontSizeDp: Float,
    /** Verse-number gutter text size in DIP (0.7 × main size, matches Appearances.applyVerseNumberAppearance). */
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
)

private const val ATTENTION_DURATION_MS = 2000f
private const val AUDIO_HIGHLIGHT_FADE_IN_MS = 200
private const val AUDIO_HIGHLIGHT_FADE_OUT_MS = 150

/**
 * Compose port of [VerseItem]. Public surface mirrors the legacy class so the
 * RecyclerView holder can use either interchangeably:
 *
 *   - [bind] swaps in fresh state, triggering a single recomposition.
 *   - The `var` properties below mirror those on the legacy [VerseItem] so
 *     [VersesControllerImpl.setAudioHighlight] / `.callAttention` etc. work
 *     without any controller-side branching.
 *   - [onDragEvent] handles progress-mark drag-drop at the View level (same as
 *     legacy), so we don't have to translate the platform DragEvent into a
 *     Compose drag-target.
 *   - [getContentDescription] reproduces the legacy TalkBack content
 *     description verbatim.
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
    /** Mirrors [VerseItem.audioHighlightColor]: setting clears/triggers the overlay fade. */
    var audioHighlightColor: Int
        get() = audioHighlightColorState.intValue
        set(value) { audioHighlightColorState.intValue = value }

    private val attentionStartState = mutableLongStateOf(0L)
    /** Mirrors [VerseItem.callAttention]: 0 = no attention flash, otherwise wall-clock start. */
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
            val item = event.clipData.getItemAt(0)
            val presetId = Integer.parseInt(item.text.toString())
            onPinDroppedHandler(presetId)
            true
        }
        else -> false
    }

    /**
     * Force TalkBack to read the verse via [getContentDescription] instead of
     * descending into the (now Compose) child hierarchy.
     */
    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
        event.text.add(contentDescription)
        return true
    }

    @SuppressLint("StringFormatMatches", "GetContentDescriptionOverride")
    override fun getContentDescription(): CharSequence {
        val s = state ?: return ""
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
                App.services.storage.db.getProgressMarkByPresetId(presetId)?.let { progressMark ->
                    val caption = if (TextUtils.isEmpty(progressMark.caption)) {
                        context.getString(AttributeView.getDefaultProgressMarkStringResource(presetId))
                    } else {
                        progressMark.caption
                    }
                    res.append(' ').append(context.getString(R.string.desc_verse_attribute_progress_mark, caption))
                }
            }
        }

        return res
    }
}

@Composable
private fun VerseItemComposeContent(
    state: VerseItemComposeState,
    checked: Boolean,
    collapsed: Boolean,
    audioHighlightColor: Int,
    attentionStart: Long,
    dragHover: Boolean,
    onAttentionDone: () -> Unit,
) {
    // Legacy TextView paths use TypedValue.COMPLEX_UNIT_DIP, which ignores the
    // system font scale. Strip fontScale from the density so sp == dp inside
    // this row — required for pixel parity with the legacy view.
    val baseDensity = LocalDensity.current
    val unscaledDensity = remember(baseDensity.density) {
        Density(density = baseDensity.density, fontScale = 1f)
    }

    CompositionLocalProvider(LocalDensity provides unscaledDensity) {
        // Computed once and shared with [VerseTextRegion] below; see the
        // commentary inside [VerseTextRegion] for the parity rationale.
        val lineMetrics = rememberLineMetrics(state)

        val sizeModifier = if (collapsed) {
            // VerseItem.onMeasure forces measured height to 0 when collapsed.
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
                // Legacy [VerseItem.onMeasure] adds `extra` to the outer
                // measured height regardless of whether text or attribute
                // dominates. We can't replicate that as outer padding without
                // overshooting text-dominant rows, so instead we extend the
                // attribute column by `extra`. The Row's height becomes
                // `max(textRegionHeight, attrHeight + extra)`, which matches
                // legacy's `max(textHeight - extra, attrHeight) + extra` in
                // both branches.
                AttributeColumnAndroidView(
                    attribute = state.attribute,
                    modifier = Modifier.padding(bottom = with(LocalDensity.current) { lineMetrics.rowExtraPaddingPx.toDp() }),
                )
            }
        }
    }
}

private data class LineMetrics(
    val lineHeightSp: Float,
    val rowExtraPaddingPx: Int,
)

@Composable
private fun rememberLineMetrics(state: VerseItemComposeState): LineMetrics {
    val density = LocalDensity.current
    return remember(state.typeface, state.fontSizeDp, state.fontBold, state.lineSpacingMult, density.density) {
        val paint = android.text.TextPaint().apply {
            isAntiAlias = true
            textSize = state.fontSizeDp * density.density
            typeface = if (state.fontBold == android.graphics.Typeface.BOLD) {
                android.graphics.Typeface.create(state.typeface ?: android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            } else {
                state.typeface ?: android.graphics.Typeface.DEFAULT
            }
        }
        val fm = paint.fontMetrics
        val naturalLineHeightPx = fm.descent - fm.ascent + fm.leading
        val targetLineHeightPx = naturalLineHeightPx * state.lineSpacingMult
        val rowExtraPaddingPx = (naturalLineHeightPx * (state.lineSpacingMult - 1f) + 0.5f).toInt()
        LineMetrics(
            // density.fontScale == 1f upstream → px ↔ sp == /density.
            lineHeightSp = targetLineHeightPx / density.density,
            rowExtraPaddingPx = rowExtraPaddingPx,
        )
    }
}

/**
 * Mirrors the FrameLayout in `item_verse.xml`: the verse text fills the width,
 * and (when the renderer puts the number in the gutter) a small overlay
 * positions the verse number at top-start, sitting in the leading margin
 * reserved by the paragraph's TextIndent.
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
        // Map Android's built-in Typeface singletons to Compose's built-in
        // FontFamily counterparts. Compose's stock FontFamilies have italic
        // and bold variants pre-registered, so `FontStyle.Italic` /
        // `FontWeight.Bold` resolve to the correct glyphs (or synthesise
        // cleanly when no italic glyph exists). Wrapping a raw Typeface via
        // `FontFamily(Typeface)` registers it as a single regular variant,
        // which means italic on `Typeface.DEFAULT` etc. silently renders
        // upright. Custom (user-loaded) Typefaces still go through the
        // single-variant wrapper — they typically don't have italic glyphs
        // anyway, and Compose will fall back to a synthesised slant.
        val tf = state.typeface
        val fontFamily: FontFamily = when (tf) {
            null, android.graphics.Typeface.DEFAULT -> FontFamily.Default
            android.graphics.Typeface.SERIF -> FontFamily.Serif
            android.graphics.Typeface.MONOSPACE -> FontFamily.Monospace
            android.graphics.Typeface.SANS_SERIF -> FontFamily.SansSerif
            else -> FontFamily(ComposeTypeface(tf))
        }
        TextStyle(
            color = textColor,
            fontSize = state.fontSizeDp.sp,
            lineHeight = lineMetrics.lineHeightSp.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Proportional,
                // Trim.None keeps the full line-height box on every line
                // (matching legacy `setLineSpacing(0, mult)` measurement post
                // the Lollipop bug-fix in VerseItem.onMeasure).
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
                ),
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

/**
 * Reuses the legacy [AttributeView] so the icon column is byte-identical to the
 * non-Compose path. Embedding via AndroidView is intentional: the suspected
 * rendering bug is in the text layer, not the icons, so we deliberately keep
 * surfaces where parity bugs can be introduced as small as possible.
 */
@Composable
private fun AttributeColumnAndroidView(attribute: AttributeState, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AttributeView(ctx).apply {
                isClickable = true
            }
        },
        update = { view ->
            view.setScale(attribute.scale)
            view.bookmarkCount = attribute.bookmarkCount
            view.noteCount = attribute.noteCount
            view.progressMarkBits = attribute.progressMarkBits
            view.hasMaps = attribute.hasMaps
            view.setAttributeListener(
                attribute.attributeListener,
                attribute.version,
                attribute.versionId,
                attribute.ari,
            )
        },
    )
}

// ---- Overlays / decorations (kept in this file because they directly mirror VerseItem.onDraw) ----

private fun Modifier.checkedOverlay(checked: Boolean): Modifier = if (!checked) this else this.drawBehind {
    val colorRgb = Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default)
    val color = ColorUtils.setAlphaComponent(colorRgb, 0xa0)
    drawRect(color = Color(color))
}

@Composable
private fun Modifier.audioHighlightOverlay(audioHighlightColor: Int): Modifier {
    // The legacy onDraw is gated by `audioHighlightColor != 0 && audioHighlightAlpha > 0f`,
    // so the fade-out branch never actually paints anything; we faithfully reproduce
    // that "fade-in only" behavior here.
    if (audioHighlightColor == 0) return this
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = AUDIO_HIGHLIGHT_FADE_IN_MS, easing = LinearEasing),
        label = "audioHighlightFadeIn",
    )
    return drawBehind {
        val baseAlpha = ((audioHighlightColor ushr 24) and 0xff) / 255f
        val a = (baseAlpha * alpha).coerceIn(0f, 1f)
        if (a <= 0f) return@drawBehind
        val tinted = ColorUtils.setAlphaComponent(audioHighlightColor, (a * 255f).toInt().coerceIn(0, 255))
        drawRect(color = Color(tinted))
    }
}

@Composable
private fun Modifier.attentionOverlay(attentionStart: Long, onDone: () -> Unit): Modifier {
    if (attentionStart == 0L) return this
    val now = remember(attentionStart) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(attentionStart) {
        while (true) {
            withFrameMillis { now.longValue = it }
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
 * Re-implements [yuku.alkitab.base.widget.VerseTextView]'s 24dp-radius hit-testing
 * in Compose: each tap walks every inline-link range, computes the link's bounding
 * rect via the [TextLayoutResult], and picks the nearest link whose squared
 * distance falls within (24dp)².
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
 * Produces 1–3 bounding rectangles for a substring, matching the legacy
 * VerseTextView algorithm: single line → one rect; multi-line → tail of the
 * first line + head of the last line + the middle block (when the line gap > 1).
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
