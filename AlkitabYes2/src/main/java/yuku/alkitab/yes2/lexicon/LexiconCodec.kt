package yuku.alkitab.yes2.lexicon

/**
 * Rewrites the start of a root the way a nasal prefix does, e.g. `k` to `ng` so that `me` + the
 * rewritten `kasih` spells `mengasih`. Stored with the lexicon, so a version can carry its own.
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

/** A root and its forms, each form written the way [LexiconCodec] abbreviates it. */
class EncodedFamily(val root: String, val forms: List<String>)

/**
 * The text notation for a form, used in `.yet` files: [ROOT] stands for the root and
 * [REWRITTEN_ROOT] for the root rewritten by the prefix table. With root `kasih` and the rule
 * `k` to `ng`, `me<i` is `mengasihi` and `ke~-ke~nya` is `kekasih-kekasihnya`.
 */
object LexiconCodec {
    const val ROOT = '~'
    const val REWRITTEN_ROOT = '<'

    @JvmStatic
    fun decodeForm(root: String, encoded: String, table: LexiconPrefixTable): String {
        if (encoded.indexOf(ROOT) < 0 && encoded.indexOf(REWRITTEN_ROOT) < 0) return encoded
        return buildString {
            for (token in tokenize(encoded)) {
                when (token) {
                    ROOT.toString() -> append(root)
                    REWRITTEN_ROOT.toString() -> append(
                        table.rewrite(root) ?: throw IllegalArgumentException("no prefix rule applies to root '$root' in '$encoded'")
                    )
                    else -> append(token)
                }
            }
        }
    }

    /**
     * Abbreviates [form], replacing every occurrence of the root, or of the root rewritten by the
     * prefix table, scanning left to right.
     */
    @JvmStatic
    fun encodeForm(root: String, form: String, table: LexiconPrefixTable): String {
        requirePlain(root)
        requirePlain(form)
        val rewritten = table.rewrite(root)
        return buildString {
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

    /**
     * Splits an abbreviated form into its tokens: `"~"`, `"<"`, or a run of text between them.
     * `me~-~kan` is `me`, `~`, `-`, `~`, `kan`.
     */
    @JvmStatic
    fun tokenize(encoded: String): List<String> {
        val res = ArrayList<String>(4)
        var start = 0
        for (i in encoded.indices) {
            val c = encoded[i]
            if (c == ROOT || c == REWRITTEN_ROOT) {
                if (i > start) res += encoded.substring(start, i)
                res += c.toString()
                start = i + 1
            }
        }
        if (start < encoded.length) res += encoded.substring(start)
        return res
    }

    @JvmStatic
    fun decodeFamily(family: EncodedFamily, table: LexiconPrefixTable): List<String> =
        family.forms.map { decodeForm(family.root, it, table) }

    private fun requirePlain(word: String) {
        require(word.isNotEmpty() && word.none { it == ROOT || it == REWRITTEN_ROOT || it.isWhitespace() }) { "not a plain word: '$word'" }
    }
}
