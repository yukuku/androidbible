package yuku.alkitab.base.search.theme.model

import kotlin.math.sqrt

class GraniteEmbeddingEngine(
    private val tokenizer: EmbeddingTokenizer,
    private val session: EmbeddingSession,
    private val maxLength: Int = 128,
) : AutoCloseable {
    private var closed = false

    fun embed(text: String): FloatArray {
        check(!closed) { "Embedding engine is closed" }
        val encoded = tokenizer.encode(text, maxLength)
        val hidden = session.run(encoded.inputIds, encoded.attentionMask)
        val cls = hidden.firstOrNull()?.firstOrNull()?.copyOf()
            ?: error("Granite returned no CLS embedding")
        require(cls.size == EMBEDDING_DIMENSIONS) { "Unexpected Granite dimension ${cls.size}" }
        var squaredNorm = 0.0
        cls.forEach { value -> squaredNorm += value * value }
        val norm = sqrt(squaredNorm).toFloat()
        require(norm > 0f && norm.isFinite()) { "Granite returned an invalid embedding" }
        for (index in cls.indices) cls[index] /= norm
        return cls
    }

    override fun close() {
        if (closed) return
        closed = true
        session.close()
    }

    companion object {
        const val EMBEDDING_DIMENSIONS = 384
    }
}
