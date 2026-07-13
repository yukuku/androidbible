package yuku.alkitab.songs.newdoc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The configured [Json] instance for the canonical portable-song document
 * (design doc §3), plus the on-device `dataFormatVersion` marker for this
 * payload (android-implementation-plan.md §3) and the song-book download
 * wrapper (design §3.7).
 */
object SongDocumentJson {
    /**
     * `dataFormatVersion` written to `song_info.dataFormatVersion` for rows
     * holding this JSON payload (as opposed to a legacy Parcelable BLOB).
     */
    const val DATA_FORMAT_VERSION = 5

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /**
     * Computes `meta` from `blocks` (design §3.1/§8.3): the text of the first
     * `title`/`title_original`-role blocks. This is **not** invoked
     * automatically by [encode]/[decode] — `blocks` is the flowing document
     * content, not something re-parsed on every load. It's the job of
     * whichever code *authors* a [SongDocument] to call this once and store
     * the result: [LegacySongConverter] calls it when synthesizing a
     * document from a decoded legacy `Song` (there's no other producer for
     * that artifact); an external authoring tool (e.g. `kidung-data`'s
     * `OutputJson`) is expected to do the same and emit a trustworthy
     * `meta` in the JSON it produces. From then on `meta` travels with the
     * document and [encode]/[decode] pass it through as-is.
     */
    @JvmStatic
    fun deriveMeta(blocks: List<Block>): Meta {
        var title: String? = null
        var titleOriginal: String? = null
        for (block in blocks) {
            if (block is PBlock) {
                if (title == null && block.role == "title") {
                    title = block.content.plainText()
                } else if (titleOriginal == null && block.role == "title_original") {
                    titleOriginal = block.content.plainText()
                }
            }
            if (title != null && titleOriginal != null) break
        }
        return Meta(title = title, title_original = titleOriginal)
    }

    @JvmStatic
    fun encode(doc: SongDocument): String = json.encodeToString(SongDocument.serializer(), doc)

    @JvmStatic
    fun decode(text: String): SongDocument = json.decodeFromString(SongDocument.serializer(), text)

    @Serializable
    data class SongBookMeta(
        val name: String,
        val title: String? = null,
        val copyright: String? = null,
    )

    /**
     * Song-book download wrapper (design §3.7): `{ v, book, songs }`,
     * gzipped over the wire, replacing the gzipped Java-serialized
     * `List<Song>`.
     */
    @Serializable
    data class SongBookWrapper(
        val v: Int = 1,
        val book: SongBookMeta,
        val songs: List<SongDocument> = emptyList(),
    )

    @JvmStatic
    fun encodeSongBook(wrapper: SongBookWrapper): String = json.encodeToString(SongBookWrapper.serializer(), wrapper)

    @JvmStatic
    fun decodeSongBook(text: String): SongBookWrapper = json.decodeFromString(SongBookWrapper.serializer(), text)
}
