package yuku.alkitab.yes2.section

import java.io.InputStream
import yuku.alkitab.yes2.io.RandomInputStream
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
 * uint8 data_format_version = 1
 * uint8 prefix_rule_count
 * value<string> prefix_rules[prefix_rule_count * 2]   // from, to, from, to, ...
 * int family_count
 * value<string> families[family_count]                // see LexiconCodec
 * ```
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
        private const val DATA_FORMAT_VERSION = 1

        @JvmStatic
        fun readFrom(input: InputStream): LexiconSection {
            val br = BintexReader(input)
            val version = br.readUint8()
            if (version != DATA_FORMAT_VERSION) throw RuntimeException("Lexicon section version not supported: $version")

            val ruleCount = br.readUint8()
            val rules = List(ruleCount) { LexiconPrefixTable.Rule(br.readValueString(), br.readValueString()) }
            val table = LexiconPrefixTable(rules)

            val familyCount = br.readInt()
            val families = LinkedHashMap<String, List<String>>(familyCount * 2)
            repeat(familyCount) {
                val (root, forms) = LexiconCodec.decodeFamily(br.readValueString(), table)
                families[root] = forms
            }
            return LexiconSection(table, families)
        }

        /**
         * Writes the section body. [encodedFamilies] are lines as [LexiconCodec] encodes them;
         * every one is decoded first, so a line the reader could not decode is rejected here.
         */
        @JvmStatic
        fun writeTo(bw: BintexWriter, prefixTable: LexiconPrefixTable, encodedFamilies: List<String>) {
            require(prefixTable.rules.size <= 255) { "too many prefix rules" }
            for (line in encodedFamilies) LexiconCodec.decodeFamily(line, prefixTable)

            bw.writeUint8(DATA_FORMAT_VERSION)
            bw.writeUint8(prefixTable.rules.size)
            for (rule in prefixTable.rules) {
                bw.writeValueString(rule.from)
                bw.writeValueString(rule.to)
            }
            bw.writeInt(encodedFamilies.size)
            for (line in encodedFamilies) bw.writeValueString(line)
        }
    }
}
