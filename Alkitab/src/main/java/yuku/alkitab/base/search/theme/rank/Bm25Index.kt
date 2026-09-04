package yuku.alkitab.base.search.theme.rank

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
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

    internal fun writeTo(file: File) {
        DataOutputStream(BufferedOutputStream(file.outputStream())).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(FORMAT_VERSION)
            output.writeInt(documentLengths.size)
            documentLengths.toSortedMap().forEach { (ari, length) ->
                output.writeInt(ari)
                output.writeInt(length)
            }
            output.writeInt(postings.size)
            postings.toSortedMap().forEach { (token, tokenPostings) ->
                val bytes = token.toByteArray(Charsets.UTF_8)
                require(bytes.size <= MAX_TOKEN_BYTES) { "BM25 token is too long" }
                output.writeInt(bytes.size)
                output.write(bytes)
                output.writeInt(tokenPostings.size)
                tokenPostings.sortedBy(Posting::ari).forEach { posting ->
                    output.writeInt(posting.ari)
                    output.writeInt(posting.frequency)
                }
            }
        }
    }

    companion object {
        private const val K1 = 1.2
        private const val B = 0.75
        private const val MAGIC = 0x59424d32 // YBM2
        private const val FORMAT_VERSION = 1
        private const val MAX_DOCUMENTS = 100_000
        private const val MAX_TOKENS = 1_000_000
        private const val MAX_POSTINGS_PER_TOKEN = 100_000
        private const val MAX_TOKEN_BYTES = 1_048_576

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

        internal fun readFrom(file: File): Bm25Index {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                require(input.readInt() == MAGIC) { "Not a BM25 cache" }
                require(input.readInt() == FORMAT_VERSION) { "Unsupported BM25 cache version" }

                val documentCount = input.readBoundedCount(MAX_DOCUMENTS, "document")
                val lengths = HashMap<Int, Int>(documentCount)
                repeat(documentCount) {
                    val ari = input.readInt()
                    val length = input.readInt()
                    require(length > 0) { "Invalid BM25 document length" }
                    require(lengths.put(ari, length) == null) { "Duplicate BM25 document" }
                }

                val tokenCount = input.readBoundedCount(MAX_TOKENS, "token")
                val restoredPostings = HashMap<String, List<Posting>>(tokenCount)
                repeat(tokenCount) {
                    val byteCount = input.readBoundedCount(MAX_TOKEN_BYTES, "token byte")
                    val bytes = ByteArray(byteCount)
                    input.readFully(bytes)
                    val token = bytes.toString(Charsets.UTF_8)
                    require(token.isNotEmpty()) { "Empty BM25 token" }
                    val postingCount = input.readBoundedCount(MAX_POSTINGS_PER_TOKEN, "posting")
                    val tokenPostings = ArrayList<Posting>(postingCount)
                    repeat(postingCount) {
                        val ari = input.readInt()
                        val frequency = input.readInt()
                        require(frequency > 0 && lengths.containsKey(ari)) { "Invalid BM25 posting" }
                        tokenPostings += Posting(ari, frequency)
                    }
                    require(restoredPostings.put(token, tokenPostings) == null) { "Duplicate BM25 token" }
                }
                require(input.read() == -1) { "Trailing BM25 cache data" }

                return Bm25Index(
                    postings = restoredPostings,
                    documentLengths = lengths,
                    averageLength = lengths.values.average().takeIf { it.isFinite() && it > 0 } ?: 1.0,
                )
            }
        }

        private fun DataInputStream.readBoundedCount(maximum: Int, label: String): Int =
            readInt().also { require(it in 0..maximum) { "Invalid BM25 $label count" } }
    }
}
