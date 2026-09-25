package yuku.alkitab.yes2.section

import java.io.InputStream
import yuku.alkitab.yes2.io.RandomInputStream
import yuku.alkitab.yes2.lexicon.LexiconCompiler
import yuku.alkitab.yes2.lexicon.RootRewriteTable
import yuku.alkitab.yes2.section.base.SectionContent
import yuku.bintex.BintexReader
import yuku.bintex.BintexWriter

/**
 * The word families smart search widens a query to: each root with the forms of it that occur in
 * this version. Stored as the `lexicon` section of a yes file, and as `<prefix>_lexicon_bt.bt` for
 * internal versions, in the same format:
 *
 * ```
 * uint8        data_format_version = 1
 * uint8        start_rule_count
 * autostring   start_rules[start_rule_count * 2]   // from, to, from, to, ...
 * uint8        end_rule_count
 * autostring   end_rules[end_rule_count * 2]
 * varuint      piece_count
 * autostring   pieces[piece_count]
 * int          family_count
 * family[family_count] {
 *     autostring  root
 *     varuint     form_count
 *     form[form_count] {
 *         varuint  token_count
 *         token[token_count]                       // varuint, see below
 *     }
 * }
 * ```
 *
 * A form is the concatenation of its tokens: [TOKEN_ROOT] is the root as is,
 * [TOKEN_START_REWRITTEN] the root with its start rewritten by the start rules,
 * [TOKEN_END_REWRITTEN] the root with its end rewritten by the end rules, [TOKEN_LITERAL] is
 * followed by an autostring spelled out in full, and any value from [FIRST_PIECE_TOKEN] up is
 * `pieces[value - FIRST_PIECE_TOKEN]`. Values 4 and 5 are reserved. With root `reka`,
 * `mereka-rekakan` is `me`, root, `-`, root, `kan`. See [RootRewriteTable] for how rules apply.
 *
 * The writer takes plain forms and finds the rules and pieces itself ([LexiconCompiler]).
 */
class LexiconSection private constructor(
    val startTable: RootRewriteTable,
    val endTable: RootRewriteTable,
    /** Root to its forms, in file order. */
    val families: LinkedHashMap<String, List<String>>,
) : SectionContent(SECTION_NAME) {

    class Reader : SectionContent.Reader<LexiconSection> {
        override fun read(input: RandomInputStream): LexiconSection = readFrom(input)
    }

    companion object {
        const val SECTION_NAME = "lexicon"
        private const val DATA_FORMAT_VERSION = 1

        const val TOKEN_ROOT = 0
        const val TOKEN_START_REWRITTEN = 1
        const val TOKEN_LITERAL = 2
        const val TOKEN_END_REWRITTEN = 3
        const val FIRST_PIECE_TOKEN = 6

        @JvmStatic
        fun readFrom(input: InputStream): LexiconSection {
            val br = BintexReader(input)
            val version = br.readUint8()
            if (version != DATA_FORMAT_VERSION) throw RuntimeException("Lexicon section version not supported: $version")

            fun readTable(side: RootRewriteTable.Side) = RootRewriteTable(side, List(br.readUint8()) { RootRewriteTable.Rule(br.readAutoString(), br.readAutoString()) })
            val start = readTable(RootRewriteTable.Side.START)
            val end = readTable(RootRewriteTable.Side.END)
            val pieces = Array(br.readVarUint()) { br.readAutoString() }

            val familyCount = br.readInt()
            val families = LinkedHashMap<String, List<String>>(familyCount * 2)
            val sb = StringBuilder()
            repeat(familyCount) {
                val root = br.readAutoString()
                val startRewritten by lazy { start.rewrite(root) ?: throw RuntimeException("no start rule applies to root '$root'") }
                val endRewritten by lazy { end.rewrite(root) ?: throw RuntimeException("no end rule applies to root '$root'") }
                families[root] = List(br.readVarUint()) {
                    sb.setLength(0)
                    repeat(br.readVarUint()) {
                        when (val token = br.readVarUint()) {
                            TOKEN_ROOT -> sb.append(root)
                            TOKEN_START_REWRITTEN -> sb.append(startRewritten)
                            TOKEN_LITERAL -> sb.append(br.readAutoString())
                            TOKEN_END_REWRITTEN -> sb.append(endRewritten)
                            in FIRST_PIECE_TOKEN..Int.MAX_VALUE -> sb.append(pieces[token - FIRST_PIECE_TOKEN])
                            else -> throw RuntimeException("reserved lexicon token $token in the family of '$root'")
                        }
                    }
                    sb.toString()
                }
            }
            return LexiconSection(start, end, families)
        }

        /** Writes the section body for [families], root to plain forms, and returns how it was encoded. */
        @JvmStatic
        fun writeTo(bw: BintexWriter, families: Map<String, List<String>>): LexiconCompiler.Compiled {
            val compiled = LexiconCompiler.compile(families)
            val pieceToken = compiled.pieces.withIndex().associate { (i, p) -> p to FIRST_PIECE_TOKEN + i }

            bw.writeUint8(DATA_FORMAT_VERSION)
            for (table in listOf(compiled.startTable, compiled.endTable)) {
                require(table.rules.size <= 255) { "too many ${table.side} rules" }
                bw.writeUint8(table.rules.size)
                for (rule in table.rules) {
                    bw.writeAutoString(rule.from)
                    bw.writeAutoString(rule.to)
                }
            }
            bw.writeVarUint(compiled.pieces.size)
            for (p in compiled.pieces) bw.writeAutoString(p)

            bw.writeInt(compiled.families.size)
            for (family in compiled.families) {
                bw.writeAutoString(family.root)
                bw.writeVarUint(family.forms.size)
                for (parts in family.forms) {
                    bw.writeVarUint(parts.size)
                    for (part in parts) {
                        val text = part.text
                        val piece = text?.let { pieceToken[it] }
                        when {
                            text == null -> bw.writeVarUint(part.token)
                            piece != null -> bw.writeVarUint(piece)
                            else -> {
                                bw.writeVarUint(TOKEN_LITERAL)
                                bw.writeAutoString(text)
                            }
                        }
                    }
                }
            }
            return compiled
        }
    }
}
