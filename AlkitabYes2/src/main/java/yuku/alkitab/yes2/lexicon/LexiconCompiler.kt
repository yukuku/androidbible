package yuku.alkitab.yes2.lexicon

import yuku.alkitab.yes2.section.LexiconSection

/**
 * Decides how a lexicon of plain forms is stored: the rewrite rules for the start and the end of
 * roots, how each form splits into roots and text, and which text runs are shared pieces.
 *
 * Rules are not given; they are found in the data. Candidate rules are the ones that would explain
 * forms not containing their root, and they are adopted one at a time, always the one that makes
 * the stored lexicon smallest, until none makes it smaller. The rules are whatever is smallest, not
 * necessarily what a linguist would write: `k` to `g` with a shared piece `men` can beat `k` to `ng`
 * with `me`. A rule lengthens the root by at most one letter, which keeps it from swallowing a
 * whole affix.
 *
 * Sizes are counted the way the section stores them: one byte per token, plus each distinct text
 * run once (a shared piece, or a literal when used once), plus the rules.
 */
object LexiconCompiler {
    /** Part of a form: a root token of [LexiconSection], or [text]. */
    class Part(val token: Int, val text: String?)

    class Family(val root: String, val forms: List<List<Part>>)

    class Compiled(
        val startTable: RootRewriteTable,
        val endTable: RootRewriteTable,
        /** Text runs used at least [MIN_PIECE_USES] times, most used first. */
        val pieces: List<String>,
        val families: List<Family>,
    ) {
        private val pieceSet by lazy { pieces.toHashSet() }
        val literalCount get() = families.sumOf { f -> f.forms.sumOf { parts -> parts.count { it.text != null && it.text !in pieceSet } } }

        fun describe() = "lexicon: ${families.size} families, ${families.sumOf { it.forms.size }} forms, " +
            "start rules ${startTable.rules}, end rules ${endTable.rules}, ${pieces.size} pieces, $literalCount literals"
    }

    const val MIN_PIECE_USES = 2

    /** Stored once per distinct text run: autostring type, length, chars. The token that refers to it is counted per use. */
    private fun runStorage(run: String) = 2 + run.length
    private fun ruleCost(rule: RootRewriteTable.Rule) = 4 + rule.from.length + rule.to.length

    private const val MAX_FROM = 2
    /** A rewrite keeps at least this many letters of the root, so it cannot turn a root into a fragment that matches anywhere. */
    private const val MIN_KEPT = 2
    private const val MIN_CANDIDATE_USES = 3
    private const val SHORTLIST_PER_SIDE = 60

    private const val INF = Int.MAX_VALUE / 2

    /** How many forms use each text run, with an overlay of changes that can be tried and dropped. */
    private class RunCounts {
        val base = HashMap<String, Int>()
        val overlay = HashMap<String, Int>()

        operator fun get(run: String) = (base[run] ?: 0) + (overlay[run] ?: 0)
        fun add(run: String, n: Int) { overlay[run] = (overlay[run] ?: 0) + n }

        /** Change in stored bytes of distinct runs if the overlay were committed. */
        fun storageDelta(): Int {
            var d = 0
            for ((run, n) in overlay) {
                val before = base[run] ?: 0
                val after = before + n
                if (before == 0 && after > 0) d += runStorage(run)
                if (before > 0 && after == 0) d -= runStorage(run)
            }
            return d
        }

        fun commit() {
            for ((run, n) in overlay) {
                val v = (base[run] ?: 0) + n
                if (v == 0) base.remove(run) else base[run] = v
            }
            overlay.clear()
        }
    }

