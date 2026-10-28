package yuku.alkitab.songs

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class SongMedia(
    val audioClips: List<SongAudioClip>,
    val youtubeVideos: List<SongYoutubeVideo>,
    val isIncomplete: Boolean,
) {
    companion object {
        fun parse(json: String): SongMedia {
            val root = Json.parseToJsonElement(json).jsonObject
            val audio = root.getValue("audio_clips").jsonArray.mapNotNull { value ->
                val url = value.jsonObject["url"]?.jsonPrimitive?.contentOrNull
                url?.takeIf { it.startsWith("https://") }?.let(::SongAudioClip)
            }
            val videos = root.getValue("youtube_videos").jsonArray.mapNotNull { value ->
                val item = value.jsonObject
                val id = item["videoId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) return@mapNotNull null
                val views = item["views"]?.takeUnless { it is JsonNull }?.jsonObject
                val count = views?.get("view_count")?.jsonPrimitive?.longOrNull?.takeIf { it >= 0 }
                val precision = views?.get("precision")?.jsonPrimitive?.contentOrNull
                val published = item["published_at"]?.jsonPrimitive?.contentOrNull?.let {
                    runCatching { Instant.parse(it) }.getOrNull()
                }
                SongYoutubeVideo(
                    videoId = id,
                    title = item["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    channelName = item["channel_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    thumbnailUrl = item["thumbnail_url"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.startsWith("https://") },
                    viewCount = count?.takeIf { precision == "exact" || precision == "approximate" },
                    isViewCountApproximate = precision == "approximate",
                    publishedAt = published,
                )
            }
            return SongMedia(audio, videos, root["is_incomplete"]?.jsonPrimitive?.booleanOrNull == true)
        }
    }
}

data class SongAudioClip(val url: String)

data class SongYoutubeVideo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val viewCount: Long?,
    val isViewCountApproximate: Boolean,
    val publishedAt: Instant?,
)
