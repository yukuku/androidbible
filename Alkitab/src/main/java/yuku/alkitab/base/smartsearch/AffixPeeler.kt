package yuku.alkitab.base.smartsearch

/**
 * One affix taken off a word while looking for a form the lexicon knows.
 *
 * @property restored the root-initial letter a nasal prefix had swallowed and that this step put
 * back, e.g. `k` when `mengasihi` becomes `kasihi`. Empty when nothing was restored.
 */
data class PeelStep(
    val from: String,
    val to: String,
    val affix: String,
    val isPrefix: Boolean,
    val restored: String,
) {
    /** How the step reads in the diagnostics, e.g. `-kan` or `meng- (+k)`. */
    val label: String
        get() = when {
            !isPrefix -> "-$affix"
            restored.isEmpty() -> "$affix-"
            else -> "$affix- (+$restored)"
        }
}

/**
 * Peels Indonesian affixes off a word the reader typed until what is left is a form the lexicon
 * knows.
 *
 * The lexicon only holds forms that occur in the translation it was built from, while a reader can
 * type anything: `sembuhkan`, the plain imperative "heal", occurs nowhere in Terjemahan Baru. The
 * peeling branches instead of committing to the first ending that fits, because `amukan` is
 * `amuk` + `-an` and not `amu` + `-kan`; each depth is searched completely before the next one, so
 * the shallowest landing wins.
 *
 * Nothing here scores or guesses. A peel counts only when it lands on something [isKnown] accepts.
 */
object AffixPeeler {
    const val MAX_DEPTH = 4
    const val MIN_STEM = 3

    private val SUFFIXES = arrayOf("lah", "kah", "pun", "nya", "ku", "mu", "kan", "an", "i")

    /** Longest first, so `memper` is tried before `mem` and `meng` before `men`. */
    private val PREFIXES = arrayOf(
        "memper", "diper",
        "meng", "peng",
        "meny", "peny",
        "mem", "pem",
        "men", "pen",
        "ber", "ter", "per",
        "di", "ke", "se",
        "me", "pe",
    )

    /**
     * The root-initial letters [prefix] may have swallowed, given the letter that follows it.
     * An empty string means the root starts with that following letter unchanged.
     *
     * Nasal prefixes assimilate to the root: `k` becomes `ng` (`kasih`, `mengasihi`), `s` becomes
     * `ny`, `p` becomes `m` and `t` becomes `n`, while roots starting with `b`, `d`, `c`, `j` or `g`
     * keep their letter (`membawa`, `mendengar`, `menggali`).
     */
    private fun restoresFor(prefix: String, next: Char): Array<String> = when (prefix) {
        "meng", "peng" -> if (isVowel(next)) arrayOf("", "k") else arrayOf("")
        "meny", "peny" -> if (isVowel(next)) arrayOf("s") else emptyArray()
        "mem", "pem" -> if (isVowel(next)) arrayOf("p", "m") else arrayOf("")
        "men", "pen" -> if (isVowel(next)) arrayOf("t", "n") else arrayOf("")
        else -> arrayOf("")
    }

    private fun isVowel(c: Char) = c == 'a' || c == 'i' || c == 'u' || c == 'e' || c == 'o'

    /**
     * Returns the peel steps from [word] to the first known form, or null when no sequence of peels
     * lands on one. An empty list means [word] itself is known.
     */
    fun peel(word: String, isKnown: (String) -> Boolean): List<PeelStep>? {
        val start = word.lowercase()
        val cameFrom = HashMap<String, PeelStep>()
        val seen = hashSetOf(start)
        var frontier = listOf(start)

        repeat(MAX_DEPTH) {
            val next = ArrayList<String>()
            for (w in frontier) {
                if (isKnown(w)) return pathTo(w, cameFrom)

                for (suf in SUFFIXES) {
                    if (w.endsWith(suf) && w.length - suf.length >= MIN_STEM) {
                        val cand = w.substring(0, w.length - suf.length)
                        if (seen.add(cand)) {
                            cameFrom[cand] = PeelStep(w, cand, suf, isPrefix = false, restored = "")
                            next += cand
                        }
                    }
                }
                for (pre in PREFIXES) {
                    if (w.startsWith(pre) && w.length - pre.length >= MIN_STEM) {
                        val rest = w.substring(pre.length)
                        for (letter in restoresFor(pre, rest[0])) {
                            val cand = letter + rest
                            if (seen.add(cand)) {
                                cameFrom[cand] = PeelStep(w, cand, pre, isPrefix = true, restored = letter)
                                next += cand
                            }
                        }
                    }
                }
            }
            if (next.isEmpty()) return null
            frontier = next
        }
        return null
    }

    /**
     * Every string reachable from [word] by peeling, with the steps that reach it, depth by depth.
     * The on-device rules-only lexicon uses this to find the deepest stem that occurs in the text.
     */
    fun allPeels(word: String, maxDepth: Int = MAX_DEPTH): Map<String, List<PeelStep>> {
        val start = word.lowercase()
        val cameFrom = HashMap<String, PeelStep>()
        val res = LinkedHashMap<String, List<PeelStep>>()
        res[start] = emptyList()
        var frontier = listOf(start)

        repeat(maxDepth) {
            val next = ArrayList<String>()
            for (w in frontier) {
                for (suf in SUFFIXES) {
                    if (w.endsWith(suf) && w.length - suf.length >= MIN_STEM) {
                        val cand = w.substring(0, w.length - suf.length)
                        if (cand !in res) {
                            cameFrom[cand] = PeelStep(w, cand, suf, isPrefix = false, restored = "")
                            res[cand] = pathTo(cand, cameFrom)
                            next += cand
                        }
                    }
                }
                for (pre in PREFIXES) {
                    if (w.startsWith(pre) && w.length - pre.length >= MIN_STEM) {
                        val rest = w.substring(pre.length)
                        for (letter in restoresFor(pre, rest[0])) {
                            val cand = letter + rest
                            if (cand !in res) {
                                cameFrom[cand] = PeelStep(w, cand, pre, isPrefix = true, restored = letter)
                                res[cand] = pathTo(cand, cameFrom)
                                next += cand
                            }
                        }
                    }
                }
            }
            if (next.isEmpty()) return res
            frontier = next
        }
        return res
    }

    private fun pathTo(w: String, cameFrom: Map<String, PeelStep>): List<PeelStep> {
        val steps = ArrayList<PeelStep>()
        var cur = w
        while (true) {
            val step = cameFrom[cur] ?: break
            steps += step
            cur = step.from
        }
        steps.reverse()
        return steps
    }
}