    @JvmStatic
    fun compile(families: Map<String, List<String>>): Compiled {
        for ((root, forms) in families) {
            require(root.isNotEmpty()) { "empty root" }
            require(forms.none { it.isEmpty() }) { "empty form in the family of '$root'" }
        }

        var start = RootRewriteTable(RootRewriteTable.Side.START, emptyList())
        var end = RootRewriteTable(RootRewriteTable.Side.END, emptyList())
        val counts = RunCounts()
        val splits = HashMap<String, List<List<Part>>>(families.size * 2)
        for ((root, forms) in families) splits[root] = resplit(root, forms, emptyList(), start, end, counts).second
        counts.commit()

        val candidates = shortlist(families, RootRewriteTable.Side.START) + shortlist(families, RootRewriteTable.Side.END)
        while (true) {
            var bestDelta = 0
            var bestStart: RootRewriteTable? = null
            var bestEnd: RootRewriteTable? = null
            var bestSplits: Map<String, List<List<Part>>>? = null
            for ((side, rule) in candidates) {
                val current = if (side == RootRewriteTable.Side.START) start else end
                if (current.rules.any { it.from == rule.from }) continue
                val next = current.with(rule)
                val nextStart = if (side == RootRewriteTable.Side.START) next else start
                val nextEnd = if (side == RootRewriteTable.Side.END) next else end

                val affected = families.filter { (root, _) -> next.rewrite(root) != current.rewrite(root) }
                for (root in affected.keys) for (parts in splits.getValue(root)) for (p in parts) p.text?.let { counts.add(it, -1) }
                var delta = ruleCost(rule)
                val changed = HashMap<String, List<List<Part>>>()
                for ((root, forms) in affected) {
                    val (partDelta, parts) = resplit(root, forms, splits.getValue(root), nextStart, nextEnd, counts)
                    delta += partDelta
                    changed[root] = parts
                }
                delta += counts.storageDelta()
                counts.overlay.clear()

                if (delta < bestDelta) {
                    bestDelta = delta
                    bestStart = nextStart
                    bestEnd = nextEnd
                    bestSplits = changed
                }
            }
            if (bestSplits == null) break
            start = bestStart!!
            end = bestEnd!!
            for ((root, parts) in bestSplits) {
                for (old in splits.getValue(root)) for (p in old) p.text?.let { counts.add(it, -1) }
                for (new in parts) for (p in new) p.text?.let { counts.add(it, 1) }
                splits[root] = parts
            }
            counts.commit()
        }

        // One more pass, now that every family's runs are known, lets earlier families reuse runs that later ones introduced.
        for ((root, forms) in families) {
            for (old in splits.getValue(root)) for (p in old) p.text?.let { counts.add(it, -1) }
            splits[root] = resplit(root, forms, emptyList(), start, end, counts).second
            counts.commit()
        }

        val compiled = families.map { (root, forms) ->
            val variants = variantsOf(root, start, end)
            val parts = splits.getValue(root)
            for ((form, p) in forms.zip(parts)) {
                check(p.joinToString("") { it.text ?: variants.first { v -> v.first == it.token }.second } == form) { "'$form' does not reassemble" }
            }
            Family(root, parts)
        }
        val pieces = counts.base.filterValues { it >= MIN_PIECE_USES }.keys.sortedWith(compareByDescending<String> { counts[it] }.thenBy { it })
        return Compiled(start, end, pieces, compiled)
    }

