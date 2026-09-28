package yuku.alkitab.base.smartsearch

import android.util.SparseBooleanArray
import yuku.alkitab.base.util.SearchEngine
import yuku.alkitab.base.util.SearchEngineQuery
import yuku.alkitab.model.Version
import yuku.alkitab.util.IntArrayList

/**
 * Everything one smart search did, kept so the search screen can explain it.
 *
 * @property classic what the classic letter search returns for the same query, or null when
 * diagnostics are off and it was not run.
 * @property vocabularyMillis time spent reading the whole translation to learn its words, or -1
 * when an earlier search had already done it.
 */
class SmartSearchReport(
    val query: String,
    val versionId: String,
    val versionName: String,
    val selection: LexiconSelection,
    val terms: List<SearchEngine.TermOutcome>,
    val result: IntArrayList,
    val selectMillis: Long,
    val vocabularyMillis: Long,
    val planMillis: Long,
    val searchMillis: Long,
    val classic: IntArrayList?,
    val classicMillis: Long,
) {
    val plan: List<PlannedTerm> get() = terms.map { it.term }

    val widened: Boolean get() = terms.any { it.term.kind == TermKind.FAMILY || it.term.resolution == Resolution.OWN_FAMILY }

    private val resultSet: Set<Int> by lazy { result.toSet() }
    private val classicSet: Set<Int>? by lazy { classic?.toSet() }

    /** Verses smart search found that the letter search missed. */
    val gained: IntArrayList by lazy { filtered(result) { it !in (classicSet ?: resultSet) } }

    /** Verses the letter search found that smart search leaves out, usually a different word containing the letters. */
    val dropped: IntArrayList by lazy { classic?.let { c -> filtered(c) { it !in resultSet } } ?: IntArrayList() }

    fun isGained(ari: Int): Boolean {
        val c = classicSet ?: return false
        return ari !in c
    }

    private fun filtered(src: IntArrayList, keep: (Int) -> Boolean): IntArrayList {
        val res = IntArrayList()
        for (i in 0 until src.size()) {
            val ari = src[i]
            if (keep(ari)) res.add(ari)
        }
        return res
    }

    private fun IntArrayList.toSet(): Set<Int> {
        val s = HashSet<Int>(size() * 2)
        for (i in 0 until size()) s += get(i)
        return s
    }
}

object SmartSearchRunner {
    /**
     * Runs a smart search. Must be called off the main thread: the first search in a translation
     * reads all of it to learn its vocabulary.
     */
    fun run(
        version: Version,
        versionId: String,
        query: SearchEngineQuery,
        withClassicComparison: Boolean,
    ): SmartSearchReport {
        val bookIds = query.bookIds ?: SparseBooleanArray()
        val queryString = query.query_string.orEmpty()

        val hadVocabulary = VocabularyCache.peek(versionId) != null
        val t0 = System.nanoTime()
        val selection = LexiconRepository.select(version, versionId)
        val selectMillis = (System.nanoTime() - t0) / 1_000_000
        val vocabularyMillis = if (!hadVocabulary && selection.vocabulary != null) selection.vocabulary.buildMillis else -1L

        val t1 = System.nanoTime()
        val plan = SmartSearchPlanner(selection.lexicon, selection.vocabulary).plan(queryString)
        val planMillis = (System.nanoTime() - t1) / 1_000_000

        val t2 = System.nanoTime()
        val outcome = SearchEngine.searchByPlan(version, plan, bookIds)
        val searchMillis = (System.nanoTime() - t2) / 1_000_000

        var classic: IntArrayList? = null
        var classicMillis = -1L
        // Without a lexicon nothing was widened, so the classic search would only repeat the same work.
        if (withClassicComparison && selection.lexicon != null) {
            val t3 = System.nanoTime()
            classic = SearchEngine.searchByGrep(version, query)
            classicMillis = (System.nanoTime() - t3) / 1_000_000
        }

        return SmartSearchReport(
            query = queryString,
            versionId = versionId,
            versionName = version.initials,
            selection = selection,
            terms = outcome.terms,
            result = outcome.result,
            selectMillis = selectMillis,
            vocabularyMillis = vocabularyMillis,
            planMillis = planMillis,
            searchMillis = searchMillis,
            classic = classic,
            classicMillis = classicMillis,
        )
    }
}
