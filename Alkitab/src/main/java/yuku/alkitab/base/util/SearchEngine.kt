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

object SearchEngine {
    private val TAG = SearchEngine::class.java.simpleName

    /**
     * Contains processed tokens that is more efficient to be passed in to methods here such as
     * [hilite] and [satisfiesTokens].
     */
    class ReadyTokens(inputTokens: Array<String>) {
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
    }

    @JvmStatic
    fun searchByGrep(version: Version, query: SearchEngineQuery): IntArrayList {
        var tokens = QueryTokenizer.tokenize(query.query_string)

        // sort by word length, then alphabetically
        tokens.sortWith { o1, o2 ->
            val len1 = o1.length
            val len2 = o2.length
            when {
                len1 > len2 -> -1
                len1 == len2 -> o1.compareTo(o2)
                else -> 1
            }
        }

        // remove duplicates
        run {
            val atokens = mutableListOf<String>()
            var last: String? = null
            for (token in tokens) {
                if (token != last) {
                    atokens.add(token)
                }
                last = token
            }
            tokens = atokens.toTypedArray()
            AppLog.d(TAG, "tokens = ${tokens.contentToString()}")
        }

        val bookIds = query.bookIds ?: SparseBooleanArray()

        // really search
        var result: IntArrayList? = null

        for (token in tokens) {
            val prev = result
            val ms = System.currentTimeMillis()
            result = searchByGrepInside(version, token, prev, bookIds)
            AppLog.d(TAG, "search token '$token' needed: ${System.currentTimeMillis() - ms} ms")

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

    internal fun searchByGrepInside(version: Version, tokenIn: String, source: IntArrayList?, bookIds: SparseBooleanArray): IntArrayList {
        val res = IntArrayList()
        var token = tokenIn
        val hasPlus = QueryTokenizer.isPlussedToken(token)

        if (hasPlus) {
            token = QueryTokenizer.tokenWithoutPlus(token)
        }

        if (source == null) {
            for (book in version.consecutiveBooks) {
                if (!bookIds.get(book.bookId, false)) {
                    continue // the book is not included in selected books to be searched
                }

                for (chapter1 in 1..book.chapter_count) {
                    val ariBc = Ari.encode(book.bookId, chapter1, 0)
                    searchByGrepForOneChapter(version, book, chapter1, token, hasPlus, ariBc, res)
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

                searchByGrepForOneChapter(version, book, chapter1, token, hasPlus, curAriBc, res)

                count++
            }

            if (BuildConfig.DEBUG) AppLog.d(TAG, "searchByGrepInside book with source ${source.size()} needed to read as many as $count book-chapter. res.size=${res.size()}")
        }

        return res
    }

    /**
     * @param token searched token without plusses
     * @param res (output) result aris
     * @param ariBc book-chapter ari, with verse must be set to 0
     * @param hasPlus whether the token had plus
     */
    private fun searchByGrepForOneChapter(version: Version, book: Book, chapter1: Int, token: String, hasPlus: Boolean, ariBc: Int, res: IntArrayList) {
        val oneChapter = version.loadChapterTextLowercasedWithoutSplit(book, chapter1) ?: return

        var verse0 = 0
        var lastV = -1

        var multiword: Array<String>? = null
        val consumedLengthPtr = intArrayOf(0)

        val initPosToken: Int
        val initConsumedLength: Int

        if (hasPlus) {
            multiword = QueryTokenizer.tokenizeMultiwordToken(token)

            if (multiword != null) {
                initPosToken = indexOfWholeMultiword(oneChapter, multiword, 0, true, consumedLengthPtr)
                initConsumedLength = consumedLengthPtr[0]
            } else {
                initPosToken = indexOfWholeWord(oneChapter, token, 0)
                initConsumedLength = token.length
            }
        } else {
            initPosToken = oneChapter.indexOf(token)
            initConsumedLength = token.length
        }

        if (initPosToken == -1) return

        var curPosToken = initPosToken
        var curConsumedLength = initConsumedLength
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
                if (hasPlus) {
                    if (multiword != null) {
                        curPosToken = indexOfWholeMultiword(oneChapter, multiword, curPosToken + curConsumedLength, true, consumedLengthPtr)
                        curConsumedLength = consumedLengthPtr[0]
                    } else {
                        curPosToken = indexOfWholeWord(oneChapter, token, curPosToken + curConsumedLength)
                        curConsumedLength = token.length
                    }
                } else {
                    curPosToken = oneChapter.indexOf(token, curPosToken + curConsumedLength)
                    curConsumedLength = token.length
                }
                if (curPosToken == -1) return
            }
        }
    }

    /**
     * Case sensitive! Make sure [s] and [rt] tokens have been lowercased (or normalized).
     */
    @JvmStatic
    fun satisfiesTokens(s: String, rt: ReadyTokens): Boolean {
        for (i in 0 until rt.tokenCount) {
            val hasPlus = rt.hasPlusses[i]

            val posToken: Int
            if (hasPlus) {
                val multiwordTokens = rt.multiwordsTokens[i]
                posToken = if (multiwordTokens != null) {
                    indexOfWholeMultiword(s, multiwordTokens, 0, false, null)
                } else {
                    indexOfWholeWord(s, rt.tokens[i], 0)
                }
            } else {
                posToken = s.indexOf(rt.tokens[i])
            }

            if (posToken == -1) return false
        }
        return true
    }

    /**
     * This looks for a word that is surrounded by non-letter-or-digit characters.
     * This works well only if the word is not a multiword.
     *
     * @param text haystack
     * @param word needle
     * @param start start at character
     * @return -1 or position of the word
     */
    private fun indexOfWholeWord(text: String, word: String, start: Int): Int {
        val len = text.length
        var s = start

        while (true) {
            val pos = text.indexOf(word, s)
            if (pos == -1) return -1

            // check left
            if (pos != 0 && Character.isLetterOrDigit(text[pos - 1])) {
                if (pos != 1 && text[pos - 2] == '@') {
                    // oh, before this word there is a tag. Then it is OK.
                } else {
                    s = pos + 1
                    continue
                }
            }

            // check right
            val end = pos + word.length
            if (end != len && Character.isLetterOrDigit(text[end])) {
                s = pos + 1
                continue
            }

            return pos
        }
    }

    /**
     * This looks for a multiword that is surrounded by non-letter characters.
     * This works for multiword because it tries to strip tags and punctuations from the text before matching.
     *
     * @param text haystack.
     * @param multiword multiword that has been split into words. Must have at least one element.
     * @param start character index of text to start searching from
     * @param isNewlineDelimitedText [text] has '\n' as delimiter between verses. [multiword] cannot be searched across different verses.
     * @param consumedLengthPtr (length-1 array output) how many characters matched from the source text to satisfy the multiword. Will be 0 if this method returns -1.
     * @return -1 or position of the multiword.
     */
    private fun indexOfWholeMultiword(text: String, multiword: Array<String>, start: Int, isNewlineDelimitedText: Boolean, consumedLengthPtr: IntArray?): Int {
        val len = text.length
        val firstWord = multiword[0]
        var s = start

        findAllWords@ while (true) {
            val firstPos = indexOfWholeWord(text, firstWord, s)
            if (firstPos == -1) {
                if (consumedLengthPtr != null) consumedLengthPtr[0] = 0
                return -1
            }

            var pos = firstPos + firstWord.length

            for (i in 1 until multiword.size) {
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

                val word = multiword[i]

                val foundWordStart = indexOfWholeWord(text, word, pos)
                if (foundWordStart == -1 || foundWordStart != pos) {
                    s = pos
                    continue@findAllWords
                }

                pos = foundWordStart + word.length
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

        // from source text, produce a plain text lowercased
        val newString = CharArray(s.length) { i ->
            val c = s[i]
            if (c in 'A'..'Z') (c.code or 0x20).toChar() else c.lowercaseChar()
        }
        val plainText = String(newString)

        var pos = 0
        val attempts = IntArray(tokenCount)
        val consumedLengths = IntArray(tokenCount)

        val hasPlusses = rt.hasPlusses
        val tokens = rt.tokens
        val multiwordsTokens = rt.multiwordsTokens

        val consumedLengthPtr = intArrayOf(0)
        while (true) {
            for (i in 0 until tokenCount) {
                if (hasPlusses[i]) {
                    val mwt = multiwordsTokens[i]
                    if (mwt != null) {
                        attempts[i] = indexOfWholeMultiword(plainText, mwt, pos, false, consumedLengthPtr)
                        consumedLengths[i] = consumedLengthPtr[0]
                    } else {
                        attempts[i] = indexOfWholeWord(plainText, tokens[i], pos)
                        consumedLengths[i] = tokens[i].length
                    }
                } else {
                    attempts[i] = plainText.indexOf(tokens[i], pos)
                    consumedLengths[i] = tokens[i].length
                }
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
