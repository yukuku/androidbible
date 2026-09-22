package yuku.alkitab.base.verses

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import yuku.alkitab.base.App
import yuku.alkitab.base.settings.ExperimentalFlags
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.base.util.SearchEngine
import yuku.alkitab.base.widget.VerseRendererCompose

/**
 * One slot's text plus the verse appearance it is drawn with, snapshotted at
 * bind time so it mirrors what
 * [yuku.alkitab.base.util.Appearances.applyTextAppearance] puts on a TextView.
 */
data class VerseTextComposeState(
    val text: AnnotatedString,
    val fontSizeDp: Float,
    val fontColor: Int,
    val lineSpacingMult: Float,
    val typeface: android.graphics.Typeface?,
    val fontBold: Int,
    val maxLines: Int,
)

/**
 * Verse text for the screens outside the reader: search results, the marker
 * list, the progress-mark list, the report screen. Those show a verse without
 * the reader's selection, attribute and drag machinery, so they reuse the
 * Compose renderer and the reader's font metrics rather than a whole
 * [VerseItemComposeView].
 *
 * Callers hand it an [AnnotatedString] built by [renderVerseText] (or one they
 * build themselves) plus the version's font size multiplier.
 */
class VerseTextComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AbstractComposeView(context, attrs) {

    /** The appearance snapshot [bind] took, or null until the first bind. */
    internal var state by mutableStateOf<VerseTextComposeState?>(null)
        private set

    /**
     * The line cap the replaced TextView carried, so a row that elides its
     * snippet keeps doing so.
     */
    var maxLines: Int = Int.MAX_VALUE

    /**
     * [colorOverride] of 0 keeps the reader's configured verse color; the list
     * screens pass the checked-row text color when a row is selected.
     */
    fun bind(text: AnnotatedString, textSizeMult: Float, colorOverride: Int = 0) {
        val applied = App.services.uiDimensions.applied()
        state = VerseTextComposeState(
            text = text,
            fontSizeDp = applied.fontSize2dp * textSizeMult,
            fontColor = if (colorOverride != 0) colorOverride else applied.fontColor,
            lineSpacingMult = applied.lineSpacingMult,
            typeface = applied.fontFace,
            fontBold = applied.fontBold,
            maxLines = maxLines,
        )
    }

    @Composable
    override fun Content() {
        val s = state ?: return
        VerseTextComposeContent(s)
    }

    @SuppressLint("GetContentDescriptionOverride")
    override fun getContentDescription(): CharSequence = state?.text?.text ?: ""
}

