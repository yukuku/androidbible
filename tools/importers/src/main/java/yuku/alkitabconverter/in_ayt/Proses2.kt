package yuku.alkitabconverter.in_ayt

import org.xml.sax.Attributes
import org.xml.sax.ext.DefaultHandler2
import yuku.alkitab.util.Ari
import yuku.alkitab.yes2.model.PericopeData
import yuku.alkitabconverter.util.FootnoteDb
import yuku.alkitabconverter.util.TextDb
import yuku.alkitabconverter.util.XrefDb
import yuku.alkitabconverter.yes_common.Yes2Common
import yuku.alkitabconverter.yet.YetFileOutput
import java.io.File
import java.io.FileInputStream
import java.util.*
import javax.xml.parsers.SAXParserFactory

private val INPUT_TEXT_DIR = File(System.getProperty("java.io.tmpdir")).relativeTo(File(".").absoluteFile)

private const val INFO_LOCALE = "in"
private const val INFO_SHORT_NAME = "AYT"
private const val INFO_LONG_NAME = "Alkitab Yang Terbuka (AYT)"
private const val INFO_DESCRIPTION = "Alkitab Yang Terbuka (AYT)"
private const val XREF_SHIFT_TB = false // whether detected xref references are TB-verse-shifted

private const val LEVEL_p_r = -2
private const val LEVEL_p_ms = -3
private const val LEVEL_p_mr = -4

private val factory = SAXParserFactory.newInstance()

internal var teksDb = TextDb()
internal var misteri = StringBuilder()
internal var xrefDb = XrefDb()
internal var footnoteDb = FootnoteDb()

internal var pericopeData = PericopeData()

fun main(args: Array<String>) {
    println(File(".").absolutePath)
    val INPUT_BOOK_NAMES = args[0]
    val OUTPUT_YET = args[1]
    val REVISION = args[2]

    val files = INPUT_TEXT_DIR.list { _, name -> name.endsWith("-utf8.usfx.xml") }.sorted()

    pericopeData.entries = ArrayList<PericopeData.Entry>()

    for (file in files) {
        println("file $file start;")

        val `in` = FileInputStream(File(INPUT_TEXT_DIR, file))
        val parser = factory.newSAXParser()
        val r = parser.xmlReader
        println("input buffer size (old) = " + r.getProperty("http://apache.org/xml/properties/input-buffer-size"))
        r.setProperty("http://apache.org/xml/properties/input-buffer-size", 1048576)
        println("input buffer size (new) = " + r.getProperty("http://apache.org/xml/properties/input-buffer-size"))
        r.setFeature("http://xml.org/sax/features/namespaces", true)
        parser.parse(`in`, Handler(Integer.parseInt(file.substring(0, 2))))

        println("file " + file + " done; now total rec: " + teksDb.size())
    }

    println("OUTPUT MISTERI:")
    println(misteri)

    println("OUTPUT XREF:")

    @Suppress("ConstantConditionIf")
    xrefDb.processEach(if (XREF_SHIFT_TB) XrefDb.defaultShiftTbProcessor else XrefDb.defaultWithoutShiftTbProcessor)
    xrefDb.dump()

    println("OUTPUT FOOTNOTE:")
    footnoteDb.dump()

    // POST-PROCESS

    teksDb.normalize()
    teksDb.removeEmptyVerses()
    teksDb.dump()

    // //////// PROSES KE YET

    val yetOutputFile = File(OUTPUT_YET)

    yetOutputFile.parentFile?.mkdir()
    val yet = YetFileOutput(yetOutputFile)
    val versionInfo = Yes2Common.VersionInfo()
    versionInfo.locale = INFO_LOCALE
    versionInfo.shortName = INFO_SHORT_NAME
    versionInfo.longName = INFO_LONG_NAME
    versionInfo.description = "$INFO_DESCRIPTION revisi #$REVISION"
    versionInfo.setBookNamesFromFile(INPUT_BOOK_NAMES)
    yet.setVersionInfo(versionInfo)
    yet.setTextDb(teksDb)
    yet.setPericopeData(pericopeData)
    yet.setXrefDb(xrefDb)
    yet.setFootnoteDb(footnoteDb)

    yet.write()
}

