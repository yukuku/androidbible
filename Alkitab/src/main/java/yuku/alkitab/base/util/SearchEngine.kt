package yuku.alkitab.base.util

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.SparseBooleanArray
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.model.Book
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList
import java.util.Locale

object SearchEngine {
    private val TAG = SearchEngine::class.java.simpleName

    /**
     * Contains processed tokens that is more efficient to be passed in to methods here such as
     * [hilite] and [satisfiesTokens].
     */
    class ReadyTokens @JvmOverloads constructor(
        inputTokens: Array<String>,
        /** Match against the original text instead of the lowercased one. */
        val caseSensitive: Boolean = false,
        looseStarts: BooleanArray? = null,
        looseEnds: BooleanArray? = null,
    ) {
        val tokenCount: Int = inputTokens.size
        val hasPlusses: BooleanArray = BooleanArray(tokenCount) { i ->
            QueryTokenizer.isPlussedToken(inputTokens[i])
        }
        /** Already without plusses */
        val tokens: Array<String> = Array(tokenCount) { i ->
            val token = inputTokens[i]
            if (hasPlusses[i]) QueryTokenizer.tokenWithoutPlus(token) else token
        }
        val multiwordsTokens: Array<Array<String>?> = Array(tokenCount) { i ->
            if (hasPlusses[i]) QueryTokenizer.tokenizeMultiwordToken(tokens[i]) else null
        }
        /** First word of a phrase may start mid-word. */
        val looseStarts: BooleanArray = looseStarts ?: BooleanArray(tokenCount)
        /** Last word of a phrase may end mid-word. */
        val looseEnds: BooleanArray = looseEnds ?: BooleanArray(tokenCount)
        /** ALL-CAPS form of each token, only when [caseSensitive]. */
        val upperTokens: Array<String?> = Array(tokenCount) { i ->
            if (caseSensitive) upperAlternative(tokens[i]) else null
        }
        val upperMultiwordsTokens: Array<Array<String?>?> = Array(tokenCount) { i ->
            if (caseSensitive) multiwordsTokens[i]?.let { words -> Array(words.size) { j -> upperAlternative(words[j]) } } else null
        }

        companion object {
            /** Longest token first, since it narrows the results fastest. */
            @JvmStatic
            fun forQuery(query: String?, options: SearchOptions): ReadyTokens {
                val rawTokens = QueryTokenizer.tokenize(query, lowercase = !options.matchCapitals)

                if (options.exactPhrase && rawTokens.size >= 2) {
                    val words = rawTokens.flatMap { QueryTokenizer.splitWords(QueryTokenizer.tokenWithoutPlus(it)) }
                    if (words.size >= 2) {
                        return ReadyTokens(
                            arrayOf("+" + words.joinToString(" ")),
                            caseSensitive = options.matchCapitals,
                            looseStarts = booleanArrayOf(!options.wholeWords && !QueryTokenizer.isPlussedToken(rawTokens.first())),
                            looseEnds = booleanArrayOf(!options.wholeWords && !QueryTokenizer.isPlussedToken(rawTokens.last())),
                        )
                    }
                }

                val tokens = if (options.wholeWords) {
                    rawTokens.map { if (QueryTokenizer.isPlussedToken(it)) it else "+$it" }
                } else {
                    rawTokens.toList()
                }

                val sorted = tokens
                    .sortedWith(compareByDescending<String> { it.length }.thenBy { it })
                    .distinct()

                return ReadyTokens(sorted.toTypedArray(), caseSensitive = options.matchCapitals)
            }

            /**
             * ALL-CAPS version of the word, so "Lord" also finds "LORD".
             * Null if uppercasing changes the length (like German "ß" becoming "SS").
             */
            private fun upperAlternative(word: String): String? {
                val upper = word.uppercase(Locale.ROOT)
                return if (upper != word && upper.length == word.length) upper else null
            }
        }
    }

