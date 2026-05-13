package yuku.alkitab.base.util

import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

object QueryTokenizer {
    private val oneToken = Pattern.compile("(\\+?)((?:[\"\\u201c\\u201d].*?[\"\\u201c\\u201d]|\\S)+)")
    private val pattern_letters = Pattern.compile("[\\p{javaLetterOrDigit}'-]+")

    /**
     * Convert a query string into tokens. Takes care of the quotes.
     * A single word without quotes means: look for words that contain it. So 'word' matches 'sword'.
     * A single word between quotes means: look for the exact word (aka whole-word). So '"word"' only matches 'word', not 'words' or 'sword'
     * Multiple words between quotes: look for the exact ordering of those words, and all of them needs to be exact. So '"mama papa"' matches 'mama papa' but not 'papa mama' or 'mamas papas' or 'mama papaya'
     *
     * @return List of tokens, starting with the character '+' if it is to be matched in a whole-word/whole-phrase manner.
     * No tokens will be an empty string or "+" (just a plus sign). After the optional '+', there will not be another '+'.
     */
    @JvmStatic
    fun tokenize(query: String?): Array<String> {
        if (query.isNullOrEmpty()) return emptyArray()

        val rawTokens = mutableListOf<String>()

        val matcher = oneToken.matcher(query.lowercase(Locale.getDefault()))
        while (matcher.find()) {
            rawTokens.add((matcher.group(1) ?: "") + matcher.group(2)!!)
        }

        val processed = mutableListOf<String>()
        for (rawTokenOrig in rawTokens) {
            var rawToken = rawTokenOrig
            var plussed = false

            while (true) {
                when {
                    rawToken.isNotEmpty() && rawToken[0] == '+' -> {
                        plussed = true
                        rawToken = rawToken.substring(1)
                    }
                    rawToken.length >= 2 && isQuoteChar(rawToken[0]) && isQuoteChar(rawToken[rawToken.length - 1]) -> {
                        plussed = true
                        rawToken = rawToken.substring(1, rawToken.length - 1)
                    }
                    rawToken.length >= 2 && isQuoteChar(rawToken[0]) -> {
                        plussed = true
                        rawToken = rawToken.substring(1)
                    }
                    else -> break
                }
            }

            if (rawToken.isNotEmpty()) {
                processed.add(if (plussed) "+$rawToken" else rawToken)
            }
        }

        return processed.toTypedArray()
    }

    private fun isQuoteChar(c: Char) = c == '"' || c == '“' || c == '”'

    @JvmStatic
    fun isPlussedToken(token: String) = token.isNotEmpty() && token[0] == '+'

    /**
     * Removes a single '+' from the [token] if exists.
     *
     * @param token may start or not start with '+'
     */
    @JvmStatic
    fun tokenWithoutPlus(token: String): String {
        return if (token.isNotEmpty() && token[0] == '+') token.substring(1) else token
    }

    @JvmStatic
    fun matcherizeTokens(tokens: Array<String>): Array<Matcher> {
        return Array(tokens.size) { i ->
            val token = tokens[i]
            if (isPlussedToken(token)) {
                Pattern.compile("\\b" + Pattern.quote(tokenWithoutPlus(token)) + "\\b", Pattern.CASE_INSENSITIVE).matcher("")
            } else {
                Pattern.compile(Pattern.quote(token), Pattern.CASE_INSENSITIVE).matcher("")
            }
        }
    }

    /**
     * Splits a plussed multiword token (from quoted-phrase input) into its constituent words
     * using the [pattern_letters] pattern. Apostrophe `'` and hyphen `-` are treated as word
     * characters so embedded ones (e.g. `don't`, `self-aware`) stay intact; a standalone `-`
     * surrounded by non-word characters becomes its own "word".
     *
     * @return null if the input produces fewer than two words (i.e. it is not actually a multiword).
     */
    internal fun tokenizeMultiwordToken(token: String): Array<String>? {
        val res = mutableListOf<String>()
        val m = pattern_letters.matcher(token)
        while (m.find()) {
            res.add(m.group())
        }

        if (res.size <= 1) return null
        return res.toTypedArray()
    }
}
