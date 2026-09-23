package yuku.alkitab.yes2.lexicon

/**
 * Rewrites the start of a root the way a nasal prefix does, e.g. `k` to `ng` so that `me` + the
 * rewritten `kasih` spells `mengasih`. Stored with the lexicon, so a translation can carry its own.
 *
 * When more than one rule matches a root, the longest [Rule.from] wins.
 */
class LexiconPrefixTable(val rules: List<Rule>) {
    class Rule(val from: String, val to: String)

    private val longestFirst = rules.sortedByDescending { it.from.length }

    /** The root with its start rewritten, or null when no rule applies to it. */
    fun rewrite(root: String): String? {
        val rule = longestFirst.firstOrNull { root.startsWith(it.from) } ?: return null
        return rule.to + root.substring(rule.from.length)
    }

    companion object {
        @JvmField
        val EMPTY = LexiconPrefixTable(emptyList())
    }
}

/**
 * Text form of one word family: the root, then its forms, separated by spaces. Within a form,
 * [ROOT] stands for the root and [REWRITTEN_ROOT] for the root rewritten by the prefix table:
 *
 * ```
 * kasih ~ me<i di~i ke~
 * ```
 *
 * is `kasih`, `mengasihi`, `dikasihi` and `kekasih`, given the rule `k` to `ng`.
 */
object LexiconCodec {
    const val ROOT = '~'
    const val REWRITTEN_ROOT = '<'

    /** Returns the root followed by its decoded forms. */
    @JvmStatic
    fun decodeFamily(line: String, table: LexiconPrefixTable): Pair<String, List<String>> {
        val tokens = line.split(' ').filter { it.isNotEmpty() }
        require(tokens.isNotEmpty()) { "empty word family" }
        val root = tokens[0]
        val rewritten by lazy { table.rewrite(root) }
        val forms = tokens.subList(1, tokens.size).map { token ->
            if (token.indexOf(ROOT) < 0 && token.indexOf(REWRITTEN_ROOT) < 0) return@map token
            buildString {
                for (c in token) {
                    when (c) {
                        ROOT -> append(root)
                        REWRITTEN_ROOT -> append(rewritten ?: throw IllegalArgumentException("no prefix rule applies to root '$root' in '$line'"))
                        else -> append(c)
                    }
                }
            }
        }
        return root to forms
    }

    /**
     * Encodes a word family, replacing every occurrence of the root, or of the root rewritten by
     * the prefix table, scanning left to right.
     */
    @JvmStatic
    fun encodeFamily(root: String, forms: List<String>, table: LexiconPrefixTable): String {
        requirePlain(root)
        val rewritten = table.rewrite(root)
        return buildString {
            append(root)
            for (form in forms) {
                requirePlain(form)
                append(' ')
                var i = 0
                while (i < form.length) {
                    when {
                        form.startsWith(root, i) -> { append(ROOT); i += root.length }
                        rewritten != null && form.startsWith(rewritten, i) -> { append(REWRITTEN_ROOT); i += rewritten.length }
                        else -> { append(form[i]); i++ }
                    }
                }
            }
        }
    }

    private fun requirePlain(word: String) {
        require(word.isNotEmpty() && word.none { it == ' ' || it == ROOT || it == REWRITTEN_ROOT }) { "not a plain word: '$word'" }
    }
}
