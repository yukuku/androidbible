package yuku.alkitab.yes2.section

import java.io.InputStream
import yuku.alkitab.yes2.io.RandomInputStream
import yuku.alkitab.yes2.lexicon.EncodedFamily
import yuku.alkitab.yes2.lexicon.LexiconCodec
import yuku.alkitab.yes2.lexicon.LexiconPrefixTable
import yuku.alkitab.yes2.section.base.SectionContent
import yuku.bintex.BintexReader
import yuku.bintex.BintexWriter

/**
 * The word families smart search widens a query to: each root with the forms of it that occur in
 * this version. Stored as the `lexicon` section of a yes file, and as `<prefix>_lexicon_bt.bt` for
 * internal versions, in the same format:
 *
 * ```
 * uint8        data_format_version = 2
 * uint8        rewrite_count
 * autostring   rewrites[rewrite_count * 2]      // from, to, from, to, ...
 * varuint      piece_count
 * autostring   pieces[piece_count]
 * int          family_count
 * family[family_count] {
 *     autostring  root
 *     varuint     form_count
 *     form[form_count] {
 *         varuint  token_count
 *         token[token_count]                    // varuint, see below
 *     }
 * }
 * ```
 *
 * A form is the concatenation of its tokens: [TOKEN_ROOT] is the root as is, [TOKEN_REWRITTEN_ROOT]
 * the root rewritten by the rewrite table, [TOKEN_LITERAL] is followed by an autostring spelled out
 * in full, and any value from [FIRST_PIECE_TOKEN] up is `pieces[value - FIRST_PIECE_TOKEN]`. Values
 * between are reserved. With root `reka`, `mereka-rekakan` is `me`, root, `-`, root, `kan`.
 */
class LexiconSection private constructor(
    val prefixTable: LexiconPrefixTable,
    /** Root to its forms, in file order. */
    val families: LinkedHashMap<String, List<String>>,
) : SectionContent(SECTION_NAME) {

    class Reader : SectionContent.Reader<LexiconSection> {
        override fun read(input: RandomInputStream): LexiconSection = readFrom(input)
    }

    companion object {
        const val SECTION_NAME = "lexicon"
        private const val DATA_FORMAT_VERSION = 2

        const val TOKEN_ROOT = 0
        const val TOKEN_REWRITTEN_ROOT = 1
        const val TOKEN_LITERAL = 2
        const val FIRST_PIECE_TOKEN = 6

        /** A text piece used this many times or more goes in the piece table; rarer ones are spelled out. */
        private const val MIN_PIECE_USES = 2

        @JvmStatic
        fun readFrom(input: InputStream): LexiconSection {
            val br = BintexReader(input)
            val version = br.readUint8()
            if (version != DATA_FORMAT_VERSION) throw RuntimeException("Lexicon section version not supported: $version")

            val rules = List(br.readUint8()) { LexiconPrefixTable.Rule(br.readAutoString(), br.readAutoString()) }
            val table = LexiconPrefixTable(rules)
            val pieces = Array(br.readVarUint()) { br.readAutoString() }

            val familyCount = br.readInt()
            val families = LinkedHashMap<String, List<String>>(familyCount * 2)
            val sb = StringBuilder()
            repeat(familyCount) {
                val root = br.readAutoString()
                val rewritten by lazy { table.rewrite(root) ?: throw RuntimeException("no rewrite rule applies to root '$root'") }
                families[root] = List(br.readVarUint()) {
                    sb.setLength(0)
                    repeat(br.readVarUint()) {
                        when (val token = br.readVarUint()) {
                            TOKEN_ROOT -> sb.append(root)
                            TOKEN_REWRITTEN_ROOT -> sb.append(rewritten)
                            TOKEN_LITERAL -> sb.append(br.readAutoString())
                            in FIRST_PIECE_TOKEN..Int.MAX_VALUE -> sb.append(pieces[token - FIRST_PIECE_TOKEN])
                            else -> throw RuntimeException("reserved lexicon token $token in the family of '$root'")
                        }
                    }
                    sb.toString()
                }
            }
            return LexiconSection(table, families)
        }

        /**
         * Writes the section body from families in the `.yet` notation of [LexiconCodec]. Text
         * pieces used at least twice across the lexicon go in the piece table, most used first so
         * they get the one-byte tokens; the rest are written as literals.
         */
        @JvmStatic
        fun writeTo(bw: BintexWriter, prefixTable: LexiconPrefixTable, families: List<EncodedFamily>) {
            require(prefixTable.rules.size <= 255) { "too many rewrite rules" }

            val tokenized = families.map { f ->
                for (form in f.forms) LexiconCodec.decodeForm(f.root, form, prefixTable) // rejects what the reader could not decode
                f.root to f.forms.map { LexiconCodec.tokenize(it) }
            }

            val uses = HashMap<String, Int>()
            for ((_, forms) in tokenized) for (tokens in forms) for (t in tokens) {
                if (!isRootMarker(t)) uses[t] = (uses[t] ?: 0) + 1
            }
            val pieces = uses.filterValues { it >= MIN_PIECE_USES }.keys
                .sortedWith(compareByDescending<String> { uses.getValue(it) }.thenBy { it })
            val pieceToken = pieces.withIndex().associate { (i, p) -> p to FIRST_PIECE_TOKEN + i }

            bw.writeUint8(DATA_FORMAT_VERSION)
            bw.writeUint8(prefixTable.rules.size)
            for (rule in prefixTable.rules) {
                bw.writeAutoString(rule.from)
                bw.writeAutoString(rule.to)
            }
            bw.writeVarUint(pieces.size)
            for (p in pieces) bw.writeAutoString(p)

            bw.writeInt(tokenized.size)
            for ((root, forms) in tokenized) {
                bw.writeAutoString(root)
                bw.writeVarUint(forms.size)
                for (tokens in forms) {
                    bw.writeVarUint(tokens.size)
                    for (t in tokens) {
                        when {
                            t == LexiconCodec.ROOT.toString() -> bw.writeVarUint(TOKEN_ROOT)
                            t == LexiconCodec.REWRITTEN_ROOT.toString() -> bw.writeVarUint(TOKEN_REWRITTEN_ROOT)
                            else -> {
                                val piece = pieceToken[t]
                                if (piece != null) {
                                    bw.writeVarUint(piece)
                                } else {
                                    bw.writeVarUint(TOKEN_LITERAL)
                                    bw.writeAutoString(t)
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun isRootMarker(token: String) =
            token.length == 1 && (token[0] == LexiconCodec.ROOT || token[0] == LexiconCodec.REWRITTEN_ROOT)
    }
}