private class Handler(kitab_0: Int) : DefaultHandler2() {
    private var kitab_0 = -1
    private var pasal_1 = 0
    private var ayat_1 = 0

    private var tree = arrayOfNulls<String>(80)
    private var depth = 0

    private var tujuanTulis = Stack<Any>()
    private var tujuanTulis_misteri = Any()
    private var tujuanTulis_teks = Any()
    private var tujuanTulis_judulPerikop = Any()
    private var tujuanTulis_xref = Any()
    private var tujuanTulis_footnote = Any()

    private var perikopBuffer: MutableList<PericopeData.Entry> = ArrayList()
    private var afterThisMustStartNewPerikop = true // if true, we have done with a pericope title, so the next text must become a new pericope title instead of appending to existing one

    // states
    private var sLevel = 0
    private var menjorokTeks = -1 // -2 adalah para start; 0 1 2 3 4 adalah q level;
    private var xref_state = -1 // 0 is initial (just encountered xref tag <x>), 1 is source, 2 is target
    private var footnote_state = -1 // 0 is initial (just encountered footnote tag <f>), 1 is fr (reference), 2 is fk (keywords), 3 is ft (text)

    // for preventing split of characters in text elements
    private var charactersBuffer = StringBuilder()

    private val a = StringBuilder()

    init {
        this.kitab_0 = kitab_0
        tujuanTulis.push(tujuanTulis_misteri)
    }

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        flushCharactersBuffer()

        tree[depth++] = localName

        print("(start:) ")
        cetak()

