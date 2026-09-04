package yuku.alkitab.base.search.theme.rank

import org.junit.Assert.assertEquals
import org.junit.Test
import yuku.alkitab.base.search.theme.index.RankedAri
import yuku.alkitab.util.Ari

class ThemeRankingTest {
    @Test
    fun `Indonesian synonym expansion is bounded and deterministic`() {
        val normalizer = QueryNormalizer(mapOf("cemas" to listOf("kecemasan", "khawatir", "kekhawatiran")))
        assertEquals(listOf("cemas", "kecemasan", "kekhawatiran", "khawatir"), normalizer.expand("kecemasan"))
    }

    @Test
    fun `BM25 ranks matching rare term before common-only document`() {
        val rare = Ari.encode(0, 1, 1)
        val common = Ari.encode(0, 1, 2)
        val index = Bm25Index.fromDocuments(
            mapOf(rare to "hikmat kasih", common to "kasih kasih kasih", Ari.encode(0, 1, 3) to "kasih"),
            QueryNormalizer(),
        )
        assertEquals(rare, index.search(listOf("hikmat", "kasih"), BooleanArray(66) { true }, 3).first().ari)
    }

    @Test
    fun `RRF uses rank rather than incomparable raw score`() {
        val expected = Ari.encode(0, 1, 1)
        val other = Ari.encode(0, 1, 2)
        val fused = ReciprocalRankFusion.fuse(
            listOf(
                listOf(RankedAri(expected, 0.1f), RankedAri(other, 0.09f)),
                listOf(RankedAri(expected, 1f), RankedAri(other, 999_999f)),
            ),
            2,
        )
        assertEquals(expected, fused.first().ari)
    }
}
