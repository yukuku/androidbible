package yuku.alkitab.base.search.theme.model

import java.util.regex.Pattern

data class TokenizedInput(
    val inputIds: LongArray,
    val attentionMask: LongArray,
) {
    fun unpaddedIds(): LongArray = inputIds.copyOf(attentionMask.count { it == 1L })
}

/** Exact byte-level BPE path used by the pinned Granite tokenizer. */
class ByteLevelBpeTokenizer(
    private val data: TokenizerData,
) : EmbeddingTokenizer {
    override fun encode(text: String, maxLength: Int): TokenizedInput {
        require(maxLength >= 2) { "maxLength must hold start and return tokens" }
        val content = ArrayList<Int>()
        val matcher = PRE_TOKEN_PATTERN.matcher(text)
        while (matcher.find()) content += bpe(matcher.group())

        val kept = content.take(maxLength - 2)
        val ids = LongArray(maxLength) { data.padTokenId.toLong() }
        val mask = LongArray(maxLength)
        ids[0] = data.startTokenId.toLong()
        mask[0] = 1
        kept.forEachIndexed { index, token ->
            ids[index + 1] = token.toLong()
            mask[index + 1] = 1
        }
        val returnIndex = kept.size + 1
        ids[returnIndex] = data.returnTokenId.toLong()
        mask[returnIndex] = 1
        return TokenizedInput(ids, mask)
    }

    private fun bpe(piece: String): List<Int> {
        var tokens = piece.toByteArray(Charsets.UTF_8).map { byte ->
            val unsigned = byte.toInt() and 0xff
            data.byteTokenIds[unsigned].takeIf { it >= 0 }
                ?: error("Tokenizer cannot encode UTF-8 byte $unsigned")
        }
        while (tokens.size > 1) {
            var bestRule: Long? = null
            var bestRank = Int.MAX_VALUE
            for (i in 0 until tokens.lastIndex) {
                val rule = data.mergeRules[TokenizerData.pairKey(tokens[i], tokens[i + 1])] ?: continue
                val rank = TokenizerData.ruleRank(rule)
                if (rank < bestRank) {
                    bestRank = rank
                    bestRule = rule
                }
            }
            val selected = bestRule ?: break
            val mergedId = TokenizerData.mergedId(selected)
            val next = ArrayList<Int>(tokens.size)
            var i = 0
            while (i < tokens.size) {
                if (
                    i < tokens.lastIndex &&
                    data.mergeRules[TokenizerData.pairKey(tokens[i], tokens[i + 1])] == selected
                ) {
                    next += mergedId
                    i += 2
                } else {
                    next += tokens[i++]
                }
            }
            tokens = next
        }
        return tokens
    }

    companion object {
        // Stored verbatim from the pinned tokenizer's Split pre-tokenizer.
        private val PRE_TOKEN_PATTERN = Pattern.compile(
            "[^\\r\\n\\p{L}\\p{N}]?[\\p{Lu}\\p{Lt}\\p{Lm}\\p{Lo}\\p{M}]*[\\p{Ll}\\p{Lm}\\p{Lo}\\p{M}]+(?i:'s|'t|'re|'ve|'m|'ll|'d)?|" +
                "[^\\r\\n\\p{L}\\p{N}]?[\\p{Lu}\\p{Lt}\\p{Lm}\\p{Lo}\\p{M}]+[\\p{Ll}\\p{Lm}\\p{Lo}\\p{M}]*(?i:'s|'t|'re|'ve|'m|'ll|'d)?|" +
                "\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n/]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+",
        )

        internal fun bytesToUnicode(): CharArray {
            val visible = BooleanArray(256)
            (33..126).forEach { visible[it] = true }
            (161..172).forEach { visible[it] = true }
            (174..255).forEach { visible[it] = true }
            var extra = 0
            return CharArray(256) { byte ->
                if (visible[byte]) byte.toChar() else (256 + extra++).toChar()
            }
        }
    }
}

fun interface EmbeddingTokenizer {
    fun encode(text: String, maxLength: Int): TokenizedInput
}