        val alamat = alamat()
        if (alamat.endsWith("/c")) {
            val id = attributes!!.getValue("id")
            println("#c:$id")
            pasal_1 = Integer.parseInt(id.trim { it <= ' ' })
            ayat_1 = 1 // reset ayat tiap ganti pasal
        } else if (alamat.endsWith("/v")) {
            val id = attributes!!.getValue("id")
            println("#v:$id")
            try {
                ayat_1 = Integer.parseInt(id)
            } catch (e: NumberFormatException) {
                println("// number format exception for: $id")
                // get until first non number
                for (pos in 0 until id.length) {
                    if (!Character.isDigit(id[pos])) {
                        val s = id.substring(0, pos)
                        ayat_1 = Integer.parseInt(s)
                        println("// number format exception simplified to: $s")
                        break
                    }
                }
            }
        } else if (alamat.endsWith("/f")) {
            tujuanTulis.push(tujuanTulis_footnote)
            footnote_state = 0
        } else if (alamat.endsWith("/f/fr")) {
            footnote_state = 1
        } else if (alamat.endsWith("/f/fk")) {
            footnote_state = 2
        } else if (alamat.endsWith("/f/ft")) {
            footnote_state = 3
        } else if (alamat.endsWith("/p")) {
            val sfm = attributes!!.getValue("sfm")
            if (sfm != null) {
                when (sfm) {
                    "r" -> {
                        tujuanTulis.push(tujuanTulis_judulPerikop)
                        sLevel = LEVEL_p_r
                    }
                    "mt" -> tujuanTulis.push(tujuanTulis_misteri)
                    "ms" -> {
                        tujuanTulis.push(tujuanTulis_judulPerikop)
                        sLevel = LEVEL_p_ms
                    }
                    "mr" -> {
                        tujuanTulis.push(tujuanTulis_judulPerikop)
                        sLevel = LEVEL_p_mr
                    }
                    "mi" -> {
                        tujuanTulis.push(tujuanTulis_teks)
                        menjorokTeks = 2
                    }
                    "pi" -> { // Indented para
                        tujuanTulis.push(tujuanTulis_teks)
                        menjorokTeks = 1
                    }
                    "pc" -> { // Centered para
                        tujuanTulis.push(tujuanTulis_teks)
                        menjorokTeks = 2
                    }
                    "m" -> {
                        /*
                         * Flush left (margin) paragraph.
                         * • No first line indent.
                         * • Followed immediately by a space and paragraph text, or by a new line and a verse marker.
                         * • Usually used to resume prose at the margin (without indent) after poetry or OT quotation (i.e. continuation of the previous paragraph).
                         */
                        tujuanTulis.push(tujuanTulis_teks)
                        menjorokTeks = 0 // inden 0
                    }
                    "li" -> {
                        /*
                         * \li#(_text...)
                         * List item.
                         * · An out-dented paragraph meant to highlight the items of a list.
                         * · Lists may be used to markup the elements of a recurrent structure, such as the days within the creation account, or the Decalogue (10 commandments).
                         * · The variable # represents the level of indent.
                         */
                        tujuanTulis.push(tujuanTulis_teks)
                        menjorokTeks = 2 // inden 2
                    }
                    "nb" ->
                        /*
                         * Indicates "no-break" with previous paragraph (regardless of previous paragraph type).
                         * Commonly used in cases where the previous paragraph spans the chapter boundary.
                         */
                        tujuanTulis.push(tujuanTulis_teks)

                    // do not change menjorokTeks, treat as if it was no new para marker
                    "rq" -> {
                        /*
                         * Inline quotation reference(s).
                         * A (cross) reference indicating the source text for the preceding quotation (usually an Old Testament quote).
                         */
                        tujuanTulis.push(tujuanTulis_xref)
                        xref_state = 0
                    }
                    "cls" -> {
                        /*
                         * Closure of an epistle/letter.
                         * Similar to "With love," or "Sincerely yours,".
                         */
                        tujuanTulis.push(tujuanTulis_teks)
                        menjorokTeks = 4 // inden 4
                    }
                    else -> throw RuntimeException("p@sfm ga dikenal: $sfm")
                }
            } else {
                tujuanTulis.push(tujuanTulis_teks)
                menjorokTeks = -2
            }
        } else if (alamat.endsWith("/q")) {
            tujuanTulis.push(tujuanTulis_teks)
            val level = Integer.parseInt(attributes!!.getValue("level"))
            if (level in 1..4) {
                menjorokTeks = level
            } else {
                throw RuntimeException("q level = $level")
            }
        } else if (alamat.endsWith("/s")) {
            tujuanTulis.push(tujuanTulis_judulPerikop)
            sLevel = Integer.parseInt(attributes!!.getValue("level"))
        } else if (alamat.endsWith("/x")) {
            tujuanTulis.push(tujuanTulis_xref)
            xref_state = 0
        } else if (alamat.endsWith("/x/milestone")) { // after milestone, we will have xref source
            xref_state = 1
        } else if (alamat.endsWith("/x/xt")) { // after xt, we will have xref target
            xref_state = 2
        } else if (alamat.endsWith("/wj")) {
            tujuanTulis.push(tujuanTulis_teks)
            tulis("@6")
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        flushCharactersBuffer()

        print("(end:) ")
        cetak()

        val alamat = alamat()
        when {
            alamat.endsWith("/p") -> tujuanTulis.pop()
            alamat.endsWith("/f") -> tujuanTulis.pop()
            alamat.endsWith("/s") -> {
                afterThisMustStartNewPerikop = true
                tujuanTulis.pop()
                // reset xref_state to -1 to prevent undetected unset xref_state
                xref_state = -1
            }
            alamat.endsWith("/x") -> tujuanTulis.pop()
            alamat.endsWith("/wj") -> {
                tulis("@5")
                tujuanTulis.pop()
            }
        }

        tree[--depth] = null
    }

    private fun flushCharactersBuffer() {
        if (charactersBuffer.isNotEmpty()) {
            charactersCompleted(charactersBuffer.toString())
            charactersBuffer.setLength(0)
        }
    }

    override fun characters(ch: CharArray?, start: Int, length: Int) {
        charactersBuffer.append(ch, start, length)
    }

