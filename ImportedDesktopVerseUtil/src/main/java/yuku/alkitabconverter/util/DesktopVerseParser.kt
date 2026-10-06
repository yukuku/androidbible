package yuku.alkitabconverter.util

import java.util.Locale
import yuku.alkitab.util.IntArrayList

object DesktopVerseParser {
    private val bookNamesPattern_indonesian = "kejadian|kej|kel|keluaran|im|imamat|bil|bilangan|ul|ulangan|yos|yosua|hak|hakim-hakim|rut|ru|1\\s+samuel|1samuel|1\\s+sam|1sam|1\\s+sa|1sa|i\\s+samuel|i\\s+sam|i\\s+sa|2\\s+samuel|2samuel|2\\s+sam|2sam|2\\s+sa|2sa|ii\\s+samuel|ii\\s+sam|ii\\s+sa|1\\s+raj|1\\s+raja|1raj|1raja|1\\s+raja-raja|1raja-raja|2\\s+raj|2\\s+raja|2raj|2raja|2\\s+raja-raja|2raja-raja|i\\s+raj|i\\s+raja|iraj|iraja|i\\s+raja-raja|iraja-raja|ii\\s+raj|ii\\s+raja|iiraj|iiraja|ii\\s+raja-raja|iiraja-raja|1\\s+tawarikh|1tawarikh|1\\s+taw|1taw|i\\s+tawarikh|i\\s+taw|2\\s+tawarikh|2tawarikh|2\\s+taw|2taw|ii\\s+tawarikh|ii\\s+taw|ezra|ezr|neh|nh|ne|nehemia|est|es|ester|ayub|ayb|ay|mazmur|maz|mzm|amsal|ams|pengkhotbah|pkh|kidung\\s+agung|kidungagung|kid|yesaya|yes|yeremia|yer|ratapan|rat|yehezkiel|yeh|daniel|dan|dn|hosea|hos|ho|yoel|yl|amos|amo|am|obaja|oba|ob|yunus|yun|mikha|mik|mi|nahum|nah|na|habakkuk|habakuk|hab|zefanya|zef|haggai|hagai|hag|zakharia|zak|za|maleakhi|mal|matius|mat|mt|markus|mark|mar|mrk|mr|mk|lukas|luk|lu|lk|yohanes|yoh|kisah\\s+para\\s+rasul|kisah\\s+rasul|kis|roma|rom|rm|ro|1\\s+korintus|1korintus|1\\s+kor|1kor|2\\s+korintus|2korintus|2\\s+kor|2kor|i\\s+korintus|ikorintus|i\\s+kor|ikor|ii\\s+korintus|iikorintus|ii\\s+kor|iikor|galatia|gal|ga|efesus|ef|filipi|flp|fil|kolose|kol|1\\s+tesalonika|1tesalonika|1\\s+tes|1tes|i\\s+tesalonika|i\\s+tes|2\\s+tesalonika|2tesalonika|2\\s+tes|2tes|ii\\s+tesalonika|ii\\s+tes|1timotius|1\\s+timotius|1\\s+tim|1tim|1\\s+ti|1ti|i\\s+tim|i\\s+ti|i\\s+timotius|2timotius|2\\s+timotius|2\\s+tim|2tim|2\\s+ti|2ti|ii\\s+timotius|ii\\s+tim|ii\\s+ti|titus|tit|filemon|flm|ibrani|ibr|yakobus|yak|1\\s+pet|1pet|1\\s+pe|1pe|i\\s+peter|i\\s+pet|i\\s+pe|1\\s+petrus|1petrus|1\\s+ptr|1ptr|2\\s+pet|2pet|2\\s+pe|2pe|ii\\s+peter|ii\\s+pet|ii\\s+pe|2\\s+petrus|2petrus|2\\s+ptr|2ptr|1\\s+yohanes|1yohanes|1yoh|1\\s+yoh|i\\s+yohanes|i\\s+yoh|2\\s+yohanes|2yohanes|ii\\s+yohanes|ii\\s+yoh|2yoh|2\\s+yoh|3\\s+yohanes|3yohanes|3yoh|3\\s+yoh|iii\\s+yohanes|iii\\s+yoh|yudas|yud|wahyu|why|wah"
    private val bookNamesPattern_english = "genesis|gen|ge|gn|exodus|exod|exo|ex|leviticus|lev|lv|le|numbers|num|nmb|nu|deuteronomy|deut|deu|dt|de|joshua|josh|jos|judges|judg|jdg|ruth|rut|rth|ru|1\\s+samuel|1samuel|1\\s+sam|1sam|1\\s+sa|1sa|i\\s+samuel|i\\s+sam|i\\s+sa|2\\s+samuel|2samuel|2\\s+sam|2sam|2\\s+sa|2sa|ii\\s+samuel|ii\\s+sam|ii\\s+sa|1\\s+kings|1kings|1\\s+kin|1kin|1\\s+kgs|1kgs|1\\s+ki|1ki|i\\s+kings|i\\s+kin|i\\s+kgs|i\\s+ki|2\\s+kings|2kings|2\\s+kin|2kin|2\\s+kgs|2kgs|2\\s+ki|2ki|ii\\s+kings|ii\\s+kin|ii\\s+kgs|ii\\s+ki|1\\s+chronicles|1chronicles|1\\s+chron|1chron|1\\s+chr|1chr|1\\s+ch|1ch|i\\s+chronicles|i\\s+chron|i\\s+chr|i\\s+ch|2\\s+chronicles|2chronicles|2\\s+chron|2chron|2\\s+chr|2chr|2\\s+ch|2ch|ii\\s+chronicles|ii\\s+chron|ii\\s+chr|ii\\s+ch|ezra|ezr|nehemiah|neh|nh|ne|nehemia|esther|esth|est|es|ester|job|jb|psalms|psalm|psa|pss|ps|proverbs|proverb|prov|pro|pr|ecclesiastes|eccl|ecc|ec|songs\\s+of\\s+solomon|songsofsolomon|song\\s+of\\s+solomon|songofsolomon|song\\s+of\\s+songs|songofsongs|songs|song|son|sos|so|isaiah|isa|is|jeremiah|jer|je|lamentations|lam|la|ezekiel|ezek|eze|daniel|dan|dn|da|hosea|hos|ho|joel|joe|yl|amos|amo|am|obadiah|oba|ob|jonah|jon|micah|mikha|mic|mi|nahum|nah|na|habakkuk|habakuk|hab|zephaniah|zeph|zep|haggai|hagai|hag|zechariah|zech|zec|za|malachi|mal|matthew|mathew|matt|mat|mt|markus|mark|mar|mrk|mr|mk|luke|luk|lu|lk|john|joh|jhn|jn|acts\\s+of\\s+the\\s+apostles|actsoftheapostles|acts|act|ac|romans|rom|rm|ro|1\\s+corinthians|1corinthians|1\\s+cor|1cor|1\\s+co|1co|i\\s+corinthians|i\\s+cor|i\\s+co|2\\s+corinthians|2corinthians|2\\s+cor|2cor|2\\s+co|2co|ii\\s+corinthians|ii\\s+cor|ii\\s+co|galatians|galatia|gal|ga|ephesians|eph|ep|phillippians|philippians|phill|phil|phi|php|ph|colossians|col|co|1\\s+thessalonians|1thessalonians|1\\s+thess|1thess|1\\s+thes|1thes|1\\s+the|1the|1\\s+th|1th|i\\s+thessalonians|i\\s+thess|i\\s+thes|i\\s+the|i\\s+th|2\\s+thessalonians|2thessalonians|2\\s+thess|2thess|2\\s+thes|2thes|2\\s+the|2the|2\\s+th|2th|ii\\s+thessalonians|ii\\s+thess|ii\\s+thes|ii\\s+the|ii\\s+th|1\\s+timothy|1timothy|1\\s+tim|1tim|1\\s+ti|1ti|i\\s+timothy|i\\s+tim|i\\s+ti|2\\s+timothy|2timothy|2\\s+tim|2tim|2\\s+ti|2ti|ii\\s+timothy|ii\\s+tim|ii\\s+ti|titus|tit|philemon|phile|phm|hebrews|heb|he|james|jam|jas|jms|ja|jm|1\\s+peter|1peter|1\\s+pet|1pet|1\\s+pe|1pe|i\\s+peter|i\\s+pet|i\\s+pe|1\\s+ptr|1ptr|2\\s+peter|2peter|2\\s+pet|2pet|2\\s+pe|2pe|ii\\s+peter|ii\\s+pet|ii\\s+pe|2\\s+ptr|2ptr|1\\s+john|1john|1\\s+joh|1joh|1\\s+jhn|1jhn|1\\s+jo|1jo|1\\s+jn|1jn|i\\s+john|i\\s+joh|i\\s+jhn|i\\s+jo|i\\s+jn|2\\s+john|2john|2\\s+joh|2joh|2\\s+jhn|2jhn|2\\s+jo|2jo|2\\s+jn|2jn|ii\\s+john|ii\\s+joh|ii\\s+jhn|ii\\s+jo|ii\\s+jn|3\\s+john|3john|3\\s+joh|3joh|3\\s+jhn|3jhn|3\\s+jo|3jo|3\\s+jn|3jn|iii\\s+john|iii\\s+joh|iii\\s+jhn|iii\\s+jo|iii\\s+jn|jude|jud|ju|revelations|revelation|rev|re|rv"

