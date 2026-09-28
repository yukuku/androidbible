package yuku.alkitab.base.smartsearch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.base.smartsearch.ui.BenchmarkRow
import yuku.alkitab.base.smartsearch.ui.ResultFilter
import yuku.alkitab.base.smartsearch.ui.SearchLabActions
import yuku.alkitab.base.smartsearch.ui.SearchLabContent
import yuku.alkitab.base.smartsearch.ui.SearchLabState
import yuku.alkitab.base.smartsearch.ui.SmartSearchPanel
import yuku.alkitab.base.smartsearch.ui.SmartSearchPanelState
import yuku.alkitab.base.smartsearch.ui.WordLabResult
import yuku.alkitab.base.util.SearchEngine
import yuku.alkitab.base.util.SearchEngineQuery
import yuku.afw.App as AfwApp

/**
 * Renders the smart-search diagnostics panel and the Search Lab, in English and Indonesian, to
 * PNGs under `Alkitab/build/snapshots/smart-search/`, so the layouts can be looked at without a
 * device. Nothing is pixel-compared; the test only checks that the images were written.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "w400dp-h2400dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmartSearchSnapshotTest {
    private val outDir = File("build/snapshots/smart-search").apply { mkdirs() }

    private val version = versionOf(
        mapOf(
            (0 to 1) to listOf(
                "Tuhan menyembuhkan penyakitmu.",
                "Orang sakit itu sembuh.",
                "Sembuhkanlah orang sakit.",
                "Ia disembuhkan dari penyakitnya.",
                "Kesembuhan datang bagi yang sakit.",
            ),
        )
    )

    private val lexicon = SearchLexicon(
        "TB", LexiconOrigin.VERSION,
        familiesOf("sembuh sembuh menyembuhkan disembuhkan kesembuhan penyembuhan sembuhkanlah", "sakit sakit penyakit kesakitan menyakiti penyakitmu penyakitnya"),
        6,
    )

    private val vocabulary = VersionVocabulary.build("preset/in-tb.yes", version)

    private fun report(query: String): SmartSearchReport {
        val books = allBooksOf(version)
        val plan = SmartSearchPlanner(lexicon, vocabulary).plan(query)
        val outcome = SearchEngine.searchByPlan(version, plan, books)
        return SmartSearchReport(
            query = query,
            versionId = "preset/in-tb.yes",
            versionName = "TB",
            selection = LexiconSelection(lexicon, SelectionReason.BUILT_IN, vocabulary),
            terms = outcome.terms,
            result = outcome.result,
            selectMillis = 3,
            vocabularyMillis = 412,
            planMillis = 1,
            searchMillis = 38,
            classic = SearchEngine.searchByGrep(version, SearchEngineQuery(query, books)),
            classicMillis = 21,
        )
    }

    private fun render(name: String, content: @Composable () -> Unit): File {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { BibleAppTheme { content() } }
        shadowOf(Looper.getMainLooper()).idle()

        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val width = activity.resources.displayMetrics.widthPixels
        val maxHeight = activity.resources.displayMetrics.heightPixels
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST),
        )
        root.layout(0, 0, width, root.measuredHeight)
        shadowOf(Looper.getMainLooper()).idle()

        val bitmap = Bitmap.createBitmap(width, root.measuredHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff303030.toInt())
        root.draw(Canvas(bitmap))
        val file = File(outDir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private fun panel(expanded: Boolean, filter: ResultFilter = ResultFilter.ALL): @Composable () -> Unit = {
        val state = SmartSearchPanelState().apply {
            this.report = report("sembuhkan sakit")
            this.expanded = expanded
            this.filter = filter
        }
        SmartSearchPanel(state, onFilterChanged = {}, onOpenLab = {})
    }

    private val noActions = object : SearchLabActions {
        override fun setSmartEnabled(on: Boolean) {}
        override fun setDiagnostics(on: Boolean) {}
        override fun setMode(mode: LexiconMode) {}
        override fun setQuery(q: String) {}
        override fun runBenchmark() {}
    }

    private fun labState(): SearchLabState {
        val primary = SmartSearchPlanner(lexicon, vocabulary).plan("sembuhkan sakit")
        val rules = SmartSearchPlanner(RulesLexiconBuilder.build(vocabulary), vocabulary).plan("sembuhkan sakit")
        return SearchLabState(
            versionId = "preset/in-tb.yes",
            versionName = "Terjemahan Baru",
            versionLocale = "in",
            preparing = false,
            selection = LexiconSelection(lexicon, SelectionReason.BUILT_IN, vocabulary),
            query = "sembuhkan sakit",
            word = WordLabResult("sembuhkan sakit", primary, rules, true, 6, 48, 42, 0),
            benchmark = listOf(
                BenchmarkRow("kasih", 853, 828, 120, 145, 41, 22),
                BenchmarkRow("berkat", 3059, 279, 0, 2780, 38, 19),
                BenchmarkRow("iman", 686, 225, 0, 461, 35, 18),
                BenchmarkRow("sembuhkan sakit", 6, 48, 42, 0, 77, 30),
            ),
        )
    }

    @Test
    fun `renders the diagnostics panel and the Search Lab`() {
        val files = mutableListOf<File>()
        for (lang in listOf("en", "in")) {
            if (lang == "in") RuntimeEnvironment.setQualifiers("+in")
            files += render("panel-collapsed-$lang", panel(expanded = false, filter = ResultFilter.NEW))
            files += render("panel-expanded-$lang", panel(expanded = true))
            files += render("lab-$lang") {
                Column { SearchLabContent(labState(), noActions, onUp = {}, onChangeVersion = {}) }
            }
        }
        for (f in files) assertTrue("${f.name} was not written", f.length() > 0)
    }
}
