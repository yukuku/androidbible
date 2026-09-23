package yuku.alkitab.base.smartsearch.ui

import android.util.SparseBooleanArray
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.settings.ExperimentalFlags
import yuku.alkitab.base.smartsearch.LexiconMode
import yuku.alkitab.base.smartsearch.LexiconOrigin
import yuku.alkitab.base.smartsearch.LexiconRepository
import yuku.alkitab.base.smartsearch.LexiconSelection
import yuku.alkitab.base.smartsearch.PlannedTerm
import yuku.alkitab.base.smartsearch.SearchLab
import yuku.alkitab.base.smartsearch.SearchLexicon
import yuku.alkitab.base.smartsearch.SmartSearchPlanner
import yuku.alkitab.base.smartsearch.VocabularyCache
import yuku.alkitab.base.util.SearchEngine
import yuku.alkitab.base.util.SearchEngineQuery
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version

/** Searches known to go wrong with letter matching, run by the self-check. */
private val BENCHMARK_QUERIES = listOf(
    "kasih", "berkat", "iman", "alam", "pada", "kata",
    "beriman", "mengasihi", "menyembah", "mengampuni",
    "sembuhkan sakit", "sembah berhala", "\"kota daud\"", "yerus",
)

/** How one query would be searched, with each available lexicon, and what it finds. */
class WordLabResult(
    val query: String,
    val primary: List<PlannedTerm>,
    val alternative: List<PlannedTerm>?,
    val alternativeIsRules: Boolean,
    val letterCount: Int,
    val smartCount: Int,
    val gained: Int,
    val dropped: Int,
)

class BenchmarkRow(val query: String, val letters: Int, val smart: Int, val gained: Int, val dropped: Int, val smartMillis: Long, val letterMillis: Long)

data class SearchLabState(
    val versionId: String = "",
    val versionName: String = "",
    val versionLocale: String? = null,
    val smartEnabled: Boolean = true,
    val diagnostics: Boolean = true,
    val mode: LexiconMode = LexiconMode.AUTO,
    val preparing: Boolean = true,
    val selection: LexiconSelection? = null,
    val query: String = "",
    val word: WordLabResult? = null,
    val wordBusy: Boolean = false,
    val benchmark: List<BenchmarkRow>? = null,
    val benchmarkRunning: Boolean = false,
)

class SearchLabViewModel : ViewModel(), SearchLabActions {
    private val _state = MutableStateFlow(
        SearchLabState(
            smartEnabled = ExperimentalFlags.smartSearch(),
            diagnostics = ExperimentalFlags.smartSearchDiagnostics(),
            mode = LexiconMode.current(),
        )
    )
    val state: StateFlow<SearchLabState> = _state.asStateFlow()

    private var version: Version? = null
    private var wordJob: Job? = null

    fun setVersion(versionId: String, version: Version) {
        if (this.version === version && _state.value.versionId == versionId) return
        this.version = version
        _state.update {
            it.copy(
                versionId = versionId,
                versionName = version.longName ?: version.shortName.orEmpty(),
                versionLocale = version.locale,
                benchmark = null,
                word = null,
            )
        }
        refreshSelection()
    }

    override fun setSmartEnabled(on: Boolean) {
        Preferences.setBoolean(App.context.getString(R.string.pref_smartSearch_key), on)
        SearchLab.changed()
        _state.update { it.copy(smartEnabled = on) }
    }

    override fun setDiagnostics(on: Boolean) {
        Preferences.setBoolean(App.context.getString(R.string.pref_smartSearchDiagnostics_key), on)
        SearchLab.changed()
        _state.update { it.copy(diagnostics = on) }
    }

    override fun setMode(mode: LexiconMode) {
        LexiconMode.set(mode)
        SearchLab.changed()
        _state.update { it.copy(mode = mode, benchmark = null) }
        refreshSelection()
    }

