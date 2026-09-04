package yuku.alkitab.base.search.theme

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yuku.alkitab.base.App
import yuku.alkitab.base.model.MVersionDb
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.model.MVersionPreset
import yuku.alkitab.base.search.theme.index.RankedAri
import yuku.alkitab.base.search.theme.index.SemanticIndexReader
import yuku.alkitab.base.search.theme.model.ByteLevelBpeTokenizer
import yuku.alkitab.base.search.theme.model.GraniteEmbeddingEngine
import yuku.alkitab.base.search.theme.model.OnnxEmbeddingSession
import yuku.alkitab.base.search.theme.model.TokenizerData
import yuku.alkitab.base.search.theme.pack.ModelPackRepository
import yuku.alkitab.base.search.theme.pack.ModelPackState
import yuku.alkitab.base.search.theme.rank.Bm25IndexCache
import yuku.alkitab.base.search.theme.rank.QueryNormalizer
import yuku.alkitab.base.search.theme.rank.ReciprocalRankFusion
import yuku.alkitab.base.util.FormattedVerseText
import yuku.alkitab.model.Version

interface ThemeSemanticRanker : AutoCloseable {
    fun rank(query: String, allowedBooks: BooleanArray, limit: Int): List<RankedAri>
}

fun interface ThemeSemanticRankerFactory {
    fun create(pack: ModelPackState.Ready): ThemeSemanticRanker
}

/** Retrieval-only hybrid search. No query or Bible text leaves this process. */
class ThemeSearchEngine internal constructor(
    private val readyPack: () -> ModelPackState.Ready?,
    private val semanticFactory: ThemeSemanticRankerFactory,
    private val lexicalSearch: (Version, String, List<String>, BooleanArray, Int) -> List<RankedAri>,
    private val normalizer: QueryNormalizer,
    private val dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val runtimeLock = Any()
    private var runtimeKey: String? = null
    private var runtime: ThemeSemanticRanker? = null

    constructor(context: Context) : this(
        readyPack = ModelPackRepository(context)::readyPack,
        semanticFactory = ProductionSemanticRanker.factory(context.applicationContext),
        lexicalSearch = { version, versionId, tokens, allowedBooks, limit ->
            Bm25IndexCache.getOrBuild(
                context.cacheDir,
                version,
                versionId,
                versionRevision(versionId),
                QueryNormalizer.from(context.applicationContext),
            )
                .search(tokens, allowedBooks, limit)
        },
        normalizer = QueryNormalizer.from(context.applicationContext),
        dispatcher = Dispatchers.Default,
    )

    suspend fun search(
        version: Version,
        versionId: String,
        query: String,
        allowedBooks: BooleanArray,
        limit: Int = DEFAULT_RESULT_LIMIT,
    ): ThemeSearchResponse = withContext(dispatcher) {
        val started = System.nanoTime()
        val pack = readyPack() ?: return@withContext ThemeSearchResponse(
            aris = emptyList(),
            elapsedMs = elapsedSince(started),
            error = ThemeSearchError.PACK_REQUIRED,
        )
        if (query.isBlank() || limit <= 0) {
            return@withContext ThemeSearchResponse(emptyList(), elapsedSince(started))
        }

        try {
            val semantic = semanticRuntime(pack).rank(normalizer.semanticQuery(query), allowedBooks, CANDIDATE_LIMIT)
            val lexical = lexicalSearch(
                version,
                versionId,
                normalizer.expand(query),
                allowedBooks,
                CANDIDATE_LIMIT,
            )
            val aris = ReciprocalRankFusion.fuse(listOf(semantic, lexical), CANDIDATE_LIMIT)
                .asSequence()
                .map { it.ari }
                .filter { !FormattedVerseText.removeSpecialCodes(version.loadVerseText(it), true).isNullOrBlank() }
                .take(limit)
                .toList()
            ThemeSearchResponse(aris, elapsedSince(started))
        } catch (_: Exception) {
            ThemeSearchResponse(emptyList(), elapsedSince(started), ThemeSearchError.MODEL_ERROR)
        }
    }

    private fun semanticRuntime(pack: ModelPackState.Ready): ThemeSemanticRanker = synchronized(runtimeLock) {
        val key = "${pack.model.absolutePath}:${pack.tokenizer.absolutePath}"
        if (runtime == null || runtimeKey != key) {
            runtime?.close()
            runtime = semanticFactory.create(pack)
            runtimeKey = key
        }
        checkNotNull(runtime)
    }

    override fun close() = synchronized(runtimeLock) {
        runtime?.close()
        runtime = null
        runtimeKey = null
    }

    private fun elapsedSince(started: Long) = (System.nanoTime() - started) / 1_000_000

    companion object {
        const val DEFAULT_RESULT_LIMIT = 30
        private const val CANDIDATE_LIMIT = 200
    }
}

private fun versionRevision(versionId: String): Long {
    val versions = App.services.versions
    val model = versions.getVersionFromVersionId(versionId)
        ?: versions.activeMVersion().takeIf { it.versionId == versionId }
    return when (model) {
        is MVersionDb -> model.modifyTime.toLong().takeIf { it != 0L }
            ?: File(model.filename).lastModified()
        is MVersionPreset -> model.modifyTime.toLong()
        is MVersionInternal -> App.getVersionCode().toLong()
        else -> 0L
    }
}

private class ProductionSemanticRanker(
    private val embedding: GraniteEmbeddingEngine,
    private val index: SemanticIndexReader,
) : ThemeSemanticRanker {
    override fun rank(query: String, allowedBooks: BooleanArray, limit: Int): List<RankedAri> =
        index.topK(embedding.embed(query), allowedBooks, limit)

    override fun close() = embedding.close()

    companion object {
        fun factory(context: Context) = ThemeSemanticRankerFactory { pack ->
            ProductionSemanticRanker(
                embedding = GraniteEmbeddingEngine(
                    ByteLevelBpeTokenizer(TokenizerData.load(pack.tokenizer)),
                    OnnxEmbeddingSession(pack.model),
                ),
                index = SemanticIndexReader.fromBundledAsset(context),
            )
        }
    }
}
