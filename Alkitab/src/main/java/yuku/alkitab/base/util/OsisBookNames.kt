package yuku.alkitab.base.util

import yuku.alkitab.util.Ari
import java.util.HashMap
import java.util.Locale

object OsisBookNames {
    private val names = arrayOf(
        "Gen",
        "Exod",
        "Lev",
        "Num",
        "Deut",
        "Josh",
        "Judg",
        "Ruth",
        "1Sam",
        "2Sam",
        "1Kgs",
        "2Kgs",
        "1Chr",
        "2Chr",
        "Ezra",
        "Neh",
        "Esth",
        "Job",
        "Ps",
        "Prov",
        "Eccl",
        "Song",
        "Isa",
        "Jer",
        "Lam",
        "Ezek",
        "Dan",
        "Hos",
        "Joel",
        "Amos",
        "Obad",
        "Jonah",
        "Mic",
        "Nah",
        "Hab",
        "Zeph",
        "Hag",
        "Zech",
        "Mal",
        "Matt",
        "Mark",
        "Luke",
        "John",
        "Acts",
        "Rom",
        "1Cor",
        "2Cor",
        "Gal",
        "Eph",
        "Phil",
        "Col",
        "1Thess",
        "2Thess",
        "1Tim",
        "2Tim",
        "Titus",
        "Phlm",
        "Heb",
        "Jas",
        "1Pet",
        "2Pet",
        "1John",
        "2John",
        "3John",
        "Jude",
        "Rev", // bookId=65, book_1=66
        "1Esd",
        "2Esd",
        "Tob",
        "Jdt",
        "1Macc",
        "2Macc",
        "3Macc",
        "4Macc", // bookId=73, book_1=74
        null,
        "Odes",
        "Wis",
        "Sir",
        "PssSol",
        "EpJer",
        "Bar",
        "Sus",
        "PrAzar",
        "Bel",
        "PrMan",
        "AddEsth",
        "AddPs",
        "EpLao",
        "AddDan" // bookId=88, book_1=89
    )

    /**
     * Map of lowercase OSIS book name to bookId
     */
    private val bookNameToBookIdMap: Map<String, Int> by lazy {
        HashMap<String, Int>(names.size).apply {
            for (i in names.indices) {
                val name = names[i]
                if (name != null) {
                    put(name.lowercase(Locale.US), i)
                }
            }
        }
    }

    @JvmStatic
    val bookNameWithChapterAndOptionalVerseRegex by lazy {
        Regex(buildString(names.size * 6) {
            append('(')
            for (i in names.indices) {
                if (i != 0) {
                    append('|')
                }
                val name = names[i]
                if (name != null) {
                    append(name)
                }
            }
            append(')')
            append("\\.([1-9][0-9]{0,2})(?:\\.([1-9][0-9]{0,2}))?")
        }, RegexOption.IGNORE_CASE)
    }

    /**
     * @param osisBookName OSIS Book Name (only OT and NT currently supported)
     * @return non-negative bookId when found, -1 when not found
     */
    @JvmStatic
    fun osisBookNameToBookId(osisBookName: String): Int {
        return bookNameToBookIdMap[osisBookName.lowercase(Locale.US)] ?: -1
    }

    @JvmStatic
    fun osisToAri(osis: String): Int {
        val m = bookNameWithChapterAndOptionalVerseRegex.matchEntire(osis)
        if (m != null) {
            val (osisBookName, chapter_s, verse_s) = m.destructured
            val bookId = osisBookNameToBookId(osisBookName)
            val chapter_1 = chapter_s.toInt()
            val verse_1 = if (verse_s.isEmpty()) 0 else verse_s.toInt()
            return Ari.encode(bookId, chapter_1, verse_1)
        }
        return 0
    }
}
