package yuku.alkitab.base.smartsearch

/**
 * Splits verse text into words the way the search lexicon was built: a word is a run of letters or
 * digits, and a hyphen between two such runs keeps them one word, so `kasih-Nya` and `orang-orang`
 * are single words. Verse formatting codes (`@6`, `@/`, `@<f1@>`) are skipped, so a code glued to a
 * word never becomes part of it.
 *
 * Works on both raw chapter text (with codes, verses separated by `\n`) and on plain text with the
 * codes already removed.
 */
object WordScanner {
    /**
     * Calls [onWord] with the `[start, end)` bounds of every word in `text[from, to)`, in order,
     * until it returns false.
     *
     * If [from] falls inside a word, that word is skipped rather than reported from the middle:
     * a match that starts halfway through a word is not a whole-word match.
     */
    inline fun forEachWord(text: CharSequence, from: Int = 0, to: Int = text.length, onWord: (start: Int, end: Int) -> Boolean) {
        var i = from
        if (i in 1 until to && isInsideWord(text, i)) {
            while (i < to && isWordCharOrJoiner(text, i, to)) i++
        }

        while (i < to) {
            val c = text[i]
            if (c == '@' && i + 1 < to) {
                i = skipCode(text, i, to)
                continue
            }
            if (!Character.isLetterOrDigit(c)) {
                i++
                continue
            }

            val start = i
            while (i < to && isWordCharOrJoiner(text, i, to)) i++
            if (!onWord(start, i)) return
        }
    }

    /**
     * Splits a hyphenated word into its parts, e.g. `kasih-nya` into `kasih` and `nya`, calling
     * [onPart] with the bounds of each.
     */
    inline fun forEachPart(text: CharSequence, start: Int, end: Int, onPart: (start: Int, end: Int) -> Boolean) {
        var partStart = start
        for (i in start until end) {
            if (text[i] == '-') {
                if (!onPart(partStart, i)) return
                partStart = i + 1
            }
        }
        onPart(partStart, end)
    }

    /** All words of [text] as lowercase strings; for tests and diagnostics, not for hot loops. */
    fun words(text: CharSequence): List<String> {
        val res = mutableListOf<String>()
        forEachWord(text) { s, e ->
            res += text.subSequence(s, e).toString().lowercase()
            true
        }
        return res
    }

    @PublishedApi
    internal fun isWordCharOrJoiner(text: CharSequence, i: Int, to: Int): Boolean {
        val c = text[i]
        if (Character.isLetterOrDigit(c)) return true
        return c == '-' && i > 0 && i + 1 < to && Character.isLetterOrDigit(text[i - 1]) && Character.isLetterOrDigit(text[i + 1])
    }

    @PublishedApi
    internal fun isInsideWord(text: CharSequence, i: Int): Boolean {
        val prev = text[i - 1]
        if (Character.isLetterOrDigit(prev)) {
            // A letter right after a formatting code such as `@9` starts a word.
            return !(i >= 2 && text[i - 2] == '@')
        }
        return prev == '-' && i >= 2 && Character.isLetterOrDigit(text[i - 2]) && Character.isLetterOrDigit(text[i])
    }

    /** Returns the index just past the formatting code starting at [i], which holds `@`. */
    @PublishedApi
    internal fun skipCode(text: CharSequence, i: Int, to: Int): Int {
        if (text[i + 1] == '<') {
            var j = i + 2
            while (j + 1 < to) {
                if (text[j] == '@' && text[j + 1] == '>') return j + 2
                j++
            }
            return to
        }
        return i + 2
    }
}
