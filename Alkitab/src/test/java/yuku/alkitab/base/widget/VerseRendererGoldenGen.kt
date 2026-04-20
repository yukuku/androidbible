package yuku.alkitab.base.widget

import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import androidx.test.core.app.ApplicationProvider
import com.google.gson.GsonBuilder
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.S
import yuku.alkitab.base.util.Highlights
import java.io.File

/**
 * Golden-master generator for the iOS port of [VerseRenderer].
 *
 * This mirrors the shape of [VerseRendererTest] (Robolectric runner, deterministic
 * [S.CalculatedDimensions] override via reflection) but, instead of asserting, it
 * serialises the rendered `SpannableStringBuilder` + its spans to JSON and writes
 * the file the iOS `AlkitabRendererTests` golden suite compares against:
 *
 *   iosbible/Tests/Fixtures/golden-verse-renderer.json
 *
 * The destination is taken from the `GOLDEN_VERSE_RENDERER_OUT` environment
 * variable. When the variable is absent, the test is a no-op (so running the
 * full Android test suite never accidentally overwrites the iOS golden).
 * [tools/GoldenGen/generate-verse-renderer.sh][1] in the iOS repo sets the env
 * var and invokes `./gradlew :Alkitab:testPlainDebugUnitTest --tests
 * yuku.alkitab.base.widget.VerseRendererGoldenGen`.
 *
 * [1]: https://github.com/…/iosbible/blob/master/tools/GoldenGen/generate-verse-renderer.sh
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class VerseRendererGoldenGen {

    // These values MUST stay in sync with the `dimensions` block in the iOS test
    // fixture. The Swift port's AppliedDimensions reads the same numbers from the
    // emitted golden JSON, so drift on either side will surface as a test failure.
    private val FONT_RED = 0xff112233.toInt()
    private val VERSE_NUMBER_COLOR = 0xff445566.toInt()
    private val INDENT_FIRST = 10
    private val INDENT_REST = 20
    private val INDENT_1 = 30
    private val INDENT_2 = 40
    private val INDENT_3 = 50
    private val INDENT_4 = 60
    private val INDENT_EXTRA = 5

    private val ARI = 0x010203

    @Before
    fun setUp() {
        yuku.afw.App.initWithAppContext(ApplicationProvider.getApplicationContext())

        val dims = S.CalculatedDimensions().apply {
            fontRedColor = FONT_RED
            verseNumberColor = VERSE_NUMBER_COLOR
            indentParagraphFirst = INDENT_FIRST
            indentParagraphRest = INDENT_REST
            indentSpacing1 = INDENT_1
            indentSpacing2 = INDENT_2
            indentSpacing3 = INDENT_3
            indentSpacing4 = INDENT_4
            indentSpacingExtra = INDENT_EXTRA
        }

        // Trigger holder init, then overwrite `applied` — same trick as
        // VerseRendererTest. S.applied() is @JvmStatic so mockkObject doesn't
        // cleanly intercept it; reflection is the most reliable override.
        S.applied()
        val holderClass = Class.forName("${S::class.java.name}\$CalculatedDimensionsHolder")
        val instance = holderClass.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
        holderClass.getDeclaredField("applied").apply { isAccessible = true }.set(instance, dims)
    }

    @Test
    fun emitGolden() {
        val outPath = System.getenv("GOLDEN_VERSE_RENDERER_OUT") ?: return

        val factory = VerseInlineLinkSpan.Factory { type, arif ->
            object : VerseInlineLinkSpan(type, arif) {}
        }

        val cases = buildList {
            // region simple render (text does NOT start with "@@")
            add(caseOf("simple_shown", "Hello", verseNumberText = "1"))
            add(caseOf("simple_hidden", "Hello", verseNumberText = "1", isVerseNumberShown = false))
            add(caseOf("simple_single_at", "@", verseNumberText = "1"))
            add(caseOf("simple_at_then_text", "@Hello", verseNumberText = "1"))
            add(caseOf("simple_wide_verse_number", "Hello", verseNumberText = "42"))
            // endregion

            // region formatted verse header "@@"
            add(caseOf("formatted_no_markup", "@@Hello", verseNumberText = "1"))
            add(caseOf("formatted_paragraph_caret_suppresses_number", "@@@^Hello", verseNumberText = "1"))
            add(caseOf("formatted_paragraph_1_suppresses_number", "@@@1Body", verseNumberText = "1"))
            add(caseOf("formatted_paragraph_2_suppresses_number", "@@@2Body", verseNumberText = "1"))
            add(caseOf("formatted_paragraph_3_suppresses_number", "@@@3Body", verseNumberText = "1"))
            add(caseOf("formatted_paragraph_4_suppresses_number", "@@@4Body", verseNumberText = "1"))
            add(caseOf("formatted_paragraph_0_keeps_number", "@@@0Hello", verseNumberText = "1"))

            // Paragraph-1 with a long verse number activates indentSpacingExtra.
            add(caseOf("formatted_paragraph_1_extra_indent", "@@@1Body", verseNumberText = "12345"))

            add(caseOf("formatted_multi_paragraph", "@@First@1Second@2Third", verseNumberText = "1"))
            // endregion

            // region italic / red / newline / highlight body
            add(caseOf("italic_balanced", "@@ab@9cd@7ef", verseNumberText = "1"))
            add(caseOf("italic_dangling_close_is_noop", "@@ab@7cd", verseNumberText = "1"))
            add(caseOf("italic_two_runs", "@@a@9b@7c@9d@7e", verseNumberText = "1"))

            add(caseOf("red_balanced", "@@Jesus said @6Verily@5 unto them", verseNumberText = "1"))
            add(caseOf("red_suppressed_when_checked", "@@a @6Verily@5 b", verseNumberText = "1", checked = true))
            add(caseOf("red_dangling_close_is_noop", "@@a@5b", verseNumberText = "1"))

            add(caseOf("linebreak_at8", "@@line1@8line2", verseNumberText = "1"))
            // endregion

            // region special tags (footnote, xref)
            add(caseOf("footnote_single_digit", "@@body@<f1@>@/", verseNumberText = "1", factory = factory))
            add(caseOf("footnote_two_digits", "@@body@<f12@>@/", verseNumberText = "1", factory = factory))
            add(caseOf("xref_single_digit", "@@body@<x1@>@/", verseNumberText = "1", factory = factory))
            add(caseOf("footnote_multi", "@@a@<f1@>@/b@<f2@>@/c", verseNumberText = "1", factory = factory))
            add(caseOf("special_tag_unknown_dropped", "@@a@<unknown@>@/b", verseNumberText = "1", factory = factory))
            // endregion

            // region highlights
            add(caseOf(
                "highlight_full_simple",
                "Hello", verseNumberText = "1",
                highlight = Highlights.Info().apply {
                    colorRgb = 0x00ff00
                    partial = null
                }
            ))
            add(caseOf(
                "highlight_partial_hash_matches",
                "Hello world", verseNumberText = "1",
                highlight = Highlights.Info().apply {
                    colorRgb = 0xff0000
                    partial = Highlights.Info.Partial().apply {
                        hashCode = Highlights.hashCode("Hello world")
                        startOffset = 0
                        endOffset = 5
                    }
                }
            ))
            add(caseOf(
                "highlight_partial_hash_mismatch_falls_back_to_full",
                "Hello", verseNumberText = "1",
                highlight = Highlights.Info().apply {
                    colorRgb = 0x0000ff
                    partial = Highlights.Info.Partial().apply {
                        hashCode = 12345
                        startOffset = 0
                        endOffset = 3
                    }
                }
            ))
            // endregion
        }

        val fixture = Fixture(
            dimensions = Dimensions(
                fontRedColor = FONT_RED,
                verseNumberColor = VERSE_NUMBER_COLOR,
                indentParagraphFirst = INDENT_FIRST,
                indentParagraphRest = INDENT_REST,
                indentSpacing1 = INDENT_1,
                indentSpacing2 = INDENT_2,
                indentSpacing3 = INDENT_3,
                indentSpacing4 = INDENT_4,
                indentSpacingExtra = INDENT_EXTRA,
            ),
            ari = ARI,
            cases = cases,
        )

        val json = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(fixture)
        File(outPath).apply {
            parentFile?.mkdirs()
            writeText(json + "\n")
        }
        println("wrote golden verse renderer fixture: $outPath (${cases.size} cases)")
    }

    private fun caseOf(
        name: String,
        text: String,
        verseNumberText: String,
        isVerseNumberShown: Boolean = true,
        checked: Boolean = false,
        highlight: Highlights.Info? = null,
        factory: VerseInlineLinkSpan.Factory? = null,
    ): Case {
        val ftr = VerseRenderer.FormattedTextResult()
        val offset = VerseRenderer.render(
            /* lText = */ null,
            /* lVerseNumber = */ null,
            isVerseNumberShown,
            ARI,
            text,
            verseNumberText,
            highlight,
            checked,
            factory,
            ftr,
        )
        // On the formatted path ftr.result is a Spanned; on the simpleRender path
        // it's the raw input String. For the simpleRender path we still want the
        // SpannableStringBuilder contents, so re-run via a capturing TextView.
        val spanned: Spanned = if (ftr.result is Spanned) {
            ftr.result as Spanned
        } else {
            val lText = android.widget.TextView(ApplicationProvider.getApplicationContext())
            VerseRenderer.render(
                lText, null, isVerseNumberShown, ARI, text, verseNumberText, highlight, checked, factory, null,
            )
            lText.text as Spanned
        }

        return Case(
            name = name,
            input = CaseInput(
                ari = ARI,
                text = text,
                verseNumberText = verseNumberText,
                isVerseNumberShown = isVerseNumberShown,
                checked = checked,
                highlight = highlight?.let {
                    HighlightJson(
                        colorRgb = it.colorRgb,
                        partial = it.partial?.let { p ->
                            PartialJson(hashCode = p.hashCode, startOffset = p.startOffset, endOffset = p.endOffset)
                        },
                    )
                },
            ),
            output = CaseOutput(
                text = spanned.toString(),
                startPosAfterVerseNumber = offset,
                spans = serializeSpans(spanned),
            ),
        )
    }

    /**
     * Translates the span instances attached to the rendered text into our
     * portable JSON shape. Sort order — `(start, end, type, extra)` — is the
     * canonical order the Swift test applies before comparing, so drift in
     * Android's attach order (which is ordered by insertion, not position)
     * doesn't cause false negatives.
     */
    private fun serializeSpans(spanned: Spanned): List<SpanJson> {
        val out = mutableListOf<SpanJson>()
        for (span in spanned.getSpans(0, spanned.length, Any::class.java)) {
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            when (span) {
                is VerseRenderer.VerseNumberSpan -> {
                    val applyColorField = VerseRenderer.VerseNumberSpan::class.java.getDeclaredField("applyColor")
                    applyColorField.isAccessible = true
                    out += SpanJson.verseNumber(start, end, applyColorField.getBoolean(span))
                }
                is StyleSpan -> if (span.style == android.graphics.Typeface.ITALIC) {
                    out += SpanJson.italic(start, end)
                }
                is ForegroundColorSpan -> out += SpanJson.red(start, end, span.foregroundColor)
                is LeadingMarginSpan.Standard -> out += SpanJson.leadingMargin(
                    start, end,
                    span.getLeadingMargin(true),
                    span.getLeadingMargin(false),
                )
                is BackgroundColorSpan -> out += SpanJson.highlight(start, end, span.backgroundColor)
                is VerseInlineLinkSpan -> {
                    val typeField = VerseInlineLinkSpan::class.java.getDeclaredField("type")
                    typeField.isAccessible = true
                    val arifField = VerseInlineLinkSpan::class.java.getDeclaredField("arif")
                    arifField.isAccessible = true
                    val t = typeField.get(span) as VerseInlineLinkSpan.Type
                    out += SpanJson.inlineLink(start, end, t.name, arifField.getInt(span))
                }
            }
        }
        // Canonical order: the Swift golden test sorts identically before comparing.
        return out.sortedWith(compareBy({ it.start }, { it.end }, { it.type }, { it.tieBreak() }))
    }

    // region JSON shape (must stay in lockstep with the Swift decoder)

    private data class Fixture(
        val dimensions: Dimensions,
        val ari: Int,
        val cases: List<Case>,
    )

    private data class Dimensions(
        val fontRedColor: Int,
        val verseNumberColor: Int,
        val indentParagraphFirst: Int,
        val indentParagraphRest: Int,
        val indentSpacing1: Int,
        val indentSpacing2: Int,
        val indentSpacing3: Int,
        val indentSpacing4: Int,
        val indentSpacingExtra: Int,
    )

    private data class Case(val name: String, val input: CaseInput, val output: CaseOutput)
    private data class CaseInput(
        val ari: Int,
        val text: String,
        val verseNumberText: String,
        val isVerseNumberShown: Boolean,
        val checked: Boolean,
        val highlight: HighlightJson?,
    )
    private data class CaseOutput(
        val text: String,
        val startPosAfterVerseNumber: Int,
        val spans: List<SpanJson>,
    )
    private data class HighlightJson(val colorRgb: Int, val partial: PartialJson?)
    private data class PartialJson(val hashCode: Int, val startOffset: Int, val endOffset: Int)

    /**
     * Flat shape — fields are set only for the variants that use them. We
     * avoided a sealed-class hierarchy here because Gson can't handle a
     * subclass whose constructor re-declares a superclass field (it flags
     * a "duplicate field" conflict on `start`/`end`).
     */
    private data class SpanJson(
        val type: String,
        val start: Int,
        val end: Int,
        val applyColor: Boolean? = null,
        val colorArgb: Int? = null,
        val first: Int? = null,
        val rest: Int? = null,
        val kind: String? = null,
        val arif: Int? = null,
    ) {
        fun tieBreak(): String = listOfNotNull(
            applyColor?.let { "a=$it" },
            colorArgb?.let { "c=$it" },
            first?.let { "f=$it" },
            rest?.let { "r=$it" },
            kind?.let { "k=$it" },
            arif?.let { "A=$it" },
        ).joinToString(",")

        companion object {
            fun verseNumber(start: Int, end: Int, applyColor: Boolean) =
                SpanJson("verseNumber", start, end, applyColor = applyColor)
            fun italic(start: Int, end: Int) = SpanJson("italic", start, end)
            fun red(start: Int, end: Int, colorArgb: Int) =
                SpanJson("red", start, end, colorArgb = colorArgb)
            fun leadingMargin(start: Int, end: Int, first: Int, rest: Int) =
                SpanJson("leadingMargin", start, end, first = first, rest = rest)
            fun highlight(start: Int, end: Int, colorArgb: Int) =
                SpanJson("highlight", start, end, colorArgb = colorArgb)
            fun inlineLink(start: Int, end: Int, kind: String, arif: Int) =
                SpanJson("inlineLink", start, end, kind = kind, arif = arif)
        }
    }

    // endregion
}
