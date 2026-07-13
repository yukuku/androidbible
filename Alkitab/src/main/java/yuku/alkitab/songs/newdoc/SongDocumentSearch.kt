package yuku.alkitab.songs.newdoc

/**
 * Text extraction for [yuku.alkitab.songs.SongFilter]'s
 * `match(SongDocument, CompiledFilter)` overload (design §6.5 of the
 * android-implementation-plan.md). Deliberately mirrors the *fields* the
 * legacy `SongFilter.match(Song, CompiledFilter)` scans: `code`, `title`,
 * `title_original`, `authors_lyric`, `authors_music`, `tune`, and every
 * lyric verse line. It does **not** scan `caption`, `musical`, or
 * `scripture` blocks, because the legacy matcher never scanned
 * `keySignature`/`timeSignature`/`scriptureReferences`/lyric captions
 * either.
 */
object SongDocumentSearch {
    @JvmStatic
    fun searchableTexts(doc: SongDocument): List<String> {
        val texts = mutableListOf<String>()
        texts.add(doc.code)
        doc.meta.title?.let { texts.add(it) }
        doc.meta.title_original?.let { texts.add(it) }
        for (block in doc.blocks) collect(block, texts)
        return texts
    }

    private fun collect(block: Block, out: MutableList<String>) {
        when (block) {
            is PBlock -> if (block.role == "tune") out.add(block.content.plainText())
            is RowBlock -> for (item in block.items) {
                if (item.role == "authors_lyric" || item.role == "authors_music") out.add(item.content.plainText())
            }
            is LyricBlock -> for (verse in block.verses) {
                for (line in verse.lines) out.add(line.plainText())
            }
            is ScriptureBlock, is YoutubeBlock, is GapBlock, is UnknownBlock -> {}
        }
    }
}
