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
     * `meta` is derived, never trusted from input (design §3.1/§8.3): it is
     * always recomputed from `blocks` so a decode-from-legacy and a parse of
     * hand-authored `@doc` JSON converge on the same value.
     */
    @JvmStatic
    fun deriveMeta(blocks: List<Block>): Meta {
        val title = blocks.filterIsInstance<PBlock>().firstOrNull { it.role == "title" }?.content?.plainText()
        val titleOriginal = blocks.filterIsInstance<PBlock>().firstOrNull { it.role == "title_original" }?.content?.plainText()
        return Meta(title = title, title_original = titleOriginal)
    }

    private fun withDerivedMeta(doc: SongDocument): SongDocument = doc.copy(meta = deriveMeta(doc.blocks))

    @JvmStatic
    fun encode(doc: SongDocument): String = json.encodeToString(SongDocument.serializer(), withDerivedMeta(doc))

    @JvmStatic
    fun decode(text: String): SongDocument = withDerivedMeta(json.decodeFromString(SongDocument.serializer(), text))

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
    fun encodeSongBook(wrapper: SongBookWrapper): String {
        val normalized = wrapper.copy(songs = wrapper.songs.map(::withDerivedMeta))
        return json.encodeToString(SongBookWrapper.serializer(), normalized)
    }

    @JvmStatic
    fun decodeSongBook(text: String): SongBookWrapper {
        val raw = json.decodeFromString(SongBookWrapper.serializer(), text)
        return raw.copy(songs = raw.songs.map(::withDerivedMeta))
    }
}