@Composable
internal fun VerseTextComposeContent(state: VerseTextComposeState, modifier: Modifier = Modifier) {
    // Strip fontScale from the density so sp values render at dp dimensions,
    // the same way the reader's verse rows size their text.
    val baseDensity = LocalDensity.current
    val unscaledDensity = remember(baseDensity.density) {
        Density(density = baseDensity.density, fontScale = 1f)
    }

    CompositionLocalProvider(LocalDensity provides unscaledDensity) {
        val scaledDensity = unscaledDensity.density
        val lineMetrics = remember(state.typeface, state.fontSizeDp, state.fontBold, state.lineSpacingMult, scaledDensity) {
            computeLineMetrics(state.typeface, state.fontSizeDp, state.fontBold, state.lineSpacingMult, scaledDensity, rubyFontSizeDp = 0f)
        }

        val bold = state.fontBold == android.graphics.Typeface.BOLD
        val textStyle = remember(state.fontSizeDp, state.fontColor, lineMetrics, state.typeface, bold) {
            TextStyle(
                color = Color(state.fontColor),
                fontSize = state.fontSizeDp.sp,
                lineHeight = lineMetrics.lineHeightSp.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Proportional,
                    trim = LineHeightStyle.Trim.None,
                ),
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                fontFamily = composeFontFamilyFor(state.typeface, bold),
            )
        }

        BasicText(
            text = state.text,
            style = textStyle,
            maxLines = state.maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

/**
 * Puts a [VerseTextComposeView] where [textView] sits, carrying over its id,
 * child index and layout params so the row's layout rules and the caller's
 * `findViewById` keep working. Returns null when the Verse (legacy views)
 * experimental setting is on, leaving the TextView in place.
 */
private fun replaceWithVerseTextCompose(textView: TextView): VerseTextComposeView? {
    if (!ExperimentalFlags.useComposeVerseItem()) return null

    val parent = textView.parent as ViewGroup
    val childIndex = parent.indexOfChild(textView)
    val layoutParams = textView.layoutParams
    parent.removeViewAt(childIndex)

    val composeView = VerseTextComposeView(textView.context)
    composeView.id = textView.id
    composeView.maxLines = textView.maxLines
    parent.addView(composeView, childIndex, layoutParams)
    return composeView
}

/**
 * The verse-text slot of one row, which is a [VerseTextComposeView] unless the
 * Verse (legacy views) experimental setting is on, and the row's own TextView
 * otherwise. Rows go through a slot so filling it costs them one call either
 * way.
 */
class VerseTextSlot private constructor(private val textView: TextView?, private val composeView: VerseTextComposeView?) {
    companion object {
        /** The slot at [id] on [activity]'s content view. */
        fun of(activity: Activity, id: Int): VerseTextSlot = of(activity.findViewById<View>(android.R.id.content), id)

        /**
         * The slot at [id] inside [root], swapping the row's TextView for its
         * Compose equivalent the first time it is asked for. Idempotent, so a
         * recycled row can ask again on every bind.
         */
        fun of(root: View, id: Int): VerseTextSlot = when (val slotView = root.findViewById<View>(id)) {
            is VerseTextComposeView -> VerseTextSlot(null, slotView)
            is TextView -> {
                val composeView = replaceWithVerseTextCompose(slotView)
                VerseTextSlot(if (composeView == null) slotView else null, composeView)
            }
            else -> throw IllegalArgumentException("view $id is neither a TextView nor a VerseTextComposeView")
        }
    }

    /**
     * Fills the slot. [legacy] styles and sets text on the row's TextView the
     * way that row does with the setting off; [compose] supplies the rendered
     * verse for the Compose slot. Only the branch in use runs, so neither
     * renderer costs anything on the other path.
     */
    fun setText(
        textSizeMult: Float,
        colorOverride: Int = 0,
        legacy: (TextView) -> Unit,
        compose: () -> AnnotatedString,
    ) {
        val composeView = composeView
        if (composeView != null) {
            composeView.bind(compose(), textSizeMult, colorOverride)
        } else {
            legacy(checkNotNull(textView))
        }
    }
}

/**
 * Renders one verse through the Compose renderer, with no verse number, for
 * the screens that show verse text outside the reader. [highlightInfo] paints
 * the marker's highlight band the same way the reader does.
 */
fun renderVerseText(ari: Int, rawVerseText: String, highlightInfo: Highlights.Info? = null): AnnotatedString =
    VerseRendererCompose.render(
        isVerseNumberShown = false,
        ari = ari,
        text = rawVerseText,
        verseNumberText = "",
        highlightInfo = highlightInfo,
    ).text

/**
 * Overlays the search hilite on an already-rendered verse. The match runs over
 * the plain text, so [SearchEngine.hilite]'s offsets line up with this
 * string's own, and only the bold and color spans it sets are carried over.
 */
fun AnnotatedString.withSearchHilite(rt: SearchEngine.ReadyTokens?, hiliteColor: Int): AnnotatedString {
    if (rt == null) return this

    val hilited: Spanned = SearchEngine.hilite(text, rt, hiliteColor)
    return AnnotatedString.Builder(this).apply {
        for (span in hilited.getSpans(0, hilited.length, Any::class.java)) {
            val start = hilited.getSpanStart(span)
            val end = hilited.getSpanEnd(span)
            when {
                span is ForegroundColorSpan -> addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
                span is StyleSpan && span.style == android.graphics.Typeface.BOLD ->
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            }
        }
    }.toAnnotatedString()
}

/** Prepends the underlined reference that the marker and pin lists show before the verse. */
fun AnnotatedString.withReferencePrefix(reference: CharSequence): AnnotatedString = buildAnnotatedString {
    pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
    append(reference.toString())
    pop()
    append(" ")
    append(this@withReferencePrefix)
}
