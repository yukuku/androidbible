package yuku.alkitab.base.smartsearch

import yuku.alkitab.model.Version

/**
 * Every word a translation uses, with how often. Built once per translation by reading the whole
 * text, then kept in memory.
 *
 * Smart search needs it for two things: a typed word that occurs in the text but belongs to no
 * lexicon family is a family of one and must be matched as itself rather than peeled into some
 * unrelated root; and the rules-only lexicon is derived from it.
 *
 * @property counts occurrences of each lowercase word, hyphenated compounds counted whole.
 * @property partCounts occurrences of each hyphen-separated part, so `orang` is known from
 * `orang-orang` too.
 */
class VersionVocabulary(
    val versionKey: String,
    val counts: Map<String, Int>,
    val partCounts: Map<String, Int>,
    val tokenCount: Int,
    val buildMillis: Long,
) {
    operator fun contains(word: String) = word in counts || word in partCounts

    fun countOf(word: String): Int = counts[word] ?: partCounts[word] ?: 0

    companion object {
        fun build(versionKey: String, version: Version): VersionVocabulary {
            val t0 = System.nanoTime()
            val counts = HashMap<String, Int>(32768)
            val partCounts = HashMap<String, Int>(4096)
            var tokens = 0
            for (book in version.consecutiveBooks) {
                for (chapter1 in 1..book.chapter_count) {
                    val text = version.loadChapterTextLowercasedWithoutSplit(book, chapter1) ?: continue
                    WordScanner.forEachWord(text) { s, e ->
                        val w = text.substring(s, e)
                        counts[w] = (counts[w] ?: 0) + 1
                        tokens++
                        if (w.indexOf('-') >= 0) {
                            WordScanner.forEachPart(w, 0, w.length) { ps, pe ->
                                val p = w.substring(ps, pe)
                                partCounts[p] = (partCounts[p] ?: 0) + 1
                                true
                            }
                        }
                        true
                    }
                }
            }
            return VersionVocabulary(versionKey, counts, partCounts, tokens, (System.nanoTime() - t0) / 1_000_000)
        }
    }
}

/** Keeps the vocabulary of the most recently searched translations, since building one reads the whole Bible. */
object VocabularyCache {
    private const val MAX_ENTRIES = 3
    private val entries = LinkedHashMap<String, VersionVocabulary>(4, 0.75f, true)

    @Synchronized
    fun peek(versionKey: String): VersionVocabulary? = entries[versionKey]

    /** Returns the cached vocabulary or builds it on the calling thread, which must not be the main thread. */
    fun get(versionKey: String, version: Version): VersionVocabulary {
        peek(versionKey)?.let { return it }
        val built = VersionVocabulary.build(versionKey, version)
        synchronized(this) {
            entries[versionKey] = built
            while (entries.size > MAX_ENTRIES) {
                entries.remove(entries.keys.first())
            }
        }
        return built
    }

    @Synchronized
    fun clear() = entries.clear()
}
