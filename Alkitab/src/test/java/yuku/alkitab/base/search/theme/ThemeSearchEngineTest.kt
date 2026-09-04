package yuku.alkitab.base.search.theme

import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import yuku.alkitab.base.search.theme.index.RankedAri
import yuku.alkitab.base.search.theme.pack.ModelPackState
import yuku.alkitab.base.search.theme.rank.QueryNormalizer
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari

class ThemeSearchEngineTest {
    private val oldAri = Ari.encode(0, 1, 1)
    private val newAri = Ari.encode(39, 1, 1)
    private val version = mockk<Version> {
        every { loadVerseText(oldAri) } returns "Kasih itu sabar"
        every { loadVerseText(newAri) } returns "Mintalah hikmat"
    }
    private val ready = ModelPackState.Ready(File("model"), File("tokenizer"))

    @Test
    fun `missing pack returns pack required without creating a runtime`() = runBlocking {
        var creates = 0
        val engine = engine(pack = null, semanticFactory = { creates++; fakeRanker(emptyList()) })

        val response = engine.search(version, "test", "kasih", BooleanArray(66) { true })

        assertEquals(ThemeSearchError.PACK_REQUIRED, response.error)
        assertTrue(response.aris.isEmpty())
        assertEquals(0, creates)
    }

    @Test
    fun `search fuses ranks filters books and drops missing active verses`() = runBlocking {
        val allowed = BooleanArray(66).also { it[39] = true }
        val missing = Ari.encode(39, 1, 2)
        every { version.loadVerseText(missing) } returns null
        val engine = engine(
            semanticFactory = { fakeRanker(listOf(RankedAri(oldAri, 9f), RankedAri(newAri, 8f), RankedAri(missing, 7f))) },
            lexical = listOf(RankedAri(newAri, 100f)),
        )

        val response = engine.search(version, "test", "meminta hikmat", allowed)

        assertNull(response.error)
        assertEquals(listOf(newAri), response.aris)
    }

    @Test
    fun `runtime is reused and closed once`() = runBlocking {
        var closeCount = 0
        val ranker = object : ThemeSemanticRanker {
            override fun rank(query: String, allowedBooks: BooleanArray, limit: Int) = listOf(RankedAri(oldAri, 1f))
            override fun close() { closeCount++ }
        }
        val engine = engine(semanticFactory = { ranker })
        val allowed = BooleanArray(66) { true }

        engine.search(version, "test", "kasih", allowed)
        engine.search(version, "test", "sabar", allowed)
        engine.close()
        engine.close()

        assertEquals(1, closeCount)
    }

    private fun engine(
        pack: ModelPackState.Ready? = ready,
        semanticFactory: (ModelPackState.Ready) -> ThemeSemanticRanker = { fakeRanker(listOf(RankedAri(oldAri, 1f))) },
        lexical: List<RankedAri> = emptyList(),
    ) = ThemeSearchEngine(
        readyPack = { pack },
        semanticFactory = ThemeSemanticRankerFactory(semanticFactory),
        lexicalSearch = { _, _, _, _, _ -> lexical },
        normalizer = QueryNormalizer(),
        dispatcher = Dispatchers.Unconfined,
    )

    private fun fakeRanker(results: List<RankedAri>) = object : ThemeSemanticRanker {
        override fun rank(query: String, allowedBooks: BooleanArray, limit: Int) = results.filter {
            val book = Ari.toBook(it.ari)
            book in allowedBooks.indices && allowedBooks[book]
        }
        override fun close() = Unit
    }
}