    @JvmStatic
    fun searchByGrep(version: Version, query: SearchEngineQuery): IntArrayList {
        val rt = ReadyTokens.forQuery(query.query_string, query.options)
        AppLog.d(TAG, "tokens = ${rt.tokens.contentToString()}")

        val bookIds = query.bookIds ?: SparseBooleanArray()

        // really search
        var result: IntArrayList? = null

        for (i in 0 until rt.tokenCount) {
            val prev = result
            val ms = System.currentTimeMillis()
            result = searchByGrepInside(version, rt, i, prev, bookIds)
            AppLog.d(TAG, "search token '${rt.tokens[i]}' needed: ${System.currentTimeMillis() - ms} ms")

            if (prev != null) {
                AppLog.d(TAG, "Will intersect ${prev.size()} elements with ${result.size()} elements...")
                result = intersect(prev, result)
                AppLog.d(TAG, "... the result is ${result.size()} elements")
            }
        }

        return result ?: IntArrayList()
    }

    private fun intersect(a: IntArrayList, b: IntArrayList): IntArrayList {
        val res = IntArrayList(a.size())

        val aa = a.buffer()
        val bb = b.buffer()
        val alen = a.size()
        val blen = b.size()

        var apos = 0
        var bpos = 0

        while (true) {
            if (apos >= alen) break
            if (bpos >= blen) break

            val av = aa[apos]
            val bv = bb[bpos]

            when {
                av == bv -> { res.add(av); apos++; bpos++ }
                av > bv -> bpos++
                else -> apos++ // av < bv
            }
        }

        return res
    }

    /**
     * Return the next ari (with only book and chapter) after the lastAriBc by scanning the source starting from pos.
     *
     * @param ppos pointer to pos. pos will be changed to ONE AFTER THE FOUND POSITION. So do not do another increment (++) outside this method.
     */
    private fun nextAri(source: IntArrayList, ppos: IntArray, lastAriBc: Int): Int {
        val s = source.buffer()
        val len = source.size()
        var pos = ppos[0]

        while (true) {
            if (pos >= len) return 0x0

            val curAri = s[pos]
            val curAriBc = Ari.toBookChapter(curAri)

            if (curAriBc != lastAriBc) {
                pos++
                ppos[0] = pos
                return curAriBc
            } else {
                pos++
            }
        }
    }

    internal fun searchByGrepInside(version: Version, rt: ReadyTokens, tokenIndex: Int, source: IntArrayList?, bookIds: SparseBooleanArray): IntArrayList {
        val res = IntArrayList()

        if (source == null) {
            for (book in version.consecutiveBooks) {
                if (!bookIds.get(book.bookId, false)) {
                    continue // the book is not included in selected books to be searched
                }

                for (chapter1 in 1..book.chapter_count) {
                    val ariBc = Ari.encode(book.bookId, chapter1, 0)
                    searchByGrepForOneChapter(version, book, chapter1, rt, tokenIndex, ariBc, res)
                }

                if (BuildConfig.DEBUG) AppLog.d(TAG, "searchByGrepInside book ${book.shortName} done. res.size = ${res.size()}")
            }
        } else {
            var count = 0

            val ppos = intArrayOf(0)
            var curAriBc = 0x000000

            while (true) {
                curAriBc = nextAri(source, ppos, curAriBc)
                if (curAriBc == 0) break

                // No need to check null book, because we go here only after searching a previous
                // token based on getConsecutiveBooks, which is impossible to have null books.
                val book = version.getBook(Ari.toBook(curAriBc)) ?: continue
                val chapter1 = Ari.toChapter(curAriBc)

                searchByGrepForOneChapter(version, book, chapter1, rt, tokenIndex, curAriBc, res)

                count++
            }

            if (BuildConfig.DEBUG) AppLog.d(TAG, "searchByGrepInside book with source ${source.size()} needed to read as many as $count book-chapter. res.size=${res.size()}")
        }

        return res
    }

    /**
     * @param res (output) result aris
     * @param ariBc book-chapter ari, with verse must be set to 0
     */
    private fun searchByGrepForOneChapter(version: Version, book: Book, chapter1: Int, rt: ReadyTokens, tokenIndex: Int, ariBc: Int, res: IntArrayList) {
        val oneChapter = if (rt.caseSensitive) {
            version.loadChapterTextWithoutSplit(book, chapter1)
        } else {
            version.loadChapterTextLowercasedWithoutSplit(book, chapter1)
        } ?: return

        var verse0 = 0
        var lastV = -1

        val consumedLengthPtr = intArrayOf(0)

        var curPosToken = indexOfToken(oneChapter, rt, tokenIndex, 0, true, consumedLengthPtr)
        if (curPosToken == -1) return

        var posN = oneChapter.indexOf('\n')

        while (true) {
            if (posN < curPosToken) {
                verse0++
                posN = oneChapter.indexOf('\n', posN + 1)
                if (posN == -1) return
            } else {
                if (verse0 != lastV) {
                    res.add(ariBc + verse0 + 1) // +1 to make it verse_1
                    lastV = verse0
                }
                curPosToken = indexOfToken(oneChapter, rt, tokenIndex, curPosToken + consumedLengthPtr[0], true, consumedLengthPtr)
                if (curPosToken == -1) return
            }
        }
    }

