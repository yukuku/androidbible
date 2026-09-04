package yuku.alkitab.base.search.theme.rank

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import yuku.alkitab.base.util.FormattedVerseText
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari

/** Memory + private-disk cache containing token postings, never a second verse-text corpus. */
object Bm25IndexCache {
    private const val CACHE_SCHEMA = 1
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val lock = Any()

    fun getOrBuild(
        cacheRoot: File,
        version: Version,
        versionId: String,
        versionRevision: Long,
        normalizer: QueryNormalizer,
    ): Bm25Index = getOrBuild(cacheRoot, versionId, versionRevision, normalizer) {
        build(version, normalizer)
    }

    internal fun getOrBuild(
        cacheRoot: File,
        versionId: String,
        versionRevision: Long,
        normalizer: QueryNormalizer,
        builder: () -> Bm25Index,
    ): Bm25Index {
        val key = "$CACHE_SCHEMA\n$versionId\n$versionRevision\n${normalizer.fingerprint}".sha256()
        cache[key]?.let { return it.index }
        return synchronized(lock) {
            cache[key]?.index ?: loadOrBuild(cacheRoot, key, builder).also { index ->
                cache[key] = CacheEntry(versionId, index)
            }
        }
    }

    fun invalidate(versionId: String) {
        cache.filterValues { it.versionId == versionId }.keys.forEach(cache::remove)
    }

    internal fun clearMemoryForTest() = cache.clear()

    private fun loadOrBuild(cacheRoot: File, key: String, builder: () -> Bm25Index): Bm25Index {
        val directory = File(cacheRoot, "offline_search/bm25").apply { mkdirs() }
        val destination = File(directory, "$key.bm25")
        if (destination.isFile) {
            runCatching { return Bm25Index.readFrom(destination) }
            destination.delete()
        }

        val index = builder()
        runCatching {
            val temporary = File.createTempFile("$key-", ".part", directory)
            try {
                index.writeTo(temporary)
                check(temporary.renameTo(destination)) { "Unable to publish BM25 cache" }
            } finally {
                temporary.delete()
            }
        }
        return index
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

    private data class CacheEntry(val versionId: String, val index: Bm25Index)

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
