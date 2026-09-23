package yuku.alkitabconverter.reading_plan

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import java.io.File
import yuku.alkitab.util.Ari
import yuku.alkitabconverter.cli.Command
import yuku.alkitabconverter.util.KjvUtils
import yuku.bintex.BintexReader

@Parameters(commandNames = ["rpbdump"], commandDescription = "Print the contents of an .rpb reading plan and how often each verse is read")
class RpbDump : Command {
    @field:Parameter(description = "<rpb-file>")
    var params: MutableList<String> = ArrayList()

    @field:Parameter(names = ["--help"], help = true, description = "Show this help")
    override var help = false

    @field:Parameter(names = ["--verbose", "-v"], description = "List every verse under its read count, not only the verses that are never read")
    var verbose = false

    override fun run(): Int {
        if (params.size != 1) {
            System.err.println("Usage: rpbdump <rpb-file>")
            return 1
        }

        BintexReader(File(params[0]).inputStream().buffered()).use { reader ->
            val header = ByteArray(8)
            reader.readRaw(header)
            if (!header.copyOf(RPB_HEADER.size).contentEquals(RPB_HEADER)) {
                System.err.println("Header is not recognized")
                return 1
            }
            println("version: ${header[7]}")

            val map = reader.readValueSimpleMap()
            for ((key, value) in map) {
                println("$key: $value")
            }

            val duration = map.getInt("duration")
            val plans = ArrayList<IntArray>()
            while (plans.size < duration) {
                val count = reader.readUint8()
                if (count == -1) break
                plans += IntArray(count) { reader.readInt() }
            }
            if (reader.readUint8() != 0) {
                println("ERROR: no footer")
            }
            println("days: ${plans.size}")

            printCoverage(plans)
        }
        return 0
    }

    private fun printCoverage(plans: List<IntArray>) {
        val readCounts = IntArray(TOTAL_VERSES)
        for (aris in plans) {
            for (i in 0 until aris.size / 2) {
                val startLid = startLid(aris[i * 2])
                val endLid = endLid(aris[i * 2 + 1])
                if (startLid == 0 || endLid == 0 || endLid < startLid) {
                    println("ERROR: invalid range ${describe(aris[i * 2])} - ${describe(aris[i * 2 + 1])}")
                    continue
                }
                for (lid in startLid..endLid) readCounts[lid - 1]++
            }
        }

        for (times in 0..(readCounts.maxOrNull() ?: 0)) {
            val lids = readCounts.indices.filter { readCounts[it] == times }.map { it + 1 }
            println("------------- read $times times: ${lids.size} verses -------------")
            if (verbose || times == 0) {
                lids.forEach { lid -> println(describe(KjvUtils.lidToAri(lid))) }
            }
        }

        val total = readCounts.count { it > 0 }
        println("verses covered: $total of $TOTAL_VERSES")
    }

    /** Resolves a chapter-only or book-only ari to its first verse. */
    private fun startLid(ari: Int): Int {
        val chapter = Ari.toChapter(ari).coerceAtLeast(1)
        val verse = Ari.toVerse(ari).coerceAtLeast(1)
        return KjvUtils.toLid(Ari.toBook(ari), chapter, verse)
    }

    /** Resolves a chapter-only or book-only ari to its last verse. */
    private fun endLid(ari: Int): Int {
        val book = Ari.toBook(ari)
        val chapter = Ari.toChapter(ari)
        if (chapter == 0) return KjvUtils.endLidForBookId(book)
        val verse = Ari.toVerse(ari).takeIf { it != 0 } ?: KjvUtils.getVerseCount(book, chapter)
        return KjvUtils.toLid(book, chapter, verse)
    }

    private fun describe(ari: Int): String {
        val book = BOOK_NAMES.getOrElse(Ari.toBook(ari)) { "book${Ari.toBook(ari)}" }
        return "ari $ari: $book ${Ari.toChapter(ari)}:${Ari.toVerse(ari)}"
    }

    private companion object {
        const val TOTAL_VERSES = 31102

        val BOOK_NAMES = listOf(
            "Gen", "Exod", "Lev", "Num", "Deut", "Josh", "Judg", "Ruth", "1Sam", "2Sam", "1Kgs", "2Kgs", "1Chr", "2Chr",
            "Ezra", "Neh", "Esth", "Job", "Ps", "Prov", "Eccl", "Song", "Isa", "Jer", "Lam", "Ezek", "Dan", "Hos", "Joel",
            "Amos", "Obad", "Jonah", "Mic", "Nah", "Hab", "Zeph", "Hag", "Zech", "Mal", "Matt", "Mark", "Luke", "John",
            "Acts", "Rom", "1Cor", "2Cor", "Gal", "Eph", "Phil", "Col", "1Thess", "2Thess", "1Tim", "2Tim", "Titus", "Phlm",
            "Heb", "Jas", "1Pet", "2Pet", "1John", "2John", "3John", "Jude", "Rev",
        )
    }
}
