package yuku.alkitab.base.search.theme.rank

import java.util.concurrent.ConcurrentHashMap
import yuku.alkitab.base.util.FormattedVerseText
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari

/** Process cache; rebuilding happens off-main and never stores a second verse-text corpus. */
object Bm25IndexCache {
    private val cache = ConcurrentHashMap<String, Bm25Index>()

    fun getOrBuild(version: Version, versionId: String, normalizer: QueryNormalizer): Bm25Index =
        cache[versionId] ?: build(version, normalizer).also { cache[versionId] = it }

    fun invalidate(versionId: String) {
        cache.remove(versionId)
    }

    private fun build(version: Version, normalizer: QueryNormalizer): Bm25Index {
        val documents = HashMap<Int, String>(32_000)
        version.consecutiveBooks.forEach { book ->
            book.verse_counts.forEachIndexed { chapter0, verseCount ->
                for (verse1 in 1..verseCount) {
                    val ari = Ari.encode(book.bookId, chapter0 + 1, verse1)
                    val text = FormattedVerseText.removeSpecialCodes(version.loadVerseText(ari), true)
                    if (!text.isNullOrBlank()) documents[ari] = text
                }
            }
        }
        return Bm25Index.fromDocuments(documents, normalizer)
    }
}
