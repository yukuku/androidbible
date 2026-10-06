package yuku.alkitab.base.devotion

import java.io.IOException
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.DevotionActivity
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.events.AppEvents
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.BuildConfig

internal class DevotionDownloadBackend(
    private val client: () -> OkHttpClient,
    private val url: (DevotionDownloader.Key) -> String,
    private val cached: (DevotionDownloader.Key) -> DevotionArticle?,
    private val store: (DevotionArticle) -> Unit,
) : DevotionDownloader.Backend {
    override fun createRequest(key: DevotionDownloader.Key, refresh: Boolean): DevotionDownloader.Request {
        // Explicit refreshes revalidate the HTTP cache as well as bypassing stored articles.
        val request = Request.Builder().url(url(key))
        if (refresh) request.cacheControl(CacheControl.FORCE_NETWORK)
        val call = client().newCall(request.build())
        return object : DevotionDownloader.Request {
            override fun cancel() = call.cancel()

            override fun execute(): DevotionDownloader.State {
                try {
                    // Recheck storage because a queued request may already have been cached.
                    if (!refresh) {
                        cached(key)?.let {
                            return if (it.readyToUse) DevotionDownloader.State.READY else DevotionDownloader.State.UNAVAILABLE
                        }
                    }

                    // Read the complete successful response before creating a cache entry.
                    val output = call.execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Devotion HTTP ${response.code}")
                        response.body.string()
                    }
                    if (call.isCanceled()) throw IOException("Devotion cancelled")

                    // Parse the source response and preserve a ready article when refresh returns NG.
                    val kind = DevotionActivity.DevotionKind.getByName(key.name)
                        ?: throw IOException("Unknown devotion source ${key.name}")
                    val article = kind.getArticle(key.date)
                    article.fillIn(output)
                    if (article.readyToUse || cached(key)?.readyToUse != true) store(article)
                    if (!article.readyToUse) return DevotionDownloader.State.UNAVAILABLE

                    // Notify readers only after persistence succeeds.
                    AppEvents.emitDevotionDownloaded(key.name, key.date)
                    return DevotionDownloader.State.READY
                } catch (e: Exception) {
                    // Network, HTTP, parsing, and storage errors all terminate the loading state.
                    AppLog.d("DevotionDownloader", "Download failed for ${key.name} ${key.date}", e)
                    return DevotionDownloader.State.FAILED
                }
            }
        }
    }

    companion object {
        fun create(): DevotionDownloadBackend = DevotionDownloadBackend(
            client = { Connections.devotionOkHttp },
            url = {
                BuildConfig.SERVER_HOST + "/devotion/get?name=${it.name}&date=${it.date}&" + App.getAppIdentifierParamsEncoded()
            },
            cached = { App.services.storage.db.tryGetDevotion(it.name, it.date) },
            store = { App.services.storage.db.storeArticleToDevotions(it) },
        )
    }
}
