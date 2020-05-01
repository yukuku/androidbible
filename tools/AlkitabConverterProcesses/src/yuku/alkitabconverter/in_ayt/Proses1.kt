package yuku.alkitabconverter.in_ayt

import yuku.alkitabconverter.util.Usfm2Usfx
import yuku.alkitabconverter.util.UsfmBookName
import java.io.File
import java.io.PrintWriter

// make it relative so it can be used by wordsend (because wordsend can't accept filenames starting with "/")
private val MID_DIR = File(System.getProperty("java.io.tmpdir")).relativeTo(File(".").absoluteFile)

fun main(args: Array<String>) {
    val INPUT_TEXT_DIR = args[0]

    for (inputfn in File(INPUT_TEXT_DIR).listFiles { _, name -> name.endsWith(".SFM") }) {
        val lines = inputfn.readLines()

        var splitFile: PrintWriter? = null
        for (line in lines) {
            var baris = line

            // remove ALL BOM (0xfeff)
            if (baris.isNotEmpty()) {
                baris = baris.replace("\ufeff", "")
            }

            if (baris.startsWith("\\id ")) {
                val firstSpaceAfterId = baris.indexOf(' ', 4)
                val newId: String

                newId = if (firstSpaceAfterId == -1) {
                    baris.substring(4)
                } else {
                    baris.substring(4, firstSpaceAfterId)
                }
                splitFile?.close()

                val kitab_0 = UsfmBookName.toBookId(newId)

                val outputfile = File(MID_DIR, String.format("%02d-%s-utf8.usfm", kitab_0, newId))
                outputfile.parentFile?.mkdir()
                splitFile = PrintWriter(outputfile, "utf-8")

                println("$inputfn -> $outputfile")
            }

            // patch: Ganti \s1 dengan \s2
            baris = baris.replace("\\s1", "\\s2")

            // patch: remove erronous U+001A character
            baris = baris.replace("\u001a", "")

            splitFile?.println(baris)
        }

        splitFile?.close()
    }

    val usfms = MID_DIR.list { _, name -> name.endsWith("-utf8.usfm") }

    for (usfm in usfms) {
        val usfx = usfm.replace(".usfm", ".usfx.xml")
        Usfm2Usfx.convert(File(MID_DIR, usfm).path, File(MID_DIR, usfx).path)
    }
}