    /**
     * @param consumedLengthPtr (output) length of the match in [text]
     * @return -1 or position of the token
     */
    private fun indexOfToken(text: String, rt: ReadyTokens, tokenIndex: Int, start: Int, isNewlineDelimitedText: Boolean, consumedLengthPtr: IntArray): Int {
        val multiword = rt.multiwordsTokens[tokenIndex]
        if (multiword != null) {
            return indexOfMultiword(
                text, multiword, rt.upperMultiwordsTokens[tokenIndex], start, isNewlineDelimitedText,
                rt.looseStarts[tokenIndex], rt.looseEnds[tokenIndex], consumedLengthPtr,
            )
        }

        val token = rt.tokens[tokenIndex]
        val whole = rt.hasPlusses[tokenIndex]
        consumedLengthPtr[0] = token.length
        return indexOfWord(text, token, rt.upperTokens[tokenIndex], start, whole, whole)
    }

    /**
     * Unless [ReadyTokens.caseSensitive], [s] must already be lowercased.
     */
    @JvmStatic
    fun satisfiesTokens(s: String, rt: ReadyTokens): Boolean {
        val consumedLengthPtr = intArrayOf(0)
        for (i in 0 until rt.tokenCount) {
            if (indexOfToken(s, rt, i, 0, false, consumedLengthPtr) == -1) return false
        }
        return true
    }

    /**
     * Finds [word] or [upperWord], optionally with no letter or digit right before/after it.
     * This works well only if the word is not a multiword.
     *
     * @param text haystack
     * @param word needle
     * @param start start at character
     * @return -1 or position of the word
     */
    private fun indexOfWord(text: String, word: String, upperWord: String?, start: Int, wholeLeft: Boolean, wholeRight: Boolean): Int {
        val len = text.length
        var s = start

        while (true) {
            val pos = indexOfEither(text, word, upperWord, s)
            if (pos == -1) return -1

            // check left
            if (wholeLeft && pos != 0 && Character.isLetterOrDigit(text[pos - 1])) {
                if (pos != 1 && text[pos - 2] == '@') {
                    // oh, before this word there is a tag. Then it is OK.
                } else {
                    s = pos + 1
                    continue
                }
            }

            // check right
            val end = pos + word.length
            if (wholeRight && end != len && Character.isLetterOrDigit(text[end])) {
                s = pos + 1
                continue
            }

            return pos
        }
    }

    /** [alternative] must be as long as [word]. */
    private fun indexOfEither(text: String, word: String, alternative: String?, start: Int): Int {
        val a = text.indexOf(word, start)
        if (alternative == null) return a
        val b = text.indexOf(alternative, start)
        return when {
            a == -1 -> b
            b == -1 -> a
            else -> minOf(a, b)
        }
    }