    override fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        evaluateWord(debounce = true)
    }

    private fun refreshSelection() {
        val version = version ?: return
        val versionId = _state.value.versionId
        val mode = _state.value.mode
        _state.update { it.copy(preparing = VocabularyCache.peek(versionId) == null) }
        viewModelScope.launch {
            val selection = withContext(Dispatchers.Default) {
                LexiconRepository.selectIgnoringSwitch(version, versionId, mode)
            }
            _state.update { it.copy(preparing = false, selection = selection) }
            evaluateWord(debounce = false)
        }
    }

    private fun evaluateWord(debounce: Boolean) {
        wordJob?.cancel()
        val query = _state.value.query.trim()
        val selection = _state.value.selection
        val version = version
        if (query.isEmpty() || selection == null || version == null) {
            _state.update { it.copy(word = null, wordBusy = false) }
            return
        }
        wordJob = viewModelScope.launch {
            if (debounce) delay(350)
            _state.update { it.copy(wordBusy = true) }
            val result = withContext(Dispatchers.Default) { computeWord(query, selection, version) }
            _state.update { it.copy(word = result, wordBusy = false) }
        }
    }

    private fun alternativeLexicon(selection: LexiconSelection, version: Version): Pair<SearchLexicon, Boolean>? {
        val primary = selection.lexicon ?: return null
        val vocab = selection.vocabulary ?: return null
        return if (primary.origin == LexiconOrigin.RULES) {
            LexiconRepository.builtInFor(version, _state.value.versionId)?.let { it to false }
        } else if (SearchLexicon.sameLanguage("id", version.locale)) {
            LexiconRepository.rulesFor(vocab) to true
        } else {
            null
        }
    }

    private fun computeWord(query: String, selection: LexiconSelection, version: Version): WordLabResult {
        val primary = SmartSearchPlanner(selection.lexicon, selection.vocabulary).plan(query)
        val alt = alternativeLexicon(selection, version)
        val alternative = alt?.let { SmartSearchPlanner(it.first, selection.vocabulary).plan(query) }
        val counts = compare(query, primary, version)
        return WordLabResult(query, primary, alternative, alt?.second ?: false, counts.letters, counts.smart, counts.gained, counts.dropped)
    }

    private fun allBooks(version: Version) = SparseBooleanArray().also { a ->
        for (b in version.consecutiveBooks) a.put(b.bookId, true)
    }

    private fun compare(query: String, plan: List<PlannedTerm>, version: Version): BenchmarkRow {
        val books = allBooks(version)
        val t0 = System.nanoTime()
        val smart = SearchEngine.searchByPlan(version, plan, books).result
        val t1 = System.nanoTime()
        val letters = SearchEngine.searchByGrep(version, SearchEngineQuery(query, books))
        val t2 = System.nanoTime()

        val smartSet = HashSet<Int>()
        for (i in 0 until smart.size()) smartSet += smart[i]
        val letterSet = HashSet<Int>()
        for (i in 0 until letters.size()) letterSet += letters[i]

        return BenchmarkRow(
            query = query,
            letters = letters.size(),
            smart = smart.size(),
            gained = smartSet.count { it !in letterSet },
            dropped = letterSet.count { it !in smartSet },
            smartMillis = (t1 - t0) / 1_000_000,
            letterMillis = (t2 - t1) / 1_000_000,
        )
    }

    override fun runBenchmark() {
        val selection = _state.value.selection ?: return
        val version = version ?: return
        _state.update { it.copy(benchmarkRunning = true, benchmark = emptyList()) }
        viewModelScope.launch {
            val planner = SmartSearchPlanner(selection.lexicon, selection.vocabulary)
            for (q in BENCHMARK_QUERIES) {
                val row = withContext(Dispatchers.Default) { compare(q, planner.plan(q), version) }
                _state.update { it.copy(benchmark = it.benchmark.orEmpty() + row) }
            }
            _state.update { it.copy(benchmarkRunning = false) }
        }
    }
}
