package yuku.alkitab.base.search.theme.model

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File

interface EmbeddingSession : AutoCloseable {
    fun run(inputIds: LongArray, attentionMask: LongArray): Array<Array<FloatArray>>
}

/** Resource-safe ONNX Runtime boundary retained for multiple warm queries. */
class OnnxEmbeddingSession(model: File) : EmbeddingSession {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = environment.createSession(model.absolutePath)
    private var closed = false

    override fun run(inputIds: LongArray, attentionMask: LongArray): Array<Array<FloatArray>> {
        check(!closed) { "Embedding session is closed" }
        require(inputIds.size == attentionMask.size)
        OnnxTensor.createTensor(environment, arrayOf(inputIds)).use { idsTensor ->
            OnnxTensor.createTensor(environment, arrayOf(attentionMask)).use { maskTensor ->
                session.run(mapOf("input_ids" to idsTensor, "attention_mask" to maskTensor)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    return result[0].value as Array<Array<FloatArray>>
                }
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        session.close()
    }
}
