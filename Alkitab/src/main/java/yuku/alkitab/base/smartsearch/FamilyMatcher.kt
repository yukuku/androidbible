package yuku.alkitab.base.smartsearch

/**
 * Matches whole words in lowercase text. Known hyphenated forms stay intact, so
 * `mereka-rekakan` matches `reka`, not `mereka`. Unknown compounds may match by part.
 *
 * @param known all forms listed in the lexicon.
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