    /////////////////////////////////////    1 complete verse address (book chapter verse)
    /////////////////////////////////////     2 book name with optional period and spaces after it
    /////////////////////////////////////      3 book name
    /////////////////////////////////////                                                                               ... 4 numbers (chapter or chapter:verse, with ',' or ';' or 'dan') which is not followed by nofollow
    private val verseReferenceRegex = Regex("(((${bookNamesPattern_indonesian}|${bookNamesPattern_english})(?:\\.?\\s+|\\.))(\\d+(?:(?:-|:|(?:;\\s*\\d+:\\s*)|,|\\.|\\d|dan|\\s)+\\d+)?))", RegexOption.IGNORE_CASE)

    private val numberRangeSplitter = Regex("\\s*(;|,|dan)\\s*" /* NOT case insensitive */)

    private val numberStartEndSplitter = Regex("\\s*--?\\s*")

    private val chapterVerse = Regex("(\\d+)\\s*[:.]\\s*(\\d+)")

    private val numbersOnly = Regex("[0-9]+")

    private val orderedBooks = arrayOf(
        "kejadian|kej|genesis|gen|ge|gn",
        "kel|keluaran|exodus|exod|exo|ex",
        "im|imamat|leviticus|lev|lv|le",
        "bil|bilangan|numbers|num|nmb|nu",
        "ul|ulangan|deuteronomy|deut|deu|dt|de",
        "yos|yosua|joshua|josh|jos",
        "hak|hakim-hakim|judges|judg|jdg",
        "rut|ru|ruth|rut|rth|ru",
        "1 samuel|1samuel|1 sam|1sam|1 sa|1sa|i samuel|i sam|i sa", // same for english: 1 samuel|1samuel|1 sam|1sam|1 sa|1sa|i samuel|i sam|i sa
        "2 samuel|2samuel|2 sam|2sam|2 sa|2sa|ii samuel|ii sam|ii sa", // same for english: 2 samuel|2samuel|2 sam|2sam|2 sa|2sa|ii samuel|ii sam|ii sa
        "1 raj|1 raja|1raj|1raja|1 raja-raja|1raja-raja|i raj|i raja|iraj|iraja|i raja-raja|iraja-raja|1 kings|1kings|1 kin|1kin|1 kgs|1kgs|1 ki|1ki|i kings|i kin|i kgs|i ki",
        "2 raj|2 raja|2raj|2raja|2 raja-raja|2raja-raja|ii raj|ii raja|iiraj|iiraja|ii raja-raja|iiraja-raja|2 kings|2kings|2 kin|2kin|2 kgs|2kgs|2 ki|2ki|ii kings|ii kin|ii kgs|ii ki",
        "1 tawarikh|1tawarikh|1 taw|1taw|i tawarikh|i taw|1 chronicles|1chronicles|1 chron|1chron|1 chr|1chr|1 ch|1ch|i chronicles|i chron|i chr|i ch",
        "2 tawarikh|2tawarikh|2 taw|2taw|ii tawarikh|ii taw|2 chronicles|2chronicles|2 chron|2chron|2 chr|2chr|2 ch|2ch|ii chronicles|ii chron|ii chr|ii ch",
        "ezra|ezr", // same for english: ezra|ezr
        "neh|nh|ne|nehemia|nehemiah|neh|nh|ne|nehemia",
        "est|es|ester|esther|esth|est|es|ester",
        "ayub|ayb|ay|job|jb",
        "mazmur|maz|mzm|psalms|psalm|psa|pss|ps",
        "amsal|ams|proverbs|proverb|prov|pro|pr",
        "pengkhotbah|pkh|ecclesiastes|eccl|ecc|ec",
        "kidung agung|kidungagung|kid|songs of solomon|songsofsolomon|song of solomon|songofsolomon|song of songs|songofsongs|songs|song|son|sos|so",
        "yesaya|yes|isaiah|isa|is",
        "yeremia|yer|jeremiah|jer|je",
        "ratapan|rat|lamentations|lam|la",
        "yehezkiel|yeh|ezekiel|ezek|eze",
        "daniel|dan|dn|daniel|dan|dn|da",
        "hosea|hos|ho", // same for english: hosea|hos|ho
        "yoel|yl|joel|joe|yl",
        "amos|amo|am", // same for english: amos|amo|am
        "obaja|oba|ob|obadiah|oba|ob",
        "yunus|yun|jonah|jon",
        "mikha|mik|mi|micah|mikha|mic|mi",
        "nahum|nah|na", // same for english: nahum|nah|na
        "habakkuk|habakuk|hab", // same for english: habakkuk|habakuk|hab
        "zefanya|zef|zephaniah|zeph|zep",
        "haggai|hagai|hag", // same for english: haggai|hagai|hag
        "zakharia|zak|za|zechariah|zech|zec|za",
        "maleakhi|mal|malachi|mal",
        "matius|mat|mt|matthew|mathew|matt|mat|mt",
        "markus|mark|mar|mrk|mr|mk",
        "lukas|luk|lu|lk|luke|luk|lu|lk",
        "yohanes|yoh|john|joh|jhn|jn",
        "kisah para rasul|kisah rasul|kis|acts of the apostles|actsoftheapostles|acts|act|ac",
        "roma|rom|rm|ro|romans|rom|rm|ro",
        "1 korintus|1korintus|1 kor|1kor|i korintus|ikorintus|i kor|ikor|1 corinthians|1corinthians|1 cor|1cor|1 co|1co|i corinthians|i cor|i co|icor|ico",
        "2 korintus|2korintus|2 kor|2kor|ii korintus|iikorintus|ii kor|iikor|2 corinthians|2corinthians|2 cor|2cor|2 co|2co|ii corinthians|ii cor|ii co|iicor|iico",
        "galatia|gal|ga|galatians|galatia|gal|ga",
        "efesus|ef|ephesians|eph|ep",
        "filipi|flp|fil|phillippians|philippians|phill|phil|phi|php|ph",
        "kolose|kol|colossians|col|co",
        "1 tesalonika|1tesalonika|1 tes|1tes|i tesalonika|i tes|1 thessalonians|1thessalonians|1 thess|1thess|1 thes|1thes|1 the|1the|1 th|1th|i thessalonians|i thess|i thes|i the|i th",
        "2 tesalonika|2tesalonika|2 tes|2tes|ii tesalonika|ii tes|2 thessalonians|2thessalonians|2 thess|2thess|2 thes|2thes|2 the|2the|2 th|2th|ii thessalonians|ii thess|ii thes|ii the|ii th",
        "1timotius|1 timotius|1 tim|1tim|1 ti|1ti|i tim|i ti|i timotius|i tim|i ti|1 timothy|1timothy|1 tim|1tim|1 ti|1ti|i timothy|i tim|i ti|itim|iti",
        "2timotius|2 timotius|2 tim|2tim|2 ti|2ti|ii tim|ii ti|ii timotius|ii tim|ii ti|2 timothy|2timothy|2 tim|2tim|2 ti|2ti|ii timothy|ii tim|ii ti|iitim|iiti",
        "titus|tit", // same for english: titus|tit
        "filemon|flm|philemon|phile|phm",
        "ibrani|ibr|hebrews|heb|he",
        "yakobus|yak|james|jam|jas|jms|ja|jm",
        "1 pet|1pet|1 pe|1pe|i peter|i pet|i pe|1 petrus|1petrus|1 ptr|1ptr|1 peter|1peter|1 pet|1pet|1 pe|1pe|i peter|i pet|i pe|1 ptr|1ptr|ipet|ipe",
        "2 pet|2pet|2 pe|2pe|ii peter|ii pet|ii pe|2 petrus|2petrus|2 ptr|2ptr|2 peter|2peter|2 pet|2pet|2 pe|2pe|ii peter|ii pet|ii pe|2 ptr|2ptr|iipet|iipe",
        "1 yohanes|1yohanes|1yoh|1 yoh|i yohanes|i yoh|1 john|1john|1 joh|1joh|1 jhn|1jhn|1 jo|1jo|1 jn|1jn|i john|i joh|i jhn|i jo|i jn|ijoh|ijhn|ijo|ijn",
        "2 yohanes|2yohanes|ii yohanes|ii yoh|2yoh|2 yoh|2 john|2john|2 joh|2joh|2 jhn|2jhn|2 jo|2jo|2 jn|2jn|ii john|ii joh|ii jhn|ii jo|ii jn|iijoh|iijhn|iijo|iijn",
        "3 yohanes|3yohanes|3yoh|3 yoh|iii yohanes|iii yoh|3 john|3john|3 joh|3joh|3 jhn|3jhn|3 jo|3jo|3 jn|3jn|iii john|iii joh|iii jhn|iii jo|iii jn|iiijoh,iiijhn|iiijo|iiijn",
        "yudas|yud|jude|jud|ju",
        "wahyu|why|wah|revelations|revelation|rev|re|rv",
    )

