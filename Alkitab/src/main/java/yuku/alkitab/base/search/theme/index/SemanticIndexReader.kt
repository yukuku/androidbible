package yuku.alkitab.base.search.theme.index

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.PriorityQueue
import yuku.alkitab.util.Ari

data class RankedAri(val ari: Int, val score: Float)

class IndexFormatException(message: String) : IllegalArgumentException(message)

/** Memory-mapped linear cosine scorer for the compact public-domain WEB index. */
class SemanticIndexReader(
    file: File,
    expectedDimensions: Int = DIMENSIONS,
) {
    private val buffer: ByteBuffer
    val dimensions: Int
    val count: Int

    init {
        buffer = FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()).order(ByteOrder.LITTLE_ENDIAN)
        }
        if (buffer.remaining() < HEADER_BYTES) throw IndexFormatException("Index header is truncated")
        val magic = ByteArray(8).also(buffer::get)
        if (!magic.contentEquals(MAGIC)) throw IndexFormatException("Index magic is invalid")
        val version = buffer.int
        if (version != FORMAT_VERSION) throw IndexFormatException("Index version $version is unsupported")
        dimensions = buffer.int
        if (dimensions != expectedDimensions) throw IndexFormatException("Index dimension $dimensions is incompatible")
        count = buffer.int
        if (count < 0) throw IndexFormatException("Negative row count")
        val expectedBytes = HEADER_BYTES.toLong() + count.toLong() * (ROW_PREFIX_BYTES + dimensions)
        if (expectedBytes != buffer.capacity().toLong()) throw IndexFormatException("Index length does not match header")
    }

    fun topK(query: FloatArray, allowedBooks: BooleanArray, k: Int): List<RankedAri> {
        require(query.size == dimensions)
        if (k <= 0) return emptyList()
        val heap = PriorityQueue<RankedAri>(compareBy { it.score })
        val rows = buffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN).apply { position(HEADER_BYTES) }
        repeat(count) {
            val ari = rows.int
            val scale = rows.float
            val allowed = Ari.toBook(ari).let { it in allowedBooks.indices && allowedBooks[it] }
            var dot = 0f
            for (dimension in 0 until dimensions) {
                val encoded = rows.get().toInt()
                if (allowed) dot += query[dimension] * encoded
            }
            if (allowed) {
                val ranked = RankedAri(ari, dot * scale)
                if (heap.size < k) heap += ranked
                else if (ranked.score > heap.peek().score) {
                    heap.poll()
                    heap += ranked
                }
            }
        }
        return heap.sortedByDescending { it.score }
    }

    companion object {
        const val DIMENSIONS = 384
        private const val FORMAT_VERSION = 1
        private const val HEADER_BYTES = 20
        private const val ROW_PREFIX_BYTES = 8
        private val MAGIC = "YUKUSEM1".toByteArray()
        private const val ASSET = "offline_search/web_granite97m_r2.int8"

        fun fromBundledAsset(context: Context): SemanticIndexReader {
            val destination = File(context.cacheDir, "offline-search/$ASSET".substringAfterLast('/'))
            if (!destination.isFile) {
                check(destination.parentFile!!.mkdirs() || destination.parentFile!!.isDirectory)
                val part = File(destination.parentFile, destination.name + ".part")
                context.assets.open(ASSET).use { input -> part.outputStream().use(input::copyTo) }
                if (!part.renameTo(destination)) error("Unable to publish semantic index cache")
            }
            return SemanticIndexReader(destination)
        }
    }
}
