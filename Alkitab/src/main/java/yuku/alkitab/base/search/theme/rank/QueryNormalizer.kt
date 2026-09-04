package yuku.alkitab.base.search.theme.rank

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class QueryNormalizer(
    synonyms: Map<String, List<String>> = emptyMap(),
) {
    private val groups: Map<String, List<String>> = buildMap {
        synonyms.forEach { (root, words) ->
            val group = (listOf(root) + words).flatMap(::tokens).distinct().sorted()
            group.forEach { word -> put(word, group) }
        }
    }

    fun tokens(text: String): List<String> = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotBlank() }

    fun expand(text: String, maxPerToken: Int = 8): List<String> = tokens(text)
        .flatMap { token -> groups[token]?.take(maxPerToken) ?: listOf(token) }
        .distinct()

    companion object {
        fun from(context: Context): QueryNormalizer {
            val root = context.assets.open("offline_search/theme_synonyms_id_en.json").bufferedReader().use {
                Json.parseToJsonElement(it.readText()).jsonObject
            }
            return QueryNormalizer(root.mapValues { (_, value) -> value.jsonArray.map { it.jsonPrimitive.content } })
        }
    }
}