    private val bookNameToId = HashMap<String, Int>(512).apply {
        for ((index, aliases) in orderedBooks.withIndex()) {
            for (bookName in aliases.split('|')) put(bookName, index)
        }
    }

    /**
     * If succeeded, will return start-end pairs [start, end, start, end, ...]. Single verses will have the same values for both start and end.
     * @return null when failed
     */
    @JvmStatic
    fun verseStringToAri(verse: String): IntArrayList? {
        val m = verseReferenceRegex.find(verse) ?: return null

        val bookName = m.groupValues[3].lowercase(Locale.getDefault())
        val bookId = bookNameToId[bookName] ?: return null
        val book_0 = bookId
        val singleChapterBook = (book_0 == 30 /* obaja */
            || book_0 == 56 /* filemon */
            || book_0 == 62 /* 2yoh */
            || book_0 == 63 /* 3yoh */
            || book_0 == 64 /* yudas */
        )
        var lastChapter = 0
        val book_0_shifted = book_0 shl 16
        val res = IntArrayList()
        val numbers = m.groupValues[4]
        val ranges = numberRangeSplitter.split(numbers).dropLastWhile { it.isEmpty() }
        for (range in ranges) {
            val startend = numberStartEndSplitter.split(range).dropLastWhile { it.isEmpty() }
            if (startend.size == 1) {
                val cv = parseCv(startend[0], singleChapterBook, lastChapter) ?: return null
                if (cv != 0) {
                    res.add(book_0_shifted or cv) // start
                    res.add(book_0_shifted or cv) // end same as start
                    lastChapter = if ((cv and 0xff) != 0) (cv shr 8) and 0xff else 0
                }
            } else if (startend.size == 2) {
                val cvStart = parseCv(startend[0], singleChapterBook, lastChapter) ?: return null
                if (cvStart != 0) {
                    val cvEnd: Int
                    val startend_1_trim = startend[1].trim { it <= ' ' }
                    if (numbersOnly.matches(startend_1_trim)) { // check for cases like "2:3-17" (chapter 2 verse 3 to chapter 2 verse 17) or "14-17" (chapter 14 to chapter 17)
                        val startend_1_number = startend_1_trim.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
                        if ((cvStart and 0xff) == 0) { // cvStart has no verse number, so this is for cases like "14-17" (chapter 14 to chapter 17)
                            cvEnd = startend_1_number shl 8
                        } else { // for cases like "2:3-17" (chapter 2 verse 3 to chapter 2 verse 17)
                            cvEnd = (cvStart and 0xff00) or startend_1_number
                        }
                    } else {
                        cvEnd = parseCv(startend[1], singleChapterBook, lastChapter) ?: return null
                    }
                    if (cvEnd != 0) {
                        if (cvEnd >= cvStart) {
                            res.add(book_0_shifted or cvStart)
                            res.add(book_0_shifted or cvEnd)
                            lastChapter = if ((cvEnd and 0xff) != 0) (cvEnd shr 8) and 0xff else 0
                        }
                    }
                }
            }
        }
        return res
    }

