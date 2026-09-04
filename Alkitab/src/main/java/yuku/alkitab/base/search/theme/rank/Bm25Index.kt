package yuku.alkitab.base.search.theme.rank

import kotlin.math.ln
import yuku.alkitab.base.search.theme.index.RankedAri
import yuku.alkitab.util.Ari

class Bm25Index private constructor(
    private val postings: Map<String, List<Posting>>,
    private val documentLengths: Map<Int, Int>,
    private val averageLength: Double,
) {
    private data class Posting(val ari: Int, val frequency: Int)

    fun search(tokens: List<String>, allowedBooks: BooleanArray, k: Int): List<RankedAri> {
        if (tokens.isEmpty() || k <= 0 || documentLengths.isEmpty()) return emptyList()
        val scores = HashMap<Int, Double>()
        tokens.distinct().forEach { token ->
            val list = postings[token] ?: return@forEach
            val idf = ln(1.0 + (documentLengths.size - list.size + 0.5) / (list.size + 0.5))
            list.forEach { posting ->
                val book = Ari.toBook(posting.ari)
                if (book !in allowedBooks.indices || !allowedBooks[book]) return@forEach
                val length = documentLengths.getValue(posting.ari)
                val numerator = posting.frequency * (K1 + 1.0)
                val denominator = posting.frequency + K1 * (1.0 - B + B * length / averageLength)
                scores[posting.ari] = (scores[posting.ari] ?: 0.0) + idf * numerator / denominator
            }
        }
        return scores.entries.asSequence()
            .map { RankedAri(it.key, it.value.toFloat()) }
            .sortedWith(compareByDescending<RankedAri> { it.score }.thenBy { it.ari })
            .take(k)
            .toList()
    }

    companion object {
        private const val K1 = 1.2
        private const val B = 0.75

        fun fromDocuments(documents: Map<Int, String>, normalizer: QueryNormalizer): Bm25Index {
            val postings = HashMap<String, MutableList<Posting>>()
            val lengths = HashMap<Int, Int>()
            documents.forEach { (ari, text) ->
                val tokens = normalizer.tokens(text)
                if (tokens.isEmpty()) return@forEach
                lengths[ari] = tokens.size
                tokens.groupingBy { it }.eachCount().forEach { (token, count) ->
                    postings.getOrPut(token, ::ArrayList) += Posting(ari, count)
                }
            }
            return Bm25Index(
                postings = postings,
                documentLengths = lengths,
                averageLength = lengths.values.average().takeIf { it.isFinite() && it > 0 } ?: 1.0,
            )
        }
    }
}
