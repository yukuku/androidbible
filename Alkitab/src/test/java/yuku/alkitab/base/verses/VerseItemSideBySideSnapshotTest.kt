package yuku.alkitab.base.verses

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.afw.App as AfwApp
import yuku.alkitab.base.S
import yuku.alkitab.base.util.Appearances
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.base.util.TextColorUtil
import yuku.alkitab.base.widget.AttributeView
import yuku.alkitab.base.widget.VerseRenderer
import yuku.alkitab.base.widget.VerseRendererCompose
import yuku.alkitab.debug.R
import yuku.alkitab.util.Ari

/**
 * Visual side-by-side snapshot test for the legacy [VerseItem] and the
 * experimental [VerseItemComposeView]. Each test case is rendered through
 * both pipelines, the bitmaps are saved as PNGs under
 * `Alkitab/build/snapshots/verse-item/`, and a top-level `index.html` is
 * generated that displays them in a two-column table for human inspection.
 *
 * This is the primary tool for hunting the random text-rendering bug that
 * motivated [VerseItemComposeView] (see GH #199). If a row in the HTML report
 * shows the two columns diverging on a case that doesn't involve the suspected
 * spans, that's a parity bug to fix first. If they match across the board,
 * the legacy renderer is the suspect.
 *
 * Not a strict pixel-diff: PNGs are written, not compared. Future work may
 * compute an SSIM and assert similarity; for now the report is for eyeballing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VerseItemSideBySideSnapshotTest {

    private val FONT_COLOR = 0xff202020.toInt()
    private val FONT_RED = 0xffcc0000.toInt()
    private val VERSE_NUMBER_COLOR = 0xff445566.toInt()
    private val FONT_SIZE_DP = 17f
    private val LINE_SPACING_MULT = 1.15f
    // Mirror the production values from `res/values/dimens_indent.xml`. With
    // a 1×-density (mdpi) test display, 1dp == 1px, so we can use the dp
    // numbers directly as the pixel offsets the CalculatedDimensions struct
    // expects. The key relationship is `FIRST > REST` for the `@^` marker:
    // first line is pushed in to make room for the verse-number gutter, and
    // continuation lines flow back to the left — NOT a typographic hanging
    // indent. Earlier test values had these inverted, which made both
    // renderers agree on a layout that real production never produces.
    private val INDENT_FIRST = 38
    private val INDENT_REST = 5
    private val INDENT_1 = 22
    private val INDENT_2 = 38
    private val INDENT_3 = 54
    private val INDENT_4 = 70
    private val INDENT_EXTRA = 6

    /** Width in pixels for the snapshot viewport (~360dp at mdpi). */
    private val VIEWPORT_WIDTH_PX = 360

    private lateinit var dims: S.CalculatedDimensions

    @Before
    fun setUp() {
        AfwApp.initWithAppContext(ApplicationProvider.getApplicationContext())

        // Pin the selected-verse background colour so both rendering paths
        // agree on what `TextColorUtil.getForCheckedVerse(...)` returns. The
        // legacy holder reads this via Preferences; the Compose path reads it
        // via Preferences too. Without an explicit value, the two paths can
        // diverge if one falls back to a hard-coded constant and the other
        // resolves the resource default.
        val prefContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        yuku.afw.storage.Preferences.setInt(
            prefContext.getString(R.string.pref_selectedVerseBgColor_key),
            SELECTED_BG_COLOR,
        )

        // Mirror the override approach used in VerseRendererTest so both
        // renderers (legacy and Compose) see identical dimensions.
        dims = S.CalculatedDimensions().apply {
            fontSize2dp = FONT_SIZE_DP
            fontFace = Typeface.DEFAULT
            fontBold = Typeface.NORMAL
            fontColor = FONT_COLOR
            fontRedColor = FONT_RED
            verseNumberColor = VERSE_NUMBER_COLOR
            lineSpacingMult = LINE_SPACING_MULT
            indentParagraphFirst = INDENT_FIRST
            indentParagraphRest = INDENT_REST
            indentSpacing1 = INDENT_1
            indentSpacing2 = INDENT_2
            indentSpacing3 = INDENT_3
            indentSpacing4 = INDENT_4
            indentSpacingExtra = INDENT_EXTRA
        }
        S.applied()
        overrideAppliedDimensions(dims)
    }

    private fun overrideAppliedDimensions(d: S.CalculatedDimensions) {
        val holderClass = Class.forName("${S::class.java.name}\$CalculatedDimensionsHolder")
        val instance = holderClass.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
        holderClass.getDeclaredField("applied").apply { isAccessible = true }.set(instance, d)
    }

    @Test
    fun `produce side-by-side snapshots for many verse formattings, sizes, colors and attributes`() {
        val cases = buildCases()
        val outputDir = resolveSnapshotDir()
        outputDir.mkdirs()

        val rows = StringBuilder()

        for (case in cases) {
            val legacyBitmap = renderLegacy(case)
            val composeBitmap = renderCompose(case)

            val legacyFile = File(outputDir, "${case.name}-legacy.png")
            val composeFile = File(outputDir, "${case.name}-compose.png")
            FileOutputStream(legacyFile).use { legacyBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileOutputStream(composeFile).use { composeBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

            rows.append(
                "<tr>" +
                    "<td class=\"label\"><div class=\"name\">${escapeHtml(case.name)}</div><pre>${escapeHtml(case.describe())}</pre></td>" +
                    "<td><img src=\"${legacyFile.name}\"/></td>" +
                    "<td><img src=\"${composeFile.name}\"/></td>" +
                    "</tr>\n"
            )
        }

        val html = """
            <!doctype html>
            <html><head><meta charset="utf-8"/><title>VerseItem vs VerseItemCompose</title>
            <style>
            body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; margin: 24px; }
            table { border-collapse: collapse; }
            td, th { vertical-align: top; padding: 8px 12px; border-bottom: 1px solid #ddd; }
            th { text-align: left; background: #f4f4f4; position: sticky; top: 0; }
            td.label { width: 320px; }
            td.label .name { font-weight: 600; }
            td.label pre { margin: 4px 0 0; white-space: pre-wrap; word-break: break-word; color: #444; font-size: 12px; }
            img { display: block; max-width: 480px; image-rendering: pixelated; border: 1px solid #eee; }
            </style></head>
            <body>
            <h1>VerseItem vs VerseItemCompose snapshots</h1>
            <p>${cases.size} cases. Compare the two columns row-by-row; differences indicate either parity bugs in the Compose port or rendering bugs in the legacy view.</p>
            <table>
              <thead><tr><th>Case</th><th>Legacy VerseItem</th><th>VerseItemCompose</th></tr></thead>
              <tbody>
            ${rows}
              </tbody>
            </table>
            </body></html>
        """.trimIndent()

        val indexFile = File(outputDir, "index.html")
        indexFile.writeText(html)

        println("Snapshot report written to: ${indexFile.absolutePath}")
        assertTrue("index.html should exist", indexFile.exists())
    }

    private fun resolveSnapshotDir(): File {
        // Robolectric runs with `user.dir` set to the module dir (Alkitab/).
        // Both `build/snapshots/...` and an env override are accepted for CI.
        val override = System.getenv("VERSE_ITEM_SNAPSHOT_DIR")
        if (!override.isNullOrBlank()) return File(override)
        val moduleDir = File(System.getProperty("user.dir") ?: ".")
        return File(moduleDir, "build/snapshots/verse-item")
    }

    // ---- Test data ------------------------------------------------------------------------------

    /**
     * One side-by-side snapshot case. Default values keep call sites short for
     * the common "render a paragraph of text" cases.
     */
    private data class Case(
        val name: String,
        val text: String,
        val isVerseNumberShown: Boolean = true,
        val verseNumber: Int = 1,
        val checked: Boolean = false,
        val textSizeMult: Float = 1f,
        val highlightColorRgb: Int? = null,
        val partialHighlightStart: Int? = null,
        val partialHighlightEnd: Int? = null,
        val bookmarkCount: Int = 0,
        val noteCount: Int = 0,
        val progressMarkBits: Int = 0,
        val hasMaps: Boolean = false,
        /** When non-null, both renderers swap in this Typeface for the row. */
        val typeface: Typeface? = null,
        val typefaceLabel: String? = null,
    ) {
        fun describe(): String {
            val parts = mutableListOf<String>()
            parts += "text=\"$text\""
            if (!isVerseNumberShown) parts += "verseNumberShown=false"
            if (verseNumber != 1) parts += "verse=$verseNumber"
            if (checked) parts += "checked"
            if (textSizeMult != 1f) parts += "textSizeMult=$textSizeMult"
            if (highlightColorRgb != null) parts += "highlight=#${"%06x".format(highlightColorRgb)}"
            if (partialHighlightStart != null) parts += "partial=[$partialHighlightStart..$partialHighlightEnd]"
            if (bookmarkCount > 0) parts += "bookmarks=$bookmarkCount"
            if (noteCount > 0) parts += "notes=$noteCount"
            if (progressMarkBits != 0) parts += "pm=0x${"%x".format(progressMarkBits)}"
            if (hasMaps) parts += "hasMaps"
            if (typefaceLabel != null) parts += "typeface=$typefaceLabel"
            return parts.joinToString("\n")
        }
    }

    private fun buildCases(): List<Case> = listOf(
        // Plain text
        Case("01-simple", "In the beginning God created the heavens and the earth."),
        Case("02-simple-no-number", "And the earth was without form, and void.", isVerseNumberShown = false),
        Case("03-simple-long-number", "And it came to pass.", verseNumber = 119),
        Case("04-checked", "Selected verse content here.", checked = true),

        // Formatting markers
        Case("10-formatted-default", "@@A simple formatted verse with no paragraph marker."),
        Case("11-paragraph-zero", "@@@0First line of paragraph zero, which keeps the verse number inline."),
        Case("12-paragraph-first-indent", "@@@^This paragraph uses caret indent for first-line hanging style. The rest of the paragraph should wrap to a different indent."),
        // Reference case from the maintainer (see PR #202 review): verse
        // number sits in the gutter, first line indented to clear it,
        // continuation lines flow flush-left at indentParagraphRest.
        Case("12b-caret-long-wrap", "@@@^1:1:6 para start with looooooooooooong text laba laba bala bala laba laba bala bala laba laba", verseNumber = 6),
        Case("13-paragraph-one", "@@@1Indent level 1 paragraph that wraps several lines to demonstrate consistent rest-indent."),
        Case("14-paragraph-two", "@@@2Indent level 2 paragraph that wraps several lines."),
        Case("15-paragraph-three", "@@@3Indent level 3 paragraph wraps to show indent3 rest spacing."),
        Case("16-paragraph-four", "@@@4Indent level 4 paragraph wraps to demonstrate the deepest preset indent."),

        // Inline styles
        Case("20-italic", "@@he said, @9thus says the LORD@7 unto you all."),
        Case("21-red-words", "@@@6Truly I say unto you, until heaven and earth pass away.@5"),
        Case("22-mixed-italic-red", "@@@9narrator:@7 @6and Jesus answered@5 with a parable."),

        // Line break + multiple paragraphs
        Case("23-explicit-linebreak", "@@First line.@8Second line after explicit break."),
        Case("24-multi-paragraph", "@@@^A first paragraph with caret indent that wraps to multiple lines for testing.@1A second paragraph at indent level 1."),
        // `@^` in the middle of a verse is common in real Bible texts — it
        // starts a new logical paragraph mid-verse (no @@ prefix required).
        Case("25-caret-mid-verse", "@@First sentence of the verse.@^A new paragraph starting mid-verse with caret indent."),
        // `@8@^` is even more common: an explicit line break followed by a
        // caret-indented paragraph.
        Case("26-linebreak-then-caret", "@@First sentence.@8@^Next block after a line break and caret indent."),
        // All indent levels in a single verse, mirroring real OT poetry / Psalms.
        Case("27-multi-indent-cycle", "@@@^Caret intro line.@1At indent level one.@2At indent level two.@3At indent level three.@4At indent level four.@0Back to indent zero."),

        // Special tags
        Case("30-footnote", "@@The word came@<f1@>@/ unto the prophet."),
        Case("31-xref", "@@As it is written@<x1@>@/ in the law."),
        Case("32-footnote-and-xref", "@@A verse@<f2@>@/ with a cross-reference@<x3@>@/ inline."),
        Case("33-multi-digit-footnote", "@@Verse with footnote@<f42@>@/ tag."),

        // Highlights
        Case("40-full-highlight", "@@The whole verse highlighted in yellow.", highlightColorRgb = 0xfff4d03f.toInt()),
        // Partial highlight only matches if the renderer's `hashCode` of the
        // verse body equals the stored partial.hashCode. The simpleRender
        // path computes the hash against the raw text directly, so we use a
        // non-formatted verse here. (Formatted-text partial highlights are
        // exercised by the legacy path through real saved highlights, where
        // the renderer's hash matches the stored verse body.)
        Case("41-partial-highlight", "The middle words are highlighted only.",
            highlightColorRgb = 0xff85c1e9.toInt(),
            partialHighlightStart = 4, partialHighlightEnd = 14),

        // Text sizes
        Case("50-small-text", "Render at smaller text size.", textSizeMult = 0.7f),
        Case("51-large-text", "Render at larger text size.", textSizeMult = 1.5f),
        Case("52-very-large-text", "Render at extra-large text size to push wrapping.", textSizeMult = 2.0f),

        // Attributes
        Case("60-bookmark", "Verse with one bookmark.", bookmarkCount = 1),
        Case("61-multi-bookmarks", "Verse with several bookmarks stacked.", bookmarkCount = 4),
        Case("62-note", "Verse with one note.", noteCount = 1),
        Case("63-note-and-bookmark", "Verse with both bookmark and note.", bookmarkCount = 1, noteCount = 1),
        Case("64-progress-mark", "Verse pinned with the first progress mark.",
            progressMarkBits = 1 shl (AttributeView.PROGRESS_MARK_BITS_START + 0)),
        Case("65-all-progress-marks", "Verse pinned with every progress mark.",
            progressMarkBits = AttributeView.PROGRESS_MARK_BIT_MASK),
        Case("66-has-maps", "Verse that has accompanying map data.", hasMaps = true),
        Case("67-everything", "Verse with every attribute set at once.",
            bookmarkCount = 2, noteCount = 1,
            progressMarkBits = (1 shl (AttributeView.PROGRESS_MARK_BITS_START + 1)),
            hasMaps = true,
            highlightColorRgb = 0xffaed581.toInt()),

        // Custom fonts — verifies typeface plumbs through both renderers
        // and that italic / bold synthesis still works.
        Case("70-serif-simple", "Verse rendered with a Serif typeface.",
            typeface = Typeface.SERIF, typefaceLabel = "Serif"),
        Case("71-serif-italic", "@@he said, @9thus says the LORD@7 with serif italics.",
            typeface = Typeface.SERIF, typefaceLabel = "Serif"),
        Case("72-monospace-simple", "Verse rendered with a Monospace typeface.",
            typeface = Typeface.MONOSPACE, typefaceLabel = "Monospace"),
        Case("73-monospace-formatted", "@@@^A monospace verse with @6red words@5 and @9italic@7 mid-line.",
            typeface = Typeface.MONOSPACE, typefaceLabel = "Monospace"),
    )

    // ---- Rendering helpers ----------------------------------------------------------------------

    private fun renderLegacy(case: Case): Bitmap {
        val activity = buildActivity()
        val verseItem = activity.layoutInflater.inflate(R.layout.item_verse, null, false) as VerseItem
        attachAndDoFirstLayout(activity, verseItem)

        val ari = Ari.encode(0, 1, case.verseNumber)
        val highlightInfo = case.toHighlightInfo()

        // Mirror VerseTextHolder.bind for the bits we care about.
        VerseRenderer.render(
            lText = verseItem.lText,
            lVerseNumber = verseItem.lVerseNumber,
            isVerseNumberShown = case.isVerseNumberShown,
            ari = ari,
            text = case.text,
            verseNumberText = case.verseNumber.toString(),
            highlightInfo = highlightInfo,
            checked = case.checked,
            inlineLinkSpanFactory = null,
        )

        // Override the calculated dimensions for this row's typeface (a per-case
        // value, in support of the custom-font test cases).
        val savedTypeface = dims.fontFace
        dims.fontFace = case.typeface ?: Typeface.DEFAULT
        try {
            Appearances.applyTextAppearance(verseItem.lText, case.textSizeMult)
            Appearances.applyVerseNumberAppearance(verseItem.lVerseNumber, case.textSizeMult)
        } finally {
            dims.fontFace = savedTypeface
        }
        if (case.checked) {
            val selectedBg = yuku.afw.storage.Preferences.getInt(
                R.string.pref_selectedVerseBgColor_key,
                R.integer.pref_selectedVerseBgColor_default,
            )
            val color = TextColorUtil.getForCheckedVerse(selectedBg)
            verseItem.lText.setTextColor(color)
            verseItem.lVerseNumber.setTextColor(color)
        }

        val attr = verseItem.attributeView
        attr.setScale(scaleForAttributeView(FONT_SIZE_DP * case.textSizeMult))
        attr.bookmarkCount = case.bookmarkCount
        attr.noteCount = case.noteCount
        attr.progressMarkBits = case.progressMarkBits
        attr.hasMaps = case.hasMaps

        verseItem.checked = case.checked
        verseItem.collapsed = case.text.isEmpty() && !attr.isShowingSomething

        return measureAndDraw(verseItem)
    }

    private fun renderCompose(case: Case): Bitmap {
        val activity = buildActivity()
        val view = VerseItemComposeView(activity)
        attachAndDoFirstLayout(activity, view)

        val ari = Ari.encode(0, 1, case.verseNumber)
        val highlightInfo = case.toHighlightInfo()

        val renderResult = VerseRendererCompose.render(
            isVerseNumberShown = case.isVerseNumberShown,
            ari = ari,
            text = case.text,
            verseNumberText = case.verseNumber.toString(),
            highlightInfo = highlightInfo,
            checked = case.checked,
        )

        val applied = S.applied()
        val state = VerseItemComposeState(
            render = renderResult,
            fontSizeDp = applied.fontSize2dp * case.textSizeMult,
            verseNumberFontSizeDp = applied.fontSize2dp * 0.7f * case.textSizeMult,
            fontColor = applied.fontColor,
            verseNumberColor = applied.verseNumberColor,
            lineSpacingMult = applied.lineSpacingMult,
            typeface = case.typeface ?: applied.fontFace,
            fontBold = applied.fontBold,
            attribute = AttributeState(
                bookmarkCount = case.bookmarkCount,
                noteCount = case.noteCount,
                progressMarkBits = case.progressMarkBits,
                hasMaps = case.hasMaps,
                scale = scaleForAttributeView(applied.fontSize2dp * case.textSizeMult),
                version = null,
                versionId = null,
                ari = ari,
                attributeListener = object : VersesController.AttributeListener() {},
                // No DB available in this unit test; captions stay null. The
                // accessibility path is exercised by separate tests; the
                // snapshot test only cares about the visual output, which
                // doesn't consult this list.
                progressMarkCaptions = List(AttributeView.PROGRESS_MARK_TOTAL_COUNT) { null },
            ),
            onClick = {},
            onInlineLinkClick = { _, _ -> },
            onPinDropped = {},
        )

        view.bind(state)
        view.checked = case.checked
        view.collapsed = case.text.isEmpty() && case.bookmarkCount == 0 && case.noteCount == 0 &&
            case.progressMarkBits == 0 && !case.hasMaps

        // Pump Compose's recompositions and choreographer frames so the first
        // composition produces a TextLayoutResult before we measure.
        idleLoopers()

        return measureAndDraw(view)
    }

    private fun Case.toHighlightInfo(): Highlights.Info? {
        if (highlightColorRgb == null) return null
        val info = Highlights.Info()
        info.colorRgb = highlightColorRgb
        if (partialHighlightStart != null && partialHighlightEnd != null) {
            val partial = Highlights.Info.Partial()
            partial.startOffset = partialHighlightStart
            partial.endOffset = partialHighlightEnd
            partial.hashCode = Highlights.hashCode(stripFormattingForHash(text))
            info.partial = partial
        }
        return info
    }

    /**
     * Compute the partial-highlight hash the same way as the verse-body comparison —
     * see [Highlights.Info.shouldRenderAsPartialForVerseText]. The simple-render
     * path uses the raw text; for formatted text we approximate by removing
     * markers, which is enough to make partial highlights render in test cases
     * where the partial offsets fall in the rendered substring.
     */
    private fun stripFormattingForHash(text: String): String {
        if (text.length < 2 || text[0] != '@' || text[1] != '@') return text
        // Best-effort: drop @@ + tokens. The snapshot covers partial-highlight on
        // a non-formatted verse, so this branch isn't exercised here yet.
        return text
    }

    private fun buildActivity(): AppCompatActivity {
        // `setup()` runs onCreate → onStart → onResume → makes the activity
        // window "visible" (adds the decor view to the WindowManager). The last
        // step is required so AbstractComposeView can resolve a WindowRecomposer.
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        // Theme used by VerseTextView / AttributeView inflation.
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        return activity
    }

    private fun attachAndDoFirstLayout(activity: Activity, view: View) {
        val frame = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            setBackgroundColor(AndroidColor.WHITE)
        }
        activity.setContentView(frame)
        idleLoopers()
    }

    private fun measureAndDraw(view: View): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight.coerceAtLeast(1))

        // Ensure Compose finished its first frame before we draw.
        idleLoopers()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight.coerceAtLeast(1))

        val w = view.measuredWidth.coerceAtLeast(1)
        val h = view.measuredHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)
        view.draw(canvas)
        return bitmap
    }

    private fun idleLoopers() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun scaleForAttributeView(fontSizeDp: Float) = when {
        fontSizeDp >= 13f && fontSizeDp < 24f -> 1f
        fontSizeDp < 8f -> 0.5f
        fontSizeDp < 18f -> 0.75f
        fontSizeDp >= 36f -> 2f
        else -> 1.5f
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    companion object {
        /** R.string.pref_selectedVerseBgColor_default isn't loaded in unit tests; pin a value. */
        private const val SELECTED_BG_COLOR = 0xfffff59d.toInt()
    }
}
