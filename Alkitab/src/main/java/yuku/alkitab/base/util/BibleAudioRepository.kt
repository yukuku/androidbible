package yuku.alkitab.base.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.model.MAudio
import yuku.alkitab.base.model.MTiming

private const val TAG = "BibleAudioRepository"

/**
 * Provides audio URLs and verse timing data for supported Bible versions.
 *
 * Audio files are served from media.sabda.org; timing data from karaoke.sabda.org.
 * All URL building is pure and can be called on any thread. Timing fetches are
 * suspending and should be called from a coroutine (they switch to Dispatchers.IO).
 */
object BibleAudioRepository {

    private const val AUDIO_BASE = "https://media.sabda.org/alkitab_audio"
    private const val TIMING_BASE = "https://karaoke.sabda.org/api/timming.php"

    /** Bible versions that have audio available on the SABDA media server. */
    val SUPPORTED: List<MAudio> = listOf(
        MAudio(versionId = "TB", audioFolder = "tb_alkitabsuara", supportsDual = true),
        MAudio(versionId = "AYT", audioFolder = "ayt-ai-v2"),
        MAudio(versionId = "AVB", audioFolder = "avb"),
        MAudio(versionId = "KJV", audioFolder = "kjv"),
    )

    /** Returns the [MAudio] for [versionId], or null if this version has no audio. */
    fun findAudio(versionId: String): MAudio? = SUPPORTED.find { it.versionId == versionId }

    /**
     * Builds the MP3 URL for a chapter.
     *
     * @param bookId  0-based book index (0 = Genesis … 65 = Revelation)
     * @param chapter_1  1-based chapter number
     * @param audioFolder  the [MAudio.audioFolder] value for the target version
     * @return the fully-formed URL, or null when [bookId] is out of range
     */
    fun buildUrl(bookId: Int, chapter_1: Int, audioFolder: String): String? {
        val info = BOOK_INFO.getOrNull(bookId) ?: return null
        val bookCode = (bookId + 1).toString().padStart(2, '0')
        val subdir = if (bookId < 39) "pl/mp3/cd" else "pb/mp3/cd"
        val chapter = chapter_1.toString().padStart(3, '0')
        return "$AUDIO_BASE/$audioFolder/$subdir/${bookCode}_${info.folderName}/${bookCode}_${info.abbr}$chapter.mp3"
    }

