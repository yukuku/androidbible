package yuku.alkitab.base.util

import java.util.Locale
import java.util.WeakHashMap
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.model.Book

class Jumper {
    fun interface Logger {
        fun d(msg: String)
    }

    // default logger
    private var logger: Logger = Logger { msg -> AppLog.d(TAG, msg) }

    private var p_book: String? = null
    private var p_chapter: Int = 0
    private var p_verse: Int = 0

    /**
     * The reference string is a verse range, with dash as delimiter
     */
    private var p_hasRange: Boolean = false

    /**
     * If bookId found from OSIS book names, set this to other than -1 and this will be returned
     */
    private var p_bookIdFromOsis: Int = -1

    val parseSucceeded: Boolean

    /**
     * Parse with default logger.
     */
    constructor(referenceToParse: String?) {
        parseSucceeded = parse(referenceToParse!!)
    }

    constructor(referenceToParse: String?, logger: Logger) {
        this.logger = logger
        parseSucceeded = parse(referenceToParse!!)
    }

    class BookRef {
        var condensed: String = ""
        var bookId: Int = 0

        override fun toString(): String = "$condensed:$bookId"
    }

    private fun parse0(referenceIn: String): Boolean {
        var reference = referenceIn.trim()

        if (reference.isEmpty()) {
            return false
        }

        if (BuildConfig.DEBUG) logger.d("jumper stage 0: $reference")

        //# STAGE 4: replace en-dash and em-dash to normal dash
        if (reference.contains('–') || reference.contains('—')) {
            reference = reference.replace(Regex("[–—]"), "-")

            if (BuildConfig.DEBUG) logger.d("jumper stage 4: $reference")
        }

        //# STAGE 5: Remove spaces on the left and right of "-"
        if (reference.indexOf('-') >= 0) {
            reference = reference.replace(Regex("\\s+-\\s+|\\s+-|-\\s+"), "-")

            if (BuildConfig.DEBUG) logger.d("jumper stage 5: $reference")
        }

        //# STAGE 7: Check whether this is in strict osis ID format.
        // This can be BookName.Chapter.Verse or BookName.Chapter
        // Or, either of the above separated by a '-'
        run notosis@{
            if (reference.indexOf('.') < 0) {
                return@notosis // must contain a dot
            }

            val osisId: String
            if (reference.indexOf('-') >= 0) { // optionally a '-'
                val osisIds = reference.split("-")
                if (osisIds.size != 2) {
                    return@notosis // wrong format
                }
                osisId = osisIds[0]
                p_hasRange = true
            } else {
                osisId = reference
            }

            val p = OsisBookNames.bookNameWithChapterAndOptionalVerseRegex
            val m = p.matchEntire(osisId)
            if (m != null) {
                if (BuildConfig.DEBUG) logger.d("jumper stage 7: ref matching osis pattern found: $osisId")
                val groups = m.groupValues
                val osisBookName = groups[1]
                val chapter_s = groups[2]
                val verse_s = groups[3]

                try {
                    p_bookIdFromOsis = OsisBookNames.osisBookNameToBookId(osisBookName)
                    p_chapter = chapter_s.toInt()
                    p_verse = if (verse_s.isEmpty()) 0 else verse_s.toInt()
                } catch (e: Exception) {
                    throw RuntimeException("Should not happen. In jumper stage 7", e)
                }

                if (BuildConfig.DEBUG) logger.d("jumper stage 7: successfully parsed osis id: $p_bookIdFromOsis $p_chapter $p_verse")
                return true
            }
        }

        //# STAGE 10: Split based on SPACE, :, PERIOD, and whitespaces between -'s and numbers.
        //# Sample of wrong output: [Kisah, rasul34, 6-7, 8]
        //# Sample of right output: [Kisah, rasul34, 6, -, 7, 8]
        var parts: Array<String> = reference.split(Regex("((\\s|:|\\.)+|(?=[0-9])(?<=-)|(?=-)(?<=[0-9][a-z]?))")).toTypedArray()
        if (BuildConfig.DEBUG) logger.d("jumper stage 10: ${parts.contentToString()}")

        //# STAGE 12: Remove string from empty parts
        run {
            var hasEmpty = 0
            for (b in parts) {
                if (b.isEmpty()) {
                    hasEmpty++
                    break
                }
            }
            if (hasEmpty > 0) {
                val partsWithoutEmpties = arrayOfNulls<String>(parts.size - hasEmpty)
                var c = 0
                for (b in parts) {
                    if (b.isNotEmpty()) {
                        partsWithoutEmpties[c++] = b
                    }
                }
                @Suppress("UNCHECKED_CAST")
                parts = partsWithoutEmpties as Array<String>
            }
        }
        if (BuildConfig.DEBUG) logger.d("jumper stage 12: ${parts.contentToString()}")

        if (parts.isEmpty()) {
            return false
        }

        //# STAGE 20: Expand cases like Joh3 to Joh 3
        //# Sample output: [Kisah, rasul, 34, 6, -, 7, 8]
        run {
            val bel = ArrayList<String>()

            for (b in parts) {
                if (isWord(b)) {
                    var number = ""
                    for (i in b.length - 1 downTo 0) {
                        val c = b[i]
                        if (c in '0'..'9') {
                            // found a digit
                            number = c + number
                        } else {
                            break
                        }
                    }

                    if (number.isNotEmpty()) { // a number found behind a word
                        bel.add(b.substring(0, b.length - number.length))
                        bel.add(number)
                    } else {
                        bel.add(b)
                    }
                } else {
                    bel.add(b)
                }
            }

            parts = bel.toTypedArray()
        }
        if (BuildConfig.DEBUG) logger.d("jumper stage 20: ${parts.contentToString()}")


        //# STAGE 25: Look for part that is "-", then remove from it to the end.
        run {
            var hasDash = false
            var at = -1

            for (i in parts.indices) {
                if ("-" == parts[i] || "--" == parts[i]) {
                    hasDash = true
                    at = i
                    break
                }
            }

            if (hasDash) {
                val bel = arrayOfNulls<String>(at)
                System.arraycopy(parts, 0, bel, 0, at)
                @Suppress("UNCHECKED_CAST")
                parts = bel as Array<String>

                p_hasRange = true

                if (BuildConfig.DEBUG) logger.d("jumper stage 25: ${parts.contentToString()}")
            }
        }

        //# STAGE 30: Morph something like "3" "john" to "3 john"
        run {
            val bel = ArrayList<String>()

            var startWord = 0

            // see from the right which one is not a number. That is the start of book.
            for (i in parts.indices.reversed()) {
                val part = parts[i]

                if (!isNumber(part)) {
                    // this and all earlier than this is the book.
                    startWord = i

                    break
                }

                if (i == 0) { // special case, probably the first part is something like "1j" or "1y" for 1 John.
                    if (isWord(part)) {
                        startWord = i

                        break
                    }

                    if (BuildConfig.DEBUG) {
                        logger.d("jumper stage 30: too much, how come there are more than 2 numbers: returning false")
                    }
                    return false
                }
            }

            var s: String? = null
            for (j in 0..startWord) {
                s = if (s == null) parts[j] else "$s ${parts[j]}"
            }

            bel.add(s!!)
            for (k in startWord + 1 until parts.size) {
                bel.add(parts[k])
            }

            parts = bel.toTypedArray()
        }
        if (BuildConfig.DEBUG) logger.d("jumper stage 30: ${parts.contentToString()}")

        if (parts.size == 1) { // 1 part only
            // It means it can be CHAPTER or BOOK only
            return if (isWord(parts[0])) { // it's a BOOK
                p_book = parts[0]
                true
            } else { // it's a CHAPTER
                p_chapter = numberize(parts[0])
                true
            }
        }

        if (parts.size == 2) { // 2 parts
            // means it could be CHAPTER VERSE (in the same book)
            if (isPureNumber(parts[0]) && isNumber(parts[1])) {
                p_chapter = numberize(parts[0])
                p_verse = numberize(parts[1])
                return true
            }

            // or BOOK CHAPTER
            if (isPureNumber(parts[1])) {
                p_book = parts[0]
                p_chapter = numberize(parts[1])
                return true
            }
            return false
        }

        if (parts.size == 3) { // 3 parts
            // it means it must be BOOK CHAPTER VERSE. Could not be otherwise.
            p_book = parts[0]
            p_chapter = numberize(parts[1])
            p_verse = numberize(parts[2])
            return true
        }

        return false
    }

