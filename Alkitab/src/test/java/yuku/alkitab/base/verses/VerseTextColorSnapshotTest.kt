package yuku.alkitab.base.verses

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.afw.App as AfwApp
import yuku.alkitab.base.S
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.base.util.TextColorUtil
import yuku.alkitab.base.widget.VerseRenderer
import yuku.alkitab.debug.R
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.Ari

/**
 * Fake dictionary analyzer that links every occurrence of a few fixed words, so each rendered
 * verse carries dictionary links both inside and outside a partial highlight.
 */
class WordListDictionaryProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor {
        val text = uri.getQueryParameter("text").orEmpty().lowercase()
        val cursor = MatrixCursor(arrayOf("offset", "len", "key"))
        for (word in WORDS) {
            var index = text.indexOf(word)
            while (index >= 0) {
                cursor.addRow(arrayOf<Any>(index, word.length, "key_$word"))
                index = text.indexOf(word, index + 1)
            }
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    companion object {
        val WORDS = listOf("body", "love")
    }
}

/**
 * Visual matrix of verse text colors: every reading theme, selection color, highlight color
 * and selection state, each rendered through both the legacy [VerseItem] and the Compose
 * [VerseItemComposeView] bind paths exactly as the reader does it. Every verse carries
 * words of Jesus and dictionary-linked words so their colors can be checked in the same row.
 *
 * Per-block PNGs (one per theme and selection color), per-theme contact sheets and an
 * `index.html` land in `Alkitab/build/snapshots/verse-text-color/`. Nothing is pixel-compared:
 * the test asserts only that the report was produced.
 *
 * The whole matrix runs in the shared unit-test JVM, so it reuses one activity per pipeline
 * and recycles every bitmap as soon as it has been composited.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VerseTextColorSnapshotTest {

    private val ROW_WIDTH_PX = 360
    private val COLUMN_GAP_PX = 12
    private val ROW_GAP_PX = 4
    private val LABEL_HEIGHT_PX = 30
    private val BLOCK_HEADER_PX = 40
    private val SHEET_GUTTER_PX = 20
    private val SHEET_HEADER_PX = 48

    private val SAMPLE = "@@The whole body is fitted together perfectly. @6As each part does its work, the body grows@5 and builds itself up in love. @6Love one another.@5"

    private class Theme(val name: String, val background: Int, val fontColor: Int, val fontRedColor: Int, val verseNumberColor: Int)

    private class Selection(val name: String, val rgb: Int)

    private class Highlight(val name: String, val rgb: Int)

    private class Row(val highlight: Highlight?, val partial: Boolean, val checked: Boolean) {
        val label: String
            get() = listOf(
                highlight?.name ?: "no highlight",
                if (highlight == null) null else if (partial) "partial" else "full",
                if (checked) "selected" else "not selected",
            ).filterNotNull().joinToString(" · ")
    }

    private class Pipeline(val controller: ActivityController<AppCompatActivity>, val frame: FrameLayout) {
        val activity: Activity get() = controller.get()
    }

    private val themes = listOf(
        Theme("light-default", 0xfff0f0f0.toInt(), 0xff212121.toInt(), 0xffb71c1c.toInt(), 0xff828282.toInt()),
        Theme("white", 0xffffffff.toInt(), 0xff000000.toInt(), 0xffb71c1c.toInt(), 0xff828282.toInt()),
        Theme("sepia", 0xfffff8e7.toInt(), 0xff3b3021.toInt(), 0xff8b2c1c.toInt(), 0xff8a7a5c.toInt()),
        Theme("dark-grey", 0xff303030.toInt(), 0xffdddddd.toInt(), 0xffef9a9a.toInt(), 0xff9e9e9e.toInt()),
        Theme("night-default", 0xff000000.toInt(), 0xffaeaeae.toInt(), 0xffa25c5c.toInt(), 0xff6c6cb3.toInt()),
    )

    private val selections = listOf(
        Selection("blue-default", 0x0277bd),
        Selection("light-yellow", 0xfff59d),
        Selection("green", 0x4caf50),
        Selection("red", 0xe53935),
        Selection("dark", 0x37474f),
    )

    private val highlights = listOf(
        Highlight("yellow", 0xffff00),
        Highlight("green", 0x00ff00),
        Highlight("azure", 0x0080ff),
        Highlight("rose", 0xff0080),
    )

    private val rows: List<Row> = buildList {
        add(Row(null, partial = false, checked = false))
        add(Row(null, partial = false, checked = true))
        for (highlight in highlights) {
            add(Row(highlight, partial = false, checked = false))
            add(Row(highlight, partial = false, checked = true))
            add(Row(highlight, partial = true, checked = true))
        }
    }

    private class FakeVerses(private val texts: List<String>) : SingleChapterVerses {
        override val verseCount: Int get() = texts.size
        override fun getVerse(verse_0: Int): String = texts[verse_0]
    }

    private lateinit var context: Context
    private lateinit var dims: S.CalculatedDimensions
    private lateinit var verseBody: String
    private var partialStart = 0
    private var partialEnd = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AfwApp.initWithAppContext(context)

        Robolectric.buildContentProvider(WordListDictionaryProvider::class.java).create(
            ProviderInfo().apply { authority = "org.sabda.kamus.provider" }
        )

        dims = S.CalculatedDimensions().apply {
            fontSize2dp = 17f
            fontFace = Typeface.DEFAULT
            fontBold = Typeface.NORMAL
            lineSpacingMult = 1.15f
            indentParagraphFirst = 38
            indentParagraphRest = 5
            indentSpacing1 = 22
            indentSpacing2 = 38
            indentSpacing3 = 54
            indentSpacing4 = 70
            indentSpacingExtra = 6
        }
        S.overrideAppliedDimensions(dims)

        val ftr = VerseRenderer.FormattedTextResult()
        VerseRenderer.render(ari = Ari.encode(48, 4, 16), text = SAMPLE, ftr = ftr)
        verseBody = ftr.result.toString()
        partialStart = verseBody.indexOf("body is fitted")
        partialEnd = verseBody.indexOf("does its work,") + "does its work,".length
        assertTrue(partialStart in 0 until partialEnd)
    }

    @Test
    fun `render every theme, selection color, highlight color and selection state through both verse pipelines for visual review`() {
        val outputDir = resolveSnapshotDir()
        outputDir.mkdirs()

        val html = StringBuilder()
        html.append(
            """
            <!doctype html>
            <html><head><meta charset="utf-8"/><title>Verse text color across themes, selections and highlights</title>
            <style>
            body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; margin: 24px; background: #fafafa; }
            h2 { margin-top: 36px; }
            pre { color: #555; font-size: 12px; }
            img { display: block; max-width: 100%; border: 1px solid #ccc; }
            ul { font-size: 13px; }
            </style></head>
            <body>
            <h1>Verse text color: theme × selection color × highlight × selection state</h1>
            <p>Each block is one reading theme and one selection color. Rows go from no highlight through every
            highlight color, first not selected, then selected with a full highlight, then selected with a partial
            highlight. The left column is the legacy VerseItem, the right the Compose VerseItemComposeView. Every verse
            has words of Jesus (red text when not selected) and dictionary-linked words (underlined: "body", "love").</p>
            <p>The label above each row gives the text color used on the highlight band and its contrast ratio against
            what the band actually paints, then the color used on the rest of the verse and its contrast against the
            selection overlay (or the page when not selected).</p>
            """.trimIndent()
        )

        val legacy = buildPipeline()
        val compose = buildPipeline()
        try {
            for (theme in themes) {
                applyTheme(theme, legacy, compose)
                val blocks = mutableListOf<Pair<Selection, Bitmap>>()
                for (selection in selections) {
                    pinSelection(selection)
                    val block = renderBlock(theme, selection, legacy, compose)
                    blocks += selection to block
                    write(block, File(outputDir, "${theme.name}--${selection.name}.png"))
                }
                val sheetFile = File(outputDir, "${theme.name}.png")
                val sheet = renderThemeSheet(theme, blocks)
                write(sheet, sheetFile)
                sheet.recycle()
                for ((_, block) in blocks) block.recycle()

                html.append("<h2>${theme.name}</h2>\n")
                html.append(
                    "<pre>page #%06x   text #%06x   words of Jesus #%06x   verse number #%06x</pre>\n".format(
                        theme.background and 0xffffff, theme.fontColor and 0xffffff, theme.fontRedColor and 0xffffff, theme.verseNumberColor and 0xffffff,
                    )
                )
                html.append("<img src=\"${sheetFile.name}\"/>\n<ul>\n")
                for ((selection, _) in blocks) {
                    html.append("<li><a href=\"${theme.name}--${selection.name}.png\">${selection.name} block</a></li>\n")
                }
                html.append("</ul>\n")
            }
        } finally {
            legacy.controller.pause().stop().destroy()
            compose.controller.pause().stop().destroy()
        }

        html.append("</body></html>")
        val indexFile = File(outputDir, "index.html")
        indexFile.writeText(html.toString())

        println("Verse text color report written to: ${indexFile.absolutePath}")
        assertTrue("index.html should exist", indexFile.exists())
    }

    private fun applyTheme(theme: Theme, vararg pipelines: Pipeline) {
        dims.backgroundColor = theme.background
        dims.fontColor = theme.fontColor
        dims.fontRedColor = theme.fontRedColor
        dims.verseNumberColor = theme.verseNumberColor
        for (pipeline in pipelines) pipeline.frame.setBackgroundColor(theme.background)
    }

    private fun pinSelection(selection: Selection) {
        yuku.afw.storage.Preferences.setInt(context.getString(R.string.pref_selectedVerseBgColor_key), 0xff000000.toInt() or selection.rgb)
    }

    private fun renderBlock(theme: Theme, selection: Selection, legacy: Pipeline, compose: Pipeline): Bitmap {
        val data = buildData()
        val ui = VersesUiModel.EMPTY.copy(
            isVerseNumberShown = true,
            dictionaryModeAris = rows.indices.map { Ari.encodeWithBc(data.ari_bc_, it + 1) }.toSet(),
        )

        val rendered = rows.mapIndexed { index, row ->
            Triple(row, renderLegacy(legacy, data, ui, index, row.checked), renderCompose(compose, data, ui, index, row.checked))
        }

        val blockWidth = ROW_WIDTH_PX * 2 + COLUMN_GAP_PX
        val blockHeight = BLOCK_HEADER_PX + rendered.sumOf { (_, left, right) -> LABEL_HEIGHT_PX + maxOf(left.height, right.height) + ROW_GAP_PX }
        val bitmap = Bitmap.createBitmap(blockWidth, blockHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(theme.background)

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.fontColor
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.fontColor
            textSize = 11f
            typeface = Typeface.SANS_SERIF
        }

        canvas.drawText("selection ${selection.name} #%06x".format(selection.rgb), 4f, 16f, title)
        canvas.drawText("legacy VerseItem", 4f, 32f, caption)
        canvas.drawText("Compose VerseItemComposeView", (ROW_WIDTH_PX + COLUMN_GAP_PX + 4).toFloat(), 32f, caption)
        title.textSize = 11f

        var y = BLOCK_HEADER_PX
        for ((row, left, right) in rendered) {
            canvas.drawText(row.label, 4f, (y + 11).toFloat(), title)
            canvas.drawText(describeColors(theme, selection, row), 4f, (y + 24).toFloat(), caption)
            y += LABEL_HEIGHT_PX
            canvas.drawBitmap(left, 0f, y.toFloat(), null)
            canvas.drawBitmap(right, (ROW_WIDTH_PX + COLUMN_GAP_PX).toFloat(), y.toFloat(), null)
            y += maxOf(left.height, right.height) + ROW_GAP_PX
            left.recycle()
            right.recycle()
        }

        return bitmap
    }

    private fun describeColors(theme: Theme, selection: Selection, row: Row): String {
        val selectionArgb = 0xff000000.toInt() or selection.rgb
        val overlay = ColorUtils.setAlphaComponent(selectionArgb, TextColorUtil.CHECKED_VERSE_OVERLAY_ALPHA)
        val restBackground = if (row.checked) ColorUtils.compositeColors(overlay, theme.background) else theme.background
        val restText = if (row.checked) TextColorUtil.getForCheckedVerse(selectionArgb) else theme.fontColor
        val rest = "rest #%06x %.1f:1".format(restText and 0xffffff, ColorUtils.calculateContrast(restText, restBackground))

        val highlight = row.highlight ?: return rest
        val band = Highlights.blendOver(highlight.rgb, theme.background)
        val painted = ColorUtils.compositeColors(band, restBackground)
        val bandText = if (row.checked) {
            TextColorUtil.getForCheckedVerseHighlight(theme.fontColor, selectionArgb, theme.background, band)
        } else {
            theme.fontColor
        }
        return "band a=%02x text #%06x %.1f:1   %s".format((band ushr 24) and 0xff, bandText and 0xffffff, ColorUtils.calculateContrast(bandText, painted), rest)
    }

    private fun buildData(): VersesDataModel {
        val attributes = VersesAttributes.createEmpty(rows.size)
        for ((index, row) in rows.withIndex()) {
            val highlight = row.highlight ?: continue
            attributes.highlightInfoMap_[index] = Highlights.Info().apply {
                colorRgb = highlight.rgb
                partial = if (row.partial) {
                    Highlights.Info.Partial().apply {
                        hashCode = Highlights.hashCode(verseBody)
                        startOffset = partialStart
                        endOffset = partialEnd
                    }
                } else {
                    null
                }
            }
        }
        return VersesDataModel(
            ari_bc_ = Ari.encode(48, 4, 0),
            verses_ = FakeVerses(List(rows.size) { SAMPLE }),
            versesAttributes = attributes,
        )
    }

    private fun renderLegacy(pipeline: Pipeline, data: VersesDataModel, ui: VersesUiModel, index: Int, checked: Boolean): Bitmap {
        val item = pipeline.activity.layoutInflater.inflate(R.layout.item_verse, pipeline.frame, false) as VerseItem
        pipeline.frame.addView(item, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        idleLoopers()
        VerseTextHolder(item).bind(data, ui, VersesListeners.EMPTY, Attention(), AudioHighlight(), checked, {}, index)
        val bitmap = measureAndDraw(item, dims.backgroundColor)
        pipeline.frame.removeView(item)
        return bitmap
    }

    private fun renderCompose(pipeline: Pipeline, data: VersesDataModel, ui: VersesUiModel, index: Int, checked: Boolean): Bitmap {
        val view = VerseItemComposeView(pipeline.activity)
        pipeline.frame.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        idleLoopers()
        val state = buildVerseItemComposeState(
            context = pipeline.activity,
            data = data,
            ui = ui,
            listeners = VersesListeners.EMPTY,
            index = index,
            checked = checked,
            currentPosition = { index },
            toggleChecked = {},
            inlineLinkViewProvider = { view },
        )
        view.bind(state)
        view.checked = checked
        idleLoopers()
        val bitmap = measureAndDraw(view, dims.backgroundColor)
        pipeline.frame.removeView(view)
        idleLoopers()
        return bitmap
    }

    private fun renderThemeSheet(theme: Theme, blocks: List<Pair<Selection, Bitmap>>): Bitmap {
        val blockWidth = blocks.maxOf { it.second.width }
        val blockHeight = blocks.maxOf { it.second.height }
        val width = SHEET_GUTTER_PX + blocks.size * (blockWidth + SHEET_GUTTER_PX)
        val height = SHEET_HEADER_PX + blockHeight + SHEET_GUTTER_PX

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xfffafafa.toInt())

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xff111111.toInt()
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xff555555.toInt()
            textSize = 13f
            typeface = Typeface.SANS_SERIF
        }
        canvas.drawText(theme.name, SHEET_GUTTER_PX.toFloat(), 22f, title)
        canvas.drawText(
            "page #%06x   text #%06x   words of Jesus #%06x   verse number #%06x".format(
                theme.background and 0xffffff, theme.fontColor and 0xffffff, theme.fontRedColor and 0xffffff, theme.verseNumberColor and 0xffffff,
            ),
            SHEET_GUTTER_PX.toFloat(),
            40f,
            caption,
        )

        var x = SHEET_GUTTER_PX
        for ((_, block) in blocks) {
            canvas.drawBitmap(block, x.toFloat(), SHEET_HEADER_PX.toFloat(), null)
            x += blockWidth + SHEET_GUTTER_PX
        }
        return bitmap
    }

    private fun buildPipeline(): Pipeline {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        val frame = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        activity.setContentView(frame)
        idleLoopers()
        return Pipeline(controller, frame)
    }

    private fun measureAndDraw(view: View, background: Int): Bitmap {
        repeat(2) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(ROW_WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight.coerceAtLeast(1))
            idleLoopers()
        }

        val bitmap = Bitmap.createBitmap(view.measuredWidth.coerceAtLeast(1), view.measuredHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        view.draw(canvas)
        return bitmap
    }

    private fun idleLoopers() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun resolveSnapshotDir(): File {
        val override = System.getenv("VERSE_TEXT_COLOR_SNAPSHOT_DIR")
        if (!override.isNullOrBlank()) return File(override)
        val moduleDir = File(System.getProperty("user.dir") ?: ".")
        return File(moduleDir, "build/snapshots/verse-text-color")
    }

    private fun write(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
