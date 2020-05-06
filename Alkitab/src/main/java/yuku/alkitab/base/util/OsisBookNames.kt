package yuku.alkitab.base.util

import yuku.alkitab.util.Ari
import java.util.HashMap

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
        "Rev"
    )

    private val bookNameToBookIdMap: Map<String, Int> by lazy {
        HashMap<String, Int>(names.size).apply {
            for (i in names.indices) {
                put(names[i], i)
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
                append(names[i])
            }
            append(')')
            append("\\.([1-9][0-9]{0,2})(?:\\.([1-9][0-9]{0,2}))?")
        })
    }

    /**
     * @param osisBookName OSIS Book Name (only OT and NT currently supported)
     * @return 0 to 65 when OK, -1 when not found
     */
    @JvmStatic
    fun osisBookNameToBookId(osisBookName: String?): Int {
        if (osisBookName == null) return -1
        return bookNameToBookIdMap[osisBookName] ?: -1
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