    private fun parse(alamat: String): Boolean {
        val res = parse0(alamat)

        if (BuildConfig.DEBUG) {
            logger.d("jumper after parse0: p_book=$p_book p_chapter=$p_chapter p_verse=$p_verse")
        }

        return res
    }

    private fun guessBook(refs: List<BookRef>): Int {
        val initial = p_book ?: return -1

        val res = -1

        // 0. clean up p_book
        val pb = initial.replace(Regex("(\\s|-|_)"), "").lowercase(Locale.getDefault())
        p_book = pb
        if (BuildConfig.DEBUG) logger.d("guessBook phase 0: p_book = $pb")

        // 1. try to match wholly (e.g.: "genesis", "john")
        for (ref in refs) {
            if (ref.condensed == pb) {
                if (BuildConfig.DEBUG) logger.d("guessBook phase 1 success: $pb")
                return ref.bookId
            }
        }

        // 2. try to match by prefix. If there is only one match, success
        var pos_forLater = -1
        run {
            var passed = 0
            for (ref in refs) {
                if (ref.condensed.startsWith(pb)) {
                    passed++
                    if (passed == 1) pos_forLater = ref.bookId
                }
            }

            if (passed == 1) {
                if (BuildConfig.DEBUG) logger.d("guessBook phase 2 success: $pos_forLater for $pb")
                return pos_forLater
            } else {
                if (BuildConfig.DEBUG) logger.d("guessBook phase 2: passed=$passed")
            }
        }

        // 3. String matching only when p_book is 2 letters or more
        if (pb.length >= 2) {
            var minScore = 99999999
            var pos = -1

            for (ref in refs) {
                var score = Levenshtein.distance(pb, ref.condensed)
                if (pb[0] != ref.condensed[0]) {
                    score += 150 // approximately 1.5 times insertion cost
                }

                if (BuildConfig.DEBUG) {
                    logger.d("guessBook phase 3: with $ref, score $score")
                }

                if (score < minScore) {
                    minScore = score
                    pos = ref.bookId
                }
            }

            if (pos != -1) {
                if (BuildConfig.DEBUG) logger.d("guessBook phase 3 success: $pos with score $minScore")
                return pos
            }
        }

        // 7. Return the earlier match if there is more than one that passed phase 2.
        if (pos_forLater != -1) {
            if (BuildConfig.DEBUG) logger.d("guessBook phase 7 success: $pos_forLater for $pb")
            return pos_forLater
        }

        return res
    }