    private fun charactersCompleted(text: String) {
        println("#text:$text")
        if (text.trim { it <= ' ' }.isEmpty()) {
            // when processing footnotes, we still continue even though the text is whitespace only.
            if (tujuanTulis.peek() !== tujuanTulis_footnote) {
                return
            }
        }
        tulis(text)
    }

    private fun tulis(chars: String) {
        val tujuan = tujuanTulis.peek()
        if (tujuan === tujuanTulis_misteri) {
            println("\$tulis ke misteri $kitab_0 $pasal_1 $ayat_1:$chars")
            misteri.append(chars).append('\n')
        } else if (tujuan === tujuanTulis_teks) {
            println("\$tulis ke teks[jenis=$menjorokTeks] $kitab_0 $pasal_1 $ayat_1:$chars")
            teksDb.append(kitab_0, pasal_1, ayat_1, chars.replace("\n", " ").replace("\\s+".toRegex(), " "), menjorokTeks)
            menjorokTeks = -1 // reset

            if (perikopBuffer.size > 0) {
                for (pe in perikopBuffer) {
                    pe.block.title = pe.block.title.replace("\n", " ").replace("  ", " ").trim { it <= ' ' }
                    println("(commit to perikopData " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ":) " + pe.block.title)
                    pe.ari = Ari.encode(kitab_0, pasal_1, ayat_1)
                    pericopeData.entries.add(pe)
                }
                perikopBuffer.clear()
            }
        } else if (tujuan === tujuanTulis_judulPerikop) {
            // masukin ke data perikop

            if (sLevel == 0 || sLevel == 1 || sLevel == 2 || sLevel == LEVEL_p_mr || sLevel == LEVEL_p_ms) {
                if (afterThisMustStartNewPerikop || perikopBuffer.size == 0) {
                    val entry = PericopeData.Entry()
                    entry.ari = 0 // done later when writing teks so we know which verse this pericope starts from
                    entry.block = PericopeData.Block()
                    entry.block.title = chars
                    perikopBuffer.add(entry)
                    afterThisMustStartNewPerikop = false
                    println("\$tulis ke perikopBuffer (new entry) (size now: " + perikopBuffer.size + "): " + chars)
                } else if (sLevel > 2) {
                    throw RuntimeException("sLevel not handled: $sLevel")
                } else {
                    perikopBuffer[perikopBuffer.size - 1].block.title += chars
                    println("\$tulis ke perikopBuffer (append to existing) (size now: " + perikopBuffer.size + "): " + chars)
                }
            } else if (sLevel == LEVEL_p_r) { // paralel
                if (perikopBuffer.size == 0) {
                    throw RuntimeException("paralel found but no perikop on buffer: $chars")
                }

                val entry = perikopBuffer[perikopBuffer.size - 1]
                entry.block.parallels = parseParalel(chars)
            } else if (sLevel == 2) {
                println("\$tulis ke tempat sampah (perikop level 2): $chars")
            } else {
                throw RuntimeException("sLevel = $sLevel not understood: $chars")
            }
        } else if (tujuan === tujuanTulis_xref) {
            println("\$tulis ke xref (state=$xref_state) $kitab_0 $pasal_1 $ayat_1:$chars")
            val ari = Ari.encode(kitab_0, pasal_1, ayat_1)
            when (xref_state) {
                0 -> {
                    // compatibility when \x and \x* are written without any \xo or \xt markers.
                    // Check chars, if it contains more than just spaces, -, +, or a character, it means it looks like a complete xref entry.
                    val xrefIndex: Int = if (chars.replaceFirst("[-+a-zA-Z]".toRegex(), "").replace("\\s".toRegex(), "").isNotEmpty()) {
                        xrefDb.addComplete(ari, chars)
                    } else {
                        xrefDb.addBegin(ari)
                    }
                    teksDb.append(ari, "@<x" + (xrefIndex + 1) + "@>@/", -1)
                }
                1 -> xrefDb.appendText(ari, chars)
                2 -> xrefDb.appendText(ari, chars)
                else -> throw RuntimeException("xref_state not supported")
            }
        } else if (tujuan === tujuanTulis_footnote) {
            println("\$tulis ke footnote (state=$footnote_state) $kitab_0 $pasal_1 $ayat_1:$chars")
            val ari = Ari.encode(kitab_0, pasal_1, ayat_1)
            if (footnote_state == 0) {
                val content = if (chars.matches("[a-zA-Z+-]\\s.*".toRegex())) {
                    // remove that first 2 characters
                    chars.substring(2)
                } else {
                    chars
                }

                // remove caller at the beginning
                val footnoteIndex = footnoteDb.addBegin(ari)

                if (content.trim { it <= ' ' }.isNotEmpty()) {
                    footnoteDb.appendText(ari, content.replace("\n", " "))
                }

                teksDb.append(ari, "@<f" + (footnoteIndex + 1) + "@>@/", -1)
            } else if (footnote_state == 2) {
                footnoteDb.appendText(ari, "@9" + chars.replace("\n", " ") + "@7")
            } else if (footnote_state == 1 || footnote_state == 3) {
                footnoteDb.appendText(ari, chars.replace("\n", " "))
            } else {
                throw RuntimeException("footnote_state not supported")
            }
        }
    }

