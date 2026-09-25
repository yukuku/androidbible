package yuku.alkitab.base.smartsearch

/**
 * Builds fallback families for versions without a lexicon. Without a dictionary of
 * roots, it can group unrelated words such as `kepada` and `pada`.
 */
object RulesLexiconBuilder {
    private val CLITICS = setOf("nya", "ku", "mu", "lah", "nyalah", "kulah", "mulah", "pun")

    fun build(vocabulary: VersionVocabulary): SearchLexicon {
        val t0 = System.nanoTime()
        val rootCache = HashMap<String, String>()

        fun rootOfPlain(w: String): String = rootCache.getOrPut(w) {
            var best = w
            var bestDepth = 0
            var bestCount = vocabulary.countOf(w)
            for ((cand, steps) in AffixPeeler.allPeels(w)) {
                if (steps.isEmpty()) continue
                if (cand.length < AffixPeeler.MIN_STEM || cand !in vocabulary) continue
                val count = vocabulary.countOf(cand)
                if (steps.size > bestDepth || (steps.size == bestDepth && count > bestCount)) {
                    best = cand
                    bestDepth = steps.size
                    bestCount = count
                }
            }
            best
        }

        fun rootOf(w: String): String {
            if (w.indexOf('-') < 0) return rootOfPlain(w)
            val parts = w.split('-')
            // `kasih-nya`: the clitic hangs off the first part. `orang-orang`, `mereka-rekakan`:
            // in a reduplication the later half is the bare reduplicant carrying the root.
            return if (parts.size == 2 && parts[1] in CLITICS) rootOfPlain(parts[0]) else rootOfPlain(parts.last())
        }

        val families = HashMap<String, MutableList<String>>()
        for (w in vocabulary.counts.keys) {
            if (w.any { it.isDigit() }) continue
            families.getOrPut(rootOf(w)) { mutableListOf() } += w
        }

        val useful = LinkedHashMap<String, List<String>>()
        for (root in families.keys.sorted()) {
            val forms = families.getValue(root)
            if (forms.size == 1 && forms[0] == root) continue
            useful[root] = forms.sortedByDescending { vocabulary.countOf(it) }
        }

        return SearchLexicon(
            id = "rules-${vocabulary.versionKey}",
            origin = LexiconOrigin.RULES,
            families = useful,
            loadMillis = (System.nanoTime() - t0) / 1_000_000,
        )
    }
}
