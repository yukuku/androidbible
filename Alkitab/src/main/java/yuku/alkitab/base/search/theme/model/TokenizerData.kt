package yuku.alkitab.base.search.theme.model

import com.google.gson.stream.JsonReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

/** Compact runtime form of the pinned tokenizer: byte tokens plus ID-based BPE rules. */
class TokenizerData internal constructor(
    internal val byteTokenIds: IntArray,
    internal val mergeRules: Map<Long, Long>,
    val startTokenId: Int,
    val returnTokenId: Int,
    val padTokenId: Int,
) {
    companion object {
        fun load(file: File): TokenizerData = file.inputStream().buffered().use(::load)

        fun load(input: InputStream): TokenizerData {
            val reader = JsonReader(InputStreamReader(input, Charsets.UTF_8))
            val specialIds = mutableMapOf<String, Int>()
            var vocab: HashMap<String, Int>? = null
            var mergeRules: HashMap<Long, Long>? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "added_tokens" -> readAddedTokens(reader, specialIds)
                    "model" -> {
                        val parsed = readModel(reader)
                        vocab = parsed.first
                        mergeRules = parsed.second
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            reader.close()

            val tokenIds = requireNotNull(vocab) { "Tokenizer vocab is missing" }
            val bytesToUnicode = ByteLevelBpeTokenizer.bytesToUnicode()
            val byteTokenIds = IntArray(256) { byte ->
                tokenIds[bytesToUnicode[byte].toString()] ?: -1
            }
            return TokenizerData(
                byteTokenIds = byteTokenIds,
                mergeRules = requireNotNull(mergeRules) { "Tokenizer merges are missing" },
                startTokenId = specialIds.getValue("<|startoftext|>"),
                returnTokenId = specialIds.getValue("<|return|>"),
                padTokenId = specialIds.getValue("<|endoftext|>"),
            )
        }

        private fun readAddedTokens(reader: JsonReader, output: MutableMap<String, Int>) {
            reader.beginArray()
            while (reader.hasNext()) {
                var id: Int? = null
                var content: String? = null
                var special = false
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "id" -> id = reader.nextInt()
                        "content" -> content = reader.nextString()
                        "special" -> special = reader.nextBoolean()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (special && id != null && content != null) output[content] = id
            }
            reader.endArray()
        }

        private fun readModel(reader: JsonReader): Pair<HashMap<String, Int>, HashMap<Long, Long>> {
            val vocab = HashMap<String, Int>(240_000)
            val rules = HashMap<Long, Long>(560_000)
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "vocab" -> {
                        reader.beginObject()
                        while (reader.hasNext()) vocab[reader.nextName()] = reader.nextInt()
                        reader.endObject()
                    }
                    "merges" -> {
                        check(vocab.isNotEmpty()) { "Pinned tokenizer must list vocab before merges" }
                        reader.beginArray()
                        var rank = 0
                        while (reader.hasNext()) {
                            reader.beginArray()
                            val left = reader.nextString()
                            val right = reader.nextString()
                            while (reader.hasNext()) reader.skipValue()
                            reader.endArray()
                            val leftId = vocab[left] ?: error("Unknown left merge token")
                            val rightId = vocab[right] ?: error("Unknown right merge token")
                            val mergedId = vocab[left + right] ?: error("Unknown merged token")
                            rules[pairKey(leftId, rightId)] = packRule(rank++, mergedId)
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return vocab to rules
        }

        internal fun pairKey(left: Int, right: Int): Long =
            (left.toLong() shl 32) or (right.toLong() and 0xffff_ffffL)

        private fun packRule(rank: Int, mergedId: Int): Long =
            (rank.toLong() shl 32) or (mergedId.toLong() and 0xffff_ffffL)

        internal fun ruleRank(rule: Long): Int = (rule ushr 32).toInt()
        internal fun mergedId(rule: Long): Int = rule.toInt()
    }
}
