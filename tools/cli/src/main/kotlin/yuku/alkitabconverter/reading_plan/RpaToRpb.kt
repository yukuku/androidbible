package yuku.alkitabconverter.reading_plan

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import java.io.File
import yuku.alkitabconverter.cli.Command
import yuku.bintex.BintexWriter
import yuku.bintex.ValueMap

@Parameters(commandNames = ["rpa2rpb"], commandDescription = "Convert an .rpa reading plan to the binary .rpb the app reads")
class RpaToRpb : Command {
    @field:Parameter(description = "<rpa-file> [<rpb-file>]")
    var params: MutableList<String> = ArrayList()

    @field:Parameter(names = ["--help"], help = true, description = "Show this help")
    override var help = false

    @field:Parameter(names = ["--name"], description = "Plan name stored in the file (defaults to the .rpa file name without its extension)")
    var name: String? = null

    override fun run(): Int {
        if (params.size !in 1..2) {
            System.err.println("Usage: rpa2rpb <rpa-file> [<rpb-file>]")
            return 1
        }

        val rpaFile = File(params[0])
        val rpbFile = params.getOrNull(1)?.let(::File) ?: File(rpaFile.absoluteFile.parentFile, rpaFile.nameWithoutExtension + ".rpb")

        System.err.println("input:  $rpaFile")
        System.err.println("output: $rpbFile")

        val rpa = Rpa.parse(rpaFile)
        for (key in listOf("title", "description", "duration")) {
            if (rpa.infos[key] == null) {
                System.err.println("rpa file doesn't contain info '$key' which is required")
                return 1
            }
        }
        val duration = rpa.infos.getValue("duration").toInt()
        if (duration != rpa.plans.size) {
            System.err.println("warning: duration is $duration but the file has ${rpa.plans.size} plan lines")
        }

        writeRpb(rpbFile, name ?: rpaFile.nameWithoutExtension, rpa)
        return 0
    }

    private fun writeRpb(file: File, planName: String, rpa: Rpa) {
        BintexWriter(file.outputStream().buffered()).use { writer ->
            writer.writeRaw(RPB_HEADER + RPB_VERSION)

            val map = ValueMap()
            map["name"] = planName
            map["title"] = rpa.infos["title"]
            map["description"] = rpa.infos["description"]
            map["duration"] = rpa.infos.getValue("duration").toInt()
            writer.writeValueSimpleMap(map)

            for (aris in rpa.plans) {
                writer.writeUint8(aris.size)
                aris.forEach(writer::writeInt)
            }

            writer.writeUint8(0) // footer
        }
    }
}

/**
 * The tab-separated .rpa authoring format: `info <key> <value>` lines, and one
 * `plan <range-count> <start-ari> <end-ari>...` line per day.
 */
class Rpa(val infos: Map<String, String>, val plans: List<IntArray>) {
    companion object {
        fun parse(file: File): Rpa {
            val infos = LinkedHashMap<String, String>()
            val plans = ArrayList<IntArray>()
            file.forEachLine(Charsets.UTF_8) { line ->
                val splits = line.split('\t')
                when (splits[0]) {
                    "info" -> infos[splits[1]] = splits[2]
                    "plan" -> {
                        val count = splits[1].toInt() * 2
                        plans += IntArray(count) { splits[it + 2].toInt() }
                    }
                }
            }
            return Rpa(infos, plans)
        }
    }
}
