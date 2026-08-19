package yuku.alkitab.base.widget

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.S
import yuku.alkitab.base.util.Highlights

/**
 * Characterization tests for [VerseRenderer].
 *
 * These tests lock down the current rendering behavior of the ~200-line `render()` method so
 * that the TD-10 decomposition (splitting `render()` into `renderVerseNumber`,
 * `applyParagraphStyle`, `processFormattingCodes`, `processSpecialTags`) can be performed
 * without behavioral drift. They intentionally treat the current implementation as the
 * contract — if a test reveals what looks like a bug, flag it rather than silently "fixing"
 * it here (per CLAUDE.md's unit-testing guidance).
 *
 * Why Robolectric? [VerseRenderer] builds real Android [android.text.SpannableStringBuilder]s
 * and attaches real [StyleSpan] / [ForegroundColorSpan] / [LeadingMarginSpan] /
 * [BackgroundColorSpan] spans — plain JUnit cannot instantiate those. We also mock
 * [S.applied] with a deterministic [S.CalculatedDimensions] so indent and color assertions
 * are stable regardless of the device or user preferences.
 *
 * `yuku.afw.App.initWithAppContext` is called so that any code path that reaches into
 * `App.context` (e.g. the `reportInvalidSpecialTag` Toast handler, or S's own
 * initialisation of its [S.CalculatedDimensions] from Preferences and Resources) has a
 * live Context to use.
 *
 * Why reflection instead of `mockkObject(S)`? `S.applied()` is `@JvmStatic`, and mockk's
 * object intercept does not cleanly redirect the Java-level static invocation that
 * [VerseRenderer] (a Java class) uses. The simplest and most reliable override is to reach
 * into S's private `CalculatedDimensionsHolder` via reflection and set our own
 * [S.CalculatedDimensions] — the same object that S.applied() would otherwise return. This
 * avoids fighting with static-method interception and keeps test setup trivial.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class VerseRendererTest {

    private val FONT_RED = 0xff112233.toInt()
    private val VERSE_NUMBER_COLOR = 0xff445566.toInt()
    private val BACKGROUND = 0xfff0f0f0.toInt()
    private val INDENT_FIRST = 10
    private val INDENT_REST = 20
    private val INDENT_1 = 30
    private val INDENT_2 = 40
    private val INDENT_3 = 50
    private val INDENT_4 = 60
    private val INDENT_EXTRA = 5

    private val ARI = 0x010203 // arbitrary

    private lateinit var dims: S.CalculatedDimensions

    @Before
    fun setUp() {
        yuku.afw.App.initWithAppContext(ApplicationProvider.getApplicationContext())

        dims = S.CalculatedDimensions().apply {
            fontRedColor = FONT_RED
            verseNumberColor = VERSE_NUMBER_COLOR
            backgroundColor = BACKGROUND
            indentParagraphFirst = INDENT_FIRST
            indentParagraphRest = INDENT_REST
            indentSpacing1 = INDENT_1
            indentSpacing2 = INDENT_2
            indentSpacing3 = INDENT_3
            indentSpacing4 = INDENT_4
            indentSpacingExtra = INDENT_EXTRA
        }

        // Trigger the Holder's one-time init so its backing Java class is loaded and its
        // INSTANCE field exists, then overwrite `applied` with our deterministic dims.
        S.applied()
        overrideAppliedDimensions(dims)
    }

    private fun overrideAppliedDimensions(d: S.CalculatedDimensions) {
        val holderClass = Class.forName("${S::class.java.name}\$CalculatedDimensionsHolder")
        val instance = holderClass.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
        holderClass.getDeclaredField("applied").apply { isAccessible = true }.set(instance, d)
    }

    // region simpleRender: text that does not start with "@@"

    @Test
    fun `a verse that does not start with the at-at header uses simpleRender and writes the verse number + text into lText, returning the offset past the prefix`() {
        val lText = android.widget.TextView(ApplicationProvider.getApplicationContext())
        val offset = VerseRenderer.render(
            lText, null, true, ARI, "Hello", "1", null, false, null, null,
        )

        assertEquals("1  Hello", lText.text.toString())
        assertEquals("simple-render returns the offset past 'N  ' (verse number + two spaces)", 3, offset)
    }

    @Test
    fun `simpleRender with verse number hidden writes only the verse text into lText and returns a zero offset`() {
        val lText = android.widget.TextView(ApplicationProvider.getApplicationContext())
        val offset = VerseRenderer.render(
            lText, null, false, ARI, "Hello", "1", null, false, null, null,
        )

        assertEquals("Hello", lText.text.toString())
        assertEquals(0, offset)
    }

    @Test
    fun `a single-character text falls into simpleRender because the at-at header requires at least two characters`() {
        val lText = android.widget.TextView(ApplicationProvider.getApplicationContext())
        val offset = VerseRenderer.render(
            lText, null, true, ARI, "@", "1", null, false, null, null,
        )

        // simpleRender appends the text verbatim; '@' passes through untouched.
        assertEquals("1  @", lText.text.toString())
        assertEquals(3, offset)
    }

    @Test
    fun `text whose second character is not an at-sign falls into simpleRender (only @@ triggers the formatted path)`() {
        val lText = android.widget.TextView(ApplicationProvider.getApplicationContext())
        val offset = VerseRenderer.render(
            lText, null, true, ARI, "@Hello", "1", null, false, null, null,
        )

        assertEquals("1  @Hello", lText.text.toString())
        assertEquals(3, offset)
    }

    @Test
    fun `simpleRender populates FormattedTextResult with the raw text when the verse is unformatted (ftr path bypasses the SpannableStringBuilder)`() {
        val ftr = VerseRenderer.FormattedTextResult()
        render(text = "plain text", verseNumberText = "7", ftr = ftr)

        // Contract: on the simpleRender path, ftr.result is set to the input text, not the
        // formatted SpannableStringBuilder. Callers that need the verse-number-prefixed
        // Spannable must read from lText, not ftr.
        assertSame("ftr.result must be the original text reference on the simpleRender path", "plain text", ftr.result)
    }

    @Test
    fun `simpleRender attaches a VerseNumberSpan covering only the verse number digits`() {
        val sb = renderToSb(text = "Hello", verseNumberText = "42")
        val spans = sb.getSpans(0, sb.length, VerseRenderer.VerseNumberSpan::class.java)

        assertEquals(1, spans.size)
        assertEquals(0, sb.getSpanStart(spans[0]))
        assertEquals("VerseNumberSpan ends at the length of the verse number, not including the trailing spaces", 2, sb.getSpanEnd(spans[0]))
    }

    @Test
    fun `simpleRender applies a hanging-indent LeadingMarginSpan (first-line 0, rest indentParagraphRest) when a verse number is shown`() {
        val sb = renderToSb(text = "Hello", verseNumberText = "1")
        val margins = sb.getSpans(0, sb.length, LeadingMarginSpan.Standard::class.java)

        assertEquals(1, margins.size)
        assertEquals(0, margins[0].getLeadingMargin(true))
        assertEquals(INDENT_REST, margins[0].getLeadingMargin(false))
    }

    @Test
    fun `simpleRender applies a uniform LeadingMarginSpan of indentParagraphRest for both lines when the verse number is hidden`() {
        val sb = renderToSb(text = "Hello", verseNumberText = "1", isVerseNumberShown = false)
        val margins = sb.getSpans(0, sb.length, LeadingMarginSpan.Standard::class.java)

        assertEquals(1, margins.size)
        assertEquals(INDENT_REST, margins[0].getLeadingMargin(true))
        assertEquals(INDENT_REST, margins[0].getLeadingMargin(false))
    }

    // endregion

    // region formatted verses: the @@ header

    @Test
    fun `a formatted verse with no further markup embeds the verse number and appends the plain text`() {
        val ftr = VerseRenderer.FormattedTextResult()
        val offset = render(text = "@@Hello", verseNumberText = "1", ftr = ftr)

        assertEquals("1  Hello", ftr.result.toString())
        assertEquals(3, offset)
    }

    @Test
    fun `a formatted verse that starts with the first-paragraph marker @^ does NOT embed the verse number inline`() {
        val ftr = VerseRenderer.FormattedTextResult()
        val offset = render(text = "@@@^Hello", verseNumberText = "1", ftr = ftr)

        // The verse number is delegated to lVerseNumber; lText starts directly with the verse body.
        assertEquals("Hello", ftr.result.toString())
        assertEquals(0, offset)
    }

    @Test
    fun `a formatted verse that starts with @1 through @4 suppresses the inline verse number (paragraph markers take over layout)`() {
        for (marker in '1'..'4') {
            val ftr = VerseRenderer.FormattedTextResult()
            val offset = render(text = "@@@${marker}Body", verseNumberText = "1", ftr = ftr)

            assertEquals("paragraph marker @$marker should suppress inline verse number", "Body", ftr.result.toString())
            assertEquals(0, offset)
        }
    }

    @Test
    fun `a formatted verse that starts with @0 keeps the inline verse number because @0 is not in the suppress list`() {
        // Characterization: only @^ and @1..@4 suppress the inline verse number. @0 does not.
        val ftr = VerseRenderer.FormattedTextResult()
        val offset = render(text = "@@@0Body", verseNumberText = "1", ftr = ftr)

        assertEquals("1  Body", ftr.result.toString())
        assertEquals(3, offset)
    }

    // endregion

    // region paragraph markers and indentation

    @Test
    fun `an explicit @0 at the start of a formatted verse produces two paragraphs one covering the inline verse number and one covering the body`() {
        // Characterization quirk: because @0 is a "start paragraph" marker that flushes the
        // pending paragraph, putting @0 immediately after "@@" closes the (empty-except-for-
        // the-verse-number) implicit first paragraph and starts a new one for the body. Both
        // paragraphs use case '0' styling => hanging indent (first=0, rest=indentParagraphRest).
        val sb = renderToSb(text = "@@@0Hello", verseNumberText = "1")
        val margins = sb.getSpans(0, sb.length, LeadingMarginSpan.Standard::class.java)

        assertEquals(2, margins.size)
        for (m in margins) {
            assertEquals(0, m.getLeadingMargin(true))
            assertEquals(INDENT_REST, m.getLeadingMargin(false))
        }

        // The first covers "1  " (the verse-number prefix), the second covers "Hello".
        val sortedByStart = margins.sortedBy { sb.getSpanStart(it) }
        assertEquals(0, sb.getSpanStart(sortedByStart[0]))
        assertEquals(3, sb.getSpanEnd(sortedByStart[0]))
        assertEquals(3, sb.getSpanStart(sortedByStart[1]))
        assertEquals(sb.length, sb.getSpanEnd(sortedByStart[1]))
    }

    @Test
    fun `a formatted verse without an explicit paragraph marker uses a single implicit case-minus-one paragraph (hanging indent, no newline)`() {
        // Contrast with the @0 test above: omitting @0 produces a single paragraph because the
        // implicit paraType stays at -1 and no "start paragraph" flush happens mid-verse.
        val sb = renderToSb(text = "@@Hello", verseNumberText = "1")
        val margins = sb.getSpans(0, sb.length, LeadingMarginSpan.Standard::class.java)

        assertEquals(1, margins.size)
        assertEquals(0, margins[0].getLeadingMargin(true))
        assertEquals(INDENT_REST, margins[0].getLeadingMargin(false))
    }

    @Test
    fun `the @1 paragraph marker applies a uniform indent of indentSpacing1 when the verse number text fits in 2 characters`() {
        val sb = renderToSb(text = "@@@1Body", verseNumberText = "1")
        val margins = sb.getSpans(0, sb.length, LeadingMarginSpan.Standard::class.java)

        assertEquals(1, margins.size)
        assertEquals(INDENT_1, margins[0].getLeadingMargin(true))
        assertEquals(INDENT_1, margins[0].getLeadingMargin(false))
    }

    @Test
    fun `@1 through @4 each resolve to their own indentSpacing_n value`() {
        val expected = mapOf('1' to INDENT_1, '2' to INDENT_2, '3' to INDENT_3, '4' to INDENT_4)
        for ((marker, indent) in expected) {
            val sb = renderToSb(text = "@@@${marker}Body", verseNumberText = "1")
            val margins = sb.getSpans(0, sb.length, LeadingMarginSpan.Standard::class.java)

            assertEquals("@$marker should have one LeadingMarginSpan", 1, margins.size)
            assertEquals("@$marker should use indentSpacing$marker = $indent", indent, margins[0].getLeadingMargin(true))
        }
    }

    @Test
    fun `a verse number longer than 2 characters adds indentSpacingExtra per extra digit to an indented paragraph`() {
        // Verse-number-text length 5 => extra = 5 - 2 = 3 extra units.
        val sb = renderToSb(text = "@@@1Body", verseNumberText = "12345")
        val margins = sb.getSpans(0, sb.length, LeadingMarginSpan.Standard::class.java)

        assertEquals(1, margins.size)
        assertEquals(INDENT_1 + 3 * INDENT_EXTRA, margins[0].getLeadingMargin(true))
    }

    @Test
    fun `@^ paragraph marker applies a first-line indent of indentParagraphFirst and a rest indent of indentParagraphRest`() {
        val sb = renderToSb(text = "@@@^Hello", verseNumberText = "1")
        val margins = sb.getSpans(0, sb.length, LeadingMarginSpan.Standard::class.java)

        assertEquals(1, margins.size)
        assertEquals(INDENT_FIRST, margins[0].getLeadingMargin(true))
        assertEquals(INDENT_REST, margins[0].getLeadingMargin(false))
    }

    @Test
    fun `multiple paragraphs each get their own LeadingMarginSpan, separated by a newline`() {
        // "@@First@1Second@2Third" -> paragraphs: First (implicit @-1), Second (@1), Third (@2).
        val ftr = VerseRenderer.FormattedTextResult()
        val sb = renderToSb(text = "@@First@1Second@2Third", verseNumberText = "1", ftr = ftr)
        val margins = sb.getSpans(0, sb.length, LeadingMarginSpan.Standard::class.java)

        // Text layout: "1  First\nSecond\nThird"
        assertEquals("1  First\nSecond\nThird", ftr.result.toString())

        // Three paragraphs => three margin spans.
        assertEquals(3, margins.size)

        val sortedByStart = margins.sortedBy { sb.getSpanStart(it) }
        // First paragraph: verse-number + "First", hanging indent.
        assertEquals(0, sortedByStart[0].getLeadingMargin(true))
        assertEquals(INDENT_REST, sortedByStart[0].getLeadingMargin(false))
        // Second paragraph (@1).
        assertEquals(INDENT_1, sortedByStart[1].getLeadingMargin(true))
        // Third paragraph (@2).
        assertEquals(INDENT_2, sortedByStart[2].getLeadingMargin(true))
    }

    // endregion

    // region @9..@7 italic formatting

    @Test
    fun `@9 and @7 wrap an italic StyleSpan exactly around the enclosed text`() {
        val sb = renderToSb(text = "@@ab@9cd@7ef", verseNumberText = "1")

        assertEquals("1  abcdef", sb.toString())

        val italics = sb.getSpans(0, sb.length, StyleSpan::class.java).filter { it.style == Typeface.ITALIC }
        assertEquals(1, italics.size)
        // "1  ab" = 5 chars before "cd", which is 2 chars.
        assertEquals(5, sb.getSpanStart(italics[0]))
        assertEquals(7, sb.getSpanEnd(italics[0]))
    }

    @Test
    fun `an italic run started without a preceding @9 marker is not applied (unbalanced close is a no-op)`() {
        // Dangling @7 with no @9: startItalic stays -1, no span created.
        val sb = renderToSb(text = "@@ab@7cd", verseNumberText = "1")

        assertEquals("1  abcd", sb.toString())
        assertEquals(0, sb.getSpans(0, sb.length, StyleSpan::class.java).size)
    }

    @Test
    fun `multiple italic runs in the same verse each produce their own StyleSpan`() {
        val sb = renderToSb(text = "@@a@9b@7c@9d@7e", verseNumberText = "1")

        assertEquals("1  abcde", sb.toString())
        val italics = sb.getSpans(0, sb.length, StyleSpan::class.java).filter { it.style == Typeface.ITALIC }
        assertEquals(2, italics.size)
    }

    // endregion

    // region @6..@5 red-letter formatting

    @Test
    fun `@6 and @5 wrap a ForegroundColorSpan using fontRedColor from the applied dimensions`() {
        val sb = renderToSb(text = "@@Jesus said @6Verily@5 unto them", verseNumberText = "1", checked = false)
        val reds = sb.getSpans(0, sb.length, ForegroundColorSpan::class.java)

        assertEquals(1, reds.size)
        assertEquals(FONT_RED, reds[0].foregroundColor)
        // "1  Jesus said " is 14 chars, "Verily" is 6 chars.
        assertEquals(14, sb.getSpanStart(reds[0]))
        assertEquals(20, sb.getSpanEnd(reds[0]))
    }

    @Test
    fun `checked=true suppresses the red-letter ForegroundColorSpan (so Jesus' words do not look red when the verse is selected)`() {
        val sb = renderToSb(text = "@@a @6Verily@5 b", verseNumberText = "1", checked = true)
        val reds = sb.getSpans(0, sb.length, ForegroundColorSpan::class.java)

        assertEquals("no red span must be attached when the verse is checked", 0, reds.size)
    }

    @Test
    fun `a dangling @5 without a matching @6 is a no-op (unbalanced close)`() {
        val sb = renderToSb(text = "@@a@5b", verseNumberText = "1")

        assertEquals("1  ab", sb.toString())
        assertEquals(0, sb.getSpans(0, sb.length, ForegroundColorSpan::class.java).size)
    }

    // endregion

    // region @8 line break

    @Test
    fun `the @8 line-break marker inserts a literal newline into the rendered text`() {
        val ftr = VerseRenderer.FormattedTextResult()
        render(text = "@@line1@8line2", verseNumberText = "1", ftr = ftr)

        assertEquals("1  line1\nline2", ftr.result.toString())
    }

    // endregion

    // region @< ... @> @/ special tags (footnotes, cross-references)

    @Test
    fun `a footnote tag inserts a superscript digit matching the tag number`() {
        val ftr = VerseRenderer.FormattedTextResult()
        render(text = "@@body@<f1@>@/", verseNumberText = "1", ftr = ftr)

        // 'f1' => superscript ONE (U+00B9).
        assertEquals("1  body\u00b9", ftr.result.toString())
    }

    @Test
    fun `a multi-digit footnote tag inserts one superscript character per digit`() {
        val ftr = VerseRenderer.FormattedTextResult()
        render(text = "@@body@<f12@>@/", verseNumberText = "1", ftr = ftr)

        // 'f12' => superscript 1 then superscript 2.
        assertEquals("1  body\u00b9\u00b2", ftr.result.toString())
    }

    @Test
    fun `a cross-reference tag inserts the XREF_MARK asterism character`() {
        val ftr = VerseRenderer.FormattedTextResult()
        render(text = "@@body@<x1@>@/", verseNumberText = "1", ftr = ftr)

        assertEquals("1  body" + VerseRenderer.XREF_MARK, ftr.result.toString())
    }

    @Test
    fun `a footnote tag invokes the inline-link factory with type=footnote and arif=(ari shifted left 8) or field`() {
        val calls = mutableListOf<Pair<VerseInlineLinkSpan.Type, Int>>()
        val factory = VerseInlineLinkSpan.Factory { type, arif ->
            calls += type to arif
            object : VerseInlineLinkSpan(type, arif) {}
        }
        render(text = "@@body@<f3@>@/", verseNumberText = "1", inlineLinkSpanFactory = factory)

        assertEquals(1, calls.size)
        assertEquals(VerseInlineLinkSpan.Type.footnote, calls[0].first)
        assertEquals((ARI shl 8) or 3, calls[0].second)
    }

    @Test
    fun `a cross-reference tag invokes the inline-link factory with type=xref`() {
        val types = mutableListOf<VerseInlineLinkSpan.Type>()
        val factory = VerseInlineLinkSpan.Factory { type, arif ->
            types += type
            object : VerseInlineLinkSpan(type, arif) {}
        }
        render(text = "@@body@<x7@>@/", verseNumberText = "1", inlineLinkSpanFactory = factory)

        assertEquals(listOf(VerseInlineLinkSpan.Type.xref), types)
    }

    @Test
    fun `multiple footnotes in one verse produce multiple factory invocations and multiple superscripts`() {
        val calls = mutableListOf<Int>()
        val factory = VerseInlineLinkSpan.Factory { _, arif ->
            calls += arif and 0xff
            object : VerseInlineLinkSpan(VerseInlineLinkSpan.Type.footnote, arif) {}
        }
        val ftr = VerseRenderer.FormattedTextResult()
        render(text = "@@a@<f1@>@/b@<f2@>@/c", verseNumberText = "1", inlineLinkSpanFactory = factory, ftr = ftr)

        assertEquals("1  a\u00b9b\u00b2c", ftr.result.toString())
        assertEquals(listOf(1, 2), calls)
    }

    @Test
    fun `unknown special tags (neither f nor x) are silently dropped from the output`() {
        val ftr = VerseRenderer.FormattedTextResult()
        render(text = "@@a@<unknown@>@/b", verseNumberText = "1", ftr = ftr)

        // processSpecialTag only reacts to 'f' or 'x' prefixes; anything else leaves sb untouched.
        assertEquals("1  ab", ftr.result.toString())
    }

    // endregion

    // region highlights

    @Test
    fun `a full-verse highlight attaches a BackgroundColorSpan spanning the verse text (excluding the verse-number prefix)`() {
        val info = Highlights.Info().apply {
            colorRgb = 0x00ff00
            partial = null
        }
        val sb = renderToSb(text = "Hello", verseNumberText = "1", highlight = info)
        val bgs = sb.getSpans(0, sb.length, BackgroundColorSpan::class.java)

        assertEquals(1, bgs.size)
        // The background must start after "1  " (the verse-number prefix), not at 0.
        assertEquals(3, sb.getSpanStart(bgs[0]))
        assertEquals(sb.length, sb.getSpanEnd(bgs[0]))
        assertEquals(Highlights.blendOver(0x00ff00, BACKGROUND), bgs[0].backgroundColor)
    }

    @Test
    fun `a partial highlight whose hash matches the rendered body attaches a BackgroundColorSpan over the requested offsets`() {
        val text = "Hello world"
        val info = Highlights.Info().apply {
            colorRgb = 0xff0000
            partial = Highlights.Info.Partial().apply {
                hashCode = Highlights.hashCode(text)
                startOffset = 0
                endOffset = 5
            }
        }
        val sb = renderToSb(text = text, verseNumberText = "1", highlight = info)
        val bgs = sb.getSpans(0, sb.length, BackgroundColorSpan::class.java)

        assertEquals(1, bgs.size)
        // Partial offsets are relative to the verse body, so add the verse-number prefix length (3).
        assertEquals(3, sb.getSpanStart(bgs[0]))
        assertEquals(8, sb.getSpanEnd(bgs[0]))
    }

    @Test
    fun `a partial highlight whose hash does not match the rendered body falls back to highlighting the entire verse body`() {
        val info = Highlights.Info().apply {
            colorRgb = 0x0000ff
            partial = Highlights.Info.Partial().apply {
                hashCode = 12345 // deliberately wrong
                startOffset = 0
                endOffset = 3
            }
        }
        val sb = renderToSb(text = "Hello", verseNumberText = "1", highlight = info)
        val bgs = sb.getSpans(0, sb.length, BackgroundColorSpan::class.java)

        assertEquals(1, bgs.size)
        // Entire body is highlighted because the partial-validity check failed.
        assertEquals(3, sb.getSpanStart(bgs[0]))
        assertEquals(sb.length, sb.getSpanEnd(bgs[0]))
    }

    // endregion

    // region FormattedTextResult contract

    @Test
    fun `FormattedTextResult on the formatted path receives the built SpannableStringBuilder (not the raw input text)`() {
        val ftr = VerseRenderer.FormattedTextResult()
        render(text = "@@a@9b@7c", verseNumberText = "1", ftr = ftr)

        // Unlike simpleRender (where ftr.result === input), the formatted path puts the Spannable in ftr.
        assertTrue("ftr.result must be a Spanned on the formatted path", ftr.result is Spanned)
        assertEquals("1  abc", ftr.result.toString())
    }

    @Test
    fun `passing a null FormattedTextResult is allowed and the render still produces output via its return value`() {
        val offset = render(text = "@@Hello", verseNumberText = "1", ftr = null)
        assertEquals(3, offset)
    }

    // endregion

    // region appendSuperscriptNumber (small surface, tested directly so a refactor cannot silently change it)

    @Test
    fun `appendSuperscriptNumber maps each digit to its Unicode superscript counterpart`() {
        val sb = android.text.SpannableStringBuilder()
        VerseRenderer.appendSuperscriptNumber(sb, 0)
        VerseRenderer.appendSuperscriptNumber(sb, 9)
        VerseRenderer.appendSuperscriptNumber(sb, 10)
        VerseRenderer.appendSuperscriptNumber(sb, 255)

        // 0, 9, 10 (=¹⁰), 255 (=²⁵⁵).
        assertEquals("\u2070\u2079\u00b9\u2070\u00b2\u2075\u2075", sb.toString())
    }

    // endregion

    // region helpers

    /**
     * Convenience wrapper around [VerseRenderer.render] that only exposes the parameters the
     * tests actually vary. Calls render with both TextViews = null (tests inspect the
     * FormattedTextResult or the raw SpannableStringBuilder via [renderToSb]).
     */
    private fun render(
        text: String,
        verseNumberText: String,
        isVerseNumberShown: Boolean = true,
        ari: Int = ARI,
        highlight: Highlights.Info? = null,
        checked: Boolean = false,
        inlineLinkSpanFactory: VerseInlineLinkSpan.Factory? = null,
        ftr: VerseRenderer.FormattedTextResult? = null,
    ): Int {
        return VerseRenderer.render(
            /* lText = */ null,
            /* lVerseNumber = */ null,
            isVerseNumberShown,
            ari,
            text,
            verseNumberText,
            highlight,
            checked,
            inlineLinkSpanFactory,
            ftr,
        )
    }

    /**
     * Renders the verse and returns the underlying [android.text.SpannableStringBuilder] via
     * a [VerseRenderer.FormattedTextResult]. For unformatted text, where ftr.result is the
     * raw String reference, we render a second time through a capturing TextView — but we
     * don't actually need that because our simpleRender tests explicitly read spans through a
     * Robolectric-backed SpannableStringBuilder. To keep the helper simple, simpleRender
     * callers receive a freshly built SpannableStringBuilder by routing through lText.
     */
    private fun renderToSb(
        text: String,
        verseNumberText: String,
        isVerseNumberShown: Boolean = true,
        highlight: Highlights.Info? = null,
        checked: Boolean = false,
        ftr: VerseRenderer.FormattedTextResult? = null,
    ): android.text.SpannableStringBuilder {
        val lText = android.widget.TextView(ApplicationProvider.getApplicationContext())
        val lVerseNumber = android.widget.TextView(ApplicationProvider.getApplicationContext())
        VerseRenderer.render(
            lText,
            lVerseNumber,
            isVerseNumberShown,
            ARI,
            text,
            verseNumberText,
            highlight,
            checked,
            /* inlineLinkSpanFactory = */ null,
            ftr,
        )
        // TextView.setText(CharSequence) copies Spannables into an internal buffer. To get
        // back a SpannableStringBuilder with the same spans, we read lText.text (which is a
        // Spanned) and wrap it.
        val rendered = lText.text
        assertNotNull("lText must have been populated by render()", rendered)
        return android.text.SpannableStringBuilder(rendered)
    }

    // endregion
}
