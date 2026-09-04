package yuku.alkitab.base.search.theme.rank

import yuku.alkitab.base.search.theme.index.RankedAri

object ReciprocalRankFusion {
    fun fuse(rankings: List<List<RankedAri>>, k: Int, constant: Int = 60): List<RankedAri> {
        require(constant >= 0)
        val scores = HashMap<Int, Float>()
        rankings.forEach { ranking ->
            ranking.forEachIndexed { index, item ->
                scores[item.ari] = (scores[item.ari] ?: 0f) + 1f / (constant + index + 1)
            }
        }
        return scores.entries.asSequence()
            .map { RankedAri(it.key, it.value) }
            .sortedWith(compareByDescending<RankedAri> { it.score }.thenBy { it.ari })
            .take(k)
            .toList()
    }
}
