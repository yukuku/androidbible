package yuku.alkitab.base.search.theme.model

import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GraniteEmbeddingEngineTest {
    @Test
    fun `embedding passes tensors selects CLS and normalizes`() {
        val ids = longArrayOf(179934, 32242, 2374, 4120, 6815, 237, 179938)
        val tokenizer = EmbeddingTokenizer { _, _ -> TokenizedInput(ids, LongArray(ids.size) { 1 }) }
        val session = FakeSession().apply {
            hiddenState = arrayOf(
                arrayOf(
                    FloatArray(384).also { it[0] = 3f; it[1] = 4f },
                    FloatArray(384) { 99f },
                ),
            )
        }
        val engine = GraniteEmbeddingEngine(tokenizer, session, 32)

        val vector = engine.embed("kasih yang sabar")

        assertEquals(384, vector.size)
        assertEquals(1.0f, sqrt(vector.sumOf { (it * it).toDouble() }).toFloat(), 1e-4f)
        assertArrayEquals(floatArrayOf(0.6f, 0.8f), vector.copyOf(2), 1e-6f)
        assertArrayEquals(ids, session.lastInputIds)
        assertArrayEquals(LongArray(ids.size) { 1 }, session.lastAttentionMask)
    }

    @Test
    fun `close releases retained ONNX session exactly once`() {
        val session = FakeSession()
        val engine = GraniteEmbeddingEngine(
            EmbeddingTokenizer { _, _ -> TokenizedInput(longArrayOf(1, 2), longArrayOf(1, 1)) },
            session,
        )

        engine.close()
        engine.close()

        assertEquals(1, session.closeCount)
    }

    private class FakeSession : EmbeddingSession {
        var hiddenState = arrayOf(arrayOf(FloatArray(384).also { it[0] = 1f }))
        var lastInputIds = longArrayOf()
        var lastAttentionMask = longArrayOf()
        var closeCount = 0

        override fun run(inputIds: LongArray, attentionMask: LongArray): Array<Array<FloatArray>> {
            lastInputIds = inputIds
            lastAttentionMask = attentionMask
            return hiddenState
        }

        override fun close() {
            closeCount++
        }
    }
}