    val unparsedBook: String?
        get() = p_book

    /**
     * @param books list of books from which the looked for book is searched
     * @return bookId of one of the books (or -1).
     */
    fun getBookId(books: Array<Book>): Int {
        if (p_bookIdFromOsis != -1) return p_bookIdFromOsis

        var refs = condensedCache[books]
        if (refs == null) {
            val bookNames = Array(books.size) { books[it].shortName }
            val bookIds = IntArray(books.size) { books[it].bookId }

            refs = createBookCandidates(bookNames, bookIds)
            condensedCache[books] = refs
            if (BuildConfig.DEBUG) logger.d("New condensedCache entry: $refs")
        }

        return guessBook(refs)
    }

    /**
     * Give list of (bookName, bookId) from which the looked for book is searched.
     *
     * @return bookId of one of the books (or -1).
     */
    fun getBookId(bookNames: Array<String>, bookIds: IntArray): Int {
        if (p_bookIdFromOsis != -1) return p_bookIdFromOsis

        return guessBook(createBookCandidates(bookNames, bookIds))
    }

    val chapter: Int
        get() = p_chapter

    val verse: Int
        get() = p_verse

    /**
     * The reference string is a verse range, with dash as delimiter
     */
    val hasRange: Boolean
        get() = p_hasRange

    companion object {
        private val TAG: String = Jumper::class.java.simpleName

        private val condensedCache = WeakHashMap<Array<Book>, List<BookRef>>()

        /**
         * Can't be parsed as a pure number. "4-5": true. "Hello": true. "123": false. "12b": false.
         * This is not the opposite of isNumber.
         */
        private fun isWord(s: String): Boolean {
            val c = s[0]
            if (c < '0' || c > '9') return true

            return try {
                s.toInt()
                false
            } catch (e: NumberFormatException) {
                true
            }
        }

        /**
         * @return true if s is a number, or s is a number followed by a single lowercase character 'a'-'z' inclusive.
         * That is for handling verse parts like 12a or 15b.
         */
        private fun isNumber(s: String): Boolean {
            try {
                s.toInt()
                return true
            } catch (e: NumberFormatException) {
                // try special case
                if (s.length > 1 && s[s.length - 1] in 'a'..'z') {
                    return try {
                        s.substring(0, s.length - 1).toInt()
                        true
                    } catch (e2: NumberFormatException) {
                        false
                    }
                }
                return false
            }
        }

        /**
         * @return true if s is a number.
         */
        private fun isPureNumber(s: String): Boolean {
            return try {
                s.toInt()
                true
            } catch (e: NumberFormatException) {
                false
            }
        }

        /**
         * @return integer value of s if s is a number, or s is a number followed by a single lowercase character 'a'-'z' inclusive.
         * That is for handling verse parts like 12a or 15b.
         * Returns 0 when it's unable to parse.
         */
        private fun numberize(s: String): Int {
            try {
                return s.toInt()
            } catch (e: NumberFormatException) {
                // try special case
                if (s.length > 1 && s[s.length - 1] in 'a'..'z') {
                    return try {
                        s.substring(0, s.length - 1).toInt()
                    } catch (e2: NumberFormatException) {
                        0
                    }
                }
                return 0
            }
        }

        @JvmStatic
        fun createBookCandidates(books: Array<Book>): List<BookRef> {
            val bookNames = Array(books.size) { books[it].shortName }
            val bookIds = IntArray(books.size) { books[it].bookId }
            return createBookCandidates(bookNames, bookIds)
        }

        private fun createBookCandidates(bookNames: Array<String>, bookIds: IntArray): List<BookRef> {
            // create cache of condensed book titles where all spaces are stripped and lowercased and "1" becomes "I", "2" becomes "II" etc.
            val res = ArrayList<BookRef>()

            for (i in bookNames.indices) {
                var condensed = bookNames[i].replace(Regex("(\\s|-|_)+"), "").lowercase(Locale.getDefault())

                run {
                    val ref = BookRef()
                    ref.condensed = condensed
                    ref.bookId = bookIds[i]

                    res.add(ref)
                }

                if (condensed.contains("1") || condensed.contains("2") || condensed.contains("3")) {
                    condensed = condensed.replace("1", "i").replace("2", "ii").replace("3", "iii")

                    val ref = BookRef()
                    ref.condensed = condensed
                    ref.bookId = bookIds[i]

                    res.add(ref)
                }
            }

            return res
        }
    }
}
