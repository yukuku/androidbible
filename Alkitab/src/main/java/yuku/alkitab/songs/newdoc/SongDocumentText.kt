package yuku.alkitab.songs.newdoc

import java.util.Locale

/**
 * Walks a [SongDocument] and produces plain text for copy/share.
 * Localizable strings (the book name, the "Versi N" caption fallback, and
 * the refrain marker) are supplied by the caller since this class has no
 * Android `Context`.
 *
 * Note: a `text`-kind verse renders with no number prefix at all (it's
 * spoken/instruction text, not a numbered stanza) — don't "fix" this to
 * print a verse number for it.
 */
object SongDocumentText {
    @JvmStatic
    fun render(
        doc: SongDocument,
        bookNameDisplay: CharSequence?,
        scriptureReferencesText: String?,
        versionCaption: (Int) -> String,
        refrainMarker: String,
    ): String = buildString {
        val sb = this

        if (bookNameDisplay != null) sb.append(bookNameDisplay).append(' ')
        sb.append(doc.code).append(". ")
        doc.meta.title?.let { sb.append(it) }
        sb.append('\n')
        doc.meta.title_original?.let { sb.append('(').append(it).append(')').append('\n') }
        sb.append('\n')

        val rowItems = doc.blocks.filterIsInstance<RowBlock>().firstOrNull()?.items.orEmpty()
        rowItems.firstOrNull { it.role == "authors_lyric" }?.let { sb.append(it.content.plainText()).append('\n') }
        rowItems.firstOrNull { it.role == "authors_music" }?.let { sb.append(it.content.plainText()).append('\n') }
        doc.blocks.filterIsInstance<PBlock>().firstOrNull { it.role == "tune" }?.let {
            sb.append(it.content.plainText().uppercase(Locale.ROOT)).append('\n')
        }
        sb.append('\n')

        if (!scriptureReferencesText.isNullOrEmpty()) sb.append(scriptureReferencesText).append('\n')

        doc.blocks.filterIsInstance<PBlock>().firstOrNull { it.role == "musical" }?.let {
            sb.append(it.content.plainText()).append('\n')
        }
        sb.append('\n')

        val lyricBlocks = doc.blocks.filterIsInstance<LyricBlock>()
        for ((i, lyricBlock) in lyricBlocks.withIndex()) {
            if (lyricBlocks.size > 1 || lyricBlock.caption != null) {
                if (lyricBlock.caption != null) {
                    sb.append(lyricBlock.caption.plainText()).append('\n')
                } else {
                    sb.append(versionCaption(i + 1)).append('\n')
                }
            }

            var verseNormalNo = 0
            for (verse in lyricBlock.verses) {
                if (verse.kind == VerseKind.NORMAL) verseNormalNo++

                var skipPad = false
                when (verse.kind) {
                    VerseKind.REFRAIN -> sb.append(refrainMarker).append('\n')
                    VerseKind.NORMAL -> {
                        sb.append(String.format(Locale.US, "%2d: ", verseNormalNo))
                        skipPad = true
                    }
                    VerseKind.TEXT -> {} // no number/marker prefix
                }

                for (line in verse.lines) {
                    if (!skipPad) sb.append("    ") else skipPad = false
                    sb.append(line.plainText()).append('\n')
                }
                sb.append('\n')
            }
            sb.append('\n')
        }
    }
}