    private fun parseCv(cv: String, singleChapterBook: Boolean, previousChapter: Int): Int? {
        if (numbersOnly.matches(cv)) { // either c:0 or 1:v
            val n = cv.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            return if (singleChapterBook) {
                0x0100 or (n and 0xff)
            } else if (previousChapter != 0) {
                ((previousChapter and 0xff) shl 8) or (n and 0xff)
            } else {
                (n and 0xff) shl 8
            }
        } else {
            val m = chapterVerse.matchEntire(cv)
            if (m != null) {
                val c = m.groupValues[1].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
                val v = m.groupValues[2].toIntOrNull()?.takeIf { it in 0..255 } ?: return null
                return ((c and 0xff) shl 8) or (v and 0xff)
            }
        }
        return 0
    }

    private val completeReference = Regex("^(.+?)(?:\\s+|\\.\\s*)(\\d[\\d\\s:.,;\\-a-zA-Z]*)$")
    private val completeRangeSplitter = Regex("\\s*(?:;|,|dan)\\s*", RegexOption.IGNORE_CASE)

    @JvmStatic
    fun bookIdFromName(name: String): Int {
        val normalized = StringBuilder(name.length)
        var pendingSpace = false
        for (c in name) {
            if (c == '.') continue
            if (Character.isWhitespace(c)) {
                pendingSpace = normalized.isNotEmpty()
            } else {
                if (pendingSpace) normalized.append(' ')
                normalized.append(c)
                pendingSpace = false
            }
        }
        return bookNameToId[normalized.toString().lowercase(Locale.ROOT)] ?: -1
    }

