package yuku.alkitabconverter.cli

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import kotlin.system.exitProcess
import yuku.alkitabconverter.reading_plan.RpaToRpb
import yuku.alkitabconverter.reading_plan.RpbDump
import yuku.alkitabconverter.yet.YetToInternal
import yuku.alkitabconverter.yet.YetToYes2

private class MainOptions {
    @field:Parameter(names = ["--help", "-h"], help = true, description = "Show this help")
    var help = false
}

fun main(args: Array<String>) {
    exitProcess(runCli(args))
}

fun runCli(args: Array<String>): Int {
    val options = MainOptions()
    val commands = listOf<Command>(YetToYes2(), YetToInternal(), RpaToRpb(), RpbDump())
    val jc = JCommander.newBuilder()
        .programName("alkitab-tools")
        .addObject(options)
        .apply { commands.forEach { addCommand(it) } }
        .build()

    try {
        jc.parse(*args)
    } catch (e: ParameterException) {
        System.err.println(e.message)
        jc.usage()
        return 2
    }

    val name = jc.parsedCommand
    if (options.help || name == null) {
        jc.usage()
        return if (options.help) 0 else 2
    }

    val command = jc.commands.getValue(name).objects.single() as Command
    if (command.help) {
        jc.usageFormatter.usage(name)
        return 0
    }
    return command.run()
}
