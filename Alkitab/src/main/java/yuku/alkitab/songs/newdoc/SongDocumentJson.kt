package yuku.alkitab.songs.newdoc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The configured [Json] instance for the canonical portable-song document
 * (`docs/features/portable-songs/design.md`), plus the on-device
 * `dataFormatVersion` marker for this payload and the song-book download
 * wrapper.
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

    @JvmStatic
    fun encode(doc: SongDocument): String = json.encodeToString(SongDocument.serializer(), doc)

    @JvmStatic
    fun decode(text: String): SongDocument = json.decodeFromString(SongDocument.serializer(), text)

    /**
     * Song-book download wrapper: `{ dataFormatVersion, songs }`, gzipped
     * over the wire. Book metadata (name/title/copyright) is *not* carried
     * here — it travels via the download request/redirect, since the caller
     * already has it before it asks for the payload (see
     * `SongBookUtil.downloadSongBook`).
     */
    @Serializable
    data class SongBookWrapper(
        val dataFormatVersion: Int,
        val songs: List<SongDocument> = emptyList(),
    )

    @JvmStatic
    fun encodeSongBook(wrapper: SongBookWrapper): String = json.encodeToString(SongBookWrapper.serializer(), wrapper)

    @JvmStatic
    fun decodeSongBook(text: String): SongBookWrapper = json.decodeFromString(SongBookWrapper.serializer(), text)
}
