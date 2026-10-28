package yuku.alkitab.songs

import java.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class SongMedia(
    val audioClips: List<SongAudioClip>,
    val youtubeVideos: List<SongYoutubeVideo>,
    val isIncomplete: Boolean,
) {
    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val decoder = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun parse(json: String): SongMedia {
            val response = decoder.decodeFromString(SongMediaResponse.serializer(), json)
            val audio = response.audioClips.mapNotNull { clip ->
                clip.url?.takeIf { it.startsWith("https://") }?.let(::SongAudioClip)
            }
            val videos = response.youtubeVideos.mapNotNull { video ->
                val id = video.videoId ?: return@mapNotNull null
                if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) return@mapNotNull null
                val views = video.views
                val count = views?.viewCount?.takeIf { it >= 0 }
                val precision = views?.precision
                val published = video.publishedAt?.let {
                    runCatching { Instant.parse(it) }.getOrNull()
                }
                SongYoutubeVideo(
                    videoId = id,
                    title = video.title.orEmpty(),
                    channelName = video.channelName.orEmpty(),
                    thumbnailUrl = video.thumbnailUrl?.takeIf { it.startsWith("https://") },
                    viewCount = count?.takeIf { precision == "exact" || precision == "approximate" },
                    isViewCountApproximate = precision == "approximate",
                    publishedAt = published,
                )
            }
            return SongMedia(audio, videos, response.isIncomplete == true)
        }
    }
}

@Serializable
private data class SongMediaResponse(
    @SerialName("audio_clips") val audioClips: List<SongAudioClipResponse>,
    @SerialName("youtube_videos") val youtubeVideos: List<SongYoutubeVideoResponse>,
    @SerialName("is_incomplete") val isIncomplete: Boolean?,
)

@Serializable
private data class SongAudioClipResponse(@SerialName("url") val url: String?)

@Serializable
private data class SongYoutubeVideoResponse(
    @SerialName("videoId") val videoId: String?,
    @SerialName("title") val title: String?,
    @SerialName("channel_name") val channelName: String?,
    @SerialName("thumbnail_url") val thumbnailUrl: String?,
    @SerialName("views") val views: SongVideoViewsResponse?,
    @SerialName("published_at") val publishedAt: String?,
)

@Serializable
private data class SongVideoViewsResponse(
    @SerialName("view_count") val viewCount: Long?,
    @SerialName("precision") val precision: String?,
)

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
