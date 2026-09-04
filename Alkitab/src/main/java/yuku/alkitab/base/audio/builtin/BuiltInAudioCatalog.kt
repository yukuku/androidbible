package yuku.alkitab.base.audio.builtin

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import yuku.alkitab.base.audio.model.AudioSet

/**
 * Audio recordings whose metadata is shipped with the app rather than fetched
 * from the Alkitab backend.
 *
 * The manifest is deliberately coordinate based. AudioTreasure has historical
 * filename exceptions, so deriving a URL from a book name would eventually
 * produce a plausible-looking 404.
 */
class BuiltInAudioCatalog(context: Context) {

    private val tracksByCoordinate: Map<Int, Track> = context.assets.open(MANIFEST_ASSET).use { input ->
        val manifest = Json.decodeFromString<Manifest>(input.bufferedReader().readText())
        require(manifest.schemaVersion == MANIFEST_SCHEMA)
        require(manifest.chapterCount == manifest.tracks.size)

        manifest.tracks.associateBy { track -> coordinate(track.bookId, track.chapter) }.also { indexed ->
            require(indexed.size == manifest.chapterCount) { "Duplicate audio chapter coordinates" }
        }
    }

    fun audioSetForPreset(presetName: String): AudioSet? {
        if (presetName != WEB_PRESET) return null

        return AudioSet(
            audioId = AUDIO_ID,
            title = TITLE,
            hasTiming = false,
            books_1 = CANON_BOOKS,
            mp3UrlTemplate = LOCATOR,
            timingUrlTemplate = null,
        )
    }

    fun chapterUrl(audioId: String, bookId: Int, chapter1: Int): String? {
        if (audioId != AUDIO_ID || bookId !in 0..65 || chapter1 < 1) return null
        return tracksByCoordinate[coordinate(bookId, chapter1)]?.url
    }

    @Serializable
    private data class Manifest(
        val schemaVersion: Int,
        val source: String,
        val sourceUrl: String,
        val license: String,
        val chapterCount: Int,
        val tracks: List<Track>,
    )

    @Serializable
    private data class Track(
        val bookId: Int,
        val chapter: Int,
        val url: String,
    )

    companion object {
        const val AUDIO_ID = "audiotreasure-web-david-williams"
        const val LOCATOR = "asset://audio/audiotreasure-web"

        private const val WEB_PRESET = "en-web"
        private const val TITLE = "WEB — David Williams (public domain)"
        private const val MANIFEST_ASSET = "audio/audiotreasure_web_manifest.json"
        private const val MANIFEST_SCHEMA = 1
        private val CANON_BOOKS = (1..66).toSet()

        private fun coordinate(bookId: Int, chapter1: Int): Int = bookId * 1_000 + chapter1
    }
}
