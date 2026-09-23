package yuku.alkitab.yes2.lexicon

/**
 * Rewrites one end of a root, the way an affix can change it: at the [Side.START], `k` to `ng` makes
 * `kasih` into `ngasih`, as in `mengasihi`; at the [Side.END], `y` to `i` makes `carry` into
 * `carri`, as in `carried`. Stored with the lexicon, so every version carries its own rules.
 *
 * When more than one rule matches a root, the longest [Rule.from] wins; among rules of the same
 * length, the first.
 */
class RootRewriteTable(val side: Side, val rules: List<Rule>) {
    enum class Side { START, END }

    class Rule(val from: String, val to: String) {
        override fun toString() = "$from -> $to"
    }

    private val longestFirst = rules.sortedByDescending { it.from.length }

    /** The root with its [side] rewritten, or null when no rule applies to it. */
    fun rewrite(root: String): String? = when (side) {
        Side.START -> longestFirst.firstOrNull { root.startsWith(it.from) }?.let { it.to + root.substring(it.from.length) }
        Side.END -> longestFirst.firstOrNull { root.endsWith(it.from) }?.let { root.substring(0, root.length - it.from.length) + it.to }
    }

    fun with(rule: Rule) = RootRewriteTable(side, rules + rule)
}
