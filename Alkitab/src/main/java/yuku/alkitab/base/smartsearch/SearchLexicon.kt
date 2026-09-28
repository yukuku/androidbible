package yuku.alkitab.base.smartsearch

/** Where a [SearchLexicon] came from; shown in the diagnostics so a tester knows what they are judging. */
enum class LexiconOrigin {
    /** Carried by the version itself: the `lexicon` section of its yes file, or its internal lexicon file. */
    VERSION,

    /** Derived on the device from the version's own words by affix rules alone. */
    RULES,
}

/**
 * The word families of one version: each root with the surface forms of it that occur in the
 * text, so searching `kasih` can also find `mengasihi`, whose `k` the prefix swallowed, while
 * searching `berkat` stops finding `berkata`.
 *
 * @property id what the diagnostics call it, e.g. the version's short name.
 */
class SearchLexicon(
    val id: String,
    val origin: LexiconOrigin,
    val families: Map<String, List<String>>,
    val loadMillis: Long,
) {
    /**
     * Form (or root) to root. A form maps to the family the lexicon puts it in; a root that never
     * occurs bare in the text still maps to itself, so typing it finds its family.
     */
    val rootOf: Map<String, String> = HashMap<String, String>(families.size * 8).also { m ->
        for ((root, forms) in families) {
            for (f in forms) m[f] = root
        }
        for (root in families.keys) {
            if (root !in m) m[root] = root
        }
    }

    val formCount: Int get() = families.values.sumOf { it.size }

    fun isKnown(word: String) = word in rootOf

    /** The forms to search for [root], always including the root itself. */
    fun formsOf(root: String): List<String> {
        val forms = families[root] ?: return listOf(root)
        return if (root in forms) forms else forms + root
    }

    companion object {
        /**
         * Android reports Indonesian as the legacy code `in`, while ISO 639-1 says `id` and
         * versions sometimes carry the three-letter `ind`.
         */
        fun sameLanguage(a: String?, b: String?): Boolean {
            if (a == null || b == null) return false
            return normalizeLanguage(a) == normalizeLanguage(b)
        }

        private fun normalizeLanguage(code: String): String {
            val base = code.lowercase().substringBefore('_').substringBefore('-')
            return when (base) {
                "in", "id", "ind" -> "id"
                else -> base
            }
        }
    }
}
