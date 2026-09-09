package yuku.alkitab.base.verses

import android.app.Activity
import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
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
import yuku.alkitab.base.widget.AttributeView
import yuku.alkitab.base.widget.LabeledSplitHandleButton
import yuku.alkitab.base.widget.SplitHandleButton
import yuku.alkitab.base.widget.TwofingerLinearLayout
import yuku.alkitab.debug.R
import yuku.alkitab.model.PericopeBlock
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList

/**
 * Side-by-side screenshot report comparing the legacy RecyclerView verse
 * pipeline with the fully Compose one, at the pane level (whole lists rather
 * than single rows): pericope header with parallels, every attribute icon
 * type, dictionary-mode word underlines, a selected verse, and the complete
 * split layout (vertical and horizontal) around the shared
 * [LabeledSplitHandleButton].
 *
 * Like [VerseItemSideBySideSnapshotTest], this is a report generator for
 * human inspection, not a pixel-diff: it writes PNGs plus a self-contained
 * `index.html` (images embedded as data URIs) under
 * `Alkitab/build/snapshots/reader-side-by-side/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderSideBySideSnapshotTest {

    private val PANE_WIDTH_PX = 360
    private val PANE_HEIGHT_PX = 380
    private val SPLIT_WIDTH_PX = 360
    private val SPLIT_HEIGHT_PX = 640

    private class FakeVerses(private val texts: List<String>) : SingleChapterVerses {
        override val verseCount: Int get() = texts.size
        override fun getVerse(verse_0: Int): String = texts[verse_0]
    }

    private class Case(
        val name: String,
        val description: String,
        val data: VersesDataModel,
        val checkedVerses: List<Int> = emptyList(),
        val dictionaryAris: Set<Int> = emptySet(),
    )

    @Before
    fun setUp() {
        AfwApp.initWithAppContext(ApplicationProvider.getApplicationContext())

        val prefContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        yuku.afw.storage.Preferences.setInt(
            prefContext.getString(R.string.pref_selectedVerseBgColor_key),
            0xfffff59d.toInt(),
        )

        Robolectric.buildContentProvider(FakeDictionaryProvider::class.java).create(
            ProviderInfo().apply { authority = "org.sabda.kamus.provider" }
        )

        val dims = S.CalculatedDimensions().apply {
            fontSize2dp = 17f
            fontFace = Typeface.DEFAULT
            fontBold = Typeface.NORMAL
            fontColor = 0xff202020.toInt()
            fontRedColor = 0xffcc0000.toInt()
            verseNumberColor = 0xff445566.toInt()
            backgroundColor = 0xffffffff.toInt()
            lineSpacingMult = 1.15f
            indentParagraphFirst = 38
            indentParagraphRest = 5
            indentSpacing1 = 22
            indentSpacing2 = 38
            indentSpacing3 = 54
            indentSpacing4 = 70
            indentSpacingExtra = 6
            pericopeSpacingTop = 14
            pericopeSpacingBottom = 4
        }
        S.overrideAppliedDimensions(dims)
    }

    private fun idleLoopers() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun buildActivity(): AppCompatActivity {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        return activity
    }

    private val ariBc = Ari.encode(1, 2, 0)

    private fun ariOfVerse(verse_1: Int) = Ari.encodeWithBc(ariBc, verse_1)

    private fun dataModel(
        verses: List<String>,
        pericopeBeforeVerse_1: Int? = null,
        attributes: (VersesAttributes) -> Unit = {},
    ): VersesDataModel {
        val versesAttributes = VersesAttributes.createEmpty(verses.size)
        attributes(versesAttributes)
        return VersesDataModel(
            ari_bc_ = ariBc,
            verses_ = FakeVerses(verses),
            pericopeBlockCount_ = if (pericopeBeforeVerse_1 != null) 1 else 0,
            pericopeAris_ = if (pericopeBeforeVerse_1 != null) intArrayOf(ariOfVerse(pericopeBeforeVerse_1)) else IntArray(0),
            pericopeBlocks_ = if (pericopeBeforeVerse_1 != null) {
                listOf(PericopeBlock().apply {
                    title = "@@The @9Creation@7 of the World"
                    parallels = arrayOf("@a:${Ari.encode(19, 8, 1)} Psalm 8:1", "John 1:1-3")
                })
            } else {
                emptyList()
            },
            versesAttributes = versesAttributes,
        )
    }

    private fun buildCases(): List<Case> {
        val pericopeData = dataModel(
            verses = listOf(
                "In the beginning God created the heavens and the earth.",
                "Now the earth was formless and empty, and darkness was over the surface of the deep.",
            ),
            pericopeBeforeVerse_1 = 1,
        )

        val attributesData = dataModel(
            verses = listOf(
                "This verse has two bookmarks attached to it.",
                "This verse has three notes attached to it.",
                "This verse has two progress-mark pins on it.",
                "This verse has a maps indicator on it.",
                "This verse has no attributes at all.",
            ),
        ) { attr ->
            attr.bookmarkCountMap_[0] = 2
            attr.noteCountMap_[1] = 3
            attr.progressMarkBitsMap_[2] =
                (1 shl AttributeView.PROGRESS_MARK_BITS_START) or (1 shl (AttributeView.PROGRESS_MARK_BITS_START + 1))
            attr.hasMapsMap_[3] = true
        }

        val dictionaryData = dataModel(
            verses = listOf(
                "In the beginning God created the heavens; created them all.",
                "And everything created was seen to be good.",
            ),
        )

        val selectedData = dataModel(
            verses = listOf(
                "The first verse is not selected.",
                "The second verse is the selected one, shown with the selection background.",
                "The third verse is not selected either.",
            ),
        )

        return listOf(
            Case(
                name = "pericope-and-parallels",
                description = "Pericope header: bold centered title with italic segment, plus underlined tappable parallels (one ari target, one plain reference).",
                data = pericopeData,
            ),
            Case(
                name = "attributes",
                description = "Attribute icons: bookmark with count 2, note with count 3, two progress-mark pins, maps indicator.",
                data = attributesData,
            ),
            Case(
                name = "dictionary-underline",
                description = "Dictionary mode: every occurrence of the word 'created' is underlined per-word.",
                data = dictionaryData,
                dictionaryAris = setOf(ariOfVerse(1), ariOfVerse(2)),
            ),
            Case(
                name = "selected-verse",
                description = "Verse 2 selected (checked): selection background and overridden text color.",
                data = selectedData,
                checkedVerses = listOf(2),
            ),
        )
    }

    private fun splitData() = dataModel(
        verses = List(10) { "Verse ${it + 1} of the split-view chapter, with enough words to wrap onto more than one line." },
        pericopeBeforeVerse_1 = 1,
    )

    // --- Legacy (RecyclerView) rendering ---

    private fun renderLegacyPane(case: Case, width: Int, height: Int): Bitmap {
        val activity = buildActivity()
        val rv = EmptyableRecyclerView(activity)
        val controller = VersesControllerImpl(rv, "legacy")
        configureController(controller, case)
        return measureAndDraw(rv, width, height)
    }

    // --- Compose rendering ---

    private fun renderComposePane(case: Case, width: Int, height: Int): Bitmap {
        val activity = buildActivity()
        val view = VersesComposeView(activity)
        val controller = VersesComposeControllerImpl(view, "compose")
        configureController(controller, case)
        activity.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pumpCompose(view, width, height) { controller.listState.layoutInfo.visibleItemsInfo.isNotEmpty() }
        return measureAndDraw(view, width, height)
    }

    private fun configureController(controller: VersesController, case: Case) {
        controller.versesDataModel = case.data
        controller.versesUiModel = VersesUiModel.EMPTY.copy(
            isVerseNumberShown = true,
            dictionaryModeAris = case.dictionaryAris,
        )
        if (case.checkedVerses.isNotEmpty()) {
            val list = IntArrayList()
            for (v in case.checkedVerses) list.add(v)
            controller.checkVerses(list, callSelectedVersesListener = false)
        }
    }

    private fun pumpCompose(view: View, width: Int, height: Int, composed: () -> Boolean) {
        var rounds = 0
        while (true) {
            idleLoopers()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, width, height)
            idleLoopers()
            rounds++
            if (composed()) return
            assertTrue("compose pane never composed its items", rounds < 10)
        }
    }

    // --- Split layout rendering ---

    private fun renderSplit(vertical: Boolean, compose: Boolean): Bitmap {
        val activity = buildActivity()
        val handleThickness = activity.resources.getDimensionPixelSize(R.dimen.split_handle_thickness)

        val splitRoot = TwofingerLinearLayout(activity)
        splitRoot.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

        val handle = LabeledSplitHandleButton(activity, null)
        handle.orientation = if (vertical) SplitHandleButton.Orientation.vertical else SplitHandleButton.Orientation.horizontal
        handle.setLabel1("▲ KJV")
        handle.setLabel2("TB ▼")

        val case = Case("split", "", splitData())

        val pane0Params: LinearLayout.LayoutParams
        val handleParams: LinearLayout.LayoutParams
        val pane1Params: LinearLayout.LayoutParams
        if (vertical) {
            pane0Params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (SPLIT_HEIGHT_PX - handleThickness) / 2)
            handleParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, handleThickness)
            pane1Params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            pane0Params = LinearLayout.LayoutParams((SPLIT_WIDTH_PX - handleThickness) / 2, ViewGroup.LayoutParams.MATCH_PARENT)
            handleParams = LinearLayout.LayoutParams(handleThickness, ViewGroup.LayoutParams.MATCH_PARENT)
            pane1Params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val composeControllers = mutableListOf<VersesComposeControllerImpl>()
        if (compose) {
            val pane0 = VersesComposeView(activity)
            val pane1 = VersesComposeView(activity)
            val c0 = VersesComposeControllerImpl(pane0, "split0")
            val c1 = VersesComposeControllerImpl(pane1, "split1")
            configureController(c0, case)
            configureController(c1, case)
            composeControllers += c0
            composeControllers += c1
            splitRoot.addView(pane0, pane0Params)
            splitRoot.addView(handle, handleParams)
            splitRoot.addView(pane1, pane1Params)
        } else {
            val pane0 = EmptyableRecyclerView(activity)
            val pane1 = EmptyableRecyclerView(activity)
            configureController(VersesControllerImpl(pane0, "split0"), case)
            configureController(VersesControllerImpl(pane1, "split1"), case)
            splitRoot.addView(pane0, pane0Params)
            splitRoot.addView(handle, handleParams)
            splitRoot.addView(pane1, pane1Params)
        }

        activity.setContentView(splitRoot, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (compose) {
            pumpCompose(splitRoot, SPLIT_WIDTH_PX, SPLIT_HEIGHT_PX) {
                composeControllers.all { it.listState.layoutInfo.visibleItemsInfo.isNotEmpty() }
            }
        } else {
            idleLoopers()
        }
        return measureAndDraw(splitRoot, SPLIT_WIDTH_PX, SPLIT_HEIGHT_PX)
    }

    // --- Bitmap + report plumbing ---

    private fun measureAndDraw(view: View, width: Int, height: Int): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        idleLoopers()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)
        view.draw(canvas)
        return bitmap
    }

    private fun toDataUri(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray())
    }

    private fun savePng(dir: File, name: String, bitmap: Bitmap) {
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun resolveSnapshotDir(): File {
        val override = System.getenv("READER_SIDE_BY_SIDE_SNAPSHOT_DIR")
        return if (override != null) File(override) else File("build/snapshots/reader-side-by-side")
    }

    @Test
    fun `produce side-by-side pane and split-layout snapshots for the legacy and Compose verse pipelines`() {
        val outputDir = resolveSnapshotDir()
        outputDir.mkdirs()

        class Row(val name: String, val description: String, val legacy: Bitmap, val compose: Bitmap)

        val rows = mutableListOf<Row>()

        for (case in buildCases()) {
            FakeDictionaryProvider.lastAnalyzeText = null
            val legacy = renderLegacyPane(case, PANE_WIDTH_PX, PANE_HEIGHT_PX)
            val compose = renderComposePane(case, PANE_WIDTH_PX, PANE_HEIGHT_PX)
            rows += Row(case.name, case.description, legacy, compose)
        }

        rows += Row(
            "split-vertical",
            "Top-bottom split: two panes around the shared LabeledSplitHandleButton (labels + rotate button).",
            renderSplit(vertical = true, compose = false),
            renderSplit(vertical = true, compose = true),
        )
        rows += Row(
            "split-horizontal",
            "Side-by-side split: two panes around the vertical split handle.",
            renderSplit(vertical = false, compose = false),
            renderSplit(vertical = false, compose = true),
        )

        val html = StringBuilder()
        html.append(
            """
            <!doctype html>
            <html><head><meta charset="utf-8"/><title>Reader: legacy vs Compose</title>
            <style>
            body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; margin: 24px; }
            table { border-collapse: collapse; }
            td, th { vertical-align: top; padding: 8px 12px; border-bottom: 1px solid #ddd; }
            th { text-align: left; background: #f4f4f4; position: sticky; top: 0; }
            td.label { width: 280px; }
            td.label .name { font-weight: 600; }
            td.label p { margin: 4px 0 0; color: #444; font-size: 13px; }
            img { display: block; max-width: 400px; image-rendering: pixelated; border: 1px solid #eee; }
            </style></head>
            <body>
            <h1>Reader pipelines: legacy (RecyclerView) vs Verse (Compose)</h1>
            <p>Whole-pane and split-layout renders from identical data. Compare the two columns row by row.</p>
            <table>
              <thead><tr><th>Case</th><th>Legacy</th><th>Compose</th></tr></thead>
              <tbody>
            """.trimIndent()
        )

        for (row in rows) {
            savePng(outputDir, "${row.name}-legacy.png", row.legacy)
            savePng(outputDir, "${row.name}-compose.png", row.compose)
            html.append(
                "<tr>" +
                    "<td class=\"label\"><div class=\"name\">${escapeHtml(row.name)}</div><p>${escapeHtml(row.description)}</p></td>" +
                    "<td><img src=\"${toDataUri(row.legacy)}\"/></td>" +
                    "<td><img src=\"${toDataUri(row.compose)}\"/></td>" +
                    "</tr>\n"
            )
        }

        html.append("</tbody></table></body></html>")

        val indexFile = File(outputDir, "index.html")
        indexFile.writeText(html.toString())

        println("Reader side-by-side report written to: ${indexFile.absolutePath}")
        assertTrue("index.html should exist", indexFile.exists())
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