    /**
     * This looks for a multiword that is surrounded by non-letter characters.
     * This works for multiword because it tries to strip tags and punctuations from the text before matching.
     *
     * @param text haystack.
     * @param multiword multiword that has been split into words. Must have at least one element.
     * @param upperMultiword ALL-CAPS form of each word, or null.
     * @param start character index of text to start searching from
     * @param isNewlineDelimitedText [text] has '\n' as delimiter between verses. [multiword] cannot be searched across different verses.
     * @param looseStart first word may start mid-word.
     * @param looseEnd last word may end mid-word.
     * @param consumedLengthPtr (length-1 array output) how many characters matched from the source text to satisfy the multiword. Will be 0 if this method returns -1.
     * @return -1 or position of the multiword.
     */
    private fun indexOfMultiword(
        text: String,
        multiword: Array<String>,
        upperMultiword: Array<String?>?,
        start: Int,
        isNewlineDelimitedText: Boolean,
        looseStart: Boolean,
        looseEnd: Boolean,
        consumedLengthPtr: IntArray?,
    ): Int {
        val len = text.length
        val lastIndex = multiword.size - 1
        val firstWord = multiword[0]
        var s = start

        findAllWords@ while (true) {
            val firstPos = indexOfWord(text, firstWord, upperMultiword?.get(0), s, !looseStart, !(looseEnd && lastIndex == 0))
            if (firstPos == -1) {
                if (consumedLengthPtr != null) consumedLengthPtr[0] = 0
                return -1
            }

            var pos = firstPos + firstWord.length

            for (i in 1..lastIndex) {
                val posBeforeConsume = pos
                // consume!
                while (pos < len) {
                    val c = text[pos]
                    if (c == '@') {
                        if (pos == len - 1) {
                            // bad data (nothing after '@')
                        } else {
                            pos++
                            val d = text[pos]
                            if (d == '<') {
                                val closingTagStart = text.indexOf("@>", pos + 1)
                                if (closingTagStart == -1) {
                                    // bad data (no closing tag)
                                } else {
                                    pos = closingTagStart + 1
                                }
                            }
                            // else: single-letter formatting code, move on...
                        }
                    } else if (Character.isLetterOrDigit(c)) {
                        break
                    } else if (isNewlineDelimitedText && c == '\n') {
                        // can't cross verse boundary, so we give up and try from beginning again
                        s = pos + 1
                        continue@findAllWords
                    }
                    // else: non-letter, move on...
                    pos++
                }

                if (BuildConfig.DEBUG) {
                    AppLog.d(TAG, "=========================")
                    AppLog.d(TAG, "multiword: ${multiword.contentToString()}")
                    AppLog.d(TAG, "text     : #${text.substring(maxOf(0, posBeforeConsume - multiword[i - 1].length), minOf(len, posBeforeConsume + 80))}#")
                    AppLog.d(TAG, "skipped  : #${text.substring(posBeforeConsume, pos)}#")
                    AppLog.d(TAG, "=========================////")
                }

                // left side is already a boundary, we just skipped non-letters
                val word = multiword[i]
                val upperWord = upperMultiword?.get(i)
                val matchesHere = text.startsWith(word, pos) || (upperWord != null && text.startsWith(upperWord, pos))
                val end = pos + word.length
                val wholeRight = i != lastIndex || !looseEnd
                if (!matchesHere || (wholeRight && end != len && Character.isLetterOrDigit(text[end]))) {
                    s = pos
                    continue@findAllWords
                }

                pos = end
            }

            // all words are found!
            if (consumedLengthPtr != null) consumedLengthPtr[0] = pos - firstPos
            return firstPos
        }
    }

    @JvmStatic
    fun hilite(s: CharSequence, rt: ReadyTokens?, hiliteColor: Int): SpannableStringBuilder {
        val res = SpannableStringBuilder(s)

        if (rt == null) return res

        val tokenCount = rt.tokenCount

        // lowercase without changing any offsets
        val plainText = if (rt.caseSensitive) {
            s.toString()
        } else {
            val newString = CharArray(s.length) { i ->
                val c = s[i]
                if (c in 'A'..'Z') (c.code or 0x20).toChar() else c.lowercaseChar()
            }
            String(newString)
        }

        var pos = 0
        val attempts = IntArray(tokenCount)
        val consumedLengths = IntArray(tokenCount)

        val consumedLengthPtr = intArrayOf(0)
        while (true) {
            for (i in 0 until tokenCount) {
                attempts[i] = indexOfToken(plainText, rt, i, pos, false, consumedLengthPtr)
                consumedLengths[i] = consumedLengthPtr[0]
            }

            // from the attempts above, find the earliest
            var minpos = Integer.MAX_VALUE
            var mintokenindex = -1

            for (i in 0 until tokenCount) {
                if (attempts[i] >= 0 && attempts[i] < minpos) {
                    minpos = attempts[i]
                    mintokenindex = i
                }
            }

            if (mintokenindex == -1) break

            val topos = minpos + consumedLengths[mintokenindex]
            res.setSpan(StyleSpan(Typeface.BOLD), minpos, topos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            res.setSpan(ForegroundColorSpan(hiliteColor), minpos, topos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            pos = topos
        }

        return res
    }
}
