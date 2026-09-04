package yuku.alkitab.base.search.theme.pack

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ModelPackArtifact(
    val fileName: String,
    val url: String,
    val length: Long,
    val sha256: String,
)

@Serializable
data class ModelPackManifest(
    val schema: Int,
    val modelId: String,
    val revision: String,
    val model: ModelPackArtifact,
    val tokenizer: ModelPackArtifact,
) {
    companion object {
        private const val ASSET = "offline_search/pack_manifest.json"

        fun load(context: Context): ModelPackManifest = context.assets.open(ASSET).bufferedReader().use {
            Json { ignoreUnknownKeys = false }.decodeFromString(serializer(), it.readText())
        }
    }
}