    /**
     * Fetches verse timing data for [bookId]/[chapter_1] in [versionId].
     * Performs a network request on [Dispatchers.IO].
     * Returns an empty list on network or parse failure (non-fatal).
     */
    suspend fun fetchTiming(bookId: Int, chapter_1: Int, versionId: String): List<MTiming> {
        val info = BOOK_INFO.getOrNull(bookId) ?: return emptyList()
        val url = "$TIMING_BASE?book=${info.timingName}&chapter=$chapter_1&version=$versionId"
        return withContext(Dispatchers.IO) {
            try {
                val response = Connections.okHttp.newCall(
                    okhttp3.Request.Builder().url(url).build()
                ).execute()
                response.use { r ->
                    if (!r.isSuccessful) return@withContext emptyList()
                    val body = r.body?.string() ?: return@withContext emptyList()
                    MTiming.parseList(body)
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to fetch timing for bookId=$bookId chapter=$chapter_1: ${e.message}")
                emptyList()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Static book data – indexed by bookId (0-based, 0=Genesis … 65=Revelation)
    // folderName : lowercase canonical short name used in the audio URL path
    // abbr       : lowercase abbreviation used in the audio filename
    // timingName : full Indonesian name sent as the `book` param to the timing API
    // -----------------------------------------------------------------------

    private data class BookInfo(val folderName: String, val abbr: String, val timingName: String)

    private val BOOK_INFO = arrayOf(
        // OT (bookId 0–38)
        BookInfo("kejadian",     "kej",  "Kejadian"),         // 0  Genesis
        BookInfo("keluaran",     "kel",  "Keluaran"),         // 1  Exodus
        BookInfo("imamat",       "ima",  "Imamat"),           // 2  Leviticus
        BookInfo("bilangan",     "bil",  "Bilangan"),         // 3  Numbers
        BookInfo("ulangan",      "ula",  "Ulangan"),          // 4  Deuteronomy
        BookInfo("yosua",        "yos",  "Yosua"),            // 5  Joshua
        BookInfo("hakim",        "hak",  "Hakim-hakim"),      // 6  Judges
        BookInfo("rut",          "rut",  "Rut"),              // 7  Ruth
        BookInfo("1samuel",      "1sa",  "1 Samuel"),         // 8  1 Samuel
        BookInfo("2samuel",      "2sa",  "2 Samuel"),         // 9  2 Samuel
        BookInfo("1raja",        "1ra",  "1 Raja-raja"),      // 10 1 Kings
        BookInfo("2raja",        "2ra",  "2 Raja-raja"),      // 11 2 Kings
        BookInfo("1tawarikh",    "1ta",  "1 Tawarikh"),       // 12 1 Chronicles
        BookInfo("2tawarikh",    "2ta",  "2 Tawarikh"),       // 13 2 Chronicles
        BookInfo("ezra",         "ezr",  "Ezra"),             // 14 Ezra
        BookInfo("nehemia",      "neh",  "Nehemia"),          // 15 Nehemiah
        BookInfo("ester",        "est",  "Ester"),            // 16 Esther
        BookInfo("ayub",         "ayb",  "Ayub"),             // 17 Job
        BookInfo("mazmur",       "mzm",  "Mazmur"),           // 18 Psalms
        BookInfo("amsal",        "ams",  "Amsal"),            // 19 Proverbs
        BookInfo("pengkhotbah",  "pkh",  "Pengkhotbah"),      // 20 Ecclesiastes
        BookInfo("kidung",       "kid",  "Kidung Agung"),     // 21 Song of Songs
        BookInfo("yesaya",       "yes",  "Yesaya"),           // 22 Isaiah
        BookInfo("yeremia",      "yer",  "Yeremia"),          // 23 Jeremiah
        BookInfo("ratapan",      "rat",  "Ratapan"),          // 24 Lamentations
        BookInfo("yehezkiel",    "yeh",  "Yehezkiel"),        // 25 Ezekiel
        BookInfo("daniel",       "dan",  "Daniel"),           // 26 Daniel
        BookInfo("hosea",        "hos",  "Hosea"),            // 27 Hosea
        BookInfo("yoel",         "yoe",  "Yoel"),             // 28 Joel
        BookInfo("amos",         "amo",  "Amos"),             // 29 Amos
        BookInfo("obaja",        "oba",  "Obaja"),            // 30 Obadiah
        BookInfo("yunus",        "yun",  "Yunus"),            // 31 Jonah
        BookInfo("mikha",        "mik",  "Mikha"),            // 32 Micah
        BookInfo("nahum",        "nah",  "Nahum"),            // 33 Nahum
        BookInfo("habakuk",      "hab",  "Habakuk"),          // 34 Habakkuk
        BookInfo("zefanya",      "zef",  "Zefanya"),          // 35 Zephaniah
        BookInfo("hagai",        "hag",  "Hagai"),            // 36 Haggai
        BookInfo("zakharia",     "zak",  "Zakharia"),         // 37 Zechariah
        BookInfo("maleakhi",     "mal",  "Maleakhi"),         // 38 Malachi
        // NT (bookId 39–65)
        BookInfo("matius",       "mat",  "Matius"),           // 39 Matthew
        BookInfo("markus",       "mrk",  "Markus"),           // 40 Mark
        BookInfo("lukas",        "luk",  "Lukas"),            // 41 Luke
        BookInfo("yohanes",      "yoh",  "Yohanes"),          // 42 John
        BookInfo("kisah",        "kis",  "Kisah Para Rasul"), // 43 Acts
        BookInfo("roma",         "rom",  "Roma"),             // 44 Romans
        BookInfo("1korintus",    "1ko",  "1 Korintus"),       // 45 1 Corinthians
        BookInfo("2korintus",    "2ko",  "2 Korintus"),       // 46 2 Corinthians
        BookInfo("galatia",      "gal",  "Galatia"),          // 47 Galatians
        BookInfo("efesus",       "efe",  "Efesus"),           // 48 Ephesians
        BookInfo("filipi",       "fil",  "Filipi"),           // 49 Philippians
        BookInfo("kolose",       "kol",  "Kolose"),           // 50 Colossians
        BookInfo("1tesalonika",  "1te",  "1 Tesalonika"),     // 51 1 Thessalonians
        BookInfo("2tesalonika",  "2te",  "2 Tesalonika"),     // 52 2 Thessalonians
        BookInfo("1timotius",    "1ti",  "1 Timotius"),       // 53 1 Timothy
        BookInfo("2timotius",    "2ti",  "2 Timotius"),       // 54 2 Timothy
        BookInfo("titus",        "tit",  "Titus"),            // 55 Titus
        BookInfo("filemon",      "flm",  "Filemon"),          // 56 Philemon
        BookInfo("ibrani",       "ibr",  "Ibrani"),           // 57 Hebrews
        BookInfo("yakobus",      "yak",  "Yakobus"),          // 58 James
        BookInfo("1petrus",      "1pe",  "1 Petrus"),         // 59 1 Peter
        BookInfo("2petrus",      "2pe",  "2 Petrus"),         // 60 2 Peter
        BookInfo("1yohanes",     "1yo",  "1 Yohanes"),        // 61 1 John
        BookInfo("2yohanes",     "2yo",  "2 Yohanes"),        // 62 2 John
        BookInfo("3yohanes",     "3yo",  "3 Yohanes"),        // 63 3 John
        BookInfo("yudas",        "yud",  "Yudas"),            // 64 Jude
        BookInfo("wahyu",        "wah",  "Wahyu"),            // 65 Revelation
    )
}