    /** Returns complete start/end ARI pairs, rejecting malformed or overflowing addresses. */
    @JvmStatic
    fun parseReference(reference: String): IntArrayList? {
        val normalized = reference.replace('\u2013', '-').replace('\u2014', '-').trim()
        val match = completeReference.matchEntire(normalized)
        if (match == null) {
            val book = bookIdFromName(normalized)
            if (book < 0) return null
            return IntArrayList(2).apply {
                add(book shl 16)
                add(book shl 16)
            }
        }
        val book = bookIdFromName(match.groupValues[1])
        if (book < 0) return null
        var chapter = 0
        var verseMode = false
        val result = IntArrayList()
        for (range in completeRangeSplitter.split(match.groupValues[2])) {
            val ends = numberStartEndSplitter.split(range)
            if (ends.size !in 1..2) return null
            val start = parseCompleteAddress(ends[0], chapter, verseMode) ?: return null
            chapter = start shr 8
            verseMode = (start and 0xff) != 0
            val end = if (ends.size == 1) start else parseCompleteAddress(ends[1], chapter, verseMode) ?: return null
            if (end < start) return null
            result.add((book shl 16) or start)
            result.add((book shl 16) or end)
            chapter = end shr 8
            verseMode = (end and 0xff) != 0
        }
        return result
    }

    private fun parseCompleteAddress(value: String, chapter: Int, verseMode: Boolean): Int? {
        val text = value.trim()
        if (numbersOnly.matches(text)) {
            val number = text.toIntOrNull()?.takeIf { it in 1..255 } ?: return null
            return if (verseMode) (chapter shl 8) or number else number shl 8
        }
        val match = chapterVerse.matchEntire(text) ?: return null
        val first = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..255 } ?: return null
        val second = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..255 } ?: return null
        return (first shl 8) or second
    }
}
