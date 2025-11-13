package yuku.alkitab.base.util

import java.util.Locale
import kotlin.to
import yuku.alkitab.model.Book

object BibleMediaManager {
    private const val BASE_URL = "https://media.sabda.org/alkitab_audio"
    private const val SUBDIR_PL = "pl/mp3/cd"
    private const val SUBDIR_PB = "pb/mp3/cd"
    private const val LAST_OLD_TESTAMENT_BOOK_ID = 38
    val bookAbbrMap: Map<String, String> = mapOf(
        "Kejadian" to "kej",
        "Keluaran" to "kel",
        "Imamat" to "ima",
        "Bilangan" to "bil",
        "Ulangan" to "ula",
        "Yosua" to "yos",
        "Hakim-hakim" to "hak",
        "Rut" to "rut",
        "1 Samuel" to "1sa",
        "2 Samuel" to "2sa",
        "1 Raja-raja" to "1ra",
        "2 Raja-raja" to "2ra",
        "1 Tawarikh" to "1ta",
        "2 Tawarikh" to "2ta",
        "Ezra" to "ezr",
        "Nehemia" to "neh",
        "Ester" to "est",
        "Ayub" to "ayb",
        "Mazmur" to "mzm",
        "Amsal" to "ams",
        "Pengkhotbah" to "pkh",
        "Kidung Agung" to "kid",
        "Yesaya" to "yes",
        "Yeremia" to "yer",
        "Ratapan" to "rat",
        "Yehezkiel" to "yeh",
        "Daniel" to "dan",
        "Hosea" to "hos",
        "Yoel" to "yoe",
        "Amos" to "amo",
        "Obaja" to "oba",
        "Yunus" to "yun",
        "Mikha" to "mik",
        "Nahum" to "nah",
        "Habakuk" to "hab",
        "Zefanya" to "zef",
        "Hagai" to "hag",
        "Zakharia" to "zak",
        "Maleakhi" to "mal",
        "Matius" to "mat",
        "Markus" to "mrk",
        "Lukas" to "luk",
        "Yohanes" to "yoh",
        "Kisah Para Rasul" to "kis",
        "Roma" to "rom",
        "1 Korintus" to "1ko",
        "2 Korintus" to "2ko",
        "Galatia" to "gal",
        "Efesus" to "efe",
        "Filipi" to "fil",
        "Kolose" to "kol",
        "1 Tesalonika" to "1te",
        "2 Tesalonika" to "2te",
        "1 Timotius" to "1ti",
        "2 Timotius" to "2ti",
        "Titus" to "tit",
        "Filemon" to "flm",
        "Ibrani" to "ibr",
        "Yakobus" to "yak",
        "1 Petrus" to "1pe",
        "2 Petrus" to "2pe",
        "1 Yohanes" to "1yo",
        "2 Yohanes" to "2yo",
        "3 Yohanes" to "3yo",
        "Yudas" to "yud",
        "Wahyu" to "wah"
    )

    fun getCanonicalShortName(name: String): String = when (name) {
        "Hakim-hakim" -> "Hakim"
        "1 Raja-raja" -> "1Raja"
        "2 Raja-raja" -> "2Raja"
        "Kidung Agung" -> "Kidung"
        "Kisah Para Rasul" -> "Kisah"
        else -> name
    }

    // == AUDIO VERSION ==

    fun getSpecialAudioVersion(version: String): String{
        return when (version.uppercase()){
            "TB" -> "tbsuara"
            // bisa ditambahkan sesuai kebutuhan
            else -> version.lowercase()
        }
    }

    fun buildAudioUrl(book: Book, chapter: Int, version: String): String {
        val bookAbbr = bookAbbrMap[book.shortName] ?: return ""
        val audioVersion = MediaList.AUDIO.find { it.version == version }?.audio1 ?: return ""

        val isPL = book.bookId <= LAST_OLD_TESTAMENT_BOOK_ID // 0–38 = PL, 39–65 = PB
        val subdir = if (isPL) SUBDIR_PL else SUBDIR_PB
        val bookCode = String.format(Locale.US, "%02d", if (isPL) book.bookId + 1 else book.bookId - 38)
        val shortNameClean = getCanonicalShortName(book.shortName)
        val chapterFormatted = String.format(Locale.US, if (book.chapter_count >= 100) "%03d" else "%02d", chapter)

        return "$BASE_URL/$audioVersion/$subdir/${bookCode}_${shortNameClean.lowercase()}/${bookCode}_${bookAbbr}${chapterFormatted}.mp3"
    }
}