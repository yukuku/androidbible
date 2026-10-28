package yuku.alkitab.songs

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongMediaTest {
    @Test
    fun `combined response preserves audio and complete video metadata`() {
        val media = SongMedia.parse(response("{\"view_count\":12345678901,\"precision\":\"exact\"}"))
        assertEquals("https://example.com/song.mid", media.audioClips.single().url)
        val video = media.youtubeVideos.single()
        assertEquals("abcdefghijk", video.videoId)
        assertEquals("A song", video.title)
        assertEquals("Choir", video.channelName)
        assertEquals("https://example.com/thumb.jpg", video.thumbnailUrl)
        assertEquals(12345678901L, video.viewCount)
        assertFalse(video.isViewCountApproximate)
        assertEquals(Instant.parse("2020-01-02T03:04:05Z"), video.publishedAt)
        assertFalse(media.isIncomplete)
    }

    @Test
    fun `approximate view counts keep the server precision`() {
        val video = SongMedia.parse(response("{\"view_count\":1200,\"precision\":\"approximate\"}")).youtubeVideos.single()
        assertEquals(1200L, video.viewCount)
        assertTrue(video.isViewCountApproximate)
    }

    @Test
    fun `missing or unsupported view data is not presented as exact zero`() {
        for (views in listOf("null", "{\"view_count\":null,\"precision\":\"exact\"}", "{\"view_count\":100,\"precision\":\"unknown\"}", "{\"view_count\":-1,\"precision\":\"exact\"}")) {
            assertNull(SongMedia.parse(response(views)).youtubeVideos.single().viewCount)
        }
    }

    @Test
    fun `empty and partial media responses remain distinguishable`() {
        val empty = SongMedia.parse("""{"audio_clips":[],"youtube_videos":[]}""")
        assertTrue(empty.audioClips.isEmpty())
        assertTrue(empty.youtubeVideos.isEmpty())
        assertFalse(empty.isIncomplete)
        val partial = SongMedia.parse("""{"audio_clips":[],"youtube_videos":[],"is_incomplete":true}""")
        assertTrue(partial.isIncomplete)
    }

    @Test
    fun `invalid video ids and insecure media urls are not exposed`() {
        val json = response("null").replace("abcdefghijk", "bad&id=xxx").replace("https://", "http://")
        val media = SongMedia.parse(json)
        assertTrue(media.audioClips.isEmpty())
        assertTrue(media.youtubeVideos.isEmpty())
    }

    @Test
    fun `invalid optional publication date does not hide a video`() {
        val json = response("null").replace("2020-01-02T03:04:05Z", "invalid")
        assertNull(SongMedia.parse(json).youtubeVideos.single().publishedAt)
    }

    @Test
    fun `nullable thumbnails and view counts keep the video available`() {
        val json = response("null").replace("\"thumbnail_url\":\"https://example.com/thumb.jpg\"", "\"thumbnail_url\":null")
        val video = SongMedia.parse(json).youtubeVideos.single()
        assertNull(video.thumbnailUrl)
        assertNull(video.viewCount)
        assertEquals("abcdefghijk", video.videoId)
    }

    @Test
    fun `additional server fields are ignored`() {
        val json = response("null").replace("\"audio_clips\":", "\"future_field\":{\"nested\":[1,true,null]},\"audio_clips\":")
        assertEquals(SongMedia.parse(response("null")), SongMedia.parse(json))
    }

    @Test
    fun `song playback times retain minutes past one hour`() {
        assertEquals("0:00", formatSongTime(0))
        assertEquals("1:05", formatSongTime(65000))
        assertEquals("61:01", formatSongTime(3661000))
    }

    private fun response(views: String): String = """
        {"audio_clips":[{"url":"https://example.com/song.mid"}],"youtube_videos":[{
            "videoId":"abcdefghijk","title":"A song","channel_name":"Choir",
            "thumbnail_url":"https://example.com/thumb.jpg","views":$views,
            "published_at":"2020-01-02T03:04:05Z"
        }]}
    """.trimIndent()
}
