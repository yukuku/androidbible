package yuku.alkitab.base.smartsearch

/**
 * Finds whole words belonging to one word family in lowercase text.
 *
 * A hyphenated word is looked up whole first, because the lexicon lists compounds such as
 * `kasih-nya` and `mereka-rekakan` under the family they really belong to. Only a compound the
 * lexicon has never seen is split, so that its parts can still match: `mereka-rekakan` must not
 * match a search for the pronoun `mereka` merely because it starts with it.
 *
 * @param known every form of every family, i.e. the compounds the lexicon has an opinion on.
 */
class FamilyMatcher(val forms: Set<String>, private val known: Set<String>) {
    /**
     * Returns the start of the first matching word in `text[from, to)`, writing its end into
     * `endOut[0]`, or -1 when there is none.
     */
    fun indexIn(text: String, from: Int, to: Int, endOut: IntArray): Int {
        var found = -1
        WordScanner.forEachWord(text, from, to) { s, e ->
            val w = text.substring(s, e)
            if (w in forms) {
                found = s
                endOut[0] = e
                return@forEachWord false
            }
            if (w.indexOf('-') >= 0 && w !in known) {
                WordScanner.forEachPart(text, s, e) { ps, pe ->
                    if (text.substring(ps, pe) in forms) {
                        found = ps
                        endOut[0] = pe
                        false
                    } else {
                        true
                    }
                }
                if (found >= 0) return@forEachWord false
            }
            true
        }
        return found
    }

    /** Every distinct matching word in [text], in order of first appearance. */
    fun matchesIn(text: String): List<String> {
        val res = LinkedHashSet<String>()
        val end = IntArray(1)
        var pos = 0
        while (true) {
            val s = indexIn(text, pos, text.length, end)
            if (s < 0) break
            res += text.substring(s, end[0])
            pos = end[0]
        }
        return res.toList()
    }
}
