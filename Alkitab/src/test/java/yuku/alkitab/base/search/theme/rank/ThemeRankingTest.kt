package yuku.alkitab.base.search.theme.rank

import java.nio.file.Files
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
    fun `semantic query adds bilingual terms and removes function words`() {
        val normalizer = QueryNormalizer(mapOf("kasih" to listOf("love", "sabar", "patient")))
        assertEquals("kasih love patient sabar", normalizer.semanticQuery("kasih yang sabar"))
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
    fun `BM25 binary round trip preserves ranking`() {
        val allowedBooks = BooleanArray(66) { true }
        val documents = mapOf(
            Ari.encode(0, 1, 1) to "pada mulanya Allah menciptakan langit dan bumi",
            Ari.encode(0, 1, 2) to "bumi belum berbentuk dan kosong",
            Ari.encode(0, 1, 3) to "berfirmanlah Allah jadilah terang",
        )
        val original = Bm25Index.fromDocuments(documents, QueryNormalizer())
        val file = Files.createTempFile("bm25-round-trip", ".bin").toFile()
        try {
            original.writeTo(file)
            val restored = Bm25Index.readFrom(file)
            assertEquals(
                original.search(listOf("Allah", "menciptakan"), allowedBooks, 3),
                restored.search(listOf("Allah", "menciptakan"), allowedBooks, 3),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `BM25 cache survives process memory reset without rebuilding`() {
        val cacheRoot = Files.createTempDirectory("bm25-cache").toFile()
        val normalizer = QueryNormalizer(mapOf("hikmat" to listOf("wisdom")))
        var builds = 0
        try {
            val build = {
                builds += 1
                Bm25Index.fromDocuments(
                    mapOf(Ari.encode(44, 1, 5) to "jika kekurangan hikmat mintalah kepada Allah"),
                    normalizer,
                )
            }
            Bm25IndexCache.getOrBuild(cacheRoot, "preset/in-tb", 123, normalizer, build)
            Bm25IndexCache.clearMemoryForTest()
            val restored = Bm25IndexCache.getOrBuild(cacheRoot, "preset/in-tb", 123, normalizer, build)

            assertEquals(1, builds)
            assertEquals(
                Ari.encode(44, 1, 5),
                restored.search(listOf("hikmat"), BooleanArray(66) { true }, 1).single().ari,
            )
        } finally {
            Bm25IndexCache.clearMemoryForTest()
            cacheRoot.deleteRecursively()
        }
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
