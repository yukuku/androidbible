package yuku.alkitab.songs

import com.google.gson.JsonParser
import java.time.Instant

data class SongMedia(
    val audioClips: List<SongAudioClip>,
    val youtubeVideos: List<SongYoutubeVideo>,
    val isIncomplete: Boolean,
) {
    companion object {
        fun parse(json: String): SongMedia {
            val root = JsonParser.parseString(json).asJsonObject
            val audio = root.getAsJsonArray("audio_clips").mapNotNull { value ->
                val url = value.asJsonObject.get("url")?.asString
                url?.takeIf { it.startsWith("https://") }?.let(::SongAudioClip)
            }
            val videos = root.getAsJsonArray("youtube_videos").mapNotNull { value ->
                val item = value.asJsonObject
                val id = item.get("videoId")?.asString ?: return@mapNotNull null
                if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) return@mapNotNull null
                val views = item.get("views")?.takeUnless { it.isJsonNull }?.asJsonObject
                val count = views?.get("view_count")?.takeUnless { it.isJsonNull }?.asLong?.takeIf { it >= 0 }
                val precision = views?.get("precision")?.asString
                val published = item.get("published_at")?.asString?.let {
                    runCatching { Instant.parse(it) }.getOrNull()
                }
                SongYoutubeVideo(
                    videoId = id,
                    title = item.get("title")?.asString.orEmpty(),
                    channelName = item.get("channel_name")?.asString.orEmpty(),
                    thumbnailUrl = item.get("thumbnail_url")?.takeUnless { it.isJsonNull }?.asString
                        ?.takeIf { it.startsWith("https://") },
                    viewCount = count?.takeIf { precision == "exact" || precision == "approximate" },
                    isViewCountApproximate = precision == "approximate",
                    publishedAt = published,
                )
            }
            return SongMedia(audio, videos, root.get("is_incomplete")?.asBoolean == true)
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