    private fun cetak() {
        for (i in 0 until depth) {
            print('/')
            print(tree[i])
        }
        println()
    }

    private fun alamat(): String {
        a.setLength(0)
        for (i in 0 until depth) {
            a.append('/').append(tree[i])
        }
        return a.toString()
    }
}

/**
 * (Mat. 23:1-36; Mrk. 12:38-40; Luk. 20:45-47) -> [Mat. 23:1-36, Mrk. 12:38-40, Luk. 20:45-47]
 * (Mat. 10:26-33, 19-20) -> [Mat. 10:26-33, Mat. 10:19-20]
 * (Mat. 6:25-34, 19-21) -> [Mat. 6:25-34, Mat. 6:19-21]
 * (Mat. 10:34-36) -> [Mat. 10:34-36]
 * (Mat. 26:57-58, 69-75; Mrk. 14:53-54, 66-72; Yoh. 18:12-18, 25-27) -> [Mat. 26:57-58, Mat. 26:69-75, Mrk. 14:53-54, Mrk. 14:66-72, Yoh. 18:12-18, Yoh. 18:25-27]
 * (2Taw. 13:1--14:1) -> [2Taw. 13:1--14:1]
 * (2Taw. 14:1-5, 15:16--16:13) -> [2Taw. 14:1-5, 2Taw. 15:16--16:13]
 * (2Taw. 2:13-14, 3:15--5:1) -> [2Taw. 2:13-14, 2Taw. 3:15--5:1]
 * (2Taw. 34:3-7, 35:1-27) -> [2Taw. 34:3-7, 2Taw. 35:1-27]
 */
internal fun parseParalel(judul0: String): List<String> {
    var judul = judul0
    val res = ArrayList<String>()

    judul = judul.trim { it <= ' ' }
    if (judul.startsWith("(")) judul = judul.substring(1)
    if (judul.endsWith(")")) judul = judul.substring(0, judul.length - 1)

    var kitab: String? = null
    var pasal: String? = null
    var ayat: String?

    val alamats = judul.split("[;,]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    for (alamat0 in alamats) {
        val alamat = alamat0.trim { it <= ' ' }

        val bagians = alamat.split(" +".toRegex(), 2).toTypedArray()
        val pa: String
        if (bagians.size == 1) { // no kitab;
            if (kitab == null) throw RuntimeException("no existing kitab")
            pa = bagians[0]
        } else {
            kitab = bagians[0]
            pa = bagians[1]
        }

        val parts = pa.split(":".toRegex(), 2).toTypedArray()
        if (parts.size == 1) { // no pasal
            if (pasal == null) throw RuntimeException("no existing pasal")
            ayat = parts[0]
        } else {
            pasal = parts[0]
            ayat = parts[1]
        }

        res.add("$kitab $pasal:$ayat")
    }

    return res
}