    /**
     * Splits every form of a family, adding the runs it uses to [counts] as it goes so later forms
     * can share them. Returns the change in token count against [old] and the new split.
     */
    private fun resplit(
        root: String, forms: List<String>, old: List<List<Part>>,
        start: RootRewriteTable, end: RootRewriteTable, counts: RunCounts,
    ): Pair<Int, List<List<Part>>> {
        val variants = variantsOf(root, start, end)
        val res = forms.map { form ->
            split(form, variants, counts).also { parts -> for (p in parts) p.text?.let { counts.add(it, 1) } }
        }
        return (res.sumOf { it.size } - old.sumOf { it.size }) to res
    }
    /** Rewrite rules that would explain some form not containing its root, most promising first. */
    private fun shortlist(families: Map<String, List<String>>, side: RootRewriteTable.Side): List<Pair<RootRewriteTable.Side, RootRewriteTable.Rule>> {
        val counts = HashMap<Pair<String, String>, Int>()
        for ((root, forms) in families) for (form in forms) {
            if (form.contains(root)) continue
            val seen = HashSet<Pair<String, String>>()
            for (d in 1..MAX_FROM) {
                val kept = root.length - d
                if (kept < MIN_KEPT) break
                if (side == RootRewriteTable.Side.START) {
                    val from = root.substring(0, d)
                    val tail = root.substring(d)
                    var i = form.indexOf(tail)
                    while (i >= 0) {
                        for (k in 0..minOf(d + 1, i)) form.substring(i - k, i).let { if (it != from) seen += from to it }
                        i = form.indexOf(tail, i + 1)
                    }
                } else {
                    val from = root.substring(kept)
                    val head = root.substring(0, kept)
                    var i = form.indexOf(head)
                    while (i >= 0) {
                        val after = i + kept
                        for (k in 0..minOf(d + 1, form.length - after)) form.substring(after, after + k).let { if (it != from) seen += from to it }
                        i = form.indexOf(head, i + 1)
                    }
                }
            }
            for (p in seen) counts[p] = (counts[p] ?: 0) + 1
        }
        return counts.entries
            .filter { it.value >= MIN_CANDIDATE_USES }
            .sortedWith(compareByDescending<Map.Entry<Pair<String, String>, Int>> { it.value }.thenBy { it.key.first }.thenBy { it.key.second })
            .take(SHORTLIST_PER_SIDE)
            .map { side to RootRewriteTable.Rule(it.key.first, it.key.second) }
    }

    /** The spellings of a root a form can refer to by token, root first. */
    private fun variantsOf(root: String, start: RootRewriteTable, end: RootRewriteTable): List<Pair<Int, String>> {
        val res = ArrayList<Pair<Int, String>>(3)
        res += LexiconSection.TOKEN_ROOT to root
        start.rewrite(root)?.let { s -> if (s.isNotEmpty() && res.none { it.second == s }) res += LexiconSection.TOKEN_START_REWRITTEN to s }
        end.rewrite(root)?.let { s -> if (s.isNotEmpty() && res.none { it.second == s }) res += LexiconSection.TOKEN_END_REWRITTEN to s }
        return res
    }

    /**
     * The cheapest split of [form] into root variants and text runs, never two text runs in a row.
     * A run costs its token, plus its storage unless [counts] shows it already stored. On equal
     * cost a variant beats text, an earlier variant beats a later one, and a shorter run beats a
     * longer one.
     */
    private fun split(form: String, variants: List<Pair<Int, String>>, counts: RunCounts): List<Part> {
        val n = form.length
        val fromVariant = IntArray(n + 1) { INF } // cost of form[i:] when it starts with a variant; 0 at the end
        val variantAt = IntArray(n + 1) { -1 }
        val best = IntArray(n + 1) { INF }
        val textUntil = IntArray(n + 1) { -1 } // end of the leading text run of the best split, or -1
        fromVariant[n] = 0
        best[n] = 0
        for (i in n - 1 downTo 0) {
            for ((vi, v) in variants.withIndex()) {
                if (!form.startsWith(v.second, i)) continue
                val c = 1 + best[i + v.second.length]
                if (c < fromVariant[i]) {
                    fromVariant[i] = c
                    variantAt[i] = vi
                }
            }
            best[i] = fromVariant[i]
            for (j in i + 1..n) {
                if (fromVariant[j] >= INF) continue
                val run = form.substring(i, j)
                val c = 1 + (if (counts[run] > 0) 0 else runStorage(run)) + fromVariant[j]
                if (c < best[i]) {
                    best[i] = c
                    textUntil[i] = j
                }
            }
        }

        val parts = ArrayList<Part>(4)
        var i = 0
        var afterText = false
        while (i < n) {
            if (!afterText && textUntil[i] >= 0) {
                parts += Part(LexiconSection.TOKEN_LITERAL, form.substring(i, textUntil[i]))
                i = textUntil[i]
                afterText = true
            } else {
                val v = variants[variantAt[i]]
                parts += Part(v.first, null)
                i += v.second.length
                afterText = false
            }
        }
        return parts
    }
}
