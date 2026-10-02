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
    private val client: () -> OkHttpClient = { Connections.devotionOkHttp },
    private val url: (DevotionDownloader.Key) -> String = {
        BuildConfig.SERVER_HOST + "/devotion/get?name=${it.name}&date=${it.date}&" + App.getAppIdentifierParamsEncoded()
    },
    private val cached: (DevotionDownloader.Key) -> DevotionArticle? = {
        App.services.storage.db.tryGetDevotion(it.name, it.date)
    },
    private val store: (DevotionArticle) -> Unit = { App.services.storage.db.storeArticleToDevotions(it) },
) : DevotionDownloader.Backend {
    override fun createRequest(key: DevotionDownloader.Key, refresh: Boolean): DevotionDownloader.Request {
        val request = Request.Builder().url(url(key))
        if (refresh) request.cacheControl(CacheControl.FORCE_NETWORK)
        val call = client().newCall(request.build())
        return object : DevotionDownloader.Request {
            override fun cancel() = call.cancel()

            override fun execute(): DevotionDownloader.State {
                try {
                    if (!refresh) {
                        cached(key)?.let {
                            return if (it.readyToUse) DevotionDownloader.State.READY else DevotionDownloader.State.UNAVAILABLE
                        }
                    }
                    val output = call.execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Devotion HTTP ${response.code}")
                        response.body.string()
                    }
                    if (call.isCanceled()) throw IOException("Devotion cancelled")
                    val kind = DevotionActivity.DevotionKind.getByName(key.name)
                        ?: throw IOException("Unknown devotion source ${key.name}")
                    val article = kind.getArticle(key.date)
                    article.fillIn(output)
                    // An unsuccessful refresh must leave an offline reading intact.
                    if (article.readyToUse || cached(key)?.readyToUse != true) store(article)
                    if (!article.readyToUse) return DevotionDownloader.State.UNAVAILABLE
                    AppEvents.emitDevotionDownloaded(key.name, key.date)
                    return DevotionDownloader.State.READY
                } catch (e: Exception) {
                    AppLog.d("DevotionDownloader", "Download failed for ${key.name} ${key.date}", e)
                    return DevotionDownloader.State.FAILED
                }
            }
        }
    }
}
