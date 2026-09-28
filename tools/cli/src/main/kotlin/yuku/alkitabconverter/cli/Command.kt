package yuku.alkitabconverter.cli

/**
 * A subcommand of the converter CLI. Implementations carry JCommander annotations for their
 * options, including a `--help` flag backing [help], and are registered in [runCli].
 */
interface Command {
    val help: Boolean

    /** @return the process exit code. */
    fun run(): Int
}
